package com.legendaryweaponssmp.structures;

import com.legendaryweaponssmp.weapons.WeaponType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class RitualStructureBuilder {
    private static final int FORGE_FOUNDATION_RADIUS = 4;
    private static final int FORGE_PILLAR_RADIUS = 3;
    private static final int FORGE_TOP_RADIUS = 4;
    private static final int FORGE_PILLAR_HEIGHT = 10;

    public BuildPlan createPlan(Location center, WeaponType type) {
        return createPlan(center, type, false);
    }

    public BuildPlan createDungeonPlan(Location center, WeaponType type) {
        return createPlan(center, type, true);
    }

    private BuildPlan createPlan(Location center, WeaponType type, boolean dungeon) {
        List<Placement> placements = new ArrayList<>();
        Material dungeonFoundation = dungeon ? dungeonFoundationMaterial(center) : null;
        int foundationY = dungeon ? -2 : -1;
        for (int x = -FORGE_FOUNDATION_RADIUS; x <= FORGE_FOUNDATION_RADIUS; x++) {
            for (int z = -FORGE_FOUNDATION_RADIUS; z <= FORGE_FOUNDATION_RADIUS; z++) {
                Material material = dungeon
                    ? dungeonFoundation
                    : Math.abs(x) == FORGE_FOUNDATION_RADIUS || Math.abs(z) == FORGE_FOUNDATION_RADIUS
                        ? Material.BLACKSTONE
                        : Material.POLISHED_BLACKSTONE_BRICKS;
                placements.add(new Placement(x, foundationY, z, material));
            }
        }
        for (int y = 0; y < FORGE_PILLAR_HEIGHT; y++) {
            int radius = Math.max(FORGE_PILLAR_RADIUS, FORGE_TOP_RADIUS);
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    boolean withinTop = Math.abs(x) <= FORGE_TOP_RADIUS && Math.abs(z) <= FORGE_TOP_RADIUS;
                    boolean floorOrTop = withinTop && y == FORGE_PILLAR_HEIGHT - 1;
                    boolean withinWall = Math.abs(x) <= FORGE_PILLAR_RADIUS && Math.abs(z) <= FORGE_PILLAR_RADIUS;
                    boolean innerBase = y == 0 && withinWall;
                    boolean outerWall = y > 0 && withinWall && (Math.abs(x) == FORGE_PILLAR_RADIUS || Math.abs(z) == FORGE_PILLAR_RADIUS);
                    if (floorOrTop || innerBase || outerWall) {
                        Material material = x == 0 && y == 0 && z == 0
                            ? Material.RESPAWN_ANCHOR
                            : Material.BARRIER;
                        placements.add(new Placement(x, y, z, material));
                    }
                }
            }
        }
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                placements.add(new Placement(x, FORGE_PILLAR_HEIGHT, z, Material.BARRIER));
            }
        }
        if (!dungeon && shouldClearInterior(center)) {
            for (int y = 1; y <= FORGE_PILLAR_HEIGHT + 6; y++) {
                for (int x = -FORGE_TOP_RADIUS + 1; x <= FORGE_TOP_RADIUS - 1; x++) {
                    for (int z = -FORGE_TOP_RADIUS + 1; z <= FORGE_TOP_RADIUS - 1; z++) {
                        if (y == FORGE_PILLAR_HEIGHT - 1
                            || (y == FORGE_PILLAR_HEIGHT && Math.abs(x) <= 1 && Math.abs(z) <= 1)) {
                            continue;
                        }
                        placements.add(new Placement(x, y, z, Material.AIR));
                    }
                }
            }
        }
        return new BuildPlan(center.clone(), placements);
    }

    private Material dungeonFoundationMaterial(Location center) {
        World world = center.getWorld();
        if (world == null) {
            return Material.COBBLED_DEEPSLATE;
        }
        Map<Material, Integer> counts = new HashMap<>();
        int baseX = center.getBlockX();
        int baseY = center.getBlockY();
        int baseZ = center.getBlockZ();
        for (int yOffset = 1; yOffset <= 3; yOffset++) {
            int sampleY = baseY - yOffset;
            if (sampleY < world.getMinHeight() || sampleY >= world.getMaxHeight()) {
                continue;
            }
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    Material material = world.getBlockAt(baseX + x, sampleY, baseZ + z).getType();
                    if (isDungeonBlendMaterial(material)) {
                        counts.merge(material, 1, Integer::sum);
                    }
                }
            }
            if (!counts.isEmpty()) {
                break;
            }
        }
        return counts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(Material.COBBLED_DEEPSLATE);
    }

    private boolean isDungeonBlendMaterial(Material material) {
        return material == Material.COBBLED_DEEPSLATE
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

    private boolean shouldClearInterior(Location center) {
        int solidSamples = 0;
        for (int y = 1; y <= FORGE_PILLAR_HEIGHT + 4; y++) {
            Block block = center.clone().add(0, y, 0).getBlock();
            if (!block.isPassable()) {
                solidSamples++;
            }
        }
        return solidSamples >= 3;
    }

    private void buildMainArena(List<Placement> p, Theme t) {
        addDisc(p, 9, -1, t.foundation);
        addRing(p, 9, 0, t.outerRing);
        addRing(p, 8, 0, t.midRing);
        addDisc(p, 7, 0, t.floor);
        addRing(p, 6, 1, t.rune);
        addRing(p, 4, 1, t.highlight);
        addRing(p, 2, 1, t.accent);

        addCross(p, 8, 0, t.path);
        addDiagonalCross(p, 7, 0, t.path);
        addCross(p, 5, 1, t.path);

        addCornerPillars(p, 7, 9, t.pillar, t.cap);
        addCardinalObelisks(p, 8, 7, t.pillar, t.cap);

        // Inner altar frame (keep center clear for placed Ritual Core).
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }
                if (Math.abs(x) == 2 || Math.abs(z) == 2) {
                    p.add(new Placement(x, 0, z, t.innerFrame));
                } else {
                    p.add(new Placement(x, 1, z, t.accent));
                }
            }
        }
    }

    private void buildRaisedAltars(List<Placement> p, Theme t) {
        int[][] marks = new int[][]{{4, 0}, {-4, 0}, {0, 4}, {0, -4}};
        for (int[] mark : marks) {
            int x = mark[0];
            int z = mark[1];
            p.add(new Placement(x, 1, z, t.highlight));
            p.add(new Placement(x, 2, z, t.glow));
            p.add(new Placement(x, 1, z, t.highlight));
            if (x != 0) {
                p.add(new Placement(x - Integer.signum(x), 1, z, t.accent));
                p.add(new Placement(x + Integer.signum(x), 1, z, t.accent));
            } else {
                p.add(new Placement(x, 1, z - Integer.signum(z), t.accent));
                p.add(new Placement(x, 1, z + Integer.signum(z), t.accent));
            }
        }
    }

    private void buildFloatingCrown(List<Placement> p, Theme t) {
        addRing(p, 5, 6, t.crown);
        addRing(p, 4, 7, t.highlight);
        addCardinalMarks(p, 5, 7, t.glow);
        addDiagonalMarks(p, 4, 6, t.spark);
        addCardinalObelisks(p, 3, 5, t.spark, t.glow);
    }

    private void buildThemeOrnaments(List<Placement> p, WeaponType type, Theme t) {
        addCardinalMarks(p, 9, 1, t.glow);
        addRing(p, 5, 2, t.highlight);
        addCross(p, 3, 2, t.spark);
        if (type.ordinal() % 2 == 0) {
            addDiagonalCross(p, 4, 2, t.accent);
        }
        // Decorative sparks around arena edge.
        for (int i = 0; i < 24; i++) {
            double angle = (Math.PI * 2.0 * i) / 24.0;
            int x = (int) Math.round(Math.cos(angle) * 9.0);
            int z = (int) Math.round(Math.sin(angle) * 9.0);
            p.add(new Placement(x, 1, z, t.spark));
        }
        addRing(p, 7, 3, t.highlight);
        addRing(p, 6, 4, t.accent);
    }

    private Theme themeFor(WeaponType type) {
        return switch (type.barColor()) {
            case RED -> new Theme(
                Material.BLACKSTONE, Material.BASALT, Material.POLISHED_BLACKSTONE, Material.NETHERRACK,
                Material.RED_NETHER_BRICKS, Material.BASALT, Material.CRYING_OBSIDIAN, Material.NETHER_BRICKS, Material.MAGMA_BLOCK,
                Material.CHISELED_NETHER_BRICKS, Material.GLOWSTONE, Material.RED_STAINED_GLASS, Material.RED_CONCRETE, Material.SHROOMLIGHT
            );
            case BLUE -> new Theme(
                Material.PACKED_ICE, Material.BLUE_ICE, Material.SNOW_BLOCK, Material.CALCITE,
                Material.LIGHT_BLUE_CONCRETE, Material.PACKED_ICE, Material.END_ROD, Material.BLUE_ICE, Material.SNOW_BLOCK,
                Material.CHISELED_QUARTZ_BLOCK, Material.SEA_LANTERN, Material.LIGHT_BLUE_STAINED_GLASS, Material.CYAN_CONCRETE, Material.SEA_LANTERN
            );
            case GREEN -> new Theme(
                Material.MOSSY_STONE_BRICKS, Material.MOSS_BLOCK, Material.EMERALD_BLOCK, Material.ROOTED_DIRT,
                Material.LIME_CONCRETE, Material.MOSSY_COBBLESTONE, Material.EMERALD_BLOCK, Material.OAK_LEAVES, Material.MOSS_BLOCK,
                Material.CHISELED_STONE_BRICKS, Material.END_ROD, Material.LIME_STAINED_GLASS, Material.GREEN_CONCRETE, Material.SEA_LANTERN
            );
            case PINK -> new Theme(
                Material.CALCITE, Material.CHERRY_PLANKS, Material.PINK_STAINED_GLASS, Material.MOSS_BLOCK,
                Material.PINK_CONCRETE, Material.CHERRY_LOG, Material.FLOWERING_AZALEA, Material.CHERRY_LEAVES, Material.PINK_PETALS,
                Material.CHISELED_QUARTZ_BLOCK, Material.END_ROD, Material.PINK_STAINED_GLASS, Material.MAGENTA_CONCRETE, Material.SEA_LANTERN
            );
            case PURPLE -> new Theme(
                Material.POLISHED_BLACKSTONE_BRICKS, Material.CRYING_OBSIDIAN, Material.AMETHYST_BLOCK, Material.BLACKSTONE,
                Material.PURPLE_CONCRETE, Material.POLISHED_BLACKSTONE, Material.CRYING_OBSIDIAN, Material.OBSIDIAN, Material.SCULK,
                Material.CHISELED_POLISHED_BLACKSTONE, Material.SOUL_LANTERN, Material.PURPLE_STAINED_GLASS, Material.MAGENTA_CONCRETE, Material.SOUL_LANTERN
            );
            default -> new Theme(
                Material.POLISHED_TUFF, Material.CUT_COPPER, Material.GOLD_BLOCK, Material.SMOOTH_STONE,
                Material.YELLOW_CONCRETE, Material.CUT_COPPER, Material.LIGHTNING_ROD, Material.CUT_COPPER, Material.COPPER_BLOCK,
                Material.LODESTONE, Material.LIGHTNING_ROD, Material.YELLOW_STAINED_GLASS, Material.ORANGE_CONCRETE, Material.SEA_LANTERN
            );
        };
    }

    private void addRing(List<Placement> p, int radius, int y, Material m) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double dist = Math.sqrt((x * x) + (z * z));
                if (dist >= radius - 0.45 && dist <= radius + 0.45) {
                    p.add(new Placement(x, y, z, m));
                }
            }
        }
    }

    private void addDisc(List<Placement> p, int radius, int y, Material m) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if ((x * x) + (z * z) <= radius * radius) {
                    p.add(new Placement(x, y, z, m));
                }
            }
        }
    }

    private void addCross(List<Placement> p, int radius, int y, Material m) {
        for (int i = -radius; i <= radius; i++) {
            p.add(new Placement(i, y, 0, m));
            p.add(new Placement(0, y, i, m));
        }
    }

    private void addDiagonalCross(List<Placement> p, int radius, int y, Material m) {
        for (int i = -radius; i <= radius; i++) {
            p.add(new Placement(i, y, i, m));
            p.add(new Placement(i, y, -i, m));
        }
    }

    private void addDiagonalMarks(List<Placement> p, int offset, int y, Material m) {
        p.add(new Placement(offset, y, offset, m));
        p.add(new Placement(offset, y, -offset, m));
        p.add(new Placement(-offset, y, offset, m));
        p.add(new Placement(-offset, y, -offset, m));
    }

    private void addCornerPillars(List<Placement> p, int offset, int height, Material body, Material cap) {
        int[][] corners = new int[][]{{offset, offset}, {offset, -offset}, {-offset, offset}, {-offset, -offset}};
        for (int[] corner : corners) {
            for (int y = 1; y <= height; y++) {
                p.add(new Placement(corner[0], y, corner[1], y == height ? cap : body));
            }
        }
    }

    private void addCardinalObelisks(List<Placement> p, int offset, int height, Material body, Material cap) {
        int[][] marks = new int[][]{{offset, 0}, {-offset, 0}, {0, offset}, {0, -offset}};
        for (int[] mark : marks) {
            for (int y = 1; y <= height; y++) {
                p.add(new Placement(mark[0], y, mark[1], y == height ? cap : body));
            }
        }
    }

    private void addCardinalMarks(List<Placement> p, int offset, int y, Material m) {
        p.add(new Placement(offset, y, 0, m));
        p.add(new Placement(-offset, y, 0, m));
        p.add(new Placement(0, y, offset, m));
        p.add(new Placement(0, y, -offset, m));
    }

    public static class BuildPlan {
        private final Location center;
        private final List<Placement> placements;
        private final Map<LocationKey, BlockState> replaced = new HashMap<>();
        private final Map<LocationKey, String> originalBlockData = new HashMap<>();
        private final int minX;
        private final int maxX;
        private final int minY;
        private final int maxY;
        private final int minZ;
        private final int maxZ;
        private final int maxHorizontalRadius;
        private int pointer;
        private boolean captureOriginals = true;
        private boolean loadedOriginalSnapshots;

        public BuildPlan(Location center, List<Placement> placements) {
            this.center = center;
            this.placements = placements;
            int localMinX = 0;
            int localMaxX = 0;
            int localMinY = 0;
            int localMaxY = 0;
            int localMinZ = 0;
            int localMaxZ = 0;
            int localMaxHorizontalRadius = 0;
            if (!placements.isEmpty()) {
                localMinX = Integer.MAX_VALUE;
                localMaxX = Integer.MIN_VALUE;
                localMinY = Integer.MAX_VALUE;
                localMaxY = Integer.MIN_VALUE;
                localMinZ = Integer.MAX_VALUE;
                localMaxZ = Integer.MIN_VALUE;
                for (Placement placement : placements) {
                    localMinX = Math.min(localMinX, placement.x);
                    localMaxX = Math.max(localMaxX, placement.x);
                    localMinY = Math.min(localMinY, placement.y);
                    localMaxY = Math.max(localMaxY, placement.y);
                    localMinZ = Math.min(localMinZ, placement.z);
                    localMaxZ = Math.max(localMaxZ, placement.z);
                    localMaxHorizontalRadius = Math.max(localMaxHorizontalRadius,
                        Math.max(Math.abs(placement.x), Math.abs(placement.z)));
                }
            }
            this.minX = localMinX;
            this.maxX = localMaxX;
            this.minY = localMinY;
            this.maxY = localMaxY;
            this.minZ = localMinZ;
            this.maxZ = localMaxZ;
            this.maxHorizontalRadius = localMaxHorizontalRadius;
            this.pointer = 0;
        }

        public boolean placeNext(int count) {
            for (int i = 0; i < count; i++) {
                if (pointer >= placements.size()) {
                    return false;
                }
                place(placements.get(pointer++));
            }
            return pointer < placements.size();
        }

        public void placeAll() {
            while (pointer < placements.size()) {
                place(placements.get(pointer++));
            }
        }

        public void setCaptureOriginals(boolean captureOriginals) {
            this.captureOriginals = captureOriginals;
        }

        public void clearSpawnVolume(int horizontalPadding, int extraBelow, int extraAbove) {
            int baseX = center.getBlockX();
            int baseY = center.getBlockY();
            int baseZ = center.getBlockZ();
            int worldMinY = center.getWorld().getMinHeight();
            int worldMaxY = center.getWorld().getMaxHeight() - 1;
            int clearMinY = Math.max(worldMinY, baseY + minY - Math.max(0, extraBelow));
            int clearMaxY = Math.min(worldMaxY, baseY + maxY + Math.max(0, extraAbove));
            for (int x = baseX + minX - horizontalPadding; x <= baseX + maxX + horizontalPadding; x++) {
                for (int y = clearMinY; y <= clearMaxY; y++) {
                    for (int z = baseZ + minZ - horizontalPadding; z <= baseZ + maxZ + horizontalPadding; z++) {
                        Block block = center.getWorld().getBlockAt(x, y, z);
                        Material current = block.getType();
                        if (current.isAir() || current == Material.BEDROCK) {
                            continue;
                        }
                        rememberOriginal(block);
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }

        private void place(Placement placement) {
            Location loc = center.clone().add(placement.x, placement.y, placement.z);
            Block block = loc.getBlock();
            rememberOriginal(block);
            block.setType(placement.material, false);
        }

        private void rememberOriginal(Block block) {
            if (!captureOriginals) {
                return;
            }
            LocationKey key = new LocationKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
            replaced.putIfAbsent(key, block.getState());
            originalBlockData.putIfAbsent(key, block.getBlockData().getAsString());
        }

        public double integrityRatio() {
            int checked = 0;
            if (placements.isEmpty()) {
                return 1.0;
            }
            int intact = 0;
            for (Placement placement : placements) {
                if (placement.material == Material.AIR) {
                    continue;
                }
                checked++;
                Location loc = center.clone().add(placement.x, placement.y, placement.z);
                if (loc.getBlock().getType() == placement.material) {
                    intact++;
                }
            }
            return checked == 0 ? 1.0 : (double) intact / (double) checked;
        }

        public boolean isRitualOwnedBlock(Location location) {
            if (!center.getWorld().equals(location.getWorld())) {
                return false;
            }
            int relativeX = location.getBlockX() - center.getBlockX();
            int relativeY = location.getBlockY() - center.getBlockY();
            int relativeZ = location.getBlockZ() - center.getBlockZ();
            Material current = location.getBlock().getType();
            if (current.isAir()) {
                return false;
            }
            for (Placement placement : placements) {
                if (placement.material == Material.AIR) {
                    continue;
                }
                if (placement.x == relativeX
                    && placement.y == relativeY
                    && placement.z == relativeZ
                    && placement.material == current) {
                    return true;
                }
            }
            return false;
        }

        public void collapse(double chance) {
            for (Placement placement : placements) {
                Location loc = center.clone().add(placement.x, placement.y, placement.z);
                Block block = loc.getBlock();
                if (block.getType() != placement.material) {
                    continue;
                }
                if (ThreadLocalRandom.current().nextDouble() <= chance) {
                    block.setType(Material.AIR, false);
                }
            }
        }

        public boolean rollback() {
            boolean restored = false;
            if (loadedOriginalSnapshots && !originalBlockData.isEmpty()) {
                for (Map.Entry<LocationKey, String> entry : originalBlockData.entrySet()) {
                    LocationKey key = entry.getKey();
                    World world = Bukkit.getWorld(key.world());
                    if (world == null) {
                        continue;
                    }
                    try {
                        BlockData blockData = Bukkit.createBlockData(entry.getValue());
                        world.getBlockAt(key.x(), key.y(), key.z()).setBlockData(blockData, false);
                        restored = true;
                    } catch (IllegalArgumentException ignored) {
                        // Skip malformed legacy snapshots without blocking cleanup.
                    }
                }
                replaced.clear();
                originalBlockData.clear();
                loadedOriginalSnapshots = false;
                return restored;
            }
            for (BlockState state : replaced.values()) {
                state.update(true, false);
                restored = true;
            }
            replaced.clear();
            originalBlockData.clear();
            loadedOriginalSnapshots = false;
            return restored;
        }

        public boolean hasLoadedOriginalSnapshots() {
            return loadedOriginalSnapshots;
        }

        public List<SavedBlock> originalSnapshots() {
            return originalBlockData.entrySet().stream()
                .map(entry -> new SavedBlock(
                    entry.getKey().world(),
                    entry.getKey().x(),
                    entry.getKey().y(),
                    entry.getKey().z(),
                    entry.getValue()
                ))
                .toList();
        }

        public void loadOriginalSnapshots(List<SavedBlock> snapshots) {
            if (snapshots.isEmpty()) {
                return;
            }
            for (SavedBlock snapshot : snapshots) {
                originalBlockData.putIfAbsent(
                    new LocationKey(snapshot.world(), snapshot.x(), snapshot.y(), snapshot.z()),
                    snapshot.blockData()
                );
            }
            loadedOriginalSnapshots = true;
        }

        public void clearRitualBlocks() {
            for (Placement placement : placements) {
                if (placement.material == Material.AIR) {
                    continue;
                }
                Location loc = center.clone().add(placement.x, placement.y, placement.z);
                Block block = loc.getBlock();
                if (block.getType() == placement.material) {
                    block.setType(Material.AIR, false);
                }
            }
        }

        public Location center() {
            return center;
        }

        public int maxHorizontalRadius() {
            return maxHorizontalRadius;
        }

        public int minYOffset() {
            return minY;
        }

        public int maxYOffset() {
            return maxY;
        }

        public boolean contains(Location location, int horizontalPadding, int verticalPadding) {
            if (!center.getWorld().equals(location.getWorld())) {
                return false;
            }
            int relativeX = location.getBlockX() - center.getBlockX();
            int relativeY = location.getBlockY() - center.getBlockY();
            int relativeZ = location.getBlockZ() - center.getBlockZ();
            return relativeX >= (minX - horizontalPadding)
                && relativeX <= (maxX + horizontalPadding)
                && relativeY >= (minY - verticalPadding)
                && relativeY <= (maxY + verticalPadding)
                && relativeZ >= (minZ - horizontalPadding)
                && relativeZ <= (maxZ + horizontalPadding);
        }
    }

    public record SavedBlock(String world, int x, int y, int z, String blockData) {}

    private record Theme(
        Material foundation,
        Material outerRing,
        Material midRing,
        Material floor,
        Material rune,
        Material pillar,
        Material cap,
        Material crown,
        Material path,
        Material innerFrame,
        Material spark,
        Material highlight,
        Material accent,
        Material glow
    ) {}

    public record Placement(int x, int y, int z, Material material) {}

    private record LocationKey(String world, int x, int y, int z) {}
}
