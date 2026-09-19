package org.sawiq.svvoicechanger.client.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.sawiq.svvoicechanger.SvVoiceChanger;
import org.sawiq.svvoicechanger.client.VoiceChangerController;
import org.sawiq.svvoicechanger.client.api.VoiceChangerApi;
import org.sawiq.svvoicechanger.client.api.VoicePreset;
import org.sawiq.svvoicechanger.protocol.PolicyReason;
import org.sawiq.svvoicechanger.protocol.SharedPreset;
import org.sawiq.svvoicechanger.protocol.VoiceChangerChannel;
import org.sawiq.svvoicechanger.protocol.VoiceChangerCodec;
import org.sawiq.svvoicechanger.protocol.VoiceSourceRule;

/**
 * What the server on the other end of the current connection has told us.
 *
 * <p>The handshake is deliberately client-first: this side greets the server as
 * soon as the channel can carry a packet, and everything the server has to say
 * is an answer to that. A server without the mod simply never answers, which
 * needs no handling — nothing was ever restricted and nothing was ever
 * offered.</p>
 *
 * <p>Everything received is scoped to the connection. Disconnecting drops the
 * policy and takes the server's voices back out of the studio, so a restriction
 * from one server cannot follow the player to another, or into single-player.
 * </p>
 */
public final class ServerVoiceSession {
    /**
     * Voices offered by a server live under their own namespace so they can be
     * told apart from the ones mods contributed, which must survive a
     * disconnect.
     */
    private static final String SHARED_NAMESPACE = VoiceSourceRule.SERVER_NAMESPACE;

    private final VoiceChangerController controller;
    private final List<String> sharedPresetIds = new ArrayList<>();

    private boolean greeted;

    public ServerVoiceSession(VoiceChangerController controller) {
        this.controller = controller;
    }

    /**
     * Greets the server once the channel is up, and forgets everything once it
     * is gone.
     *
     * <p>Driven from the client tick rather than from a join event because
     * "the connection can carry this channel" is the condition that actually
     * matters, and it is the one thing every loader and version reports the
     * same way.</p>
     */
    public void tick() {
        if (!ClientVoiceChannel.isConnected()) {
            if (this.greeted) {
                endSession();
            }
            return;
        }

        if (!this.greeted) {
            this.greeted = ClientVoiceChannel.send(VoiceChangerCodec.encodeHello(new VoiceChangerCodec.Hello(
                    VoiceChangerChannel.PROTOCOL_VERSION,
                    VoiceChangerApi.API_VERSION,
                    SvVoiceChanger.MOD_VERSION)));
        }
    }

    /** Called on the client thread with a payload the server sent. */
    public void receive(byte[] payload) {
        try {
            switch (VoiceChangerCodec.peekType(payload)) {
                case VoiceChangerChannel.TYPE_POLICY -> applyPolicy(VoiceChangerCodec.decodePolicy(payload));
                case VoiceChangerChannel.TYPE_PRESETS -> applyPresets(VoiceChangerCodec.decodePresets(payload));
                default -> {
                    // A message from a newer server. Ignoring it is correct:
                    // anything this build must obey would have come with a
                    // protocol bump, which the greeting already settled.
                }
            }
        } catch (IOException exception) {
            SvVoiceChanger.LOGGER.warn("Ignoring an unreadable voice changer message from the server: {}",
                    exception.getMessage());
        } catch (RuntimeException exception) {
            // Caught broadly on purpose: this runs on the thread that applies
            // network packets, and letting anything escape there disconnects
            // the player. A server message that cannot be applied is worth a
            // log line and nothing more.
            SvVoiceChanger.LOGGER.warn("Could not apply a voice changer message from the server", exception);
        }
    }

    private void applyPolicy(VoiceChangerCodec.Policy policy) {
        boolean wasAllowed = this.controller.isAllowedByServer();
        VoiceSourceRule previousRule = this.controller.getVoiceRule();

        this.controller.applyServerPolicy(
                policy.allowed(), policy.reason(), policy.message(), policy.voices());

        // Told in chat rather than the action bar: a player whose voice changer
        // just stopped working needs to be able to scroll back and read why.
        if (wasAllowed != policy.allowed()) {
            sendChatMessage(policy.allowed()
                    ? Component.translatable("svvoicechanger.server.restored")
                    : Component.translatable("svvoicechanger.server.blocked", messageOf(policy)));
            return;
        }

        // Only worth saying when it actually costs the player something: a
        // restriction they already satisfy needs no announcement.
        if (previousRule != policy.voices() && !this.controller.isSelectedVoiceAllowed()) {
            sendChatMessage(Component.translatable(
                    "svvoicechanger.server.voices_restricted",
                    Component.translatable(policy.voices().translationKey())));
        }
    }

    private void applyPresets(List<SharedPreset> presets) {
        removeSharedPresets();

        for (SharedPreset preset : presets) {
            String id = SHARED_NAMESPACE + ":" + preset.id();
            this.controller.api().contributedPresets().registerShared(VoicePreset.builder(id)
                    .displayName(Component.literal(preset.name()))
                    .description(Component.translatable("svvoicechanger.server.shared_voice"))
                    .profile(preset.toProfile())
                    .build());
            this.sharedPresetIds.add(id);
        }
    }

    private void endSession() {
        this.greeted = false;
        removeSharedPresets();
        this.controller.applyServerPolicy(true, PolicyReason.ALLOWED, "", VoiceSourceRule.ALL);
    }

    private void removeSharedPresets() {
        for (String id : this.sharedPresetIds) {
            this.controller.api().contributedPresets().unregister(id);
        }
        this.sharedPresetIds.clear();
    }

    /** The server's own wording when it gave one, otherwise ours, translated. */
    private static Component messageOf(VoiceChangerCodec.Policy policy) {
        return policy.message().isBlank()
                ? Component.translatable(policy.reason().translationKey())
                : Component.literal(policy.message());
    }

    private static void sendChatMessage(Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        //? if >=26.1 {
        /*client.player.sendSystemMessage(message);
        *///?} else {
        client.player.displayClientMessage(message, false);
        //?}
    }
}
