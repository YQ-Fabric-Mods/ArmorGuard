package local.armorguard;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class GuardConfigScreen {
    private GuardConfigScreen() {}

    public static Screen create(Screen parent) {
        ArmorGuardClient client = ArmorGuardClient.getInstance();
        GuardConfig current = client.getConfig();
        // Edit a draft so Cancel/Escape never changes the active configuration.
        GuardConfig draft = new GuardConfig();
        draft.enabled = current.enabled;
        draft.threshold = current.threshold;
        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.translatable("armor_guard.config.title"))
            .setSavingRunnable(() -> client.applyConfig(draft.enabled, draft.threshold));
        var category = builder.getOrCreateCategory(Component.translatable("armor_guard.config.general"));
        var entries = builder.entryBuilder();
        category.addEntry(entries.startBooleanToggle(Component.translatable("armor_guard.config.enabled"), draft.enabled)
            .setDefaultValue(true)
            .setSaveConsumer(value -> draft.enabled = value)
            .build());
        category.addEntry(entries.startIntField(Component.translatable("armor_guard.config.threshold"), draft.threshold)
            .setDefaultValue(GuardConfig.DEFAULT_THRESHOLD)
            .setMin(1)
            .setMax(10000)
            .setSaveConsumer(value -> draft.threshold = value)
            .build());
        return builder.build();
    }
}
