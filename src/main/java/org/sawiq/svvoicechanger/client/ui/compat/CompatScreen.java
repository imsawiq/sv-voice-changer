package org.sawiq.svvoicechanger.client.ui.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21.9 {
/*import net.minecraft.client.input.KeyEvent;
*///?}
import net.minecraft.network.chat.Component;

/**
 * Screen base class for the AI voice screens, hiding the version-specific
 * render and key-event signatures behind three stable hooks. The studio
 * itself predates it and handles the versions inline.
 */
public abstract class CompatScreen extends Screen {
    protected CompatScreen(Component title) {
        super(title);
    }

    @Override
    //? if >=26.1 {
    /*public void extractRenderState(GuiGraphics context, int mouseX, int mouseY, float delta) {
    *///?} else {
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
    //?}
        StudioGraphics graphics = new StudioGraphics(context, this.font);
        paintBehindWidgets(graphics);
        //? if >=26.1 {
        /*super.extractRenderState(context, mouseX, mouseY, delta);
        *///?} else {
        super.render(context, mouseX, mouseY, delta);
        //?}
        paintAboveWidgets(graphics, mouseX, mouseY);
    }

    //? if >=1.21.9 {
    /*@Override
    public boolean keyPressed(KeyEvent event) {
        return onKeyPressed(event.key()) || super.keyPressed(event);
    }
    *///?} else {
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return onKeyPressed(keyCode) || super.keyPressed(keyCode, scanCode, modifiers);
    }
    //?}

    /** Drawn before the widgets: backdrops, panels, section headings. */
    protected void paintBehindWidgets(StudioGraphics graphics) {
    }

    /** Drawn after the widgets: scrollbars and status lines. */
    protected void paintAboveWidgets(StudioGraphics graphics, int mouseX, int mouseY) {
    }

    /** @return true when the key was handled and should not fall through */
    protected boolean onKeyPressed(int keyCode) {
        return false;
    }
}
