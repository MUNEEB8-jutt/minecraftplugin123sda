package com.legendaryweaponssmp.rituals;

import com.legendaryweaponssmp.config.ConfigManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class RitualMobProtectionListener implements Listener {
    private static final double DUNGEON_VERTICAL_RANGE = 80.0;

    private final RitualManager ritualManager;
    private final ConfigManager configManager;

    public RitualMobProtectionListener(JavaPlugin plugin, RitualManager ritualManager, ConfigManager configManager) {
        this.ritualManager = ritualManager;
        this.configManager = configManager;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::purgeDungeonHostiles, 40L, 100L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Enemy) || !isInsideDungeonProtection(entity.getLocation())) {
            return;
        }
        event.setCancelled(true);
    }

    private void purgeDungeonHostiles() {
        double radius = protectionRadius();
        double vertical = DUNGEON_VERTICAL_RANGE;
        for (RitualSession session : ritualManager.activeSessions()) {
            if (!session.dungeonRitual() || session.manuallyStopped()) {
                continue;
            }
            Location center = session.center();
            World world = center.getWorld();
            if (world == null) {
                continue;
            }
            for (Entity entity : world.getNearbyEntities(center, radius, vertical, radius)) {
                if (entity instanceof Enemy) {
                    entity.remove();
                }
            }
        }
    }

    private boolean isInsideDungeonProtection(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        double radiusSq = protectionRadius() * protectionRadius();
        for (RitualSession session : ritualManager.activeSessions()) {
            if (!session.dungeonRitual() || session.manuallyStopped()) {
                continue;
            }
            Location center = session.center();
            if (!location.getWorld().equals(center.getWorld())) {
                continue;
            }
            if (Math.abs(location.getY() - center.getY()) > DUNGEON_VERTICAL_RANGE) {
                continue;
            }
            double dx = location.getX() - center.getX();
            double dz = location.getZ() - center.getZ();
            if ((dx * dx) + (dz * dz) <= radiusSq) {
                return true;
            }
        }
        return false;
    }

    private double protectionRadius() {
        return Math.max(64.0, configManager.ritualZoneRadius() + 48.0);
    }
}
