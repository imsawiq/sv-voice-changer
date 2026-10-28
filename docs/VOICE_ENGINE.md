# Voice Changer Engine protocol, version 1

What the mod expects from a local voice conversion program. The Plasmo Voice
and Simple Voice Chat builds speak the same protocol, so one engine serves
both.

Plain HTTP/1.1 on `127.0.0.1:18889` by default. The mod keeps one connection
alive and sends a request every chunk (120 ms by default), so answers should
come back well inside the chunk length.

## `GET /v1/status`

```json
{
  "protocol": 1,
  "name": "Voice Changer Engine",
  "version": "1.0.0",
  "device": "NVIDIA GeForce RTX 4060 Ti (CUDA)",
  "models": [
    { "id": "rvc/miku", "name": "Miku", "kind": "RVC v2" }
  ]
}
```

- `protocol` must be `1`; anything else is shown as an error.
- `device` is free text, shown next to the engine's status.
- `models` lists what the player can pick. An empty list shows as
  "pick a voice". `kind` is shown in the picker's tooltip.

## `POST /v1/configure`

Sent once each time the mod starts a stream, before any audio.

```json
{ "session": "6f1c...", "model": "rvc/miku", "pitch": 12 }
```

- `session` is a fresh random id per stream. Keep each session's context
  (previous chunk, crossfade state) apart.
- `model` is an `id` from the status, or empty for whatever is loaded.
- `pitch` is in semitones, from -24 to 24.

The mod waits up to 30 seconds, so loading the model here is fine. Answer
`2xx` when ready, or an error status with `{"error": "..."}`, which the player
sees.

## `POST /v1/convert?session=...`

- Body: raw PCM, signed 16-bit little-endian, mono, 48 kHz.
  `Content-Type: application/octet-stream`.
- Answer: the converted audio in the same format and, ideally, the same
  length. A shorter or longer answer is still played, only out of step by the
  difference.

The mod times out after 5 seconds and gives up on the engine after three
failures in a row. Errors use `{"error": "..."}` as above.

After the player stops talking the mod sends silence for one more chunk, so
whatever the engine holds back for context is flushed out.
