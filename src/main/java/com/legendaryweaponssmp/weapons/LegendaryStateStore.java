package com.legendaryweaponssmp.weapons;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public class LegendaryStateStore {
    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;
    private final Map<WeaponType, WeaponState> cache = new EnumMap<>(WeaponType.class);

    public LegendaryStateStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "legendary-state.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
        load();
    }

    private void load() {
        for (WeaponType type : WeaponType.values()) {
            String path = "weapons." + type.id();
            boolean existsLegacy = yaml.getBoolean(path + ".exists", false);
            int existingCount = yaml.getInt(path + ".existing-count", -1);
            if (existingCount < 0) {
                existingCount = existsLegacy ? 1 : 0;
            }
            if (existingCount == 0 && existsLegacy) {
                existingCount = 1;
            }
            boolean inRitual = yaml.getBoolean(path + ".in-ritual", false);
            String ownerRaw = yaml.getString(path + ".owner");
            UUID owner = ownerRaw == null || ownerRaw.isBlank() ? null : UUID.fromString(ownerRaw);
            cache.put(type, new WeaponState(existingCount > 0, existingCount, inRitual, owner));
        }
        persist();
    }

    public synchronized boolean canCreate(WeaponType type) {
        return canCreate(type, 1);
    }

    public synchronized boolean canCreate(WeaponType type, int limit) {
        int safeLimit = Math.max(1, limit);
        WeaponState state = cache.get(type);
        if (state == null) {
            return false;
        }
        int ritualCount = state.inRitual() ? 1 : 0;
        return (state.existingCount() + ritualCount) < safeLimit;
    }

    public synchronized boolean existsOrRitual(WeaponType type) {
        WeaponState state = cache.get(type);
        return state != null && (state.existingCount() > 0 || state.inRitual());
    }

    public synchronized void markRitual(WeaponType type, UUID starter) {
        WeaponState state = cache.getOrDefault(type, new WeaponState(false, 0, false, null));
        cache.put(type, new WeaponState(state.existingCount() > 0, state.existingCount(), true, starter != null ? starter : state.owner()));
        persist();
    }

    public synchronized void markCreated(WeaponType type, UUID owner) {
        WeaponState state = cache.getOrDefault(type, new WeaponState(false, 0, false, null));
        int count = Math.max(0, state.existingCount()) + 1;
        UUID resolvedOwner = owner != null ? owner : state.owner();
        cache.put(type, new WeaponState(true, count, false, resolvedOwner));
        persist();
    }

    public synchronized void transferOwner(WeaponType type, UUID owner) {
        WeaponState state = cache.get(type);
        if (state == null) {
            return;
        }
        cache.put(type, new WeaponState(state.exists(), state.existingCount(), state.inRitual(), owner));
        persist();
    }

    public synchronized boolean claimFirstOwner(WeaponType type, UUID owner) {
        WeaponState state = cache.get(type);
        if (state == null || !state.exists() || state.owner() != null) {
            return false;
        }
        cache.put(type, new WeaponState(true, state.existingCount(), state.inRitual(), owner));
        persist();
        return true;
    }

    public synchronized void clear(WeaponType type) {
        cache.put(type, new WeaponState(false, 0, false, null));
        persist();
    }

    public synchronized void clearRitual(WeaponType type) {
        WeaponState state = cache.getOrDefault(type, new WeaponState(false, 0, false, null));
        int existingCount = Math.max(0, state.existingCount());
        cache.put(type, new WeaponState(existingCount > 0, existingCount, false, existingCount > 0 ? state.owner() : null));
        persist();
    }

    public synchronized WeaponState state(WeaponType type) {
        return cache.get(type);
    }

    public synchronized Map<WeaponType, WeaponState> snapshot() {
        return new EnumMap<>(cache);
    }

    private void persist() {
        for (WeaponType type : WeaponType.values()) {
            WeaponState state = cache.getOrDefault(type, new WeaponState(false, 0, false, null));
            String path = "weapons." + type.id();
            yaml.set(path + ".exists", state.exists());
            yaml.set(path + ".existing-count", state.existingCount());
            yaml.set(path + ".in-ritual", state.inRitual());
            yaml.set(path + ".owner", state.owner() == null ? null : state.owner().toString());
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save legendary-state.yml: " + e.getMessage());
        }
    }

    public record WeaponState(boolean exists, int existingCount, boolean inRitual, UUID owner) {}
}
