# AI voice

The studio has a second tab, **AI Voice**, where the voice changer speaks with
an AI voice instead of the effect presets. Other players need nothing: they
hear an ordinary voice chat stream.

There are three engines. **Auto** uses the first one that is ready, in this
order, and you can pin one instead.

| Engine | Cost | When you are heard | Runs on |
|---|---|---|---|
| Voice Changer Engine | free | live, about 0.2 s behind | your PC (GPU recommended) |
| w-okada | free | live, about 0.2 s behind | your PC or a second PC on your network |
| ElevenLabs | paid, about $0.12 per minute of speech | after you finish each phrase | ElevenLabs' servers |

While an engine is not ready, the voice changer falls back to the effects, and
the AI tab says why on the line under the engine list.

With the AI voice speaking, your real voice is never sent, not even when the
engine fails: you are silent until it answers again.

A server can switch AI voices off with `allow-ai-voice=false` in
`config/sv-voice-changer/server.properties` or `/svvoicechanger ai off`.

Only use voices you have the right to use. Cloning a real person's voice
without their consent is against ElevenLabs' terms and, in many places, the law.

## Voice Changer Engine

A small companion program that runs voice models on your own PC and talks to
the mod over `127.0.0.1:18889`. Its protocol is described in
[VOICE_ENGINE.md](VOICE_ENGINE.md), so any program that speaks it works too.

1. Start the engine and load a model in it.
2. In the AI tab, open **Engine**, check the address, pick the model and press
   **Refresh**.
3. **Pitch** moves the voice in semitones: +12 when a male voice speaks into a
   female model, -12 the other way.
4. **Chunk** is how much audio goes per request. Lower answers sooner; raise
   it if the voice stutters on a slower GPU.

## w-okada

