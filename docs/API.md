# Simple Voice Changer — API and server guide

Two audiences, two halves.

- **Mod developers**: an API inside the mod jar for changing a player's voice
  while something is happening — a radio, a mask, a machine — and for adding
  your own voices to the studio.
- **Server operators**: a config, commands and permissions for deciding whether
  the voice changer may be used on your server, plus a folder of voices you can
  hand out to everyone who joins.

The Plasmo Voice build of the mod (`pv-voice-changer`) has the same API and the
same server features; see [Differences](#differences-from-the-plasmo-voice-build).

---

## Part 1 — For mod developers

### Adding the dependency

The API ships inside the mod jar, in `org.sawiq.svvoicechanger.client.api`.
There is no separate artifact to publish or version-match.

```gradle
repositories {
    maven { url = "https://api.modrinth.com/maven" }
}

dependencies {
    // Compile against it; the player supplies it at runtime.
    modCompileOnly "maven.modrinth:sv-voice-changer:VERSION"
}
```

Then make it a soft dependency, so your mod still loads without it:

```json
"suggests": {
  "sv-voice-changer": "*"
}
```

Everything in `org.sawiq.svvoicechanger.client.api`, plus `VoiceProfile` and
`VoiceParameter`, is a stable contract. Treat every other class as internal.

### Getting the API

```java
Optional<VoiceChangerApi> api = VoiceChangerApi.get();
```

It never throws, so a soft dependency can call it unconditionally. It is empty
when the mod is absent or has not started yet. Every method is safe to call from
any thread.

### Changing the voice while something is happening

This is what the API is for. Push an override, keep the handle, release it when
the effect ends:

```java
VoiceOverride radio = api.pushOverride(
        VoiceOverride.request("radiomod:handset", 100)
                .profile(api.builtInProfile("radio").orElseThrow())
                .build());

// ... later, when the player puts the handset down
radio.release();
```

An override never touches what the player chose. Their own voice is still there
underneath and comes back by itself the moment the override is released, which
is what removes the need to save and restore their settings by hand — and what
means a crash halfway through leaves nothing broken.

Several mods may hold overrides at once. The highest priority wins; ties go to
whoever pushed last. As a rough scale:

| Priority | For |
|---:|---|
| 0–99 | ambience and cosmetics a player would expect to be overridden |
| 100–499 | equipment and items: a radio, a mask, a helmet |
| 500+ | mechanics the player cannot opt out of: possession, a curse, a machine |

`releaseOverrides("yourmod:something")` drops every override you hold under that
owner id, which is worth calling on world unload as a safety net.

**Options:**

```java
VoiceOverride.request("mymod:effect", 200)
        .profile(profile)     // required
        .strength(80)         // optional: overrides the player's strength slider
        .forceEnabled()       // optional: applies even with the effect switched off
        .build();
```

Use `forceEnabled()` only for mechanics the player is not meant to opt out of.
It does not get past a server that has switched the voice changer off — a policy
the player has to obey is not one another mod may waive on their behalf.

### Building a voice

`VoiceProfile` is immutable. Start from a built-in voice, or from scratch:

```java
VoiceProfile deepRadio = api.builtInProfile("radio").orElseThrow()
        .with(VoiceParameter.PITCH, 0.85D)
        .with(VoiceParameter.LOW_EQ, 4.0D);

VoiceProfile scratch = VoiceProfile.builder()
        .set(VoiceParameter.MIX, 1.00D)
        .set(VoiceParameter.PITCH, 0.80D)
        .set(VoiceParameter.FORMANT, 0.85D)
        .set(VoiceParameter.GROWL, 0.40D)
        .build();
```

Built-in ids: `man`, `woman`, `kid`, `titan`, `demon`, `batman`, `radio`.

Values are clamped to each parameter's own range on the way in, so an
out-of-range or non-finite number can never reach the audio thread.

Two parameters are worth understanding, because they are what makes a voice
sound like a person rather than a chipmunk:

- **`PITCH`** moves the harmonics — how high the voice sounds.
- **`FORMANT`** moves the spectral envelope — how large the speaker seems.

They are genuinely independent here. A real adult female voice sits about a
musical fourth above a male one in pitch but only ~20% up in vocal tract length;
moving both by the same amount is exactly what produces the chipmunk sound.

`VOICE_MATCH` decides how much a preset is treated as a target voice rather than
as a plain multiplier. At `0.0` the numbers apply as written, whoever is
speaking; at `1.0` the chain adapts fully to the speaker's own pitch, which
means a speaker who already sits on the target hears nothing happen. The
built-in character voices use `0.60`. A transmission effect such as `radio`
leaves it at `0.0`, because it is not a different person and must keep your
pitch.

### Contributing a voice to the studio

```java
api.registerPreset(VoicePreset.builder("radiomod:dispatch")
        .displayName(Component.translatable("radiomod.voice.dispatch"))
        .description(Component.translatable("radiomod.voice.dispatch.desc"))
        .profile(profile)
        .build());
```

The id must be `namespace:path`, lowercase, using only `a-z 0-9 _ - .`. It is
written into the player's saved settings, so keep it stable across releases.
Registering the same id again replaces the earlier one. If your mod is later
removed, the player keeps the tuning they had and the studio simply shows it as
a custom voice.

The `server` namespace is refused: it belongs to the voices a server shares,
which are taken back out of the studio on disconnect. A voice registered there
would disappear with them.

### Reading state and listening for changes

```java
api.isEffectEnabled();       // the player's own switch
api.isAllowed();             // whether the server permits it here
api.getStrength();           // 0-100
api.getSelectedVoiceId();    // "man", "yourmod:voice", or "custom"
api.getActiveProfile();      // what is actually being applied, override included
api.getPlayerProfile();      // what the player chose, ignoring overrides
api.getActiveOverride();     // the override currently winning, if any
```

```java
api.addListener(new VoiceChangerListener() {
    @Override
    public void onVoiceChanged(String voiceId) { ... }

    @Override
    public void onAllowedChanged(boolean allowed, String reason) { ... }
});
```

Every listener method has a default, so implement only what you care about.

### What the API deliberately does not do

There is no way to change the player's own saved voice, their strength, or
whether the effect is on. A mod that wants a different voice asks for it with an
override, which is visible, temporary and reversible.

---

## Part 2 — For server operators

Install the mod on the server as well as the client. It works on Fabric and
NeoForge, and a world opened to LAN gets the same policy as a dedicated server.

### What this can and cannot do

The voice is changed on the speaker's own machine, before the audio is encoded
and sent. By the time anything reaches the server it has already happened and
cannot be undone.

So everything below is a **policy an unmodified client obeys**, in the same way
the vanilla client obeys a server telling it the player is in survival mode. It
stops the ordinary player who was asked not to. It does not stop somebody
running a patched build, and no server-side code could — this is worth being
clear about before you rely on it.

### The config

`config/sv-voice-changer/server.properties`, created on first start:

```properties
allow-voice-changer = true
denied-message =
share-presets = true
```

| Key | Meaning |
|---|---|
| `allow-voice-changer` | Whether players may change their voice here. Also toggled live with `/svvoicechanger on\|off`. |
| `denied-message` | Shown to a player who is refused. Leave empty to use the client's own message, which is already translated. |
| `share-presets` | Whether the voices in the `shared-voices` folder are offered to players. |
| `allowed-voices` | How much freedom players have over their voice: `all`, `presets` or `server-only`. See below. |

### Limiting where voices come from

The other half of "may they use it" is "how far may they go with it". A server
full of people with the reverb and distortion sliders at maximum is unpleasant
to be in, so `allowed-voices` narrows what a player may pick without switching
the feature off:

| Value | Players may |
|---|---|
| `all` | anything, including the sliders (default) |
| `presets` | pick a ready-made voice — built-in, added by another mod, or shared by this server. No hand tuning, no personal preset files. |
| `server-only` | pick only a voice this server shares |

`/svvoicechanger restrict presets` changes it live and tells every connected
player at once.

Under `presets` and `server-only` the studio's tuning controls and the personal
preset library are greyed out with the reason, the voice grid only lists what is
permitted, and the client refuses those edits even if a widget were left enabled
by mistake. A player whose current voice is not permitted is told to pick
another, and nothing is applied to their microphone until they do — their own
saved settings are left alone and come back when they leave.

**A voice another mod is holding is not affected.** A radio, a mask or a machine
is a mechanic you installed, not a player turning knobs, and restricting it
would break the thing the restriction is meant to protect.

### Commands

`/svvoicechanger` (alias `/svc`):

| Command | Does |
|---|---|
| `status` | Current policy, mute count, shared voice count |
| `on` / `off` | Allow or deny the voice changer for everybody |
| `restrict <all\|presets\|server-only>` | Limit where players' voices may come from |
| `mute <player>` | Switch off one player's voice changer |
| `unmute <player>` | Give it back |
| `mutelist` | Who is muted |
| `voices` | The voices this server is offering |
| `reload` | Reread the config, the mute list and the presets folder |

Mutes are stored by UUID in `mutes.properties`, so they apply to an offline
player, survive a name change, and cannot be shed by reconnecting. The player
name is resolved through the same vanilla lookup `/ban` uses.

### Permissions

**NeoForge** has a permission API built into the loader, so both nodes are real
permissions that any permission manager can grant:

| Node | Default | Controls |
|---|---|---|
| `sv-voice-changer:use` | granted | May change their voice on this server. Take it away to refuse somebody. |
| `sv-voice-changer:command` | not granted | Lets a non-operator run `/svvoicechanger`. Operators can run it regardless. |

**Fabric** has no equivalent in the loader or in Fabric API, and this mod does
not take a dependency on the community library that fills the gap. There, the
command requires the vanilla operator level (permission level 2), and per-player
control is the mute list rather than a permission node.

### Sharing voices with players

Drop preset files into `config/sv-voice-changer/shared-voices/` and they
appear in every player's studio, alongside their own voices, for as long as
they are connected. They are not saved into the player's own library and
disappear when they leave.

The folder is deliberately separate from `config/sv-voice-changer/presets`,
where a player's own saved voices live: on a world opened to LAN those two
would otherwise be the same folder, and every private voice would be shared.

The format is the one the client already saves presets in: tune a voice in the
studio, press Save, then use the studio's "Open folder" button to find the file
and copy it in. Add a line to control the label:

```properties
name = Dispatch Radio
```

Without it, the filename is used. The filename also becomes the voice's id. Run
`/svvoicechanger reload` after adding files.

**On safety.** Only numbers are read from these files. The id is derived from
the filename rather than taken from the file, every parameter is clamped to its
own range, and anything the client does not recognise is dropped. Nothing a
server puts in this folder can make a client load or run anything — the worst it
can do is offer a voice that sounds bad.

### The protocol, if you are curious

One plugin channel, `sv-voice-changer:main`, carrying three messages: the
client's greeting, the policy, and the preset list. The client greets first, and
everything the server says is an answer to that, so a server without the mod
simply never answers and nothing is ever restricted. Messages are
length-bounded, version-checked, and refused rather than half-read when they do
not parse.

---

## Differences from the Plasmo Voice build

The `pv-voice-changer` build has the same API — the same class names under
`org.sawiq.client.api` — and the same server config, commands and shared
presets.

- Its command is `/voicechanger` (alias `/vc`).
- Its permissions go through Plasmo Voice's own permission layer on **both**
  loaders, so LuckPerms works on Fabric there as well.
