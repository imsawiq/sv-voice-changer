package org.sawiq.svvoicechanger.client.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.sawiq.svvoicechanger.SvVoiceChanger;
import org.sawiq.svvoicechanger.client.VoiceChangerController;
import org.sawiq.svvoicechanger.client.ai.AiVoiceController;
import org.sawiq.svvoicechanger.client.ai.engine.EngineKind;
import org.sawiq.svvoicechanger.client.ai.engine.EngineModel;
import org.sawiq.svvoicechanger.client.ai.engine.local.DatasetReport;
import org.sawiq.svvoicechanger.client.ai.engine.local.Recordings;
import org.sawiq.svvoicechanger.client.ai.engine.local.TrainingStatus;
import org.sawiq.svvoicechanger.client.ai.engine.local.VoiceName;
import org.sawiq.svvoicechanger.client.ai.install.EngineFiles;
import org.sawiq.svvoicechanger.client.compat.ClientScreens;
import org.sawiq.svvoicechanger.client.ui.compat.CompatScreen;
import org.sawiq.svvoicechanger.client.ui.compat.StudioGraphics;

/**
 * Making a voice of your own without leaving the game, in three steps shown
 * as tabs in the header, the way Plasmo Voice shows its settings pages: which
 * voice, its recordings, training.
 *
 * <p>A step's tab only opens once the steps before it are done, and the
 * footer's main button moves on to the next one. The recordings folder is
 * read from the disk every second and checked by the engine whenever it
 * changes, so the player sees their files the moment they drop them in.</p>
 */
public final class AiTrainScreen extends CompatScreen {
    private static final int WIDGET_HEIGHT = SettingsList.WIDGET_HEIGHT;
    private static final int RESCAN_TICKS = 20;
    private static final int LISTED_FILES = 5;
    private static final double ENOUGH_MINUTES = 10.0D;

    private final Screen parent;
    private final VoiceChangerController addon;
    private final List<Runnable> refreshers = new ArrayList<>();

    private SettingsList list;
    private Step step = Step.VOICE;
    /** The voice being worked on: a folder name under the datasets folder, or empty. */
    private String voice = "";
    private List<String> voices = List.of();
    private Quality quality = Quality.NORMAL;
    private Recordings recordings = Recordings.EMPTY;
    private int ticksSinceScan;
    private DatasetReport report;
    /** What the last check was run on; a change in the folder runs it again. */
    private Recordings checkedRecordings;
    private boolean checking;
    private boolean starting;
    private Component notice;
    /** What the widgets were built for; when it changes, they are built again. */
    private String layoutKey = "";
    private EditBox newName;

    private enum Step {
        VOICE, RECORDINGS, TRAIN;

