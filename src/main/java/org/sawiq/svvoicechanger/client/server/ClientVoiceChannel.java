package org.sawiq.svvoicechanger.client.server;

import java.util.function.Consumer;
import org.sawiq.svvoicechanger.SvVoiceChanger;
import org.sawiq.svvoicechanger.network.VoiceChangerNetwork;
import org.sawiq.svvoicechanger.network.VoiceChangerPayload;

//? if fabric {
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
//?} else {
/*import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
*///?}
//? if neoforge && >=1.21.7 {
/*import net.neoforged.neoforge.client.network.ClientPacketDistributor;
*///?}
//? if neoforge && <1.21.7 {
/*import net.neoforged.neoforge.network.PacketDistributor;
*///?}

/** The client end of the channel the server sends its policy over. */
public final class ClientVoiceChannel {
    private ClientVoiceChannel() {
    }

    /**
     * Starts listening. The packet type itself is declared by
     * {@link VoiceChangerNetwork}, from common code, because a type registered
     * on one side only is one neither side can use.
     */
    public static void initialize(Consumer<byte[]> receiver) {
        //? if fabric {
        ClientPlayNetworking.registerGlobalReceiver(VoiceChangerPayload.TYPE, (payload, context) -> {
            byte[] data = payload.data();
            // Arrives on a network thread; everything it touches is client state.
            context.client().execute(() -> receiver.accept(data));
        });
        //?} else {
        /*VoiceChangerNetwork.setClientReceiver(receiver);
        *///?}
    }

    /** Whether there is a server on the other end that speaks this channel. */
    public static boolean isConnected() {
        //? if fabric {
        return ClientPlayNetworking.canSend(VoiceChangerPayload.TYPE);
        //?} else {
        /*Minecraft client = Minecraft.getInstance();
        return client.getConnection() != null
                && NetworkRegistry.hasChannel(client.getConnection(), VoiceChangerPayload.TYPE.id());
        *///?}
    }

    /** @return whether the message was handed to the network layer */
    public static boolean send(byte[] payload) {
        if (!isConnected()) {
            return false;
        }

        try {
            //? if fabric {
            ClientPlayNetworking.send(new VoiceChangerPayload(payload));
            //?}
            //? if neoforge && >=1.21.7 {
            /*ClientPacketDistributor.sendToServer(new VoiceChangerPayload(payload));
            *///?}
            //? if neoforge && <1.21.7 {
            /*PacketDistributor.sendToServer(new VoiceChangerPayload(payload));
            *///?}
            return true;
        } catch (RuntimeException exception) {
            // The connection can drop between the check and the send.
            SvVoiceChanger.LOGGER.debug("Could not send on the voice changer channel", exception);
            return false;
        }
    }
}
