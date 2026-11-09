package local.armorguard;

/** Pure durability policy shared by the client and the executable checks. */
public final class ProtectionPolicy {
    private ProtectionPolicy() {}

    public static int boundedThreshold(int threshold) {
        return Math.clamp(threshold, 1, 10000);
    }

    public static boolean shouldUnequip(int maximum, int damage, int threshold) {
        return maximum > 0 && maximum - Math.max(0, damage) <= boundedThreshold(threshold);
    }
}