        String translationKey() {
            return "svvoicechanger.ai.train.step." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** How long to train: more passes over the recordings sound closer and take longer. */
    private enum Quality {
        FAST(100), NORMAL(200), BEST(400);

        final int epochs;

        Quality(int epochs) {
            this.epochs = epochs;
        }

        Quality next() {
            return values()[(ordinal() + 1) % values().length];
        }

        String translationKey() {
            return "svvoicechanger.ai.train.quality." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** Where the training step stands for the chosen voice; each shows different rows. */
    private enum Phase {
        SETUP, RUNNING, DONE, STOPPED, OTHER_RUNNING
    }

    public AiTrainScreen(Screen parent, VoiceChangerController addon) {
        super(Component.translatable("svvoicechanger.ai.train.title"));
        this.parent = parent;
        this.addon = addon;
        this.voices = voiceFolders();
        TrainingStatus training = addon.aiVoice().training();
        if (!training.name().isBlank() && !training.state().equals("idle")) {
            // Coming back to a job: straight to where it can be followed.
            this.voice = training.name();
            this.step = Step.TRAIN;
        } else if (!this.voices.isEmpty()) {
            this.voice = this.voices.get(0);
        }
        rescan();
    }

    private AiVoiceController ai() {
        return this.addon.aiVoice();
    }

    // --- Layout --------------------------------------------------------------

    @Override
    protected void init() {
        String typed = this.newName != null ? this.newName.getValue() : "";
        clearWidgets();
        this.refreshers.clear();
        this.newName = null;
        this.layoutKey = layoutKey();

        StudioFrame.addTabs(stepTabs(), this.font::width, this::addRenderableWidget);
        this.list = new SettingsList(this.width, this::addRenderableWidget);
        switch (this.step) {
            case VOICE -> buildVoiceStep(typed);
            case RECORDINGS -> buildRecordingsStep();
            case TRAIN -> buildTrainStep();
        }
        this.list.finish(this.height);
        buildFooter();
        refresh();
    }

    private String layoutKey() {
        return this.step + "/" + (this.step == Step.TRAIN ? phase() : "") + "/" + this.voices + "/" + this.voice
                + "/" + this.recordings.isEmpty();
    }

    /** The steps as header tabs; one opens once the steps before it are done. */
    private List<StudioFrame.Tab> stepTabs() {
        List<StudioFrame.Tab> tabs = new ArrayList<>();
        for (Step each : Step.values()) {
            boolean reachable = switch (each) {
                case VOICE -> true;
                case RECORDINGS -> !this.voice.isEmpty();
                case TRAIN -> !this.voice.isEmpty() && !this.recordings.isEmpty();
            };
            tabs.add(new StudioFrame.Tab(
                    Component.literal((each.ordinal() + 1) + ". ").append(Component.translatable(each.translationKey())),
                    null, each == this.step, reachable, () -> goTo(each)));
        }
        return tabs;
    }

    private void buildVoiceStep(String typed) {
        this.list.text(() -> Component.translatable("svvoicechanger.ai.train.voice.explain"),
                () -> StudioPaint.GREY, 2);

        this.list.category(Component.translatable("svvoicechanger.ai.train.voices"));
        if (this.voices.isEmpty()) {
            this.list.text(() -> Component.translatable("svvoicechanger.ai.train.no_voice"),
                    () -> StudioPaint.YELLOW, 2);
        }
        for (String name : this.voices) {
            boolean selected = name.equals(this.voice);
            int files = Recordings.scan(EngineFiles.datasetsDirectory().resolve(name)).audio().size();
            Component label = Component.literal(name).append(Component.translatable(
                    "svvoicechanger.ai.train.voice_files", files).withStyle(ChatFormatting.GRAY));
            Button choose = Button.builder(Component.translatable(selected
                                    ? "svvoicechanger.ai.train.chosen"
                                    : "svvoicechanger.ai.train.choose"),
                            pressed -> selectVoice(name))
                    .bounds(0, 0, SettingsList.CONTROL_WIDTH, WIDGET_HEIGHT).build();
            choose.active = !selected;
            this.list.option(label, choose);
        }

        this.list.category(Component.translatable("svvoicechanger.ai.train.new_voice"));
        this.newName = new EditBox(this.font, 0, 0, SettingsList.CONTROL_WIDTH, WIDGET_HEIGHT,
                Component.translatable("svvoicechanger.ai.train.name"));
        this.newName.setMaxLength(VoiceName.MAX_LENGTH);
        this.newName.setHint(Component.translatable("svvoicechanger.ai.train.name.hint"));
        this.newName.setTooltip(Tooltip.create(Component.translatable("svvoicechanger.ai.train.name.desc")));
        this.newName.setValue(typed);
        this.list.option(Component.translatable("svvoicechanger.ai.train.name"), this.newName);
        Button create = this.list.option(Component.empty(), Button.builder(
                        Component.translatable("svvoicechanger.ai.train.create"), pressed -> createVoice())
                .bounds(0, 0, SettingsList.CONTROL_WIDTH, WIDGET_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("svvoicechanger.ai.train.create.desc")))
                .build());
        this.refreshers.add(() -> create.active = create.visible && this.newName != null
                && !VoiceName.sanitize(this.newName.getValue()).isEmpty());
        if (this.notice != null) {
            Component shown = this.notice;
            this.list.text(() -> shown, () -> StudioPaint.RED, 2);
        }
    }

    private void buildRecordingsStep() {
        this.list.category(Component.translatable("svvoicechanger.ai.train.recordings.heading", this.voice));
        this.list.custom(SettingsList.ROW_HEIGHT, this::paintSpeechAmount);
        this.list.text(this::verdict, this::verdictColor, 2);

        this.list.option(Component.translatable("svvoicechanger.ai.train.folder_row"),
                button("svvoicechanger.ai.train.open_folder", "svvoicechanger.ai.train.open_folder.desc",
                        this::openFolder));
        this.list.option(Component.translatable("svvoicechanger.ai.train.how_to_record"),
                button("svvoicechanger.ai.train.open", "svvoicechanger.ai.train.how_to_record.desc",
                        this::openRecordingGuide));

        this.list.category(Component.translatable("svvoicechanger.ai.train.files_heading"));
        this.list.text(this::fileList, () -> this.recordings.isEmpty() ? StudioPaint.YELLOW : StudioPaint.GREY,
                LISTED_FILES + 1);
    }

    private void buildTrainStep() {
        this.list.category(Component.translatable("svvoicechanger.ai.train.train.heading", this.voice));
        TrainingStatus training = ai().training();
        switch (phase()) {
            case SETUP, STOPPED -> {
                Button qualityButton = this.list.option(Component.translatable("svvoicechanger.ai.train.quality.label"),
                        Button.builder(Component.translatable(this.quality.translationKey()), pressed -> {
                                    this.quality = this.quality.next();
                                    pressed.setMessage(Component.translatable(this.quality.translationKey()));
                                })
                                .bounds(0, 0, SettingsList.CONTROL_WIDTH, WIDGET_HEIGHT)
                                .tooltip(Tooltip.create(Component.translatable("svvoicechanger.ai.train.quality.desc")))
                                .build());
                qualityButton.active = phase() == Phase.SETUP;
                this.list.text(() -> Component.translatable(this.quality.translationKey() + ".desc"),
                        () -> StudioPaint.TEXT, 2);
                if (phase() == Phase.STOPPED) {
                    this.list.text(() -> Component.translatable(training.state().equals("failed")
                            ? "svvoicechanger.ai.train.failed"
                            : "svvoicechanger.ai.train.cancelled", this.voice), () -> StudioPaint.YELLOW, 2);
                }
                this.list.text(this::setupAdvice, () -> this.notice != null ? StudioPaint.RED : StudioPaint.YELLOW, 2);
                this.list.text(() -> Component.translatable("svvoicechanger.ai.train.note"), () -> StudioPaint.GREY, 3);
            }
            case RUNNING -> {
                this.list.custom(SettingsList.ROW_HEIGHT, this::paintTrainingBar);
                this.list.text(this::trainingDetail, () -> StudioPaint.TEXT, 1);
                this.list.text(() -> ai().training().etaSeconds() >= 0
                        ? Component.translatable("svvoicechanger.ai.train.eta", (ai().training().etaSeconds() + 59) / 60)
                        : Component.translatable("svvoicechanger.ai.train.eta_unknown"), () -> StudioPaint.GREY, 1);
                this.list.text(() -> Component.translatable("svvoicechanger.ai.train.keep_open"),
                        () -> StudioPaint.YELLOW, 1);
            }
            case DONE -> {
                this.list.custom(SettingsList.ROW_HEIGHT, (graphics, x, y, width) ->
                        StudioPaint.bar(graphics, x, y + 9, width, 6, 1.0D, StudioPaint.GREEN));
                this.list.text(() -> Component.translatable("svvoicechanger.ai.train.done", this.voice),
                        () -> StudioPaint.GREEN, 1);
                this.list.text(() -> Component.translatable("svvoicechanger.ai.train.done.explain"),
                        () -> StudioPaint.TEXT, 2);
            }
            case OTHER_RUNNING -> this.list.text(() -> Component.translatable(
                    "svvoicechanger.ai.train.other_running", ai().training().name()), () -> StudioPaint.YELLOW, 2);
        }
    }

    /** Back on the left, the step's one main action on the right. */
    private void buildFooter() {
        int width = 150;
        int y = StudioFrame.footerY(this.height);
        addRenderableWidget(Button.builder(Component.translatable(this.step == Step.VOICE
                                ? "gui.done"
                                : "svvoicechanger.ai.train.back"),
                        pressed -> back())
                .bounds(this.width / 2 - width - 2, y, width, WIDGET_HEIGHT).build());
        Button primary = addRenderableWidget(Button.builder(primaryLabel(), pressed -> onPrimary())
                .bounds(this.width / 2 + 2, y, width, WIDGET_HEIGHT).build());
        this.refreshers.add(() -> {
            primary.setMessage(primaryLabel());
            primary.active = isPrimaryActive();
        });
    }

    private static Button button(String labelKey, String descriptionKey, Runnable action) {
        return Button.builder(Component.translatable(labelKey), pressed -> action.run())
                .bounds(0, 0, SettingsList.CONTROL_WIDTH, WIDGET_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable(descriptionKey)))
                .build();
    }

    @Override
    public void tick() {
        super.tick();
        if (++this.ticksSinceScan >= RESCAN_TICKS) {
            rescan();
        }
        checkWhenChanged();
        if (!layoutKey().equals(this.layoutKey)) {
            init();
            return;
        }
        refresh();
    }

    private void refresh() {
        for (Runnable refresher : this.refreshers) {
            refresher.run();
        }
    }

    @Override
    public void onClose() {
        ClientScreens.open(this.parent);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.list.scrollBy(-(int) Math.round(verticalAmount * SettingsList.ROW_HEIGHT * 2))) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    // --- Navigation ----------------------------------------------------------

    private void goTo(Step target) {
        this.step = target;
        this.notice = null;
        init();
    }

    private void back() {
        switch (this.step) {
            case VOICE -> onClose();
            case RECORDINGS -> goTo(Step.VOICE);
            case TRAIN -> goTo(Step.RECORDINGS);
        }
    }

    private Component primaryLabel() {
        return switch (this.step) {
            case VOICE, RECORDINGS -> Component.translatable("svvoicechanger.ai.train.next");
            case TRAIN -> Component.translatable(switch (phase()) {
                case SETUP, OTHER_RUNNING -> "svvoicechanger.ai.train.start";
                case RUNNING -> "svvoicechanger.ai.train.cancel";
                case DONE -> "svvoicechanger.ai.train.use_short";
                case STOPPED -> "svvoicechanger.ai.train.resume";
            });
        };
    }

    private boolean isPrimaryActive() {
        return switch (this.step) {
            case VOICE -> !this.voice.isEmpty();
            case RECORDINGS -> !this.recordings.isEmpty();
            case TRAIN -> switch (phase()) {
                case SETUP, STOPPED -> trainBlocker() == null;
                case RUNNING, DONE -> true;
                case OTHER_RUNNING -> false;
            };
        };
    }

    private void onPrimary() {
        switch (this.step) {
            case VOICE -> goTo(Step.RECORDINGS);
            case RECORDINGS -> goTo(Step.TRAIN);
            case TRAIN -> {
                switch (phase()) {
                    case SETUP, STOPPED -> startTraining();
                    case RUNNING -> ai().cancelTraining();
                    case DONE -> useTrainedVoice();
                    case OTHER_RUNNING -> {
                    }
                }
            }
        }
    }

    // --- Step 1: the voice ---------------------------------------------------

    private static List<String> voiceFolders() {
        Path root = EngineFiles.datasetsDirectory();
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> children = Files.list(root)) {
            return children.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            return List.of();
        }
    }

