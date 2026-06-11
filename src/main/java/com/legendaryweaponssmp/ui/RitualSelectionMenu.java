package com.legendaryweaponssmp.ui;

import com.legendaryweaponssmp.config.ConfigManager;
import com.legendaryweaponssmp.core.MessageService;
import com.legendaryweaponssmp.rituals.RitualBoxService;
import com.legendaryweaponssmp.rituals.RitualManager;
import com.legendaryweaponssmp.weapons.LegendaryStateStore;
import com.legendaryweaponssmp.weapons.WeaponType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class RitualSelectionMenu implements Listener {
    private final ConfigManager configManager;
    private final RitualManager ritualManager;
    private final RitualBoxService ritualBoxService;
    private final LegendaryStateStore stateStore;
    private final MessageService messageService;

    public RitualSelectionMenu(ConfigManager configManager,
                               RitualManager ritualManager,
                               RitualBoxService ritualBoxService,
                               LegendaryStateStore stateStore,
                               MessageService messageService) {
        this.configManager = configManager;
        this.ritualManager = ritualManager;
        this.ritualBoxService = ritualBoxService;
        this.stateStore = stateStore;
        this.messageService = messageService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onRitualCorePlace(BlockPlaceEvent event) {
        if (!ritualBoxService.isRitualBox(event.getItemInHand())) {
            return;
        }
        Player player = event.getPlayer();
        WeaponType type = ritualBoxService.resolveCoreType(event.getItemInHand()).orElse(null);
        if (type == null) {
            event.setCancelled(true);
            messageService.send(player, "&cInvalid Ritual Core.");
            return;
        }
        Location location = event.getBlockPlaced().getLocation();
        Bukkit.getScheduler().runTaskLater(ritualManager.getPlugin(), () -> {
            boolean started = ritualManager.startRitual(player, type, location);
            if (started) {
                return;
            }
            if (location.getBlock().getType() != Material.AIR) {
                location.getBlock().setType(Material.AIR, false);
            }
            player.getInventory().addItem(ritualBoxService.createItem(type));
        }, 1L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLegacyCoreUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getItem() == null || event.getItem().getType().isBlock()) {
            return;
        }
        if (!ritualBoxService.isRitualBox(event.getItem())) {
            return;
        }
        Player player = event.getPlayer();
        WeaponType type = ritualBoxService.resolveCoreType(event.getItem()).orElse(null);
        if (type == null) {
            event.setCancelled(true);
            messageService.send(player, "&cInvalid Ritual Core.");
            return;
        }
        Location placeAt = event.getClickedBlock().getRelative(BlockFace.UP).getLocation();
        if (placeAt.getBlock().getType() != Material.AIR) {
            event.setCancelled(true);
            messageService.send(player, "&cNeed empty space above target block for Ritual Core.");
            return;
        }
        event.setCancelled(true);
        if (!ritualBoxService.consumeOne(player, type)) {
            messageService.send(player, "&cYou must hold the correct Ritual Core.");
            return;
        }
        placeAt.getBlock().setType(Material.RESPAWN_ANCHOR, false);
        Bukkit.getScheduler().runTaskLater(ritualManager.getPlugin(), () -> {
            boolean started = ritualManager.startRitual(player, type, placeAt);
            if (started) {
                return;
            }
            if (placeAt.getBlock().getType() != Material.AIR) {
                placeAt.getBlock().setType(Material.AIR, false);
            }
            player.getInventory().addItem(ritualBoxService.createItem(type));
        }, 1L);
    }
}
