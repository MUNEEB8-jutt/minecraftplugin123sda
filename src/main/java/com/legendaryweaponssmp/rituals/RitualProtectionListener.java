package com.legendaryweaponssmp.rituals;

import com.legendaryweaponssmp.core.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class RitualProtectionListener implements Listener {
    private final JavaPlugin plugin;
    private final RitualManager ritualManager;
    private final MessageService messageService;

    public RitualProtectionListener(JavaPlugin plugin, RitualManager ritualManager, MessageService messageService) {
        this.plugin = plugin;
        this.ritualManager = ritualManager;
        this.messageService = messageService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!ritualManager.isProtected(event.getBlock().getLocation())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (canRemoveRitual(player) && ritualManager.breakRitualStructure(player, event.getBlock().getLocation())) {
            return;
        }
        messageService.send(player, "&cOnly creative operators can remove ritual blocks.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        // Players may build inside the ritual area; only plugin-owned ritual blocks are protected from breaking.
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> ritualManager.isProtected(block.getLocation()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> ritualManager.isProtected(block.getLocation()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (ritualManager.isProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null && ritualManager.isRitualAnchor(event.getClickedBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    private boolean canRemoveRitual(Player player) {
        return player.getGameMode() == GameMode.CREATIVE
            && (player.isOp() || player.hasPermission("legendary.admin"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (ritualManager.acceptOfferingDrop(event.getPlayer(), event.getItemDrop())) {
            return;
        }
        UUID playerId = event.getPlayer().getUniqueId();
        Item item = event.getItemDrop();
        for (long delay : new long[]{2L, 6L, 12L}) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!item.isValid()) {
                    return;
                }
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    ritualManager.acceptOfferingDrop(player, item);
                }
            }, delay);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        ritualManager.refreshPlayerRitualBars(event.getPlayer());
        UUID playerId = event.getPlayer().getUniqueId();
        for (long delay : new long[]{20L, 80L, 160L}) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                org.bukkit.entity.Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    ritualManager.refreshPlayerRitualBars(player);
                }
            }, delay);
        }
    }
}
