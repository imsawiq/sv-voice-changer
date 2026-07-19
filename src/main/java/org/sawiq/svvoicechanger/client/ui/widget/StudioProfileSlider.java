package org.sawiq.svvoicechanger.client.ui.widget;

import java.text.DecimalFormat;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import org.sawiq.svvoicechanger.client.model.VoiceChangerProfile;

public final class StudioProfileSlider extends AbstractSliderButton {
    private static final DecimalFormat DECIMAL = new DecimalFormat("0.00");

    private final String label;
    private final String description;
    private final double min;
    private final double max;
    private final Supplier<VoiceChangerProfile> profileSupplier;
    private final Consumer<VoiceChangerProfile> profileConsumer;
    private final Function<VoiceChangerProfile, Number> getter;
    private final BiFunction<VoiceChangerProfile, Double, VoiceChangerProfile> setter;

    public StudioProfileSlider(
            int x,
            int y,
            int width,
            String label,
            String description,
            double min,
            double max,
            Supplier<VoiceChangerProfile> profileSupplier,
            Consumer<VoiceChangerProfile> profileConsumer,
            Function<VoiceChangerProfile, Number> getter,
            BiFunction<VoiceChangerProfile, Double, VoiceChangerProfile> setter
    ) {
        super(x, y, width, 20, Component.translatable(label), 0.0D);
        this.label = label;
        this.description = description;
        this.min = min;
        this.max = max;
        this.profileSupplier = profileSupplier;
        this.profileConsumer = profileConsumer;
        this.getter = getter;
        this.setter = setter;
        refresh(profileSupplier.get());
    }

    public void refresh(VoiceChangerProfile profile) {
        double current = this.getter.apply(profile).doubleValue();
        if (!Double.isFinite(current)) {
            current = this.min;
        }

        this.value = Math.max(0.0D, Math.min(1.0D, (current - this.min) / (this.max - this.min)));
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        double realValue = this.min + (this.max - this.min) * this.value;
        this.setMessage(Component.literal(Component.translatable(this.label).getString() + ": " + DECIMAL.format(realValue)));
    }

    @Override
    protected void applyValue() {
        double realValue = this.min + (this.max - this.min) * this.value;
        if (!Double.isFinite(realValue)) {
            realValue = this.min;
        }

        this.profileConsumer.accept(this.setter.apply(this.profileSupplier.get(), realValue));
    }

    public Component descriptionText() {
        return Component.translatable(this.description);
    }
}

