# Changelog

## 1.2.5

### EN

#### Fixed
- **The key binds have a proper heading on Minecraft 1.21.9 and later.** In
  Controls > Key Binds the two voice changer binds sat under a raw text key
  ("key.category.sv-voice-changer.voice_changer") instead of the mod's name,
  at the very bottom of the list, and were easy to miss. They are now under
  "Simple Voice Voice Changer".

### RU

#### Исправлено
- **У биндов нормальный заголовок на Minecraft 1.21.9 и новее.** В "Управление
  > Привязки клавиш" оба бинда войсченджера стояли в самом низу под сырым
  текстовым ключом ("key.category.sv-voice-changer.voice_changer") вместо
  названия мода, и их было легко не заметить. Теперь они под заголовком
  "Simple Voice Voice Changer".


## 1.2.4

### EN

#### Fixed
- **The studio no longer crashes, or opens empty, on Fabric.** The fix in 1.2.3
  looked up the text-drawing call by its name, and that name only exists in the
  development environment: a Fabric game installed by a launcher calls it
  something else. Every Fabric build for 1.21 through 1.21.11 crashed the
  moment the studio drew its title, and where another mod caught that crash
  the studio showed up as a dark screen with nothing on it. Text is now drawn
  through a call that has the same shape on every 1.21 release, so nothing has
  to be looked up.
- **NeoForge on Minecraft 1.21.7 through 1.21.11 no longer stops at loading.** NeoForge
  21.7 changed how a packet that travels both ways is registered: the old call
  now leaves the client side without a handler, and NeoForge stops the game
  while loading ("clientbound payloads are missing client-side handlers").
  Those versions now register both sides the new way; the 1.21.8 build, which
  also covers releases from before the change, checks which one it is on.
- **The studio title is sharp again on 1.21 through 1.21.5.** Those releases
  blur the menu background as part of drawing the buttons, and the title was
  drawn before that, so it came out blurred.
- **The studio fits the default game window.** At 854x480 with automatic GUI
  scale, the right-hand buttons ran under the scrollbar and a pixel off the
  screen. The columns now narrow to fit.
- **The on/off switch in an open studio follows the server.** If a server
  turned the voice changer off while the studio was open, the switch kept
  reading "On" until you reopened it.
- **The diagnostics line stops claiming audio is arriving when it is not.** It
  kept showing the last busy second after the microphone went quiet, which with
  push-to-talk is every time the key is let go.

### RU

#### Исправлено
- **Студия на Fabric больше не падает и не открывается пустой.** Исправление в
  1.2.3 искало вызов отрисовки текста по имени, а это имя есть только в среде
  разработки: в игре, установленной через лаунчер, Fabric называет его иначе.
  Все Fabric-сборки под 1.21 - 1.21.11 падали, как только студия рисовала
  заголовок, а если этот краш перехватывал другой мод, студия выглядела как
  тёмный экран без интерфейса. Теперь текст рисуется через вызов, который
  одинаков во всех релизах 1.21, и искать ничего не нужно.
