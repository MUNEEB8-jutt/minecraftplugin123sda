package com.legendaryweaponssmp.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

public final class SafeYamlFiles {
    private SafeYamlFiles() {
    }

    public static YamlConfiguration loadOrQuarantine(File file, Logger logger) {
        YamlConfiguration configuration = new YamlConfiguration();
        if (!file.isFile()) {
            return configuration;
        }
        try {
            configuration.load(file);
            return configuration;
        } catch (IOException | InvalidConfigurationException ex) {
            File backup = new File(
                file.getParentFile(),
                file.getName() + ".corrupt-" + System.currentTimeMillis() + ".bak"
            );
            try {
                Files.move(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                logger.warning("Quarantined invalid YAML file as " + backup.getName() + ": " + ex.getMessage());
            } catch (IOException moveFailure) {
                logger.warning("Invalid YAML file could not be quarantined: " + moveFailure.getMessage());
            }
            return new YamlConfiguration();
        }
    }
}
