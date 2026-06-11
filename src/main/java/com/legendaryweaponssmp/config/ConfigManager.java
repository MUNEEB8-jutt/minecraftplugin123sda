package com.legendaryweaponssmp.config;

import com.legendaryweaponssmp.weapons.WeaponType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {
    private static final List<String> DEFAULT_DISABLED_WEAPON_IDS = List.of();
    private final JavaPlugin plugin;
    private final File configDir;
    private final File weaponConfigDir;
    private final Map<String, YamlConfiguration> configs = new HashMap<>();
    private final Map<String, YamlConfiguration> weaponConfigs = new HashMap<>();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configDir = new File(plugin.getDataFolder(), "config");
        this.weaponConfigDir = new File(configDir, "weapons");
        ensureDefaults();
        reloadAll();
    }

    private void ensureDefaults() {
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        copyDefault("general.yml");
        copyDefault("weapons.yml");
        copyDefault("recipes.yml");
        copyDefault("rituals.yml");
        copyDefault("cooldowns.yml");
        copyDefault("particles.yml");
        if (!weaponConfigDir.exists()) {
            weaponConfigDir.mkdirs();
        }
        for (WeaponType type : WeaponType.values()) {
            copyDefault("weapons/" + type.id() + ".yml", new File(weaponConfigDir, type.id() + ".yml"));
        }
    }

    private void copyDefault(String name) {
        copyDefault(name, new File(configDir, name));
    }

    private void copyDefault(String resourcePath, File target) {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        if (target.exists()) {
            return;
        }
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) {
                return;
            }
            Files.copy(in, target.toPath());
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create default config file " + resourcePath + ": " + e.getMessage());
        }
    }

    public void reloadAll() {
        configs.put("general", YamlConfiguration.loadConfiguration(new File(configDir, "general.yml")));
        configs.put("weapons", YamlConfiguration.loadConfiguration(new File(configDir, "weapons.yml")));
        configs.put("recipes", YamlConfiguration.loadConfiguration(new File(configDir, "recipes.yml")));
        configs.put("rituals", YamlConfiguration.loadConfiguration(new File(configDir, "rituals.yml")));
        configs.put("cooldowns", YamlConfiguration.loadConfiguration(new File(configDir, "cooldowns.yml")));
        configs.put("particles", YamlConfiguration.loadConfiguration(new File(configDir, "particles.yml")));
        weaponConfigs.clear();
        for (WeaponType type : WeaponType.values()) {
            File file = new File(weaponConfigDir, type.id() + ".yml");
            weaponConfigs.put(type.id(), YamlConfiguration.loadConfiguration(file));
        }
        migrateRitualLimitDefault();
    }

    public YamlConfiguration general() {
        return configs.get("general");
    }

    public YamlConfiguration weapons() {
        return configs.get("weapons");
    }

    public boolean isWeaponEnabled(WeaponType type) {
        List<String> disabledIds = general().contains("legendary.disabled-weapon-ids")
            ? general().getStringList("legendary.disabled-weapon-ids")
            : DEFAULT_DISABLED_WEAPON_IDS;
        if (disabledIds.stream().anyMatch(id -> id.equalsIgnoreCase(type.id()))) {
            return false;
        }
        return weapons().getBoolean("weapons." + type.id() + ".enabled", true);
    }

    public List<WeaponType> enabledWeaponTypes() {
        List<WeaponType> enabled = new ArrayList<>();
        for (WeaponType type : WeaponType.values()) {
            if (isWeaponEnabled(type)) {
                enabled.add(type);
            }
        }
        return enabled;
    }

    public YamlConfiguration rituals() {
        return configs.get("rituals");
    }

    public YamlConfiguration recipes() {
        return configs.get("recipes");
    }

    public YamlConfiguration cooldowns() {
        return configs.get("cooldowns");
    }

    public YamlConfiguration particles() {
        return configs.get("particles");
    }

    public YamlConfiguration weapon(String weaponId) {
        return weaponConfigs.get(weaponId);
    }

    public double weaponDouble(String weaponId, String path, double fallback) {
        YamlConfiguration cfg = weapon(weaponId);
        if (cfg == null) {
            return fallback;
        }
        return cfg.getDouble(path, fallback);
    }

    public int weaponInt(String weaponId, String path, int fallback) {
        YamlConfiguration cfg = weapon(weaponId);
        if (cfg == null) {
            return fallback;
        }
        return cfg.getInt(path, fallback);
    }

    public List<Double> weaponDoubleList(String weaponId, String path) {
        YamlConfiguration cfg = weapon(weaponId);
        if (cfg == null) {
            return List.of();
        }
        List<?> raw = cfg.getList(path);
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<Double> values = new ArrayList<>();
        for (Object value : raw) {
            if (value instanceof Number number) {
                values.add(number.doubleValue());
                continue;
            }
            if (value instanceof String text) {
                try {
                    values.add(Double.parseDouble(text.trim()));
                } catch (NumberFormatException ignored) {
                    // Ignore invalid value.
                }
            }
        }
        return values;
    }

    public int cooldownSeconds(String key, int fallback) {
        return cooldowns().getInt("cooldowns." + key, fallback);
    }

    public int ritualDurationSeconds() {
        return rituals().getInt("ritual.duration-seconds", 900);
    }

    public int ritualBroadcastIntervalSeconds() {
        return rituals().getInt("ritual.progress-broadcast-interval-seconds", 300);
    }

    public int ritualStructureRadius() {
        return rituals().getInt("ritual.structure-radius", 6);
    }

    public int ritualZoneRadius() {
        return rituals().getInt("ritual.zone-radius", 25);
    }

    public int ritualEmptyFailSeconds() {
        return rituals().getInt("ritual.empty-fail-seconds", 120);
    }

    public int ritualFailureCooldownSeconds() {
        return rituals().getInt("ritual.failure-cooldown-seconds", 120);
    }

    public double ritualIntegrityThreshold() {
        return rituals().getDouble("ritual.integrity-threshold", 0.72);
    }

    public int ritualPulseIntervalSeconds() {
        return rituals().getInt("ritual.pulse-interval-seconds", 10);
    }

    public int ritualBuildDelayTicks() {
        return rituals().getInt("ritual.build-delay-ticks", 2);
    }

    public int ritualBlocksPerStep() {
        return rituals().getInt("ritual.build-blocks-per-step", 2);
    }

    public double particleDensity() {
        return particles().getDouble("particles.ability-density", 1.0);
    }

    public double ritualParticleDensity() {
        return particles().getDouble("particles.ritual-density", 1.0);
    }

    public double abilityRadius() {
        return general().getDouble("abilities.radius", 8.0);
    }

    public double knockbackStrength() {
        return general().getDouble("abilities.knockback-strength", 2.2);
    }

    public int weaponLimit() {
        return Math.max(1, general().getInt("legendary.weapon-limit", 1));
    }

    public void setWeaponLimit(int limit) {
        int safe = Math.max(1, limit);
        YamlConfiguration cfg = general();
        cfg.set("legendary.weapon-limit", safe);
        save(cfg, new File(configDir, "general.yml"), "general.yml");
    }

    public int ritualLimit() {
        int fallback = defaultRitualLimit();
        return Math.max(1, general().getInt("performance.max-active-rituals", fallback));
    }

    public void setRitualLimit(int limit) {
        int safe = Math.max(1, limit);
        YamlConfiguration cfg = general();
        cfg.set("performance.max-active-rituals", safe);
        cfg.set("performance.ritual-limit-user-set", true);
        save(cfg, new File(configDir, "general.yml"), "general.yml");
    }

    private int defaultRitualLimit() {
        return Math.max(1, enabledWeaponTypes().size());
    }

    private void migrateRitualLimitDefault() {
        YamlConfiguration cfg = general();
        if (cfg.getBoolean("performance.ritual-limit-user-set", false)) {
            return;
        }
        int wanted = defaultRitualLimit();
        int current = cfg.getInt("performance.max-active-rituals", wanted);
        if (current <= 1 && wanted > 1) {
            cfg.set("performance.max-active-rituals", wanted);
            save(cfg, new File(configDir, "general.yml"), "general.yml");
        }
    }

    private void save(YamlConfiguration cfg, File file, String name) {
        try {
            if (!configDir.exists()) {
                configDir.mkdirs();
            }
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save " + name + ": " + e.getMessage());
        }
    }
}