    /** A new voice is a new folder; it is opened at once, since putting recordings in is the next step. */
    private void createVoice() {
        String name = VoiceName.sanitize(this.newName.getValue());
        if (name.isEmpty()) {
            return;
        }
        try {
            Files.createDirectories(EngineFiles.datasetsDirectory().resolve(name));
        } catch (IOException exception) {
            this.notice = Component.translatable("svvoicechanger.ai.open_link_failed",
                    EngineFiles.datasetsDirectory().toString());
            init();
            return;
        }
        this.newName.setValue("");
        this.voice = name;
        resetCheck();
        rescan();
        goTo(Step.RECORDINGS);
        openFolder();
    }

    private void selectVoice(String name) {
        this.voice = name;
        resetCheck();
        rescan();
        init();
    }

    private void resetCheck() {
        this.report = null;
        this.checkedRecordings = null;
        this.notice = null;
    }

    // --- Step 2: recordings --------------------------------------------------

    private Path voiceFolder() {
        return EngineFiles.datasetsDirectory().resolve(this.voice);
    }

    private void rescan() {
        this.ticksSinceScan = 0;
        this.voices = voiceFolders();
        this.recordings = this.voice.isEmpty() ? Recordings.EMPTY : Recordings.scan(voiceFolder());
    }

    /** Asks the engine about the recordings whenever they differ from what it last judged. */
    private void checkWhenChanged() {
        if (this.checking || this.recordings.isEmpty() || this.recordings.equals(this.checkedRecordings)
                || !ai().canCheckRecordings()) {
            return;
        }
        Recordings checked = this.recordings;
        this.checking = true;
        this.checkedRecordings = checked;
        ai().checkRecordings(voiceFolder(), answer -> Minecraft.getInstance().execute(() -> {
            this.checking = false;
            if (checked.equals(this.checkedRecordings)) {
                this.report = answer;
            }
        }));
    }