- **NeoForge на Minecraft 1.21.7 - 1.21.11 больше не останавливается при загрузке.** В NeoForge
  21.7 поменялась регистрация пакета, который ходит в обе стороны: старый
  вызов теперь оставляет клиентскую сторону без обработчика, и NeoForge
  останавливает игру при загрузке ("clientbound payloads are missing
  client-side handlers"). Теперь на этих версиях обе стороны регистрируются
  по-новому, а сборка под 1.21.8, которая покрывает и версии до этого
  изменения, сама проверяет, на какой из них запущена.
- **Заголовок студии снова чёткий на 1.21 - 1.21.5.** Эти версии размывают фон
  меню в момент отрисовки кнопок, а заголовок рисовался раньше и размывался
  вместе с фоном.
- **Студия помещается в окно игры по умолчанию.** При 854x480 и автоматическом
  масштабе интерфейса правые кнопки заезжали под полосу прокрутки и на пиксель
  за край экрана. Теперь колонки сужаются под окно.
- **Переключатель в открытой студии следует за сервером.** Если сервер
  выключал войсченджер, пока студия была открыта, переключатель продолжал
  показывать "вкл" до повторного открытия.
- **Строка диагностики больше не утверждает, что звук идёт, когда его нет.** Она
  продолжала показывать последнюю активную секунду после того, как микрофон
  затих, а с push-to-talk это происходит каждый раз, когда отпускаешь клавишу.


## 1.2.3

### EN

#### Added
- **A build of its own for Minecraft 1.21.9 and 1.21.10.** Those two releases
  were covered by the 1.21.11 build, which was compiled against 1.21.11 - and
  Minecraft changed enough in that release that the build could not work on the
  two below it. They now get a build compiled against them, and the 1.21.11
  build covers only 1.21.11.

#### Fixed
- **The studio opens again on Minecraft 1.21 through 1.21.5.** Minecraft 1.21.6
  changed what its text-drawing call returns. Nothing about the call looks
  different in the source, but it is a different method as far as the game is
  concerned, so a build compiled against the newer half came down the instant it
  tried to draw a label - which is to say the moment you opened the studio. The
  call is now resolved when the mod loads, so it works on either side of that
  change.

### RU

#### Добавлено
- **Отдельная сборка под Minecraft 1.21.9 и 1.21.10.** Эти две версии закрывала
  сборка под 1.21.11, собранная под 1.21.11 - а в этом релизе Minecraft
  изменился настолько, что на двух версиях ниже она работать не могла. Теперь у
  них своя сборка, собранная под них, а сборка под 1.21.11 закрывает только
  1.21.11.

#### Исправлено
- **Студия снова открывается на Minecraft 1.21 - 1.21.5.** В 1.21.6 Minecraft
  изменил то, что возвращает вызов отрисовки текста. В исходниках вызов выглядит
  точно так же, но для игры это другой метод, поэтому сборка, собранная под
  более новую половину диапазона, падала в тот момент, когда пыталась нарисовать
  первую подпись - то есть как только ты открывала студию. Теперь нужный вызов
  определяется при загрузке мода и работает по обе стороны от этого изменения.


## 1.2.2

### EN

#### Fixed
- **NeoForge builds no longer crash when you join a world on Minecraft 1.21
  through 1.21.6.** NeoForge moved the client-side packet send into a new class
  in 1.21.7 and removed the old one, and this build covers 1.21 through 1.21.8
  - both sides of that move. It was compiled against the newer Minecraft, so on
  1.21.6 and earlier the class it reached for was not there and the game came
  down as soon as the mod greeted the server. The right class is now looked up
  when the mod loads, so one build works across the whole range. Only NeoForge
  was affected; Fabric never had this.

### RU

#### Исправлено
- **Сборки под NeoForge больше не падают при входе в мир на Minecraft 1.21 -
  1.21.6.** В 1.21.7 NeoForge перенёс отправку пакета с клиента в новый класс,
  а старый убрал; эта сборка охватывает 1.21 - 1.21.8, то есть обе стороны
  этого переноса. Собрана она была под более новый Minecraft, поэтому на 1.21.6
  и старше нужного класса просто не оказывалось, и игра падала, как только мод
  здоровался с сервером. Теперь нужный класс определяется при загрузке мода, и
  одна сборка работает на всём диапазоне. Fabric это не затрагивало.


## 1.2.1

### EN

#### Fixed
- **NeoForge builds launch again.** 1.2.0 registered its network packet once
  per direction, and NeoForge refuses a second registration of the same
  packet id, so the mod failed during startup and took the game down with it.
  Both directions now go through a single registration. Fabric was never
  affected: it keeps the two directions in separate registries.

  On NeoForge 26.1 and later the fix needed a second step: the three-argument
  `playBidirectional` means something different there. It leaves the other
  direction without a handler, and NeoForge then refuses to finish loading.
  Those builds use the four-argument form, which takes both handlers.
- **No more warning screen on Minecraft 26.3.** NeoForge deprecated the
  `logoFile` key there and puts a warning in front of the player on startup for
  it. The 26.3 build now uses `iconFile`; older NeoForge does not know that key,
  so the earlier builds keep `logoFile`.

### RU

#### Исправлено
- **Сборки под NeoForge снова запускаются.** В 1.2.0 сетевой пакет
  регистрировался отдельно на каждое направление, а NeoForge не допускает
  повторную регистрацию пакета с тем же идентификатором, — мод падал при
  запуске и ронял игру. Теперь оба направления регистрируются одним вызовом.
  Fabric это никогда не затрагивало: там направления лежат в разных реестрах.

  На NeoForge 26.1 и новее понадобился второй шаг: трёхаргументный
  `playBidirectional` там значит другое — оставляет второе направление без
  обработчика, и NeoForge отказывается достраивать загрузку. Эти сборки
  используют четырёхаргументный вариант, который принимает оба обработчика.
- **На Minecraft 26.3 больше нет экрана с предупреждением.** NeoForge объявил
  там ключ `logoFile` устаревшим и показывает из-за него предупреждение при
  запуске. Сборка под 26.3 теперь использует `iconFile`; более старый NeoForge
  такого ключа не знает, поэтому у остальных сборок остаётся `logoFile`.


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

#### Fixed
- **The studio button in the Simple Voice Chat menu draws its icon again.**
  Simple Voice Chat refers to its icons one way in 2.5.x and another from
  2.6.x on, and the button only ever used the newer form, so on the older
  releases it drew the missing-texture checkerboard. It now takes the form
  from a button Simple Voice Chat built itself, which is the one its renderer
  is guaranteed to accept.
- **The update notice says which mod it is about.** It carried the Plasmo
  Voice build's name.

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

#### Исправлено
- **Кнопка студии в меню Simple Voice Chat снова рисует иконку.** В 2.5.x
  Simple Voice Chat ссылается на иконки одним способом, а начиная с 2.6.x -
  другим; кнопка всегда использовала только новый, поэтому на старых версиях
  вместо иконки была фиолетовая клетка. Теперь форма берётся с кнопки, которую
  сделал сам Simple Voice Chat, - её его отрисовщик точно принимает.
- **В уведомлении об обновлении теперь правильное название мода.** Там стояло
  название версии для Plasmo Voice.

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
