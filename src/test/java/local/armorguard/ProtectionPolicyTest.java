package local.armorguard;

public final class ProtectionPolicyTest {
    private static int checks;

    public static void main(String[] args) {
        for (int maximum : new int[]{165, 240, 225, 195, 528}) {
            verify(!ProtectionPolicy.shouldUnequip(maximum, maximum - 21, 20), "21 durability stays equipped regardless of material");
            verify(ProtectionPolicy.shouldUnequip(maximum, maximum - 20, 20), "20 durability reaches the inclusive boundary");
        }
        verify(ProtectionPolicy.shouldUnequip(165, 150, 20), "damage jumping below threshold still triggers");
        verify(!ProtectionPolicy.shouldUnequip(165, 0, 20), "new armor stays equipped");
        verify(!ProtectionPolicy.shouldUnequip(0, 0, 20), "non-damageable items ignored");
        verify(!ProtectionPolicy.shouldUnequip(165, 140, 20), "old percentage threshold no longer applies");
        verify(ProtectionPolicy.shouldUnequip(165, 140, 25), "custom point threshold applies");
        verify(ProtectionPolicy.boundedThreshold(0) == 1, "invalid low threshold bounded");
        verify(ProtectionPolicy.boundedThreshold(10001) == 10000, "invalid high threshold bounded");
        System.out.println("Passed " + checks + " absolute durability policy checks.");
    }

    private static void verify(boolean condition, String description) {
        checks++;
        if (!condition) throw new AssertionError(description);
    }
}
