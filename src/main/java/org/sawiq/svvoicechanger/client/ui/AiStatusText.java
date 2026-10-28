package org.sawiq.svvoicechanger.client.ui;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.sawiq.svvoicechanger.client.VoiceChangerController;
import org.sawiq.svvoicechanger.client.ai.AiVoiceController;
import org.sawiq.svvoicechanger.client.ai.engine.EngineKind;
import org.sawiq.svvoicechanger.client.ai.engine.EngineStatus;
import org.sawiq.svvoicechanger.client.ai.runtime.AiVoiceRuntime;

/**
 * The words and colours the AI tab uses to say what is going on: one line per
 * engine, and one line on what the player is heard as right now.
 *
 * <p>Kept apart from the screen because the screen is layout and buttons,
 * while this is the part that has to stay right as states are added.</p>
 */
final class AiStatusText {
    static final int GREEN = 0xFF66DD66;
    static final int YELLOW = 0xFFFFCC55;
    static final int RED = 0xFFFF7777;
    static final int GREY = 0xFFAAAAAA;

    private AiStatusText() {
    }

    /** "Voice Changer Engine: ready - RTX 4060 Ti", marked when it is the one speaking. */
    static Component engineLine(AiVoiceController ai, EngineKind kind) {
        EngineStatus status = ai.status(kind);
        Component state = Component.translatable(status.availability().translationKey());
        String marker = kind == ai.runningEngine() ? "> " : "  ";

        MutableComponent line = Component.literal(marker)
                .append(Component.translatable(kind.translationKey()))
                .append(": ")
                .append(state);
        if (!status.detail().isBlank()) {
            line.append(" - " + status.detail());
        }
        return line;
    }

    static int engineColor(EngineStatus status) {
        return switch (status.availability()) {
            case READY -> GREEN;
            case NO_MODEL -> YELLOW;
            case CHECKING, STARTING, NOT_RUNNING, NO_KEY -> GREY;
            case BAD_KEY, NO_CREDITS, FAILED -> RED;
        };
    }

    /** What other players hear, and if it is not the AI voice, the one thing in the way. */
    static Component nowLine(VoiceChangerController addon) {
        AiVoiceController ai = addon.aiVoice();
        if (!addon.isAiAllowedByServer()) {
            return Component.translatable("svvoicechanger.ai.now.server_denied");
        }
        if (!ai.isSelected()) {
            return Component.translatable("svvoicechanger.ai.now.effects");
        }
        if (!addon.isEffectEnabled()) {
            return Component.translatable("svvoicechanger.ai.now.switched_off");
        }

        EngineKind running = ai.runningEngine();
        if (running != null) {
            return Component.translatable("svvoicechanger.ai.now.speaking",
                    Component.translatable(running.translationKey()), delayOf(ai, running));
        }
        if (ai.startFailure() != null) {
            return Component.translatable("svvoicechanger.ai.now.failed", ai.startFailure());
        }
        return Component.translatable("svvoicechanger.ai.now.none_ready");
    }

    /**
     * The one next step, in words, for whichever engine the player is aiming
     * at; empty once the AI voice is speaking or not wanted. Players who see
     * "not ready" without this have no idea what to do about it.
     */
    static Component hintLine(VoiceChangerController addon) {
        AiVoiceController ai = addon.aiVoice();
        if (!addon.isAiAllowedByServer() || !ai.isSelected() || !addon.isEffectEnabled()
                || ai.runningEngine() != null) {
            return Component.empty();
        }

        EngineKind target = ai.targetEngine();
        if (target == null) {
            return Component.translatable("svvoicechanger.ai.hint.start");
        }
        if (target == EngineKind.VOICE_ENGINE && !ai.installer().isInstalled()) {
            return Component.translatable("svvoicechanger.ai.hint.not_installed");
        }
        return Component.translatable("svvoicechanger.ai.hint.engine",
                Component.translatable(target.translationKey()),
                Component.translatable("svvoicechanger.ai.hint."
                        + ai.status(target).availability().name().toLowerCase(Locale.ROOT)));
    }

    static int nowColor(VoiceChangerController addon) {
        AiVoiceController ai = addon.aiVoice();
        if (!addon.isAiAllowedByServer() || ai.startFailure() != null && ai.runningEngine() == null) {
            return RED;
        }
        if (ai.runningEngine() != null) {
            return GREEN;
        }
        return ai.isSelected() && addon.isEffectEnabled() ? YELLOW : GREY;
    }

    /**
     * A live engine is about one chunk and one round trip behind; a phrase
     * engine speaks once the phrase is over, which cannot be put in numbers.
     */
    private static Component delayOf(AiVoiceController ai, EngineKind running) {
        AiVoiceRuntime.LiveTiming timing = ai.liveTiming();
        if (running.delivery() == EngineKind.Delivery.PHRASE || timing == null) {
            return Component.translatable("svvoicechanger.ai.delay.phrase");
        }
        long millis = timing.chunkFrames() * 20L + timing.roundTripMillis();
        return Component.translatable("svvoicechanger.ai.delay.live", millis);
    }
}
