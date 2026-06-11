package com.legendaryweaponssmp.particles;

import com.legendaryweaponssmp.config.ConfigManager;
import com.legendaryweaponssmp.weapons.WeaponType;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class ParticleService {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;

    public ParticleService(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void weaponBurst(Location center, WeaponType type) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        double mult = configManager.particleDensity();
        world.spawnParticle(Particle.DUST, center, (int) (34 * mult), 1.0, 0.8, 1.0, 0.03,
            new Particle.DustOptions(type.color(), 1.35f));
        world.spawnParticle(Particle.END_ROD, center, (int) (10 * mult), 0.55, 0.55, 0.55, 0.02);
    }

    public void ritualCircle(Location center, int radius) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        double density = configManager.ritualParticleDensity();
        int points = Math.max(24, (int) (72 * density));
        for (int i = 0; i < points; i++) {
            double angle = 2.0 * Math.PI * i / points;
            double x = center.getX() + Math.cos(angle) * radius;
            double z = center.getZ() + Math.sin(angle) * radius;
            world.spawnParticle(Particle.ENCHANT, x, center.getY() + 0.2, z, 1, 0.03, 0.03, 0.03, 0.0);
            world.spawnParticle(Particle.END_ROD, x, center.getY() + 0.3, z, 1, 0.03, 0.03, 0.03, 0.0);
        }
    }

    public void ritualBeam(Location from, Location to) {
        World world = from.getWorld();
        if (world == null || to.getWorld() == null || world != to.getWorld()) {
            return;
        }
        Vector direction = to.toVector().subtract(from.toVector());
        double length = direction.length();
        if (length <= 0.01) {
            return;
        }
        direction.normalize();
        double step = Math.max(0.35, configManager.particles().getDouble("particles.beam-step", 1.0));
        for (double d = 0; d <= length; d += step) {
            Location point = from.clone().add(direction.clone().multiply(d));
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, point, 1, 0.02, 0.02, 0.02, 0.0);
            world.spawnParticle(Particle.END_ROD, point, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    public void storm(Player owner, Location center, double radius, int ticks, double damagePerTick) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        new BukkitRunnable() {
            int left = ticks;
            double angle = 0.0;
            @Override
            public void run() {
                if (left <= 0 || !owner.isOnline()) {
                    cancel();
                    return;
                }
                Location pivot = owner.getLocation().add(0, 1.0, 0);
                world.spawnParticle(Particle.SNOWFLAKE, pivot, 180, radius, 1.9, radius, 0.03);
                world.spawnParticle(Particle.CLOUD, pivot, 130, radius * 0.85, 1.3, radius * 0.85, 0.02);
                for (int ring = 0; ring < 3; ring++) {
                    double ringRadius = radius * (0.4 + ring * 0.22);
                    double y = pivot.getY() + (ring * 0.6) - 0.4;
                    for (int i = 0; i < 18; i++) {
                        double stepAngle = angle + (i * (Math.PI * 2.0 / 18.0));
                        double x = pivot.getX() + Math.cos(stepAngle) * ringRadius;
                        double z = pivot.getZ() + Math.sin(stepAngle) * ringRadius;
                        world.spawnParticle(Particle.SNOWFLAKE, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
                        world.spawnParticle(Particle.CLOUD, x, y, z, 1, 0.03, 0.03, 0.03, 0.0);
                    }
                }
                world.getNearbyLivingEntities(pivot, radius, 2.8, radius, entity -> !entity.getUniqueId().equals(owner.getUniqueId()))
                    .forEach(entity -> {
                        entity.damage(damagePerTick, owner);
                        entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 3, true, true, true));
                    });
                world.playSound(pivot, Sound.WEATHER_RAIN_ABOVE, 0.55f, 0.7f);
                angle += 0.55;
                left -= 10;
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    public Particle.DustOptions runeDust() {
        return new Particle.DustOptions(Color.fromRGB(120, 240, 255), 1.4f);
    }
}