    private void openFolder() {
        Path folder = voiceFolder();
        try {
            Files.createDirectories(folder);
            LinkOpener.open(folder.toString());
        } catch (Exception exception) {
            ClientScreens.sendChatMessage(Component.translatable("svvoicechanger.ai.open_link_failed", folder.toString()));
        }
    }

    private void openRecordingGuide() {
        ClientScreens.open(new AiTextScreen(this,
                Component.translatable("svvoicechanger.ai.train.how_to_record"),
                Component.translatable("svvoicechanger.ai.train.guide"),
                Component.translatable("svvoicechanger.ai.train.open_folder"),
                this::openFolder));
    }

    /** The minutes of speech against the ten a good voice needs: words on the left, a bar on the right. */
    private void paintSpeechAmount(StudioGraphics graphics, int x, int y, int width) {
        Component amount;
        double fraction = 0.0D;
        int color = StudioPaint.YELLOW;
        if (this.report != null) {
            amount = Component.translatable("svvoicechanger.ai.train.minutes",
                    String.format(Locale.ROOT, "%.1f", this.report.minutes()));
            fraction = this.report.minutes() / ENOUGH_MINUTES;
            color = "ok".equals(this.report.verdict()) ? StudioPaint.GREEN : StudioPaint.YELLOW;
        } else if (this.recordings.isEmpty()) {
            amount = Component.translatable("svvoicechanger.ai.train.minutes", "0");
        } else if (!this.checking && this.recordings.equals(this.checkedRecordings)) {
            amount = Component.translatable("svvoicechanger.ai.own.check_failed");
            color = StudioPaint.RED;
        } else {
            amount = Component.translatable(ai().canCheckRecordings()
                    ? "svvoicechanger.ai.own.checking"
                    : "svvoicechanger.ai.train.check_waits");
            color = StudioPaint.GREY;
        }
        StudioPaint.line(graphics, amount, x, y + 8, width - SettingsList.CONTROL_WIDTH - 4, color);
        StudioPaint.bar(graphics, x + width - SettingsList.CONTROL_WIDTH, y + 9, SettingsList.CONTROL_WIDTH, 6,
                fraction, color);
    }

