"""Launch real game clients with the built jars and walk through the voice changer.

Every case in cases.txt starts a client with one jar from dist/, its voice mod
and Fabric API fetched from Modrinth, and the probe mod, which opens the studio
over the title screen, joins a world, opens it again from the voice mod's own
menu, switches modes and runs the microphone through the effect. A case passes
only if the probe reaches its end with no crash, no hang and no error logged by
the voice changer.

    python tools/runtime-check/check.py              every case
    python tools/runtime-check/check.py 1.21.11      cases for one Minecraft version
    python tools/runtime-check/check.py fabric       cases for one loader
"""
import glob
import json
import os
import re
import shutil
import subprocess
import sys
import urllib.parse
import urllib.request

# --- What this repository checks ---------------------------------------------
TARGET = "probe.SimpleVoiceTarget"
VOICE_MOD = "simple-voice-chat"

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
WORK = os.path.join(HERE, ".work")
GRADLE = os.path.join(REPO, "gradlew.bat" if os.name == "nt" else "gradlew")

NEOFORGE = {
    "1.21.1": "21.1.233", "1.21.3": "21.3.97", "1.21.4": "21.4.157", "1.21.5": "21.5.98", "1.21.6": "21.6.20-beta",
    "1.21.8": "21.8.54", "1.21.9": "21.9.16-beta", "1.21.10": "21.10.64", "1.21.11": "21.11.44",
    "26.1.2": "26.1.2.82", "26.2": "26.2.0.23-beta", "26.3": "26.3.0.7-beta",
}
GAME_OPTIONS = ("onboardAccessibility:false\npauseOnLostFocus:false\nsoundCategory_music:0.0\n"
                "tutorialStep:none\nnarrator:0\n")
CASE_TIMEOUT_SECONDS = 20 * 60
USER_AGENT = {"User-Agent": "imsawiq/voice-changer runtime-check"}


def main():
    filters = sys.argv[1:]
    cases = [c for c in read_cases() if all(f in (c["loader"], c["mc"]) for f in filters)]
    if not cases:
        sys.exit(f"No case matches {filters}")
    for case in cases:
        case["jar"] = newest_jar(case["pattern"])
        case["name"] = f"{case['loader']}-{case['mc']}-{os.path.basename(case['jar'])[:-4]}"

    results = [(case, run_case(case)) for case in cases]

    print("\n== Summary")
    for case, problems in results:
        print(f"  {'PASS' if not problems else 'FAIL'}  {case['name']}")
    sys.exit(1 if any(problems for _, problems in results) else 0)


def read_cases():
    cases = []
    with open(os.path.join(HERE, "cases.txt"), encoding="utf-8") as file:
        for line in file:
            line = line.split("#", 1)[0].strip()
            if not line:
                continue
            loader, mc, pattern = line.split()
            cases.append({"loader": loader, "mc": mc, "pattern": pattern})
    return cases


def newest_jar(pattern):
    jars = [j for j in glob.glob(os.path.join(REPO, pattern)) if not j.endswith("-sources.jar")]
    if not jars:
        sys.exit(f"No jar matches {pattern}; build it first")
    return max(jars, key=os.path.getmtime)


# --- One case -----------------------------------------------------------------

def run_case(case):
    print(f"\n== {case['name']}", flush=True)
    problems, died_at_startup = attempt_case(case)
    if died_at_startup:
        # The JVM itself has come down with an access violation a few times
        # while the window and the GL driver were being set up, before any
        # mod code ran, on cases that pass every other time. One retry, and
        # only for exactly that.
        print("    the game died natively before the title screen; retrying once", flush=True)
        problems, _ = attempt_case(case)
    return problems


