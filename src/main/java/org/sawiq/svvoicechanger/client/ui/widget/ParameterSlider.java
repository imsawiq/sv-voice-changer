package org.sawiq.svvoicechanger.client.ui.widget;

import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.sawiq.svvoicechanger.client.model.VoiceParameter;
import org.sawiq.svvoicechanger.client.model.VoiceProfile;

/**
 * Slider bound to one {@link VoiceParameter}.
 *
 * <p>Range, label, unit and formatting all come from the parameter itself, so
 * a knob cannot end up with a slider range that disagrees with what the audio
 * engine and the preset files accept. The previous studio repeated those
 * numbers at every call site.</p>
 */
public final class ParameterSlider extends AbstractSliderButton {
    private final VoiceParameter parameter;
    private final Supplier<VoiceProfile> profileSupplier;
    private final Consumer<VoiceProfile> profileConsumer;

    public ParameterSlider(
            int x,
            int y,
            int width,
            int height,
            VoiceParameter parameter,
            Supplier<VoiceProfile> profileSupplier,
            Consumer<VoiceProfile> profileConsumer
    ) {
        super(x, y, width, height, Component.translatable(parameter.translationKey()), 0.0D);
        this.parameter = parameter;
        this.profileSupplier = profileSupplier;
        this.profileConsumer = profileConsumer;
        setTooltip(Tooltip.create(Component.translatable(parameter.descriptionKey())));
        refresh(profileSupplier.get());
    }

    /**
     * Moves the handle to match the profile without reporting a change back,
     * which is what makes preset switching update the sliders instead of the
     * sliders overwriting the freshly loaded preset.
     */
    public void refresh(VoiceProfile profile) {
        this.value = toSliderPosition(profile.get(this.parameter));
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        Component label = Component.translatable(this.parameter.translationKey());
        setMessage(Component.literal(label.getString() + ": " + this.parameter.formatValue(currentValue())));
    }

    @Override
    protected void applyValue() {
        this.profileConsumer.accept(this.profileSupplier.get().with(this.parameter, currentValue()));
    }

    private double currentValue() {
        double range = this.parameter.max() - this.parameter.min();
        return this.parameter.sanitize(this.parameter.min() + range * this.value);
    }

    private double toSliderPosition(double value) {
        double range = this.parameter.max() - this.parameter.min();
        if (range <= 0.0D) {
            return 0.0D;
        }

        double position = (value - this.parameter.min()) / range;
        return Math.max(0.0D, Math.min(1.0D, position));
    }
}
