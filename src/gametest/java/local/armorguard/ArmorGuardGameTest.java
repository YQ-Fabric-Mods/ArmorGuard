package local.armorguard;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.clothconfig2.gui.ClothConfigScreen;
import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import me.shedaniel.clothconfig2.gui.entries.IntegerListEntry;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@SuppressWarnings("UnstableApiUsage")
public final class ArmorGuardGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try {
            verifyClothConfig(context);
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            TestServerContext server = world.getServer();
            context.waitFor(client -> client.player != null && client.gui.screen() == null);
            server.runOnServer(game -> {
                ServerPlayer player = game.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SURVIVAL);
                player.getInventory().clearContent();
                player.setItemSlot(EquipmentSlot.HEAD, helmet(144));
                player.inventoryMenu.broadcastChanges();
            });
            context.waitTicks(10);
            check(server.computeOnServer(game -> !first(game).getItemBySlot(EquipmentSlot.HEAD).isEmpty()),
                "Healthy helmet above threshold must stay equipped");

            changeSettings(context, false, 20);
            server.runOnServer(game -> {
                first(game).setItemSlot(EquipmentSlot.HEAD, helmet(145));
                first(game).inventoryMenu.broadcastChanges();
            });
            context.waitTicks(10);
            check(server.computeOnServer(game -> !first(game).getItemBySlot(EquipmentSlot.HEAD).isEmpty()),
                "Disabled setting from Cloth Config must stop automatic unequipping");
            changeSettings(context, true, 20);
            context.waitTicks(20);
            server.runOnServer(game -> {
                ServerPlayer player = first(game);
                check(player.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "Low helmet must leave armor slot on the server");
                check(countHelmets(player) == 1, "Exactly one helmet must remain in the inventory");
                check(findHelmet(player).getDamageValue() == 145, "Damage value must be preserved");
            });

            server.runOnServer(game -> {
                ServerPlayer player = first(game);
                player.getInventory().clearContent();
                for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
                player.setItemSlot(EquipmentSlot.HEAD, helmet(145));
                player.inventoryMenu.broadcastChanges();
            });
            context.waitTicks(20);
            check(server.computeOnServer(game -> !first(game).getItemBySlot(EquipmentSlot.HEAD).isEmpty()),
                "Full inventory must leave armor equipped");
            server.runOnServer(game -> {
                first(game).getInventory().setItem(15, ItemStack.EMPTY);
                first(game).inventoryMenu.broadcastChanges();
            });
            context.waitTicks(20);
            server.runOnServer(game -> {
                check(first(game).getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "Making room must resume protection");
                check(countHelmets(first(game)) == 1, "Full inventory recovery must preserve exactly one helmet");
            });

            server.runOnServer(game -> {
                ServerPlayer player = first(game);
                player.getInventory().clearContent();
                ItemStack bound = helmet(145);
                bound.enchant(game.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.BINDING_CURSE), 1);
                player.setItemSlot(EquipmentSlot.HEAD, bound);
                player.inventoryMenu.broadcastChanges();
            });
            context.waitTicks(20);
            check(server.computeOnServer(game -> !first(game).getItemBySlot(EquipmentSlot.HEAD).isEmpty()),
                "Binding curse must remain governed by vanilla pickup rules");

            context.setScreen(() -> new InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
            server.runOnServer(game -> {
                first(game).setItemSlot(EquipmentSlot.HEAD, helmet(145));
                first(game).inventoryMenu.broadcastChanges();
            });
            context.waitTicks(110);
            check(server.computeOnServer(game -> first(game).getItemBySlot(EquipmentSlot.HEAD).isEmpty()),
                "Open inventory screen must allow automation");
            context.setScreen(() -> null);
            context.waitTicks(20);
            check(server.computeOnServer(game -> first(game).getItemBySlot(EquipmentSlot.HEAD).isEmpty()),
                "Armor must remain unequipped after closing the screen");

            server.runOnServer(game -> {
                ServerPlayer player = first(game);
                player.getInventory().clearContent();
                player.setItemSlot(EquipmentSlot.HEAD, helmet(145));
                player.setItemSlot(EquipmentSlot.CHEST, worn(Items.CHAINMAIL_CHESTPLATE, 220));
                player.setItemSlot(EquipmentSlot.LEGS, worn(Items.CHAINMAIL_LEGGINGS, 205));
                player.setItemSlot(EquipmentSlot.FEET, worn(Items.CHAINMAIL_BOOTS, 175));
                player.inventoryMenu.broadcastChanges();
            });
            context.waitTicks(20);
            server.runOnServer(game -> {
                ServerPlayer player = first(game);
                for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    check(player.getItemBySlot(slot).isEmpty(), "Every worn armor slot must be cleared");
                }
                int saved = 0;
                for (int slot = 0; slot < 36; slot++) if (!player.getInventory().getItem(slot).isEmpty()) saved++;
                check(saved == 4, "All four armor pieces must remain in the backpack");
                player.setItemSlot(EquipmentSlot.CHEST, worn(Items.ELYTRA, 430));
                player.inventoryMenu.broadcastChanges();
            });
            context.waitTicks(20);
            check(server.computeOnServer(game -> first(game).getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)),
                "Elytra must never be removed");
            context.takeScreenshot("armor-guard-verified");
            System.out.println("Armor Guard 1.1: Auto Config persistence, Cloth Config, toggles, 20-point boundary, and client/server inventory scenarios passed.");
        }
    }

    private static void verifyClothConfig(ClientGameTestContext context) throws java.io.IOException {
        context.runOnClient(client -> {
            var holder = AutoConfig.getConfigHolder(GuardConfig.class);
            holder.resetToDefault();
            holder.save();
            JsonObject values = readSettings();
            check(values.get("enabled").getAsBoolean() && values.get("threshold").getAsInt() == 20,
                "Auto Config must write the default settings");
        });
        context.setScreen(() -> new ModMenuIntegration().getModConfigScreenFactory().create(null));
        context.waitTicks(2);
        context.takeScreenshot("armor-guard-cloth-config");
        context.runOnClient(client -> {
            ClothConfigScreen screen = (ClothConfigScreen) client.gui.screen();
            var entries = screen.getCategorizedEntries().values().stream().flatMap(java.util.Collection::stream).toList();
            check(entries.size() == 2, "Cloth Config must expose exactly two settings");
            BooleanListEntry toggle = (BooleanListEntry) entries.get(0);
            IntegerListEntry threshold = (IntegerListEntry) entries.get(1);
            check(toggle.getValue() && threshold.getValue() == 20, "Default settings must be ON and 20 points");
            check(threshold.getDefaultValue().orElseThrow() == 20, "Reset value must be 20 points");
            ((EditBox) threshold.children().stream().filter(EditBox.class::isInstance).findFirst().orElseThrow()).setValue("37");
            check(ArmorGuardClient.getInstance().getConfig().threshold == 20, "Unsaved edit must not affect active config");
        });
        context.setScreen(() -> null);
        context.runOnClient(client -> check(ArmorGuardClient.getInstance().getConfig().threshold == 20,
            "Closing without saving must discard edits"));
        changeSettings(context, false, 33);
        context.runOnClient(client -> {
            JsonObject values = readSettings();
            check(values.size() == 2 && values.has("enabled") && values.has("threshold"),
                "Saved configuration must contain exactly the two current keys");
            check(!values.get("enabled").getAsBoolean() && values.get("threshold").getAsInt() == 33,
                "Both Cloth Config settings must persist as JSON");
            var holder = AutoConfig.getConfigHolder(GuardConfig.class);
            holder.resetToDefault();
            check(holder.load(), "Auto Config must reload saved settings");
            GuardConfig saved = ArmorGuardClient.getInstance().getConfig();
            check(!saved.enabled && saved.threshold == 33, "Reload must replace the active configuration");

            var path = FabricLoader.getInstance().getConfigDir().resolve("armor-guard.json");
            Files.writeString(path, "{\"threshold\": 0}", StandardCharsets.UTF_8);
            check(holder.load() && ArmorGuardClient.getInstance().getConfig().threshold == 1,
                "Reloaded threshold must respect the lower bound");
            Files.writeString(path, "{\"threshold\": 10001}", StandardCharsets.UTF_8);
            check(holder.load() && ArmorGuardClient.getInstance().getConfig().threshold == 10000,
                "Reloaded threshold must respect the upper bound");
            Files.writeString(path, "{invalid json", StandardCharsets.UTF_8);
            check(!holder.load(), "Malformed JSON must report a failed reload");
            GuardConfig fallback = ArmorGuardClient.getInstance().getConfig();
            check(fallback.enabled && fallback.threshold == 20, "Failed reload must restore safe defaults");
        });
        changeSettings(context, true, 20);
    }

    private static void changeSettings(ClientGameTestContext context, boolean enabled, int points) {
        context.setScreen(() -> GuardConfigScreen.create(null));
        context.runOnClient(client -> {
            ClothConfigScreen screen = (ClothConfigScreen) client.gui.screen();
            var entries = screen.getCategorizedEntries().values().stream().flatMap(java.util.Collection::stream).toList();
            BooleanListEntry toggle = (BooleanListEntry) entries.get(0);
            if (toggle.getValue() != enabled) {
                // Invoke the real toggle widget's action, then use Cloth Config's save flow.
                ((Button) toggle.children().getFirst()).onPress(null);
            }
            IntegerListEntry threshold = (IntegerListEntry) entries.get(1);
            ((EditBox) threshold.children().stream().filter(EditBox.class::isInstance).findFirst().orElseThrow()).setValue(Integer.toString(points));
            check(threshold.getError().isEmpty(), "Valid point threshold must pass Cloth Config validation");
            screen.saveAll(true);
            check(ArmorGuardClient.getInstance().getConfig().enabled == enabled
                && ArmorGuardClient.getInstance().getConfig().threshold == points, "Saved settings must apply immediately");
        });
    }

    private static JsonObject readSettings() throws java.io.IOException {
        try (var reader = Files.newBufferedReader(FabricLoader.getInstance().getConfigDir().resolve("armor-guard.json"), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static ServerPlayer first(net.minecraft.server.MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static ItemStack helmet(int damage) { return worn(Items.CHAINMAIL_HELMET, damage); }

    private static ItemStack worn(net.minecraft.world.item.Item item, int damage) {
        ItemStack stack = new ItemStack(item);
        stack.setDamageValue(damage);
        return stack;
    }

    private static int countHelmets(ServerPlayer player) {
        int count = 0;
        for (int slot = 0; slot < 36; slot++) if (player.getInventory().getItem(slot).is(Items.CHAINMAIL_HELMET)) count++;
        return count;
    }

    private static ItemStack findHelmet(ServerPlayer player) {
        for (int slot = 0; slot < 36; slot++) if (player.getInventory().getItem(slot).is(Items.CHAINMAIL_HELMET)) return player.getInventory().getItem(slot);
        return ItemStack.EMPTY;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
