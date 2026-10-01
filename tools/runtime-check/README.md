# Runtime check

Compiling every fork proves the code links against the Minecraft it was built
for. It says nothing about what happens when a player opens the jar in a real
game, and the crashes reported so far were all of that kind: a reflective
lookup by a name that exists only in the dev environment, a method whose return
type changed inside one jar's range, a packet registered the way only some
versions accept.

This starts real clients instead. For each line of `cases.txt` it takes the jar
from `dist/`, fetches the voice mod and Fabric API for that Minecraft version
from Modrinth, and launches the game with a small probe mod. Fabric runs as a
production client (intermediary names on 1.21.x, like a player's launcher);
NeoForge uses Mojang's names in production anyway, so its dev client with the
jars in `mods/` is the same thing.

The probe then:

1. opens the studio over the title screen, in both modes;
2. joins a flat test world, written by the vanilla server of the same
   version so no upgrade prompt gets in the way, and waits for the voice
   connection;
3. opens the voice mod's own settings and the studio from there;
4. turns the effect on, and runs the microphone through it with self-listen;
5. switches modes again, closes everything, and quits.

It takes a screenshot at each stage. A case fails on a crash, on a hang (a
watchdog dumps every thread's stack into the log after 30 seconds without a
client tick), on any step timing out, or on any warning or error the voice
changer logs.

## Running

Build the jars first (`scripts/build-all.ps1`), then:

```
python tools/runtime-check/check.py            # every case, a bit over an hour
python tools/runtime-check/check.py 1.21.11    # one Minecraft version
python tools/runtime-check/check.py neoforge   # one loader
python tools/runtime-check/check.py fabric 26.2  # both at once
```

A microphone has to be connected: the self-listen step waits for real audio.
Game windows open and close on their own; leave them alone while it runs.

Everything it downloads or writes goes to `tools/runtime-check/.work/`. Each
case leaves its game directory there (`run/<case>/`), with `logs/latest.log`,
`screenshots/` and `gradle.log`. Look at the screenshots: a screen that renders
without crashing can still be empty.
