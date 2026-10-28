package org.sawiq.svvoicechanger.client.ui.widget;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * A slider over a whole-number range, for the AI voice settings: pitch in
 * semitones, chunk length in milliseconds, ElevenLabs percentages.
 *
 * <p>Reports every step it lands on; the AI voice controller already waits
 * for a drag to settle before rebuilding anything.</p>
 */
public final class RangeSlider extends AbstractSliderButton {
    private final String labelKey;
    private final int min;
    private final int max;
    private final IntSupplier valueSupplier;
    private final IntConsumer valueConsumer;
    private final IntFunction<String> formatter;
    private boolean valueOnly;

    public RangeSlider(int x, int y, int width, int height, String labelKey, int min, int max,
                       IntFunction<String> formatter, IntSupplier valueSupplier, IntConsumer valueConsumer) {
        super(x, y, width, height, Component.translatable(labelKey), 0.0D);
        this.labelKey = labelKey;
        this.min = min;
        this.max = max;
        this.formatter = formatter;
        this.valueSupplier = valueSupplier;
        this.valueConsumer = valueConsumer;
        setTooltip(Tooltip.create(Component.translatable(labelKey + ".desc")));
        refresh();
    }

    public void refresh() {
        int current = Math.max(this.min, Math.min(this.max, this.valueSupplier.getAsInt()));
        this.value = (current - this.min) / (double) (this.max - this.min);
        updateMessage();
    }

    /** Shows just the value, for a settings row that already names it on the left. */
    public RangeSlider valueOnly() {
        this.valueOnly = true;
        updateMessage();
        return this;
    }

    @Override
    protected void updateMessage() {
        String value = this.formatter.apply(currentValue());
        setMessage(this.valueOnly
                ? Component.literal(value)
                : Component.translatable(this.labelKey).copy().append(": " + value));
    }

    @Override
    protected void applyValue() {
        this.valueConsumer.accept(currentValue());
    }

    private int currentValue() {
        return this.min + (int) Math.round(this.value * (this.max - this.min));
    }
}
