package com.legendaryweaponssmp.resourcepack;

import com.legendaryweaponssmp.config.ConfigManager;
import com.legendaryweaponssmp.core.MessageService;
import com.legendaryweaponssmp.rituals.RitualManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

public class ResourcePackListener implements Listener {
    private final ResourcePackManager resourcePackManager;
    private final ConfigManager configManager;
    private final MessageService messageService;
    private final RitualManager ritualManager;

    public ResourcePackListener(ResourcePackManager resourcePackManager,
                                ConfigManager configManager,
                                MessageService messageService,
                                RitualManager ritualManager) {
        this.resourcePackManager = resourcePackManager;
        this.configManager = configManager;
        this.messageService = messageService;
        this.ritualManager = ritualManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (configManager.general().getBoolean("resource-pack.auto-send-on-join", true)) {
            resourcePackManager.sendPack(event.getPlayer());
        }
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> {
                Player player = event.getPlayer();
                messageService.send(player, "&aLegendary resource pack loaded.");
                ritualManager.refreshPlayerRitualBars(player);
                for (long delay : new long[]{20L, 60L, 120L}) {
                    Bukkit.getScheduler().runTaskLater(ritualManager.getPlugin(), () -> {
                        if (player.isOnline()) {
                            ritualManager.refreshPlayerRitualBars(player);
                        }
                    }, delay);
                }
            }
            case DECLINED -> {
                boolean forced = configManager.general().getBoolean("resource-pack.force", true);
                if (forced) {
                    messageService.send(event.getPlayer(), "&cResource pack is required on this server.");
                } else {
                    messageService.send(event.getPlayer(), "&eResource pack declined. Visuals may be missing.");
                }
            }
            default -> {
            }
        }
    }
}
