package com.legendaryweaponssmp.weapons;

import com.legendaryweaponssmp.config.ConfigManager;
import com.legendaryweaponssmp.core.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;

public class LegendaryItemListener implements Listener {
    private static final TextColor[] TITLE_COLORS = new TextColor[]{
        TextColor.color(255, 210, 84),
        TextColor.color(255, 110, 150),
        TextColor.color(120, 215, 255),
        TextColor.color(170, 130, 255),
        TextColor.color(110, 255, 170)
    };
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final WeaponItemFactory itemFactory;
    private final LegendaryStateStore stateStore;
    private final MessageService messageService;

    public LegendaryItemListener(JavaPlugin plugin, ConfigManager configManager, WeaponItemFactory itemFactory, LegendaryStateStore stateStore, MessageService messageService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.itemFactory = itemFactory;
        this.stateStore = stateStore;
        this.messageService = messageService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack stack = event.getItem().getItemStack();
        WeaponType type = itemFactory.peekType(stack).orElse(null);
        if (type == null || !configManager.isWeaponEnabled(type)) {
            return;
        }
        itemFactory.resolve(stack);
        if (!stateStore.claimFirstOwner(type, player.getUniqueId())) {
            return;
        }
        animateGlobalObtainTitle(player.getName(), type.displayName());
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().clone().add(0, 1.0, 0), 90, 0.6, 1.0, 0.6, 0.15);
        player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation().clone().add(0, 1.2, 0), 150, 1.25, 1.1, 1.25, 0.08);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().clone().add(0, 1.2, 0), 90, 0.85, 0.85, 0.85, 0.03);
        player.getWorld().playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.05f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ALLAY_AMBIENT_WITH_ITEM, 0.9f, 1.45f);
        Bukkit.broadcastMessage(messageService.colorize(messageService.prefix()
            + "&6" + player.getName() + " &ehas obtained &6" + type.displayName()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        boolean currentLegendary = itemFactory.isLegendary(current);
        boolean cursorLegendary = itemFactory.isLegendary(cursor);
        if (!currentLegendary && !cursorLegendary) {
            return;
        }
        boolean blockedDestination = isBlockedInventory(event.getClickedInventory()) || isBlockedInventory(event.getView().getTopInventory());
        boolean bundleAttempt = isBundle(current) || isBundle(cursor);
        if (blockedDestination || bundleAttempt || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                messageService.send(player, "&cLegendary weapons cannot be stored in ender chests, shulkers, or bundles.");
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!itemFactory.isLegendary(event.getOldCursor())) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (isBlockedInventory(top)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                messageService.send(player, "&cLegendary weapons cannot be stored there.");
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        if (!itemFactory.isLegendary(event.getItem())) {
            return;
        }
        if (isBlockedInventory(event.getDestination())) {
            event.setCancelled(true);
        }
    }

    private boolean isBlockedInventory(Inventory inventory) {
        if (inventory == null) {
            return false;
        }
        if (inventory.getType() == InventoryType.ENDER_CHEST) {
            return true;
        }
        return inventory.getType().name().contains("SHULKER_BOX");
    }

    private boolean isBundle(ItemStack stack) {
        return stack != null && stack.getType() == Material.BUNDLE;
    }

    private void animateGlobalObtainTitle(String playerName, String weaponName) {
        for (int i = 0; i < 6; i++) {
            int index = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                TextColor mainColor = TITLE_COLORS[index % TITLE_COLORS.length];
                TextColor subColor = TITLE_COLORS[(index + 2) % TITLE_COLORS.length];
                Title title = Title.title(
                    Component.text(playerName + " obtained Legendary Weapon", mainColor),
                    Component.text(weaponName, subColor),
                    Title.Times.times(Duration.ofMillis(120), Duration.ofMillis(700), Duration.ofMillis(180))
                );
                Bukkit.getOnlinePlayers().forEach(online -> online.showTitle(title));
            }, i * 4L);
        }
    }
}
