package com.legendaryweaponssmp.abilities;

import com.legendaryweaponssmp.hud.BetterHudIntegration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CooldownManager {
    private final JavaPlugin plugin;
    private final Map<UUID, Map<String, Long>> cooldownEnds = new HashMap<>();
    private final Map<UUID, Map<String, BossBarHolder>> bars = new HashMap<>();
    private final Map<UUID, Map<String, ActionBarHolder>> actionBars = new HashMap<>();
    private final Map<UUID, PairHud> pairHuds = new HashMap<>();
    private BetterHudIntegration betterHudIntegration;
    private BukkitTask updaterTask;

    public CooldownManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void attachBetterHudIntegration(BetterHudIntegration betterHudIntegration) {
        this.betterHudIntegration = betterHudIntegration;
    }

    public void start() {
        if (updaterTask != null) {
            updaterTask.cancel();
        }
        updaterTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickBars, 1L, 1L);
    }

    public void stop() {
        if (updaterTask != null) {
            updaterTask.cancel();
            updaterTask = null;
        }
        for (Map<String, BossBarHolder> playerBars : bars.values()) {
            for (BossBarHolder holder : playerBars.values()) {
                holder.bar.removeAll();
            }
        }
        bars.clear();
        actionBars.clear();
        pairHuds.clear();
        cooldownEnds.clear();
        if (betterHudIntegration != null) {
            Bukkit.getOnlinePlayers().forEach(betterHudIntegration::clearCooldown);
        }
    }

    public boolean isOnCooldown(Player player, String key) {
        Long end = cooldownEnds.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>()).get(key);
        return end != null && end > System.currentTimeMillis();
    }

    public long remainingMillis(Player player, String key) {
        Long end = cooldownEnds.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>()).get(key);
        if (end == null) {
            return 0L;
        }
        return Math.max(0L, end - System.currentTimeMillis());
    }

    public void startCooldown(Player player, String key, int seconds, String label) {
        startActionBarCooldown(player, key, seconds, label, styleForKey(key));
    }

    private ActionBarStyle styleForKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        if (normalized.contains("quantum")) {
            return ActionBarStyle.WARDEN;
        }
        if (normalized.contains("drakefire") || normalized.contains("bloodchain") || normalized.contains("sanguine")) {
            return ActionBarStyle.HELL;
        }
        if (normalized.contains("goldfang") || normalized.contains("stormbreaker") || normalized.contains("bifrost")) {
            return ActionBarStyle.LIGHTNING;
        }
        if (normalized.contains("frostnova") || normalized.contains("hornhook") || normalized.contains("tempest")) {
            return ActionBarStyle.ICE;
        }
        if (normalized.contains("voidglass") || normalized.contains("necromancer")) {
            return ActionBarStyle.SOUL;
        }
        if (normalized.contains("timberlord")) {
            return ActionBarStyle.STONE;
        }
        return ActionBarStyle.EMERALD;
    }

    public void startEmeraldCooldown(Player player, String key, int seconds, String label) {
        startActionBarCooldown(player, key, seconds, label, ActionBarStyle.EMERALD);
    }

    public void startWebCooldown(Player player, String key, int seconds, String label) {
        startActionBarCooldown(player, key, seconds, label, ActionBarStyle.WEB);
    }

    public void startIceCooldown(Player player, String key, int seconds, String label) {
        startActionBarCooldown(player, key, seconds, label, ActionBarStyle.ICE);
    }

    public void startLightningCooldown(Player player, String key, int seconds, String label) {
        startActionBarCooldown(player, key, seconds, label, ActionBarStyle.LIGHTNING);
    }

    public void startWardenCooldown(Player player, String key, int seconds, String label) {
        startActionBarCooldown(player, key, seconds, label, ActionBarStyle.WARDEN);
    }

    public void startSoulCooldown(Player player, String key, int seconds, String label) {
        startActionBarCooldown(player, key, seconds, label, ActionBarStyle.SOUL);
    }

    public void startHellCooldown(Player player, String key, int seconds, String label) {
        startActionBarCooldown(player, key, seconds, label, ActionBarStyle.HELL);
    }

    public void startStoneCooldown(Player player, String key, int seconds, String label) {
        startActionBarCooldown(player, key, seconds, label, ActionBarStyle.STONE);
    }

    private void startActionBarCooldown(Player player, String key, int seconds, String label, ActionBarStyle style) {
        long now = System.currentTimeMillis();
        long end = now + seconds * 1000L;
        UUID playerId = player.getUniqueId();
        cooldownEnds.computeIfAbsent(playerId, ignored -> new HashMap<>()).put(key, end);
        removeBossBar(playerId, key);
        Map<String, ActionBarHolder> playerActionBars = actionBars.computeIfAbsent(playerId, ignored -> new HashMap<>());
        playerActionBars.put(key, new ActionBarHolder(now, end, label, style));
        registerPairHud(playerId, key, style);
    }

    public void clearPlayer(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && betterHudIntegration != null) {
            betterHudIntegration.clearCooldown(player);
        }
        cooldownEnds.remove(playerId);
        Map<String, BossBarHolder> playerBars = bars.remove(playerId);
        if (playerBars == null) {
            actionBars.remove(playerId);
            pairHuds.remove(playerId);
            return;
        }
        for (BossBarHolder holder : playerBars.values()) {
            holder.bar.removeAll();
        }
        actionBars.remove(playerId);
        pairHuds.remove(playerId);
    }

    private void tickBars() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Map<String, BossBarHolder>>> it = bars.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Map<String, BossBarHolder>> entry = it.next();
            UUID playerId = entry.getKey();
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                for (BossBarHolder holder : entry.getValue().values()) {
                    holder.bar.removeAll();
                }
                it.remove();
                cooldownEnds.remove(playerId);
                continue;
            }
            Iterator<Map.Entry<String, BossBarHolder>> barsIt = entry.getValue().entrySet().iterator();
            while (barsIt.hasNext()) {
                Map.Entry<String, BossBarHolder> barEntry = barsIt.next();
                BossBarHolder holder = barEntry.getValue();
                if (now >= holder.endTime) {
                    holder.bar.removeAll();
                    barsIt.remove();
                    Map<String, Long> playerCooldowns = cooldownEnds.get(playerId);
                    if (playerCooldowns != null) {
                        playerCooldowns.remove(barEntry.getKey());
                    }
                    continue;
                }
                double progress = (double) (holder.endTime - now) / (double) (holder.endTime - holder.startTime);
                holder.bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
                holder.bar.setTitle(holder.label + " " + Math.max(1L, (holder.endTime - now + 999L) / 1000L) + "s");
            }
            if (entry.getValue().isEmpty()) {
                it.remove();
            }
        }
        tickActionBars(now);
    }

    private void tickActionBars(long now) {
        Iterator<Map.Entry<UUID, Map<String, ActionBarHolder>>> playersIt = actionBars.entrySet().iterator();
        while (playersIt.hasNext()) {
            Map.Entry<UUID, Map<String, ActionBarHolder>> entry = playersIt.next();
            Iterator<Map.Entry<String, ActionBarHolder>> cooldownIt = entry.getValue().entrySet().iterator();
            while (cooldownIt.hasNext()) {
                Map.Entry<String, ActionBarHolder> coolEntry = cooldownIt.next();
                ActionBarHolder holder = coolEntry.getValue();
                if (now >= holder.endTime) {
                    cooldownIt.remove();
                    Map<String, Long> playerCooldowns = cooldownEnds.get(entry.getKey());
                    if (playerCooldowns != null) {
                        playerCooldowns.remove(coolEntry.getKey());
                    }
                    continue;
                }
            }
            if (entry.getValue().isEmpty()) {
                playersIt.remove();
            }
        }

        Set<UUID> toRender = new HashSet<>();
        toRender.addAll(actionBars.keySet());
        toRender.addAll(pairHuds.keySet());
        for (UUID playerId : toRender) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                actionBars.remove(playerId);
                pairHuds.remove(playerId);
                continue;
            }

            PairHud pairHud = pairHuds.get(playerId);
            if (pairHud != null) {
                ActionBarHolder leftHolder = holderFor(playerId, pairHud.leftKey());
                ActionBarHolder rightHolder = holderFor(playerId, pairHud.rightKey());
                String leftText = formatDualSlot(pairHud.style(), pairHud.leftName(), leftHolder, now);
                String rightText = formatDualSlot(pairHud.style(), pairHud.rightName(), rightHolder, now);
                if (renderBetterHudPair(player, pairHud, leftHolder, rightHolder, now)) {
                    continue;
                }
                player.sendActionBar(formatDualActionBar(pairHud.style(), leftText, rightText));
                continue;
            }

            ActionBarHolder active = latestActive(actionBars.get(playerId));
            if (active == null) {
                continue;
            }
            long total = Math.max(1L, active.endTime - active.startTime);
            double progress = Math.max(0.0, Math.min(1.0, (double) (now - active.startTime) / total));
            int segments = 6;
            int filledSegments = Math.max(0, Math.min(segments, (int) Math.round(progress * segments)));
            String filled = "\u25A0".repeat(filledSegments);
            String empty = "\u25A1".repeat(segments - filledSegments);
            long secondsLeft = Math.max(1L, (active.endTime - now + 999L) / 1000L);
            if (renderBetterHudSingle(player, active, progress, secondsLeft)) {
                continue;
            }
            player.sendActionBar(formatActionBar(active.style, filled, empty, active.label, secondsLeft));
        }
    }

    private boolean renderBetterHudPair(Player player, PairHud pairHud, ActionBarHolder leftHolder, ActionBarHolder rightHolder, long now) {
        if (betterHudIntegration == null || !betterHudIntegration.isAvailable()) {
            return false;
        }
        CooldownSlot left = slot(pairHud.leftName(), leftHolder, now);
        CooldownSlot right = slot(pairHud.rightName(), rightHolder, now);
        betterHudIntegration.updateCooldown(
            player,
            left.name(),
            left.time(),
            left.progress(),
            right.name(),
            right.time(),
            right.progress()
        );
        return true;
    }

    private boolean renderBetterHudSingle(Player player, ActionBarHolder active, double progress, long secondsLeft) {
        if (betterHudIntegration == null || !betterHudIntegration.isAvailable()) {
            return false;
        }
        betterHudIntegration.updateCooldown(
            player,
            active.label,
            secondsLeft + "s",
            progress,
            "READY",
            "READY",
            1.0
        );
        return true;
    }

    private CooldownSlot slot(String name, ActionBarHolder holder, long now) {
        if (holder == null || now >= holder.endTime) {
            return new CooldownSlot(name, "READY", 1.0);
        }
        long total = Math.max(1L, holder.endTime - holder.startTime);
        double progress = Math.max(0.0, Math.min(1.0, (double) (now - holder.startTime) / total));
        long secondsLeft = Math.max(1L, (holder.endTime - now + 999L) / 1000L);
        return new CooldownSlot(name, secondsLeft + "s", progress);
    }

    private long remainingForKey(UUID playerId, String key, long now) {
        Map<String, Long> playerCooldowns = cooldownEnds.get(playerId);
        if (playerCooldowns == null) {
            return 0L;
        }
        Long end = playerCooldowns.get(key);
        if (end == null) {
            return 0L;
        }
        return Math.max(0L, end - now);
    }

    private ActionBarHolder latestActive(Map<String, ActionBarHolder> byKey) {
        if (byKey == null || byKey.isEmpty()) {
            return null;
        }
        ActionBarHolder active = null;
        for (ActionBarHolder holder : byKey.values()) {
            if (active == null || holder.startTime > active.startTime) {
                active = holder;
            }
        }
        return active;
    }

    private ActionBarHolder holderFor(UUID playerId, String key) {
        Map<String, ActionBarHolder> byKey = actionBars.get(playerId);
        if (byKey == null) {
            return null;
        }
        return byKey.get(key);
    }

    private void registerPairHud(UUID playerId, String key, ActionBarStyle style) {
        PairHud pair = resolvePairHud(key, style);
        if (pair != null) {
            pairHuds.put(playerId, pair);
        }
    }

    private PairHud resolvePairHud(String key, ActionBarStyle style) {
        String id = key.endsWith("_left") ? key.substring(0, key.length() - "_left".length())
            : key.endsWith("_right") ? key.substring(0, key.length() - "_right".length())
            : "";
        if (!id.isBlank()) {
            return new PairHud("SIGNATURE", "ULTIMATE", id + "_left", id + "_right", style);
        }
        return null;
    }

    private String formatDualSlot(ActionBarStyle style, String name, ActionBarHolder holder, long now) {
        Palette palette = palette(style);
        if (holder == null || now >= holder.endTime) {
            return palette.active() + name + " " + ChatColor.GREEN + "READY";
        }
        long total = Math.max(1L, holder.endTime - holder.startTime);
        double progress = Math.max(0.0, Math.min(1.0, (double) (now - holder.startTime) / total));
        int segments = 6;
        int filledSegments = Math.max(0, Math.min(segments, (int) Math.round(progress * segments)));
        String filled = "\u25A0".repeat(filledSegments);
        String empty = "\u25A1".repeat(segments - filledSegments);
        long secondsLeft = Math.max(1L, (holder.endTime - now + 999L) / 1000L);
        return palette.active() + name + " " + palette.active() + filled + ChatColor.DARK_GRAY + empty + ChatColor.WHITE + " " + secondsLeft + "s";
    }

    private String formatDualActionBar(ActionBarStyle style, String leftText, String rightText) {
        Palette palette = palette(style);
        return palette.frame() + "[" + leftText + palette.frame() + "] "
            + palette.frame() + "[" + rightText + palette.frame() + "]";
    }

    private String formatActionBar(ActionBarStyle style, String filled, String empty, String label, long secondsLeft) {
        if (style == ActionBarStyle.WEB) {
            return ChatColor.GRAY + "[" + ChatColor.WHITE + filled + ChatColor.DARK_GRAY + empty + ChatColor.GRAY + "] "
                + ChatColor.WHITE + label + " " + ChatColor.GRAY + secondsLeft + "s";
        }
        if (style == ActionBarStyle.ICE) {
            return ChatColor.BLUE + "[" + ChatColor.AQUA + filled + ChatColor.DARK_GRAY + empty + ChatColor.BLUE + "] "
                + ChatColor.AQUA + label + " " + ChatColor.WHITE + secondsLeft + "s";
        }
        if (style == ActionBarStyle.LIGHTNING) {
            return ChatColor.GOLD + "[" + ChatColor.YELLOW + filled + ChatColor.DARK_GRAY + empty + ChatColor.GOLD + "] "
                + ChatColor.YELLOW + label + " " + ChatColor.WHITE + secondsLeft + "s";
        }
        if (style == ActionBarStyle.WARDEN) {
            return ChatColor.DARK_PURPLE + "[" + ChatColor.LIGHT_PURPLE + filled + ChatColor.DARK_GRAY + empty + ChatColor.DARK_PURPLE + "] "
                + ChatColor.LIGHT_PURPLE + label + " " + ChatColor.WHITE + secondsLeft + "s";
        }
        if (style == ActionBarStyle.SOUL) {
            return ChatColor.DARK_AQUA + "[" + ChatColor.AQUA + filled + ChatColor.DARK_GRAY + empty + ChatColor.DARK_AQUA + "] "
                + ChatColor.AQUA + label + " " + ChatColor.WHITE + secondsLeft + "s";
        }
        if (style == ActionBarStyle.HELL) {
            return ChatColor.DARK_RED + "[" + ChatColor.RED + filled + ChatColor.DARK_GRAY + empty + ChatColor.DARK_RED + "] "
                + ChatColor.RED + label + " " + ChatColor.WHITE + secondsLeft + "s";
        }
        if (style == ActionBarStyle.STONE) {
            return ChatColor.GRAY + "[" + ChatColor.GOLD + filled + ChatColor.DARK_GRAY + empty + ChatColor.GRAY + "] "
                + ChatColor.GOLD + label + " " + ChatColor.WHITE + secondsLeft + "s";
        }
        return ChatColor.DARK_GREEN + "[" + ChatColor.GREEN + filled + ChatColor.DARK_GRAY + empty + ChatColor.DARK_GREEN + "] "
            + ChatColor.GREEN + label + " " + ChatColor.WHITE + secondsLeft + "s";
    }

    private void removeBossBar(UUID playerId, String key) {
        Map<String, BossBarHolder> playerBars = bars.get(playerId);
        if (playerBars == null) {
            return;
        }
        BossBarHolder old = playerBars.remove(key);
        if (old != null) {
            old.bar.removeAll();
        }
        if (playerBars.isEmpty()) {
            bars.remove(playerId);
        }
    }

    private Palette palette(ActionBarStyle style) {
        if (style == ActionBarStyle.WEB) {
            return new Palette(ChatColor.GRAY, ChatColor.WHITE);
        }
        if (style == ActionBarStyle.ICE) {
            return new Palette(ChatColor.BLUE, ChatColor.AQUA);
        }
        if (style == ActionBarStyle.LIGHTNING) {
            return new Palette(ChatColor.GOLD, ChatColor.YELLOW);
        }
        if (style == ActionBarStyle.WARDEN) {
            return new Palette(ChatColor.DARK_PURPLE, ChatColor.LIGHT_PURPLE);
        }
        if (style == ActionBarStyle.SOUL) {
            return new Palette(ChatColor.DARK_AQUA, ChatColor.AQUA);
        }
        if (style == ActionBarStyle.HELL) {
            return new Palette(ChatColor.DARK_RED, ChatColor.RED);
        }
        if (style == ActionBarStyle.STONE) {
            return new Palette(ChatColor.GRAY, ChatColor.GOLD);
        }
        return new Palette(ChatColor.DARK_GREEN, ChatColor.GREEN);
    }

    private record BossBarHolder(BossBar bar, long startTime, long endTime, String label) {}
    private record ActionBarHolder(long startTime, long endTime, String label, ActionBarStyle style) {}
    private record PairHud(String leftName, String rightName, String leftKey, String rightKey, ActionBarStyle style) {}
    private record Palette(ChatColor frame, ChatColor active) {}
    private record CooldownSlot(String name, String time, double progress) {}
    private enum ActionBarStyle { EMERALD, WEB, ICE, LIGHTNING, WARDEN, SOUL, HELL, STONE }
}
