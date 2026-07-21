package org.sawiq.svvoicechanger.client.ui;

import java.awt.Desktop;
import java.net.URI;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.sawiq.svvoicechanger.SvVoiceChanger;
import org.sawiq.svvoicechanger.client.MinecraftScreenAccess;

/**
 * Lightweight update prompt shown once on the title screen.
 * Uses only widgets so it stays compatible across 1.21.x / 26.x and both loaders.
 */
public final class UpdateAvailableScreen extends Screen {
    private final Screen parent;
    private final String newVersion;
    private final String currentVersion;
    private final String url;

    public UpdateAvailableScreen(Screen parent, String newVersion, String currentVersion, String url) {
        super(Component.translatable("svvoicechanger.update.title"));
        this.parent = parent;
        this.newVersion = newVersion;
        this.currentVersion = currentVersion;
        this.url = url;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Read-only rows: inactive buttons act as centered labels on every MC version.
        addLabel(
                Component.translatable("svvoicechanger.update.subtitle", this.newVersion),
                centerX - 140,
                centerY - 36,
                280
        );
        addLabel(
                Component.translatable("svvoicechanger.update.current", this.currentVersion),
                centerX - 140,
                centerY - 12,
                280
        );

        addRenderableWidget(Button.builder(Component.translatable("svvoicechanger.update.open_page"), button -> openPage())
                .bounds(centerX - 100, centerY + 24, 200, 20)
                .build());

        addRenderableWidget(Button.builder(Component.translatable("svvoicechanger.update.dismiss"), button -> onClose())
                .bounds(centerX - 100, centerY + 50, 200, 20)
                .build());
    }

    @Override
    public void onClose() {
        if (this.minecraft == null) {
            return;
        }
        MinecraftScreenAccess.show(this.minecraft, this.parent);
    }

    private void addLabel(Component text, int x, int y, int width) {
        Button label = Button.builder(text, button -> {
                })
                .bounds(x, y, width, 20)
                .build();
        label.active = false;
        addRenderableWidget(label);
    }

    private void openPage() {
        try {
            Desktop.getDesktop().browse(URI.create(this.url));
        } catch (Exception exception) {
            SvVoiceChanger.LOGGER.warn("Unable to open the Modrinth update page", exception);
        }
    }
}