def attempt_case(case):
    loader, mc = case["loader"], case["mc"]

    game_dir = os.path.join(WORK, "run", case["name"])
    shutil.rmtree(game_dir, ignore_errors=True)
    os.makedirs(os.path.join(game_dir, "saves"))
    shutil.copytree(ensure_world(mc), os.path.join(game_dir, "saves", "probe"))
    with open(os.path.join(game_dir, "options.txt"), "w") as file:
        file.write(GAME_OPTIONS)

    runtime_mods = fetch_runtime_mods(loader, mc)
    modern = mc.startswith("26.")
    project = os.path.join(HERE, loader + ("-26" if modern else ""))
    common = [f"-PgameDir={game_dir}", f"-Ptarget={TARGET}"]
    if loader == "fabric":
        mods_dir = os.path.join(WORK, "cases", case["name"])
        shutil.rmtree(mods_dir, ignore_errors=True)
        os.makedirs(mods_dir)
        fabric_api = next(re.match(r"fabric-api-(.+)\.jar", os.path.basename(j)).group(1)
                          for j in runtime_mods if os.path.basename(j).startswith("fabric-api-"))
        task = ["runProbe", f"-Pmc={mc}", f"-PfabricApi={fabric_api}",
                f"-PfabricLoader={latest_fabric_loader()}", f"-PmodsDir={mods_dir}"]
    else:
        mods_dir = os.path.join(game_dir, "mods")
        os.makedirs(mods_dir)
        task = ["runClient", f"-Pneoforge={NEOFORGE[mc]}"]
    for jar in runtime_mods + [case["jar"]]:
        shutil.copy(jar, mods_dir)

    command = [GRADLE, "-p", project, "--console=plain", *task, *common]
    try:
        gradle = subprocess.run(command, capture_output=True, text=True, timeout=CASE_TIMEOUT_SECONDS)
        gradle_output = gradle.stdout + gradle.stderr
    except subprocess.TimeoutExpired as timeout:
        gradle_output = f"timed out after {CASE_TIMEOUT_SECONDS} s\n{timeout.stdout or ''}"
    with open(os.path.join(game_dir, "gradle.log"), "w", encoding="utf-8") as file:
        file.write(gradle_output)

    problems = report(game_dir, gradle_output)
    return problems, NATIVE_CRASH in gradle_output and not probe_started(game_dir)


# Windows' STATUS_ACCESS_VIOLATION, as Gradle prints a process exit value.
NATIVE_CRASH = "exit value -1073741819"


def probe_started(game_dir):
    log_path = os.path.join(game_dir, "logs", "latest.log")
    if not os.path.exists(log_path):
        return False
    with open(log_path, encoding="utf-8", errors="replace") as file:
        return "[probe] ok" in file.read()


def report(game_dir, gradle_output):
    log_path = os.path.join(game_dir, "logs", "latest.log")
    log = open(log_path, encoding="utf-8", errors="replace").read() if os.path.exists(log_path) else ""
    crashes = glob.glob(os.path.join(game_dir, "crash-reports", "*.txt"))

    for line in log.splitlines():
        if "[probe] " in line:
            print("   ", line.split("]: ", 1)[-1][:300])

    problems = []
    if "[probe] PASS" not in log:
        problems.append("the probe did not reach its end")
    problems += [f"crash report {path}" for path in crashes]
    # Anything the voice changer logged at WARN or above, caught or not.
    for entry in re.split(r"\n(?=\[\d\d:\d\d:\d\d\])", log):
        head = entry.split("\n", 1)[0]
        if re.search(r"/(ERROR|WARN)\]", head) and re.search(r"sawiq|voicechanger", entry, re.IGNORECASE):
            problems.append("logged: " + head[:300])
    if not log:
        problems.append("the game never started:\n" + gradle_output[-3000:])

    print(f"    screenshots: {os.path.join(game_dir, 'screenshots')}")
    for problem in problems:
        print("    PROBLEM:", problem)
    print("    " + ("PASS" if not problems else "FAIL"), flush=True)
    return problems


def latest_fabric_loader():
    """The newest stable Fabric Loader, which is what a launcher installs."""
    url = "https://meta.fabricmc.net/v2/versions/loader"
    with urllib.request.urlopen(urllib.request.Request(url, headers=USER_AGENT)) as response:
        return next(v["version"] for v in json.load(response) if v["stable"])


