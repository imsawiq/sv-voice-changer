package org.sawiq.svvoicechanger.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the messages into bytes and back.
 *
 * <p>Decoding treats everything it reads as hostile, because the other end of
 * a plugin channel is a machine somebody else administers. Every count is
 * bounded before it is used to size anything, every string has a length limit,
 * and anything unrecognised is refused rather than skipped: a message this
 * version cannot fully understand is not one it should act on half of.</p>
 */
public final class VoiceChangerCodec {
    /** Enough for a full library while staying far below the message cap. */
    private static final int MAX_PRESETS = 64;
    private static final int MAX_VALUES_PER_PRESET = 64;
    private static final int MAX_REASON_LENGTH = 256;
    private static final int MAX_VERSION_LENGTH = 32;

    private VoiceChangerCodec() {
    }

    // --- Serverbound ---------------------------------------------------------

    /** The client announcing itself, so the server knows the policy will be obeyed. */
    public record Hello(int protocolVersion, int apiVersion, String modVersion) {
    }

    public static byte[] encodeHello(Hello hello) {
        return write(out -> {
            out.writeByte(VoiceChangerChannel.TYPE_HELLO);
            out.writeInt(hello.protocolVersion());
            out.writeInt(hello.apiVersion());
            out.writeUTF(truncate(hello.modVersion(), MAX_VERSION_LENGTH));
        });
    }

    // --- Clientbound ---------------------------------------------------------

    /**
     * @param reason  why, so the client can say it in the player's language
     * @param message the server's own wording, which wins over {@code reason}
     *                when it is not empty
     * @param voices  how much freedom the player has over their voice here
     */
    public record Policy(boolean allowed, PolicyReason reason, String message, VoiceSourceRule voices) {
    }

    public static byte[] encodePolicy(Policy policy) {
        return write(out -> {
            out.writeByte(VoiceChangerChannel.TYPE_POLICY);
            out.writeBoolean(policy.allowed());
            out.writeInt(policy.reason().code());
            out.writeUTF(truncate(policy.message(), MAX_REASON_LENGTH));
            out.writeInt(policy.voices().code());
        });
    }

    public static byte[] encodePresets(List<SharedPreset> presets) {
        List<SharedPreset> capped = presets.size() > MAX_PRESETS
                ? presets.subList(0, MAX_PRESETS)
                : presets;

        return write(out -> {
            out.writeByte(VoiceChangerChannel.TYPE_PRESETS);
            out.writeInt(capped.size());
            for (SharedPreset preset : capped) {
                out.writeUTF(preset.id());
                out.writeUTF(preset.name());

                Map<String, Double> values = preset.values();
                out.writeInt(values.size());
                for (Map.Entry<String, Double> value : values.entrySet()) {
                    out.writeUTF(value.getKey());
                    out.writeDouble(value.getValue());
                }
            }
        });
    }

    // --- Decoding ------------------------------------------------------------

    /** @return the message type, or -1 when the payload is unusable */
    public static int peekType(byte[] payload) {
        if (payload == null || payload.length == 0 || payload.length > VoiceChangerChannel.MAX_MESSAGE_BYTES) {
            return -1;
        }
        return payload[0];
    }

    /**
     * @throws IOException if the payload is truncated, oversized, or claims a
     *                     protocol this build does not speak
     */
    public static Hello decodeHello(byte[] payload) throws IOException {
        try (DataInputStream in = open(payload, VoiceChangerChannel.TYPE_HELLO)) {
            int protocolVersion = in.readInt();
            int apiVersion = in.readInt();
            String modVersion = in.readUTF();

            if (protocolVersion != VoiceChangerChannel.PROTOCOL_VERSION) {
                throw new IOException("Unsupported protocol version " + protocolVersion);
            }
            return new Hello(protocolVersion, apiVersion, truncate(modVersion, MAX_VERSION_LENGTH));
        }
    }

    public static Policy decodePolicy(byte[] payload) throws IOException {
        try (DataInputStream in = open(payload, VoiceChangerChannel.TYPE_POLICY)) {
            boolean allowed = in.readBoolean();
            PolicyReason reason = PolicyReason.byCode(in.readInt());
            String message = truncate(in.readUTF(), MAX_REASON_LENGTH);
            VoiceSourceRule voices = VoiceSourceRule.byCode(in.readInt());
            return new Policy(allowed, reason, message, voices);
        }
    }

    /** Drops presets that fail validation rather than refusing the whole list. */
    public static List<SharedPreset> decodePresets(byte[] payload) throws IOException {
        try (DataInputStream in = open(payload, VoiceChangerChannel.TYPE_PRESETS)) {
            int count = in.readInt();
            if (count < 0 || count > MAX_PRESETS) {
                throw new IOException("Preset count out of range: " + count);
            }

            List<SharedPreset> presets = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String id = in.readUTF();
                String name = truncate(in.readUTF(), SharedPreset.MAX_NAME_LENGTH);

                int valueCount = in.readInt();
                if (valueCount < 0 || valueCount > MAX_VALUES_PER_PRESET) {
                    throw new IOException("Parameter count out of range: " + valueCount);
                }

                Map<String, Double> values = new LinkedHashMap<>();
                for (int v = 0; v < valueCount; v++) {
                    String parameterId = in.readUTF();
                    double value = in.readDouble();
                    if (Double.isFinite(value)) {
                        values.put(parameterId, value);
                    }
                }

                if (SharedPreset.isValidId(id) && !name.isBlank()) {
                    presets.add(new SharedPreset(id, name, SharedPreset.sanitizeValues(values)));
                }
            }
            return List.copyOf(presets);
        }
    }

    // --- Plumbing ------------------------------------------------------------

    private static DataInputStream open(byte[] payload, byte expectedType) throws IOException {
        if (payload == null || payload.length == 0) {
            throw new IOException("Empty payload");
        }
        if (payload.length > VoiceChangerChannel.MAX_MESSAGE_BYTES) {
            throw new IOException("Payload of " + payload.length + " bytes is too large");
        }

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        byte type = in.readByte();
        if (type != expectedType) {
            in.close();
            throw new IOException("Expected message type " + expectedType + " but found " + type);
        }
        return in;
    }

    private interface Body {
        void write(DataOutputStream out) throws IOException;
    }

    /**
     * Writing into memory cannot fail for any reason the caller could act on,
     * so the checked exception is not passed on to every call site.
     */
    private static byte[] write(Body body) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            body.write(out);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to encode a voice changer message", exception);
        }
        return bytes.toByteArray();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
