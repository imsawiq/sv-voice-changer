# Changelog

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
