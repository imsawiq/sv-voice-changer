package org.sawiq.svvoicechanger.server;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.sawiq.svvoicechanger.SvVoiceChanger;
import org.sawiq.svvoicechanger.network.VoiceChangerNetwork;
import org.sawiq.svvoicechanger.protocol.PolicyReason;
import org.sawiq.svvoicechanger.protocol.SharedPreset;
import org.sawiq.svvoicechanger.protocol.VoiceChangerChannel;
import org.sawiq.svvoicechanger.protocol.VoiceChangerCodec;
import org.sawiq.svvoicechanger.protocol.VoiceSourceRule;

/**
 * The server half of the mod: the policy, the moderation state and the voices
 * the server offers.
 *
 * <p>Worth being clear about what this can and cannot do. The voice is changed
 * on the speaker's own machine, before the audio is encoded and sent, so by the
 * time anything reaches the server it has already happened and cannot be
 * undone. Everything here is therefore a policy that an unmodified client
 * obeys, in the same way the vanilla client obeys a server telling it the
 * player is in survival mode. It stops the ordinary player who was asked not
 * to; it does not stop somebody running a patched build, and no server-side
 * code could.</p>
 *
 * <p>Loader-neutral: the events that drive it are wired up by whichever
 * entrypoint is running, and everything it sends goes through
 * {@link VoiceChangerNetwork}.</p>
 */
public final class VoiceChangerServerRuntime {
    public static final VoiceChangerServerRuntime INSTANCE = new VoiceChangerServerRuntime();

    private MinecraftServer server;
    private ServerVoiceChangerConfig config;
    private VoiceChangerMutes mutes;
    private ServerPresetLibrary presetLibrary;

    private VoiceChangerServerRuntime() {
    }

    /**
     * @param configDirectory the loader's config folder; the mod's own folder
     *                        is created inside it
     */
    public void start(MinecraftServer server, Path configDirectory) {
        Path directory = configDirectory.resolve(SvVoiceChanger.MOD_ID);

        this.server = server;
        this.config = new ServerVoiceChangerConfig(directory);
        this.mutes = new VoiceChangerMutes(directory);
        this.presetLibrary = new ServerPresetLibrary(directory);
        loadFromDisk();

        VoiceChangerNetwork.setServerReceiver(this::onChannelMessage);

        SvVoiceChanger.LOGGER.info("Voice changer server policy ready: {}, {} muted, {} shared voices",
                this.config.isAllowed() ? "allowed" : "denied",
                this.mutes.size(),
                this.presetLibrary.presets().size());
    }

    public void stop() {
        VoiceChangerNetwork.setServerReceiver(null);
        this.server = null;
    }

    public boolean isRunning() {
        return this.server != null && this.config != null;
    }

    // --- State the command acts on ------------------------------------------

    public ServerVoiceChangerConfig config() {
        return this.config;
    }

    public VoiceChangerMutes mutes() {
        return this.mutes;
    }

    public ServerPresetLibrary presetLibrary() {
        return this.presetLibrary;
    }

    public MinecraftServer server() {
        return this.server;
    }

    /** Rereads every file, so an operator can edit them without a restart. */
    public void reload() throws IOException {
        this.config.load();
        this.mutes.load();
        this.presetLibrary.reload();
        broadcastPolicy();
    }

    // --- Telling clients what applies ---------------------------------------

    /**
     * Re-sends the policy to everybody who can receive it.
     *
     * <p>"Can receive it" is asked of the network layer rather than tracked
     * here: a client that registered our channel is exactly a client with the
     * mod, and that list is already maintained for us and already correct
     * across reconnects.</p>
     */
    public void broadcastPolicy() {
        if (!isRunning()) {
            return;
        }

        for (ServerPlayer player : this.server.getPlayerList().getPlayers()) {
            sendPolicy(player);
        }
    }

    /** Re-sends the policy to one player, if they are here and have the mod. */
    public void sendPolicyIfPresent(UUID playerId) {
        if (!isRunning()) {
            return;
        }

        ServerPlayer player = this.server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            sendPolicy(player);
        }
    }

    /** A no-op for a player without the mod: the send is refused by the channel. */
    private void sendPolicy(ServerPlayer player) {
        VoiceChangerNetwork.sendToPlayer(player, VoiceChangerCodec.encodePolicy(policyFor(player)));
    }

    /**
     * The strictest of the three answers wins, and the reason names the first
     * thing that would have to change: telling a muted player that the server
     * has it switched off would send them to complain to the wrong person.
     */
    private VoiceChangerCodec.Policy policyFor(ServerPlayer player) {
        VoiceSourceRule voices = this.config.getAllowedVoices();

        if (!this.config.isAllowed()) {
            return new VoiceChangerCodec.Policy(
                    false, PolicyReason.SERVER_DISABLED, this.config.getDeniedMessage(), voices);
        }
        if (this.mutes.isMuted(player.getUUID())) {
            return new VoiceChangerCodec.Policy(false, PolicyReason.PLAYER_MUTED, "", voices);
        }
        if (!ServerPermissions.canUse(player)) {
            return new VoiceChangerCodec.Policy(false, PolicyReason.NO_PERMISSION, "", voices);
        }
        return new VoiceChangerCodec.Policy(true, PolicyReason.ALLOWED, "", voices);
    }

    // --- Channel ------------------------------------------------------------

    /**
     * The client's greeting is the whole handshake: it proves the mod is there
     * and gives us a moment to answer that is guaranteed to be after the client
     * can receive on this channel.
     */
    private void onChannelMessage(ServerPlayer player, byte[] payload) {
        if (!isRunning() || VoiceChangerCodec.peekType(payload) != VoiceChangerChannel.TYPE_HELLO) {
            return;
        }

        try {
            VoiceChangerCodec.Hello hello = VoiceChangerCodec.decodeHello(payload);
            sendPolicy(player);
            sendPresets(player);

            SvVoiceChanger.LOGGER.debug("{} joined with voice changer {} (api {})",
                    player.getName().getString(), hello.modVersion(), hello.apiVersion());
        } catch (IOException exception) {
            // A client we cannot understand is one we cannot usefully answer.
            SvVoiceChanger.LOGGER.debug("Ignoring an unreadable voice changer greeting: {}",
                    exception.getMessage());
        }
    }

    private void sendPresets(ServerPlayer player) {
        if (!this.config.isSharePresets()) {
            return;
        }

        List<SharedPreset> presets = this.presetLibrary.presets();
        if (!presets.isEmpty()) {
            VoiceChangerNetwork.sendToPlayer(player, VoiceChangerCodec.encodePresets(presets));
        }
    }

    private void loadFromDisk() {
        try {
            this.config.load();
        } catch (IOException exception) {
            SvVoiceChanger.LOGGER.warn("Could not read the voice changer server config; using defaults", exception);
        }

        try {
            this.mutes.load();
        } catch (IOException exception) {
            SvVoiceChanger.LOGGER.warn("Could not read the voice changer mute list; starting empty", exception);
        }

        try {
            this.presetLibrary.reload();
        } catch (IOException exception) {
            SvVoiceChanger.LOGGER.warn("Could not read the shared voice folder; sharing nothing", exception);
        }
    }
}
