package com.legendaryweaponssmp.hud;

import com.legendaryweaponssmp.config.ConfigManager;
import com.legendaryweaponssmp.config.SafeYamlFiles;
import com.legendaryweaponssmp.resourcepack.ResourcePackManager;
import com.legendaryweaponssmp.weapons.WeaponType;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Vanilla client HUD renderer. Every visible state is one pre-baked bitmap glyph.
 */
public class BetterHudIntegration implements Listener {
    private static final Key RITUAL_FONT = Key.key("legendary", "ritual_hud");
    private static final Key COOLDOWN_FONT = Key.key("legendary", "cooldown_hud");

    private static final int RITUAL_GLYPH_BASE = 0xE000;
    private static final int COOLDOWN_GLYPH_BASE = 0xE400;
    private static final int COOLDOWN_RIGHT_ANCHOR_GLYPH = 0xE4F0;
    private static final int POSITIVE_SPACE_GLYPH_BASE = 0xE500;
    private static final int NEGATIVE_SPACE_GLYPH_BASE = 0xE520;

    private static final int RITUAL_FONT_HEIGHT = 110;
    private static final int RITUAL_FONT_ASCENT = 15;
    private static final int COOLDOWN_FONT_HEIGHT = 80;
    private static final int COOLDOWN_FONT_ASCENT = 52;
    private static final int COOLDOWN_RIGHT_ADVANCE = 600;
    private static final int COOLDOWN_GLYPH_ADVANCE = 312;
    private static final int COOLDOWN_LINE_ADVANCE = COOLDOWN_RIGHT_ADVANCE + COOLDOWN_GLYPH_ADVANCE;

    private static final String HUD_RESOURCE_ROOT = "hud/";
    private static final String RITUAL_ATLAS_REVISION = "single-glyph-atlas-v5";
    private static final String COOLDOWN_ATLAS_REVISION = "single-glyph-atlas-v4";
    private static final String RITUAL_MARKER = ".ritual-atlas-key";
    private static final String COOLDOWN_MARKER = ".cooldown-atlas-key";
    private static final String DEFAULT_COORDINATES = "X 0 Y 0 Z 0";
    private static final String DUNGEON_RITUAL_ID = "dungeon";

    private final JavaPlugin plugin;
    private final CompositeHudFrames compositeFrames;
    private final Object cacheLock = new Object();
    private final Map<UUID, BossBar> ritualBars = new HashMap<>();
    private final Map<UUID, RitualVisualState> ritualStates = new HashMap<>();
    private final Map<UUID, String> ritualActionLabels = new HashMap<>();
    private final Map<UUID, CooldownVisualState> cooldownStates = new HashMap<>();

    private ResourcePackManager resourcePackManager;
    private volatile String activeCoordinates = "";
    private volatile String activeRitualId = "";
    private volatile String generatedCoordinateKey = "";
    private volatile String generatingCoordinateKey = "";
    private volatile String pendingCoordinateRecheckKey = "";
    private boolean initialized;

    public BetterHudIntegration(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.compositeFrames = new CompositeHudFrames(plugin);
    }