    private Component verdict() {
        if (this.report == null) {
            return Component.empty();
        }
        return Component.translatable(switch (this.report.verdict()) {
            case "ok" -> "svvoicechanger.ai.train.verdict.ok";
            case "too_short" -> "svvoicechanger.ai.train.verdict.too_short";
            default -> "svvoicechanger.ai.train.verdict.bad_quality";
        });
    }

    private int verdictColor() {
        return this.report != null && "ok".equals(this.report.verdict()) ? StudioPaint.GREEN : StudioPaint.YELLOW;
    }

    /** The files the engine will learn from, a few by name, or what is wrong with the folder. */
    private Component fileList() {
        Recordings current = this.recordings;
        if (current.isEmpty()) {
            return Component.translatable(current.others().isEmpty()
                    ? "svvoicechanger.ai.train.files.empty"
                    : "svvoicechanger.ai.train.files.wrong_type", current.others().size());
        }
        StringBuilder text = new StringBuilder();
        int shown = Math.min(current.audio().size(), LISTED_FILES);
        for (int i = 0; i < shown; i++) {
            text.append("• ").append(current.audio().get(i)).append('\n');
        }
        if (current.audio().size() > shown) {
            text.append(Component.translatable("svvoicechanger.ai.train.files.more",
                    current.audio().size() - shown).getString());
        }
        return Component.literal(text.toString().strip());
    }

    private boolean isRecordingsWeak() {
        return this.report != null && !"ok".equals(this.report.verdict());
    }

    // --- Step 3: training ----------------------------------------------------

    private Phase phase() {
        TrainingStatus training = ai().training();
        boolean ours = training.name().equals(this.voice);
        if (training.isRunning()) {
            return ours ? Phase.RUNNING : Phase.OTHER_RUNNING;
        }
        if (ours && training.isDone()) {
            return Phase.DONE;
        }
        return ours && training.canResume() ? Phase.STOPPED : Phase.SETUP;
    }

