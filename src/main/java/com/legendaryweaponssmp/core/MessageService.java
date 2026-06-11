package com.legendaryweaponssmp.core;

import com.legendaryweaponssmp.config.ConfigManager;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MessageService {
    private final ConfigManager configManager;

    public MessageService(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public String colorize(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    public String prefix() {
        return colorize(configManager.general().getString("messages.prefix", "&6[Legendary]&r "));
    }

    public void send(CommandSender sender, String message) {
        sender.sendMessage(prefix() + colorize(message));
    }

    public void action(Player player, String message) {
        player.sendActionBar(colorize(prefix() + message));
    }
}
