package com.legendaryweaponssmp.rituals;

import com.legendaryweaponssmp.animations.AnimationService;
import com.legendaryweaponssmp.config.ConfigManager;
import com.legendaryweaponssmp.config.SafeYamlFiles;
import com.legendaryweaponssmp.core.MessageService;
import com.legendaryweaponssmp.hud.BetterHudIntegration;
import com.legendaryweaponssmp.particles.ParticleService;
import com.legendaryweaponssmp.structures.RitualStructureBuilder;
import com.legendaryweaponssmp.weapons.LegendaryStateStore;
import com.legendaryweaponssmp.weapons.WeaponItemFactory;
import com.legendaryweaponssmp.weapons.WeaponType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class RitualManager {
    private static final TextColor[] TITLE_COLORS = new TextColor[]{
        TextColor.color(255, 90, 188),
        TextColor.color(255, 208, 84),
        TextColor.color(84, 214, 255),
        TextColor.color(176, 125, 255),
        TextColor.color(104, 255, 176)
    };
    private static final String FORGE_BASE_MODEL = "ritual/forge_base";
    private static final String FORGE_WORKBENCH_MODEL = "ritual/forge_workbench";
    private static final float FORGE_BASE_WIDTH = 8.2f;
    private static final float FORGE_BASE_HEIGHT_SCALE = 8.42f;
    private static final float FORGE_BASE_Y = 4.10f;
    private static final float FORGE_WORKBENCH_Y = 10.42f;
    private static final String RITUAL_DISPLAY_TAG = "legendary_ritual_display";
    private static final String RITUAL_DISPLAY_TYPE_PREFIX = "legendary_ritual_type_";
    private static final int FLOATING_DUNGEON_SURFACE_SEARCH_Y = 241;
    private static final double[][] DUNGEON_RITUAL_OFFSETS = new double[][]{
        {0, -270},
        {110, -247},
        {201, -181},
        {257, -84},
        {268, 28},
        {234, 135},
        {158, 219},
        {56, 264},
        {-56, 264},
        {-158, 219},
        {-234, 135},
        {-268, 28},
        {-257, -84},
        {-201, -181},
        {-110, -247}
    };
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageService messageService;
    private final LegendaryStateStore stateStore;
    private final WeaponItemFactory itemFactory;
    private final ParticleService particleService;
    private final AnimationService animationService;
    private final RitualStructureBuilder structureBuilder;
    private final File activeRitualsFile;
    private final Map<WeaponType, RitualSession> activeByWeapon = new EnumMap<>(WeaponType.class);
    private final Map<WeaponType, RitualSession> residualByWeapon = new EnumMap<>(WeaponType.class);
    private final Map<WeaponType, DungeonParticleProfile> dungeonParticleProfiles = new EnumMap<>(WeaponType.class);
    private final Map<UUID, WeaponType> hudSelections = new HashMap<>();
    private RitualBoxService ritualBoxService;
    private BetterHudIntegration betterHudIntegration;
    private BukkitTask autosaveTask;
    private BukkitTask dungeonParticleTask;

    public RitualManager(JavaPlugin plugin,
                         ConfigManager configManager,
                         MessageService messageService,
                         LegendaryStateStore stateStore,
                         WeaponItemFactory itemFactory,
                         ParticleService particleService,
                         AnimationService animationService,
                         RitualStructureBuilder structureBuilder) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageService = messageService;
        this.stateStore = stateStore;
        this.itemFactory = itemFactory;
        this.particleService = particleService;
        this.animationService = animationService;
        this.structureBuilder = structureBuilder;
        this.activeRitualsFile = new File(plugin.getDataFolder(), "active-rituals.yml");
    }

    public void attachRitualBoxService(RitualBoxService ritualBoxService) {
        this.ritualBoxService = ritualBoxService;
    }

    public void attachBetterHudIntegration(BetterHudIntegration betterHudIntegration) {
        this.betterHudIntegration = betterHudIntegration;
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    public synchronized void startAutosave() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        autosaveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            synchronized (RitualManager.this) {
                if (!activeByWeapon.isEmpty()) {
                    saveActiveRituals();
                }
            }
        }, 100L, 100L);
        startDungeonParticleTask();
    }

    private synchronized void startDungeonParticleTask() {
        if (dungeonParticleTask != null && !dungeonParticleTask.isCancelled()) {
            dungeonParticleTask.cancel();
        }
        dungeonParticleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            List<RitualSession> dungeonSessions;
            synchronized (RitualManager.this) {
                dungeonSessions = activeByWeapon.values().stream()
                    .filter(session -> session.dungeonRitual() && !session.manuallyStopped())
                    .toList();
            }
            for (RitualSession session : dungeonSessions) {
                spawnDungeonZoneRing(session);
            }
        }, 20L, 5L);
    }

    public synchronized boolean hasActiveRitual() {
        return !activeByWeapon.isEmpty();
    }

    public synchronized boolean startRitual(Player starter, WeaponType type, Location origin) {
        return startRitual(starter, type, origin, true);
    }

    public synchronized boolean spawnRitual(Player starter, WeaponType type, Location origin) {
        return startRitual(starter, type, origin, false);
    }

    public synchronized int spawnDungeonRituals(Player starter, Location origin) {
        if (starter == null || origin == null || origin.getWorld() == null) {
            return 0;
        }
        List<WeaponType> types = configManager.enabledWeaponTypes();
        int spawned = 0;
        int ritualLimit = configManager.ritualLimit();
        for (int i = 0; i < types.size(); i++) {
            WeaponType type = types.get(i);
            if (!configManager.isWeaponEnabled(type) || activeByWeapon.containsKey(type)) {
                continue;
            }
            if (activeByWeapon.size() >= ritualLimit) {
                messageService.send(starter, "&cActive ritual limit reached (&f" + ritualLimit + "&c). Remaining dungeon altars were skipped.");
                break;
            }
            Location center = dungeonRitualCenter(origin, i);
            RitualStructureBuilder.BuildPlan plan = structureBuilder.createDungeonPlan(center, type);
            RitualSession session = new RitualSession(type, starter.getUniqueId(), center, center.clone(), plan, configManager.ritualDurationSeconds());
            session.setDungeonRitual(true);
            dungeonParticleProfiles.remove(type);
            activeByWeapon.put(type, session);
            stateStore.markRitual(type, starter.getUniqueId());
            beginBuild(session);
            beginOfferingStage(starter, session, false);
            spawned++;
        }
        if (spawned > 0) {
            saveActiveRituals();
        }
        return spawned;
    }

    private synchronized boolean startRitual(Player starter, WeaponType type, Location origin, boolean revealImmediately) {
        if (!configManager.isWeaponEnabled(type)) {
            messageService.send(starter, "&c" + type.displayName() + " is currently disabled.");
            return false;
        }
        if (activeByWeapon.containsKey(type)) {
            messageService.send(starter, "&c" + type.displayName() + " already has an active ritual.");
            return false;
        }
        int ritualLimit = configManager.ritualLimit();
        if (activeByWeapon.size() >= ritualLimit) {
            messageService.send(starter, "&cActive ritual limit reached (&f" + ritualLimit + "&c).");
            return false;
        }
        Location center = new Location(origin.getWorld(), origin.getBlockX() + 0.5, origin.getBlockY(), origin.getBlockZ() + 0.5);
        RitualStructureBuilder.BuildPlan plan = structureBuilder.createPlan(center, type);
        RitualSession session = new RitualSession(type, starter.getUniqueId(), center, center.clone(), plan, configManager.ritualDurationSeconds());
        activeByWeapon.put(type, session);
        stateStore.markRitual(type, starter.getUniqueId());
        beginBuild(session);
        beginOfferingStage(starter, session, revealImmediately);
        saveActiveRituals();
        return true;
    }

    private Location dungeonRitualCenter(Location origin, int index) {
        World world = origin.getWorld();
        double[] offset = DUNGEON_RITUAL_OFFSETS[index % DUNGEON_RITUAL_OFFSETS.length];
        int x = origin.getBlockX() + (int) Math.round(offset[0]);
        int z = origin.getBlockZ() + (int) Math.round(offset[1]);
        int baseY = Math.max(origin.getBlockY(), FLOATING_DUNGEON_SURFACE_SEARCH_Y);
        int[] surface = findNearbyDungeonSurface(world, x, z, baseY, 14);
        return new Location(world, surface[0] + 0.5, surface[1], surface[2] + 0.5);
    }

    private int[] findNearbyDungeonSurface(World world, int x, int z, int baseY, int radius) {
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (r > 0 && Math.abs(dx) != r && Math.abs(dz) != r) {
                        continue;
                    }
                    int y = findDungeonSurfaceY(world, x + dx, z + dz, baseY);
                    if (y != Integer.MIN_VALUE) {
                        return new int[]{x + dx, y, z + dz};
                    }
                }
            }
        }
        int fallbackY = Math.max(world.getMinHeight() + 1, Math.min(world.getMaxHeight() - 8, baseY));
        return new int[]{x, fallbackY, z};
    }

    private int findDungeonSurfaceY(World world, int x, int z, int baseY) {
        int minY = Math.max(world.getMinHeight() + 1, baseY - 36);
        int maxY = Math.min(world.getMaxHeight() - 8, baseY + 96);
        int maxDelta = Math.max(maxY - baseY, baseY - minY);
        for (int delta = 0; delta <= maxDelta; delta++) {
            int up = baseY + delta;
            if (up <= maxY && isDungeonRitualSurface(world, x, up, z)) {
                return up;
            }
            int down = baseY - delta;
            if (down >= minY && isDungeonRitualSurface(world, x, down, z)) {
                return down;
            }
        }
        return Integer.MIN_VALUE;
    }

    private boolean isDungeonRitualSurface(World world, int x, int y, int z) {
        int solidSupport = 0;
        int dungeonSupport = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Material support = world.getBlockAt(x + dx, y - 1, z + dz).getType();
                if (support.isSolid()) {
                    solidSupport++;
                }
                if (isDungeonFloorMaterial(support)) {
                    dungeonSupport++;
                }
            }
        }
        if (solidSupport < 7 || dungeonSupport < 4) {
            return false;
        }
        for (int dy = 0; dy <= 5; dy++) {
            int radius = dy <= 2 ? 1 : 0;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Material space = world.getBlockAt(x + dx, y + dy, z + dz).getType();
                    if (!space.isAir() && space.isSolid()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean isDungeonFloorMaterial(Material material) {
        return material == Material.COBBLED_DEEPSLATE
            || material == Material.GRASS_BLOCK
            || material == Material.DIRT
            || material == Material.STONE
            || material == Material.DEEPSLATE
            || material == Material.POLISHED_DEEPSLATE
            || material == Material.DEEPSLATE_BRICKS
            || material == Material.DEEPSLATE_TILES
            || material == Material.CRACKED_DEEPSLATE_BRICKS
            || material == Material.CRACKED_DEEPSLATE_TILES
            || material == Material.CHISELED_DEEPSLATE
            || material == Material.SMOOTH_BASALT
            || material == Material.BASALT
            || material == Material.POLISHED_BASALT
            || material == Material.BLACKSTONE
            || material == Material.POLISHED_BLACKSTONE
            || material == Material.POLISHED_BLACKSTONE_BRICKS
            || material == Material.CRACKED_POLISHED_BLACKSTONE_BRICKS
            || material == Material.WARPED_HYPHAE
            || material == Material.STRIPPED_WARPED_HYPHAE
            || material == Material.WARPED_PLANKS
            || material == Material.WARPED_NYLIUM
            || material == Material.TUFF
            || material == Material.CALCITE
            || material == Material.DRIPSTONE_BLOCK;
    }

    public synchronized boolean revealRitual(Player player) {
        return revealRitualSession(player, null).isPresent();
    }

    public synchronized Optional<RitualSession> revealRitualSession(Player player, WeaponType requestedType) {
        RitualSession session = requestedType != null
            ? activeByWeapon.get(requestedType)
            : (player != null ? nearestHiddenSession(player.getLocation()) : currentSession());
        if (session == null) {
            return Optional.empty();
        }
        RitualSession revealed = currentRevealedSession();
        if (revealed != null && revealed != session) {
            return Optional.empty();
        }
        if (!session.revealed()) {
            session.setRevealed(true);
            refreshOfferingDisplays(session);
            beginBossBar(session);
            broadcastOfferingStart(player, session);
            if (session.offeringRequired().isEmpty()) {
                completeOfferingStage(session);
            }
            saveActiveRituals();
        }
        return Optional.of(session);
    }

    public synchronized int revealDungeonRituals(Player player) {
        Optional<Location> location = dungeonLocationCenter();
        location.ifPresent(center -> broadcastDungeonLocation(player, center));
        return location.isPresent() ? 1 : 0;
    }

    private Optional<Location> dungeonLocationCenter() {
        World world = null;
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        int count = 0;
        for (RitualSession session : activeByWeapon.values()) {
            if (!session.dungeonRitual() || session.manuallyStopped() || session.center().getWorld() == null) {
                continue;
            }
            if (world == null) {
                world = session.center().getWorld();
            }
            if (!world.equals(session.center().getWorld())) {
                continue;
            }
            x += session.center().getX();
            y += session.center().getY();
            z += session.center().getZ();
            count++;
        }
        if (world == null || count == 0) {
            return Optional.empty();
        }
        return Optional.of(new Location(world, x / count, y / count, z / count));
    }

    public synchronized Optional<RitualSession> revealedSession() {
        return Optional.ofNullable(currentRevealedSession());
    }

    public synchronized boolean unrevealRitual(WeaponType requestedType) {
        RitualSession session = requestedType != null ? activeByWeapon.get(requestedType) : currentRevealedSession();
        if (session == null || !session.revealed() || session.ritualStarted()) {
            return false;
        }
        session.setRevealed(false);
        removeOfferingDisplays(session);
        removeVanillaBossBar(session);
        hudSelections.values().removeIf(type -> type == session.weaponType());
        refreshBetterHudRituals();
        saveActiveRituals();
        return true;
    }

    public synchronized void onRitualBlockBreak(Location location) {
        RitualSession session = anchorSessionAt(location);
        if (session == null) {
            return;
        }
        cleanupRitualAnchor(session, null);
    }

    private void beginBuild(RitualSession session) {
        Material coreMaterial = Material.RESPAWN_ANCHOR;
        session.setExpectedCoreMaterial(coreMaterial);
        displacePlayersFromSpawnArea(session);
        ensureChunkLoaded(session);
        if (!session.dungeonRitual()) {
            session.buildPlan().clearSpawnVolume(2, 0, 8);
        }
        session.buildPlan().placeAll();
        session.coreLocation().getBlock().setType(coreMaterial, false);
        World world = session.center().getWorld();
        world.playSound(session.center(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.85f);
        world.playSound(session.center(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 0.6f);
    }

    private void beginBossBar(RitualSession session) {
        if (!session.revealed() || session.manuallyStopped()) {
            return;
        }
        if (betterHudIntegration != null && betterHudIntegration.isAvailable()) {
            removeVanillaBossBar(session);
            refreshBetterHudRituals();
            return;
        }
        String title = session.weaponType().displayName() + " Ritual at "
            + session.center().getBlockX() + " "
            + session.center().getBlockY() + " "
            + session.center().getBlockZ();
        BossBar bossBar = session.bossBar();
        if (bossBar == null) {
            BarColor color = session.weaponType().barColor();
            bossBar = Bukkit.createBossBar(title, color, BarStyle.SEGMENTED_20);
            session.setBossBar(bossBar);
        }
        Bukkit.getOnlinePlayers().forEach(bossBar::addPlayer);
        refreshBossBar(session);
    }

    public synchronized void refreshPlayerRitualBars(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (betterHudIntegration != null && betterHudIntegration.isAvailable()) {
            RitualSession selected = selectedHudSession(player);
            if (selected == null) {
                betterHudIntegration.clearRitual(player);
            } else {
                updateBetterHudRitual(player, selected);
            }
            return;
        }
        for (RitualSession session : activeByWeapon.values()) {
            if (!session.revealed() || session.manuallyStopped()) {
                continue;
            }
            beginBossBar(session);
            BossBar bossBar = session.bossBar();
            if (bossBar != null) {
                bossBar.addPlayer(player);
                refreshBossBar(session);
            }
        }
    }

    private void beginOfferingStage(Player starter, RitualSession session, boolean revealImmediately) {
        Location center = session.center().clone();
        removePersistedRitualDisplays(session);
        ItemDisplay base = spawnForgeModel(session, center, ritualBaseModel(session.weaponType()));
        session.setCoreDisplay(base);

        ItemDisplay workbench = spawnForgeWorkbench(session, center);
        session.setWorkbenchDisplay(workbench);
        if (session.dungeonRitual()) {
            spawnDungeonNameHologram(session, center);
        }
        Map<Integer, RitualBoxService.RitualOffering> recipe = ritualBoxService != null
            ? ritualBoxService.offeringGrid(session.weaponType())
            : Map.of();
        session.setOfferingRequired(recipe);
        animateForgeBase(base, 0.0, 0.0);
        animateForgeWorkbench(workbench, 0.0);
        if (revealImmediately) {
            session.setRevealed(true);
        }
        if (session.revealed()) {
            refreshOfferingDisplays(session);
            beginBossBar(session);
        }
        if (session.manuallyStopped()) {
            return;
        }

        startOfferingFxTask(session, center, base, workbench);
        if (revealImmediately) {
            broadcastOfferingStart(starter, session);
        }
        if (session.revealed() && recipe.isEmpty()) {
            completeOfferingStage(session);
        }
    }

    private void startOfferingFxTask(RitualSession session, Location center, ItemDisplay base, ItemDisplay workbench) {
        if (session.fxTask() != null && !session.fxTask().isCancelled()) {
            session.fxTask().cancel();
        }
        BukkitTask fxTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int fxTicks;

            @Override
            public void run() {
                RitualSession live = byWeapon(session.weaponType()).orElse(null);
                if (live == null || live != session) {
                    return;
                }
                if (base == null || !base.isValid() || workbench == null || !workbench.isValid()) {
                    return;
                }
                fxTicks++;
                double progress = ritualProgress(session);
                double angle = fxTicks * 0.052;
                animateForgeBase(base, progress, angle);
                animateForgeWorkbench(workbench, progress);

                if (fxTicks % 10 == 0) {
                    spawnForgeAmbient(session.weaponType(), center, fxTicks);
                }
                if (session.revealed() && fxTicks % 5 == 0) {
                    collectNearbyOfferings(session);
                }

                if (session.expectedCoreMaterial() != null && session.coreLocation().getBlock().getType() != session.expectedCoreMaterial()) {
                    failRitual(session.weaponType(), "The Ritual Core was disrupted");
                    return;
                }
                if (!session.dungeonRitual() && session.buildPlan().integrityRatio() < configManager.ritualIntegrityThreshold()) {
                    failRitual(session.weaponType(), "The ritual structure was heavily damaged");
                }
            }
        }, 2L, 1L);
        session.setFxTask(fxTask);
    }

    private void beginWeaponVisuals(RitualSession session) {
        Location center = session.center().clone();
        ItemDisplay weapon = center.getWorld().spawn(center, ItemDisplay.class);
        prepareRitualDisplay(session, weapon);
        weapon.setItemStack(itemFactory.create(session.weaponType()));
        weapon.setBillboard(Display.Billboard.FIXED);
        weapon.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        weapon.setBrightness(new Display.Brightness(15, 15));
        disableFrustumCulling(weapon);
        smoothDisplay(weapon);
        session.addForgeDisplay(weapon);
        session.setDisplay(weapon);
        animateForgeWeapon(weapon, 0.0, 0.0);

        TextDisplay progressDisplay = null;
        if (!usingBetterHud()) {
            progressDisplay = center.getWorld().spawn(center, TextDisplay.class);
            prepareRitualDisplay(session, progressDisplay);
            progressDisplay.setBillboard(Display.Billboard.VERTICAL);
            progressDisplay.setBrightness(new Display.Brightness(15, 15));
            progressDisplay.setShadowed(true);
            progressDisplay.setDefaultBackground(false);
            progressDisplay.setLineWidth(520);
            progressDisplay.setAlignment(TextDisplay.TextAlignment.CENTER);
            disableFrustumCulling(progressDisplay);
            smoothDisplay(progressDisplay);
            session.addForgeDisplay(progressDisplay);
            session.setProgressDisplay(progressDisplay);
            animateRitualProgress(progressDisplay, session, 0.0);
        } else {
            session.setProgressDisplay(null);
        }
        TextDisplay fallbackProgressDisplay = progressDisplay;

        if (session.fxTask() != null && !session.fxTask().isCancelled()) {
            session.fxTask().cancel();
        }
        BukkitTask fxTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int fxTicks;

            @Override
            public void run() {
                RitualSession live = byWeapon(session.weaponType()).orElse(null);
                if (live == null || live != session) {
                    return;
                }
                fxTicks++;
                double progress = ritualProgress(session);
                double angle = fxTicks * 0.052;
                animateForgeBase(session.coreDisplay(), progress, angle);
                animateForgeWorkbench(session.workbenchDisplay(), progress);
                animateForgeWeapon(weapon, progress, angle);
                animateRitualProgress(fallbackProgressDisplay, session, progress);

                if (fxTicks % 10 == 0) {
                    spawnForgeAmbient(session.weaponType(), center, fxTicks);
                }
                if (session.expectedCoreMaterial() != null && session.coreLocation().getBlock().getType() != session.expectedCoreMaterial()) {
                    failRitual(session.weaponType(), "The Ritual Core was disrupted");
                    return;
                }
                if (!session.dungeonRitual() && session.buildPlan().integrityRatio() < configManager.ritualIntegrityThreshold()) {
                    failRitual(session.weaponType(), "The ritual structure was heavily damaged");
                }
            }
        }, 2L, 1L);
        session.setFxTask(fxTask);
    }

    public synchronized boolean acceptOfferingDrop(Player player, Item item) {
        if (item == null || !item.isValid()) {
            return false;
        }
        RitualSession session = offeringSessionAt(item.getLocation());
        if (session == null) {
            return false;
        }
        return consumeOfferingItem(session, item, player);
    }

    private void collectNearbyOfferings(RitualSession session) {
        if (session.manuallyStopped() || !session.revealed() || session.ritualStarted() || session.offeringsComplete()) {
            return;
        }
        Location scanCenter = session.center().clone().add(0.0, FORGE_WORKBENCH_Y + 0.85, 0.0);
        for (org.bukkit.entity.Entity entity : scanCenter.getWorld().getNearbyEntities(scanCenter, 4.2, 3.2, 4.2)) {
            if (entity instanceof Item item) {
                consumeOfferingItem(session, item, null);
                if (session.ritualStarted()) {
                    return;
                }
            }
        }
    }

    private boolean consumeOfferingItem(RitualSession session, Item item, Player source) {
        if (session.manuallyStopped() || !session.revealed() || session.ritualStarted() || session.offeringsComplete() || item == null || !item.isValid()) {
            return false;
        }
        if (!isInsideOfferingZone(session, item.getLocation())) {
            return false;
        }
        ItemStack stack = item.getItemStack();
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        int amount = stack.getAmount();
        int consumed = 0;
        List<Integer> completedSlots = new ArrayList<>();
        while (amount > 0) {
            Integer slot = nextOpenOfferingSlot(session, stack.getType());
            if (slot == null) {
                break;
            }
            RitualBoxService.RitualOffering offering = session.offeringRequired().get(slot);
            int before = session.offeringProvided().getOrDefault(slot, 0);
            int after = before + 1;
            session.offeringProvided().put(slot, after);
            session.highlightOfferingSlot(slot, System.currentTimeMillis() + 500L);
            if (offering != null && before < offering.amount() && after >= offering.amount()) {
                completedSlots.add(slot);
            }
            amount--;
            consumed++;
        }
        if (consumed == 0) {
            return false;
        }
        if (amount <= 0) {
            item.remove();
        } else {
            stack.setAmount(amount);
            item.setItemStack(stack);
            item.setPickupDelay(Math.max(item.getPickupDelay(), 20));
        }
        refreshOfferingDisplays(session);
        refreshBossBar(session);
        Location at = session.center().clone().add(0.0, FORGE_WORKBENCH_Y + 1.0, 0.0);
        at.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at, 2, 0.22, 0.08, 0.22, 0.01);
        at.getWorld().playSound(at, Sound.ENTITY_ITEM_PICKUP, 0.22f, 1.45f);
        if (!completedSlots.isEmpty()) {
            at.getWorld().spawnParticle(Particle.ENCHANTED_HIT, at, 5, 0.25, 0.08, 0.25, 0.02);
            at.getWorld().playSound(at, Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.85f, 1.08f);
        }
        if (source != null) {
            OfferingProgress offeringProgress = offeringProgress(session);
            messageService.action(source, "&aOffering accepted: &f" + prettyMaterial(stack.getType())
                + " &7| &dCrafting " + offeringProgress.provided() + "/" + offeringProgress.required()
                + " (" + offeringProgress.percent() + "%)");
        }
        saveActiveRituals();
        if (session.offeringsComplete()) {
            completeOfferingStage(session);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                synchronized (this) {
                    RitualSession live = byWeapon(session.weaponType()).orElse(null);
                    if (live == session && !session.ritualStarted()) {
                        refreshOfferingDisplays(session);
                    }
                }
            }, 12L);
        }
        return true;
    }

    private boolean isInsideOfferingZone(RitualSession session, Location location) {
        if (!session.center().getWorld().equals(location.getWorld())) {
            return false;
        }
        Location top = session.center().clone().add(0.0, FORGE_WORKBENCH_Y + 0.85, 0.0);
        double dx = location.getX() - top.getX();
        double dz = location.getZ() - top.getZ();
        double dy = Math.abs(location.getY() - top.getY());
        return ((dx * dx) + (dz * dz)) <= 18.0 && dy <= 4.0;
    }

    private Integer nextOpenOfferingSlot(RitualSession session, Material material) {
        for (Map.Entry<Integer, RitualBoxService.RitualOffering> entry : session.offeringRequired().entrySet()) {
            RitualBoxService.RitualOffering offering = entry.getValue();
            if (offering.material() == material && !session.offeringSlotComplete(entry.getKey())) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void refreshOfferingDisplays(RitualSession session) {
        session.clearExpiredOfferingHighlights();
        removeOfferingDisplays(session);
        for (Map.Entry<Integer, RitualBoxService.RitualOffering> entry : session.offeringRequired().entrySet()) {
            spawnOfferingSlot(session, entry.getKey(), entry.getValue(),
                session.offeringSlotComplete(entry.getKey()));
        }
    }

    private void spawnOfferingSlot(RitualSession session, int slot, RitualBoxService.RitualOffering offering, boolean complete) {
        Location center = session.center();
        World world = center.getWorld();
        Material material = offering.material();
        int requiredAmount = offering.amount();
        int providedAmount = session.offeringProvided().getOrDefault(slot, 0);
        boolean highlighted = session.offeringSlotHighlighted(slot);
        float spacing = 0.78f;
        int row = slot / 3;
        int column = slot % 3;
        float x = (column - 1) * spacing;
        float z = (row - 1) * spacing;
        float y = FORGE_WORKBENCH_Y + 0.54f;

        BlockDisplay background = world.spawn(center, BlockDisplay.class);
        prepareRitualDisplay(session, background);
        Material slotMaterial = highlighted ? Material.ORANGE_STAINED_GLASS : (complete ? Material.LIME_STAINED_GLASS : Material.RED_STAINED_GLASS);
        background.setBlock(slotMaterial.createBlockData());
        background.setBillboard(Display.Billboard.FIXED);
        background.setBrightness(new Display.Brightness(15, 15));
        disableFrustumCulling(background);
        smoothDisplay(background);
        background.setTransformation(new Transformation(
            new Vector3f(x - 0.32f, y, z - 0.32f),
            new Quaternionf(),
            new Vector3f(0.64f, 0.008f, 0.64f),
            new Quaternionf()
        ));
        session.addOfferingDisplay(background);

        ItemDisplay icon = world.spawn(center, ItemDisplay.class);
        prepareRitualDisplay(session, icon);
        icon.setItemStack(offeringIconItem(material));
        icon.setBillboard(Display.Billboard.FIXED);
        icon.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
        icon.setBrightness(new Display.Brightness(15, 15));
        disableFrustumCulling(icon);
        icon.setCustomName((complete ? "\u00a7a" : "\u00a7c") + requiredAmount + "x " + prettyMaterial(material));
        icon.setCustomNameVisible(false);
        smoothDisplay(icon);
        icon.setTransformation(new Transformation(
            new Vector3f(x, y + 0.10f, z),
            euler((float) Math.toRadians(-90.0), 0f, 0f),
            new Vector3f(0.40f, 0.40f, 0.40f),
            new Quaternionf()
        ));
        session.addOfferingDisplay(icon);

        TextDisplay label = world.spawn(center, TextDisplay.class);
        prepareRitualDisplay(session, label);
        String amountText = providedAmount > 0 && !complete ? providedAmount + "/" + requiredAmount + "x" : requiredAmount + "x";
        String labelColor = highlighted ? "\u00a76" : (complete ? "\u00a7a" : "\u00a7c");
        String labelText = labelColor + amountText + "\n\u00a7f" + prettyMaterial(material);
        label.setText(labelText);
        label.setBillboard(Display.Billboard.FIXED);
        label.setBrightness(new Display.Brightness(15, 15));
        label.setShadowed(true);
        label.setDefaultBackground(false);
        label.setLineWidth(70);
        label.setAlignment(TextDisplay.TextAlignment.CENTER);
        disableFrustumCulling(label);
        smoothDisplay(label);
        label.setTransformation(new Transformation(
            new Vector3f(x, y + 0.19f, z + 0.20f),
            euler((float) Math.toRadians(-90.0), 0f, 0f),
            new Vector3f(0.23f, 0.23f, 0.23f),
            new Quaternionf()
        ));
        session.addOfferingDisplay(label);
    }

    private void removeOfferingDisplays(RitualSession session) {
        for (Display display : session.offeringDisplays()) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        session.clearOfferingDisplays();
    }

    private void completeOfferingStage(RitualSession session) {
        if (session.manuallyStopped() || session.ritualStarted()) {
            return;
        }
        session.setRitualStarted(true);
        if (session.fxTask() != null) {
            session.fxTask().cancel();
            session.setFxTask(null);
        }
        removeOfferingDisplays(session);
        saveActiveRituals();

        Location center = session.center().clone().add(0.0, FORGE_WORKBENCH_Y + 1.15, 0.0);
        World world = center.getWorld();
        world.spawnParticle(Particle.FIREWORK, center, 24, 0.65, 0.35, 0.65, 0.06);
        world.spawnParticle(Particle.ENCHANTED_HIT, center, 42, 0.8, 0.45, 0.8, 0.08);
        world.playSound(center, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.85f, 0.9f);
        world.playSound(center, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.65f, 1.15f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            synchronized (this) {
                RitualSession live = byWeapon(session.weaponType()).orElse(null);
                if (live == null || live != session) {
                    return;
                }
                beginWeaponVisuals(session);
                beginTimer(session);
                beginBossBar(session);
                broadcastStart(Bukkit.getPlayer(session.starterId()), session);
            }
        }, 16L);
    }

    private String prettyMaterial(Material material) {
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private ItemDisplay spawnForgeModel(RitualSession session, Location center, String modelPath) {
        ItemDisplay display = center.getWorld().spawn(center, ItemDisplay.class);
        prepareRitualDisplay(session, display);
        display.setItemStack(modelItem(modelPath));
        display.setBillboard(Display.Billboard.FIXED);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        display.setBrightness(new Display.Brightness(15, 15));
        disableFrustumCulling(display);
        smoothDisplay(display);
        session.addForgeDisplay(display);
        return display;
    }

    private ItemDisplay spawnForgeWorkbench(RitualSession session, Location center) {
        ItemDisplay display = center.getWorld().spawn(center, ItemDisplay.class);
        prepareRitualDisplay(session, display);
        display.setItemStack(modelItem(FORGE_WORKBENCH_MODEL));
        display.setBillboard(Display.Billboard.FIXED);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        display.setBrightness(new Display.Brightness(15, 15));
        disableFrustumCulling(display);
        smoothDisplay(display);
        session.addForgeDisplay(display);
        return display;
    }

    private ItemStack modelItem(String modelPath) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(NamespacedKey.fromString("legendary:" + modelPath));
        meta.setEnchantmentGlintOverride(false);
        stack.setItemMeta(meta);
        return stack;
    }

    private String ritualBaseModel(WeaponType type) {
        return FORGE_BASE_MODEL + "_" + type.id();
    }

    private ItemStack offeringIconItem(Material material) {
        if (material == Material.OBSIDIAN) {
            return modelItem("offering/obsidian_flat");
        }
        return new ItemStack(material);
    }

    private void smoothDisplay(Display display) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(3);
        display.setTeleportDuration(3);
        display.setViewRange(256.0f);
    }

    private void disableFrustumCulling(Display display) {
        display.setDisplayWidth(0.0f);
        display.setDisplayHeight(0.0f);
    }

    private double ritualProgress(RitualSession session) {
        if (session.totalSeconds() <= 0) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, 1.0 - ((double) session.remainingSeconds() / (double) session.totalSeconds())));
    }

    private double easeOutCubic(double value) {
        double t = Math.max(0.0, Math.min(1.0, value));
        double inverse = 1.0 - t;
        return 1.0 - (inverse * inverse * inverse);
    }

    private void animateForgeBase(ItemDisplay display, double progress, double angle) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setTransformation(new Transformation(
            new Vector3f(0f, FORGE_BASE_Y, 0f),
            new Quaternionf(),
            new Vector3f(FORGE_BASE_WIDTH, FORGE_BASE_HEIGHT_SCALE, FORGE_BASE_WIDTH),
            new Quaternionf()
        ));
    }

    private void animateForgeWorkbench(ItemDisplay display, double progress) {
        if (display == null || !display.isValid()) {
            return;
        }
        float pulse = (float) (0.02 * Math.sin(progress * Math.PI * 18.0));
        float scale = 2.36f + pulse;
        display.setTransformation(new Transformation(
            new Vector3f(0f, FORGE_WORKBENCH_Y, 0f),
            new Quaternionf(),
            new Vector3f(scale, 1.0f + pulse, scale),
            new Quaternionf()
        ));
    }

    private void animateForgeWeapon(ItemDisplay display, double progress, double angle) {
        if (display == null || !display.isValid()) {
            return;
        }
        double reveal = easeOutCubic(progress);
        float scale = (float) (3.10 + (reveal * 1.30));
        float y = FORGE_WORKBENCH_Y + 3.15f + (float) (reveal * 1.25);
        display.setTransformation(new Transformation(
            new Vector3f(0f, y, 0f),
            euler(0f, (float) angle, 0f),
            new Vector3f(scale, scale, scale),
            new Quaternionf()
        ));
    }

    private void animateRitualProgress(TextDisplay display, RitualSession session, double progress) {
        if (display == null || !display.isValid()) {
            return;
        }
        int percent = Math.max(0, Math.min(100, (int) Math.round(progress * 100.0)));
        display.setText("\u00a7dRitual \u00a7f" + percent + "%\n" + progressBar(progress));
        display.setTransformation(new Transformation(
            new Vector3f(0f, FORGE_WORKBENCH_Y + 8.60f, 0f),
            new Quaternionf(),
            new Vector3f(2.45f, 2.45f, 2.45f),
            new Quaternionf()
        ));
    }

    private String progressBar(double progress) {
        int segments = 26;
        int filled = Math.max(0, Math.min(segments, (int) Math.round(progress * segments)));
        return "\u00a78[\u00a7a" + "|".repeat(filled) + "\u00a78" + "|".repeat(segments - filled) + "\u00a78]";
    }

    private void spawnCompletionText(RitualSession session) {
        Location center = session.center();
        TextDisplay text = center.getWorld().spawn(center, TextDisplay.class);
        prepareRitualDisplay(session, text);
        text.setText("\u00a7aFORGED\n\u00a76" + session.weaponType().displayName());
        text.setBillboard(Display.Billboard.VERTICAL);
        text.setBrightness(new Display.Brightness(15, 15));
        text.setShadowed(true);
        text.setDefaultBackground(false);
        text.setLineWidth(220);
        text.setAlignment(TextDisplay.TextAlignment.CENTER);
        disableFrustumCulling(text);
        smoothDisplay(text);
        text.setTransformation(new Transformation(
            new Vector3f(0f, FORGE_WORKBENCH_Y + 2.95f, 0f),
            new Quaternionf(),
            new Vector3f(0.95f, 0.95f, 0.95f),
            new Quaternionf()
        ));
    }

    private void spawnDungeonNameHologram(RitualSession session, Location center) {
        TextDisplay text = center.getWorld().spawn(center, TextDisplay.class);
        prepareRitualDisplay(session, text);
        text.setText(legacyHex(session.weaponType().color()) + "\u00a7l" + session.weaponType().displayName().toUpperCase(Locale.ROOT));
        text.setBillboard(Display.Billboard.VERTICAL);
        text.setBrightness(new Display.Brightness(15, 15));
        text.setShadowed(true);
        text.setSeeThrough(true);
        text.setDefaultBackground(false);
        text.setLineWidth(760);
        text.setAlignment(TextDisplay.TextAlignment.CENTER);
        disableFrustumCulling(text);
        smoothDisplay(text);
        text.setTransformation(new Transformation(
            new Vector3f(0f, FORGE_WORKBENCH_Y + 8.85f, 0f),
            new Quaternionf(),
            new Vector3f(2.85f, 2.85f, 2.85f),
            new Quaternionf()
        ));
        session.addForgeDisplay(text);
    }

    private String legacyHex(Color color) {
        String hex = String.format("%06X", color.asRGB());
        StringBuilder builder = new StringBuilder("\u00a7x");
        for (int i = 0; i < hex.length(); i++) {
            builder.append('\u00a7').append(hex.charAt(i));
        }
        return builder.toString();
    }

    private Quaternionf euler(float x, float y, float z) {
        return new Quaternionf().rotateXYZ(x, y, z);
    }

    private void spawnForgeAmbient(WeaponType type, Location center, int ticks) {
        World world = center.getWorld();
        Location tableTop = center.clone().add(0.0, FORGE_WORKBENCH_Y + 0.72, 0.0);
        world.spawnParticle(Particle.SMOKE, tableTop, 2, 0.34, 0.05, 0.34, 0.01);
    }

    private void spawnDungeonZoneRing(RitualSession session) {
        World world = session.center().getWorld();
        if (world == null) {
            return;
        }
        if (!hasDungeonParticleViewer(session.center())) {
            return;
        }
        DungeonParticleProfile profile = dungeonParticleProfile(session);
        double radius = profile.radius();
        int points = Math.max(56, Math.min(96, (int) Math.round(radius * 3.2)));
        double centerX = session.center().getX();
        double centerZ = session.center().getZ();
        int baseY = profile.baseY();
        long ticks = world.getFullTime();
        Particle.DustOptions outerDust = new Particle.DustOptions(Color.fromRGB(255, 36, 72), 1.55f);
        Particle.DustOptions lineDust = new Particle.DustOptions(Color.fromRGB(118, 0, 22), 0.9f);
        Particle.DustOptions beamDust = new Particle.DustOptions(Color.fromRGB(255, 18, 46), 1.7f);
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0 * i) / points;
            double x = centerX + Math.cos(angle) * radius;
            double z = centerZ + Math.sin(angle) * radius;
            Location point = dungeonRingSurface(world, x, z, baseY);
            if (point != null) {
                world.spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, outerDust);
            }
        }

        int lines = 14;
        int samples = Math.max(16, Math.min(26, (int) Math.round(radius)));
        double wave = (1.0 - Math.cos((ticks % 80) / 80.0 * Math.PI * 2.0)) * 0.5;
        for (int line = 0; line < lines; line++) {
            double angle = (Math.PI * 2.0 * line) / lines;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            for (int sample = 0; sample <= samples; sample++) {
                double t = sample / (double) samples;
                double r = radius * t;
                double x = centerX + cos * r;
                double z = centerZ + sin * r;
                Location point = dungeonRingSurface(world, x, z, baseY);
                if (point == null) {
                    continue;
                }
                world.spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, lineDust);
                double distance = Math.abs(t - wave);
                if (distance < 0.13) {
                    world.spawnParticle(Particle.DUST, point.clone().add(0.0, 0.018, 0.0), 1, 0.0, 0.0, 0.0, 0.0, beamDust);
                }
                if (distance < 0.055) {
                    world.spawnParticle(Particle.DUST, point.clone().add(0.0, 0.032, 0.0), 1, 0.0, 0.0, 0.0, 0.0, outerDust);
                }
            }
        }
    }

    private boolean hasDungeonParticleViewer(Location center) {
        World world = center.getWorld();
        if (world == null) {
            return false;
        }
        double rangeSq = 144.0 * 144.0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(world) && player.getLocation().distanceSquared(center) <= rangeSq) {
                return true;
            }
        }
        return false;
    }

    private DungeonParticleProfile dungeonParticleProfile(RitualSession session) {
        World world = session.center().getWorld();
        int x = session.center().getBlockX();
        int y = session.center().getBlockY();
        int z = session.center().getBlockZ();
        DungeonParticleProfile cached = dungeonParticleProfiles.get(session.weaponType());
        if (cached != null && world != null && cached.matches(world, x, y, z)) {
            return cached;
        }
        double radius = selectDungeonParticleRadius(world, session.center().getX(), session.center().getZ(), y);
        DungeonParticleProfile profile = new DungeonParticleProfile(world == null ? null : world.getUID(), x, y, z, y, radius);
        dungeonParticleProfiles.put(session.weaponType(), profile);
        return profile;
    }

    private double selectDungeonParticleRadius(World world, double centerX, double centerZ, int baseY) {
        if (world == null) {
            return Math.max(8.0, configManager.ritualZoneRadius());
        }
        int desired = configManager.ritualZoneRadius();
        for (int radius = desired; radius >= 8; radius -= 3) {
            int samples = Math.max(24, Math.min(64, radius * 3));
            int grounded = 0;
            for (int i = 0; i < samples; i++) {
                double angle = (Math.PI * 2.0 * i) / samples;
                double x = centerX + Math.cos(angle) * radius;
                double z = centerZ + Math.sin(angle) * radius;
                if (dungeonRingSurface(world, x, z, baseY) != null) {
                    grounded++;
                }
            }
            if (grounded >= Math.ceil(samples * 0.82)) {
                return radius;
            }
        }
        return 8.0;
    }

    private Location dungeonRingSurface(World world, double x, double z, int baseY) {
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        for (int delta = 0; delta <= 14; delta++) {
            int up = baseY + delta;
            if (isRingSurface(world, blockX, up, blockZ)) {
                return new Location(world, x, up + 0.035, z);
            }
            int down = baseY - delta;
            if (isRingSurface(world, blockX, down, blockZ)) {
                return new Location(world, x, down + 0.035, z);
            }
        }
        return null;
    }

    private boolean isRingSurface(World world, int x, int y, int z) {
        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 1) {
            return false;
        }
        return world.getBlockAt(x, y - 1, z).getType().isSolid()
            && world.getBlockAt(x, y, z).getType().isAir();
    }

    private void beginTimer(RitualSession session) {
        BukkitTask timerTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int ticks;

            @Override
            public void run() {
                RitualSession live = byWeapon(session.weaponType()).orElse(null);
                if (live == null || live != session) {
                    return;
                }
                ticks++;
                applyZoneEffects(session);
                boolean hasPlayers = !session.playersInZone().isEmpty();
                if (!hasPlayers) {
                    if (!session.paused()) {
                        session.setPaused(true);
                        Bukkit.broadcastMessage(messageService.colorize(messageService.prefix() + "&6Ritual paused (no players in zone)."));
                        saveActiveRituals();
                    }
                    if (session.dungeonRitual()) {
                        session.setEmptySeconds(0);
                        refreshBossBar(session);
                        saveActiveRituals();
                        return;
                    }
                    if (!Bukkit.getOnlinePlayers().isEmpty()) {
                        session.setEmptySeconds(session.emptySeconds() + 1);
                        if (session.emptySeconds() >= configManager.ritualEmptyFailSeconds()) {
                            failRitual(session.weaponType(), "No players remained in the ritual area");
                            return;
                        }
                        saveActiveRituals();
                    }
                } else {
                    if (session.paused()) {
                        Bukkit.broadcastMessage(messageService.colorize(messageService.prefix() + "&aRitual resumed."));
                        saveActiveRituals();
                    }
                    session.setPaused(false);
                    session.setEmptySeconds(0);
                }

                if (session.paused()) {
                    refreshBossBar(session);
                    for (UUID id : session.playersInZone()) {
                        Player p = Bukkit.getPlayer(id);
                        if (p != null && p.isOnline() && !usingBetterHud()) {
                            messageService.action(p, "&6Ritual paused - bring players to altar");
                        }
                    }
                    saveActiveRituals();
                    return;
                }
                int remaining = session.remainingSeconds() - 1;
                session.setRemainingSeconds(remaining);
                refreshBossBar(session);
                for (UUID id : session.playersInZone()) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null && p.isOnline() && !usingBetterHud()) {
                        messageService.action(p, "&d" + session.weaponType().displayName() + " Ritual &7- &f" + formatTime(remaining));
                    }
                }
                if (remaining <= 0) {
                    completeRitual(session.weaponType());
                } else {
                    saveActiveRituals();
                }
            }
        }, 20L, 20L);
        session.setTimerTask(timerTask);
    }

    private void refreshBossBar(RitualSession session) {
        if (betterHudIntegration != null && betterHudIntegration.isAvailable()) {
            removeVanillaBossBar(session);
            refreshBetterHudRituals();
            return;
        }
        BossBar bossBar = session.bossBar();
        if (bossBar == null) {
            return;
        }
        Bukkit.getOnlinePlayers().forEach(bossBar::addPlayer);
        String location = session.center().getBlockX() + " "
            + session.center().getBlockY() + " "
            + session.center().getBlockZ();
        if (!session.ritualStarted()) {
            OfferingProgress offeringProgress = offeringProgress(session);
            bossBar.setProgress(offeringProgress.fraction());
            bossBar.setTitle(session.weaponType().displayName() + " Ritual revealed at "
                + location + " | Offerings " + offeringProgress.provided() + "/" + offeringProgress.required());
            return;
        }
        double progress = ritualProgress(session);
        bossBar.setProgress(progress);
        String pause = session.paused() ? " [PAUSED]" : "";
        bossBar.setTitle(session.weaponType().displayName() + " Ritual at "
            + location + " | " + formatTime(session.remainingSeconds()) + pause);
    }

    private void refreshBetterHudRituals() {
        if (betterHudIntegration == null || !betterHudIntegration.isAvailable()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            RitualSession selected = selectedHudSession(player);
            if (selected == null) {
                betterHudIntegration.clearRitual(player);
                continue;
            }
            updateBetterHudRitual(player, selected);
        }
    }

    private RitualSession selectedHudSession(Player player) {
        int radius = configManager.ritualZoneRadius();
        double radiusSq = (double) radius * radius;
        RitualSession nearestInArea = null;
        RitualSession nearestRunning = null;
        RitualSession globalRunning = null;
        RitualSession globalRevealed = null;
        double nearestInAreaDistance = Double.MAX_VALUE;
        double nearestRunningDistance = Double.MAX_VALUE;
        for (RitualSession session : activeByWeapon.values()) {
            if (!session.revealed() || session.manuallyStopped()) {
                continue;
            }
            if (globalRevealed == null) {
                globalRevealed = session;
            }
            if (session.ritualStarted() && globalRunning == null) {
                globalRunning = session;
            }
            if (!player.getWorld().equals(session.center().getWorld())) {
                continue;
            }
            double distance = horizontalDistanceSquared(player.getLocation(), session.center());
            if (distance <= radiusSq && distance < nearestInAreaDistance) {
                nearestInAreaDistance = distance;
                nearestInArea = session;
            }
            if (session.ritualStarted() && distance < nearestRunningDistance) {
                nearestRunningDistance = distance;
                nearestRunning = session;
            }
        }
        if (nearestInArea != null) {
            hudSelections.put(player.getUniqueId(), nearestInArea.weaponType());
            return nearestInArea;
        }
        RitualSession remembered = activeByWeapon.get(hudSelections.get(player.getUniqueId()));
        if (remembered != null && remembered.revealed() && !remembered.manuallyStopped()) {
            return remembered;
        }
        RitualSession selected = nearestRunning != null ? nearestRunning : (globalRunning != null ? globalRunning : globalRevealed);
        if (selected == null) {
            hudSelections.remove(player.getUniqueId());
            return null;
        }
        hudSelections.put(player.getUniqueId(), selected.weaponType());
        return selected;
    }

    private void updateBetterHudRitual(Player player, RitualSession session) {
        String coordinates = "X " + session.center().getBlockX()
            + "  Y " + session.center().getBlockY()
            + "  Z " + session.center().getBlockZ();
        String timerText;
        String stageText;
        double progress;
        int radius = configManager.ritualZoneRadius();
        boolean inRitualArea = isInsideRitualZone(player.getLocation(), session.center(), radius);
        boolean waitingForCraft = !session.ritualStarted();
        if (!session.ritualStarted()) {
            OfferingProgress offeringProgress = offeringProgress(session);
            progress = offeringProgress.fraction();
            timerText = "00:00";
            stageText = "CRAFTING WEAPON " + offeringProgress.percent() + "%";
        } else {
            int total = Math.max(1, session.totalSeconds());
            int remaining = Math.max(0, session.remainingSeconds());
            progress = 1.0 - ((double) remaining / (double) total);
            timerText = formatTime(remaining);
            if (session.paused()) {
                stageText = "RITUAL PAUSED - RETURN TO AREA";
            } else if (!inRitualArea) {
                stageText = "RETURN TO RITUAL AREA";
            } else {
                stageText = "";
            }
        }
        betterHudIntegration.updateRitual(
            player,
            session.dungeonRitual()
                ? (inRitualArea ? "dungeon:" + session.weaponType().id() : "dungeon")
                : session.weaponType().id(),
            coordinates,
            timerText,
            stageText,
            progress,
            inRitualArea,
            session.paused(),
            waitingForCraft
        );
    }

    private void removeVanillaBossBar(RitualSession session) {
        BossBar bossBar = session.bossBar();
        if (bossBar == null) {
            return;
        }
        bossBar.removeAll();
        session.setBossBar(null);
    }

    private boolean usingBetterHud() {
        return betterHudIntegration != null && betterHudIntegration.isAvailable();
    }

    private void applyZoneEffects(RitualSession session) {
        int radius = configManager.ritualZoneRadius();
        session.playersInZone().clear();
        for (Player player : session.center().getWorld().getPlayers()) {
            if (!isInsideRitualZone(player.getLocation(), session.center(), radius)) {
                continue;
            }
            session.playersInZone().add(player.getUniqueId());
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 0, true, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 0, true, false, true));
            if ((session.remainingSeconds() % 5) == 0) {
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.35f, 1.35f);
            }
        }
    }

    private void spawnFloatingRunes(Location center) {
        for (int i = 0; i < 8; i++) {
            double angle = (Math.PI * 2.0 * i) / 8.0;
            double radius = 2.8 + (0.45 * Math.sin((System.currentTimeMillis() / 140.0) + i));
            Location point = center.clone().add(Math.cos(angle) * radius, 1.4 + (0.26 * Math.cos(angle * 3)), Math.sin(angle) * radius);
            point.getWorld().spawnParticle(Particle.DUST, point, 2, 0.02, 0.02, 0.02, 0.0,
                new Particle.DustOptions(Color.fromRGB(198, 110, 255), 1.2f));
            point.getWorld().spawnParticle(Particle.ENCHANTED_HIT, point, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    private void spawnEnergyPulse(Location center, int radius) {
        for (int ring = 1; ring <= 3; ring++) {
            double r = radius * (ring / 3.0) * 0.42;
            for (int i = 0; i < 42; i++) {
                double angle = i * (Math.PI * 2.0 / 42.0);
                Location p = center.clone().add(Math.cos(angle) * r, 0.12, Math.sin(angle) * r);
                p.getWorld().spawnParticle(Particle.END_ROD, p, 1, 0.01, 0.01, 0.01, 0.0);
            }
        }
    }

    private void broadcastStart(Player starter, RitualSession session) {
        Location c = session.center();
        String starterName = starter != null ? starter.getName() : "A player";
        Bukkit.broadcastMessage(messageService.colorize(messageService.prefix()
            + "&d" + starterName + " has started a Legendary Ritual at (&f"
            + c.getBlockX() + " " + c.getBlockY() + " " + c.getBlockZ() + "&d)"));
        animateGlobalTitle(
            starterName + " started " + session.weaponType().displayName() + " Ritual",
            "at " + c.getBlockX() + " " + c.getBlockY() + " " + c.getBlockZ(),
            6,
            4L
        );
        World world = c.getWorld();
        world.spawnParticle(Particle.SMOKE, c.clone().add(0, 10.8, 0), 34, 1.0, 0.35, 1.0, 0.03);
        world.playSound(c, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.65f, 1.55f);
        world.playSound(c, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 0.95f);
    }

    private void broadcastOfferingStart(Player starter, RitualSession session) {
        Location c = session.center();
        String starterName = starter != null ? starter.getName() : "A player";
        Bukkit.broadcastMessage(messageService.colorize(messageService.prefix()
            + "&d" + starterName + " revealed &f" + session.weaponType().displayName() + " &dRitual at (&f"
            + c.getBlockX() + " " + c.getBlockY() + " " + c.getBlockZ()
            + "&d). Drop the required offerings on the workbench."));
        animateGlobalTitle(
            "LEGENDARY RITUAL REVEALED",
            session.weaponType().displayName() + " at "
                + c.getBlockX() + " " + c.getBlockY() + " " + c.getBlockZ(),
            4,
            5L
        );
        Bukkit.getOnlinePlayers().forEach(player -> {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.05f);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.45f, 1.2f);
        });
        World world = c.getWorld();
        world.playSound(c, Sound.BLOCK_BEACON_ACTIVATE, 0.95f, 0.85f);
        if (starter != null) {
            messageService.action(starter, "&eDrop the required items onto the workbench to begin.");
        }
    }

    private void broadcastDungeonLocation(Player starter, Location center) {
        String starterName = starter != null ? starter.getName() : "A player";
        String location = center != null
            ? center.getBlockX() + " " + center.getBlockY() + " " + center.getBlockZ()
            : "the dungeon";
        Bukkit.broadcastMessage(messageService.colorize(messageService.prefix()
            + "&d" + starterName + " located the &fLegendary Weapons Dungeon &dat &f" + location + "&d."));
        animateGlobalTitle(
            "LEGENDARY WEAPONS DUNGEON",
            "WEAPON DUNGEON at " + location,
            4,
            5L
        );
        Bukkit.getOnlinePlayers().forEach(player -> {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.02f);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.45f, 1.18f);
        });
        if (center != null && center.getWorld() != null) {
            World world = center.getWorld();
            world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 0.95f, 0.82f);
            world.spawnParticle(Particle.END_ROD, center.clone().add(0, 10.0, 0), 72, 2.8, 1.2, 2.8, 0.04);
        }
        if (starter != null) {
            messageService.action(starter, "&eWeapon Dungeon location shown. Reveal individual rituals when you are ready.");
        }
    }

    public synchronized boolean completeRitual(WeaponType type) {
        RitualSession session = activeByWeapon.remove(type);
        if (session == null) {
            return false;
        }
        stopTasks(session, false);
        clearCompletedRitualHud(type);
        residualByWeapon.put(type, session);
        saveActiveRituals();
        runCompletionCinematic(session);
        return true;
    }

    private void clearCompletedRitualHud(WeaponType type) {
        hudSelections.values().removeIf(selected -> selected == type);
        refreshBetterHudRituals();
    }

    private void runCompletionCinematic(RitualSession session) {
        World world = session.center().getWorld();
        Location center = session.center().clone();
        animateCompletionWeapon(session, center);
        for (int i = 0; i < 3; i++) {
            int delay = i * 8;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!isSessionRegistered(session)) {
                    return;
                }
                world.strikeLightningEffect(center);
                world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.2f, 0.8f);
            }, delay);
        }
        for (int i = 0; i < 70; i++) {
            final int tick = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!isSessionRegistered(session)) {
                    return;
                }
                double height = 16.0 - (tick * 0.22);
                Location point = center.clone().add(0.0, Math.max(0.5, height), 0.0);
                world.spawnParticle(Particle.DUST, point, 16, 0.25, 0.45, 0.25, 0.0,
                    new Particle.DustOptions(Color.fromRGB(255, 235, 140), 1.45f));
                world.spawnParticle(Particle.END_ROD, point, 6, 0.12, 0.2, 0.12, 0.01);
                particleService.ritualBeam(center.clone().add(0, 22, 0), point);
            }, i);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isSessionRegistered(session)) {
                return;
            }
            Location tableTop = center.clone().add(0, FORGE_WORKBENCH_Y + 1.25, 0);
            world.spawnParticle(Particle.FIREWORK, tableTop, 22, 0.55, 0.2, 0.55, 0.04);
            world.spawnParticle(Particle.END_ROD, tableTop, 36, 0.45, 0.2, 0.45, 0.03);
            world.playSound(tableTop, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);
            ItemStack reward = itemFactory.create(session.weaponType());
            world.dropItem(tableTop, reward).setVelocity(new Vector(0, 0.02, 0));
            if (session.display() != null && session.display().isValid()) {
                session.display().remove();
            }
            if (session.progressDisplay() != null && session.progressDisplay().isValid()) {
                session.progressDisplay().remove();
            }
            spawnCompletionText(session);
            stateStore.markCreated(session.weaponType(), null);
            if (ritualBoxService != null) {
                ritualBoxService.unlockCrafting();
            }
            saveActiveRituals();
            Bukkit.broadcastMessage(messageService.colorize(messageService.prefix()
                + "&a" + session.weaponType().displayName() + " ritual completed. The weapon has descended."));
            Title title = Title.title(
                Component.text("LEGENDARY WEAPON FORGED"),
                Component.text(session.weaponType().displayName()),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(600))
            );
            Bukkit.getOnlinePlayers().forEach(p -> p.showTitle(title));
        }, 74L);
    }

    private void animateCompletionWeapon(RitualSession session, Location center) {
        ItemDisplay weapon = session.display();
        if (weapon == null || !weapon.isValid()) {
            return;
        }
        World world = center.getWorld();
        for (int i = 0; i <= 72; i++) {
            final int tick = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!weapon.isValid()) {
                    return;
                }
                double t = tick / 72.0;
                double eased = easeOutCubic(t);
                float scale;
                float y;
                if (t < 0.28) {
                    double phase = easeOutCubic(t / 0.28);
                    scale = (float) (4.35 + phase * 3.15);
                    y = FORGE_WORKBENCH_Y + 4.55f + (float) (phase * 1.4);
                } else if (t < 0.58) {
                    double pulse = Math.sin(((t - 0.28) / 0.30) * Math.PI);
                    scale = (float) (7.25 + pulse * 0.55);
                    y = FORGE_WORKBENCH_Y + 5.95f + (float) (pulse * 0.35);
                } else {
                    double phase = easeOutCubic((t - 0.58) / 0.42);
                    scale = (float) (7.25 - (phase * 5.95));
                    y = FORGE_WORKBENCH_Y + 5.95f - (float) (phase * 4.55);
                }
                weapon.setTransformation(new Transformation(
                    new Vector3f(0f, y, 0f),
                    euler(0f, (float) (tick * 0.24), 0f),
                    new Vector3f(scale, scale, scale),
                    new Quaternionf()
                ));
                if (tick % 6 == 0) {
                    Location point = center.clone().add(0, y, 0);
                    world.spawnParticle(Particle.END_ROD, point, 4, 0.22, 0.22, 0.22, 0.02);
                    world.spawnParticle(Particle.DUST, point, 6, 0.24, 0.24, 0.24, 0.0,
                        new Particle.DustOptions(Color.fromRGB(255, 210, 95), 1.35f));
                }
                if (tick == 22 || tick == 45 || tick == 66) {
                    world.playSound(center.clone().add(0, y, 0), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.75f, 0.8f + (float) eased * 0.5f);
                }
            }, tick);
        }
    }

    public synchronized boolean failRitual(WeaponType type, String reason) {
        RitualSession session = activeByWeapon.remove(type);
        if (session == null) {
            return false;
        }
        residualByWeapon.remove(type);
        stopTasks(session, true);
        clearCompletedRitualHud(type);
        World world = session.center().getWorld();
        Location center = session.center();
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center.clone().add(0, 1.0, 0), 2, 0.3, 0.2, 0.3, 0.0);
        world.spawnParticle(Particle.SMOKE, center.clone().add(0, 1.0, 0), 240, 3.4, 2.0, 3.4, 0.05);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, center.clone().add(0, 0.8, 0), 120, 2.8, 1.1, 2.8, 0.04);
        world.playSound(center, Sound.ENTITY_WITHER_BREAK_BLOCK, 1.0f, 0.7f);
        world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.0f, 0.65f);
        stateStore.clear(type);
        if (ritualBoxService != null) {
            ritualBoxService.unlockCrafting();
        }
        saveActiveRituals();
        Bukkit.broadcastMessage(messageService.colorize(messageService.prefix()
            + "&4Legendary Ritual failed: &c" + reason));
        return true;
    }

    public synchronized boolean stopRitual(WeaponType type) {
        RitualSession session = activeByWeapon.get(type);
        if (session == null) {
            return false;
        }
        session.setManuallyStopped(true);
        session.setPaused(true);
        if (session.fxTask() != null) {
            session.fxTask().cancel();
            session.setFxTask(null);
        }
        if (session.timerTask() != null) {
            session.timerTask().cancel();
            session.setTimerTask(null);
        }
        removeVanillaBossBar(session);
        refreshBetterHudRituals();
        saveActiveRituals();
        return true;
    }

    public synchronized boolean deleteRitual(WeaponType type) {
        RitualSession session = activeByWeapon.remove(type);
        if (session == null) {
            return false;
        }
        residualByWeapon.remove(type);
        stopTasks(session, true);
        clearCompletedRitualHud(type);
        session.buildPlan().rollback();
        stateStore.clear(type);
        if (ritualBoxService != null) {
            ritualBoxService.unlockCrafting();
        }
        saveActiveRituals();
        return true;
    }

    public synchronized int deleteDungeonRituals() {
        List<WeaponType> activeTypes = new ArrayList<>();
        for (RitualSession session : activeByWeapon.values()) {
            if (session.dungeonRitual()) {
                activeTypes.add(session.weaponType());
            }
        }
        int deleted = 0;
        for (WeaponType type : activeTypes) {
            clearSavedRitualRecord(type);
            if (deleteRitual(type)) {
                deleted++;
            }
        }

        List<WeaponType> residualTypes = new ArrayList<>();
        for (RitualSession session : residualByWeapon.values()) {
            if (session.dungeonRitual()) {
                residualTypes.add(session.weaponType());
            }
        }
        for (WeaponType type : residualTypes) {
            RitualSession session = residualByWeapon.remove(type);
            if (session == null) {
                continue;
            }
            stopTasks(session, true);
            clearCompletedRitualHud(type);
            session.buildPlan().rollback();
            stateStore.clear(type);
            clearSavedRitualRecord(type);
            deleted++;
        }
        if (deleted > 0) {
            saveActiveRituals();
        }
        return deleted;
    }

    public synchronized void clearSavedRitualRecord(WeaponType type) {
        if (!activeRitualsFile.exists()) {
            return;
        }
        YamlConfiguration cfg = SafeYamlFiles.loadOrQuarantine(activeRitualsFile, plugin.getLogger());
        cfg.set("rituals." + type.id(), null);
        ConfigurationSection root = cfg.getConfigurationSection("rituals");
        if (root == null || root.getKeys(false).isEmpty()) {
            if (!activeRitualsFile.delete()) {
                try {
                    saveActiveRitualsAtomically(new YamlConfiguration());
                } catch (IOException e) {
                    plugin.getLogger().severe("Failed to clear active-rituals.yml: " + e.getMessage());
                }
            }
            return;
        }
        try {
            saveActiveRitualsAtomically(cfg);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to clear saved ritual entry for " + type.id() + ": " + e.getMessage());
        }
    }

    public synchronized void stopAll() {
        List<WeaponType> active = new ArrayList<>(activeByWeapon.keySet());
        for (WeaponType type : active) {
            deleteRitual(type);
        }
    }

    public synchronized void saveAndPauseAll() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }
        if (dungeonParticleTask != null) {
            dungeonParticleTask.cancel();
            dungeonParticleTask = null;
        }
        saveActiveRituals();
        for (RitualSession session : residualByWeapon.values()) {
            stopTasks(session, false);
        }
        residualByWeapon.clear();
        for (RitualSession session : activeByWeapon.values()) {
            stopTasks(session, false);
        }
        activeByWeapon.clear();
    }

    public synchronized void restoreSavedRituals() {
        if (!activeRitualsFile.exists()) {
            return;
        }
        YamlConfiguration cfg = SafeYamlFiles.loadOrQuarantine(activeRitualsFile, plugin.getLogger());
        ConfigurationSection root = cfg.getConfigurationSection("rituals");
        if (root == null) {
            return;
        }
        int restored = 0;
        int stale = 0;
        boolean savedDataChanged = false;
        for (String key : new ArrayList<>(root.getKeys(false))) {
            String path = "rituals." + key;
            WeaponType type = WeaponType.byId(cfg.getString(path + ".type", key)).orElse(null);
            if (type == null || !configManager.isWeaponEnabled(type)
                || activeByWeapon.containsKey(type) || residualByWeapon.containsKey(type)) {
                continue;
            }
            World world = Bukkit.getWorld(cfg.getString(path + ".world", ""));
            if (world == null) {
                cfg.set(path, null);
                stateStore.clearRitual(type);
                stale++;
                savedDataChanged = true;
                continue;
            }
            Location center = new Location(
                world,
                cfg.getDouble(path + ".x"),
                cfg.getDouble(path + ".y"),
                cfg.getDouble(path + ".z")
            );
            if (!savedWorldMatchesCurrentWorld(cfg, path, world)) {
                cleanupStaleSavedRitual(type, center);
                cfg.set(path, null);
                stateStore.clearRitual(type);
                stale++;
                savedDataChanged = true;
                continue;
            }
            UUID starterId;
            try {
                starterId = UUID.fromString(cfg.getString(path + ".starter", "00000000-0000-0000-0000-000000000000"));
            } catch (IllegalArgumentException ex) {
                starterId = new UUID(0L, 0L);
            }
            int remaining = Math.max(1, cfg.getInt(path + ".remaining-seconds", configManager.ritualDurationSeconds()));
            int total = Math.max(remaining, cfg.getInt(path + ".total-seconds", configManager.ritualDurationSeconds()));
            boolean dungeonRitual = cfg.getBoolean(path + ".dungeon-ritual", false);
            RitualStructureBuilder.BuildPlan plan = dungeonRitual
                ? structureBuilder.createDungeonPlan(center, type)
                : structureBuilder.createPlan(center, type);
            plan.loadOriginalSnapshots(loadOriginalBlockSnapshots(cfg.getConfigurationSection(path + ".original-blocks")));
            RitualSession session = new RitualSession(type, starterId, center, center.clone(), plan, remaining, total);
            session.setDungeonRitual(dungeonRitual);
            boolean savedRevealed = cfg.getBoolean(path + ".revealed", false);
            session.setRevealed(savedRevealed);
            session.setPaused(cfg.getBoolean(path + ".paused", false));
            session.setManuallyStopped(cfg.getBoolean(path + ".manually-stopped", false));
            session.setEmptySeconds(cfg.getInt(path + ".empty-seconds", 0));
            boolean completedResidual = cfg.getBoolean(path + ".completed-residual", false);
            if (completedResidual) {
                session.setRitualStarted(true);
                residualByWeapon.put(type, session);
                removePersistedRitualDisplays(session);
                restoreBuild(session);
                restored++;
                continue;
            }
            activeByWeapon.put(type, session);
            stateStore.markRitual(type, starterId);
            removePersistedRitualDisplays(session);
            restoreBuild(session);

            boolean ritualStarted = cfg.getBoolean(path + ".ritual-started", false);
            if (ritualStarted) {
                session.setRitualStarted(true);
                spawnForgeShell(session);
                beginWeaponVisuals(session);
                if (!session.manuallyStopped()) {
                    beginTimer(session);
                    beginBossBar(session);
                }
            } else {
                beginOfferingStage(Bukkit.getPlayer(starterId), session, false);
                session.setRevealed(savedRevealed);
                ConfigurationSection offerings = cfg.getConfigurationSection(path + ".offerings");
                if (offerings != null) {
                    for (String slotKey : offerings.getKeys(false)) {
                        try {
                            int slot = Integer.parseInt(slotKey);
                            session.offeringProvided().put(slot, Math.max(0, offerings.getInt(slotKey)));
                        } catch (NumberFormatException ignored) {
                            // Ignore invalid saved offering slots.
                        }
                    }
                }
                if (!session.ritualStarted() && session.revealed()) {
                    refreshOfferingDisplays(session);
                    beginBossBar(session);
                    if (session.offeringsComplete()) {
                        completeOfferingStage(session);
                    }
                }
            }
            restored++;
        }
        if (savedDataChanged) {
            saveActiveRitualConfig(cfg);
            plugin.getLogger().info("Discarded " + stale + " stale saved legendary ritual(s) for missing or regenerated worlds.");
        }
        if (restored > 0) {
            enforceSingleRevealedSession();
            plugin.getLogger().info("Restored " + restored + " active legendary ritual(s).");
            saveActiveRituals();
            Bukkit.getScheduler().runTaskLater(plugin, this::repairRestoredRitualVisuals, 40L);
            Bukkit.getScheduler().runTaskLater(plugin, this::repairRestoredRitualVisuals, 120L);
        }
    }

    private boolean savedWorldMatchesCurrentWorld(YamlConfiguration cfg, String path, World world) {
        String savedWorldUid = cfg.getString(path + ".world-uid", "");
        if (savedWorldUid == null || savedWorldUid.isBlank()) {
            return false;
        }
        try {
            return world.getUID().equals(UUID.fromString(savedWorldUid));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private void cleanupStaleSavedRitual(WeaponType type, Location center) {
        if (center.getWorld() == null) {
            return;
        }
        RitualStructureBuilder.BuildPlan plan = structureBuilder.createPlan(center, type);
        RitualSession staleSession = new RitualSession(type, new UUID(0L, 0L), center, center.clone(), plan, 1);
        staleSession.setExpectedCoreMaterial(Material.RESPAWN_ANCHOR);
        ensureChunkLoaded(staleSession);
        removePersistedRitualDisplays(staleSession);
        plan.clearRitualBlocks();
        cleanupCoreBlock(staleSession);
    }

    private void saveActiveRitualConfig(YamlConfiguration cfg) {
        ConfigurationSection root = cfg.getConfigurationSection("rituals");
        if (root == null || root.getKeys(false).isEmpty()) {
            if (activeRitualsFile.exists() && !activeRitualsFile.delete()) {
                YamlConfiguration empty = new YamlConfiguration();
                try {
                    saveActiveRitualsAtomically(empty);
                } catch (IOException e) {
                    plugin.getLogger().severe("Failed to clear active-rituals.yml: " + e.getMessage());
                }
            }
            return;
        }
        try {
            saveActiveRitualsAtomically(cfg);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save active-rituals.yml after stale cleanup: " + e.getMessage());
        }
    }

    private void enforceSingleRevealedSession() {
        boolean dungeonRevealed = activeByWeapon.values().stream()
            .anyMatch(session -> session.dungeonRitual() && session.revealed() && !session.manuallyStopped());
        if (dungeonRevealed) {
            for (RitualSession session : activeByWeapon.values()) {
                if (!session.dungeonRitual() && session.revealed() && !session.ritualStarted()) {
                    session.setRevealed(false);
                    removeOfferingDisplays(session);
                }
            }
            return;
        }
        RitualSession keeper = null;
        for (RitualSession session : activeByWeapon.values()) {
            if (!session.revealed() || session.manuallyStopped()) {
                continue;
            }
            if (keeper == null || (!keeper.ritualStarted() && session.ritualStarted())) {
                if (keeper != null && !keeper.ritualStarted()) {
                    keeper.setRevealed(false);
                    removeOfferingDisplays(keeper);
                }
                keeper = session;
                continue;
            }
            if (!session.ritualStarted()) {
                session.setRevealed(false);
                removeOfferingDisplays(session);
            }
        }
    }

    private List<RitualStructureBuilder.SavedBlock> loadOriginalBlockSnapshots(ConfigurationSection section) {
        if (section == null) {
            return List.of();
        }
        List<RitualStructureBuilder.SavedBlock> blocks = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            String path = key + ".";
            String world = section.getString(path + "world", "");
            String data = section.getString(path + "data", "");
            if (world.isBlank() || data.isBlank()) {
                continue;
            }
            blocks.add(new RitualStructureBuilder.SavedBlock(
                world,
                section.getInt(path + "x"),
                section.getInt(path + "y"),
                section.getInt(path + "z"),
                data
            ));
        }
        return blocks;
    }

    private void restoreBuild(RitualSession session) {
        Material coreMaterial = Material.RESPAWN_ANCHOR;
        session.setExpectedCoreMaterial(coreMaterial);
        ensureChunkLoaded(session);
        session.buildPlan().setCaptureOriginals(false);
        try {
            session.buildPlan().placeAll();
            session.coreLocation().getBlock().setType(coreMaterial, false);
        } finally {
            session.buildPlan().setCaptureOriginals(true);
        }
    }

    private synchronized void repairRestoredRitualVisuals() {
        for (RitualSession session : activeByWeapon.values()) {
            if (session.manuallyStopped()) {
                continue;
            }
            ensureChunkLoaded(session);
            if (session.expectedCoreMaterial() != null && session.coreLocation().getBlock().getType() != session.expectedCoreMaterial()) {
                session.coreLocation().getBlock().setType(session.expectedCoreMaterial(), false);
            }
            if (session.ritualStarted()) {
                boolean fallbackProgressMissing = !usingBetterHud() && displayMissing(session.progressDisplay());
                if (displayMissing(session.coreDisplay()) || displayMissing(session.workbenchDisplay())
                    || displayMissing(session.display()) || fallbackProgressMissing) {
                    if (session.fxTask() != null && !session.fxTask().isCancelled()) {
                        session.fxTask().cancel();
                    }
                    removeForgeDisplays(session);
                    spawnForgeShell(session);
                    beginWeaponVisuals(session);
                }
                if (session.timerTask() == null || session.timerTask().isCancelled()) {
                    beginTimer(session);
                }
                beginBossBar(session);
                continue;
            }
            if (displayMissing(session.coreDisplay()) || displayMissing(session.workbenchDisplay())) {
                if (session.fxTask() != null && !session.fxTask().isCancelled()) {
                    session.fxTask().cancel();
                }
                removeForgeDisplays(session);
                spawnForgeShell(session);
                startOfferingFxTask(session, session.center().clone(), session.coreDisplay(), session.workbenchDisplay());
            }
            if (session.revealed()) {
                refreshOfferingDisplays(session);
                beginBossBar(session);
                if (session.offeringsComplete()) {
                    completeOfferingStage(session);
                }
            }
        }
        refreshBetterHudRituals();
    }

    private boolean displayMissing(Display display) {
        return display == null || !display.isValid();
    }

    private void ensureChunkLoaded(RitualSession session) {
        if (session.center().getWorld() != null && !session.center().getChunk().isLoaded()) {
            session.center().getChunk().load(true);
        }
    }

    private void spawnForgeShell(RitualSession session) {
        Location center = session.center().clone();
        ItemDisplay base = spawnForgeModel(session, center, ritualBaseModel(session.weaponType()));
        session.setCoreDisplay(base);
        ItemDisplay workbench = spawnForgeWorkbench(session, center);
        session.setWorkbenchDisplay(workbench);
        if (session.dungeonRitual()) {
            spawnDungeonNameHologram(session, center);
        }
        double progress = ritualProgress(session);
        animateForgeBase(base, progress, 0.0);
        animateForgeWorkbench(workbench, progress);
    }

    private synchronized void saveActiveRituals() {
        if (activeByWeapon.isEmpty() && residualByWeapon.isEmpty()) {
            if (activeRitualsFile.exists() && !activeRitualsFile.delete()) {
                YamlConfiguration empty = new YamlConfiguration();
                try {
                    saveActiveRitualsAtomically(empty);
                } catch (IOException e) {
                    plugin.getLogger().severe("Failed to clear active-rituals.yml: " + e.getMessage());
                }
            }
            return;
        }
        YamlConfiguration cfg = new YamlConfiguration();
        for (RitualSession session : activeByWeapon.values()) {
            writeSavedRitual(cfg, session, false);
        }
        for (RitualSession session : residualByWeapon.values()) {
            if (activeByWeapon.get(session.weaponType()) == session) {
                continue;
            }
            writeSavedRitual(cfg, session, true);
        }
        File parent = activeRitualsFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try {
            saveActiveRitualsAtomically(cfg);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save active-rituals.yml: " + e.getMessage());
        }
    }

    private void saveActiveRitualsAtomically(YamlConfiguration configuration) throws IOException {
        File parent = activeRitualsFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        File temporary = new File(parent, activeRitualsFile.getName() + ".tmp");
        configuration.save(temporary);
        try {
            Files.move(
                temporary.toPath(),
                activeRitualsFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            );
        } catch (IOException atomicMoveFailure) {
            Files.move(
                temporary.toPath(),
                activeRitualsFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    private void writeSavedRitual(YamlConfiguration cfg, RitualSession session, boolean completedResidual) {
        String path = "rituals." + session.weaponType().id();
        World world = session.center().getWorld();
        cfg.set(path + ".type", session.weaponType().id());
        cfg.set(path + ".starter", session.starterId().toString());
        cfg.set(path + ".world", world == null ? "" : world.getName());
        cfg.set(path + ".world-uid", world == null ? null : world.getUID().toString());
        cfg.set(path + ".x", session.center().getX());
        cfg.set(path + ".y", session.center().getY());
        cfg.set(path + ".z", session.center().getZ());
        cfg.set(path + ".remaining-seconds", session.remainingSeconds());
        cfg.set(path + ".total-seconds", session.totalSeconds());
        cfg.set(path + ".ritual-started", session.ritualStarted());
        cfg.set(path + ".revealed", session.revealed());
        cfg.set(path + ".paused", session.paused());
        cfg.set(path + ".manually-stopped", session.manuallyStopped());
        cfg.set(path + ".dungeon-ritual", session.dungeonRitual());
        cfg.set(path + ".empty-seconds", session.emptySeconds());
        cfg.set(path + ".completed-residual", completedResidual);
        int blockIndex = 0;
        for (RitualStructureBuilder.SavedBlock block : session.buildPlan().originalSnapshots()) {
            String blockPath = path + ".original-blocks." + blockIndex++;
            cfg.set(blockPath + ".world", block.world());
            cfg.set(blockPath + ".x", block.x());
            cfg.set(blockPath + ".y", block.y());
            cfg.set(blockPath + ".z", block.z());
            cfg.set(blockPath + ".data", block.blockData());
        }
        for (Map.Entry<Integer, Integer> entry : session.offeringProvided().entrySet()) {
            cfg.set(path + ".offerings." + entry.getKey(), entry.getValue());
        }
    }

    private void stopTasks(RitualSession session, boolean removeDisplay) {
        if (session.buildTask() != null) {
            session.buildTask().cancel();
        }
        if (session.timerTask() != null) {
            session.timerTask().cancel();
        }
        if (session.fxTask() != null) {
            session.fxTask().cancel();
        }
        removeVanillaBossBar(session);
        if (removeDisplay) {
            removeOfferingDisplays(session);
            removeForgeDisplays(session);
            boolean hadSavedSnapshots = session.buildPlan().hasLoadedOriginalSnapshots();
            boolean restoredAnyBlocks = session.buildPlan().rollback();
            if (!restoredAnyBlocks && !hadSavedSnapshots) {
                session.buildPlan().clearRitualBlocks();
            }
            cleanupCoreBlock(session);
            removePersistedRitualDisplays(session);
        }
        if (removeDisplay && session.display() != null && session.display().isValid()) {
            animationService.stopRotating(session.display());
            session.display().remove();
        }
        if (removeDisplay && session.coreDisplay() != null && session.coreDisplay().isValid()) {
            animationService.stopRotating(session.coreDisplay());
            session.coreDisplay().remove();
        }
        refreshBetterHudRituals();
    }

    private void removeForgeDisplays(RitualSession session) {
        for (Display display : session.forgeDisplays()) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        session.clearForgeDisplays();
    }

    private void prepareRitualDisplay(RitualSession session, Display display) {
        display.setPersistent(true);
        display.addScoreboardTag(RITUAL_DISPLAY_TAG);
        display.addScoreboardTag(ritualDisplayTypeTag(session.weaponType()));
    }

    private String ritualDisplayTypeTag(WeaponType type) {
        return RITUAL_DISPLAY_TYPE_PREFIX + type.id();
    }

    private synchronized boolean isSessionRegistered(RitualSession session) {
        return activeByWeapon.get(session.weaponType()) == session
            || residualByWeapon.get(session.weaponType()) == session;
    }

    private void removePersistedRitualDisplays(RitualSession session) {
        if (session.center().getWorld() == null) {
            return;
        }
        ensureChunkLoaded(session);
        Location center = session.center();
        String typeTag = ritualDisplayTypeTag(session.weaponType());
        for (org.bukkit.entity.Entity entity : center.getWorld().getNearbyEntities(center, 10.5, 18.0, 10.5)) {
            if (entity.getScoreboardTags().contains(RITUAL_DISPLAY_TAG)
                && entity.getScoreboardTags().contains(typeTag)) {
                entity.remove();
            }
        }
    }

    private void cleanupCoreBlock(RitualSession session) {
        Block block = session.coreLocation().getBlock();
        if (session.expectedCoreMaterial() != null && block.getType() == session.expectedCoreMaterial()) {
            block.setType(Material.AIR, false);
        }
    }

    public synchronized boolean isProtected(Location location) {
        return allTrackedSessions().stream()
            .anyMatch(session -> isAnchorLocation(session, location) || session.buildPlan().isRitualOwnedBlock(location));
    }

    public synchronized boolean isRitualAnchor(Location location) {
        return anchorSessionAt(location) != null;
    }

    public synchronized boolean breakRitualAnchor(Player player, Location location) {
        RitualSession session = anchorSessionAt(location);
        if (session == null) {
            return false;
        }
        cleanupRitualAnchor(session, player);
        return true;
    }

    public synchronized boolean breakRitualStructure(Player player, Location location) {
        RitualSession session = anchorSessionAt(location);
        if (session == null) {
            session = sessionAt(location);
        }
        if (session == null) {
            return false;
        }
        cleanupRitualAnchor(session, player);
        return true;
    }

    private void cleanupRitualAnchor(RitualSession session, Player player) {
        WeaponType type = session.weaponType();
        boolean wasActive = activeByWeapon.remove(type) == session;
        boolean wasResidual = residualByWeapon.remove(type) == session;
        if (!wasActive && !wasResidual) {
            return;
        }
        removePersistedRitualDisplays(session);
        stopTasks(session, true);
        clearCompletedRitualHud(type);
        LegendaryStateStore.WeaponState state = stateStore.state(type);
        if (wasActive || (state != null && state.inRitual() && !state.exists())) {
            stateStore.clear(type);
        }
        if (ritualBoxService != null) {
            ritualBoxService.unlockCrafting();
        }
        saveActiveRituals();
        World world = session.center().getWorld();
        world.spawnParticle(Particle.SMOKE, session.center().clone().add(0, 1.0, 0), 80, 1.8, 0.8, 1.8, 0.03);
        world.playSound(session.center(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.85f, 0.8f);
        String by = player != null ? " by " + player.getName() : "";
        Bukkit.broadcastMessage(messageService.colorize(messageService.prefix()
            + "&e" + type.displayName() + " ritual anchor was removed" + by + ". Ritual area restored."));
    }

    public synchronized Optional<RitualSession> byWeapon(WeaponType type) {
        return Optional.ofNullable(activeByWeapon.get(type));
    }

    public synchronized Collection<RitualSession> activeSessions() {
        return List.copyOf(activeByWeapon.values());
    }

    private RitualSession currentSession() {
        return activeByWeapon.values().stream().findFirst().orElse(null);
    }

    private RitualSession currentRevealedSession() {
        return activeByWeapon.values().stream()
            .filter(session -> session.revealed() && !session.manuallyStopped())
            .findFirst()
            .orElse(null);
    }

    private RitualSession nearestHiddenSession(Location origin) {
        RitualSession nearest = null;
        double best = Double.MAX_VALUE;
        for (RitualSession session : activeByWeapon.values()) {
            if (session.revealed() || !session.center().getWorld().equals(origin.getWorld())) {
                continue;
            }
            double distance = session.center().distanceSquared(origin);
            if (distance < best) {
                best = distance;
                nearest = session;
            }
        }
        return nearest != null ? nearest : currentSession();
    }

    private RitualSession sessionAt(Location location) {
        for (RitualSession session : allTrackedSessions()) {
            if (session.buildPlan().contains(location, 0, 0)) {
                return session;
            }
        }
        return null;
    }

    private RitualSession anchorSessionAt(Location location) {
        for (RitualSession session : allTrackedSessions()) {
            if (isAnchorLocation(session, location)) {
                return session;
            }
        }
        return null;
    }

    private boolean isAnchorLocation(RitualSession session, Location location) {
        if (location == null || session.coreLocation().getWorld() == null || location.getWorld() == null
            || !session.coreLocation().getWorld().equals(location.getWorld())) {
            return false;
        }
        Location core = session.coreLocation();
        return core.getBlockX() == location.getBlockX()
            && core.getBlockY() == location.getBlockY()
            && core.getBlockZ() == location.getBlockZ();
    }

    private List<RitualSession> allTrackedSessions() {
        List<RitualSession> sessions = new ArrayList<>(activeByWeapon.values());
        for (RitualSession session : residualByWeapon.values()) {
            if (!sessions.contains(session)) {
                sessions.add(session);
            }
        }
        return sessions;
    }

    private RitualSession offeringSessionAt(Location location) {
        RitualSession nearest = null;
        double best = Double.MAX_VALUE;
        for (RitualSession session : activeByWeapon.values()) {
            if (!isInsideOfferingZone(session, location)) {
                continue;
            }
            double distance = session.center().distanceSquared(location);
            if (distance < best) {
                best = distance;
                nearest = session;
            }
        }
        return nearest;
    }

    private String formatTime(int totalSeconds) {
        int safe = Math.max(0, totalSeconds);
        int mm = safe / 60;
        int ss = safe % 60;
        return String.format("%02d:%02d", mm, ss);
    }

    private OfferingProgress offeringProgress(RitualSession session) {
        int required = 0;
        int provided = 0;
        for (Map.Entry<Integer, RitualBoxService.RitualOffering> entry : session.offeringRequired().entrySet()) {
            int amount = entry.getValue().amount();
            required += amount;
            provided += Math.min(amount, session.offeringProvided().getOrDefault(entry.getKey(), 0));
        }
        return new OfferingProgress(provided, required);
    }

    private boolean isInsideRitualZone(Location location, Location center, int radius) {
        return location != null
            && center != null
            && location.getWorld() != null
            && location.getWorld().equals(center.getWorld())
            && horizontalDistanceSquared(location, center) <= (double) radius * radius;
    }

    private double horizontalDistanceSquared(Location first, Location second) {
        double dx = first.getX() - second.getX();
        double dz = first.getZ() - second.getZ();
        return (dx * dx) + (dz * dz);
    }

    private record OfferingProgress(int provided, int required) {
        private double fraction() {
            if (required <= 0) {
                return 1.0;
            }
            return Math.max(0.0, Math.min(1.0, (double) provided / (double) required));
        }

        private int percent() {
            return (int) Math.round(fraction() * 100.0);
        }
    }

    private Color themeColor(WeaponType type) {
        return type.color();
    }

    private void spawnWeaponThemedBurst(WeaponType type, Location center, int fxTicks) {
        World world = center.getWorld();
        world.spawnParticle(Particle.DUST, center.clone().add(0, 1.2, 0), 16, 1.2, 0.9, 1.2, 0.03,
            new Particle.DustOptions(type.color(), 1.45f));
        world.spawnParticle(Particle.END_ROD, center.clone().add(0, 1.2, 0), 6, 0.7, 0.6, 0.7, 0.02);
        if (fxTicks % 16 == 0) {
            world.playSound(center, Sound.BLOCK_BEACON_AMBIENT, 0.35f, 1.45f);
        }
    }

    private void displacePlayersFromSpawnArea(RitualSession session) {
        World world = session.center().getWorld();
        RitualStructureBuilder.BuildPlan plan = session.buildPlan();
        double safeRadius = plan.maxHorizontalRadius() + 2.5;
        for (Player player : world.getPlayers()) {
            Location current = player.getLocation();
            if (!plan.contains(current, 1, 2)) {
                continue;
            }

            Vector pushDir = current.toVector().subtract(session.center().toVector());
            pushDir.setY(0);
            if (pushDir.lengthSquared() < 0.0001) {
                pushDir = current.getDirection().multiply(-1.0);
                pushDir.setY(0);
            }
            if (pushDir.lengthSquared() < 0.0001) {
                pushDir = new Vector(1.0, 0.0, 0.0);
            }
            pushDir.normalize();

            Location target = session.center().clone().add(pushDir.clone().multiply(safeRadius));
            double safeY = findSafeTeleportY(world, target.getBlockX(), target.getBlockZ(),
                Math.max(current.getBlockY() + 1, session.center().getBlockY() + 1));
            target.setY(safeY);
            target.setYaw(current.getYaw());
            target.setPitch(current.getPitch());

            player.teleport(target);
            player.setVelocity(pushDir.clone().multiply(0.38).setY(0.2));
            world.spawnParticle(Particle.CLOUD, target.clone().add(0, 0.2, 0), 18, 0.35, 0.2, 0.35, 0.03);
            world.playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 0.35f, 1.45f);
            messageService.action(player, "&eRitual surge pushed you away from altar.");
        }
    }

    private double findSafeTeleportY(World world, int x, int z, int preferredY) {
        int minY = world.getMinHeight() + 1;
        int maxY = world.getMaxHeight() - 2;
        int from = Math.max(minY, preferredY - 2);
        int to = Math.min(maxY, preferredY + 12);
        for (int y = from; y <= to; y++) {
            Block feet = world.getBlockAt(x, y, z);
            Block head = world.getBlockAt(x, y + 1, z);
            Block below = world.getBlockAt(x, y - 1, z);
            if (feet.isPassable() && head.isPassable() && !below.isPassable()) {
                return y;
            }
        }
        int highest = world.getHighestBlockYAt(x, z) + 1;
        return Math.max(minY, Math.min(maxY, highest));
    }

    private void animateGlobalTitle(String titleText, String subtitleText, int pulses, long intervalTicks) {
        for (int i = 0; i < pulses; i++) {
            int index = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                TextColor mainColor = TITLE_COLORS[index % TITLE_COLORS.length];
                TextColor subColor = TITLE_COLORS[(index + 2) % TITLE_COLORS.length];
                Title title = Title.title(
                    Component.text(titleText, mainColor),
                    Component.text(subtitleText, subColor),
                    Title.Times.times(Duration.ofMillis(120), Duration.ofMillis(650), Duration.ofMillis(180))
                );
                Bukkit.getOnlinePlayers().forEach(player -> player.showTitle(title));
            }, intervalTicks * i);
        }
    }

    private record DungeonParticleProfile(UUID worldId, int x, int y, int z, int baseY, double radius) {
        private boolean matches(World world, int x, int y, int z) {
            return worldId != null
                && world != null
                && worldId.equals(world.getUID())
                && this.x == x
                && this.y == y
                && this.z == z;
        }
    }
}