    /** What stands between the player and training, or a warning about the recordings, or nothing. */
    private Component setupAdvice() {
        if (this.notice != null) {
            return this.notice;
        }
        Component blocker = trainBlocker();
        if (blocker != null) {
            return blocker;
        }
        return isRecordingsWeak() ? Component.translatable("svvoicechanger.ai.train.weak_recordings") : Component.empty();
    }

    /** Why training cannot start right now, or null when it can. */
    private Component trainBlocker() {
        if (this.starting) {
            return Component.translatable("svvoicechanger.ai.train.started");
        }
        Component engine = engineProblem();
        if (engine != null) {
            return engine;
        }
        if (this.recordings.isEmpty()) {
            return Component.translatable("svvoicechanger.ai.train.blocked.no_recordings");
        }
        // Wait for the engine's verdict, unless it has already failed to give one.
        if (this.checking || this.report == null && !this.recordings.equals(this.checkedRecordings)) {
            return Component.translatable("svvoicechanger.ai.train.blocked.checking");
        }
        return null;
    }

    /** What is wrong with the engine as far as training goes, or null when it can train. */
    private Component engineProblem() {
        if (ai().canTrain()) {
            return null;
        }
        if (!ai().installer().isInstalled()) {
            return Component.translatable("svvoicechanger.ai.train.need_install");
        }
        return switch (ai().status(EngineKind.VOICE_ENGINE).availability()) {
            case READY, NO_MODEL -> Component.translatable("svvoicechanger.ai.train.need_gpu");
            case CHECKING, STARTING, NOT_RUNNING -> Component.translatable("svvoicechanger.ai.train.engine_starting");
            default -> Component.translatable("svvoicechanger.ai.train.engine_failed");
        };
    }

    private void paintTrainingBar(StudioGraphics graphics, int x, int y, int width) {
        TrainingStatus training = ai().training();
        Component stage = Component.translatable("svvoicechanger.ai.train.stage_of", training.stageIndex(),
                Math.max(training.stageCount(), 1),
                Component.translatable("svvoicechanger.ai.train.stage." + training.stage()));
        StudioPaint.line(graphics, stage, x, y + 8, width - SettingsList.CONTROL_WIDTH - 4, StudioPaint.WHITE);
        StudioPaint.bar(graphics, x + width - SettingsList.CONTROL_WIDTH, y + 9, SettingsList.CONTROL_WIDTH, 6,
                training.percent() / 100.0D, StudioPaint.BLUE);
    }

    private Component trainingDetail() {
        TrainingStatus training = ai().training();
        return training.stage().equals("training") && training.epochs() > 0
                ? Component.translatable("svvoicechanger.ai.train.epochs", training.epoch(), training.epochs(),
                        (int) training.percent())
                : Component.translatable("svvoicechanger.ai.train.percent", (int) training.percent());
    }

    private void startTraining() {
        boolean resume = phase() == Phase.STOPPED;
        this.starting = true;
        this.notice = null;
        ai().startTraining(this.voice, voiceFolder(), this.quality.epochs, resume,
                refusal -> Minecraft.getInstance().execute(() -> {
                    this.starting = false;
                    if (refusal != null) {
                        SvVoiceChanger.LOGGER.info("Training refused: {}", refusal);
                        this.notice = Component.translatable("svvoicechanger.ai.train.refused", refusal);
                    }
                }));
    }

    /** The engine lists a voice by its own id ("rvc/name"), not by the name it was trained under. */
    private void useTrainedVoice() {
        String name = ai().training().name();
        String id = ai().status(EngineKind.VOICE_ENGINE).models().stream()
                .map(EngineModel::id)
                .filter(modelId -> modelId.equals(name) || modelId.endsWith("/" + name))
                .findFirst()
                .orElse(name);
        this.addon.updateAiSettings(settings -> settings
                .withUseAi(true)
                .withVoiceEngine(settings.voiceEngine().withModel(id)));
        onClose();
    }

    // --- Rendering -----------------------------------------------------------

    @Override
    protected void paintBehindWidgets(StudioGraphics graphics) {
        StudioFrame.paintBackground(graphics, this.width, this.height);
    }

    @Override
    protected void paintAboveWidgets(StudioGraphics graphics, int mouseX, int mouseY) {
        StudioFrame.paintChrome(graphics, this.width, this.height, this.title);
        this.list.paint(graphics);
    }
}
