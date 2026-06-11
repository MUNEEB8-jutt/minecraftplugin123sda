package com.legendaryweaponssmp.structures;

import com.legendaryweaponssmp.core.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;

public class SchematicPasteService {
    private static final IslandTemplate[] FLOATING_ISLAND_SCHEMATICS = new IslandTemplate[]{
        new IslandTemplate("schematics/floating_islands/FI50.schem", 0, -270),
        new IslandTemplate("schematics/floating_islands/FI30.schem", 110, -247),
        new IslandTemplate("schematics/floating_islands/FI30.schem", 201, -181),
        new IslandTemplate("schematics/floating_islands/FI15.schem", 257, -84),
        new IslandTemplate("schematics/floating_islands/FI30.schem", 268, 28),
        new IslandTemplate("schematics/floating_islands/FI50.schem", 234, 135),
        new IslandTemplate("schematics/floating_islands/FI30.schem", 158, 219),
        new IslandTemplate("schematics/floating_islands/FI15.schem", 56, 264),
        new IslandTemplate("schematics/floating_islands/FI30.schem", -56, 264),
        new IslandTemplate("schematics/floating_islands/FI50.schem", -158, 219),
        new IslandTemplate("schematics/floating_islands/FI30.schem", -234, 135),
        new IslandTemplate("schematics/floating_islands/FI15.schem", -268, 28),
        new IslandTemplate("schematics/floating_islands/FI30.schem", -257, -84),
        new IslandTemplate("schematics/floating_islands/FI50.schem", -201, -181),
        new IslandTemplate("schematics/floating_islands/FI30.schem", -110, -247)
    };
    private static final int FLOATING_ISLAND_TOP_Y = 240;
    private static final int PLACE_BUDGET_PER_TICK = 128_000;
    private static final int MAX_ACTIVE_CHUNK_BATCHES = 24;
    private static final int MAX_CHUNK_LOAD_REQUESTS_PER_TICK = 24;
    private static final int PRELOAD_LOOKAHEAD_BATCHES = 768;
    private static final int MAX_PRELOADED_CHUNK_TICKETS = 1024;
    private static final int CHUNK_LOAD_RETRY_TICKS = 100;
    private static final int CLEAR_EXTRA_BELOW_COLUMN = 0;
    private static final int CLEAR_EXTRA_ABOVE_COLUMN = 0;
    private static final int SKY_TOP_MARGIN = 1;
    private static final long MAX_WORK_NANOS_PER_TICK = 36_000_000L;
    private static final int PROGRESS_MESSAGE_TICKS = 100;
    private static final int PACKED_Y_BITS = 10;
    private static final int PACKED_Y_MASK = (1 << PACKED_Y_BITS) - 1;
    private static final int PACKED_LOCAL_MASK = 0xF;

    private static final int TAG_END = 0;
    private static final int TAG_BYTE = 1;
    private static final int TAG_SHORT = 2;
    private static final int TAG_INT = 3;
    private static final int TAG_LONG = 4;
    private static final int TAG_FLOAT = 5;
    private static final int TAG_DOUBLE = 6;
    private static final int TAG_BYTE_ARRAY = 7;
    private static final int TAG_STRING = 8;
    private static final int TAG_LIST = 9;
    private static final int TAG_COMPOUND = 10;
    private static final int TAG_INT_ARRAY = 11;
    private static final int TAG_LONG_ARRAY = 12;

    private final JavaPlugin plugin;
    private final MessageService messageService;
    private final AtomicBoolean pasteRunning = new AtomicBoolean(false);
    private CompletableFuture<ArrayList<PreparedIsland>> schematicFuture;

    public SchematicPasteService(JavaPlugin plugin, MessageService messageService) {
        this.plugin = plugin;
        this.messageService = messageService;
        preloadSchematic();
    }

