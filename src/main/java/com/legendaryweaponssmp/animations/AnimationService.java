package com.legendaryweaponssmp.animations;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AnimationService {
    private final JavaPlugin plugin;
    private final Map<UUID, BukkitTask> rotators = new HashMap<>();

    public AnimationService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void startRotating(ItemDisplay display, float speedRadians) {
        stopRotating(display);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!display.isValid()) {
                stopRotating(display);
                return;
            }
            Transformation t = display.getTransformation();
            Quaternionf q = new Quaternionf(t.getLeftRotation());
            q.mul(new Quaternionf(new AxisAngle4f(speedRadians, 0f, 1f, 0f)));
            display.setTransformation(new Transformation(new Vector3f(t.getTranslation()), q, new Vector3f(t.getScale()), new Quaternionf(t.getRightRotation())));
            Location l = display.getLocation();
            l.getWorld().spawnParticle(Particle.END_ROD, l, 1, 0.2, 0.2, 0.2, 0.0);
        }, 0L, 1L);
        rotators.put(display.getUniqueId(), task);
    }

    public void stopRotating(ItemDisplay display) {
        BukkitTask task = rotators.remove(display.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    public void stopAll() {
        for (BukkitTask task : rotators.values()) {
            task.cancel();
        }
        rotators.clear();
    }
}