    public void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("Static single-glyph composite HUD enabled; intro fade/shake animations are disabled.");
    }

    public void attachResourcePackManager(ResourcePackManager resourcePackManager) {
        this.resourcePackManager = resourcePackManager;
    }

    public void shutdown() {
        for (Map.Entry<UUID, BossBar> entry : ritualBars.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.hideBossBar(entry.getValue());
            }
        }
        ritualBars.clear();
        ritualStates.clear();
        ritualActionLabels.clear();
        cooldownStates.clear();
    }

    public boolean isAvailable() {
        return initialized;
    }

    public static boolean isBetterHudEnabled() {
        return false;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        clearState(event.getPlayer(), false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearState(event.getPlayer(), true);
    }

    public boolean updateRitual(Player player,
                                String ritualId,
                                String coordinates,
                                String timerText,
                                String stageText,
                                double progress,
                                boolean inRitualArea,
                                boolean ritualPaused,
                                boolean waitingForCraft) {
        if (!initialized || player == null || !player.isOnline()) {
            return false;
        }
        String normalizedRitualId = normalizeRitualId(ritualId);
        String weaponId = labelWeaponId(normalizedRitualId);
        boolean dungeonRitual = isDungeonRitualId(normalizedRitualId);
        WeaponType weaponType = WeaponType.byId(weaponId).orElse(WeaponType.FROSTNOVA_CHAKRAM);
        String displayCoordinates = preferredCoordinates(coordinates);
        if (isUsefulCoordinates(displayCoordinates)) {
            activeCoordinates = displayCoordinates;
        } else if (activeCoordinates.isBlank()) {
            activeCoordinates = displayCoordinates;
        }
        activeRitualId = normalizedRitualId;
        ensureRitualPack(displayCoordinates, normalizedRitualId);
        scheduleCoordinateRecheck(displayCoordinates, normalizedRitualId);

        int timerSeconds = parseTimerSeconds(timerText);
        int progressStep = clampPercent(progress);
        RitualStage stage = RitualStage.RUNNING;
        if (waitingForCraft) {
            stage = RitualStage.CRAFTING;
        } else if (ritualPaused
            || "RITUAL PAUSED - RETURN TO AREA".equals(stageText)
            || "RETURN TO RITUAL AREA".equals(stageText)) {
            stage = RitualStage.PAUSED;
        }
        RitualVisualState state = new RitualVisualState(timerSeconds, progressStep, stage);
        UUID playerId = player.getUniqueId();
        ritualStates.put(playerId, state);
        ritualActionLabels.put(
            playerId,
            dungeonRitual && !inRitualArea
                ? "WEAPON DUNGEON"
                : (weaponType.displayName() + " Ritual").toUpperCase(Locale.ROOT)
        );

        BossBar bar = ritualBars.computeIfAbsent(playerId, ignored -> createRitualBar(player));
        bar.name(ritualComponent(frameFor(state)));
        renderActionBar(player);
        return true;
    }

    public void clearRitual(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        BossBar bar = ritualBars.remove(playerId);
        if (bar != null) {
            player.hideBossBar(bar);
        }
        ritualStates.remove(playerId);
        ritualActionLabels.remove(playerId);
        renderActionBar(player);
    }

    public boolean updateCooldown(Player player,
                                  String leftName,
                                  String leftTime,
                                  double leftProgress,
                                  String rightName,
                                  String rightTime,
                                  double rightProgress) {
        if (!initialized || player == null || !player.isOnline()) {
            return false;
        }
        int leftStep = clampCooldownStep(leftProgress);
        int rightStep = clampCooldownStep(rightProgress);
        UUID playerId = player.getUniqueId();
        cooldownStates.put(
            playerId,
            new CooldownVisualState(leftStep, rightStep)
        );
        renderActionBar(player);
        return true;
    }

    public void clearCooldown(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        cooldownStates.remove(playerId);
        renderActionBar(player);
    }

    public void ensureHud(Player player) {
        // Direct vanilla rendering has no attachment state.
    }

    public boolean isResourcePackCurrent() {
        RitualMetadata metadata = desiredRitualMetadata();
        String expected = coordinateKey(metadata.coordinates(), titleFor(metadata.ritualId()));
        return expected.equals(generatedCoordinateKey) && generatingCoordinateKey.isBlank();
    }

    public void ensureCurrentResourcePack() {
        RitualMetadata metadata = desiredRitualMetadata();
        ensureRitualPack(metadata.coordinates(), metadata.ritualId());
    }

    public void writeResourcePackAssets(File packRoot) throws IOException {
        RitualMetadata desired = desiredRitualMetadata();
        String coordinates = desired.coordinates();
        String title = titleFor(desired.ritualId());
        File cacheRoot = new File(plugin.getDataFolder(), "vanilla-hud-atlas-cache");
        File ritualCache = new File(cacheRoot, "ritual");
        File cooldownCache = new File(cacheRoot, "cooldown");
        synchronized (cacheLock) {
            ensureRitualCache(ritualCache, coordinates, title);
            ensureCooldownCache(cooldownCache);
            File ritualTarget = new File(packRoot, "assets/legendary/textures/font/ritual_hud");
            File cooldownTarget = new File(packRoot, "assets/legendary/textures/font/cooldown_hud");
            copyPngFiles(ritualCache, ritualTarget);
            copyPngFiles(cooldownCache, cooldownTarget);
        }
        writeRitualFont(new File(packRoot, "assets/legendary/font/ritual_hud.json"));
        writeCooldownFont(new File(packRoot, "assets/legendary/font/cooldown_hud.json"));
        generatedCoordinateKey = coordinateKey(coordinates, title);
        generatingCoordinateKey = "";
    }

    private BossBar createRitualBar(Player player) {
        BossBar bar = BossBar.bossBar(
            ritualComponent(CompositeHudFrames.ritualCraftingFrame(0)),
            0.0f,
            BossBar.Color.PURPLE,
            BossBar.Overlay.PROGRESS
        );
        player.showBossBar(bar);
        return bar;
    }

    private Component ritualComponent(int frameIndex) {
        int safeFrame = Math.max(0, Math.min(CompositeHudFrames.RITUAL_FRAME_COUNT - 1, frameIndex));
        return Component.text(String.valueOf((char) (RITUAL_GLYPH_BASE + safeFrame))).font(RITUAL_FONT);
    }

    private Component cooldownComponent(int frameIndex) {
        int safeFrame = Math.max(0, Math.min(CompositeHudFrames.COOLDOWN_FRAME_COUNT - 1, frameIndex));
        return Component.text(
            String.valueOf((char) COOLDOWN_RIGHT_ANCHOR_GLYPH)
                + (char) (COOLDOWN_GLYPH_BASE + safeFrame)
        ).font(COOLDOWN_FONT);
    }

    private void renderActionBar(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        String ritualLabel = ritualActionLabels.get(playerId);
        CooldownVisualState cooldown = cooldownStates.get(playerId);
        if (ritualLabel == null && cooldown == null) {
            player.sendActionBar(Component.empty());
            return;
        }
        if (cooldown == null) {
            player.sendActionBar(ritualLabelComponent(ritualLabel));
            return;
        }
        int cooldownFrame = CompositeHudFrames.cooldownFrame(cooldown.leftStep(), cooldown.rightStep());
        if (ritualLabel == null) {
            player.sendActionBar(cooldownComponent(cooldownFrame));
            return;
        }

        int textWidth = minecraftTextWidth(ritualLabel);
        int prefixWidth = Math.max(0, (COOLDOWN_LINE_ADVANCE - textWidth) / 2);
        int resetWidth = -(prefixWidth + textWidth);
        Component combined = Component.empty()
            .append(spacingComponent(prefixWidth))
            .append(ritualLabelComponent(ritualLabel))
            .append(spacingComponent(resetWidth))
            .append(cooldownComponent(cooldownFrame));
        player.sendActionBar(combined);
    }

    private Component ritualLabelComponent(String label) {
        return Component.text(label == null ? "" : label)
            .color(TextColor.color(214, 162, 255));
    }

    private Component spacingComponent(int pixels) {
        if (pixels == 0) {
            return Component.empty();
        }
        int base = pixels > 0 ? POSITIVE_SPACE_GLYPH_BASE : NEGATIVE_SPACE_GLYPH_BASE;
        int remaining = Math.abs(pixels);
        StringBuilder glyphs = new StringBuilder();
        for (int bit = 10; bit >= 0; bit--) {
            int value = 1 << bit;
            if (remaining >= value) {
                glyphs.append((char) (base + bit));
                remaining -= value;
            }
        }
        return Component.text(glyphs.toString()).font(COOLDOWN_FONT);
    }

    private int minecraftTextWidth(String text) {
        int width = 0;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            width += switch (character) {
                case ' ' -> 4;
                case 'I' -> 4;
                case '.', ',', ':', ';', '!', '\'', '|' -> 2;
                case '(', ')', '[', ']', '{', '}', 'T', 'F', 'K', 'X' -> 5;
                default -> 6;
            };
        }
        return width;
    }

    private int frameFor(RitualVisualState state) {
        return switch (state.stage()) {
            case CRAFTING -> CompositeHudFrames.ritualCraftingFrame(state.progressStep());
            case PAUSED -> CompositeHudFrames.ritualPausedFrame(state.timerSeconds());
            case RUNNING -> CompositeHudFrames.ritualRunningFrame(state.timerSeconds());
        };
    }

    private int parseTimerSeconds(String timerText) {
        if (timerText == null) {
            return 0;
        }
        String[] parts = timerText.split(":", 2);
        if (parts.length != 2) {
            return 0;
        }
        try {
            int minutes = Integer.parseInt(parts[0]);
            int seconds = Integer.parseInt(parts[1]);
            return Math.max(0, Math.min(600, (minutes * 60) + seconds));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private int clampPercent(double progress) {
        return Math.max(0, Math.min(100, (int) Math.round(progress * 100.0)));
    }

    private int clampCooldownStep(double progress) {
        return Math.max(0, Math.min(10, (int) Math.round(progress * 10.0)));
    }

    private String preferredCoordinates(String coordinates) {
        String sanitized = sanitizeCoordinates(coordinates);
        if (isUsefulCoordinates(sanitized)) {
            return sanitized;
        }
        if (isUsefulCoordinates(activeCoordinates)) {
            return activeCoordinates;
        }
        RitualMetadata persisted = loadPersistedRitualMetadata();
        if (isUsefulCoordinates(persisted.coordinates())) {
            return persisted.coordinates();
        }
        return sanitized;
    }

    private String sanitizeCoordinates(String coordinates) {
        if (coordinates == null || coordinates.isBlank()) {
            return DEFAULT_COORDINATES;
        }
        return coordinates.trim().replaceAll("\\s+", " ");
    }

    private boolean isUsefulCoordinates(String coordinates) {
        String sanitized = sanitizeCoordinates(coordinates);
        return !DEFAULT_COORDINATES.equals(sanitized);
    }

    private String normalizeRitualId(String ritualId) {
        if (ritualId == null || ritualId.isBlank()) {
            return WeaponType.FROSTNOVA_CHAKRAM.id();
        }
        String normalized = ritualId.toLowerCase(Locale.ROOT);
        if ("all".equals(normalized)) {
            return DUNGEON_RITUAL_ID;
        }
        return normalized;
    }

    private boolean isDungeonRitualId(String ritualId) {
        return DUNGEON_RITUAL_ID.equals(ritualId) || ritualId.startsWith(DUNGEON_RITUAL_ID + ":");
    }

    private String labelWeaponId(String ritualId) {
        if (ritualId != null && ritualId.startsWith(DUNGEON_RITUAL_ID + ":")) {
            return ritualId.substring((DUNGEON_RITUAL_ID + ":").length());
        }
        return ritualId;
    }

    private String titleFor(String ritualId) {
        return isDungeonRitualId(normalizeRitualId(ritualId))
            ? "LEGENDARY WEAPONS DUNGEON"
            : "LEGENDARY WEAPONS RITUAL";
    }

    private void scheduleCoordinateRecheck(String coordinates, String ritualId) {
        if (!isUsefulCoordinates(coordinates)) {
            return;
        }
        String key = coordinateKey(coordinates, titleFor(ritualId));
        if (key.equals(generatedCoordinateKey) || key.equals(pendingCoordinateRecheckKey)) {
            return;
        }
        pendingCoordinateRecheckKey = key;
        Bukkit.getScheduler().runTaskLater(plugin, () -> ensureRitualPack(coordinates, ritualId), 40L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            ensureRitualPack(coordinates, ritualId);
            if (key.equals(pendingCoordinateRecheckKey)) {
                pendingCoordinateRecheckKey = "";
            }
        }, 700L);
    }

    private void ensureRitualPack(String coordinates, String ritualId) {
        String title = titleFor(ritualId);
        String key = coordinateKey(coordinates, title);
        if (key.equals(generatedCoordinateKey) || key.equals(generatingCoordinateKey)) {
            return;
        }
        generatingCoordinateKey = key;
        if (resourcePackManager != null) {
            resourcePackManager.regenerateAndBroadcast();
        }
    }

    private void ensureRitualCache(File cache,
                                   String coordinates,
                                   String title) throws IOException {
        String cacheKey = coordinateKey(coordinates, title);
        if (markerMatches(cache, RITUAL_MARKER, cacheKey)
            && pngCount(cache) == CompositeHudFrames.ritualAtlasCount()) {
            return;
        }
        File temp = new File(cache.getParentFile(), "ritual-building");
        deleteDirectory(temp);
        temp.mkdirs();
        File purpleBar = new File(temp, "purple_segment_bar.png");
        copyHudAsset("purple_segment_bar.png", purpleBar);
        plugin.getLogger().info("Generating single-glyph ritual atlases...");
        compositeFrames.generateRitualAtlases(temp, purpleBar, coordinates, title);
        Files.deleteIfExists(purpleBar.toPath());
        writeMarker(temp, RITUAL_MARKER, cacheKey);
        deleteDirectory(cache);
        Files.move(temp.toPath(), cache.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private void ensureCooldownCache(File cache) throws IOException {
        if (markerMatches(cache, COOLDOWN_MARKER, COOLDOWN_ATLAS_REVISION)
            && pngCount(cache) == CompositeHudFrames.cooldownAtlasCount()) {
            return;
        }
        File temp = new File(cache.getParentFile(), "cooldown-building");
        deleteDirectory(temp);
        temp.mkdirs();
        File purpleBar = new File(temp, "purple_segment_bar.png");
        copyHudAsset("purple_segment_bar.png", purpleBar);
        plugin.getLogger().info("Generating single-glyph cooldown atlases...");
        compositeFrames.generateCooldownAtlases(temp, purpleBar);
        Files.deleteIfExists(purpleBar.toPath());
        writeMarker(temp, COOLDOWN_MARKER, COOLDOWN_ATLAS_REVISION);
        deleteDirectory(cache);
        Files.move(temp.toPath(), cache.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private int pngCount(File directory) {
        File[] files = directory.listFiles((ignored, name) -> name.endsWith(".png"));
        return files == null ? 0 : files.length;
    }

    private void copyPngFiles(File source, File target) throws IOException {
        deleteDirectory(target);
        target.mkdirs();
        File[] files = source.listFiles((ignored, name) -> name.endsWith(".png"));
        if (files == null) {
            throw new IOException("HUD atlas cache is unreadable: " + source);
        }
        for (File file : files) {
            Files.copy(file.toPath(), new File(target, file.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void writeRitualFont(File file) throws IOException {
        writeAtlasFont(
            file,
            "legendary:font/ritual_hud/",
            "ritual_atlas_",
            CompositeHudFrames.ritualAtlasCount(),
            CompositeHudFrames.RITUAL_ATLAS_COLUMNS,
            CompositeHudFrames.RITUAL_ATLAS_ROWS,
            CompositeHudFrames.RITUAL_ATLAS_CAPACITY,
            RITUAL_GLYPH_BASE,
            RITUAL_FONT_ASCENT,
            RITUAL_FONT_HEIGHT,
            Map.of()
        );
    }

    private void writeCooldownFont(File file) throws IOException {
        writeAtlasFont(
            file,
            "legendary:font/cooldown_hud/",
            "cooldown_atlas_",
            CompositeHudFrames.cooldownAtlasCount(),
            CompositeHudFrames.COOLDOWN_ATLAS_COLUMNS,
            CompositeHudFrames.COOLDOWN_ATLAS_ROWS,
            CompositeHudFrames.COOLDOWN_ATLAS_CAPACITY,
            COOLDOWN_GLYPH_BASE,
            COOLDOWN_FONT_ASCENT,
            COOLDOWN_FONT_HEIGHT,
            cooldownSpaces()
        );
    }

    private Map<Integer, Integer> cooldownSpaces() {
        Map<Integer, Integer> spaces = new LinkedHashMap<>();
        spaces.put(COOLDOWN_RIGHT_ANCHOR_GLYPH, COOLDOWN_RIGHT_ADVANCE);
        for (int bit = 0; bit <= 10; bit++) {
            int advance = 1 << bit;
            spaces.put(POSITIVE_SPACE_GLYPH_BASE + bit, advance);
            spaces.put(NEGATIVE_SPACE_GLYPH_BASE + bit, -advance);
        }
        return spaces;
    }

    private void writeAtlasFont(File file,
                                String texturePrefix,
                                String atlasPrefix,
                                int atlasCount,
                                int columns,
                                int rows,
                                int atlasCapacity,
                                int glyphBase,
                                int ascent,
                                int height,
                                Map<Integer, Integer> spaces) throws IOException {
        StringBuilder json = new StringBuilder("{\n  \"providers\": [\n");
        int totalProviders = atlasCount + (spaces.isEmpty() ? 0 : 1);
        int written = 0;
        if (!spaces.isEmpty()) {
            json.append("    {\n")
                .append("      \"type\": \"space\",\n")
                .append("      \"advances\": {");
            int index = 0;
            for (Map.Entry<Integer, Integer> entry : spaces.entrySet()) {
                if (index++ > 0) {
                    json.append(", ");
                }
                json.append('"')
                    .append(unicodeEscape(entry.getKey()))
                    .append("\": ")
                    .append(entry.getValue());
            }
            json.append("}\n    }");
            if (++written < totalProviders) {
                json.append(',');
            }
            json.append('\n');
        }
        for (int atlasIndex = 0; atlasIndex < atlasCount; atlasIndex++) {
            json.append("    {\n")
                .append("      \"type\": \"bitmap\",\n")
                .append("      \"file\": \"")
                .append(texturePrefix)
                .append(String.format(Locale.ROOT, "%s%02d.png", atlasPrefix, atlasIndex))
                .append("\",\n")
                .append("      \"ascent\": ").append(ascent).append(",\n")
                .append("      \"height\": ").append(height).append(",\n")
                .append("      \"chars\": [\n");
            int firstGlyph = glyphBase + (atlasIndex * atlasCapacity);
            for (int row = 0; row < rows; row++) {
                json.append("        \"");
                for (int column = 0; column < columns; column++) {
                    json.append(unicodeEscape(firstGlyph + (row * columns) + column));
                }
                json.append('"');
                if (row + 1 < rows) {
                    json.append(',');
                }
                json.append('\n');
            }
            json.append("      ]\n")
                .append("    }");
            if (++written < totalProviders) {
                json.append(',');
            }
            json.append('\n');
        }
        json.append("  ]\n}\n");
        writeText(file, json.toString());
    }

    private String unicodeEscape(int codePoint) {
        return String.format(Locale.ROOT, "\\u%04x", codePoint);
    }

    private RitualMetadata loadPersistedRitualMetadata() {
        File file = new File(plugin.getDataFolder(), "active-rituals.yml");
        if (file.isFile()) {
            YamlConfiguration configuration = SafeYamlFiles.loadOrQuarantine(file, plugin.getLogger());
            ConfigurationSection rituals = configuration.getConfigurationSection("rituals");
            if (rituals != null) {
                for (String key : rituals.getKeys(false)) {
                    ConfigurationSection ritual = rituals.getConfigurationSection(key);
                    if (ritual == null
                        || !ritual.getBoolean("revealed", false)
                        || ritual.getBoolean("manually-stopped", false)) {
                        continue;
                    }
                    String coordinates = sanitizeCoordinates("X " + floorCoordinate(ritual.getDouble("x"))
                        + "  Y " + floorCoordinate(ritual.getDouble("y"))
                        + "  Z " + floorCoordinate(ritual.getDouble("z")));
                    String ritualId = ritual.getBoolean("dungeon-ritual", false)
                        ? DUNGEON_RITUAL_ID
                        : ritual.getString("type", key);
                    return new RitualMetadata(coordinates, ritualId);
                }
            }
        }
        return new RitualMetadata(DEFAULT_COORDINATES, WeaponType.FROSTNOVA_CHAKRAM.id());
    }

    private RitualMetadata desiredRitualMetadata() {
        RitualMetadata persisted = loadPersistedRitualMetadata();
        String coordinates = activeCoordinates.isBlank() ? persisted.coordinates() : activeCoordinates;
        String ritualId = activeRitualId.isBlank() ? persisted.ritualId() : activeRitualId;
        return new RitualMetadata(sanitizeCoordinates(coordinates), normalizeRitualId(ritualId));
    }

    private int floorCoordinate(double value) {
        return (int) Math.floor(value);
    }

    private String coordinateKey(String coordinates, String title) {
        return RITUAL_ATLAS_REVISION + "|" + title + "|" + sanitizeCoordinates(coordinates);
    }

    private boolean markerMatches(File dir, String markerName, String expected) {
        File marker = new File(dir, markerName);
        if (!marker.isFile()) {
            return false;
        }
        try {
            return expected.equals(Files.readString(marker.toPath(), StandardCharsets.UTF_8).trim());
        } catch (IOException ignored) {
            return false;
        }
    }

    private void writeMarker(File dir, String markerName, String value) throws IOException {
        dir.mkdirs();
        Files.writeString(new File(dir, markerName).toPath(), value, StandardCharsets.UTF_8);
    }

    private void copyHudAsset(String name, File file) throws IOException {
        try (InputStream input = plugin.getResource(HUD_RESOURCE_ROOT + name)) {
            if (input == null) {
                throw new IOException("Bundled HUD asset is missing: " + name);
            }
            Files.copy(input, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteDirectory(File directory) throws IOException {
        if (!directory.exists()) {
            return;
        }
        try (var paths = Files.walk(directory.toPath())) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private void writeText(File file, String text) throws IOException {
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
    }

    private void clearState(Player player, boolean quitting) {
        UUID playerId = player.getUniqueId();
        BossBar bar = ritualBars.remove(playerId);
        if (bar != null && !quitting) {
            player.hideBossBar(bar);
        }
        ritualStates.remove(playerId);
        ritualActionLabels.remove(playerId);
        cooldownStates.remove(playerId);
    }

    private enum RitualStage {
        RUNNING,
        CRAFTING,
        PAUSED
    }

    private record RitualVisualState(int timerSeconds, int progressStep, RitualStage stage) {}

    private record CooldownVisualState(int leftStep, int rightStep) {}

    private record RitualMetadata(String coordinates, String ritualId) {}
}
