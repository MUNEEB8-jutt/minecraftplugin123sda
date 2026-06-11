package com.legendaryweaponssmp.resourcepack;

import com.legendaryweaponssmp.config.ConfigManager;
import com.legendaryweaponssmp.core.MessageService;
import com.legendaryweaponssmp.hud.BetterHudIntegration;
import com.legendaryweaponssmp.weapons.WeaponType;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ResourcePackManager {
    private static final String[] SWING_MODEL_SUFFIXES = {
        "left_windup",
        "left_impact",
        "left_recover",
        "right_windup",
        "right_impact",
        "right_recover",
        "front_windup",
        "front_impact",
        "front_recover"
    };
    private static final Map<String, String> VFX_MODELS = new LinkedHashMap<>();

    static {
        VFX_MODELS.put("vfx/beam", "minecraft:custom/beam");
        VFX_MODELS.put("vfx/bat_sigil", "minecraft:custom/bat_sigil");
        VFX_MODELS.put("vfx/black_cubes", "minecraft:custom/black_cubes");
        VFX_MODELS.put("vfx/red_cubes", "minecraft:custom/red_cubes");
        VFX_MODELS.put("vfx/warrior_sigil", "minecraft:custom/warrior_sigil");
        VFX_MODELS.put("vfx/frost", "minecraft:custom/frostsideways");
        VFX_MODELS.put("vfx/ring", "minecraft:custom/r");
        VFX_MODELS.put("vfx/slash", "minecraft:custom/slashing");
        VFX_MODELS.put("vfx/skull", "minecraft:custom/vulcans_skull");
        VFX_MODELS.put("vfx/heart", "minecraft:custom/warden_heart");
        VFX_MODELS.put("vfx/bifrost", "civilization:item/cosmetics/bifrost");
        VFX_MODELS.put("vfx/blaze", "civilization:item/cosmetics/blaze_blast");
        VFX_MODELS.put("vfx/chains", "civilization:item/cosmetics/desecrated_chains_kill_effect");
        VFX_MODELS.put("vfx/coffin", "civilization:item/cosmetics/coffin_crusher_kill_effect");
        VFX_MODELS.put("vfx/flower", "civilization:item/cosmetics/flower_trail_victory_dance");
        VFX_MODELS.put("vfx/inferno", "civilization:item/cosmetics/inferno_blast_kill_effect");
        VFX_MODELS.put("vfx/lightning", "civilization:item/cosmetics/lightning_strike");
        VFX_MODELS.put("vfx/lightning_storm", "civilization:item/cosmetics/lightning_storm");
        VFX_MODELS.put("vfx/purple_stars", "civilization:item/cosmetics/purple_stars");
        VFX_MODELS.put("vfx/sakura", "civilization:item/cosmetics/sakura_whirlwind");
        VFX_MODELS.put("vfx/sonic", "civilization:item/cosmetics/sonic_boom");
        VFX_MODELS.put("vfx/sonic_smash", "civilization:item/cosmetics/sonic_smash");
        VFX_MODELS.put("vfx/storm_cloud", "civilization:item/cosmetics/storm_cloud");
        VFX_MODELS.put("vfx/summoning_circle", "minecraft:custom/quantum_summoning_circle");
        VFX_MODELS.put("ritual/forge_base", "legendary:item/ritual/forge_base");
        VFX_MODELS.put("ritual/forge_workbench", "legendary:item/ritual/forge_workbench");
        VFX_MODELS.put("ritual/forge_pickaxe", "legendary:item/ritual/forge_pickaxe");
        VFX_MODELS.put("ritual/forge_axe", "legendary:item/ritual/forge_axe");
        VFX_MODELS.put("offering/obsidian_flat", "legendary:item/offering/obsidian_flat");
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageService messageService;
    private final AtomicBoolean regenerationInProgress = new AtomicBoolean();
    private BetterHudIntegration hudIntegration;
    private File workDir;
    private File zipFile;
    private File hudZipFile;
    private File compatibleHudZipFile;
    private byte[] sha1;
    private byte[] hudSha1;
    private long hudShaLastModified;
    private long hudShaLength;
    private long compatibleHudSourceLastModified;
    private long compatibleHudSourceLength;
    private HttpServer server;
    private String packUrl;
    private String hudPackUrl;

    public ResourcePackManager(JavaPlugin plugin, ConfigManager configManager, MessageService messageService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageService = messageService;
    }

    public void attachHudIntegration(BetterHudIntegration hudIntegration) {
        this.hudIntegration = hudIntegration;
    }

    public void initialize() {
        regenerate();
        startHttp();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public void regenerateAndBroadcast() {
        if (!regenerationInProgress.compareAndSet(false, true)) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            regenerate();
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    startHttp();
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        sendPack(player);
                    }
                } finally {
                    regenerationInProgress.set(false);
                }
            });
        });
    }

    public void regenerate() {
        File root = new File(plugin.getDataFolder(), "resourcepack");
        workDir = new File(root, "pack");
        zipFile = new File(root, "LegendaryWeaponsSMP.zip");
        hudZipFile = new File(plugin.getDataFolder().getParentFile(), "BetterHud/build.zip");
        compatibleHudZipFile = new File(root, "LegendaryWeaponsSMP-Hud.zip");
        if (workDir.exists()) {
            deleteRecursively(workDir);
        }
        workDir.mkdirs();
        try {
            generatePack(workDir);
            zipDirectory(workDir, zipFile);
            sha1 = digest(zipFile);
        } catch (Exception e) {
            plugin.getLogger().severe("Resource pack generation failed: " + e.getMessage());
        }
    }

    public void sendPack(Player player) {
        if (configManager.enabledWeaponTypes().isEmpty()) {
            return;
        }
        if (regenerationInProgress.get()) {
            return;
        }
        if (hudIntegration != null && !hudIntegration.isResourcePackCurrent()) {
            hudIntegration.ensureCurrentResourcePack();
            return;
        }
        File selectedPack = preferredPack();
        boolean mergedHudPack = selectedPack != null && selectedPack.equals(compatibleHudZipFile);
        String selectedUrl = mergedHudPack ? hudPackUrl : packUrl;
        byte[] selectedSha = mergedHudPack ? hudDigest() : sha1;
        if (selectedUrl == null || selectedSha == null) {
            return;
        }
        String prompt = configManager.general().getString("resource-pack.prompt", "Legendary weapons assets are required");
        boolean force = configManager.general().getBoolean("resource-pack.force", true);
        player.setResourcePack(selectedUrl, selectedSha, Component.text(prompt), force);
    }

    public void broadcastCurrentPack() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendPack(player);
        }
    }

    public synchronized void refreshHudPackAndBroadcast() {
        regenerateAndBroadcast();
    }

    public String packUrl() {
        return packUrl;
    }

    public byte[] sha1() {
        return sha1;
    }

    private void startHttp() {
        if (zipFile == null || !zipFile.exists()) {
            return;
        }
        stop();
        String host = configManager.general().getString("resource-pack.host", "0.0.0.0");
        int port = configManager.general().getInt("resource-pack.port", 8127);
        String publicHost = configManager.general().getString("resource-pack.public-host", "");
        try {
            server = HttpServer.create(new InetSocketAddress(host, port), 0);
            server.createContext("/LegendaryWeaponsSMP.zip", new ZipHandler(zipFile));
            server.createContext("/LegendaryWeaponsSMP-Hud.zip", new PreferredZipHandler());
            server.setExecutor(null);
            server.start();
            if (publicHost == null || publicHost.isBlank()) {
                String fallback = Bukkit.getIp();
                if (fallback == null || fallback.isBlank()) {
                    fallback = "127.0.0.1";
                }
                publicHost = host.equals("0.0.0.0") ? fallback : host;
            }
            packUrl = "http://" + publicHost + ":" + port + "/LegendaryWeaponsSMP.zip";
            hudPackUrl = "http://" + publicHost + ":" + port + "/LegendaryWeaponsSMP-Hud.zip";
            plugin.getLogger().info("Resource pack hosted at " + packUrl);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to host resource pack: " + e.getMessage());
        }
    }

    private void generatePack(File root) throws IOException, URISyntaxException {
        writeText(new File(root, "pack.mcmeta"), """
            {
              "pack": {
                "pack_format": 50,
                "supported_formats": {
                  "min_inclusive": 34,
                  "max_inclusive": 999
                },
                "description": "LegendaryWeaponsSMP 15-Weapons and Abilities"
              }
            }
            """);
        generatePackIcon(new File(root, "pack.png"));
        copyBundledAssets(root);
        if (hudIntegration != null) {
            hudIntegration.writeResourcePackAssets(root);
        }

        File legendary = new File(root, "assets/legendary");
        File items = new File(legendary, "items");
        items.mkdirs();
        for (WeaponType type : configManager.enabledWeaponTypes()) {
            generateItemDefinition(new File(items, type.id() + ".json"), type.modelPath());
        }
        for (WeaponType type : configManager.enabledWeaponTypes()) {
            if (type.isRanged()) {
                continue;
            }
            for (String suffix : SWING_MODEL_SUFFIXES) {
                String modelId = type.id() + "_swing_" + suffix;
                generateItemDefinition(new File(items, modelId + ".json"), "legendary:item/" + modelId);
            }
        }
        generateItemDefinition(new File(items, "ritual_core.json"), "legendary:item/ritual/forge_base");
        for (Map.Entry<String, String> entry : VFX_MODELS.entrySet()) {
            generateItemDefinition(new File(items, entry.getKey() + ".json"), entry.getValue());
        }
        generateRitualBaseVariants(root, items);
        generateSounds(new File(legendary, "sounds.json"));
    }

    private void generateRitualBaseVariants(File root, File items) throws IOException {
        File baseModel = new File(root, "assets/legendary/models/item/ritual/forge_base.json");
        if (!baseModel.exists()) {
            plugin.getLogger().warning("Ritual base model missing; weapon-colored forge bases were not generated.");
            return;
        }

        String baseJson = Files.readString(baseModel.toPath(), StandardCharsets.UTF_8);
        File modelDir = new File(root, "assets/legendary/models/item/ritual");
        for (WeaponType type : configManager.enabledWeaponTypes()) {
            String modelId = "ritual/forge_base_" + type.id();
            String texturePath = "legendary:item/ritual/altar_" + type.id();
            String variantJson = baseJson.replace("minecraft:block/altar", texturePath);
            writeText(new File(modelDir, "forge_base_" + type.id() + ".json"), variantJson);
            generateItemDefinition(new File(items, modelId + ".json"), "legendary:item/" + modelId);
        }
    }

    private void generatePackIcon(File file) throws IOException {
        BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        tune(g);
        g.setPaint(new GradientPaint(0, 0, new Color(12, 16, 28), 128, 128, new Color(82, 24, 30)));
        g.fillRect(0, 0, 128, 128);
        g.setColor(new Color(250, 210, 92, 230));
        g.fillOval(12, 12, 104, 104);
        g.setColor(new Color(18, 18, 24));
        g.setStroke(new BasicStroke(7f));
        g.drawOval(22, 22, 84, 84);
        g.setColor(new Color(255, 255, 255, 210));
        g.fillRect(60, 34, 8, 60);
        g.fillRect(34, 60, 60, 8);
        g.dispose();
        ImageIO.write(image, "png", file);
    }

    private void generateItemDefinition(File file, String modelPath) throws IOException {
        writeText(file, """
            {
              "model": {
                "type": "minecraft:model",
                "model": "%s"
              }
            }
            """.formatted(modelPath));
    }

    private void generateSounds(File file) throws IOException {
        StringBuilder json = new StringBuilder("{\n");
        int i = 0;
        for (WeaponType type : configManager.enabledWeaponTypes()) {
            json.append("  \"legendary.")
                .append(type.id())
                .append("\": { \"sounds\": [ \"")
                .append(soundFor(type))
                .append("\" ] }");
            if (++i < configManager.enabledWeaponTypes().size()) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("}\n");
        writeText(file, json.toString());
    }

    private String soundFor(WeaponType type) {
        return switch (type.barColor()) {
            case RED -> "minecraft:entity.blaze.shoot";
            case BLUE -> "minecraft:block.amethyst_block.chime";
            case GREEN -> "minecraft:block.azalea_leaves.step";
            case PURPLE -> "minecraft:entity.warden.sonic_boom";
            default -> "minecraft:block.beacon.power_select";
        };
    }

    private int copyBundledAssets(File root) throws IOException, URISyntaxException {
        int copied = copyBundledAssetsFromManifest(root);
        if (copied > 0) {
            plugin.getLogger().info("Copied " + copied + " bundled resource-pack assets from manifest.");
            return copied;
        }

        copied = copyBundledAssetsFromCodeSource(root);
        if (copied > 0) {
            plugin.getLogger().info("Copied " + copied + " bundled resource-pack assets from code source.");
        } else {
            plugin.getLogger().warning("No bundled resource-pack assets were copied. Weapon models may show missing textures.");
        }
        return copied;
    }

    private int copyBundledAssetsFromManifest(File root) throws IOException {
        try (InputStream manifest = plugin.getResource("rp/assets_manifest.txt")) {
            if (manifest == null) {
                return 0;
            }
            int copied = 0;
            String text = new String(manifest.readAllBytes(), StandardCharsets.UTF_8).replace("\uFEFF", "");
            for (String rawLine : text.split("\\R")) {
                String name = rawLine.trim();
                if (name.isEmpty() || !name.startsWith("rp/assets/") || name.contains("..")) {
                    continue;
                }
                try (InputStream in = plugin.getResource(name)) {
                    if (in == null) {
                        plugin.getLogger().warning("Missing bundled resource-pack asset listed in manifest: " + name);
                        continue;
                    }
                    Path dest = root.toPath().resolve(name.substring("rp/".length()));
                    Files.createDirectories(dest.getParent());
                    Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                    copied++;
                }
            }
            return copied;
        }
    }

    private int copyBundledAssetsFromCodeSource(File root) throws IOException, URISyntaxException {
        Path codeSource = Path.of(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        if (Files.isDirectory(codeSource)) {
            Path source = codeSource.resolve("rp").resolve("assets");
            if (!Files.exists(source)) {
                return 0;
            }
            Path target = root.toPath().resolve("assets");
            int copied = 0;
            try (var stream = Files.walk(source)) {
                for (Path path : stream.sorted().toList()) {
                    Path rel = source.relativize(path);
                    Path dest = target.resolve(rel);
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(dest);
                    } else {
                        Files.createDirectories(dest.getParent());
                        Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
                        copied++;
                    }
                }
            }
            return copied;
        }

        int copied = 0;
        try (JarFile jar = new JarFile(codeSource.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith("rp/assets/") || entry.isDirectory()) {
                    continue;
                }
                Path dest = root.toPath().resolve(name.substring("rp/".length()));
                Files.createDirectories(dest.getParent());
                try (InputStream in = jar.getInputStream(entry)) {
                    Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                }
                copied++;
            }
        }
        return copied;
    }

    private void tune(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    private void writeText(File file, String text) throws IOException {
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
    }

    private void zipDirectory(File sourceDir, File zipFile) throws IOException {
        File temp = new File(zipFile.getParentFile(), zipFile.getName() + ".tmp");
        Files.deleteIfExists(temp.toPath());
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(temp)))) {
            zos.setLevel(Deflater.BEST_COMPRESSION);
            try (var stream = Files.walk(sourceDir.toPath())) {
                for (File file : stream
                    .map(Path::toFile)
                    .sorted(Comparator.comparing(File::getAbsolutePath))
                    .toList()) {
                    if (file.isDirectory()) {
                        continue;
                    }
                    String name = sourceDir.toPath().relativize(file.toPath()).toString().replace("\\", "/");
                    zos.putNextEntry(new ZipEntry(name));
                    Files.copy(file.toPath(), zos);
                    zos.closeEntry();
                }
            }
        }
        try {
            Files.move(
                temp.toPath(),
                zipFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            );
        } catch (IOException atomicMoveFailure) {
            Files.move(temp.toPath(), zipFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private byte[] digest(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = bis.read(buffer)) > 0) {
                md.update(buffer, 0, read);
            }
        }
        return md.digest();
    }

    private synchronized File preferredPack() {
        return zipFile;
    }

    private byte[] hudDigest() {
        if (compatibleHudZipFile == null || !compatibleHudZipFile.exists()) {
            return null;
        }
        long modified = compatibleHudZipFile.lastModified();
        long length = compatibleHudZipFile.length();
        if (hudSha1 != null && hudShaLastModified == modified && hudShaLength == length) {
            return hudSha1;
        }
        try {
            hudSha1 = digest(compatibleHudZipFile);
            hudShaLastModified = modified;
            hudShaLength = length;
            return hudSha1;
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to hash merged BetterHud pack: " + ex.getMessage());
            return null;
        }
    }

    private File compatibleHudPack() {
        long modified = hudZipFile.lastModified();
        long length = hudZipFile.length();
        if (compatibleHudZipFile.exists()
            && compatibleHudZipFile.length() > 0L
            && compatibleHudSourceLastModified == modified
            && compatibleHudSourceLength == length) {
            return compatibleHudZipFile;
        }

        File temp = new File(compatibleHudZipFile.getParentFile(), compatibleHudZipFile.getName() + ".tmp");
        int stripped = 0;
        int modernized = 0;
        try {
            Files.createDirectories(compatibleHudZipFile.getParentFile().toPath());
            Files.deleteIfExists(temp.toPath());
            try (ZipFile input = new ZipFile(hudZipFile);
                 ZipOutputStream output = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(temp)))) {
                var entries = input.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry source = entries.nextElement();
                    if (isIncompatibleBetterHudShader(source.getName())) {
                        stripped++;
                        continue;
                    }
                    ZipEntry target = new ZipEntry(source.getName());
                    target.setTime(source.getTime());
                    output.putNextEntry(target);
                    if (!source.isDirectory()) {
                        try (InputStream stream = input.getInputStream(source)) {
                            if (isModernizedBetterHudShader(source.getName())) {
                                String shader = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                                output.write(modernizeBetterHudShader(shader).getBytes(StandardCharsets.UTF_8));
                                modernized++;
                            } else {
                                stream.transferTo(output);
                            }
                        }
                    }
                    output.closeEntry();
                }
            }
            Files.move(temp.toPath(), compatibleHudZipFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            compatibleHudSourceLastModified = modified;
            compatibleHudSourceLength = length;
            hudSha1 = null;
            plugin.getLogger().info("Prepared Lunar-safe BetterHud resource pack; stripped "
                + stripped + " incompatible lightmap includes and modernized "
                + modernized + " HUD shader files.");
            return compatibleHudZipFile;
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to prepare Lunar-safe BetterHud pack: " + ex.getMessage());
            try {
                Files.deleteIfExists(temp.toPath());
            } catch (IOException ignored) {
                // The standalone pack remains available as a safe fallback.
            }
            return null;
        }
    }

    private boolean isIncompatibleBetterHudShader(String name) {
        return name.startsWith("betterhud_")
            && name.endsWith("/assets/minecraft/shaders/include/sample_lightmap.glsl");
    }

    private boolean isModernizedBetterHudShader(String name) {
        return name.startsWith("betterhud_1_21_6/assets/minecraft/shaders/core/rendertype_text.")
            && (name.endsWith(".vsh") || name.endsWith(".fsh"));
    }

    private String modernizeBetterHudShader(String shader) {
        return shader
            .replace("#version 150", "#version 330")
            .replace("#define SHADER_VERSION 2", "#define SHADER_VERSION 3")
            .replace("#moj_import <fog.glsl>", "#moj_import <minecraft:fog.glsl>")
            .replace("#moj_import <sample_lightmap.glsl>", "")
            .replace("#moj_import <dynamictransforms.glsl>", "#moj_import <minecraft:dynamictransforms.glsl>")
            .replace("#moj_import <projection.glsl>", "#moj_import <minecraft:projection.glsl>")
            .replace("#moj_import <globals.glsl>", "#moj_import <minecraft:globals.glsl>")
            .replace("sample_lightmap(Sampler2, UV2)", "texelFetch(Sampler2, UV2 / 16, 0)");
    }

    private void deleteRecursively(File root) {
        if (root.isDirectory()) {
            File[] children = root.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        root.delete();
    }

    private static class ZipHandler implements HttpHandler {
        private final File file;

        private ZipHandler(File file) {
            this.file = file;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!file.exists()) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            exchange.getResponseHeaders().add("Content-Type", "application/zip");
            exchange.sendResponseHeaders(200, file.length());
            try (OutputStream os = exchange.getResponseBody()) {
                Files.copy(file.toPath(), os);
            } finally {
                exchange.close();
            }
        }
    }

    private class PreferredZipHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            File file = preferredPack();
            if (file == null || !file.exists()) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            exchange.getResponseHeaders().add("Content-Type", "application/zip");
            exchange.sendResponseHeaders(200, file.length());
            try (OutputStream os = exchange.getResponseBody()) {
                Files.copy(file.toPath(), os);
            } finally {
                exchange.close();
            }
        }
    }
}