    public boolean pasteDungeon(Player player, Location origin, Runnable onComplete) {
        if (player == null || origin == null || origin.getWorld() == null) {
            return false;
        }
        if (!pasteRunning.compareAndSet(false, true)) {
            messageService.send(player, "&cA dungeon schematic paste is already running. Wait for it to finish.");
            return false;
        }

        UUID playerId = player.getUniqueId();
        String worldName = origin.getWorld().getName();
        Location pasteOrigin = new Location(
            origin.getWorld(),
            origin.getBlockX(),
            origin.getBlockY(),
            origin.getBlockZ()
        );

        CompletableFuture<ArrayList<PreparedIsland>> future = preloadSchematic();
        if (future.isDone() && !future.isCompletedExceptionally()) {
            ArrayList<PreparedIsland> islands = future.join();
            messageService.send(player, "&aFloating island dungeon paste started. &715 prepared islands will paste safely.");
            beginPaste(playerId, worldName, pasteOrigin, islands, onComplete);
            return true;
        }

        messageService.send(player, "&eFloating island dungeon paste queued. &7Schematics are still preparing, then blocks will start.");
        future.whenComplete((islands, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (throwable != null || islands == null || islands.isEmpty()) {
                pasteRunning.set(false);
                plugin.getLogger().log(Level.SEVERE, "Failed to load floating island dungeon schematics", throwable);
                Player live = Bukkit.getPlayer(playerId);
                if (live != null) {
                    messageService.send(live, "&cFailed to load floating island schematics. Check server console.");
                }
                return;
            }
            Player live = Bukkit.getPlayer(playerId);
            if (live != null) {
                messageService.send(live, "&aFloating island schematics loaded. Paste is starting now.");
            }
            beginPaste(playerId, worldName, pasteOrigin, islands, onComplete);
        }));
        return true;
    }

    private synchronized CompletableFuture<ArrayList<PreparedIsland>> preloadSchematic() {
        if (schematicFuture != null) {
            return schematicFuture;
        }
        schematicFuture = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ArrayList<PreparedIsland> islands = new ArrayList<>(FLOATING_ISLAND_SCHEMATICS.length);
                int totalBlocks = 0;
                int totalChunks = 0;
                for (IslandTemplate template : FLOATING_ISLAND_SCHEMATICS) {
                    try (InputStream resource = plugin.getResource(template.resourcePath())) {
                        if (resource == null) {
                            throw new IOException("Missing bundled schematic resource: " + template.resourcePath());
                        }
                        RawSchematicData raw = readSchematic(resource);
                        PreparedSchematicData schematic = prepareSchematic(raw);
                        islands.add(new PreparedIsland(template, schematic));
                        totalBlocks += schematic.totalBlocks();
                        totalChunks += schematic.localChunks().size();
                        plugin.getLogger().info("Prepared floating island schematic " + template.resourcePath()
                            + " (" + schematic.width() + "x" + schematic.height() + "x" + schematic.length()
                            + ", " + schematic.localChunks().size() + " chunk batch(es), "
                            + schematic.totalBlocks() + " non-air block(s)).");
                    }
                }
                plugin.getLogger().info("Preloaded floating island dungeon set ("
                    + islands.size() + " island(s), " + totalChunks + " chunk batch(es), "
                    + totalBlocks + " non-air block(s)).");
                schematicFuture.complete(islands);
            } catch (Throwable throwable) {
                schematicFuture.completeExceptionally(throwable);
                plugin.getLogger().log(Level.SEVERE, "Failed to preload floating island schematics", throwable);
            }
        });
        return schematicFuture;
    }

    private void beginPaste(UUID playerId, String worldName, Location origin, ArrayList<PreparedIsland> islands, Runnable onComplete) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            pasteRunning.set(false);
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            messageService.send(player, "&7Preparing floating island dungeon paste around &f"
                + origin.getBlockX() + " " + origin.getBlockY() + " " + origin.getBlockZ());
        }
        pasteIsland(playerId, worldName, origin, islands, 0, onComplete);
    }

    private void pasteIsland(UUID playerId,
                             String worldName,
                             Location origin,
                             ArrayList<PreparedIsland> islands,
                             int index,
                             Runnable onComplete) {
        if (index >= islands.size()) {
            pasteRunning.set(false);
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            pasteRunning.set(false);
            return;
        }
        pasteRunning.set(true);
        PreparedIsland island = islands.get(index);
        PreparedSchematicData schematic = island.schematic();
        IslandTemplate template = island.template();
        BlockData[] palette = buildPalette(schematic);
        int originX = origin.getBlockX() + template.offsetX();
        int originY = computeDungeonOriginY(world, origin, schematic);
        int originZ = origin.getBlockZ() + template.offsetZ();
        ChunkPlan plan = buildChunkPlan(schematic, originX, originZ);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            messageService.send(player, "&7Pasting island &f" + (index + 1) + "/" + islands.size()
                + " &7at &f" + originX + " " + originY + " " + originZ + "&7.");
        }
        startChunkPaste(playerId, worldName, originX, originY, originZ, world.getMinHeight(), world.getMaxHeight(), schematic, plan, palette, () -> {
            pasteRunning.set(true);
            pasteIsland(playerId, worldName, origin, islands, index + 1, onComplete);
        });
    }

    private int computeDungeonOriginY(World world, Location origin, PreparedSchematicData schematic) {
        int topTargetY = Math.min(FLOATING_ISLAND_TOP_Y, world.getMaxHeight() - SKY_TOP_MARGIN);
        int wantedOriginY = topTargetY - schematic.offsetY() - schematic.maxNonAirY();
        int minOriginY = world.getMinHeight() + 4 - schematic.offsetY() - schematic.minNonAirY();
        int maxOriginY = world.getMaxHeight() - SKY_TOP_MARGIN - schematic.offsetY() - schematic.maxNonAirY();
        if (maxOriginY < minOriginY) {
            maxOriginY = minOriginY;
        }
        int aligned = Math.max(minOriginY, Math.min(maxOriginY, wantedOriginY));
        plugin.getLogger().info("Floating island paste Y aligned at originY=" + aligned
            + " (topTargetY=" + topTargetY
            + ", targetTopY=" + (aligned + schematic.offsetY() + schematic.maxNonAirY())
            + ", nonAirY=" + schematic.minNonAirY() + ".." + schematic.maxNonAirY() + ").");
        return aligned;
    }

    private void startChunkPaste(UUID playerId,
                                 String worldName,
                                 int originX,
                                 int originY,
                                 int originZ,
                                 int minHeight,
                                 int maxHeight,
                                 PreparedSchematicData schematic,
                                 ChunkPlan plan,
                                 BlockData[] palette,
                                 Runnable onComplete) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            pasteRunning.set(false);
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            messageService.send(player, "&aChunk-wise dungeon paste started. &7Pasting &f"
                + plan.totalChunks() + " &7chunk batch(es), &f" + plan.totalBlocks() + " &7block(s).");
        }
        new ChunkPasteTask(playerId, world, originX, originY, originZ, minHeight, maxHeight, schematic, plan, palette, onComplete)
            .runTaskTimer(plugin, 1L, 1L);
    }

    private BlockData[] buildPalette(PreparedSchematicData schematic) {
        int maxId = schematic.paletteById().keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        BlockData[] palette = new BlockData[maxId + 1];
        int invalid = 0;
        for (Map.Entry<Integer, String> entry : schematic.paletteById().entrySet()) {
            int id = entry.getKey();
            String state = entry.getValue();
            if (id < 0 || id >= palette.length || state == null) {
                continue;
            }
            if (isAirState(state)) {
                palette[id] = Material.AIR.createBlockData();
                continue;
            }
            try {
                BlockData blockData = Bukkit.createBlockData(state);
                palette[id] = blockData;
            } catch (IllegalArgumentException ex) {
                invalid++;
            }
        }
        if (invalid > 0) {
            plugin.getLogger().warning("Skipped " + invalid + " unsupported block state(s) while preparing dungeon schematic.");
        }
        return palette;
    }

    private RawSchematicData readSchematic(InputStream stream) throws IOException {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(new GZIPInputStream(stream)))) {
            int rootTag = input.readUnsignedByte();
            if (rootTag != TAG_COMPOUND) {
                throw new IOException("Expected NBT compound root, got tag " + rootTag);
            }
            String rootName = readString(input);
            SchematicBuilder builder = new SchematicBuilder();
            readCompound(input, rootName, builder);
            return builder.build();
        }
    }

    private PreparedSchematicData prepareSchematic(RawSchematicData raw) throws IOException {
        boolean[] airPalette = buildAirPalette(raw);
        Map<Long, ChunkBucketBuilder> buckets = new HashMap<>(8192);
        byte[] data = raw.blockData();
        int dataPosition = 0;
        long blockIndex = 0L;
        long layerSize = (long) raw.width() * raw.length();
        long totalSchematicBlocks = layerSize * raw.height();
        int minNonAirY = Integer.MAX_VALUE;
        int maxNonAirY = 0;
        int nonAirCount = 0;
        int skippedVillageBlocks = 0;
        int[] blocksByY = new int[Math.min(raw.height(), PACKED_Y_MASK + 1)];
        int[] rowMinX = new int[raw.length()];
        int[] rowMaxX = new int[raw.length()];
        for (int z = 0; z < raw.length(); z++) {
            rowMinX[z] = Integer.MAX_VALUE;
            rowMaxX[z] = -1;
        }
        while (dataPosition < data.length && blockIndex < totalSchematicBlocks) {
            int paletteId = 0;
            int shift = 0;
            boolean completeVarInt = false;
            while (dataPosition < data.length) {
                int current = data[dataPosition++] & 0xFF;
                paletteId |= (current & 0x7F) << shift;
                if ((current & 0x80) == 0) {
                    completeVarInt = true;
                    break;
                }
                shift += 7;
                if (shift > 35) {
                    throw new IOException("Invalid schematic palette varint at block " + blockIndex);
                }
            }
            if (!completeVarInt) {
                throw new EOFException("Unexpected end of schematic block data at block " + blockIndex);
            }

            long currentIndex = blockIndex++;
            if (paletteId >= 0 && paletteId < airPalette.length && airPalette[paletteId]) {
                continue;
            }
            int relativeX = (int) (currentIndex % raw.width());
            int relativeZ = (int) ((currentIndex / raw.width()) % raw.length());
            int relativeY = (int) (currentIndex / layerSize);
            if (relativeY > PACKED_Y_MASK) {
                continue;
            }
            String state = raw.paletteById().get(paletteId);
            if (shouldSkipDungeonBlock(state, relativeX, relativeY, relativeZ)) {
                skippedVillageBlocks++;
                continue;
            }
            minNonAirY = Math.min(minNonAirY, relativeY);
            maxNonAirY = Math.max(maxNonAirY, relativeY);
            nonAirCount++;
            if (relativeY < blocksByY.length) {
                blocksByY[relativeY]++;
            }
            if (relativeX < rowMinX[relativeZ]) {
                rowMinX[relativeZ] = relativeX;
            }
            if (relativeX > rowMaxX[relativeZ]) {
                rowMaxX[relativeZ] = relativeX;
            }
            int chunkX = relativeX >> 4;
            int chunkZ = relativeZ >> 4;
            long chunkKey = chunkKey(chunkX, chunkZ);
            ChunkBucketBuilder bucket = buckets.computeIfAbsent(chunkKey, ignored -> new ChunkBucketBuilder(chunkX, chunkZ));
            bucket.add(relativeX & PACKED_LOCAL_MASK, relativeY, relativeZ & PACKED_LOCAL_MASK, paletteId);
        }
        if (minNonAirY == Integer.MAX_VALUE) {
            minNonAirY = 0;
        }
        int groundAnchorY = Math.max(Math.max(0, -raw.offsetY()), percentileY(blocksByY, nonAirCount, 0.95));
        groundAnchorY = Math.max(minNonAirY, Math.min(maxNonAirY, groundAnchorY));
        int interiorClearColumns = addRowFootprintClearColumns(raw, buckets, rowMinX, rowMaxX, minNonAirY, maxNonAirY);

        ArrayList<LocalChunkBatch> batches = new ArrayList<>(buckets.size());
        int totalBlocks = 0;
        int totalClearBlocks = 0;
        for (ChunkBucketBuilder bucket : buckets.values()) {
            LocalChunkBatch batch = bucket.toBatch();
            totalBlocks += batch.blocks().length;
            totalClearBlocks += batch.clearBlocks();
            batches.add(batch);
        }
        return new PreparedSchematicData(
            raw.width(),
            raw.height(),
            raw.length(),
            raw.offsetX(),
            raw.offsetY(),
            raw.offsetZ(),
            raw.paletteById(),
            batches,
            totalBlocks,
            totalClearBlocks,
            interiorClearColumns,
            skippedVillageBlocks,
            minNonAirY,
            maxNonAirY,
            groundAnchorY
        );
    }

    private int addRowFootprintClearColumns(RawSchematicData raw,
                                            Map<Long, ChunkBucketBuilder> buckets,
                                            int[] rowMinX,
                                            int[] rowMaxX,
                                            int minY,
                                            int maxY) {
        if (minY > maxY) {
            return 0;
        }
        int columns = 0;
        for (int z = 0; z < raw.length(); z++) {
            int minX = rowMinX[z];
            int maxX = rowMaxX[z];
            if (maxX < minX) {
                continue;
            }
            for (int x = minX; x <= maxX; x++) {
                int chunkX = x >> 4;
                int chunkZ = z >> 4;
                long chunkKey = chunkKey(chunkX, chunkZ);
                ChunkBucketBuilder bucket = buckets.computeIfAbsent(chunkKey, ignored -> new ChunkBucketBuilder(chunkX, chunkZ));
                bucket.coverColumn(x & PACKED_LOCAL_MASK, minY, z & PACKED_LOCAL_MASK, maxY);
                columns++;
            }
        }
        return columns;
    }

    private boolean shouldSkipDungeonBlock(String state, int x, int y, int z) {
        if (state == null || state.isBlank()) {
            return false;
        }
        if (isLowVillageRegion(x, y, z) && isLowVillageMaterial(state)) {
            return true;
        }
        return isMidVillageRegion(x, y, z) && isVillagePropMaterial(state);
    }

    private boolean isLowVillageRegion(int x, int y, int z) {
        return x >= 0 && x <= 380
            && y >= 0 && y <= 8
            && z >= 330 && z <= 930;
    }

    private boolean isMidVillageRegion(int x, int y, int z) {
        return x >= 480 && x <= 830
            && y >= 20 && y <= 45
            && z >= 430 && z <= 720;
    }

    private boolean isLowVillageMaterial(String state) {
        String material = stateName(state);
        return material.startsWith("minecraft:oak_")
            || material.startsWith("minecraft:white_bed")
            || material.startsWith("minecraft:yellow_bed")
            || material.startsWith("minecraft:red_bed")
            || material.startsWith("minecraft:glass_pane")
            || material.startsWith("minecraft:wall_torch")
            || material.startsWith("minecraft:torch")
            || material.startsWith("minecraft:chest")
            || material.startsWith("minecraft:bell")
            || material.startsWith("minecraft:cartography_table")
            || material.startsWith("minecraft:cobblestone")
            || material.startsWith("minecraft:mossy_cobblestone")
            || material.startsWith("minecraft:dandelion")
            || material.startsWith("minecraft:poppy");
    }

    private boolean isVillagePropMaterial(String state) {
        String material = stateName(state);
        return material.contains("_bed")
            || material.startsWith("minecraft:barrel")
            || material.startsWith("minecraft:beehive")
            || material.startsWith("minecraft:blast_furnace")
            || material.startsWith("minecraft:fletching_table")
            || material.startsWith("minecraft:crafting_table")
            || material.startsWith("minecraft:campfire")
            || material.startsWith("minecraft:cauldron")
            || material.startsWith("minecraft:grindstone")
            || material.startsWith("minecraft:cartography_table")
            || material.startsWith("minecraft:chest")
            || material.startsWith("minecraft:bell");
    }

    private String stateName(String state) {
        int propertiesStart = state.indexOf('[');
        return propertiesStart < 0 ? state : state.substring(0, propertiesStart);
    }

    private int percentileY(int[] counts, int total, double percentile) {
        if (counts.length == 0 || total <= 0) {
            return 0;
        }
        int target = Math.max(1, (int) Math.ceil(total * percentile));
        int seen = 0;
        for (int y = 0; y < counts.length; y++) {
            seen += counts[y];
            if (seen >= target) {
                return y;
            }
        }
        return counts.length - 1;
    }

    private ChunkPlan buildChunkPlan(PreparedSchematicData schematic, int originX, int originZ) {
        int originChunkX = originX >> 4;
        int originChunkZ = originZ >> 4;
        ArrayList<LocalChunkBatch> batches = new ArrayList<>(schematic.localChunks());
        batches.sort((left, right) -> Long.compare(
            localChunkDistanceSquared(schematic, left, originX, originZ, originChunkX, originChunkZ),
            localChunkDistanceSquared(schematic, right, originX, originZ, originChunkX, originChunkZ)
        ));
        return new ChunkPlan(new ArrayDeque<>(batches), batches.size(), schematic.totalBlocks(), schematic.totalClearBlocks());
    }

    private boolean[] buildAirPalette(RawSchematicData schematic) {
        int maxId = schematic.paletteById().keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        boolean[] airPalette = new boolean[maxId + 1];
        for (Map.Entry<Integer, String> entry : schematic.paletteById().entrySet()) {
            int id = entry.getKey();
            String state = entry.getValue();
            if (id >= 0 && id < airPalette.length && isAirState(state)) {
                airPalette[id] = true;
            }
        }
        return airPalette;
    }

    private boolean isAirState(String state) {
        return "minecraft:air".equals(state)
            || "minecraft:cave_air".equals(state)
            || "minecraft:void_air".equals(state);
    }

    private long localChunkDistanceSquared(PreparedSchematicData schematic,
                                           LocalChunkBatch batch,
                                           int originX,
                                           int originZ,
                                           int originChunkX,
                                           int originChunkZ) {
        int targetChunkX = (originX + schematic.offsetX() + (batch.chunkX() << 4) + 8) >> 4;
        int targetChunkZ = (originZ + schematic.offsetZ() + (batch.chunkZ() << 4) + 8) >> 4;
        return chunkDistanceSquared(targetChunkX, targetChunkZ, originChunkX, originChunkZ);
    }

    private static int packChunkBlock(int localX, int shiftedY, int localZ, int paletteId) {
        return (paletteId << 18)
            | ((shiftedY & PACKED_Y_MASK) << 8)
            | ((localZ & PACKED_LOCAL_MASK) << 4)
            | (localX & PACKED_LOCAL_MASK);
    }

    private static int unpackLocalX(int packed) {
        return packed & PACKED_LOCAL_MASK;
    }

    private static int unpackLocalZ(int packed) {
        return (packed >>> 4) & PACKED_LOCAL_MASK;
    }

    private static int unpackShiftedY(int packed) {
        return (packed >>> 8) & PACKED_Y_MASK;
    }

    private static int unpackChunkPaletteId(int packed) {
        return packed >>> 18;
    }

    private static int packColumnBounds(int minY, int maxY) {
        return ((maxY & PACKED_Y_MASK) << PACKED_Y_BITS)
            | (minY & PACKED_Y_MASK);
    }

    private static int unpackColumnMinY(int packed) {
        return packed & PACKED_Y_MASK;
    }

    private static int unpackColumnMaxY(int packed) {
        return (packed >>> PACKED_Y_BITS) & PACKED_Y_MASK;
    }

    private long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
    }

    private long chunkDistanceSquared(int chunkX, int chunkZ, int originChunkX, int originChunkZ) {
        long dx = chunkX - originChunkX;
        long dz = chunkZ - originChunkZ;
        return dx * dx + dz * dz;
    }

    private void readCompound(DataInputStream input, String path, SchematicBuilder builder) throws IOException {
        while (true) {
            int tag = input.readUnsignedByte();
            if (tag == TAG_END) {
                return;
            }
            String name = readString(input);
            String fullPath = path == null || path.isBlank() ? name : path + "." + name;
            if ("Schematic.Blocks.Palette".equals(path) && tag == TAG_INT) {
                builder.paletteById.put(input.readInt(), name);
                continue;
            }
            readPayload(input, tag, fullPath, builder);
        }
    }

    private void readPayload(DataInputStream input, int tag, String path, SchematicBuilder builder) throws IOException {
        switch (tag) {
            case TAG_BYTE -> input.readByte();
            case TAG_SHORT -> builder.setDimension(path, input.readShort());
            case TAG_INT -> builder.setDimension(path, input.readInt());
            case TAG_LONG -> input.readLong();
            case TAG_FLOAT -> input.readFloat();
            case TAG_DOUBLE -> input.readDouble();
            case TAG_BYTE_ARRAY -> {
                int length = input.readInt();
                if ("Schematic.Blocks.Data".equals(path)) {
                    byte[] data = new byte[length];
                    input.readFully(data);
                    builder.blockData = data;
                } else {
                    skipFully(input, length);
                }
            }
            case TAG_STRING -> readString(input);
            case TAG_LIST -> readList(input, path, builder);
            case TAG_COMPOUND -> readCompound(input, path, builder);
            case TAG_INT_ARRAY -> {
                int length = input.readInt();
                if ("Schematic.Offset".equals(path) && length >= 3) {
                    builder.offsetX = input.readInt();
                    builder.offsetY = input.readInt();
                    builder.offsetZ = input.readInt();
                    for (int i = 3; i < length; i++) {
                        input.readInt();
                    }
                } else {
                    skipFully(input, (long) length * Integer.BYTES);
                }
            }
            case TAG_LONG_ARRAY -> {
                int length = input.readInt();
                skipFully(input, (long) length * Long.BYTES);
            }
            default -> throw new IOException("Unsupported NBT tag " + tag + " at " + path);
        }
    }

    private void readList(DataInputStream input, String path, SchematicBuilder builder) throws IOException {
        int elementTag = input.readUnsignedByte();
        int length = input.readInt();
        if ("Schematic.Offset".equals(path) && elementTag == TAG_INT && length >= 3) {
            builder.offsetX = input.readInt();
            builder.offsetY = input.readInt();
            builder.offsetZ = input.readInt();
            for (int i = 3; i < length; i++) {
                input.readInt();
            }
            return;
        }
        for (int i = 0; i < length; i++) {
            skipPayload(input, elementTag);
        }
    }

    private void skipPayload(DataInputStream input, int tag) throws IOException {
        switch (tag) {
            case TAG_END -> {
            }
            case TAG_BYTE -> input.readByte();
            case TAG_SHORT -> input.readShort();
            case TAG_INT -> input.readInt();
            case TAG_LONG -> input.readLong();
            case TAG_FLOAT -> input.readFloat();
            case TAG_DOUBLE -> input.readDouble();
            case TAG_BYTE_ARRAY -> skipFully(input, input.readInt());
            case TAG_STRING -> readString(input);
            case TAG_LIST -> {
                int elementTag = input.readUnsignedByte();
                int length = input.readInt();
                for (int i = 0; i < length; i++) {
                    skipPayload(input, elementTag);
                }
            }
            case TAG_COMPOUND -> {
                while (true) {
                    int nestedTag = input.readUnsignedByte();
                    if (nestedTag == TAG_END) {
                        return;
                    }
                    readString(input);
                    skipPayload(input, nestedTag);
                }
            }
            case TAG_INT_ARRAY -> skipFully(input, (long) input.readInt() * Integer.BYTES);
            case TAG_LONG_ARRAY -> skipFully(input, (long) input.readInt() * Long.BYTES);
            default -> throw new IOException("Unsupported NBT tag " + tag);
        }
    }

    private String readString(DataInputStream input) throws IOException {
        int length = input.readUnsignedShort();
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void skipFully(DataInputStream input, long bytes) throws IOException {
        long remaining = bytes;
        while (remaining > 0) {
            long skipped = input.skip(remaining);
            if (skipped <= 0) {
                if (input.read() == -1) {
                    throw new EOFException("Unexpected end of NBT stream");
                }
                skipped = 1;
            }
            remaining -= skipped;
        }
    }

    private final class ChunkPasteTask extends BukkitRunnable {
        private final UUID playerId;
        private final World world;
        private final int originX;
        private final int originY;
        private final int originZ;
        private final int minHeight;
        private final int maxHeight;
        private final PreparedSchematicData schematic;
        private final ChunkPlan plan;
        private final BlockData[] palette;
        private final Runnable onComplete;
        private final ArrayList<ActiveChunkBatch> activeChunks = new ArrayList<>(MAX_ACTIVE_CHUNK_BATCHES);
        private final Map<Long, Integer> ticketCounts = new HashMap<>();
        private final Map<Long, Integer> requestedChunkTicks = new HashMap<>();
        private final Set<Long> preloadTickets = new HashSet<>();
        private final Queue<Long> preloadTicketOrder = new ArrayDeque<>();
        private int clearChecks;
        private int clearedExistingBlocks;
        private int placedBlocks;
        private int completedChunks;
        private int skippedBlocks;
        private int ticks;
        private int chunkRequestsThisTick;
        private boolean finished;

        private ChunkPasteTask(UUID playerId,
                               World world,
                               int originX,
                               int originY,
                               int originZ,
                               int minHeight,
                               int maxHeight,
                               PreparedSchematicData schematic,
                               ChunkPlan plan,
                               BlockData[] palette,
                               Runnable onComplete) {
            this.playerId = playerId;
            this.world = world;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.minHeight = minHeight;
            this.maxHeight = maxHeight;
            this.schematic = schematic;
            this.plan = plan;
            this.palette = palette;
            this.onComplete = onComplete;
        }

        @Override
        public void run() {
            ticks++;
            chunkRequestsThisTick = 0;
            long deadline = System.nanoTime() + MAX_WORK_NANOS_PER_TICK;
            int placedThisTick = 0;

            fillActiveChunks();
            preloadChunks();

            int activeIndex = 0;
            while (activeIndex < activeChunks.size()
                && placedThisTick < PLACE_BUDGET_PER_TICK
                && System.nanoTime() < deadline) {
                ActiveChunkBatch active = activeChunks.get(activeIndex);
                if (!active.ensureLoaded()) {
                    if (active.done()) {
                        active.releaseTickets();
                        activeChunks.remove(activeIndex);
                        completedChunks++;
                        fillActiveChunks();
                        continue;
                    }
                    activeIndex++;
                    continue;
                }
                if (!active.allChunksLoaded()) {
                    active.loaded = false;
                    activeIndex++;
                    continue;
                }

                while (!active.clearDone()
                    && placedThisTick < PLACE_BUDGET_PER_TICK
                    && System.nanoTime() < deadline) {
                    active.clearNextBlock();
                    clearChecks++;
                    placedThisTick++;
                }
                if (!active.clearDone()) {
                    activeIndex++;
                    continue;
                }

                while (!active.done()
                    && placedThisTick < PLACE_BUDGET_PER_TICK
                    && System.nanoTime() < deadline) {
                    int packed = active.nextBlock();
                    int paletteId = unpackChunkPaletteId(packed);
                    if (paletteId < 0 || paletteId >= palette.length) {
                        skippedBlocks++;
                        continue;
                    }
                    BlockData blockData = palette[paletteId];
                    if (blockData == null || blockData.getMaterial().isAir()) {
                        skippedBlocks++;
                        continue;
                    }
                    int targetX = originX + schematic.offsetX() + (active.batch.chunkX() << 4) + unpackLocalX(packed);
                    int targetY = originY + schematic.offsetY() + unpackShiftedY(packed);
                    int targetZ = originZ + schematic.offsetZ() + (active.batch.chunkZ() << 4) + unpackLocalZ(packed);
                    if (targetY < minHeight || targetY >= maxHeight) {
                        skippedBlocks++;
                        continue;
                    }
                    world.getBlockAt(targetX, targetY, targetZ).setBlockData(blockData, false);
                    clearFloatingDecorationAbove(targetX, targetY, targetZ);
                    placedBlocks++;
                    placedThisTick++;
                }

                if (active.done()) {
                    active.releaseTickets();
                    activeChunks.remove(activeIndex);
                    completedChunks++;
                    fillActiveChunks();
                    continue;
                }
                activeIndex++;
            }

            if (activeChunks.isEmpty() && plan.chunks().isEmpty()) {
                finish();
                return;
            }

            if (ticks % PROGRESS_MESSAGE_TICKS == 0) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    long doneWork = (long) clearChecks + placedBlocks + skippedBlocks;
                    long totalWork = (long) plan.totalClearBlocks() + plan.totalBlocks();
                    int percent = (int) Math.min(99, Math.round((doneWork * 100.0) / Math.max(1L, totalWork)));
                    messageService.send(player, "&7Dungeon paste progress: &f" + percent + "% &7("
                        + placedBlocks + "/" + plan.totalBlocks() + " blocks, "
                        + clearChecks + "/" + plan.totalClearBlocks() + " clear, "
                        + completedChunks + "/" + plan.totalChunks() + " chunk batches)");
                }
            }
        }

        private void fillActiveChunks() {
            while (activeChunks.size() < MAX_ACTIVE_CHUNK_BATCHES && !plan.chunks().isEmpty()) {
                activeChunks.add(new ActiveChunkBatch(plan.chunks().remove()));
            }
        }

        private void preloadChunks() {
            for (ActiveChunkBatch active : activeChunks) {
                requestBatchChunks(active.batch);
                if (chunkRequestsThisTick >= MAX_CHUNK_LOAD_REQUESTS_PER_TICK) {
                    return;
                }
            }

            int checked = 0;
            for (LocalChunkBatch batch : plan.chunks()) {
                if (checked++ >= PRELOAD_LOOKAHEAD_BATCHES) {
                    break;
                }
                requestBatchChunks(batch);
                if (chunkRequestsThisTick >= MAX_CHUNK_LOAD_REQUESTS_PER_TICK) {
                    return;
                }
            }
        }

        private void clearFloatingDecorationAbove(int x, int y, int z) {
            for (int dy = 1; dy <= 3; dy++) {
                int targetY = y + dy;
                if (targetY < minHeight || targetY >= maxHeight) {
                    return;
                }
                org.bukkit.block.Block above = world.getBlockAt(x, targetY, z);
                Material material = above.getType();
                if (material.isAir() || material.isSolid()) {
                    return;
                }
                above.setType(Material.AIR, false);
            }
        }

        private final class ActiveChunkBatch {
            private final LocalChunkBatch batch;
            private final long[] requiredChunkKeys;
            private int clearColumnIndex;
            private int clearY = -1;
            private int clearMaxY = -1;
            private int index;
            private boolean loaded;
            private boolean failed;
            private boolean ticketsAdded;

            private ActiveChunkBatch(LocalChunkBatch batch) {
                this.batch = batch;
                this.requiredChunkKeys = requiredChunkKeys(batch);
            }

            private boolean ensureLoaded() {
                if (failed) {
                    return false;
                }
                if (loaded && allChunksLoaded()) {
                    return true;
                }
                if (allChunksLoaded()) {
                    loaded = true;
                    addTickets();
                    return true;
                }
                requestMissingChunks();
                return false;
            }

            private boolean allChunksLoaded() {
                for (long key : requiredChunkKeys) {
                    if (!world.isChunkLoaded(chunkXFromKey(key), chunkZFromKey(key))) {
                        return false;
                    }
                }
                return true;
            }

            private void requestMissingChunks() {
                for (long key : requiredChunkKeys) {
                    requestChunkLoad(key);
                }
            }

            private void addTickets() {
                if (ticketsAdded) {
                    return;
                }
                for (long key : requiredChunkKeys) {
                    retainTicket(key);
                }
                ticketsAdded = true;
            }

            private int nextBlock() {
                return batch.blocks()[index++];
            }

            private void clearNextBlock() {
                while (clearColumnIndex < batch.columnBounds().length) {
                    int bounds = batch.columnBounds()[clearColumnIndex];
                    if (bounds < 0) {
                        clearColumnIndex++;
                        clearY = -1;
                        continue;
                    }
                    int minY = Math.max(0, unpackColumnMinY(bounds) - CLEAR_EXTRA_BELOW_COLUMN);
                    int maxY = Math.min(PACKED_Y_MASK, unpackColumnMaxY(bounds) + CLEAR_EXTRA_ABOVE_COLUMN);
                    if (clearY < 0) {
                        int localX = clearColumnIndex & PACKED_LOCAL_MASK;
                        int localZ = (clearColumnIndex >>> 4) & PACKED_LOCAL_MASK;
                        int targetX = originX + schematic.offsetX() + (batch.chunkX() << 4) + localX;
                        int targetZ = originZ + schematic.offsetZ() + (batch.chunkZ() << 4) + localZ;
                        int highestY = world.getHighestBlockYAt(targetX, targetZ);
                        int highestRelativeY = highestY - originY - schematic.offsetY();
                        clearY = minY;
                        clearMaxY = Math.min(maxY, highestRelativeY);
                    }
                    if (clearY <= clearMaxY) {
                        int localX = clearColumnIndex & PACKED_LOCAL_MASK;
                        int localZ = (clearColumnIndex >>> 4) & PACKED_LOCAL_MASK;
                        int targetX = originX + schematic.offsetX() + (batch.chunkX() << 4) + localX;
                        int targetY = originY + schematic.offsetY() + clearY++;
                        int targetZ = originZ + schematic.offsetZ() + (batch.chunkZ() << 4) + localZ;
                        if (targetY < minHeight || targetY >= maxHeight) {
                            return;
                        }
                        org.bukkit.block.Block block = world.getBlockAt(targetX, targetY, targetZ);
                        if (!block.getType().isAir()) {
                            block.setType(Material.AIR, false);
                            clearFloatingDecorationAbove(targetX, targetY, targetZ);
                            clearedExistingBlocks++;
                        }
                        return;
                    }
                    clearColumnIndex++;
                    clearY = -1;
                    clearMaxY = -1;
                }
            }

            private boolean clearDone() {
                return clearColumnIndex >= batch.columnBounds().length;
            }

            private boolean done() {
                return failed || index >= batch.blocks().length;
            }

            private void clearFloatingDecorationAbove(int x, int y, int z) {
                for (int dy = 1; dy <= 3; dy++) {
                    int targetY = y + dy;
                    if (targetY < minHeight || targetY >= maxHeight) {
                        return;
                    }
                    org.bukkit.block.Block above = world.getBlockAt(x, targetY, z);
                    Material material = above.getType();
                    if (material.isAir() || material.isSolid()) {
                        return;
                    }
                    above.setType(Material.AIR, false);
                }
            }

            private void releaseTickets() {
                if (!ticketsAdded) {
                    return;
                }
                for (long key : requiredChunkKeys) {
                    releaseTicket(key);
                }
                ticketsAdded = false;
            }
        }

        private void requestBatchChunks(LocalChunkBatch batch) {
            int minTargetX = originX + schematic.offsetX() + (batch.chunkX() << 4);
            int maxTargetX = minTargetX + 15;
            int minTargetZ = originZ + schematic.offsetZ() + (batch.chunkZ() << 4);
            int maxTargetZ = minTargetZ + 15;
            int minChunkX = minTargetX >> 4;
            int maxChunkX = maxTargetX >> 4;
            int minChunkZ = minTargetZ >> 4;
            int maxChunkZ = maxTargetZ >> 4;
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    requestChunkLoad(chunkKey(chunkX, chunkZ));
                    if (chunkRequestsThisTick >= MAX_CHUNK_LOAD_REQUESTS_PER_TICK) {
                        return;
                    }
                }
            }
        }

        private void requestChunkLoad(long key) {
            if (chunkRequestsThisTick >= MAX_CHUNK_LOAD_REQUESTS_PER_TICK) {
                return;
            }
            int chunkX = chunkXFromKey(key);
            int chunkZ = chunkZFromKey(key);
            if (world.isChunkLoaded(chunkX, chunkZ)) {
                retainPreloadTicket(key);
                return;
            }
            Integer requestedAt = requestedChunkTicks.get(key);
            if (requestedAt != null && ticks - requestedAt < CHUNK_LOAD_RETRY_TICKS) {
                return;
            }
            requestedChunkTicks.put(key, ticks);
            chunkRequestsThisTick++;
            world.getChunkAtAsync(chunkX, chunkZ, true).whenComplete((chunk, throwable) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    requestedChunkTicks.remove(key);
                    if (finished) {
                        return;
                    }
                    if (throwable != null || chunk == null) {
                        plugin.getLogger().warning("Failed to async-load dungeon chunk "
                            + chunkX + "," + chunkZ + ".");
                        return;
                    }
                    retainPreloadTicket(key);
                }));
        }

        private void retainPreloadTicket(long key) {
            if (finished || preloadTickets.contains(key)) {
                return;
            }
            retainTicket(key);
            preloadTickets.add(key);
            preloadTicketOrder.add(key);
            while (preloadTickets.size() > MAX_PRELOADED_CHUNK_TICKETS && !preloadTicketOrder.isEmpty()) {
                releasePreloadTicket(preloadTicketOrder.remove());
            }
        }

        private void releasePreloadTicket(long key) {
            if (!preloadTickets.remove(key)) {
                return;
            }
            releaseTicket(key);
        }

        private long[] requiredChunkKeys(LocalChunkBatch batch) {
            int minTargetX = originX + schematic.offsetX() + (batch.chunkX() << 4);
            int maxTargetX = minTargetX + 15;
            int minTargetZ = originZ + schematic.offsetZ() + (batch.chunkZ() << 4);
            int maxTargetZ = minTargetZ + 15;
            int minChunkX = minTargetX >> 4;
            int maxChunkX = maxTargetX >> 4;
            int minChunkZ = minTargetZ >> 4;
            int maxChunkZ = maxTargetZ >> 4;
            long[] keys = new long[(maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1)];
            int index = 0;
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    keys[index++] = chunkKey(chunkX, chunkZ);
                }
            }
            return keys;
        }

        private void retainTicket(long key) {
            int count = ticketCounts.getOrDefault(key, 0);
            if (count == 0) {
                world.addPluginChunkTicket(chunkXFromKey(key), chunkZFromKey(key), plugin);
            }
            ticketCounts.put(key, count + 1);
        }

        private void releaseTicket(long key) {
            Integer count = ticketCounts.get(key);
            if (count == null) {
                return;
            }
            if (count <= 1) {
                ticketCounts.remove(key);
                world.removePluginChunkTicket(chunkXFromKey(key), chunkZFromKey(key), plugin);
                return;
            }
            ticketCounts.put(key, count - 1);
        }

        private int chunkXFromKey(long key) {
            return (int) (key >> 32);
        }

        private int chunkZFromKey(long key) {
            return (int) key;
        }

        private void finish() {
            finished = true;
            cancel();
            for (ActiveChunkBatch active : activeChunks) {
                active.releaseTickets();
            }
            while (!preloadTicketOrder.isEmpty()) {
                releasePreloadTicket(preloadTicketOrder.remove());
            }
            requestedChunkTicks.clear();
            activeChunks.clear();
            pasteRunning.set(false);
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                messageService.send(player, "&aDungeon schematic pasted. &7Placed &f" + placedBlocks
                    + " &7blocks and cleared &f" + clearedExistingBlocks
                    + " &7terrain block(s) across &f" + completedChunks + " &7chunk batch(es). Skipped &f"
                    + skippedBlocks + " &7unsupported block(s).");
            }
            if (onComplete != null) {
                try {
                    onComplete.run();
                } catch (Throwable throwable) {
                    plugin.getLogger().log(Level.SEVERE, "Dungeon completion callback failed", throwable);
                }
            }
        }
    }

    private static final class SchematicBuilder {
        private int width = -1;
        private int height = -1;
        private int length = -1;
        private int offsetX;
        private int offsetY;
        private int offsetZ;
        private byte[] blockData;
        private final Map<Integer, String> paletteById = new HashMap<>();

        private void setDimension(String path, int value) {
            switch (path) {
                case "Schematic.Width" -> width = value;
                case "Schematic.Height" -> height = value;
                case "Schematic.Length" -> length = value;
                default -> {
                }
            }
        }

        private RawSchematicData build() throws IOException {
            if (width <= 0 || height <= 0 || length <= 0) {
                throw new IOException("Schematic dimensions are missing or invalid.");
            }
            if (blockData == null || blockData.length == 0) {
                throw new IOException("Schematic block data is missing.");
            }
            if (paletteById.isEmpty()) {
                throw new IOException("Schematic palette is missing.");
            }
            return new RawSchematicData(width, height, length, offsetX, offsetY, offsetZ, Map.copyOf(paletteById), blockData);
        }
    }

    private static final class ChunkBucketBuilder {
        private final int chunkX;
        private final int chunkZ;
        private final IntArrayBuilder blocks = new IntArrayBuilder(4096);
        private final int[] columnBounds = new int[16 * 16];

        private ChunkBucketBuilder(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            for (int i = 0; i < columnBounds.length; i++) {
                columnBounds[i] = -1;
            }
        }

        private void add(int localX, int relativeY, int localZ, int paletteId) {
            blocks.add(packChunkBlock(localX, relativeY, localZ, paletteId));
            coverColumn(localX, relativeY, localZ, relativeY);
        }

        private void coverColumn(int localX, int minRelativeY, int localZ, int maxRelativeY) {
            int columnIndex = (localZ << 4) | localX;
            int current = columnBounds[columnIndex];
            if (current < 0) {
                columnBounds[columnIndex] = packColumnBounds(minRelativeY, maxRelativeY);
                return;
            }
            int minY = unpackColumnMinY(current);
            int maxY = unpackColumnMaxY(current);
            if (minRelativeY < minY) {
                minY = minRelativeY;
            }
            if (maxRelativeY > maxY) {
                maxY = maxRelativeY;
            }
            columnBounds[columnIndex] = packColumnBounds(minY, maxY);
        }

        private LocalChunkBatch toBatch() {
            int[] packedBounds = new int[columnBounds.length];
            for (int i = 0; i < packedBounds.length; i++) {
                packedBounds[i] = -1;
            }
            return new LocalChunkBatch(chunkX, chunkZ, blocks.toArray(), packedBounds, 0);
        }

        private int countClearBlocks(int[] bounds) {
            int total = 0;
            for (int bound : bounds) {
                if (bound < 0) {
                    continue;
                }
                int minY = Math.max(0, unpackColumnMinY(bound) - CLEAR_EXTRA_BELOW_COLUMN);
                int maxY = Math.min(PACKED_Y_MASK, unpackColumnMaxY(bound) + CLEAR_EXTRA_ABOVE_COLUMN);
                total += Math.max(0, maxY - minY + 1);
            }
            return total;
        }
    }

    private static final class IntArrayBuilder {
        private int[] values;
        private int size;

        private IntArrayBuilder(int initialCapacity) {
            values = new int[Math.max(16, initialCapacity)];
        }

        private void add(int value) {
            if (size >= values.length) {
                int[] grown = new int[values.length + (values.length >> 1) + 1];
                System.arraycopy(values, 0, grown, 0, values.length);
                values = grown;
            }
            values[size++] = value;
        }

        private int[] toArray() {
            int[] result = new int[size];
            System.arraycopy(values, 0, result, 0, size);
            return result;
        }
    }

    private record RawSchematicData(
        int width,
        int height,
        int length,
        int offsetX,
        int offsetY,
        int offsetZ,
        Map<Integer, String> paletteById,
        byte[] blockData
    ) {
    }

    private record IslandTemplate(
        String resourcePath,
        int offsetX,
        int offsetZ
    ) {
    }

    private record PreparedIsland(
        IslandTemplate template,
        PreparedSchematicData schematic
    ) {
    }

    private record PreparedSchematicData(
        int width,
        int height,
        int length,
        int offsetX,
        int offsetY,
        int offsetZ,
        Map<Integer, String> paletteById,
        ArrayList<LocalChunkBatch> localChunks,
        int totalBlocks,
        int totalClearBlocks,
        int interiorClearColumns,
        int skippedVillageBlocks,
        int minNonAirY,
        int maxNonAirY,
        int groundAnchorY
    ) {
    }

    private record ChunkPlan(
        Queue<LocalChunkBatch> chunks,
        int totalChunks,
        int totalBlocks,
        int totalClearBlocks
    ) {
    }

    private record LocalChunkBatch(
        int chunkX,
        int chunkZ,
        int[] blocks,
        int[] columnBounds,
        int clearBlocks
    ) {
    }
}
