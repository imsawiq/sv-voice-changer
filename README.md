[![GitHub](https://cdn.modrinth.com/data/cached_images/53263a70bb7a7689208e40d465e4377be013ccca.png)](https://github.com/imsawiq)
[![Discord](https://cdn.modrinth.com/data/cached_images/be7e1b5fe7e280c38c5b29dd81c453ddfdad25c2.png)](https://discord.gg/UTxEy4PtSU)

# EN

**Fabric and NeoForge** addon for **Simple Voice Chat**. It processes outgoing microphone audio in real time and adds a native-looking Voice Changer Studio to the Simple Voice Chat menu. The voice is changed on your own client; install it on the server as well to set a policy for everyone and to hand out your own voices.

### ✨ Features

- **Built-in presets** — `Man`, `Woman`, `Titan`, `Kid`, `Demon`, and `Radio`.
- **Real outgoing processing** — changes microphone samples before Simple Voice Chat encodes and sends them.
- **Voice Changer Studio** — pitch, formant, EQ, distortion, echo, tremolo, robot layer, autotune, and more.
- **Saved profiles** — create, load, delete, and share custom presets.
- **Native menu integration** — opens from the Simple Voice Chat menu and changes the talking HUD microphone while enabled.
- **API for other mods** — a radio, a mask or a machine can apply a voice while it is in use and hand it back; mods can also add their own voices to the studio. See [docs/API.md](docs/API.md)
- **Server side** — install it on the server too, to allow or deny the voice changer, mute individual players by command, and hand out your own voices to everyone connected
- **Works without a server** — everything except the server policy runs on your client alone

### 📦 Dependencies

- **Simple Voice Chat** for the installed Minecraft version — required.
- **Fabric API** — required only for Fabric builds.
- **NeoForge** — required only for NeoForge builds.

### ✅ Simple Voice Chat compatibility

Use the Simple Voice Chat file made for your exact Minecraft version. The table lists the oldest supported SVC release; using the newest available release for that Minecraft version is recommended.

| Minecraft | Minimum Simple Voice Chat |
|---|---:|
| 1.21 | 2.5.15 |
| 1.21.1 | 2.5.20 |
| 1.21.2 | 2.5.24 |
| 1.21.3 | 2.5.25 |
| 1.21.4 | 2.5.26 |
| 1.21.5 | 2.5.29 |
| 1.21.6 | 2.5.30 |
| 1.21.7 | 2.5.34 |
| 1.21.8 | 2.5.35 |
| 1.21.9 | 2.6.4 |
| 1.21.10 | 2.6.6 |
| 1.21.11 | 2.6.7 |
| 26.1 | 2.6.14 |
| 26.1.1 | 2.6.14 |
| 26.1.2 | 2.6.15 |
| 26.2 | 2.6.18 |
| 26.3 | 2.6.23 |

These minimums apply to both Fabric and NeoForge. Version 1.2.0 compiles on all 28 configured Fabric and NeoForge targets, and ships one jar per Minecraft range. Integrations fail open: if an optional SVC screen or HUD hook changes, the voice chat itself remains available.

### 🔗 Links

- **GitHub:** [imsawiq/sv-voice-changer](https://github.com/imsawiq/sv-voice-changer)
- **Discord:** [Preset sharing server](https://discord.gg/UTxEy4PtSU)

# RU

Аддон для **Fabric и NeoForge** под **Simple Voice Chat**. Он в реальном времени обрабатывает исходящий звук микрофона и аккуратно добавляет Voice Changer Studio прямо в меню Simple Voice Chat. Голос меняется на твоём клиенте; поставь аддон ещё и на сервер, чтобы задавать правила для всех и раздавать свои голоса.

### ✨ Особенности

- **Готовые пресеты** — `Man`, `Woman`, `Titan`, `Kid`, `Demon` и `Radio`.
- **Реальная обработка исходящего голоса** — микрофон меняется до кодирования и отправки через Simple Voice Chat.
- **Студия войсченджера** — pitch, formant, EQ, distortion, echo, tremolo, robot layer, autotune и другие параметры.
- **Сохранение профилей** — создание, загрузка, удаление и обмен кастомными пресетами.
- **Нативная интеграция** — студия открывается из меню Simple Voice Chat, а при включённом эффекте меняется HUD-иконка микрофона.
- **API для других модов** — рация, маска или машина могут подменить голос на время и вернуть обратно; моды могут добавлять свои голоса в студию. См. [docs/API.md](docs/API.md)
- **Серверная часть** — мод ставится и на сервер: разрешить или запретить изменение голоса, замутить игрока командой, раздавать свои голоса всем, кто зашёл
- **Работает и без сервера** — всё, кроме серверной политики, живёт на клиенте

### 📦 Зависимости

- **Simple Voice Chat** под установленную версию Minecraft — обязательно.
- **Fabric API** — только для Fabric-сборок.
- **NeoForge** — только для NeoForge-сборок.

### ✅ Совместимость с Simple Voice Chat

Ставьте файл Simple Voice Chat именно под свою версию Minecraft. В таблице указана самая старая поддерживаемая версия SVC; рекомендуется использовать самую новую доступную сборку для вашей версии Minecraft.

| Minecraft | Минимальная версия Simple Voice Chat |
|---|---:|
| 1.21 | 2.5.15 |
| 1.21.1 | 2.5.20 |
| 1.21.2 | 2.5.24 |
| 1.21.3 | 2.5.25 |
| 1.21.4 | 2.5.26 |
| 1.21.5 | 2.5.29 |
| 1.21.6 | 2.5.30 |
| 1.21.7 | 2.5.34 |
| 1.21.8 | 2.5.35 |
| 1.21.9 | 2.6.4 |
| 1.21.10 | 2.6.6 |
| 1.21.11 | 2.6.7 |
| 26.1 | 2.6.14 |
| 26.1.1 | 2.6.14 |
| 26.1.2 | 2.6.15 |
| 26.2 | 2.6.18 |
| 26.3 | 2.6.23 |

Минимумы одинаковы для Fabric и NeoForge. Версия мода 1.2.0 собирается на всех 28 настроенных таргетах Fabric и NeoForge; на каждый диапазон версий Minecraft выходит свой jar. Интеграции работают по принципу fail-open: если необязательный хук экрана или HUD изменится, сам голосовой чат останется доступен.

### 🔗 Ссылки

- **GitHub:** [imsawiq/sv-voice-changer](https://github.com/imsawiq/sv-voice-changer)
- **Discord:** [Сервер для обмена пресетами](https://discord.gg/UTxEy4PtSU)
