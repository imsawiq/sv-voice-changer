package org.sawiq.svvoicechanger.client;

import com.mojang.blaze3d.platform.InputConstants;
//? if >=1.21.9 {
/*import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
*///?}
import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.lwjgl.glfw.GLFW;
import org.sawiq.svvoicechanger.SvVoiceChanger;
import org.sawiq.svvoicechanger.client.ui.UpdateAvailableScreen;
import org.sawiq.svvoicechanger.client.ui.VoiceChangerStudioScreen;
import org.sawiq.svvoicechanger.client.update.ModrinthVersionChecker;

public final class VoiceChangerClientRuntime {
    public static final VoiceChangerClientRuntime INSTANCE = new VoiceChangerClientRuntime();
    //? if >=1.21.9 {
    /*private static final KeyMapping.Category KEY_MAPPING_CATEGORY = registerKeyMappingCategory();
    *///?}

    private final ModrinthVersionChecker versionChecker = new ModrinthVersionChecker();
    private KeyMapping toggleEffectKey;
    private KeyMapping openStudioKey;
    private ModrinthVersionChecker.Result pendingUpdate;
    private boolean updateScreenShown;
    private boolean initialized;

    private VoiceChangerClientRuntime() {
    }

    public void initialize(Path configDirectory) {
        if (this.initialized) {
            return;
        }

        VoiceChangerController.INSTANCE.initialize(configDirectory);
        this.toggleEffectKey = new KeyMapping(
                "key.svvoicechanger.toggle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                //? if >=1.21.9 {
                /*KEY_MAPPING_CATEGORY
                *///?} else {
                "category.svvoicechanger"
                //?}
        );
        this.openStudioKey = new KeyMapping(
                "key.svvoicechanger.open_studio",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                //? if >=1.21.9 {
                /*KEY_MAPPING_CATEGORY
                *///?} else {
                "category.svvoicechanger"
                //?}
        );
        startUpdateCheck();
        this.initialized = true;
        SvVoiceChanger.LOGGER.info("Initialized Simple Voice Voice Changer client runtime");
    }

    public void registerHotkeys(Consumer<KeyMapping> registrar) {
        requireInitialized();
        registrar.accept(this.toggleEffectKey);
        registrar.accept(this.openStudioKey);
    }

    public void tick(Minecraft client) {
        if (!this.initialized) {
            return;
        }

        VoiceChangerController controller = VoiceChangerController.INSTANCE;
        controller.tick();
        maybeShowUpdateScreen(client);

        while (this.toggleEffectKey.consumeClick()) {
            if (MinecraftScreenAccess.current(client) == null) {
                controller.toggleEnabled();
            }
        }

        while (this.openStudioKey.consumeClick()) {
            if (MinecraftScreenAccess.current(client) == null) {
                MinecraftScreenAccess.show(client, new VoiceChangerStudioScreen(null, controller));
            }
        }
    }

    public void shutdown() {
        if (!this.initialized) {
            return;
        }

        VoiceChangerController.INSTANCE.shutdown();
        this.pendingUpdate = null;
        this.updateScreenShown = false;
        this.initialized = false;
    }

    private void startUpdateCheck() {
        this.versionChecker.checkAsync().thenAccept(result -> {
            if (result == null) {
                return;
            }

            Minecraft client = Minecraft.getInstance();
            if (client == null) {
                return;
            }

            client.execute(() -> this.pendingUpdate = result);
        });
    }

    private void maybeShowUpdateScreen(Minecraft client) {
        if (this.pendingUpdate == null || this.updateScreenShown) {
            return;
        }

        if (!(MinecraftScreenAccess.current(client) instanceof TitleScreen titleScreen)) {
            return;
        }

        this.updateScreenShown = true;
        ModrinthVersionChecker.Result update = this.pendingUpdate;
        this.pendingUpdate = null;
        MinecraftScreenAccess.show(
                client,
                new UpdateAvailableScreen(
                        titleScreen,
                        update.version(),
                        this.versionChecker.currentVersion(),
                        update.url()
                )
        );
    }

    private void requireInitialized() {
        if (!this.initialized) {
            throw new IllegalStateException("Voice changer client runtime is not initialized");
        }
    }

    //? if >=1.21.9 {
    /*private static KeyMapping.Category registerKeyMappingCategory() {
        for (Method method : KeyMapping.Category.class.getDeclaredMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (Modifier.isStatic(method.getModifiers())
                    && method.getReturnType() == KeyMapping.Category.class
                    && parameters.length == 1
                    && parameters[0] != String.class) {
                Object identifier = MinecraftResourceAccess.create(
                        parameters[0],
                        SvVoiceChanger.MOD_ID,
                        "voice_changer"
                );
                try {
                    method.trySetAccessible();
                    return (KeyMapping.Category) method.invoke(null, identifier);
                } catch (IllegalAccessException | InvocationTargetException exception) {
                    throw new IllegalStateException("Unable to register the voice changer key category", exception);
                }
            }
        }

        throw new IllegalStateException("Unable to find the Minecraft key category registration method");
    }
    *///?}
}
