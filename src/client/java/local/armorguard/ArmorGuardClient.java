package local.armorguard;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class ArmorGuardClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("armor_guard");
    private static ArmorGuardClient instance;
    private ConfigHolder<GuardConfig> configHolder;
    private boolean openConfig;
    private Object trackedPlayer;
    private long tick;
    private long nextWarningTick;
    private final long[] retryAfter = new long[4];

    @Override
    public void onInitializeClient() {
        instance = this;
        configHolder = AutoConfig.register(GuardConfig.class, GsonConfigSerializer::new);
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
            literal("armorguard")
                .then(literal("config").executes(context -> {
                    openConfig = true;
                    return 1;
                }))
                .then(literal("reload").executes(context -> {
                    configHolder.load();
                    message("armor_guard.reloaded");
                    return 1;
                }))
        ));
        LOGGER.info("Armor Guard loaded for Minecraft 26.3");
    }

    static ArmorGuardClient getInstance() {
        if (instance == null) throw new IllegalStateException("Armor Guard has not initialized");
        return instance;
    }

    GuardConfig getConfig() {
        return configHolder.getConfig();
    }

    void applyConfig(boolean enabled, int threshold) {
        GuardConfig config = getConfig();
        config.enabled = enabled;
        config.threshold = ProtectionPolicy.boundedThreshold(threshold);
        configHolder.save();
    }

    private void onTick(Minecraft client) {
        tick++;
        if (openConfig) {
            openConfig = false;
            client.gui.setScreen(GuardConfigScreen.create(null));
        }
        if (trackedPlayer != client.player) {
            trackedPlayer = client.player;
            java.util.Arrays.fill(retryAfter, 0L);
            nextWarningTick = 0;
        }
        GuardConfig config = getConfig();
        if (!config.enabled || client.player == null || client.gameMode == null || client.level == null) return;
        var player = client.player;
        if (!player.isAlive() || player.isSpectator() || player.getAbilities().instabuild) return;

        InventoryMenu menu = player.inventoryMenu;

        for (int index = 0; index < 4; index++) {
            if (tick < retryAfter[index]) continue;
            int armorSlot = 5 + index; // Vanilla InventoryMenu: head, chest, legs, feet.
            Slot source = menu.getSlot(armorSlot);
            ItemStack stack = source.getItem();
            if (stack.isEmpty() || !stack.isDamageableItem() || stack.is(Items.ELYTRA)) continue;
            if (!ProtectionPolicy.shouldUnequip(stack.getMaxDamage(), stack.getDamageValue(),
                    config.threshold)) continue;

            if (!source.mayPickup(player)) {
                retryAfter[index] = tick + 100;
                warn("armor_guard.cannot_remove", stack.getHoverName());
                continue;
            }
            if (!hasEmptyInventorySlot(menu, stack)) {
                warn("armor_guard.inventory_full");
                return;
            }

            // Armor slots only exist in the player's own menu. Vanilla rejects clicks
            // for that menu while another container is open, so close it when needed.
            if (player.containerMenu != menu) {
                var container = player.containerMenu;
                ItemStack carried = container.getCarried();
                if (!carried.isEmpty()) {
                    // Put the cursor stack away with a normal click before closing.
                    // This keeps vanilla's close flow from dropping it when full.
                    for (Slot destination : container.slots) {
                        if (destination.container == player.getInventory() && destination.getContainerSlot() >= 0
                                && destination.getContainerSlot() < 36 && !destination.hasItem() && destination.mayPlace(carried)) {
                            client.gameMode.handleContainerInput(container.containerId, destination.index, 0,
                                ContainerInput.PICKUP, player);
                            return;
                        }
                    }
                    warn("armor_guard.inventory_full");
                    return;
                }
                player.closeContainer();
            }

            Component itemName = stack.getHoverName();
            int remaining = stack.getMaxDamage() - stack.getDamageValue();
            // A normal shift-click uses vanilla's server-synced inventory transaction.
            // No direct equipment edits, cursor pickup sequence, or item-drop action.
            client.gameMode.handleContainerInput(menu.containerId, armorSlot, 0, ContainerInput.QUICK_MOVE, player);
            retryAfter[index] = tick + 10;
            if (source.getItem().isEmpty()) {
                message("armor_guard.saved", itemName, remaining);
                LOGGER.info("Unequipped {} at {} durability", itemName.getString(), remaining);
            } else {
                warn("armor_guard.rejected", itemName);
            }
            return; // At most one inventory transaction per client tick.
        }
    }

    private static boolean hasEmptyInventorySlot(InventoryMenu menu, ItemStack armor) {
        // Only the 36 ordinary inventory/hotbar slots; crafting/offhand are excluded.
        for (int slotId = 9; slotId <= 44; slotId++) {
            Slot destination = menu.getSlot(slotId);
            if (!destination.hasItem() && destination.mayPlace(armor)) return true;
        }
        return false;
    }

    private void warn(String key, Object... args) {
        if (tick >= nextWarningTick) {
            message(key, args);
            nextWarningTick = tick + 100;
        }
    }

    private static void message(String key, Object... args) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.sendOverlayMessage(Component.translatable(key, args));
    }
}
