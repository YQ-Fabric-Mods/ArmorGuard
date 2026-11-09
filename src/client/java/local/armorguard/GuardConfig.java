package local.armorguard;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

@Config(name = "armor-guard")
public final class GuardConfig implements ConfigData {
    static final int DEFAULT_THRESHOLD = 20;
    public boolean enabled = true;
    public int threshold = DEFAULT_THRESHOLD;

    @Override
    public void validatePostLoad() {
        threshold = ProtectionPolicy.boundedThreshold(threshold);
    }
}
