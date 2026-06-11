package com.legendaryweaponssmp.abilities;

import com.legendaryweaponssmp.animations.AnimationService;
import com.legendaryweaponssmp.config.ConfigManager;
import com.legendaryweaponssmp.core.MessageService;
import com.legendaryweaponssmp.particles.ParticleService;
import com.legendaryweaponssmp.weapons.WeaponItemFactory;
import com.legendaryweaponssmp.weapons.WeaponType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class AbilityListener implements Listener {
    private static final double DEFAULT_NORMAL_DAMAGE = 5.0;
    private static final double DEFAULT_SIGNATURE_DAMAGE = 6.5;
    private static final double DEFAULT_ULTIMATE_DAMAGE = 8.0;
    private static final double DEFAULT_PROJECTILE_DAMAGE = 5.5;
    private static final org.bukkit.Color QUANTUM_ULTIMATE_RED = org.bukkit.Color.fromRGB(115, 0, 18);
    private static final Particle.DustOptions BAT_OUTLINE_DUST = new Particle.DustOptions(org.bukkit.Color.fromRGB(246, 226, 38), 1.25f);
    private static final Particle.DustOptions BAT_SHADOW_DUST = new Particle.DustOptions(org.bukkit.Color.fromRGB(5, 5, 8), 1.45f);
    private static final Particle.DustOptions WARRIOR_DUST = new Particle.DustOptions(org.bukkit.Color.fromRGB(100, 0, 16), 1.35f);
    private static final Particle.DustOptions WARRIOR_HIGHLIGHT_DUST = new Particle.DustOptions(org.bukkit.Color.fromRGB(235, 218, 210), 0.75f);
    private static final Title.Times QUANTUM_HUD_TIMES = Title.Times.times(Duration.ZERO, Duration.ofMillis(150), Duration.ZERO);
    private static final QuantumSwingStyle[] DEFAULT_SWING_COMBO = {
        QuantumSwingStyle.LEFT,
        QuantumSwingStyle.RIGHT,
        QuantumSwingStyle.FRONT,
        QuantumSwingStyle.RIGHT,
        QuantumSwingStyle.LEFT,
        QuantumSwingStyle.RIGHT,
        QuantumSwingStyle.FRONT
    };
    private static final int ABILITY_HUD_MAX_TICKS = 30;
    private static final int ABILITY_HUD_GLYPH_BASE = 0xEC00;
    private static final Key ABILITY_HUD_FONT = Key.key("legendary:ability_icons");
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageService messageService;
    private final WeaponItemFactory itemFactory;
    private final CooldownManager cooldownManager;
    private final ParticleService particleService;
    private final Map<UUID, EnumMap<WeaponType, Integer>> passiveCharges = new HashMap<>();
    private final Map<UUID, QuantumArsenalState> quantumArsenals = new HashMap<>();
    private final Map<UUID, QuantumAscensionState> quantumAscensions = new HashMap<>();
    private final Map<UUID, Integer> abilityHudTokens = new HashMap<>();
    private final Map<UUID, Integer> quantumSwingSteps = new HashMap<>();
    private final Map<UUID, BukkitTask> quantumSwingResetTasks = new HashMap<>();
    private final Map<UUID, Long> quantumSwingStartTimes = new HashMap<>();
    private final Map<UUID, QuantumSwingStyle> quantumSwingLastStyles = new HashMap<>();
    private final Map<UUID, WeaponType> projectileWeapons = new HashMap<>();
    private final Map<UUID, Double> forcedAbilityDamage = new HashMap<>();
    private final Set<UUID> manualProjectiles = new HashSet<>();
    private final Set<Display> runtimeDisplays = new HashSet<>();

    public AbilityListener(JavaPlugin plugin,
                           ConfigManager configManager,
                           MessageService messageService,
                           WeaponItemFactory itemFactory,
                           CooldownManager cooldownManager,
                           ParticleService particleService,
                           AnimationService animationService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageService = messageService;
        this.itemFactory = itemFactory;
        this.cooldownManager = cooldownManager;
        this.particleService = particleService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        Player player = event.getPlayer();
        WeaponType type = resolveEnabledWeapon(player.getInventory().getItemInMainHand());
        if (type == null) {
            return;
        }

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            if (!type.isRanged() && !player.isSneaking()) {
                animateWeaponNormalSwing(player, type);
            }
            return;
        }
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (usesQuantumAbilities(type) && !player.isSneaking()) {
            QuantumArsenalState state = quantumArsenals.get(player.getUniqueId());
            if (state != null && !state.launched) {
                event.setCancelled(true);
                launchQuantumArsenal(player, type, state);
            }
            return;
        }
        if (!player.isSneaking()) {
            return;
        }
        event.setCancelled(true);
        castUltimate(player, type);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player player = event.getPlayer();
        WeaponType type = resolveEnabledWeapon(player.getInventory().getItemInMainHand());
        if (type == null) {
            return;
        }
        if (!player.isSneaking()) {
            if (!type.isRanged()) {
                event.setCancelled(true);
                animateWeaponNormalSwing(player, type);
            }
            return;
        }
        castSignature(player, type);
    }

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || !(event.getProjectile() instanceof Projectile projectile)) {
            return;
        }
        WeaponType type = resolveEnabledWeapon(event.getBow());
        if (type == null) {
            return;
        }
        projectileWeapons.put(projectile.getUniqueId(), type);
        if (type == WeaponType.TEMPEST_SONICBOW && charges(player, type) >= 5 && !manualProjectiles.contains(projectile.getUniqueId())) {
            setCharges(player, type, 0);
            messageService.action(player, "&bOVERDRIVE: Triple Hunter Shot released");
            Bukkit.getScheduler().runTask(plugin, () -> fireExtraHunterBolts(player, type));
        }
        if (type == WeaponType.BLOOMSHOT_BLASTER) {
            spawnTrail(projectile.getLocation(), "vfx/flower", 0.9f, 24);
        } else if (type == WeaponType.HORNHOOK_HARPOON) {
            spawnTrail(projectile.getLocation(), "vfx/beam", 0.85f, 24);
        } else if (type == WeaponType.TEMPEST_SONICBOW) {
            spawnTrail(projectile.getLocation(), "vfx/sonic", 0.85f, 24);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = resolvePlayerDamager(event.getDamager());
        if (attacker == null) {
            return;
        }
        WeaponType type = null;
        if (event.getDamager() instanceof Projectile projectile) {
            type = projectileWeapons.get(projectile.getUniqueId());
        }
        if (type == null) {
            type = resolveEnabledWeapon(attacker.getInventory().getItemInMainHand());
        }
        if (type == null) {
            return;
        }
        Double abilityDamage = forcedAbilityDamage.remove(event.getEntity().getUniqueId());
        if (abilityDamage != null) {
            event.setDamage(abilityDamage);
            addCharge(attacker, type);
            applyPassiveOnHit(attacker, event.getEntity(), type);
            return;
        }
        event.setDamage(damage(type, "normal-damage", type.isRanged() ? DEFAULT_PROJECTILE_DAMAGE : DEFAULT_NORMAL_DAMAGE));
        addCharge(attacker, type);
        applyPassiveOnHit(attacker, event.getEntity(), type);
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        WeaponType type = projectileWeapons.remove(event.getEntity().getUniqueId());
        manualProjectiles.remove(event.getEntity().getUniqueId());
        if (type == null) {
            return;
        }
        Location hit = event.getEntity().getLocation();
        if (type == WeaponType.BLOOMSHOT_BLASTER) {
            delayedAreaPulse(null, type, hit, 2.8, 1, 1, "vfx/flower", Material.PINK_STAINED_GLASS, 2.5, false);
        } else if (type == WeaponType.TEMPEST_SONICBOW) {
            spawnBurst(hit, "vfx/sonic", 6, 1.5, 26);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        passiveCharges.remove(event.getPlayer().getUniqueId());
        QuantumArsenalState state = quantumArsenals.remove(event.getPlayer().getUniqueId());
        if (state != null) {
            cleanupQuantumArsenal(state, true, true);
        }
        QuantumAscensionState ascension = quantumAscensions.remove(event.getPlayer().getUniqueId());
        if (ascension != null) {
            cleanupQuantumAscension(ascension);
        }
        abilityHudTokens.remove(event.getPlayer().getUniqueId());
        BukkitTask swingResetTask = quantumSwingResetTasks.remove(event.getPlayer().getUniqueId());
        if (swingResetTask != null) {
            swingResetTask.cancel();
        }
        quantumSwingStartTimes.remove(event.getPlayer().getUniqueId());
        quantumSwingLastStyles.remove(event.getPlayer().getUniqueId());
        quantumSwingSteps.remove(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        for (QuantumArsenalState state : new ArrayList<>(quantumArsenals.values())) {
            cleanupQuantumArsenal(state, true, true);
        }
        quantumArsenals.clear();
        for (QuantumAscensionState ascension : new ArrayList<>(quantumAscensions.values())) {
            cleanupQuantumAscension(ascension);
        }
        quantumAscensions.clear();
        for (BukkitTask task : quantumSwingResetTasks.values()) {
            task.cancel();
        }
        quantumSwingResetTasks.clear();
        passiveCharges.clear();
        abilityHudTokens.clear();
        quantumSwingStartTimes.clear();
        quantumSwingLastStyles.clear();
        quantumSwingSteps.clear();
        projectileWeapons.clear();
        forcedAbilityDamage.clear();
        manualProjectiles.clear();
        for (Display display : new ArrayList<>(runtimeDisplays)) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        runtimeDisplays.clear();
    }

    private void castSignature(Player player, WeaponType type) {
        if (!beginCooldown(player, type.leftCooldownKey(), cooldown(type.leftCooldownKey(), 18), type.signatureAbility())) {
            return;
        }
        messageService.action(player, "&e" + type.signatureAbility());
        if (usesQuantumAbilities(type)) {
            constructBarrage(player, type);
            return;
        }
        spawnAbilityHud(player, type, false);
        switch (type) {
            case QUANTUM_CHRONOBLADE -> dashCleave(player, type, "vfx/blaze", 12, 6.2, true);
            case GOLDFANG_DAGGER -> dashCleave(player, type, "vfx/slash", 9, 5.8, false);
            case BLOODCHAIN_RIPPER -> hookLine(player, type, "vfx/chains", 18, true);
            case FROSTNOVA_CHAKRAM -> fallingMonoliths(player, type);
            case PETALSTORM_FANBLADE -> dashCleave(player, type, "vfx/sakura", 13, 4.8, false);
            case STORMBREAKER_RELIC -> leapSlam(player, type);
            case SANGUINE_PIKE -> joustCharge(player, type);
            case VOIDGLASS_LICH_STAFF -> prisonNearest(player, type, "vfx/coffin", Material.GREEN_STAINED_GLASS, 22, 2.0);
            case BIFROST_WAND -> prisonNearest(player, type, "vfx/bifrost", Material.CYAN_STAINED_GLASS, 24, 2.4);
            case NECROMANCER_REAPER -> soulSever(player, type);
            case TIMBERLORD_AXE -> beastwoodRam(player, type);
            case BLOOMSHOT_BLASTER -> venusVolley(player, type);
            case HORNHOOK_HARPOON -> hookLine(player, type, "vfx/beam", 22, false);
            case TEMPEST_SONICBOW -> tripleHunterShot(player, type);
        }
    }

    private void castUltimate(Player player, WeaponType type) {
        if (!beginCooldown(player, type.rightCooldownKey(), cooldown(type.rightCooldownKey(), 60), type.ultimateAbility())) {
            return;
        }
        messageService.action(player, "&6" + type.ultimateAbility());
        if (usesQuantumAbilities(type)) {
            singularityAscension(player, type);
            return;
        }
        spawnAbilityHud(player, type, true);
        switch (type) {
            case QUANTUM_CHRONOBLADE -> raidLanes(player, type, "vfx/blaze", Material.RED_STAINED_GLASS, 3, 4.0);
            case GOLDFANG_DAGGER -> heistZone(player, type);
            case BLOODCHAIN_RIPPER -> conveyorLane(player, type);
            case FROSTNOVA_CHAKRAM -> mirrorPalace(player, type);
            case PETALSTORM_FANBLADE -> heavenbloomFestival(player, type);
            case STORMBREAKER_RELIC -> skyforgeJudgement(player, type);
            case SANGUINE_PIKE -> impalementColosseum(player, type);
            case VOIDGLASS_LICH_STAFF -> lichProcession(player, type);
            case BIFROST_WAND -> celestialTribunal(player, type);
            case NECROMANCER_REAPER -> deathCarousel(player, type);
            case TIMBERLORD_AXE -> walkingFortress(player, type);
            case BLOOMSHOT_BLASTER -> gardenApocalypse(player, type);
            case HORNHOOK_HARPOON -> krakenMooring(player, type);
            case TEMPEST_SONICBOW -> crimsonRailshot(player, type);
        }
    }

    private void constructBarrage(Player player, WeaponType type) {
        int hudTicks = spawnQuantumCasterHud(player, type, false, QuantumSigil.BAT);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            playCastSound(player, type, false);
            armQuantumArsenal(player, type);
        }, hudTicks);
    }

    private void armQuantumArsenal(Player player, WeaponType type) {
        UUID playerId = player.getUniqueId();
        QuantumArsenalState old = quantumArsenals.remove(playerId);
        if (old != null) {
            cleanupQuantumArsenal(old, true, true);
        }

        int count = 6;
        int lifetime = weaponInt(type, "signature-arm-window-ticks", 600);
        double radius = weaponDouble(type, "signature-orbit-radius", 4.5);
        Location origin = player.getLocation();
        List<QuantumChunk> chunks = new ArrayList<>();
        List<RestoredBlock> restoredBlocks = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0 * i) / count;
            Location sample = origin.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            Block source = quantumSourceBlock(sample);
            BlockData blockData = quantumVisualBlockData(source, Material.GRASS_BLOCK.createBlockData());
            if (canTemporarilyRemove(source)) {
                restoredBlocks.add(new RestoredBlock(source, source.getBlockData()));
                source.setType(Material.AIR, false);
            }
            float scale = i == count - 1 ? 1.28f : 1.08f;
            Location start = source.getLocation().add(0.5, 0.1, 0.5);
            BlockDisplay display = spawnCubeBlock(start, Material.GRASS_BLOCK, scale, lifetime + 80);
            display.setBlock(blockData);
            chunks.add(new QuantumChunk(display, blockData, scale, angle));
        }

        QuantumArsenalState state = new QuantumArsenalState(chunks, restoredBlocks);
        quantumArsenals.put(playerId, state);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GRASS_BREAK, 1.2f, 0.62f);
        messageService.action(player, "&5Gravity Arsenal armed &7- right click to launch");
        state.orbitTask = startQuantumOrbit(player, type, state, radius, lifetime);
    }

    private BukkitTask startQuantumOrbit(Player player, WeaponType type, QuantumArsenalState state, double radius, int lifetime) {
        return new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || state.launched) {
                    cancel();
                    return;
                }
                if (ticks >= lifetime) {
                    expireQuantumArsenal(player.getUniqueId(), state);
                    cancel();
                    return;
                }

                Location center = player.getLocation();
                double spin = ticks * 0.1;
                for (int i = 0; i < state.chunks.size(); i++) {
                    QuantumChunk chunk = state.chunks.get(i);
                    if (!chunk.display.isValid()) {
                        continue;
                    }
                    double angle = spin + chunk.angleOffset;
                    double orbitRadius = radius + Math.sin((ticks + i * 13.0) * 0.08) * 0.28;
                    double y = 1.15 + Math.sin((ticks + i * 11.0) * 0.12) * 0.32;
                    Location loc = center.clone().add(Math.cos(angle) * orbitRadius, y, Math.sin(angle) * orbitRadius);
                    chunk.display.teleport(loc);
                    setCubeTransform(chunk.display, chunk.scale, (float) (ticks * 0.12 + i * 0.75));
                    if (ticks % 4 == 0) {
                        player.getWorld().spawnParticle(Particle.BLOCK, loc, 2, 0.14, 0.14, 0.14, 0.0, chunk.blockData);
                        player.getWorld().spawnParticle(Particle.DUST, loc, 1, 0.08, 0.08, 0.08, 0.0,
                            new Particle.DustOptions(type.color(), 1.0f));
                    }
                }
                if (ticks % 18 == 0) {
                    player.getWorld().spawnParticle(Particle.ENCHANT, center.clone().add(0, 1.1, 0), 8, radius * 0.28, 0.45, radius * 0.28, 0.0);
                    player.getWorld().playSound(center, Sound.BLOCK_ROOTED_DIRT_PLACE, 0.35f, 0.75f);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void launchQuantumArsenal(Player player, WeaponType type, QuantumArsenalState state) {
        if (quantumArsenals.remove(player.getUniqueId(), state)) {
            state.launched = true;
            if (state.orbitTask != null) {
                state.orbitTask.cancel();
            }
            double range = Math.max(72.0, weaponDouble(type, "signature-range", 72.0));
            QuantumAim aim = quantumAim(player, range);
            double shardDamage = damage(type, "signature-damage", 4.6);
            double finisherDamage = weaponDouble(type, "signature-finisher-damage", 6.2);
            Map<UUID, Integer> hitCounts = new HashMap<>();
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SHULKER_SHOOT, 1.0f, 0.58f);
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.5f);
            player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, player.getLocation().add(0, 1.0, 0), 38, 2.2, 0.6, 2.2, 0.04);
            player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1.0, 0), 18, 1.8, 0.45, 1.8, 0.02);
            Bukkit.getScheduler().runTaskLater(plugin, () -> restoreQuantumBlocks(state), 12L);
            int[] cinematicOrder = {0, 3, 1, 4, 2, 5};
            for (int order = 0; order < cinematicOrder.length && order < state.chunks.size(); order++) {
                int index = cinematicOrder[order];
                QuantumChunk chunk = state.chunks.get(index);
                int launchOrder = order;
                Bukkit.getScheduler().runTaskLater(plugin, () ->
                    launchQuantumChunk(player, type, chunk, aim, index,
                        index == state.chunks.size() - 1 ? finisherDamage : shardDamage, hitCounts), launchOrder * 3L);
            }
        }
    }

    private void launchQuantumChunk(Player player,
                                    WeaponType type,
                                    QuantumChunk chunk,
                                    QuantumAim aim,
                                    int index,
                                    double dmg,
                                    Map<UUID, Integer> hitCounts) {
        if (!chunk.display.isValid()) {
            return;
        }
        Location start = chunk.display.getLocation().clone();
        Location target = aim.location().clone();
        double distance = Math.max(1.0, start.distance(target));
        int flightTicks = Math.max(18, Math.min(54, (int) Math.ceil(distance / (index == 5 ? 1.18 : 1.0))));
        Vector targetDirection = target.toVector().subtract(start.toVector());
        if (targetDirection.lengthSquared() < 0.0001) {
            targetDirection = safeLookDirection(player);
        } else {
            targetDirection.normalize();
        }
        Vector baseDirection = targetDirection.clone();
        Vector flatForward = targetDirection.clone().setY(0);
        if (flatForward.lengthSquared() < 0.0001) {
            flatForward = safeFlatDirection(player);
        } else {
            flatForward.normalize();
        }
        Vector right = rightVector(flatForward);
        double side = (index % 2 == 0 ? -1.0 : 1.0) * (1.05 + index * 0.08);
        Location control = start.clone()
            .add(target.toVector().subtract(start.toVector()).multiply(0.46))
            .add(right.multiply(side))
            .add(0, 0.75 + (index % 3) * 0.16, 0);
        new BukkitRunnable() {
            int ticks = 0;
            Location loc = start.clone();

            @Override
            public void run() {
                if (!player.isOnline() || !chunk.display.isValid()) {
                    cancel();
                    return;
                }
                Location previous = loc.clone();
                double t = Math.min(1.0, (double) ticks / flightTicks);
                double eased = t * t * (3.0 - 2.0 * t);
                loc = bezier(start, control, target, eased);
                Vector moveDirection = loc.toVector().subtract(previous.toVector());
                if (moveDirection.lengthSquared() > 0.0001) {
                    moveDirection.normalize();
                } else {
                    moveDirection = baseDirection.clone();
                }
                chunk.display.teleport(loc);
                setCubeTransform(chunk.display, chunk.scale, (float) (ticks * 0.34 + index * 0.6));
                player.getWorld().spawnParticle(Particle.BLOCK, loc, 2, 0.12, 0.12, 0.12, 0.0, chunk.blockData);
                player.getWorld().spawnParticle(Particle.DUST, loc, 1, 0.08, 0.08, 0.08, 0.0,
                    new Particle.DustOptions(type.color(), 1.1f));
                if (ticks % 2 == 0) {
                    player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, loc, 2, 0.12, 0.1, 0.12, 0.02);
                    player.getWorld().spawnParticle(Particle.ENCHANT, loc, 2, 0.12, 0.1, 0.12, 0.0);
                }

                for (LivingEntity target : nearbyEnemies(player, loc, index == 5 ? 1.75 : 1.25)) {
                    int hits = hitCounts.merge(target.getUniqueId(), 1, Integer::sum);
                    dealAbilityDamage(player, target, dmg);
                    Vector knock = moveDirection.clone().multiply(index == 5 ? 1.15 : 0.78);
                    knock.setY(hits >= 3 || index == 5 ? 0.85 : 0.28);
                    target.setVelocity(knock);
                    spawnDirtImpact(loc, chunk.blockData);
                    if (hits >= 3 || index == 5) {
                        spawnModel(target.getLocation().add(0, 1.0, 0), "vfx/black_cubes", 1.0f, 28);
                        target.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 16, 0, true, true, true));
                    }
                    chunk.display.remove();
                    cancel();
                    return;
                }

                if (ticks >= flightTicks) {
                    if (aim.hitBlock() != null && !aim.hitBlock().getType().isAir()) {
                        spawnQuantumSurfaceBlast(player, type, aim.location(), aim.hitBlock(), aim.hitFace(), chunk.blockData, moveDirection, dmg);
                        chunk.display.remove();
                        cancel();
                        return;
                    }
                    spawnQuantumDirectionalBlast(player, type, loc, moveDirection, chunk.blockData, dmg);
                    chunk.display.remove();
                    cancel();
                    return;
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void spawnDirtImpact(Location center, BlockData blockData) {
        center.getWorld().playSound(center, Sound.BLOCK_ROOTED_DIRT_BREAK, 0.9f, 0.68f);
        for (int i = 0; i < 5; i++) {
            double angle = Math.PI * 2.0 * i / 5.0;
            Location loc = center.clone().add(Math.cos(angle) * 0.45, 0.05 + i * 0.035, Math.sin(angle) * 0.45);
            BlockDisplay chip = spawnCubeBlock(loc, Material.GRASS_BLOCK, 0.24f + i * 0.025f, 18);
            chip.setBlock(blockData);
            setCubeTransform(chip, 0.24f + i * 0.025f, i * 0.7f);
        }
    }

    private void beginQuantumGroundDive(WeaponType type, QuantumChunk chunk, Location start, BlockData fallbackData) {
        Location loc = start.clone();
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!chunk.display.isValid()) {
                    cancel();
                    return;
                }
                loc.add(0, -0.85, 0);
                chunk.display.teleport(loc);
                setCubeTransform(chunk.display, chunk.scale, (float) (ticks * 0.38));
                loc.getWorld().spawnParticle(Particle.BLOCK, loc, 3, 0.16, 0.12, 0.16, 0.0, fallbackData);
                loc.getWorld().spawnParticle(Particle.REVERSE_PORTAL, loc, 2, 0.12, 0.1, 0.12, 0.02);
                if (loc.getBlock().getType().isSolid() || ticks > 48 || loc.getY() <= loc.getWorld().getMinHeight() + 1) {
                    Location impact = quantumImpactCenter(loc);
                    spawnQuantumGroundBlast(type, impact, fallbackData);
                    chunk.display.remove();
                    cancel();
                    return;
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void spawnQuantumSurfaceBlast(Player owner,
                                          WeaponType type,
                                          Location impact,
                                          Block hitBlock,
                                          BlockFace face,
                                          BlockData fallbackData,
                                          Vector travelDirection,
                                          double impactDamage) {
        if (hitBlock == null || hitBlock.getType().isAir() || !hitBlock.getType().isSolid()) {
            spawnQuantumDirectionalBlast(owner, type, impact, travelDirection, fallbackData, impactDamage);
            return;
        }
        if (face == null || face == BlockFace.UP || face == BlockFace.DOWN) {
            spawnQuantumDirectionalBlast(owner, type, impact, travelDirection, fallbackData, impactDamage);
            return;
        }
        spawnQuantumWallBlast(owner, type, hitBlock, face, fallbackData, impactDamage);
    }

    private void spawnQuantumDirectionalBlast(Player owner,
                                              WeaponType type,
                                              Location impact,
                                              Vector travelDirection,
                                              BlockData fallbackData,
                                              double impactDamage) {
        World world = impact.getWorld();
        if (world == null) {
            return;
        }
        Vector normal = travelDirection.clone();
        if (normal.lengthSquared() < 0.0001) {
            normal = new Vector(0, -1, 0);
        } else {
            normal.normalize();
        }
        Location center = impact.clone();
        List<RestoredBlock> restoredBlocks = new ArrayList<>();
        List<QuantumBlastDebris> debris = new ArrayList<>();
        Set<Block> changedBlocks = new HashSet<>();
        double radius = Math.max(4.25, weaponDouble(type, "signature-crater-radius", 3.0));
        int blockRadius = (int) Math.ceil(radius);
        int seed = center.getBlockX() * 31 + center.getBlockY() * 17 + center.getBlockZ() * 43
            + (int) Math.round(normal.getX() * 101.0 + normal.getY() * 211.0 + normal.getZ() * 307.0);

        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.74f);
        world.playSound(center, Sound.BLOCK_ROOTED_DIRT_BREAK, 1.05f, 0.6f);
        world.playSound(center, Sound.BLOCK_LAVA_POP, 0.72f, 0.82f);
        world.spawnParticle(Particle.EXPLOSION, center.clone().add(0, 0.35, 0), 1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticle(Particle.CLOUD, center, 34, 0.7, 0.7, 0.7, 0.045);
        world.spawnParticle(Particle.REVERSE_PORTAL, center, 42, 0.85, 0.85, 0.85, 0.04);
        world.spawnParticle(Particle.BLOCK, center, 56, 0.85, 0.85, 0.85, 0.0, fallbackData);
        world.spawnParticle(Particle.LAVA, center, 10, 0.7, 0.45, 0.7, 0.0);
        damageQuantumMeteorImpact(owner, type, center, radius, impactDamage, normal);

        for (int dx = -blockRadius; dx <= blockRadius; dx++) {
            for (int dz = -blockRadius; dz <= blockRadius; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > radius) {
                    continue;
                }
                int roll = Math.abs(seed + dx * 41 + dz * 67);
                Block surface = quantumMeteorSurfaceBlock(world, center, dx, dz);
                BlockData data = quantumVisualBlockData(surface, fallbackData);
                boolean core = dist <= 1.35;
                boolean craterCell = dist <= 2.25 || (dist <= radius - 0.35 && roll % 5 <= 2);
                int depth = core ? 2 : (dist <= 3.0 ? 1 : (roll % 7 == 0 ? 1 : 0));

                if (craterCell) {
                    for (int d = 0; d < depth; d++) {
                        Block remove = surface.getRelative(BlockFace.DOWN, d);
                        if (changedBlocks.add(remove) && canTemporarilyRemove(remove)) {
                            restoredBlocks.add(new RestoredBlock(remove, remove.getBlockData()));
                            remove.setType(Material.AIR, false);
                        }
                    }
                    Block bottom = surface.getRelative(BlockFace.DOWN, depth);
                    if (canTemporarilyScorch(bottom)) {
                        if (changedBlocks.add(bottom)) {
                            restoredBlocks.add(new RestoredBlock(bottom, bottom.getBlockData()));
                        }
                        bottom.setBlockData(quantumMeteorMaterial(roll, dist).createBlockData(), false);
                    }
                }

                if (dist <= radius - 0.15 && (craterCell || roll % 3 == 0)) {
                    float scale = (float) (0.4 + Math.max(0.0, 1.8 - dist) * 0.14);
                    Location start = surface.getLocation().add(0.5, 0.9, 0.5);
                    BlockDisplay display = spawnCubeBlock(start, Material.GRASS_BLOCK, scale, 76);
                    display.setBlock(data);
                    double angle = Math.atan2(dz, dx);
                    Vector velocity = new Vector(
                        Math.cos(angle) * (0.08 + dist * 0.035),
                        0.58 + Math.max(0.0, 2.4 - dist) * 0.11,
                        Math.sin(angle) * (0.08 + dist * 0.035)
                    );
                    debris.add(new QuantumBlastDebris(display, data, velocity, scale, angle + seed * 0.01));
                }
            }
        }
        world.spawnParticle(Particle.BLOCK, center.clone().add(0, 0.18, 0), 42, radius * 0.35, 0.08, radius * 0.35, 0.0,
            Material.MAGMA_BLOCK.createBlockData());

        animateQuantumGroundBlast(debris);
        int restoreTicks = weaponInt(type, "signature-crater-restore-ticks", -1);
        if (restoreTicks >= 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> restoreBlocks(restoredBlocks), restoreTicks);
        }
    }

    private Block quantumMeteorSurfaceBlock(World world, Location center, int dx, int dz) {
        int x = center.getBlockX() + dx;
        int z = center.getBlockZ() + dz;
        int startY = Math.min(world.getMaxHeight() - 1, center.getBlockY() + 2);
        int minY = Math.max(world.getMinHeight(), center.getBlockY() - 5);
        for (int y = startY; y >= minY; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (!block.getType().isAir() && isQuantumBreakableSurface(block.getType())) {
                return block;
            }
        }
        return world.getBlockAt(x, Math.max(world.getMinHeight(), center.getBlockY()), z);
    }

    private void spawnQuantumWallBlast(Player owner,
                                       WeaponType type,
                                       Block centerBlock,
                                       BlockFace face,
                                       BlockData fallbackData,
                                       double impactDamage) {
        World world = centerBlock.getWorld();
        Location center = centerBlock.getLocation().add(0.5, 0.5, 0.5);
        Vector normal = blockFaceVector(face);
        List<RestoredBlock> restoredBlocks = new ArrayList<>();
        List<QuantumBlastDebris> debris = new ArrayList<>();
        Set<Block> changedBlocks = new HashSet<>();
        double radius = Math.max(3.85, weaponDouble(type, "signature-crater-radius", 3.0) - 0.35);

        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.84f);
        world.playSound(center, Sound.BLOCK_ROOTED_DIRT_BREAK, 1.0f, 0.62f);
        world.spawnParticle(Particle.EXPLOSION, center.clone().add(normal.clone().multiply(0.55)), 1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticle(Particle.CLOUD, center.clone().add(normal.clone().multiply(0.4)), 24, 0.45, 0.65, 0.45, 0.04);
        world.spawnParticle(Particle.BLOCK, center.clone().add(normal.clone().multiply(0.35)), 42, 0.65, 0.65, 0.65, 0.0, fallbackData);
        damageQuantumMeteorImpact(owner, type, center, radius, impactDamage, normal);

        int cx = centerBlock.getX();
        int cy = centerBlock.getY();
        int cz = centerBlock.getZ();
        boolean xPlane = face == BlockFace.EAST || face == BlockFace.WEST;
        int blockRadius = (int) Math.ceil(radius);
        for (int a = -blockRadius; a <= blockRadius; a++) {
            for (int b = -blockRadius; b <= blockRadius; b++) {
                double dist = Math.sqrt(a * a + b * b);
                if (dist > radius) {
                    continue;
                }

                Block block = xPlane ? world.getBlockAt(cx, cy + a, cz + b) : world.getBlockAt(cx + b, cy + a, cz);
                BlockData data = quantumVisualBlockData(block, fallbackData);
                boolean craterCell = dist <= 1.45 || ((Math.abs(a * 19 + b * 37) % 4) == 0 && dist <= radius - 0.2);
                if (craterCell && changedBlocks.add(block) && canTemporarilyRemove(block)) {
                    restoredBlocks.add(new RestoredBlock(block, block.getBlockData()));
                    block.setType(Material.AIR, false);
                }

                if (dist <= radius - 0.1 && (craterCell || ((Math.abs(a * 13 + b * 7) % 3) == 0))) {
                    float scale = (float) (0.42 + Math.max(0.0, 1.5 - dist) * 0.16);
                    Location start = block.getLocation().add(0.5, 0.5, 0.5).add(normal.clone().multiply(0.45));
                    BlockDisplay display = spawnCubeBlock(start, Material.GRASS_BLOCK, scale, 72);
                    display.setBlock(data);
                    Vector velocity = normal.clone().multiply(0.24 + Math.max(0.0, radius - dist) * 0.04);
                    velocity.setY(velocity.getY() + 0.52 + Math.max(0.0, 1.8 - dist) * 0.08);
                    if (xPlane) {
                        velocity.add(new Vector(0, a * 0.015, b * 0.045));
                    } else {
                        velocity.add(new Vector(b * 0.045, a * 0.015, 0));
                    }
                    debris.add(new QuantumBlastDebris(display, data, velocity, scale, a * 0.27 + b * 0.19));
                }
            }
        }
        Vector upReference = Math.abs(normal.dot(new Vector(0, 1, 0))) > 0.86 ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
        Vector axisA = upReference.clone().crossProduct(normal).normalize();
        Vector axisB = normal.clone().crossProduct(axisA).normalize();
        applyQuantumMeteorScorch(world, center, normal, axisA, axisB, radius, centerBlock.getX() * 31 + centerBlock.getY() * 17 + centerBlock.getZ() * 43, restoredBlocks, changedBlocks);

        animateQuantumGroundBlast(debris);
        int restoreTicks = weaponInt(type, "signature-crater-restore-ticks", -1);
        if (restoreTicks >= 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> restoreBlocks(restoredBlocks), restoreTicks);
        }
    }

    private void damageQuantumMeteorImpact(Player owner,
                                           WeaponType type,
                                           Location center,
                                           double radius,
                                           double damage,
                                           Vector travelDirection) {
        double hitRadius = Math.max(2.35, radius + 0.45);
        double baseDamage = Math.max(2.4, damage * 0.72);
        for (LivingEntity target : nearbyEnemies(owner, center, hitRadius)) {
            double distance = target.getLocation().add(0, 0.7, 0).distance(center);
            double falloff = distance <= 1.25 ? 1.0 : Math.max(0.45, 1.0 - ((distance - 1.25) / hitRadius) * 0.55);
            dealAbilityDamage(owner, target, baseDamage * falloff);
            Vector knock = target.getLocation().toVector().subtract(center.toVector());
            if (knock.lengthSquared() < 0.0001) {
                knock = travelDirection.clone();
            }
            if (knock.lengthSquared() > 0.0001) {
                knock.normalize().multiply(0.48 + 0.12 * falloff);
            }
            knock.setY(Math.max(0.38, knock.getY() + 0.38 + 0.22 * falloff));
            target.setVelocity(knock);
            target.getWorld().spawnParticle(Particle.BLOCK, target.getLocation().add(0, 0.7, 0), 10,
                0.32, 0.45, 0.32, 0.0, Material.MAGMA_BLOCK.createBlockData());
            if (usesQuantumAbilities(type)) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 26, 0, true, true, true));
            }
        }
    }

    private void applyQuantumMeteorScorch(World world,
                                          Location center,
                                          Vector normal,
                                          Vector axisA,
                                          Vector axisB,
                                          double radius,
                                          int seed,
                                          List<RestoredBlock> restoredBlocks,
                                          Set<Block> changedBlocks) {
        int blockRadius = (int) Math.ceil(Math.max(1.0, radius - 0.2));
        BlockData magmaData = Material.MAGMA_BLOCK.createBlockData();
        for (int a = -blockRadius; a <= blockRadius; a++) {
            for (int b = -blockRadius; b <= blockRadius; b++) {
                double dist = Math.sqrt(a * a + b * b);
                if (dist > radius - 0.25) {
                    continue;
                }
                int roll = Math.abs(seed + a * 41 + b * 67);
                boolean core = dist <= 1.15;
                boolean patch = core || (dist <= radius - 0.55 && roll % 5 <= 2);
                if (!patch) {
                    continue;
                }
                Vector planeOffset = axisA.clone().multiply(a).add(axisB.clone().multiply(b));
                Vector sample = center.toVector()
                    .add(planeOffset)
                    .add(normal.clone().multiply(1.05 + Math.max(0.0, 1.7 - dist) * 0.18));
                Block block = world.getBlockAt(sample.getBlockX(), sample.getBlockY(), sample.getBlockZ());
                if (!canTemporarilyScorch(block)) {
                    continue;
                }
                if (changedBlocks.add(block)) {
                    restoredBlocks.add(new RestoredBlock(block, block.getBlockData()));
                }
                Material scorched = quantumMeteorMaterial(roll, dist);
                block.setBlockData(scorched.createBlockData(), false);
                if (scorched == Material.MAGMA_BLOCK || core) {
                    Location glow = block.getLocation().add(0.5, 0.65, 0.5);
                    world.spawnParticle(Particle.BLOCK, glow, 4, 0.18, 0.08, 0.18, 0.0, magmaData);
                    if (roll % 3 == 0) {
                        world.spawnParticle(Particle.LAVA, glow, 1, 0.08, 0.05, 0.08, 0.0);
                    }
                }
            }
        }
    }

    private Material quantumMeteorMaterial(int roll, double dist) {
        if (dist <= 0.95 && roll % 4 == 0) {
            return Material.MAGMA_BLOCK;
        }
        return switch (roll % 8) {
            case 0 -> Material.MAGMA_BLOCK;
            case 1 -> Material.BLACKSTONE;
            case 2 -> Material.BASALT;
            case 3 -> Material.COBBLED_DEEPSLATE;
            case 4 -> Material.TUFF;
            default -> Material.COBBLESTONE;
        };
    }

    private Vector blockFaceVector(BlockFace face) {
        return switch (face) {
            case EAST -> new Vector(1, 0, 0);
            case WEST -> new Vector(-1, 0, 0);
            case SOUTH -> new Vector(0, 0, 1);
            case NORTH -> new Vector(0, 0, -1);
            case DOWN -> new Vector(0, -1, 0);
            default -> new Vector(0, 1, 0);
        };
    }

    private BlockFace dominantImpactFace(Vector direction) {
        double ax = Math.abs(direction.getX());
        double ay = Math.abs(direction.getY());
        double az = Math.abs(direction.getZ());
        if (ay >= ax && ay >= az) {
            return direction.getY() > 0 ? BlockFace.DOWN : BlockFace.UP;
        }
        if (ax >= az) {
            return direction.getX() > 0 ? BlockFace.WEST : BlockFace.EAST;
        }
        return direction.getZ() > 0 ? BlockFace.NORTH : BlockFace.SOUTH;
    }

    private void spawnQuantumGroundBlast(WeaponType type, Location impact, BlockData fallbackData) {
        Location center = quantumImpactCenter(impact);
        World world = center.getWorld();
        List<RestoredBlock> restoredBlocks = new ArrayList<>();
        List<QuantumBlastDebris> debris = new ArrayList<>();
        double radius = Math.max(4.15, weaponDouble(type, "signature-crater-radius", 3.0));

        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.05f, 0.72f);
        world.playSound(center, Sound.BLOCK_GRASS_BREAK, 1.25f, 0.55f);
        world.spawnParticle(Particle.EXPLOSION, center.clone().add(0, 0.35, 0), 1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticle(Particle.CLOUD, center.clone().add(0, 0.25, 0), 38, 1.75, 0.3, 1.75, 0.055);
        world.spawnParticle(Particle.REVERSE_PORTAL, center.clone().add(0, 0.45, 0), 62, 2.0, 0.45, 2.0, 0.055);
        world.spawnParticle(Particle.BLOCK, center.clone().add(0, 0.35, 0), 65, 1.5, 0.35, 1.5, 0.0, fallbackData);

        int blockRadius = (int) Math.ceil(radius);
        for (int dx = -blockRadius; dx <= blockRadius; dx++) {
            for (int dz = -blockRadius; dz <= blockRadius; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > radius) {
                    continue;
                }
                Location sample = center.clone().add(dx, 0, dz);
                Block block = quantumSourceBlock(sample);
                BlockData data = quantumVisualBlockData(block, fallbackData);
                double angle = Math.atan2(dz, dx);
                boolean craterCell = dist <= 2.2 || ((Math.abs(dx) + Math.abs(dz)) % 3 == 0 && dist <= radius - 0.55);

                if (craterCell && canTemporarilyRemove(block)) {
                    restoredBlocks.add(new RestoredBlock(block, block.getBlockData()));
                    block.setType(Material.AIR, false);
                }

                if (dist <= radius - 0.25 && (craterCell || (Math.abs(dx * 31 + dz * 17) % 2 == 0))) {
                    float scale = (float) (0.58 + Math.max(0.0, 1.6 - dist) * 0.2);
                    Location start = block.getLocation().add(0.5, 1.05, 0.5);
                    BlockDisplay display = spawnCubeBlock(start, Material.GRASS_BLOCK, scale, 78);
                    display.setBlock(data);
                    Vector velocity = new Vector(
                        Math.cos(angle) * (0.14 + dist * 0.045),
                        0.72 + Math.max(0.0, 2.4 - dist) * 0.14,
                        Math.sin(angle) * (0.14 + dist * 0.045)
                    );
                    debris.add(new QuantumBlastDebris(display, data, velocity, scale, angle));
                }
            }
        }

        animateQuantumGroundBlast(debris);
        int restoreTicks = weaponInt(type, "signature-crater-restore-ticks", -1);
        if (restoreTicks >= 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> restoreBlocks(restoredBlocks), restoreTicks);
        }
    }

    private void animateQuantumGroundBlast(List<QuantumBlastDebris> debris) {
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks > 58) {
                    for (QuantumBlastDebris piece : debris) {
                        if (piece.display.isValid()) {
                            piece.display.remove();
                        }
                    }
                    cancel();
                    return;
                }
                for (int i = 0; i < debris.size(); i++) {
                    QuantumBlastDebris piece = debris.get(i);
                    if (!piece.display.isValid()) {
                        continue;
                    }
                    Location loc = piece.display.getLocation().add(piece.velocity);
                    piece.velocity.setY(piece.velocity.getY() - 0.034);
                    piece.display.teleport(loc);
                    setCubeTransform(piece.display, piece.scale, (float) (piece.spinOffset + ticks * 0.22 + i * 0.17));
                    if (ticks % 3 == 0) {
                        loc.getWorld().spawnParticle(Particle.BLOCK, loc, 2, 0.1, 0.1, 0.1, 0.0, piece.blockData);
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private Location quantumImpactCenter(Location impact) {
        World world = impact.getWorld();
        int x = impact.getBlockX();
        int z = impact.getBlockZ();
        int startY = Math.min(world.getMaxHeight() - 1, impact.getBlockY() + 2);
        int minY = Math.max(world.getMinHeight(), impact.getBlockY() - 7);
        for (int y = startY; y >= minY; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (block.getType().isSolid() && !block.getType().isAir()) {
                return block.getLocation().add(0.5, 1.02, 0.5);
            }
        }
        return impact.clone();
    }

    private void restoreBlocks(List<RestoredBlock> restoredBlocks) {
        for (RestoredBlock restored : restoredBlocks) {
            if (restored.block.getType().isAir()) {
                restored.block.setBlockData(restored.blockData, false);
            }
        }
    }

    private Block quantumSourceBlock(Location sample) {
        World world = sample.getWorld();
        int x = sample.getBlockX();
        int z = sample.getBlockZ();
        int startY = Math.min(world.getMaxHeight() - 1, sample.getBlockY() + 1);
        int minY = Math.max(world.getMinHeight(), startY - 6);
        for (int y = startY; y >= minY; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (block.getType().isSolid() && !block.getType().isAir()) {
                return block;
            }
        }
        return world.getBlockAt(x, Math.max(world.getMinHeight(), sample.getBlockY() - 1), z);
    }

    private boolean canTemporarilyRemove(Block block) {
        Material material = block.getType();
        return !material.isAir()
            && isQuantumBreakableSurface(material)
            && material != Material.BEDROCK
            && !isQuantumProtectedBlock(material)
            && !(block.getState() instanceof org.bukkit.block.Container);
    }

    private boolean canTemporarilyScorch(Block block) {
        Material material = block.getType();
        return !material.isAir()
            && material.isSolid()
            && material != Material.BEDROCK
            && !isQuantumProtectedBlock(material)
            && !(block.getState() instanceof org.bukkit.block.Container);
    }

    private BlockData quantumVisualBlockData(Block block, BlockData fallbackData) {
        Material material = block.getType();
        if (material == Material.BEDROCK || material.isAir() || !isQuantumBreakableSurface(material)) {
            return fallbackData;
        }
        return block.getBlockData();
    }

    private boolean isQuantumBreakableSurface(Material material) {
        return material.isSolid() || material.name().endsWith("_LEAVES");
    }

    private boolean isQuantumProtectedBlock(Material material) {
        return switch (material) {
            case OBSIDIAN,
                 CRYING_OBSIDIAN,
                 DIAMOND_BLOCK,
                 DIAMOND_ORE,
                 DEEPSLATE_DIAMOND_ORE,
                 GOLD_BLOCK,
                 GOLD_ORE,
                 DEEPSLATE_GOLD_ORE,
                 NETHER_GOLD_ORE,
                 RAW_GOLD_BLOCK,
                 EMERALD_BLOCK,
                 EMERALD_ORE,
                 DEEPSLATE_EMERALD_ORE,
                 IRON_BLOCK,
                 IRON_ORE,
                 DEEPSLATE_IRON_ORE,
                 RAW_IRON_BLOCK,
                 LAPIS_BLOCK,
                 LAPIS_ORE,
                 DEEPSLATE_LAPIS_ORE,
                 REDSTONE_BLOCK,
                 REDSTONE_ORE,
                 DEEPSLATE_REDSTONE_ORE,
                 NETHERITE_BLOCK,
                 ANCIENT_DEBRIS,
                 COPPER_BLOCK,
                 RAW_COPPER_BLOCK,
                 COPPER_ORE,
                 DEEPSLATE_COPPER_ORE,
                 COAL_BLOCK,
                 COAL_ORE,
                 DEEPSLATE_COAL_ORE,
                 END_PORTAL_FRAME,
                 COMMAND_BLOCK,
                 CHAIN_COMMAND_BLOCK,
                 REPEATING_COMMAND_BLOCK,
                 BARRIER,
                 STRUCTURE_BLOCK,
                 STRUCTURE_VOID,
                 JIGSAW,
                 SPAWNER -> true;
            default -> false;
        };
    }

    private void expireQuantumArsenal(UUID playerId, QuantumArsenalState state) {
        QuantumArsenalState current = quantumArsenals.get(playerId);
        if (current != state) {
            return;
        }
        quantumArsenals.remove(playerId);
        state.launched = true;
        if (state.orbitTask != null) {
            state.orbitTask.cancel();
        }
        restoreQuantumBlocks(state);
        animateQuantumArsenalDrop(state);
    }

    private void cleanupQuantumArsenal(QuantumArsenalState state, boolean removeDisplays, boolean restoreBlocks) {
        state.launched = true;
        if (state.orbitTask != null) {
            state.orbitTask.cancel();
        }
        if (restoreBlocks) {
            restoreQuantumBlocks(state);
        }
        if (removeDisplays) {
            for (QuantumChunk chunk : state.chunks) {
                if (chunk.display.isValid()) {
                    chunk.display.remove();
                }
            }
        }
    }

    private void restoreQuantumBlocks(QuantumArsenalState state) {
        if (state.restored) {
            return;
        }
        state.restored = true;
        for (RestoredBlock restored : state.restoredBlocks) {
            if (restored.block.getType().isAir()) {
                restored.block.setBlockData(restored.blockData, false);
            }
        }
    }

    private void animateQuantumArsenalDrop(QuantumArsenalState state) {
        List<Location> starts = state.chunks.stream()
            .map(chunk -> chunk.display.getLocation().clone())
            .toList();
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks > 14) {
                    for (QuantumChunk chunk : state.chunks) {
                        if (chunk.display.isValid()) {
                            chunk.display.remove();
                        }
                    }
                    cancel();
                    return;
                }
                double drop = ticks * 0.16;
                for (int i = 0; i < state.chunks.size(); i++) {
                    QuantumChunk chunk = state.chunks.get(i);
                    if (!chunk.display.isValid()) {
                        continue;
                    }
                    chunk.display.teleport(starts.get(i).clone().subtract(0, drop, 0));
                    setCubeTransform(chunk.display, chunk.scale, (float) (ticks * 0.18 + i * 0.6));
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void dashCleave(Player player, WeaponType type, String model, int blocks, double dmg, boolean ignite) {
        Vector dir = player.getLocation().getDirection().setY(0).normalize();
        player.setVelocity(dir.clone().multiply(1.35).setY(0.18));
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ > 12 || !player.isOnline()) {
                    cancel();
                    return;
                }
                Location loc = player.getLocation().add(0, 1.0, 0);
                spawnModel(loc, model, 0.9f, 18);
                hitNearby(player, type, loc, 2.4, damage(type, "signature-damage", dmg), dir, 0.75, ignite ? 70 : 0);
            }
        }.runTaskTimer(plugin, 0L, 1L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> player.setVelocity(player.getVelocity().multiply(0.35)), Math.max(4, blocks));
    }

    private void hookLine(Player player, WeaponType type, String model, int range, boolean pullCaster) {
        fireModelProjectile(player, type, model, range, 1.35, damage(type, "signature-damage", 5.8), 1.3, target -> {
            Vector between = target.getLocation().toVector().subtract(player.getLocation().toVector());
            if (pullCaster) {
                player.setVelocity(between.normalize().multiply(1.25).setY(0.18));
                target.setVelocity(player.getLocation().getDirection().crossProduct(new Vector(0, 1, 0)).normalize().multiply(0.7).setY(0.25));
            } else {
                target.setVelocity(player.getLocation().toVector().subtract(target.getLocation().toVector()).normalize().multiply(1.2).setY(0.25));
            }
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 35, 1, true, true, true));
        });
    }

    private void fallingMonoliths(Player player, WeaponType type) {
        Location origin = targetPoint(player, 15);
        Vector right = player.getLocation().getDirection().crossProduct(new Vector(0, 1, 0)).normalize();
        for (int i = -1; i <= 1; i++) {
            Location target = origin.clone().add(right.clone().multiply(i * 2.6));
            telegraphBlock(target, Material.LIGHT_BLUE_STAINED_GLASS, 2.2f, 28);
            int delay = 18 + ((i + 1) * 8);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                spawnModel(target.clone().add(0, 4.0, 0), "vfx/frost", 1.8f, 26);
                hitNearby(player, type, target, 2.8, damage(type, "signature-damage", 6.4), new Vector(0, 1, 0), 0.7, 0);
                slowNearby(player, target, 3.4, 55, 2);
            }, delay);
        }
    }

    private void leapSlam(Player player, WeaponType type) {
        Vector dir = player.getLocation().getDirection().setY(0).normalize();
        player.setVelocity(dir.multiply(0.75).setY(1.05));
        Location landing = targetPoint(player, 9);
        telegraphBlock(landing, Material.YELLOW_STAINED_GLASS, 3.0f, 24);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            spawnBurst(landing.clone().add(0, 0.4, 0), "vfx/lightning", 8, 2.4, 34);
            hitNearby(player, type, landing, 4.0, damage(type, "signature-damage", 7.2), new Vector(0, 1, 0), 0.95, 0);
        }, 18L);
    }

    private void joustCharge(Player player, WeaponType type) {
        Vector dir = player.getLocation().getDirection().setY(0).normalize();
        player.setVelocity(dir.clone().multiply(1.55).setY(0.12));
        for (int i = 0; i < 14; i++) {
            int delay = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location loc = player.getLocation().add(0, 0.85, 0);
                spawnModel(loc, "vfx/slash", 1.1f, 18);
                hitNearby(player, type, loc, 2.2, damage(type, "signature-damage", 6.3), dir, 1.15, 0);
            }, delay);
        }
    }

    private void prisonNearest(Player player, WeaponType type, String model, Material material, int range, double seconds) {
        LivingEntity target = nearestEnemy(player, targetPoint(player, range), 3.0);
        if (target == null) {
            messageService.action(player, "&7No target caught");
            return;
        }
        Location center = target.getLocation().add(0, 1.0, 0);
        spawnCage(center, material, (int) (seconds * 20));
        spawnModel(center.clone().add(0, 0.3, 0), model, 1.2f, (int) (seconds * 20));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) (seconds * 20), 5, true, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, (int) (seconds * 20), 1, true, true, true));
        dealAbilityDamage(player, target, damage(type, "signature-damage", 5.4));
    }

    private void soulSever(Player player, WeaponType type) {
        Location center = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(3.0));
        spawnModel(center.clone().add(0, 1.2, 0), "vfx/slash", 2.1f, 28);
        hitNearby(player, type, center, 4.0, damage(type, "signature-damage", 6.6), player.getLocation().getDirection(), 0.8, 0);
        heal(player, 2.0);
    }

    private void beastwoodRam(Player player, WeaponType type) {
        Vector dir = player.getLocation().getDirection().setY(0).normalize();
        for (int i = 1; i <= 12; i++) {
            Location loc = player.getLocation().add(dir.clone().multiply(i));
            int delay = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                spawnBlock(loc.clone().add(0, 0.15, 0), Material.OAK_LOG, 1.25f, 48);
                hitNearby(player, type, loc, 1.8, damage(type, "signature-damage", 5.8), dir, 0.9, 0);
            }, delay);
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 90, 0, true, true, true));
    }

    private void venusVolley(Player player, WeaponType type) {
        for (int i = 0; i < 3; i++) {
            Vector dir = player.getLocation().getDirection().clone();
            dir.rotateAroundY((i - 1) * 0.16);
            fireModelProjectile(player, type, "vfx/flower", 18, 0.95, damage(type, "signature-damage", 4.8), 0.35, target -> {
                target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0, true, true, true));
                target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 80, 0, true, true, true));
            }, dir);
        }
        heal(player, 2.0);
    }

    private void tripleHunterShot(Player player, WeaponType type) {
        spendTempestCharges(player, type, 3);
        for (int i = -1; i <= 1; i++) {
            Vector dir = player.getLocation().getDirection().clone();
            dir.rotateAroundY(i * 0.13);
            fireModelProjectile(player, type, "vfx/sonic", 26, 1.25, damage(type, "signature-damage", 5.2), 0.65, null, dir);
        }
    }

    private void singularityAscension(Player player, WeaponType type) {
        double radius = weaponDouble(type, "ultimate-radius", 6.0);
        int hudTicks = spawnQuantumCasterHud(player, type, true, QuantumSigil.WARRIOR);
        preloadQuantumGroundSigil(player, radius, hudTicks);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            startSingularityAscension(player, type);
        }, hudTicks);
    }

    private void startSingularityAscension(Player player, WeaponType type) {
        int charge = charges(player, type);
        setCharges(player, type, 0);
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 1, true, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 0, true, true, true));
        if (charge > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 160, Math.min(2, charge - 1), true, true, true));
        }
        int chargeTicks = Math.max(12, (int) Math.round(weaponInt(type, "ultimate-charge-ticks", 40) / 1.2));
        int freezeTicks = Math.max(140, weaponInt(type, "ultimate-freeze-ticks", 140));
        double radius = weaponDouble(type, "ultimate-radius", 6.0);
        QuantumAscensionState old = quantumAscensions.remove(player.getUniqueId());
        if (old != null) {
            cleanupQuantumAscension(old);
        }
        playCastSound(player, type, true);
        Location center = player.getLocation().clone();
        QuantumAscensionState state = new QuantumAscensionState(player, type, center, quantumGroundPlane(center), radius, chargeTicks, freezeTicks);
        state.groundSigil = spawnQuantumGroundSigil(center, radius, chargeTicks + freezeTicks + 70);
        quantumAscensions.put(player.getUniqueId(), state);
        startQuantumAscensionZone(state);
    }

    private void startQuantumAscensionZone(QuantumAscensionState state) {
        Player player = state.player;
        World world = state.center.getWorld();
        int cubeCount = Math.min(24, Math.max(10, weaponInt(state.type, "ultimate-orbit-cube-count", 16)));
        for (int i = 0; i < cubeCount; i++) {
            float scale = i % 4 == 0 ? 0.46f : 0.34f;
            ItemDisplay display = spawnRedCubeModel(state.ground.clone().add(0, 1.82, 0), scale * 0.08f,
                state.introTicks + state.freezeTicks + 120);
            display.setBillboard(Display.Billboard.FIXED);
            state.cubes.add(new QuantumOrbitCube(display, scale, Math.PI * 2.0 * i / cubeCount));
        }
        world.playSound(state.center, Sound.ENTITY_ENDERMAN_TELEPORT, 0.9f, 0.62f);
        state.task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || quantumAscensions.get(player.getUniqueId()) != state) {
                    cleanupQuantumAscension(state);
                    cancel();
                    return;
                }
                double intro = Math.min(1.0, (ticks + 1.0) / state.introTicks);
                double easedIntro = intro * intro * (3.0 - 2.0 * intro);
                updateQuantumOrbitCubes(state, ticks, easedIntro);
                updateQuantumCaptures(state);
                renderQuantumZonePulse(state, ticks, easedIntro);
                applyQuantumGroundDecay(state, ticks);

                if (ticks == state.introTicks) {
                    world.playSound(state.center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.15f, 0.62f);
                    world.playSound(state.center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.85f, 0.74f);
                    captureQuantumEnemies(state);
                } else if (ticks > state.introTicks && ticks % 5 == 0) {
                    captureQuantumEnemies(state);
                }

                if (ticks >= state.introTicks + state.freezeTicks) {
                    beginQuantumAscensionCollapse(state, false);
                    cancel();
                    return;
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void updateQuantumOrbitCubes(QuantumAscensionState state, int ticks, double easedIntro) {
        double spin = ticks * 0.095;
        double orbitRadius = state.radius * easedIntro;
        for (int i = 0; i < state.cubes.size(); i++) {
            QuantumOrbitCube cube = state.cubes.get(i);
            if (cube.assigned || !cube.display.isValid()) {
                continue;
            }
            double angle = spin + cube.angleOffset;
            double y = 1.82 + (i % 2) * 0.52 + Math.sin(ticks * 0.12 + i * 0.72) * 0.1;
            Location loc = state.ground.clone().add(Math.cos(angle) * orbitRadius, y, Math.sin(angle) * orbitRadius);
            cube.display.teleport(loc);
            setItemCubeTransform(cube.display, (float) (cube.scale * Math.max(0.08, easedIntro)), (float) (ticks * 0.14 + i * 0.48));
        }
    }

    private void applyQuantumGroundDecay(QuantumAscensionState state, int ticks) {
        boolean refreshHud = ticks % 8 == 0;
        boolean damageTick = ticks > 0 && ticks % 20 == 0;
        if (!refreshHud && !damageTick) {
            return;
        }
        double damage = weaponDouble(state.type, "ultimate-ground-decay-damage", 1.0);
        for (LivingEntity target : nearbyEnemies(state.player, state.center, state.radius)) {
            Location location = target.getLocation();
            double dx = location.getX() - state.ground.getX();
            double dz = location.getZ() - state.ground.getZ();
            double y = location.getY() - state.ground.getY();
            if (dx * dx + dz * dz > state.radius * state.radius || y < -1.0 || y > 1.35) {
                continue;
            }
            if (refreshHud) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 24, 0, true, false, false));
            }
            if (damageTick) {
                dealAbilityDamage(state.player, target, damage);
            }
        }
    }

    private void renderQuantumZonePulse(QuantumAscensionState state, int ticks, double easedIntro) {
        if (ticks % 3 != 0) {
            return;
        }
        int count = 16;
        double ring = state.radius * easedIntro;
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0 * i / count + ticks * 0.025;
            Location loc = state.ground.clone().add(Math.cos(angle) * ring, 0.12, Math.sin(angle) * ring);
            state.center.getWorld().spawnParticle(Particle.DUST, loc, 1, 0.015, 0.015, 0.015, 0.0,
                new Particle.DustOptions(QUANTUM_ULTIMATE_RED, 0.95f));
        }
    }

    private void captureQuantumEnemies(QuantumAscensionState state) {
        for (LivingEntity target : nearbyEnemies(state.player, state.center, state.radius)) {
            if (state.captures.containsKey(target.getUniqueId())
                || target.getLocation().distanceSquared(state.center) > state.radius * state.radius) {
                continue;
            }
            QuantumOrbitCube cube = state.cubes.stream()
                .filter(candidate -> !candidate.assigned && candidate.display.isValid())
                .findFirst()
                .orElse(null);
            if (cube == null) {
                return;
            }
            cube.assigned = true;
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 28, 9, true, true, true));
            target.setVelocity(new Vector(0, 0, 0));
            state.captures.put(target.getUniqueId(), new QuantumCapture(target, cube, cube.display.getLocation().clone()));
        }
    }

    private void updateQuantumCaptures(QuantumAscensionState state) {
        List<UUID> released = new ArrayList<>();
        for (Map.Entry<UUID, QuantumCapture> entry : state.captures.entrySet()) {
            QuantumCapture capture = entry.getValue();
            LivingEntity target = capture.target;
            ItemDisplay cube = capture.cube.display;
            if (!target.isValid() || target.isDead() || !cube.isValid()) {
                capture.cube.assigned = false;
                released.add(entry.getKey());
                continue;
            }
            if (capture.phase == QuantumCapturePhase.APPROACH) {
                animateQuantumCaptureApproach(state, capture);
            } else if (capture.phase == QuantumCapturePhase.ASCEND) {
                animateQuantumCaptureAscend(state, capture);
            } else {
                holdQuantumCapture(capture);
            }
            capture.phaseTicks++;
        }
        for (UUID id : released) {
            state.captures.remove(id);
        }
    }

    private void animateQuantumCaptureApproach(QuantumAscensionState state, QuantumCapture capture) {
        int travelTicks = 10;
        double progress = Math.min(1.0, (capture.phaseTicks + 1.0) / travelTicks);
        double eased = progress * progress * (3.0 - 2.0 * progress);
        Location destination = capture.target.getLocation().clone().subtract(0, 0.12, 0);
        Location control = capture.start.clone()
            .add(destination.toVector().subtract(capture.start.toVector()).multiply(0.52))
            .add(0, 1.05, 0);
        Location loc = bezier(capture.start, control, destination, eased);
        capture.cube.display.teleport(loc);
        setItemCubeTransform(capture.cube.display, capture.cube.scale, (float) (capture.phaseTicks * 0.28));
        loc.getWorld().spawnParticle(Particle.DUST, loc, 2, 0.06, 0.06, 0.06, 0.0,
            new Particle.DustOptions(QUANTUM_ULTIMATE_RED, 1.0f));
        if (progress >= 1.0) {
            capture.phase = QuantumCapturePhase.ASCEND;
            capture.phaseTicks = -1;
            capture.liftStart = capture.target.getLocation().clone();
            capture.anchor = capture.liftStart.clone().add(0, 2.6, 0);
            dealAbilityDamage(state.player, capture.target, damage(state.type, "ultimate-damage", 5.2));
            applyQuantumCaptureEffects(capture.target, state.freezeTicks);
            loc.getWorld().playSound(loc, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.65f, 0.82f);
        }
    }

    private void animateQuantumCaptureAscend(QuantumAscensionState state, QuantumCapture capture) {
        int liftTicks = 12;
        double progress = Math.min(1.0, (capture.phaseTicks + 1.0) / liftTicks);
        double eased = 1.0 - Math.pow(1.0 - progress, 3.0);
        Location targetLoc = lerpLocation(capture.liftStart, capture.anchor, eased);
        capture.target.teleport(targetLoc);
        capture.target.setVelocity(new Vector(0, 0, 0));
        capture.cube.display.teleport(targetLoc.clone().subtract(0, 0.95, 0));
        setItemCubeTransform(capture.cube.display, 1.22f, (float) (capture.phaseTicks * 0.22));
        if (capture.phaseTicks % 3 == 0) {
            targetLoc.getWorld().spawnParticle(Particle.DUST, targetLoc, 5, 0.34, 0.45, 0.34, 0.0,
                new Particle.DustOptions(QUANTUM_ULTIMATE_RED, 1.15f));
        }
        if (progress >= 1.0) {
            capture.phase = QuantumCapturePhase.HOLD;
            capture.phaseTicks = -1;
        }
    }

    private void holdQuantumCapture(QuantumCapture capture) {
        Location anchor = capture.anchor;
        capture.target.teleport(anchor);
        capture.target.setVelocity(new Vector(0, 0, 0));
        Location platform = anchor.clone().subtract(0, 0.95, 0).add(0, Math.sin(capture.phaseTicks * 0.16) * 0.035, 0);
        capture.cube.display.teleport(platform);
        setItemCubeTransform(capture.cube.display, 1.22f, (float) (capture.phaseTicks * 0.12));
        if (capture.phaseTicks % 8 == 0) {
            anchor.getWorld().spawnParticle(Particle.REVERSE_PORTAL, platform, 4, 0.38, 0.08, 0.38, 0.02);
        }
    }

    private void applyQuantumCaptureEffects(LivingEntity target, int freezeTicks) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, freezeTicks + 30, 1, true, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, freezeTicks + 20, 9, true, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, freezeTicks + 20, 1, true, true, true));
    }

    private void beginQuantumAscensionCollapse(QuantumAscensionState state, boolean aborted) {
        if (state.closing) {
            return;
        }
        state.closing = true;
        quantumAscensions.remove(state.player.getUniqueId(), state);
        if (state.task != null) {
            state.task.cancel();
        }
        finishQuantumGroundSigil(state.groundSigil);
        releaseQuantumCaptures(state);

        List<QuantumOrbitCube> cubes = state.cubes.stream()
            .filter(cube -> cube.display.isValid())
            .toList();
        if (cubes.isEmpty()) {
            return;
        }
        List<Location> starts = cubes.stream().map(cube -> cube.display.getLocation().clone()).toList();
        Location[] corners = new Location[4];
        for (int i = 0; i < corners.length; i++) {
            double angle = Math.PI / 4.0 + Math.PI * 2.0 * i / corners.length;
            corners[i] = state.ground.clone().add(Math.cos(angle) * 2.7, 0.8, Math.sin(angle) * 2.7);
        }
        state.center.getWorld().playSound(state.center, aborted ? Sound.BLOCK_BEACON_DEACTIVATE : Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, aborted ? 0.55f : 0.82f);
        new BukkitRunnable() {
            int ticks = 0;
            boolean merged = false;
            final QuantumOrbitCube[] representatives = new QuantumOrbitCube[4];

            @Override
            public void run() {
                if (ticks <= 10) {
                    double progress = ticks / 10.0;
                    double eased = progress * progress * (3.0 - 2.0 * progress);
                    for (int i = 0; i < cubes.size(); i++) {
                        QuantumOrbitCube cube = cubes.get(i);
                        if (!cube.display.isValid()) {
                            continue;
                        }
                        int corner = i % corners.length;
                        Location loc = lerpLocation(starts.get(i), corners[corner], eased)
                            .add(0, Math.sin(progress * Math.PI) * 0.6, 0);
                        cube.display.teleport(loc);
                        setItemCubeTransform(cube.display, cube.scale, (float) (ticks * 0.24 + i * 0.38));
                    }
                } else {
                    if (!merged) {
                        for (int i = 0; i < cubes.size(); i++) {
                            QuantumOrbitCube cube = cubes.get(i);
                            int corner = i % corners.length;
                            if (representatives[corner] == null && cube.display.isValid()) {
                                representatives[corner] = cube;
                            } else if (cube.display.isValid()) {
                                cube.display.remove();
                            }
                        }
                        merged = true;
                    }
                    double progress = Math.min(1.0, (ticks - 10.0) / 12.0);
                    double eased = progress * progress * (3.0 - 2.0 * progress);
                    Location destination = state.player.isOnline()
                        ? state.player.getLocation().add(0, 1.0, 0)
                        : state.center.clone().add(0, 1.0, 0);
                    for (int i = 0; i < representatives.length; i++) {
                        QuantumOrbitCube cube = representatives[i];
                        if (cube == null || !cube.display.isValid()) {
                            continue;
                        }
                        Location loc = lerpLocation(corners[i], destination, eased);
                        cube.display.teleport(loc);
                        setItemCubeTransform(cube.display, cube.scale * (float) Math.max(0.08, 1.0 - eased), (float) (ticks * 0.32 + i));
                    }
                    if (progress >= 1.0) {
                        for (QuantumOrbitCube cube : representatives) {
                            if (cube != null && cube.display.isValid()) {
                                cube.display.remove();
                            }
                        }
                        destination.getWorld().spawnParticle(Particle.PORTAL, destination, 28, 0.32, 0.36, 0.32, 0.04);
                        cancel();
                        return;
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void releaseQuantumCaptures(QuantumAscensionState state) {
        for (QuantumCapture capture : state.captures.values()) {
            LivingEntity target = capture.target;
            if (!target.isValid() || target.isDead()) {
                continue;
            }
            target.removePotionEffect(PotionEffectType.SLOWNESS);
            target.removePotionEffect(PotionEffectType.WEAKNESS);
            target.setVelocity(new Vector(0, 0, 0));
        }
        state.captures.clear();
    }

    private void cleanupQuantumAscension(QuantumAscensionState state) {
        if (state.task != null) {
            state.task.cancel();
        }
        releaseQuantumCaptures(state);
        removeQuantumGroundSigil(state.groundSigil);
        for (QuantumOrbitCube cube : state.cubes) {
            if (cube.display.isValid()) {
                cube.display.remove();
            }
        }
    }

    private Location lerpLocation(Location start, Location end, double progress) {
        return start.clone().add(end.toVector().subtract(start.toVector()).multiply(progress));
    }

    private void setItemCubeTransform(ItemDisplay display, float scale, float angle) {
        display.setTransformation(new Transformation(
            new Vector3f(),
            new Quaternionf(new AxisAngle4f(angle, 0.25f, 1f, 0.15f)),
            new Vector3f(scale, scale, scale),
            new Quaternionf()
        ));
    }

    private QuantumGroundSigilHandle spawnQuantumGroundSigil(Location center, double radius, int lifetime) {
        Location ground = quantumGroundPlane(center);
        double fullAreaScale = Math.max(1.0, (radius * 2.0) / 3.0);
        ItemDisplay circle = spawnModel(ground.clone().add(0, 0.04, 0),
            "vfx/summoning_circle", (float) (fullAreaScale * 0.025), lifetime + 30);
        circle.setBillboard(Display.Billboard.FIXED);
        circle.setBrightness(new Display.Brightness(15, 15));
        QuantumGroundSigilHandle handle = new QuantumGroundSigilHandle(circle);

        handle.task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!circle.isValid()) {
                    cancel();
                    return;
                }
                if (ticks > lifetime) {
                    handle.closing = true;
                }
                if (handle.closing && handle.closeTicks < 0) {
                    handle.closeTicks = 0;
                }
                double intro = Math.min(1.0, (ticks + 1) / 10.0);
                double outro = handle.closeTicks < 0 ? 0.0 : Math.min(1.0, handle.closeTicks / 16.0);
                double inEase = intro * intro * (3.0 - 2.0 * intro);
                double outEase = outro * outro * (3.0 - 2.0 * outro);
                double visible = Math.max(0.0, inEase * (1.0 - outEase));
                double circleScale = fullAreaScale * Math.max(0.025, visible) * (1.0 + Math.sin(ticks * 0.1) * 0.012 * visible);
                if (circle.isValid()) {
                    circle.teleport(ground.clone().add(0, 0.04, 0));
                    circle.setTransformation(new Transformation(
                        new Vector3f(),
                        new Quaternionf(new AxisAngle4f((float) (ticks * 0.006), 0f, 1f, 0f)),
                        new Vector3f((float) circleScale, (float) circleScale, (float) circleScale),
                        new Quaternionf()
                    ));
                }
                if (ticks % 8 == 0) {
                    center.getWorld().spawnParticle(Particle.DUST, ground.clone().add(0, 0.12, 0), 18,
                        radius * 0.42, 0.02, radius * 0.42, 0.0,
                        new Particle.DustOptions(QUANTUM_ULTIMATE_RED, 1.0f));
                }
                if (outro >= 1.0) {
                    circle.remove();
                    cancel();
                    return;
                }
                if (handle.closeTicks >= 0) {
                    handle.closeTicks++;
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return handle;
    }

    private void finishQuantumGroundSigil(QuantumGroundSigilHandle handle) {
        if (handle == null || handle.closing) {
            return;
        }
        handle.closing = true;
    }

    private void removeQuantumGroundSigil(QuantumGroundSigilHandle handle) {
        if (handle == null) {
            return;
        }
        if (handle.task != null) {
            handle.task.cancel();
        }
        if (handle.display.isValid()) {
            handle.display.remove();
        }
    }

    private void preloadQuantumGroundSigil(Player player, double radius, int chargeTicks) {
        if (!player.isOnline()) {
            return;
        }
        Location ground = quantumGroundPlane(player.getLocation()).subtract(0, 0.22, 0);
        double fullAreaScale = Math.max(1.0, (radius * 2.0) / 3.0);
        ItemDisplay preload = spawnModel(ground, "vfx/summoning_circle", (float) (fullAreaScale * 0.018), Math.max(10, chargeTicks));
        preload.setBillboard(Display.Billboard.FIXED);
        preload.setBrightness(new Display.Brightness(0, 0));
    }

    private Location quantumGroundPlane(Location center) {
        World world = center.getWorld();
        int x = center.getBlockX();
        int z = center.getBlockZ();
        int startY = Math.min(world.getMaxHeight() - 1, center.getBlockY() + 2);
        int minY = Math.max(world.getMinHeight(), center.getBlockY() - 8);
        for (int y = startY; y >= minY; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (block.getType().isSolid() && !block.getType().isAir()) {
                return block.getLocation().add(0.5, 1.035, 0.5);
            }
        }
        return center.clone().add(0, 0.04, 0);
    }

    private int spawnQuantumCasterHud(Player player, WeaponType type, boolean ultimate, QuantumSigil sigil) {
        final int totalTicks = sigil.animationTicks();
        spawnQuantumActivationOverhead(player, sigil, totalTicks);
        spawnAbilityHud(player, type, ultimate);
        return totalTicks + 2;
    }

    private void spawnAbilityHud(Player player, WeaponType type, boolean ultimate) {
        UUID playerId = player.getUniqueId();
        int token = abilityHudTokens.getOrDefault(playerId, 0) + 1;
        abilityHudTokens.put(playerId, token);
        AbilityHudIcon icon = AbilityHudIcon.forAbility(type, ultimate);
        icon.playActivationSound(player);
        player.clearTitle();
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || abilityHudTokens.getOrDefault(playerId, -1) != token) {
                    cancel();
                    return;
                }
                if (ticks > icon.durationTicks()) {
                    abilityHudTokens.remove(playerId, token);
                    player.clearTitle();
                    cancel();
                    return;
                }
                Component glyph = Component.text(String.valueOf(icon.glyph(ticks))).font(ABILITY_HUD_FONT);
                player.showTitle(Title.title(glyph, Component.empty(), QUANTUM_HUD_TIMES));
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void animateWeaponNormalSwing(Player player, WeaponType type) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        long previousStart = quantumSwingStartTimes.getOrDefault(playerId, 0L);
        if (now - previousStart < 70L) {
            return;
        }
        quantumSwingStartTimes.put(playerId, now);
        int step = quantumSwingSteps.getOrDefault(playerId, 0);
        QuantumSwingStyle[] combo = swingCombo(type);
        QuantumSwingStyle style = combo[step % combo.length];
        QuantumSwingStyle lastStyle = quantumSwingLastStyles.get(playerId);
        if (ThreadLocalRandom.current().nextDouble() < 0.58) {
            style = QuantumSwingStyle.randomExcept(lastStyle == null ? style : lastStyle);
        }
        if (style == lastStyle) {
            style = style.next();
        }
        quantumSwingLastStyles.put(playerId, style);
        quantumSwingSteps.put(playerId, step + 1);

        BukkitTask oldTask = quantumSwingResetTasks.remove(playerId);
        if (oldTask != null) {
            oldTask.cancel();
        }
        QuantumSwingStyle selectedStyle = style;
        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    quantumSwingResetTasks.remove(playerId);
                    cancel();
                    return;
                }
                ItemStack item = player.getInventory().getItemInMainHand();
                if (itemFactory.peekType(item).orElse(null) != type) {
                    quantumSwingResetTasks.remove(playerId);
                    cancel();
                    return;
                }
                if (ticks == 0) {
                    setQuantumSwingModel(item, selectedStyle.windupModel(type));
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.34f, selectedStyle.windupPitch());
                } else if (ticks == 2) {
                    setQuantumSwingModel(item, selectedStyle.impactModel(type));
                    player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, player.getLocation().add(0, 1.0, 0)
                        .add(safeFlatDirection(player).multiply(1.1)), 1, 0.0, 0.0, 0.0, 0.0);
                    player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 0.22f, selectedStyle.impactPitch());
                } else if (ticks == 5) {
                    setQuantumSwingModel(item, selectedStyle.recoverModel(type));
                } else if (ticks >= 8) {
                    setQuantumSwingModel(item, type.id());
                    quantumSwingResetTasks.remove(playerId);
                    cancel();
                    return;
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        quantumSwingResetTasks.put(playerId, task);
    }

    private QuantumSwingStyle[] swingCombo(WeaponType type) {
        return switch (type) {
            case GOLDFANG_DAGGER -> new QuantumSwingStyle[] {
                QuantumSwingStyle.RIGHT,
                QuantumSwingStyle.LEFT,
                QuantumSwingStyle.RIGHT,
                QuantumSwingStyle.FRONT
            };
            case STORMBREAKER_RELIC, TIMBERLORD_AXE -> new QuantumSwingStyle[] {
                QuantumSwingStyle.FRONT,
                QuantumSwingStyle.RIGHT,
                QuantumSwingStyle.FRONT,
                QuantumSwingStyle.LEFT
            };
            case NECROMANCER_REAPER, VOIDGLASS_LICH_STAFF -> new QuantumSwingStyle[] {
                QuantumSwingStyle.LEFT,
                QuantumSwingStyle.FRONT,
                QuantumSwingStyle.RIGHT
            };
            case SANGUINE_PIKE -> new QuantumSwingStyle[] {
                QuantumSwingStyle.FRONT,
                QuantumSwingStyle.FRONT,
                QuantumSwingStyle.LEFT,
                QuantumSwingStyle.RIGHT
            };
            default -> DEFAULT_SWING_COMBO;
        };
    }

    private void setQuantumSwingModel(ItemStack item, String modelId) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        NamespacedKey modelKey = NamespacedKey.fromString("legendary:" + modelId);
        if (modelKey != null) {
            meta.setItemModel(modelKey);
            item.setItemMeta(meta);
        }
    }

    private boolean usesQuantumAbilities(WeaponType type) {
        return type == WeaponType.DRAKEFIRE_KATANA;
    }

    private void spawnQuantumActivationOverhead(Player player, QuantumSigil sigil, int totalTicks) {
        int enterTicks = Math.max(5, (int) Math.round(totalTicks * 0.22));
        int exitTicks = Math.max(7, (int) Math.round(totalTicks * 0.25));
        int exitStart = totalTicks - exitTicks;
        Vector forward = safeFlatDirection(player);
        float yaw = (float) Math.atan2(-forward.getX(), forward.getZ());
        ItemDisplay display = spawnModel(player.getLocation().add(0, 1.48, 0).add(forward.clone().multiply(0.42)), sigil.modelPath(), 0.04f, totalTicks + 12);
        display.setBillboard(Display.Billboard.FIXED);
        display.setBrightness(new Display.Brightness(13, 13));
        display.setPersistent(false);
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(1);
        display.setTeleportDuration(1);
        player.hideEntity(plugin, display);
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !display.isValid()) {
                    if (display.isValid()) {
                        display.remove();
                    }
                    cancel();
                    return;
                }

                if (ticks > totalTicks) {
                    display.remove();
                    cancel();
                    return;
                }

                double intro = Math.min(1.0, ticks / (double) enterTicks);
                double outro = ticks <= exitStart ? 0.0 : Math.min(1.0, (ticks - exitStart) / (double) exitTicks);
                double introEase = intro * intro * (3.0 - 2.0 * intro);
                double outroEase = outro * outro * (3.0 - 2.0 * outro);
                double visible = Math.max(0.0, Math.min(1.0, introEase * (1.0 - outroEase)));
                double eased = visible * visible * (3.0 - 2.0 * visible);
                Location target = player.getLocation()
                    .add(0, 1.48 + (sigil.height() - 1.48) * introEase + 0.78 * outroEase, 0)
                    .add(forward.clone().multiply(0.42));
                display.teleport(target);
                float scale = (float) (sigil.scale() * Math.max(0.02, eased));
                display.setTransformation(new Transformation(
                    new Vector3f(),
                    new Quaternionf(new AxisAngle4f(yaw, 0f, 1f, 0f)),
                    new Vector3f(scale, scale, scale),
                    new Quaternionf()
                ));

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void renderQuantumSigil(Player player, QuantumSigil sigil, int ticks) {
        Vector forward = safeFlatDirection(player);
        Vector right = rightVector(forward);
        Vector up = new Vector(0, 1, 0);
        Location center = player.getLocation().add(0, 2.95, 0).add(forward.multiply(0.42));
        double pulse = 1.0 + Math.sin(ticks * 0.18) * 0.035;
        if (sigil == QuantumSigil.BAT) {
            renderBatSigil(center, right, up, 0.86 * pulse);
            return;
        }
        renderWarriorSigil(center, right, up, 0.88 * pulse);
    }

    private void renderBatSigil(Location center, Vector right, Vector up, double scale) {
        World world = center.getWorld();
        int ellipsePoints = 84;
        for (int i = 0; i < ellipsePoints; i++) {
            double angle = Math.PI * 2.0 * i / ellipsePoints;
            plotSigilPoint(world, center, right, up, Math.cos(angle) * 1.42, Math.sin(angle) * 0.58, scale, BAT_OUTLINE_DUST);
        }

        double[][] outline = {
            {-1.20, 0.22}, {-1.02, 0.42}, {-0.72, 0.43}, {-0.52, 0.55}, {-0.42, 0.78},
            {-0.24, 0.30}, {-0.10, 0.82}, {0.00, 0.45}, {0.10, 0.82}, {0.24, 0.30},
            {0.42, 0.78}, {0.52, 0.55}, {0.72, 0.43}, {1.02, 0.42}, {1.20, 0.22},
            {0.96, -0.06}, {0.68, -0.18}, {0.50, -0.08}, {0.36, -0.38}, {0.18, -0.45},
            {0.00, -0.22}, {-0.18, -0.45}, {-0.36, -0.38}, {-0.50, -0.08}, {-0.68, -0.18},
            {-0.96, -0.06}, {-1.20, 0.22}
        };
        drawLocalPolyline(world, center, right, up, outline, scale, BAT_SHADOW_DUST, 0.055);

        double[][] fill = {
            {-0.94, 0.86, 0.22}, {-0.86, 0.86, 0.14}, {-0.78, 0.78, 0.06},
            {-0.66, 0.66, -0.04}, {-0.48, 0.48, -0.14}, {-0.24, 0.24, -0.25}
        };
        for (double[] segment : fill) {
            drawLocalLine(world, center, right, up, segment[0], segment[2], segment[1], segment[2], scale, BAT_SHADOW_DUST, 0.07);
        }
    }

    private void renderWarriorSigil(Location center, Vector right, Vector up, double scale) {
        World world = center.getWorld();
        for (int arm = 0; arm < 3; arm++) {
            double rotation = Math.PI * 2.0 * arm / 3.0;
            drawWarriorLoop(world, center, right, up, rotation, scale, WARRIOR_DUST, 64, 1.0);
            drawWarriorLoop(world, center, right, up, rotation, scale, WARRIOR_HIGHLIGHT_DUST, 42, 0.82);
        }
    }

    private void drawWarriorLoop(World world,
                                 Location center,
                                 Vector right,
                                 Vector up,
                                 double rotation,
                                 double scale,
                                 Particle.DustOptions dust,
                                 int steps,
                                 double size) {
        double cos = Math.cos(rotation);
        double sin = Math.sin(rotation);
        for (int i = 0; i <= steps; i++) {
            double t = Math.PI * 2.0 * i / steps;
            double localX = 0.33 * Math.sin(t) * size;
            double localY = (0.58 * Math.cos(t) + 0.30) * size;
            double x = localX * cos - localY * sin;
            double y = localX * sin + localY * cos;
            plotSigilPoint(world, center, right, up, x, y, scale, dust);
        }
    }

    private void drawLocalPolyline(World world,
                                   Location center,
                                   Vector right,
                                   Vector up,
                                   double[][] points,
                                   double scale,
                                   Particle.DustOptions dust,
                                   double spacing) {
        for (int i = 0; i < points.length - 1; i++) {
            drawLocalLine(world, center, right, up, points[i][0], points[i][1], points[i + 1][0], points[i + 1][1], scale, dust, spacing);
        }
    }

    private void drawLocalLine(World world,
                               Location center,
                               Vector right,
                               Vector up,
                               double x1,
                               double y1,
                               double x2,
                               double y2,
                               double scale,
                               Particle.DustOptions dust,
                               double spacing) {
        double distance = Math.hypot(x2 - x1, y2 - y1);
        int steps = Math.max(1, (int) Math.ceil(distance / spacing));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            plotSigilPoint(world, center, right, up, x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, scale, dust);
        }
    }

    private void plotSigilPoint(World world,
                                Location center,
                                Vector right,
                                Vector up,
                                double x,
                                double y,
                                double scale,
                                Particle.DustOptions dust) {
        Location point = center.clone()
            .add(right.clone().multiply(x * scale))
            .add(up.clone().multiply(y * scale));
        world.spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, dust);
    }

    private void raidLanes(Player player, WeaponType type, String model, Material mat, int passes, double width) {
        Vector dir = player.getLocation().getDirection().setY(0).normalize();
        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        Location start = targetPoint(player, 9);
        for (int i = 0; i < passes; i++) {
            Location lane = start.clone().add(right.clone().multiply((i - 1) * width));
            drawLane(lane, dir, 12, mat, 24 + i * 12);
            int delay = 24 + i * 16;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                spawnModel(lane.clone().add(0, 2.0, 0), model, 2.0f, 34);
                lineHit(player, type, lane, dir, 12, 2.1, damage(type, "ultimate-damage", 6.2), 1.0, type == WeaponType.DRAKEFIRE_KATANA ? 80 : 0);
            }, delay);
        }
    }

    private void heistZone(Player player, WeaponType type) {
        Location center = targetPoint(player, 12);
        telegraphBlock(center, Material.YELLOW_STAINED_GLASS, 6.0f, 40);
        spawnModel(center.clone().add(0, 1.0, 0), "vfx/ring", 2.6f, 140);
        for (int i = 0; i < 4; i++) {
            double angle = (Math.PI * 2 * i) / 4.0;
            Location statue = center.clone().add(Math.cos(angle) * 5, 0.8, Math.sin(angle) * 5);
            spawnModel(statue, "vfx/slash", 1.3f, 140);
            int delay = 30 + i * 18;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                spawnBurst(center.clone().add(0, 1.0, 0), "vfx/slash", 10, 3.5, 30);
                hitNearby(player, type, center, 5.2, damage(type, "ultimate-damage", 6.6), statue.toVector().subtract(center.toVector()).normalize(), 0.9, 0);
            }, delay);
        }
    }

    private void conveyorLane(Player player, WeaponType type) {
        Vector dir = player.getLocation().getDirection().setY(0).normalize();
        Location start = player.getLocation().add(dir.clone().multiply(4));
        drawLane(start, dir, 14, Material.RED_STAINED_GLASS, 130);
        spawnModel(start.clone().add(dir.clone().multiply(13)).add(0, 1.2, 0), "vfx/chains", 2.4f, 130);
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ > 90) {
                    cancel();
                    return;
                }
                lineHit(player, type, start, dir, 14, 2.1, damage(type, "ultimate-tick-damage", 1.0), 0.45, 0);
            }
        }.runTaskTimer(plugin, 20L, 8L);
    }

    private void mirrorPalace(Player player, WeaponType type) {
        Location center = targetPoint(player, 13);
        for (int i = 0; i < 4; i++) {
            double angle = (Math.PI * 2 * i) / 4.0;
            Location mirror = center.clone().add(Math.cos(angle) * 5, 1.4, Math.sin(angle) * 5);
            spawnModel(mirror, "vfx/frost", 1.8f, 130);
        }
        for (int i = 0; i < 5; i++) {
            int delay = 18 + i * 15;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                spawnBurst(center.clone().add(0, 1.0, 0), "vfx/frost", 8, 4.8, 28);
                hitNearby(player, type, center, 6.0, damage(type, "ultimate-damage", 5.4), new Vector(0, 0.5, 0), 0.6, 0);
                slowNearby(player, center, 6.0, 45, 2);
            }, delay);
        }
    }

    private void heavenbloomFestival(Player player, WeaponType type) {
        Location center = targetPoint(player, 12);
        spawnBlock(center.clone().add(0, 2.2, 0), Material.CHERRY_LOG, 3.0f, 170);
        for (int i = 0; i < 8; i++) {
            double angle = (Math.PI * 2 * i) / 8.0;
            spawnModel(center.clone().add(Math.cos(angle) * 4, 3.2, Math.sin(angle) * 4), "vfx/sakura", 1.6f, 170);
        }
        delayedAreaPulse(player, type, center, 7.0, 4, 24, "vfx/sakura", Material.PINK_STAINED_GLASS, 4.6, false);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 160, 1, true, true, true));
    }

    private void skyforgeJudgement(Player player, WeaponType type) {
        Location center = targetPoint(player, 16);
        telegraphBlock(center, Material.YELLOW_STAINED_GLASS, 6.0f, 44);
        spawnModel(center.clone().add(0, 8.0, 0), "vfx/lightning_storm", 2.8f, 48);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            spawnBurst(center.clone().add(0, 1.0, 0), "vfx/lightning", 12, 5.0, 40);
            hitNearby(player, type, center, 6.0, damage(type, "ultimate-damage", 8.2), new Vector(0, 1, 0), 1.25, 0);
        }, 42L);
    }

    private void impalementColosseum(Player player, WeaponType type) {
        Location center = targetPoint(player, 12);
        telegraphBlock(center, Material.RED_STAINED_GLASS, 7.5f, 46);
        for (int i = 0; i < 8; i++) {
            double angle = (Math.PI * 2 * i) / 8.0;
            Location spear = center.clone().add(Math.cos(angle) * 7, 1.0, Math.sin(angle) * 7);
            spawnModel(spear, "vfx/slash", 1.5f, 140);
        }
        delayedAreaPulse(player, type, center, 6.8, 3, 28, "vfx/slash", Material.RED_STAINED_GLASS, 6.2, false);
    }

    private void lichProcession(Player player, WeaponType type) {
        Vector dir = player.getLocation().getDirection().setY(0).normalize();
        Location start = targetPoint(player, 8);
        for (int i = 0; i < 4; i++) {
            Location coffin = start.clone().add(dir.clone().multiply(i * 2.5)).add(0, 1.0, 0);
            spawnModel(coffin, "vfx/coffin", 1.5f, 130);
        }
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ > 8) {
                    cancel();
                    return;
                }
                lineHit(player, type, start, dir, 12, 2.6, damage(type, "ultimate-damage", 4.5), 0.45, 0);
            }
        }.runTaskTimer(plugin, 22L, 10L);
    }

    private void celestialTribunal(Player player, WeaponType type) {
        Location center = targetPoint(player, 14);
        for (int i = 0; i < 6; i++) {
            double angle = (Math.PI * 2 * i) / 6.0;
            spawnModel(center.clone().add(Math.cos(angle) * 5, 1.8, Math.sin(angle) * 5), "vfx/bifrost", 1.6f, 130);
        }
        delayedAreaPulse(player, type, center, 6.0, 3, 30, "vfx/bifrost", Material.YELLOW_STAINED_GLASS, 5.8, false);
    }

    private void deathCarousel(Player player, WeaponType type) {
        Location center = targetPoint(player, 10);
        telegraphBlock(center, Material.PURPLE_STAINED_GLASS, 7.0f, 36);
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ > 7) {
                    cancel();
                    return;
                }
                spawnModel(center.clone().add(0, 1.3, 0), "vfx/slash", 2.3f, 20);
                hitNearby(player, type, center, 6.5, damage(type, "ultimate-tick-damage", 2.4), new Vector(0, 0.35, 0), 0.45, 0);
            }
        }.runTaskTimer(plugin, 22L, 10L);
    }

    private void walkingFortress(Player player, WeaponType type) {
        Location center = targetPoint(player, 10);
        for (int i = 0; i < 6; i++) {
            spawnBlock(center.clone().add((i % 3 - 1) * 1.4, 1.2 + i * 0.45, (i / 3) * 1.4 - 0.7), Material.OAK_LOG, 1.5f, 170);
        }
        delayedAreaPulse(player, type, center, 6.0, 4, 24, "vfx/flower", Material.LIME_STAINED_GLASS, 4.4, false);
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 170, 0, true, true, true));
    }

    private void gardenApocalypse(Player player, WeaponType type) {
        Location center = targetPoint(player, 15);
        telegraphBlock(center, Material.PINK_STAINED_GLASS, 7.0f, 32);
        for (int i = 0; i < 7; i++) {
            int delay = 18 + i * 8;
            double angle = (Math.PI * 2 * i) / 7.0;
            Location impact = center.clone().add(Math.cos(angle) * 4.5, 0, Math.sin(angle) * 4.5);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                spawnBurst(impact.clone().add(0, 1.0, 0), "vfx/flower", 7, 2.2, 34);
                hitNearby(player, type, impact, 3.0, damage(type, "ultimate-damage", 4.0), new Vector(0, 0.5, 0), 0.5, 0);
            }, delay);
        }
    }

    private void krakenMooring(Player player, WeaponType type) {
        Location center = targetPoint(player, 14);
        spawnModel(center.clone().add(0, 1.0, 0), "vfx/ring", 2.8f, 150);
        delayedAreaPulse(player, type, center, 7.0, 5, 20, "vfx/beam", Material.CYAN_STAINED_GLASS, 3.2, true);
    }

    private void crimsonRailshot(Player player, WeaponType type) {
        Vector dir = player.getLocation().getDirection().setY(0).normalize();
        Location start = player.getEyeLocation().add(dir.clone().multiply(3));
        drawLane(start, dir, 24, Material.RED_STAINED_GLASS, 34);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (int i = 0; i < 8; i++) {
                spawnModel(start.clone().add(dir.clone().multiply(i * 3.0)), "vfx/sonic_smash", 1.8f, 34);
            }
            lineHit(player, type, start, dir, 24, 2.8, damage(type, "ultimate-damage", 8.0), 1.7, 0);
        }, 32L);
    }

    private void delayedAreaPulse(Player player, WeaponType type, Location center, double radius, int pulses, int firstDelay, String model, Material telegraph, double dmg, boolean lift) {
        telegraphBlock(center, telegraph, (float) radius, firstDelay + pulses * 20 + 10);
        for (int i = 0; i < pulses; i++) {
            int delay = firstDelay + i * 20;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                spawnBurst(center.clone().add(0, 1.0, 0), model, 10, radius * 0.65, 34);
                if (player != null) {
                    hitNearby(player, type, center, radius, damage(type, "ultimate-damage", dmg), lift ? new Vector(0, 1, 0) : null, lift ? 0.75 : 0.45, 0);
                }
            }, delay);
        }
    }

    private void fireExtraHunterBolts(Player player, WeaponType type) {
        for (int i = -1; i <= 1; i += 2) {
            AbstractArrow arrow = player.launchProjectile(org.bukkit.entity.Arrow.class);
            Vector dir = player.getLocation().getDirection().clone();
            dir.rotateAroundY(i * 0.17);
            arrow.setVelocity(dir.multiply(2.75));
            projectileWeapons.put(arrow.getUniqueId(), type);
            manualProjectiles.add(arrow.getUniqueId());
            spawnTrail(arrow.getLocation(), "vfx/sonic", 0.8f, 24);
        }
    }

    private void fireModelProjectile(Player player, WeaponType type, String model, int range, double speed, double dmg, double knockback) {
        fireModelProjectile(player, type, model, range, speed, dmg, knockback, null, player.getLocation().getDirection());
    }

    private void fireModelProjectile(Player player, WeaponType type, String model, int range, double speed, double dmg, double knockback, HitAction action) {
        fireModelProjectile(player, type, model, range, speed, dmg, knockback, action, player.getLocation().getDirection());
    }

    private void fireModelProjectile(Player player, WeaponType type, String model, int range, double speed, double dmg, double knockback, HitAction action, Vector direction) {
        World world = player.getWorld();
        Vector dir = direction.clone().normalize();
        Location start = player.getEyeLocation().add(dir.clone().multiply(1.1));
        ItemDisplay display = spawnModel(start, model, 0.85f, range * 2 + 20);
        Set<UUID> hit = new HashSet<>();
        new BukkitRunnable() {
            int ticks = 0;
            Location loc = start.clone();
            @Override
            public void run() {
                if (ticks++ > range || !display.isValid()) {
                    display.remove();
                    cancel();
                    return;
                }
                loc.add(dir.clone().multiply(speed));
                display.teleport(loc);
                world.spawnParticle(Particle.DUST, loc, 1, 0.06, 0.06, 0.06, 0.0, new Particle.DustOptions(type.color(), 1.0f));
                for (LivingEntity target : nearbyEnemies(player, loc, 1.15)) {
                    if (!hit.add(target.getUniqueId())) {
                        continue;
                    }
                    dealAbilityDamage(player, target, dmg);
                    target.setVelocity(dir.clone().multiply(knockback).setY(0.25));
                    if (action != null) {
                        action.onHit(target);
                    }
                    spawnBurst(target.getLocation().add(0, 1.0, 0), model, 4, 0.9, 18);
                    if (hit.size() >= 2) {
                        display.remove();
                        cancel();
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void dealAbilityDamage(Player owner, LivingEntity target, double amount) {
        if (owner == null) {
            target.damage(amount);
            return;
        }
        forcedAbilityDamage.put(target.getUniqueId(), amount);
        try {
            target.damage(amount, owner);
        } finally {
            forcedAbilityDamage.remove(target.getUniqueId());
        }
    }

    private void hitNearby(Player owner, WeaponType type, Location center, double radius, double damage, Vector push, double knockback, int fireTicks) {
        for (LivingEntity target : nearbyEnemies(owner, center, radius)) {
            dealAbilityDamage(owner, target, damage);
            if (push != null && push.lengthSquared() > 0) {
                target.setVelocity(push.clone().normalize().multiply(knockback).setY(Math.max(0.2, push.getY() * knockback)));
            }
            if (fireTicks > 0) {
                target.setFireTicks(Math.max(target.getFireTicks(), fireTicks));
            }
            particleService.weaponBurst(target.getLocation().add(0, 1, 0), type);
        }
    }

    private void lineHit(Player owner, WeaponType type, Location start, Vector direction, double length, double width, double damage, double knockback, int fireTicks) {
        Vector dir = direction.clone().normalize();
        for (double d = 0; d <= length; d += 1.4) {
            Location point = start.clone().add(dir.clone().multiply(d));
            hitNearby(owner, type, point, width, damage, dir, knockback, fireTicks);
        }
    }

    private void slowNearby(Player owner, Location center, double radius, int ticks, int amplifier) {
        for (LivingEntity target : nearbyEnemies(owner, center, radius)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, amplifier, true, true, true));
        }
    }

    private void drawLane(Location start, Vector direction, int length, Material material, int lifetime) {
        Vector dir = direction.clone().setY(0).normalize();
        for (int i = 0; i < length; i++) {
            Location point = start.clone().add(dir.clone().multiply(i)).add(0, 0.05, 0);
            spawnBlock(point, material, 0.85f, lifetime);
        }
    }

    private void telegraphBlock(Location center, Material material, float radius, int lifetime) {
        int steps = Math.max(12, (int) (radius * 5));
        for (int i = 0; i < steps; i++) {
            double angle = Math.PI * 2 * i / steps;
            Location point = center.clone().add(Math.cos(angle) * radius, 0.08, Math.sin(angle) * radius);
            spawnBlock(point, material, 0.65f, lifetime);
        }
    }

    private void spawnCage(Location center, Material material, int lifetime) {
        double[][] offsets = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1.2, 0}};
        for (double[] offset : offsets) {
            spawnBlock(center.clone().add(offset[0], offset[1], offset[2]), material, 1.05f, lifetime);
        }
    }

    private void spawnBurst(Location center, String model, int count, double radius, int lifetime) {
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2 * i / count;
            double r = radius * (0.55 + (i % 3) * 0.18);
            spawnModel(center.clone().add(Math.cos(angle) * r, 0.2 + (i % 2) * 0.5, Math.sin(angle) * r), model, 0.75f + (i % 3) * 0.15f, lifetime);
        }
    }

    private void spawnTrail(Location start, String model, float scale, int lifetime) {
        spawnModel(start.clone().add(0, 0.6, 0), model, scale, lifetime);
    }

    private ItemDisplay spawnModel(Location location, String modelPath, float scale, int lifetime) {
        return spawnModelItemKey(location, "legendary:" + modelPath, scale, lifetime);
    }

    private ItemDisplay spawnModelItemKey(Location location, String itemModelKey, float scale, int lifetime) {
        ItemDisplay display = location.getWorld().spawn(location, ItemDisplay.class);
        trackRuntimeDisplay(display);
        display.setItemStack(modelItemKey(itemModelKey));
        display.setBillboard(Display.Billboard.CENTER);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        display.setBrightness(new Display.Brightness(15, 15));
        smoothDisplay(display);
        display.setTransformation(new Transformation(
            new Vector3f(),
            new Quaternionf(new AxisAngle4f(0f, 0f, 1f, 0f)),
            new Vector3f(scale, scale, scale),
            new Quaternionf()
        ));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (display.isValid()) {
                display.remove();
            }
            runtimeDisplays.remove(display);
        }, lifetime);
        return display;
    }

    private ItemDisplay spawnRedCubeModel(Location location, float scale, int lifetime) {
        ItemDisplay display = spawnModel(location, "vfx/red_cubes", scale, lifetime);
        applyQuantumRedLighting(display);
        return display;
    }

    private BlockDisplay spawnBlock(Location location, Material material, float scale, int lifetime) {
        BlockDisplay display = location.getWorld().spawn(location, BlockDisplay.class);
        trackRuntimeDisplay(display);
        display.setBlock(material.createBlockData());
        display.setBillboard(Display.Billboard.FIXED);
        display.setBrightness(new Display.Brightness(15, 15));
        smoothDisplay(display);
        display.setTransformation(new Transformation(
            new Vector3f(-scale / 2f, 0f, -scale / 2f),
            new Quaternionf(),
            new Vector3f(scale, 0.035f, scale),
            new Quaternionf()
        ));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (display.isValid()) {
                display.remove();
            }
            runtimeDisplays.remove(display);
        }, lifetime);
        return display;
    }

    private BlockDisplay spawnCubeBlock(Location location, Material material, float scale, int lifetime) {
        BlockDisplay display = location.getWorld().spawn(location, BlockDisplay.class);
        trackRuntimeDisplay(display);
        display.setBlock(material.createBlockData());
        display.setBillboard(Display.Billboard.FIXED);
        display.setBrightness(new Display.Brightness(15, 15));
        smoothDisplay(display);
        setCubeTransform(display, scale, 0f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (display.isValid()) {
                display.remove();
            }
            runtimeDisplays.remove(display);
        }, lifetime);
        return display;
    }

    private void trackRuntimeDisplay(Display display) {
        display.setPersistent(false);
        runtimeDisplays.add(display);
    }

    private void applyQuantumRedLighting(Display display) {
        display.setBrightness(new Display.Brightness(10, 10));
    }

    private void smoothDisplay(Display display) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(3);
        display.setTeleportDuration(3);
    }

    private void setCubeTransform(BlockDisplay display, float scale, float angle) {
        display.setTransformation(new Transformation(
            new Vector3f(-scale / 2f, -scale / 2f, -scale / 2f),
            new Quaternionf(new AxisAngle4f(angle, 0.25f, 1f, 0.15f)),
            new Vector3f(scale, scale, scale),
            new Quaternionf()
        ));
    }

    private ItemStack modelItem(String modelPath) {
        return modelItemKey("legendary:" + modelPath);
    }

    private ItemStack modelItemKey(String itemModelKey) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(NamespacedKey.fromString(itemModelKey));
        meta.setEnchantmentGlintOverride(false);
        stack.setItemMeta(meta);
        return stack;
    }

    private List<LivingEntity> nearbyEnemies(Player owner, Location center, double radius) {
        return center.getWorld().getNearbyLivingEntities(center, radius, radius, radius, entity ->
            !entity.getUniqueId().equals(owner.getUniqueId()) && !entity.isDead()).stream().toList();
    }

    private LivingEntity nearestEnemy(Player owner, Location center, double radius) {
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity entity : nearbyEnemies(owner, center, radius)) {
            double dist = entity.getLocation().distanceSquared(center);
            if (dist < bestDist) {
                bestDist = dist;
                best = entity;
            }
        }
        return best;
    }

    private Location targetPoint(Player player, double range) {
        RayTraceResult result = player.getWorld().rayTraceBlocks(player.getEyeLocation(), player.getEyeLocation().getDirection(), range);
        if (result != null && result.getHitPosition() != null) {
            return result.getHitPosition().toLocation(player.getWorld()).add(0, 0.08, 0);
        }
        return player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(range));
    }

    private QuantumAim quantumAim(Player player, double range) {
        Location eye = player.getEyeLocation();
        Vector direction = safeLookDirection(player);
        RayTraceResult result = player.getWorld().rayTraceBlocks(eye, direction, range, FluidCollisionMode.NEVER, true);
        if (result != null && result.getHitPosition() != null && result.getHitBlock() != null) {
            Location hit = result.getHitPosition().toLocation(player.getWorld());
            return new QuantumAim(hit, result.getHitBlock(), result.getHitBlockFace());
        }
        return new QuantumAim(eye.add(direction.multiply(range)), null, null);
    }

    private Location bezier(Location start, Location control, Location end, double t) {
        double inverse = 1.0 - t;
        World world = start.getWorld();
        double x = inverse * inverse * start.getX() + 2.0 * inverse * t * control.getX() + t * t * end.getX();
        double y = inverse * inverse * start.getY() + 2.0 * inverse * t * control.getY() + t * t * end.getY();
        double z = inverse * inverse * start.getZ() + 2.0 * inverse * t * control.getZ() + t * t * end.getZ();
        return new Location(world, x, y, z);
    }

    private Vector safeLookDirection(Player player) {
        Vector direction = player.getEyeLocation().getDirection();
        if (direction.lengthSquared() < 0.0001) {
            return safeFlatDirection(player);
        }
        return direction.normalize();
    }

    private Vector safeFlatDirection(Player player) {
        Vector direction = player.getLocation().getDirection().setY(0);
        if (direction.lengthSquared() < 0.0001) {
            return new Vector(0, 0, 1);
        }
        return direction.normalize();
    }

    private Vector rightVector(Vector forward) {
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        if (right.lengthSquared() < 0.0001) {
            return new Vector(1, 0, 0);
        }
        return right.normalize();
    }

    private Player resolvePlayerDamager(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private WeaponType resolveEnabledWeapon(ItemStack item) {
        WeaponType type = itemFactory.peekType(item).orElse(null);
        if (type == null || !configManager.isWeaponEnabled(type)) {
            return null;
        }
        return itemFactory.resolve(item).orElse(type);
    }

    private boolean beginCooldown(Player player, String key, int seconds, String label) {
        if (cooldownManager.isOnCooldown(player, key)) {
            long secondsLeft = Math.max(1L, cooldownManager.remainingMillis(player, key) / 1000L);
            messageService.action(player, "&c" + label + " ready in " + secondsLeft + "s");
            return false;
        }
        cooldownManager.startCooldown(player, key, seconds, label);
        return true;
    }

    private int cooldown(String key, int fallback) {
        return Math.max(1, configManager.cooldowns().getInt("cooldowns." + key, fallback));
    }

    private double damage(WeaponType type, String path, double fallback) {
        return configManager.weapons().getDouble("weapons." + type.id() + "." + path, fallback);
    }

    private double weaponDouble(WeaponType type, String path, double fallback) {
        return configManager.weapons().getDouble("weapons." + type.id() + "." + path, fallback);
    }

    private int weaponInt(WeaponType type, String path, int fallback) {
        return configManager.weapons().getInt("weapons." + type.id() + "." + path, fallback);
    }

    private void addCharge(Player player, WeaponType type) {
        int max = type == WeaponType.TEMPEST_SONICBOW ? 5 : 3;
        int next = Math.min(max, charges(player, type) + 1);
        setCharges(player, type, next);
        messageService.action(player, "&7" + type.displayName() + " charge &f" + next + "&7/&f" + max);
    }

    private int charges(Player player, WeaponType type) {
        return passiveCharges.computeIfAbsent(player.getUniqueId(), ignored -> new EnumMap<>(WeaponType.class)).getOrDefault(type, 0);
    }

    private void setCharges(Player player, WeaponType type, int value) {
        passiveCharges.computeIfAbsent(player.getUniqueId(), ignored -> new EnumMap<>(WeaponType.class)).put(type, value);
    }

    private void spendTempestCharges(Player player, WeaponType type, int amount) {
        if (type == WeaponType.TEMPEST_SONICBOW) {
            setCharges(player, type, Math.max(0, charges(player, type) - amount));
        }
    }

    private void applyPassiveOnHit(Player attacker, Entity victim, WeaponType type) {
        if (!(victim instanceof LivingEntity living)) {
            return;
        }
        switch (type) {
            case DRAKEFIRE_KATANA -> living.setFireTicks(Math.max(living.getFireTicks(), 45));
            case FROSTNOVA_CHAKRAM -> living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 45, 0, true, true, true));
            case BLOODCHAIN_RIPPER -> living.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 45, 0, true, true, true));
            case VOIDGLASS_LICH_STAFF, NECROMANCER_REAPER -> heal(attacker, 1.5);
            case TIMBERLORD_AXE -> attacker.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 60, 0, true, true, true));
            case BIFROST_WAND -> attacker.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 35, 0, true, true, true));
            case BLOOMSHOT_BLASTER -> living.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, true, true, true));
            default -> {
            }
        }
    }

    private void heal(Player player, double health) {
        player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + health));
    }

    private void playCastSound(Player player, WeaponType type, boolean ultimate) {
        float pitch = ultimate ? 0.72f : 1.22f;
        Sound sound = switch (type.barColor()) {
            case RED -> Sound.ENTITY_BLAZE_SHOOT;
            case BLUE -> Sound.BLOCK_AMETHYST_BLOCK_RESONATE;
            case GREEN -> Sound.BLOCK_AZALEA_LEAVES_STEP;
            case PURPLE -> Sound.ENTITY_ILLUSIONER_CAST_SPELL;
            default -> Sound.BLOCK_BEACON_POWER_SELECT;
        };
        player.getWorld().playSound(player.getLocation(), sound, ultimate ? 1.25f : 0.85f, pitch);
    }

    @FunctionalInterface
    private interface HitAction {
        void onHit(LivingEntity target);
    }

    private static final class QuantumArsenalState {
        private final List<QuantumChunk> chunks;
        private final List<RestoredBlock> restoredBlocks;
        private BukkitTask orbitTask;
        private boolean launched;
        private boolean restored;

        private QuantumArsenalState(List<QuantumChunk> chunks, List<RestoredBlock> restoredBlocks) {
            this.chunks = chunks;
            this.restoredBlocks = restoredBlocks;
        }
    }

    private static final class QuantumGroundSigilHandle {
        private final ItemDisplay display;
        private BukkitTask task;
        private boolean closing;
        private int closeTicks = -1;

        private QuantumGroundSigilHandle(ItemDisplay display) {
            this.display = display;
        }
    }

    private static final class QuantumAscensionState {
        private final Player player;
        private final WeaponType type;
        private final Location center;
        private final Location ground;
        private final double radius;
        private final int introTicks;
        private final int freezeTicks;
        private final List<QuantumOrbitCube> cubes = new ArrayList<>();
        private final Map<UUID, QuantumCapture> captures = new HashMap<>();
        private BukkitTask task;
        private QuantumGroundSigilHandle groundSigil;
        private boolean closing;

        private QuantumAscensionState(Player player,
                                      WeaponType type,
                                      Location center,
                                      Location ground,
                                      double radius,
                                      int introTicks,
                                      int freezeTicks) {
            this.player = player;
            this.type = type;
            this.center = center;
            this.ground = ground;
            this.radius = radius;
            this.introTicks = introTicks;
            this.freezeTicks = freezeTicks;
        }
    }

    private static final class QuantumOrbitCube {
        private final ItemDisplay display;
        private final float scale;
        private final double angleOffset;
        private boolean assigned;

        private QuantumOrbitCube(ItemDisplay display, float scale, double angleOffset) {
            this.display = display;
            this.scale = scale;
            this.angleOffset = angleOffset;
        }
    }

    private static final class QuantumCapture {
        private final LivingEntity target;
        private final QuantumOrbitCube cube;
        private final Location start;
        private QuantumCapturePhase phase = QuantumCapturePhase.APPROACH;
        private Location liftStart;
        private Location anchor;
        private int phaseTicks;

        private QuantumCapture(LivingEntity target, QuantumOrbitCube cube, Location start) {
            this.target = target;
            this.cube = cube;
            this.start = start;
        }
    }

    private record QuantumChunk(BlockDisplay display, BlockData blockData, float scale, double angleOffset) {}

    private record QuantumBlastDebris(BlockDisplay display, BlockData blockData, Vector velocity, float scale, double spinOffset) {}

    private record QuantumAim(Location location, Block hitBlock, BlockFace hitFace) {}

    private record RestoredBlock(Block block, BlockData blockData) {}

    private enum QuantumCapturePhase {
        APPROACH,
        ASCEND,
        HOLD
    }

    private enum QuantumSwingStyle {
        LEFT(
            "left_windup",
            "left_impact",
            "left_recover",
            0.72f,
            0.9f
        ),
        RIGHT(
            "right_windup",
            "right_impact",
            "right_recover",
            0.8f,
            1.02f
        ),
        FRONT(
            "front_windup",
            "front_impact",
            "front_recover",
            0.64f,
            0.78f
        );

        private final String windupSuffix;
        private final String impactSuffix;
        private final String recoverSuffix;
        private final float windupPitch;
        private final float impactPitch;

        QuantumSwingStyle(String windupSuffix,
                          String impactSuffix,
                          String recoverSuffix,
                          float windupPitch,
                          float impactPitch) {
            this.windupSuffix = windupSuffix;
            this.impactSuffix = impactSuffix;
            this.recoverSuffix = recoverSuffix;
            this.windupPitch = windupPitch;
            this.impactPitch = impactPitch;
        }

        private String windupModel(WeaponType type) {
            return swingModel(type, windupSuffix);
        }

        private String impactModel(WeaponType type) {
            return swingModel(type, impactSuffix);
        }

        private String recoverModel(WeaponType type) {
            return swingModel(type, recoverSuffix);
        }

        private String swingModel(WeaponType type, String suffix) {
            return type.id() + "_swing_" + suffix;
        }

        private float windupPitch() {
            return windupPitch;
        }

        private float impactPitch() {
            return impactPitch;
        }

        private QuantumSwingStyle next() {
            QuantumSwingStyle[] styles = values();
            return styles[(ordinal() + 1) % styles.length];
        }

        private static QuantumSwingStyle randomExcept(QuantumSwingStyle current) {
            QuantumSwingStyle[] styles = values();
            QuantumSwingStyle selected = styles[ThreadLocalRandom.current().nextInt(styles.length)];
            if (selected == current) {
                selected = styles[(selected.ordinal() + 1) % styles.length];
            }
            return selected;
        }
    }

    private enum QuantumSigil {
        BAT("vfx/bat_sigil", 1.55f, 3.12, 28),
        WARRIOR("vfx/warrior_sigil", 1.28f, 3.16, 40);

        private final String modelPath;
        private final float scale;
        private final double height;
        private final int animationTicks;

        QuantumSigil(String modelPath, float scale, double height, int animationTicks) {
            this.modelPath = modelPath;
            this.scale = scale;
            this.height = height;
            this.animationTicks = animationTicks;
        }

        private String modelPath() {
            return modelPath;
        }

        private float scale() {
            return scale;
        }

        private double height() {
            return height;
        }

        private int animationTicks() {
            return animationTicks;
        }

    }

    private enum AbilityHudIcon {
        QUANTUM_CHRONOBLADE_SIGNATURE(28),
        QUANTUM_CHRONOBLADE_ULTIMATE(30),
        DRAKEFIRE_KATANA_SIGNATURE(24),
        DRAKEFIRE_KATANA_ULTIMATE(26),
        GOLDFANG_DAGGER_SIGNATURE(16),
        GOLDFANG_DAGGER_ULTIMATE(24),
        BLOODCHAIN_RIPPER_SIGNATURE(22),
        BLOODCHAIN_RIPPER_ULTIMATE(24),
        FROSTNOVA_CHAKRAM_SIGNATURE(24),
        FROSTNOVA_CHAKRAM_ULTIMATE(26),
        PETALSTORM_FANBLADE_SIGNATURE(26),
        PETALSTORM_FANBLADE_ULTIMATE(28),
        STORMBREAKER_RELIC_SIGNATURE(18),
        STORMBREAKER_RELIC_ULTIMATE(22),
        SANGUINE_PIKE_SIGNATURE(18),
        SANGUINE_PIKE_ULTIMATE(24),
        VOIDGLASS_LICH_STAFF_SIGNATURE(24),
        VOIDGLASS_LICH_STAFF_ULTIMATE(26),
        BIFROST_WAND_SIGNATURE(24),
        BIFROST_WAND_ULTIMATE(26),
        NECROMANCER_REAPER_SIGNATURE(20),
        NECROMANCER_REAPER_ULTIMATE(28),
        TIMBERLORD_AXE_SIGNATURE(22),
        TIMBERLORD_AXE_ULTIMATE(28),
        BLOOMSHOT_BLASTER_SIGNATURE(24),
        BLOOMSHOT_BLASTER_ULTIMATE(28),
        HORNHOOK_HARPOON_SIGNATURE(18),
        HORNHOOK_HARPOON_ULTIMATE(26),
        TEMPEST_SONICBOW_SIGNATURE(22),
        TEMPEST_SONICBOW_ULTIMATE(18);

        private final int durationTicks;

        AbilityHudIcon(int durationTicks) {
            this.durationTicks = durationTicks;
        }

        private static AbilityHudIcon forAbility(WeaponType type, boolean ultimate) {
            if (type == WeaponType.DRAKEFIRE_KATANA) {
                return ultimate ? QUANTUM_CHRONOBLADE_ULTIMATE : QUANTUM_CHRONOBLADE_SIGNATURE;
            }
            if (type == WeaponType.QUANTUM_CHRONOBLADE) {
                return ultimate ? DRAKEFIRE_KATANA_ULTIMATE : DRAKEFIRE_KATANA_SIGNATURE;
            }
            return values()[type.ordinal() * 2 + (ultimate ? 1 : 0)];
        }

        private char glyph(int ticks) {
            int phase = Math.min(ABILITY_HUD_MAX_TICKS, Math.max(0, ticks));
            return (char) (ABILITY_HUD_GLYPH_BASE + ordinal() * (ABILITY_HUD_MAX_TICKS + 1) + phase);
        }

        private int durationTicks() {
            return durationTicks;
        }

        private void playActivationSound(Player player) {
            player.playSound(player.getLocation(), activationSound(), activationVolume(), activationPitch());
            if (name().endsWith("_ULTIMATE")) {
                player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.32f, Math.max(0.62f, activationPitch() - 0.18f));
            }
        }

        private Sound activationSound() {
            return switch (this) {
                case QUANTUM_CHRONOBLADE_SIGNATURE -> Sound.ITEM_TOTEM_USE;
                case QUANTUM_CHRONOBLADE_ULTIMATE -> Sound.ENTITY_WARDEN_SONIC_BOOM;
                case DRAKEFIRE_KATANA_SIGNATURE -> Sound.ENTITY_BLAZE_SHOOT;
                case DRAKEFIRE_KATANA_ULTIMATE -> Sound.ENTITY_ENDER_DRAGON_GROWL;
                case GOLDFANG_DAGGER_SIGNATURE -> Sound.BLOCK_AMETHYST_BLOCK_CHIME;
                case GOLDFANG_DAGGER_ULTIMATE -> Sound.BLOCK_BEACON_ACTIVATE;
                case BLOODCHAIN_RIPPER_SIGNATURE -> Sound.BLOCK_ANVIL_USE;
                case BLOODCHAIN_RIPPER_ULTIMATE -> Sound.ENTITY_WITHER_SPAWN;
                case FROSTNOVA_CHAKRAM_SIGNATURE -> Sound.BLOCK_AMETHYST_BLOCK_RESONATE;
                case FROSTNOVA_CHAKRAM_ULTIMATE -> Sound.BLOCK_RESPAWN_ANCHOR_CHARGE;
                case PETALSTORM_FANBLADE_SIGNATURE -> Sound.BLOCK_AZALEA_LEAVES_STEP;
                case PETALSTORM_FANBLADE_ULTIMATE -> Sound.ENTITY_ILLUSIONER_CAST_SPELL;
                case STORMBREAKER_RELIC_SIGNATURE -> Sound.ITEM_TRIDENT_THUNDER;
                case STORMBREAKER_RELIC_ULTIMATE -> Sound.ENTITY_LIGHTNING_BOLT_THUNDER;
                case SANGUINE_PIKE_SIGNATURE -> Sound.ITEM_TRIDENT_THROW;
                case SANGUINE_PIKE_ULTIMATE -> Sound.BLOCK_RESPAWN_ANCHOR_CHARGE;
                case VOIDGLASS_LICH_STAFF_SIGNATURE -> Sound.ENTITY_ENDERMAN_TELEPORT;
                case VOIDGLASS_LICH_STAFF_ULTIMATE -> Sound.ENTITY_WITHER_SPAWN;
                case BIFROST_WAND_SIGNATURE -> Sound.BLOCK_BEACON_POWER_SELECT;
                case BIFROST_WAND_ULTIMATE -> Sound.BLOCK_BEACON_ACTIVATE;
                case NECROMANCER_REAPER_SIGNATURE -> Sound.ENTITY_ILLUSIONER_CAST_SPELL;
                case NECROMANCER_REAPER_ULTIMATE -> Sound.ENTITY_WITHER_SPAWN;
                case TIMBERLORD_AXE_SIGNATURE -> Sound.BLOCK_ROOTED_DIRT_BREAK;
                case TIMBERLORD_AXE_ULTIMATE -> Sound.BLOCK_ANVIL_USE;
                case BLOOMSHOT_BLASTER_SIGNATURE -> Sound.BLOCK_AZALEA_LEAVES_STEP;
                case BLOOMSHOT_BLASTER_ULTIMATE -> Sound.ITEM_TOTEM_USE;
                case HORNHOOK_HARPOON_SIGNATURE -> Sound.ENTITY_SHULKER_SHOOT;
                case HORNHOOK_HARPOON_ULTIMATE -> Sound.ITEM_TRIDENT_THROW;
                case TEMPEST_SONICBOW_SIGNATURE -> Sound.BLOCK_AMETHYST_BLOCK_CHIME;
                case TEMPEST_SONICBOW_ULTIMATE -> Sound.ENTITY_WARDEN_SONIC_BOOM;
            };
        }

        private float activationPitch() {
            return switch (this) {
                case QUANTUM_CHRONOBLADE_SIGNATURE -> 0.78f;
                case QUANTUM_CHRONOBLADE_ULTIMATE -> 0.68f;
                case DRAKEFIRE_KATANA_SIGNATURE -> 1.35f;
                case DRAKEFIRE_KATANA_ULTIMATE -> 0.72f;
                case GOLDFANG_DAGGER_SIGNATURE -> 1.62f;
                case GOLDFANG_DAGGER_ULTIMATE -> 1.12f;
                case BLOODCHAIN_RIPPER_SIGNATURE -> 0.86f;
                case BLOODCHAIN_RIPPER_ULTIMATE -> 0.62f;
                case FROSTNOVA_CHAKRAM_SIGNATURE -> 1.45f;
                case FROSTNOVA_CHAKRAM_ULTIMATE -> 0.9f;
                case PETALSTORM_FANBLADE_SIGNATURE -> 1.28f;
                case PETALSTORM_FANBLADE_ULTIMATE -> 1.08f;
                case STORMBREAKER_RELIC_SIGNATURE -> 1.15f;
                case STORMBREAKER_RELIC_ULTIMATE -> 0.76f;
                case SANGUINE_PIKE_SIGNATURE -> 1.24f;
                case SANGUINE_PIKE_ULTIMATE -> 0.72f;
                case VOIDGLASS_LICH_STAFF_SIGNATURE -> 0.82f;
                case VOIDGLASS_LICH_STAFF_ULTIMATE -> 0.58f;
                case BIFROST_WAND_SIGNATURE -> 1.45f;
                case BIFROST_WAND_ULTIMATE -> 0.92f;
                case NECROMANCER_REAPER_SIGNATURE -> 0.7f;
                case NECROMANCER_REAPER_ULTIMATE -> 0.54f;
                case TIMBERLORD_AXE_SIGNATURE -> 0.74f;
                case TIMBERLORD_AXE_ULTIMATE -> 0.64f;
                case BLOOMSHOT_BLASTER_SIGNATURE -> 1.4f;
                case BLOOMSHOT_BLASTER_ULTIMATE -> 0.96f;
                case HORNHOOK_HARPOON_SIGNATURE -> 1.05f;
                case HORNHOOK_HARPOON_ULTIMATE -> 0.78f;
                case TEMPEST_SONICBOW_SIGNATURE -> 1.7f;
                case TEMPEST_SONICBOW_ULTIMATE -> 1.02f;
            };
        }

        private float activationVolume() {
            return name().endsWith("_ULTIMATE") ? 0.62f : 0.48f;
        }
    }
}
