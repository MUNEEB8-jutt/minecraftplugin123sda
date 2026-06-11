package com.legendaryweaponssmp.weapons;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum WeaponType {
    QUANTUM_CHRONOBLADE("quantum_chronoblade", "Quantum Chronoblade", Material.DIAMOND_SWORD, 101,
        "Aerial Dragon Duelist", "WYRMRIDE CUT", "RED DRAGON RAID",
        "civilization:item/tools/chrono_sword/engineers", Color.fromRGB(24, 18, 42), BarColor.PURPLE),
    DRAKEFIRE_KATANA("drakefire_katana", "Drakefire Katana", Material.DIAMOND_SWORD, 102,
        "Gravity Construct Duelist", "GRAVITY ARSENAL", "SINGULARITY ASCENSION",
        "civilization:item/tools/dragon_katana/dragonbreath", Color.fromRGB(235, 82, 30), BarColor.RED),
    GOLDFANG_DAGGER("goldfang_dagger", "Goldfang Dagger", Material.DIAMOND_SWORD, 103,
        "Heist Assassin", "GILDED BLADE RUSH", "GRAND HEIST",
        "civilization:item/tools/looter_dagger/prestiged", Color.fromRGB(245, 196, 64), BarColor.YELLOW),
    BLOODCHAIN_RIPPER("bloodchain_ripper", "Bloodchain Ripper", Material.DIAMOND_SWORD, 104,
        "Mechanical Capture Bruiser", "CHAINSAW REEL", "SLAUGHTER CONVEYOR",
        "civilization:item/tools/crimson_chainsword/zombie_on", Color.fromRGB(180, 18, 24), BarColor.RED),
    FROSTNOVA_CHAKRAM("frostnova_chakram", "Frostnova Chakram", Material.DIAMOND_SWORD, 105,
        "Ice Construct Controller", "COMMAND OF ICE", "GLACIER MIRROR PALACE",
        "civilization:item/tools/freezing_chakram/starfall", Color.fromRGB(120, 220, 255), BarColor.BLUE),
    PETALSTORM_FANBLADE("petalstorm_fanblade", "Petalstorm Fanblade", Material.DIAMOND_SWORD, 106,
        "Aerial Formation Support", "KOI DRAGON DANCE", "HEAVENBLOOM FESTIVAL",
        "civilization:item/tools/sakura_tessen/on", Color.fromRGB(245, 154, 205), BarColor.PINK),
    STORMBREAKER_RELIC("stormbreaker_relic", "Stormbreaker Relic", Material.DIAMOND_AXE, 107,
        "Thunder Impact Juggernaut", "THUNDER VAULT", "JUDGEMENT OF THE SKYFORGE",
        "civilization:item/tools/mjolnir/shattered", Color.fromRGB(255, 224, 68), BarColor.YELLOW),
    SANGUINE_PIKE("sanguine_pike", "Sanguine Pike", Material.TRIDENT, 108,
        "Crimson Lancer", "CRIMSON JOUST", "IMPALEMENT COLOSSEUM",
        "zombies:item/tools/sanguine_spear/default", Color.fromRGB(160, 10, 38), BarColor.RED),
    VOIDGLASS_LICH_STAFF("voidglass_lich_staff", "Voidglass Lich Staff", Material.DIAMOND_SWORD, 109,
        "Undead Siege Controller", "COFFIN SNATCH", "LICH KING PROCESSION",
        "civilization:item/tools/lich_staff/timeloss", Color.fromRGB(72, 224, 136), BarColor.GREEN),
    BIFROST_WAND("bifrost_wand", "Bifrost Wand", Material.DIAMOND_SWORD, 110,
        "Prism Architect / Capture Support", "PRISM CELL", "CELESTIAL TRIBUNAL",
        "civilization:item/tools/chrono_sword/engineers", Color.fromRGB(250, 220, 80), BarColor.YELLOW),
    NECROMANCER_REAPER("necromancer_reaper", "Necromancer Reaper", Material.DIAMOND_HOE, 111,
        "Execution Scythe / Death Arena", "SOUL SEVER", "DEATH CAROUSEL",
        "civilization:item/tools/reaper_scythe/necromancer", Color.fromRGB(96, 230, 214), BarColor.PURPLE),
    TIMBERLORD_AXE("timberlord_axe", "Timberlord Axe", Material.DIAMOND_AXE, 112,
        "Living Siege Architect", "BEASTWOOD RAM", "WALKING FORTRESS",
        "civilization:item/tools/lumberjack_axe", Color.fromRGB(122, 176, 82), BarColor.GREEN),
    BLOOMSHOT_BLASTER("bloomshot_blaster", "Bloomshot Blaster", Material.BOW, 21,
        "Living Garden Artillery", "VENUS VOLLEY", "GARDEN APOCALYPSE",
        "civilization:item/tools/beehive_blaster/flower_power/default", Color.fromRGB(226, 124, 206), BarColor.PINK),
    HORNHOOK_HARPOON("hornhook_harpoon", "Hornhook Harpoon", Material.BOW, 24,
        "Kraken Grapple Controller", "MONSTER LINE", "KRAKEN MOORING",
        "civilization:item/tools/harpoon_launcher/horn/default", Color.fromRGB(44, 178, 188), BarColor.BLUE),
    TEMPEST_SONICBOW("tempest_sonicbow", "Tempest Sonicbow", Material.CROSSBOW, 22,
        "Overdrive Railgun / Sonic Hunt", "TRIPLE HUNTER SHOT", "CRIMSON RAILSHOT",
        "civilization:item/tools/sonic_crossbow/breezy/default", Color.fromRGB(72, 232, 255), BarColor.BLUE);

    private final String id;
    private final String displayName;
    private final Material material;
    private final int modelData;
    private final String role;
    private final String signatureAbility;
    private final String ultimateAbility;
    private final String modelPath;
    private final Color color;
    private final BarColor barColor;

    WeaponType(String id,
               String displayName,
               Material material,
               int modelData,
               String role,
               String signatureAbility,
               String ultimateAbility,
               String modelPath,
               Color color,
               BarColor barColor) {
        this.id = id;
        this.displayName = displayName;
        this.material = material;
        this.modelData = modelData;
        this.role = role;
        this.signatureAbility = signatureAbility;
        this.ultimateAbility = ultimateAbility;
        this.modelPath = modelPath;
        this.color = color;
        this.barColor = barColor;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public Material material() {
        return material;
    }

    public int modelData() {
        return modelData;
    }

    public String role() {
        return role;
    }

    public String signatureAbility() {
        return signatureAbility;
    }

    public String ultimateAbility() {
        return ultimateAbility;
    }

    public String modelPath() {
        return modelPath;
    }

    public Color color() {
        return color;
    }

    public BarColor barColor() {
        return barColor;
    }

    public String leftCooldownKey() {
        return id + "_left";
    }

    public String rightCooldownKey() {
        return id + "_right";
    }

    public boolean isRanged() {
        return material == Material.BOW || material == Material.CROSSBOW;
    }

    public static Optional<WeaponType> byId(String id) {
        String normalized = id.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(v -> v.id.equals(normalized)).findFirst();
    }
}
