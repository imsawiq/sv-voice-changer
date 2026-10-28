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
import http.server
import json
import os
import re
import shutil
import subprocess
import sys
import threading
import urllib.parse
import urllib.request
import wave

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
    # --ui opens the AI screens over the title screen and quits, without a world:
    # quick to run after a change to them, and the screenshots are the point.
    global UI_ONLY, GALLERY_DIR
    UI_ONLY = "--ui" in sys.argv
    # --gallery DIR: screenshots for the mod pages, named, copied into DIR.
    if "--gallery" in sys.argv:
        GALLERY_DIR = os.path.abspath(sys.argv[sys.argv.index("--gallery") + 1])
    filters = [arg for arg in sys.argv[1:] if arg not in ("--ui", "--gallery", GALLERY_DIR)]
    cases = [c for c in read_cases() if all(f in (c["loader"], c["mc"]) for f in filters)]
    if not cases:
        sys.exit(f"No case matches {filters}")
    for case in cases:
        case["jar"] = newest_jar(case["pattern"])
        case["name"] = f"{case['loader']}-{case['mc']}-{os.path.basename(case['jar'])[:-4]}"

    engine = start_fake_engine()
    results = [(case, run_case(case)) for case in cases]
    engine.shutdown()

    print("\n== Summary")
    for case, problems in results:
        print(f"  {'PASS' if not problems else 'FAIL'}  {case['name']}")
    sys.exit(1 if any(problems for _, problems in results) else 0)


# --- A stand-in for the Voice Changer Engine ---------------------------------
#
# Speaks protocol 1 from docs/VOICE_ENGINE.md, so the probe can switch the AI
# voice on and watch it start and send. It plays
# the microphone back with its polarity flipped, which sounds the same and
# proves the audio made the round trip.

# Not the engine's default port: a game the player has open may be running
# the real engine there. The probe points the test client here instead.
ENGINE_ADDRESS = ("127.0.0.1", int(os.environ.get("VC_FAKE_ENGINE_PORT", "18899")))
engine_requests = {"configure": 0, "convert": 0}
UI_ONLY = False
GALLERY_DIR = None
# What the fake engine lists in gallery mode, so the voice gallery looks lived in.
GALLERY_VOICES = [
    ("rvc/deep_narrator", "Deep Narrator", "RVC v2 40k + index"),
    ("rvc/anime_girl", "Anime Girl", "RVC v2 48k + index"),
    ("rvc/old_wizard", "Old Wizard", "RVC v2 40k + index"),
    ("rvc/radio_host", "Radio Host", "RVC v2 48k"),
    ("rvc/robot", "Robot", "RVC v2 40k + index"),
    ("rvc/my_voice", "My Voice", "RVC v2 48k + index, trained in game"),
]
# One training job, as the wizard sees it: idle until it is started, then
# frozen part way through so the screenshot shows a running job.
training = {"state": "idle", "name": ""}


