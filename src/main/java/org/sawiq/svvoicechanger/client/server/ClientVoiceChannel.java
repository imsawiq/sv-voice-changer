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
//? if neoforge {
/*import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
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
            //? if neoforge {
            /*sendToServer(new VoiceChangerPayload(payload));
            *///?}
            return true;
        } catch (RuntimeException exception) {
            // The connection can drop between the check and the send.
            SvVoiceChanger.LOGGER.debug("Could not send on the voice changer channel", exception);
            return false;
        }
    }

    //? if neoforge {
    /*/^*
     * NeoForge moved the client-side send out of PacketDistributor and into
     * ClientPacketDistributor in 1.21.7, removing the old entry point at the
     * same time. One build of this mod covers 1.21 through 1.21.8, which sits
     * on both sides of that move, so neither class can be named at compile
     * time - doing that is what made this build crash on 1.21.1 while working
     * on 1.21.8.
     *
     * Resolved once when the class loads. The send itself is rare: a greeting
     * per connection and a reply to a policy change, never audio.^/
    private static final Method SEND_TO_SERVER = resolveSendToServer();

    private static Method resolveSendToServer() {
        String[] candidates = {
            "net.neoforged.neoforge.client.network.ClientPacketDistributor", // 1.21.7 and later
            "net.neoforged.neoforge.network.PacketDistributor",              // up to 1.21.6
        };

        for (String className : candidates) {
            try {
                Class<?> distributor = Class.forName(className);
                return distributor.getMethod(
                        "sendToServer", CustomPacketPayload.class, CustomPacketPayload[].class);
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                // Expected: only one of the two exists on any given version.
            }
        }

        SvVoiceChanger.LOGGER.error(
                "No NeoForge packet sender found; the voice changer cannot talk to the server");
        return null;
    }

    private static void sendToServer(VoiceChangerPayload payload) {
        if (SEND_TO_SERVER == null) {
            return;
        }

        try {
            SEND_TO_SERVER.invoke(null, payload, new CustomPacketPayload[0]);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Could not send on the voice changer channel", exception);
        }
    }
    *///?}
}
