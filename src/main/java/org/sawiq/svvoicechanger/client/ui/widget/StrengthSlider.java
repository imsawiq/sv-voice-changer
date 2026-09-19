package org.sawiq.svvoicechanger.client.ui.widget;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * The one control the simple mode is built around: how strongly the selected
 * voice is applied, from untouched at 0% to the full preset at 100%.
 */
public final class StrengthSlider extends AbstractSliderButton {
    private static final String LABEL_KEY = "svvoicechanger.slider.strength";

    private final IntSupplier valueSupplier;
    private final IntConsumer valueConsumer;

    public StrengthSlider(int x, int y, int width, int height, IntSupplier valueSupplier, IntConsumer valueConsumer) {
        super(x, y, width, height, Component.translatable(LABEL_KEY), 0.0D);
        this.valueSupplier = valueSupplier;
        this.valueConsumer = valueConsumer;
        setTooltip(Tooltip.create(Component.translatable(LABEL_KEY + ".desc")));
        refresh();
    }

    public void refresh() {
        this.value = Math.max(0.0D, Math.min(1.0D, this.valueSupplier.getAsInt() / 100.0D));
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.translatable(LABEL_KEY).copy().append(": " + percent() + "%"));
    }

    @Override
    protected void applyValue() {
        this.valueConsumer.accept(percent());
    }

    private int percent() {
        return (int) Math.round(this.value * 100.0D);
    }
}
