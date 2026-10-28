package org.sawiq.svvoicechanger.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;

/**
 * The studio's two tabs, the effect presets and the AI voice, for the header
 * row of {@link StudioFrame}.
 */
public final class StudioTabs {
    public enum Tab {
        EFFECTS("svvoicechanger.studio.tab.effects"),
        AI_VOICE("svvoicechanger.studio.tab.ai");

        private final String translationKey;

        Tab(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    private StudioTabs() {
    }

    static List<StudioFrame.Tab> of(Tab current, Consumer<Tab> open) {
        List<StudioFrame.Tab> tabs = new ArrayList<>();
        for (Tab tab : Tab.values()) {
            tabs.add(new StudioFrame.Tab(Component.translatable(tab.translationKey),
                    tab.translationKey + ".desc", tab == current, true, () -> open.accept(tab)));
        }
        return tabs;
    }
}
