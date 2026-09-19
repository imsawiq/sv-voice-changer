package org.sawiq.svvoicechanger.client.api.internal;

import java.util.Optional;
import org.sawiq.svvoicechanger.client.VoiceChangerController;
import org.sawiq.svvoicechanger.client.api.VoiceChangerApi;
import org.sawiq.svvoicechanger.client.api.VoiceChangerListener;
import org.sawiq.svvoicechanger.client.api.VoiceOverride;
import org.sawiq.svvoicechanger.client.api.VoicePreset;
import org.sawiq.svvoicechanger.client.model.ActiveVoice;
import org.sawiq.svvoicechanger.client.model.VoiceChangerPreset;
import org.sawiq.svvoicechanger.client.model.VoiceProfile;

/**
 * Adapts the controller to the published contract.
 *
 * <p>Deliberately thin: the override stack, the contributed voices and the
 * listeners all live on the controller, because the studio and the audio path need
 * them too. Keeping a second copy of that state behind the API is how the two
 * would drift apart.</p>
 */
public final class VoiceChangerApiImpl implements VoiceChangerApi {
    private final VoiceChangerController controller;

    public VoiceChangerApiImpl(VoiceChangerController controller) {
        this.controller = controller;
    }

    @Override
    public boolean isEffectEnabled() {
        return this.controller.isEffectEnabled();
    }

    @Override
    public boolean isAllowed() {
        return this.controller.isAllowedByServer();
    }

    @Override
    public int getStrength() {
        return this.controller.getStrength();
    }

    @Override
    public String getSelectedVoiceId() {
        return this.controller.getSelectedVoiceId();
    }

    @Override
    public Optional<VoiceProfile> getActiveProfile() {
        ActiveVoice voice = this.controller.getActiveVoice();
        return voice.active() ? Optional.of(voice.profile()) : Optional.empty();
    }

    @Override
    public VoiceProfile getPlayerProfile() {
        return this.controller.getPlayerProfile();
    }

    @Override
    public VoiceOverride pushOverride(VoiceOverride.Request request) {
        return this.controller.api().overrides().push(request);
    }

    @Override
    public void releaseOverrides(String ownerId) {
        this.controller.api().overrides().releaseOwner(ownerId);
    }

    @Override
    public Optional<VoiceOverride> getActiveOverride() {
        return this.controller.api().overrides().winning();
    }

    @Override
    public void registerPreset(VoicePreset preset) {
        this.controller.api().contributedPresets().register(preset);
    }

    @Override
    public void unregisterPreset(String presetId) {
        this.controller.api().contributedPresets().unregister(presetId);
    }

    @Override
    public Optional<VoiceProfile> builtInProfile(String builtInId) {
        return VoiceChangerPreset.byKey(builtInId).map(VoiceChangerPreset::profile);
    }

    @Override
    public void addListener(VoiceChangerListener listener) {
        this.controller.api().listeners().add(listener);
    }

    @Override
    public void removeListener(VoiceChangerListener listener) {
        this.controller.api().listeners().remove(listener);
    }
}