class FakeEngine(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_GET(self):
        if self.path == "/v1/train/status":
            running = training["state"] == "running"
            status = {"state": training["state"], "name": training["name"], "stage": "training" if running else "",
                      "stageIndex": 4 if running else 0, "stageCount": 6, "percent": 37.5 if running else 0,
                      "epoch": 75 if running else 0, "epochs": 200 if running else 0,
                      "etaSeconds": 1420 if running else -1, "lastLog": "", "error": None}
            return self.answer(200, json.dumps(status).encode())
        if self.path != "/v1/status":
            return self.answer(404, b'{"error": "unknown path"}')
        status = {"protocol": 1, "name": "runtime-check fake engine", "version": "0", "device": "Fake GPU (CUDA)",
                  "features": ["dataset_check", "train"],
                  "models": [{"id": "fake/echo", "name": "Echo", "kind": "test"}]}
        if GALLERY_DIR:
            status.update(device="NVIDIA GeForce RTX 4060 Ti (CUDA)",
                          features=["preview", "model_info", "dataset_check", "train"],
                          models=[{"id": i, "name": n, "kind": k} for i, n, k in GALLERY_VOICES])
        self.answer(200, json.dumps(status).encode())

    def do_POST(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        if self.path == "/v1/configure":
            engine_requests["configure"] += 1
            return self.answer(200, b"{}")
        if self.path == "/v1/dataset/check":
            report = {"files": 2, "minutes": 3.4, "tooQuiet": 0, "clipped": 0, "noisy": 0,
                      "verdict": "too_short", "tips": ["Record at least 10 minutes of speech."]}
            return self.answer(200, json.dumps(report).encode())
        if self.path == "/v1/train":
            training.update(state="running", name=json.loads(body)["name"])
            return self.answer(202, b'{"started": true}')
        if self.path == "/v1/train/cancel":
            training.update(state="cancelled")
            return self.answer(200, b"{}")
        if self.path.startswith("/v1/convert"):
            engine_requests["convert"] += 1
            flipped = bytearray(len(body))
            for i in range(0, len(body) - 1, 2):
                sample = int.from_bytes(body[i:i + 2], "little", signed=True)
                flipped[i:i + 2] = max(-32768, min(32767, -sample)).to_bytes(2, "little", signed=True)
            return self.answer(200, bytes(flipped), "application/octet-stream")
        self.answer(404, b'{"error": "unknown path"}')

    def answer(self, status, body, content_type="application/json"):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


def start_fake_engine():
    server = http.server.ThreadingHTTPServer(ENGINE_ADDRESS, FakeEngine)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server


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
    with open(os.path.join(game_dir, "probe-engine-address"), "w") as file:
        file.write("%s:%d" % ENGINE_ADDRESS)
    if UI_ONLY:
        prepare_ui_only(game_dir)
    if GALLERY_DIR:
        prepare_gallery(game_dir)

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
    # VC_VOICE_MOD_JAR swaps in another build of the voice mod, such as a
    # beta players run that Modrinth does not hand out as a release.
    override = os.environ.get("VC_VOICE_MOD_JAR")
    if override:
        runtime_mods = [jar for jar in runtime_mods if VOICE_MOD.replace("-", "") not in
                        os.path.basename(jar).replace("-", "").lower()] + [override]
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
    if GALLERY_DIR:
        collect_gallery(game_dir, case["name"])
    return problems, NATIVE_CRASH in gradle_output and not probe_started(game_dir)


def prepare_ui_only(game_dir):
    """The probe's marker, two short recordings for the training wizard, and Russian, the players' usual language."""
    with open(os.path.join(game_dir, "options.txt"), "a") as file:
        file.write("lang:ru_ru\n")
    open(os.path.join(game_dir, "probe-ui-only"), "w").close()
    write_demo_recordings(game_dir)


def prepare_gallery(game_dir):
    """A full HD window at the GUI scale most players use, in English, and the recordings the wizard shows."""
    with open(os.path.join(game_dir, "options.txt"), "a") as file:
        file.write("overrideWidth:1920\noverrideHeight:1080\nguiScale:3\nlang:en_us\n")
    open(os.path.join(game_dir, "probe-gallery"), "w").close()
    write_demo_recordings(game_dir)


GALLERY_SHOTS = ("pv-activation-tab-voice-changer", "svc-menu")


def collect_gallery(game_dir, case_name):
    """Copies the gallery screenshots out under the names the probe gave them."""
    log_path = os.path.join(game_dir, "logs", "latest.log")
    target = os.path.join(GALLERY_DIR, case_name)
    os.makedirs(target, exist_ok=True)
    with open(log_path, encoding="utf-8", errors="replace") as file:
        for match in re.finditer(r"\[probe\] screenshot (\S+): \S+ \S+ as (\S+\.png)", file.read()):
            name, saved = match.groups()
            if name.startswith("gallery-") or name in GALLERY_SHOTS:
                shutil.copy(os.path.join(game_dir, "screenshots", saved), os.path.join(target, name + ".png"))
    print(f"    gallery: {target}", flush=True)


def write_demo_recordings(game_dir):
    training.update(state="idle", name="")
    voice = os.path.join(game_dir, "voice-changer", "datasets", "demo-voice")
    os.makedirs(voice)
    for name in ("take_01.wav", "take_02.wav"):
        with wave.open(os.path.join(voice, name), "wb") as recording:
            recording.setnchannels(1)
            recording.setsampwidth(2)
            recording.setframerate(48000)
            recording.writeframes(bytes(48000 * 2))
    os.makedirs(os.path.join(game_dir, "voice-changer", "datasets", "second-voice"))


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
    print(f"    fake engine so far: {engine_requests['configure']} stream(s) configured, "
          f"{engine_requests['convert']} chunk(s) converted")

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
