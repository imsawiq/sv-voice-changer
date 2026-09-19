# Changelog

## 1.2.0

### EN

- **Minecraft 26.3**, on Fabric and NeoForge. Mojang replaced GLFW with SDL in
  that release, which renamed the key-type constant and changed every key code
  behind it, so the hotkeys now take their codes from Minecraft's own table
  instead of from GLFW — the right place for them on every version.
- **New audio engine**, brought over from the Plasmo Voice build. Pitch and
  formant are now shifted separately by a phase vocoder, so raising a voice
  makes it sound like a different person rather than a chipmunk. All presets
  were rebuilt on it, and a Batman voice was added.
- **An API for other mods** (`org.sawiq.svvoicechanger.client.api`, inside the
  mod jar). A radio, a mask or a machine can apply a voice for as long as it
  needs one and hand it back, without touching what the player chose and
  without having to save and restore their settings. Mods can also add their
  own voices to the studio and follow what the player is doing. See
  `docs/API.md`.
- **Server side.** The mod now installs on a server as well. A config decides
  whether the voice changer may be used there, `/svvoicechanger` mutes and
  unmutes individual players, and on NeoForge the permission nodes go through
  the loader's own permission API. Worth being clear: the voice is changed on
  the speaker's machine before it is sent, so this is a policy an unmodified
  client obeys, not something a server can enforce.
- **Servers can limit hand tuning.** `allowed-voices` in the server config, or
  `/svvoicechanger restrict`, narrows players to ready-made voices or to the
  server's own voices only.
- **Servers can share voices.** Preset files dropped into the server's
  `shared-voices` folder appear in every player's studio while they are connected.
  Only numbers are read from those files and every value is clamped to its own
  range, so a server cannot use this to make a client load or run anything.

### RU

- **Minecraft 26.3**, на Fabric и NeoForge. В этой версии Mojang заменил GLFW
  на SDL: константа типа клавиши переименовалась, а коды клавиш поменяли
  значения. Горячие клавиши теперь берут коды из таблицы самого Minecraft, а
  не из GLFW — так и должно было быть на любой версии.
- **Новый звуковой движок**, перенесён из версии для Plasmo Voice. Высота и
  форманты теперь двигаются раздельно через фазовый вокодер, поэтому поднятый
  голос звучит как другой человек, а не как бурундук. Все пресеты пересобраны
  заново, добавлен голос Бэтмена.
- **API для других модов** (`org.sawiq.svvoicechanger.client.api`, внутри
  джарника). Рация, маска или машина могут подменить голос на нужное время и
  вернуть обратно, не трогая выбор игрока и не сохраняя его настройки вручную.
  Моды также могут добавлять свои голоса в студию. См. `docs/API.md`.
- **Серверная часть.** Мод теперь ставится и на сервер. Конфиг решает, можно
  ли менять голос, `/svvoicechanger` мутит и размучивает игроков, а на
  NeoForge права идут через встроенное в лоадер permission API. Важно: голос
  меняется на машине говорящего до отправки, поэтому это политика, которой
  подчиняется честный клиент, а не то, что сервер может проконтролировать.
- **Сервер может запретить ручную настройку.** `allowed-voices` в серверном
  конфиге или `/svvoicechanger restrict` оставляет игрокам только готовые
  голоса или только голоса сервера.
- **Сервер может раздавать голоса.** Файлы пресетов, положенные в серверную
  папку `shared-voices`, появляются в студии у всех, кто подключён. Из этих файлов
  читаются только числа, и каждое значение ограничено своим диапазоном, так
  что сервер не может через это заставить клиент что-то загрузить или
  выполнить.

## 1.1.0

### EN

- Added a CurseForge download button to the update screen alongside the existing Modrinth one. Update checks still run against the Modrinth API.

### RU

- В экран обновления добавлена кнопка скачивания с CurseForge рядом с Modrinth. Проверка обновлений по-прежнему идёт через Modrinth API.

## 1.0.1

### EN

- Fixed Simple Voice Chat becoming unavailable on Fabric 1.21.11 with SVC 2.6.21.
- Made the SVC menu, microphone-test, talking-HUD, and audio-processing integrations fail open instead of breaking voice chat when an optional hook changes.
- Improved the built-in Modrinth update checker: it now filters releases by Minecraft version and loader and compares versions safely.
- Added explicit minimum Simple Voice Chat dependency ranges for every supported Minecraft branch.
- Verified all 26 configured Fabric and NeoForge build targets with their configured SVC versions.
- Verified the eight release anchors against their oldest applicable SVC builds: 1.21.8 / 2.5.35, 1.21.11 / 2.6.7, 26.1.2 / 2.6.15, and 26.2 / 2.6.18 on both loaders.
- Distribution remains eight main JARs: four Minecraft ranges for Fabric and four for NeoForge.

### RU

- Исправлена поломка Simple Voice Chat на Fabric 1.21.11 с SVC 2.6.21, из-за которой не открывался интерфейс голосового чата.
- Интеграции с меню SVC, тестом микрофона, HUD говорящего игрока и аудиообработкой переведены в fail-open режим: изменение необязательного хука больше не отключает сам голосовой чат.
- Улучшена встроенная проверка обновлений Modrinth: релизы фильтруются по версии Minecraft и загрузчику, а версии сравниваются безопасно.
- Для каждой поддерживаемой ветки Minecraft добавлены явные минимальные диапазоны Simple Voice Chat.
- Все 26 настроенных build-таргетов Fabric и NeoForge проверены с указанными в проекте версиями SVC.
- Восемь релизных таргетов дополнительно проверены на нижних версиях SVC: 1.21.8 / 2.5.35, 1.21.11 / 2.6.7, 26.1.2 / 2.6.15 и 26.2 / 2.6.18 на обоих загрузчиках.
- Для публикации по-прежнему нужно восемь основных JAR: четыре диапазона Minecraft для Fabric и четыре для NeoForge.

### Minimum Simple Voice Chat versions / Минимальные версии Simple Voice Chat

| Minecraft | SVC |
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

Install the SVC file made for the exact Minecraft version. The minimums apply to both Fabric and NeoForge.

Ставьте файл SVC именно под свою версию Minecraft. Минимумы одинаковы для Fabric и NeoForge.