# --- Modrinth -------------------------------------------------------------------

def fetch_runtime_mods(loader, mc):
    """The voice mod, Fabric API on Fabric, and whatever they require, for this version."""
    directory = os.path.join(WORK, "mods", f"{loader}-{mc}")
    os.makedirs(directory, exist_ok=True)
    wanted = [VOICE_MOD] + (["fabric-api"] if loader == "fabric" else [])
    jars, seen = [], set()
    while wanted:
        project = wanted.pop()
        if project in seen:
            continue
        seen.add(project)
        version = latest_version(project, mc, loader)
        file = next((f for f in version["files"] if f["primary"]), version["files"][0])
        path = os.path.join(directory, file["filename"])
        if not os.path.exists(path):
            download(file["url"], path)
        jars.append(path)
        wanted += [d["project_id"] for d in version["dependencies"]
                   if d["dependency_type"] == "required" and d["project_id"]]
        # Fabric API is also listed by id; skip it the second time around.
        if project == "fabric-api":
            seen.add(version["project_id"])
    return jars


def latest_version(project, mc, loader):
    query = urllib.parse.urlencode({"game_versions": json.dumps([mc]), "loaders": json.dumps([loader])})
    url = f"https://api.modrinth.com/v2/project/{project}/version?{query}"
    with urllib.request.urlopen(urllib.request.Request(url, headers=USER_AGENT)) as response:
        versions = json.load(response)
    releases = [v for v in versions if v["version_type"] == "release"] or versions
    if not releases:
        sys.exit(f"Modrinth has no {loader} build of {project} for {mc}")
    return releases[0]


def download(url, path):
    with urllib.request.urlopen(urllib.request.Request(url, headers=USER_AGENT)) as response:
        data = response.read()
    with open(path + ".part", "wb") as file:
        file.write(data)
    os.replace(path + ".part", path)


# --- The test world ---------------------------------------------------------------

def ensure_world(mc):
    """
    A small flat world written by the vanilla server of the same version, once.

    A world from any other version gets in the way: an older client refuses
    to downgrade it without asking, and newer ones ask about backups or run it
    through an upgrade that ends on a confirmation screen.
    """
    world = os.path.join(WORK, "worlds", mc)
    if os.path.exists(os.path.join(world, "level.dat")):
        return world

    server_dir = os.path.join(WORK, "worldgen")
    shutil.rmtree(server_dir, ignore_errors=True)
    os.makedirs(server_dir)
    manifest_url = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    with urllib.request.urlopen(manifest_url) as response:
        manifest = json.load(response)
    version_url = next(v["url"] for v in manifest["versions"] if v["id"] == mc)
    with urllib.request.urlopen(version_url) as response:
        server_url = json.load(response)["downloads"]["server"]["url"]
    download(server_url, os.path.join(server_dir, "server.jar"))

    with open(os.path.join(server_dir, "eula.txt"), "w") as file:
        file.write("eula=true\n")
    with open(os.path.join(server_dir, "server.properties"), "w") as file:
        file.write("level-name=probe\nlevel-type=minecraft\\:flat\ngenerate-structures=false\n"
                   "online-mode=false\nserver-port=25599\n")

    print(f"Generating the {mc} test world...", flush=True)
    server = subprocess.Popen([shutil.which("java"), "-Xmx2G", "-jar", "server.jar", "--nogui"],
                              cwd=server_dir, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                              stderr=subprocess.STDOUT, text=True)
    for line in server.stdout:
        if "Done (" in line:
            server.stdin.write("stop\n")
            server.stdin.flush()
    server.wait()

    shutil.copytree(os.path.join(server_dir, "probe"), world, ignore=shutil.ignore_patterns("session.lock"))
    shutil.rmtree(server_dir, ignore_errors=True)
    return world


if __name__ == "__main__":
    main()