[w-okada's Voice Changer](https://github.com/w-okada/voice-changer) runs RVC
and Beatrice models and is what many players already use. The mod talks to
the open-source server, version 1.5 (`MMVCServerSIO`). VCClient 2.x is closed
source and has not been tried with the mod; if its status reads "not running"
while it runs, that is why.

1. Start it as usual (the server listens on port 18888) and load a model into
   a slot in its web page.
2. In the AI tab, open **w-okada** and press **Refresh**. Your slots appear
   under **Model**.
3. **Pitch** and **Chunk** work as above. Everything else - the model's index
   ratio, the pitch detector, the GPU - is set in w-okada's own page.

There is no need for a virtual audio cable: the mod sends your microphone to
w-okada and the answer straight into voice chat.

w-okada can run on a second computer with the GPU. Put that computer's address
(for example `192.168.1.20:18888`) in **Address**; only addresses on your own
network are accepted.

## ElevenLabs

ElevenLabs has no live voice changer in its API: it takes a finished phrase
and sends it back in another voice. So you talk as usual, and other players
hear you about a second after you let go of the push-to-talk key or stop
talking.

1. Get an API key on the [API keys page](https://elevenlabs.io/app/settings/api-keys).
   A key limited to the voice changer is enough; if it may also read the
   account, the AI tab shows the credits you have left.
2. Copy the key and press **Paste key from clipboard**. It is stored in
   `config/sv-voice-changer/ai/elevenlabs.key`, outside the preset
   folder, and never shown on screen.
3. Pick a voice. The list is your ElevenLabs voices: the premade ones, your own
   clones, and anything you added from the
   [voice library](https://elevenlabs.io/app/voice-library) (press **Refresh**
   after adding one).

Your audio is sent to ElevenLabs for conversion. Phrases shorter than 0.3 s
are not sent, and a phrase longer than 20 s is cut and sent in parts.

---

# ИИ-голос

В студии есть вторая вкладка, **ИИ-голос**: войсченджер говорит ИИ-голосом
вместо пресетов эффектов. Другим игрокам ничего ставить не нужно, они слышат
обычный голос в войсчате.

Движков три. **Авто** берёт первый готовый в этом порядке, а можно выбрать
нужный вручную.

| Движок | Цена | Когда тебя слышно | Где работает |
|---|---|---|---|
| Voice Changer Engine | бесплатно | вживую, с задержкой около 0.2 с | твой ПК (лучше с видеокартой) |
| w-okada | бесплатно | вживую, с задержкой около 0.2 с | твой ПК или второй ПК в домашней сети |
| ElevenLabs | платно, около $0.12 за минуту речи | после конца каждой фразы | серверы ElevenLabs |

Пока движок не готов, войсченджер говорит эффектами, а вкладка пишет почему,
строкой под списком движков.

Когда говорит ИИ-голос, твой настоящий голос не уходит в чат никогда, даже
если движок сломался: ты просто молчишь, пока он снова не ответит.

Сервер может запретить ИИ-голоса: `allow-ai-voice=false` в
`config/sv-voice-changer/server.properties` или `/svvoicechanger ai off`.

Используй только голоса, на которые у тебя есть право. Клонировать голос
реального человека без его согласия запрещают правила ElevenLabs и во многих
странах закон.

## Voice Changer Engine

Небольшая программа-компаньон: запускает модели голоса на твоём ПК и общается с
модом через `127.0.0.1:18889`. Протокол описан в [VOICE_ENGINE.md](VOICE_ENGINE.md),
так что подойдёт и любая другая программа, которая его понимает.

1. Запусти движок и загрузи в нём модель.
2. Во вкладке ИИ открой **Движок**, проверь адрес, выбери модель и нажми
   **Обновить**.
3. **Тон** сдвигает голос в полутонах: +12, когда мужской голос говорит в
   женскую модель, -12 наоборот.
4. **Кусок** - сколько звука уходит за один запрос. Меньше отвечает быстрее;
   увеличь, если голос заикается на слабой видеокарте.

## w-okada

[Voice Changer от w-okada](https://github.com/w-okada/voice-changer) запускает
модели RVC и Beatrice, многие им уже пользуются. Мод работает с открытым
сервером версии 1.5 (`MMVCServerSIO`). VCClient 2.x закрытый, и с модом его
не проверяли; если он запущен, а статус пишет "не запущен", причина в этом.

1. Запусти его как обычно (сервер слушает порт 18888) и загрузи модель в слот
   на его странице.
2. Во вкладке ИИ открой **w-okada** и нажми **Обновить**. Слоты появятся в
   **Модель**.
3. **Тон** и **Кусок** работают так же. Остальное - index ratio, детектор тона,
   видеокарта - настраивается на странице самого w-okada.

Виртуальный аудиокабель не нужен: мод сам отправляет микрофон в w-okada, а
ответ сразу в войсчат.

w-okada может работать на втором компьютере с видеокартой. Впиши его адрес
(например `192.168.1.20:18888`) в **Адрес**; принимаются только адреса твоей
сети.

## ElevenLabs

Живого войсченджера в API ElevenLabs нет: он принимает готовую фразу и
возвращает её другим голосом. Поэтому говоришь как обычно, а другие слышат
тебя примерно через секунду после того, как отпустишь кнопку или замолчишь.

1. Возьми API-ключ на [странице ключей](https://elevenlabs.io/app/settings/api-keys).
   Хватит ключа с доступом только к войсченджеру; если ему можно читать
   аккаунт, вкладка покажет остаток кредитов.
2. Скопируй ключ и нажми **Вставить ключ из буфера**. Он хранится в
   `config/sv-voice-changer/ai/elevenlabs.key`, отдельно от папки
   пресетов, и никогда не показывается на экране.
3. Выбери голос. В списке твои голоса ElevenLabs: готовые, твои клоны и всё,
   что добавлено из [библиотеки голосов](https://elevenlabs.io/app/voice-library)
   (после добавления нажми **Обновить**).

Твой звук уходит в ElevenLabs на обработку. Фразы короче 0.3 с не
отправляются, а фраза длиннее 20 с режется и уходит частями.
