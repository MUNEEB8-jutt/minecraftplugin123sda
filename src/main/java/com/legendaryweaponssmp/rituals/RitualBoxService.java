package com.legendaryweaponssmp.rituals;

import com.legendaryweaponssmp.config.ConfigManager;
import com.legendaryweaponssmp.weapons.WeaponType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.Keyed;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class RitualBoxService implements Listener {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final NamespacedKey boxKey;
    private final NamespacedKey weaponKey;
    private final String recipePrefix;
    private RitualManager ritualManager;
    private volatile boolean craftingLocked;
    private volatile boolean coreRecipeEnabled = false;
    private final Map<WeaponType, Boolean> recipeEnabledByType = new EnumMap<>(WeaponType.class);

    public RitualBoxService(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.boxKey = new NamespacedKey(plugin, "legendary_ritual_box");
        this.weaponKey = new NamespacedKey(plugin, "legendary_ritual_weapon");
        this.recipePrefix = "legendary_ritual_core_";
        for (WeaponType type : WeaponType.values()) {
            recipeEnabledByType.put(type, configManager.isWeaponEnabled(type));
        }
    }

    public void attachRitualManager(RitualManager ritualManager) {
        this.ritualManager = ritualManager;
    }

    public void registerRecipe() {
        for (WeaponType type : WeaponType.values()) {
            NamespacedKey recipeKey = recipeKey(type);
            Bukkit.removeRecipe(recipeKey);
        }
    }

    public ItemStack createItem(WeaponType type) {
        ItemStack item = new ItemStack(Material.RESPAWN_ANCHOR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(type.displayName() + " Ritual Core", NamedTextColor.LIGHT_PURPLE));
        meta.lore(List.of(
            Component.text("Used to initiate " + type.displayName() + " ritual", NamedTextColor.GRAY),
            Component.text("Only one Ritual Core can exist server-wide", NamedTextColor.DARK_GRAY),
            Component.text("Place to begin " + type.displayName() + " ritual", NamedTextColor.AQUA)
        ));
        meta.setItemModel(NamespacedKey.fromString("legendary:ritual_core"));
        meta.setEnchantmentGlintOverride(true);
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(boxKey, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(weaponKey, PersistentDataType.STRING, type.id());
        item.setItemMeta(meta);
        return item;
    }

    public boolean isRitualBox(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        Boolean value = item.getItemMeta().getPersistentDataContainer().get(boxKey, PersistentDataType.BOOLEAN);
        return Boolean.TRUE.equals(value);
    }

    public Optional<WeaponType> resolveCoreType(ItemStack item) {
        if (!isRitualBox(item)) {
            return Optional.empty();
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(weaponKey, PersistentDataType.STRING);
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return WeaponType.byId(id).filter(configManager::isWeaponEnabled);
    }

    public boolean consumeOne(Player player, WeaponType type) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (resolveCoreType(main).orElse(null) == type) {
            decrement(player, true, main);
            return true;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (resolveCoreType(off).orElse(null) == type) {
            decrement(player, false, off);
            return true;
        }
        return false;
    }

    private void decrement(Player player, boolean mainHand, ItemStack stack) {
        stack.setAmount(stack.getAmount() - 1);
        if (stack.getAmount() <= 0) {
            if (mainHand) {
                player.getInventory().setItemInMainHand(null);
            } else {
                player.getInventory().setItemInOffHand(null);
            }
        } else if (mainHand) {
            player.getInventory().setItemInMainHand(stack);
        } else {
            player.getInventory().setItemInOffHand(stack);
        }
    }

    public void lockCrafting() {
        this.craftingLocked = true;
    }

    public void unlockCrafting() {
        this.craftingLocked = false;
    }

    public boolean isCraftingLocked() {
        return craftingLocked;
    }

    public void setCoreRecipeEnabled(boolean enabled) {
        this.coreRecipeEnabled = false;
    }

    public boolean isCoreRecipeEnabled() {
        return false;
    }

    public void setRecipeEnabled(WeaponType type, boolean enabled) {
        recipeEnabledByType.put(type, enabled && configManager.isWeaponEnabled(type));
    }

    public boolean isRecipeEnabled(WeaponType type) {
        return configManager.isWeaponEnabled(type) && recipeEnabledByType.getOrDefault(type, true);
    }

    public List<WeaponType> enabledRecipeTypes() {
        return List.of();
    }

    public boolean canCraftCore() {
        return false;
    }

    public boolean canCraftCore(WeaponType type) {
        return false;
    }

    public Optional<String> craftBlockReason(WeaponType type) {
        return Optional.of("Ritual core recipes are disabled.");
    }

    private boolean hasCoreOnServer() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (containsCore(player.getInventory().getContents())
                || containsCore(player.getInventory().getArmorContents())
                || isRitualBox(player.getInventory().getItemInOffHand())
                || isRitualBox(player.getInventory().getItemInMainHand())) {
                return true;
            }
        }
        for (World world : Bukkit.getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                if (isRitualBox(item.getItemStack())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsCore(ItemStack[] stacks) {
        if (stacks == null) {
            return false;
        }
        for (ItemStack stack : stacks) {
            if (isRitualBox(stack)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        WeaponType type = coreTypeFromRecipe(recipe).orElse(null);
        if (type == null) {
            return;
        }
        if (!canCraftCore(type)) {
            event.getInventory().setResult(null);
            return;
        }
        event.getInventory().setResult(createItem(type));
    }
    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        Recipe recipe = event.getRecipe();
        WeaponType type = coreTypeFromRecipe(recipe).orElse(null);
        if (type == null) {
            return;
        }
        if (!canCraftCore(type)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                String reason = craftBlockReason(type).orElse("Cannot craft this Ritual Core right now.");
                player.sendMessage("\u00a7c" + reason);
            }
            return;
        }
        if (event.getCurrentItem() != null) {
            event.setCurrentItem(createItem(type));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        boolean currentCore = isRitualBox(current);
        boolean cursorCore = isRitualBox(cursor);
        if (!currentCore && !cursorCore) {
            return;
        }
        boolean blockedDestination = isBlockedInventory(event.getClickedInventory())
            || isBlockedInventory(event.getView().getTopInventory());
        if (blockedDestination || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.sendMessage("\u00a7cRitual Cores cannot be stored in Ender Chests or Shulker Boxes.");
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!isRitualBox(event.getOldCursor())) {
            return;
        }
        if (isBlockedInventory(event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        if (!isRitualBox(event.getItem())) {
            return;
        }
        if (isBlockedInventory(event.getDestination())) {
            event.setCancelled(true);
        }
    }

    private boolean isBlockedInventory(org.bukkit.inventory.Inventory inventory) {
        if (inventory == null) {
            return false;
        }
        if (inventory.getType() == InventoryType.ENDER_CHEST) {
            return true;
        }
        return inventory.getType().name().contains("SHULKER_BOX");
    }

    private NamespacedKey recipeKey(WeaponType type) {
        return new NamespacedKey(plugin, recipePrefix + type.id() + "_recipe");
    }

    public Map<Integer, Material> recipeGrid(WeaponType type) {
        RecipeShape def = recipeDefinition(type);
        Map<Integer, Material> grid = new HashMap<>();
        forEachRecipeSlot(def, (slot, token, material) -> grid.put(slot, material));
        return grid;
    }

    public Map<Integer, RitualOffering> offeringGrid(WeaponType type) {
        RecipeShape def = recipeDefinition(type);
        Map<Integer, RitualOffering> grid = new HashMap<>();
        forEachRecipeSlot(def, (slot, token, material) -> {
            int amount = Math.max(1, def.amounts().getOrDefault(token, defaultAmountFor(material)));
            grid.put(slot, new RitualOffering(material, amount));
        });
        return grid;
    }

    private void forEachRecipeSlot(RecipeShape def, RecipeSlotConsumer consumer) {
        char[][] rows = new char[][]{
            def.row1().toCharArray(),
            def.row2().toCharArray(),
            def.row3().toCharArray()
        };
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                char token = rows[r][c];
                Material mat = def.ingredients().get(token);
                if (mat != null) {
                    consumer.accept((r * 3) + c, token, mat);
                }
            }
        }
    }

    private Optional<WeaponType> coreTypeFromRecipe(Recipe recipe) {
        if (!(recipe instanceof Keyed keyed)) {
            return Optional.empty();
        }
        String key = keyed.getKey().getKey();
        if (key == null || !key.startsWith(recipePrefix) || !key.endsWith("_recipe")) {
            return Optional.empty();
        }
        String typeId = key.substring(recipePrefix.length(), key.length() - "_recipe".length());
        return WeaponType.byId(typeId);
    }

    private RecipeShape recipeDefinition(WeaponType type) {
        RecipeShape configured = recipeDefinitionFromConfig(type).orElse(null);
        if (configured != null) {
            return configured;
        }
        return defaultRecipeDefinition(type);
    }

    private Optional<RecipeShape> recipeDefinitionFromConfig(WeaponType type) {
        ConfigurationSection root = configManager.recipes().getConfigurationSection("recipes." + type.id());
        if (root == null) {
            return Optional.empty();
        }
        List<String> shape = root.getStringList("shape");
        if (shape.size() != 3) {
            plugin.getLogger().warning("Invalid recipes.yml shape for " + type.id() + " (need 3 rows). Using fallback.");
            return Optional.empty();
        }
        String row1 = shape.get(0);
        String row2 = shape.get(1);
        String row3 = shape.get(2);
        if (row1.length() != 3 || row2.length() != 3 || row3.length() != 3) {
            plugin.getLogger().warning("Invalid recipes.yml row length for " + type.id() + " (each row must be 3 chars). Using fallback.");
            return Optional.empty();
        }

        ConfigurationSection ingredientsSection = root.getConfigurationSection("ingredients");
        if (ingredientsSection == null) {
            plugin.getLogger().warning("Missing recipes.yml ingredients for " + type.id() + ". Using fallback.");
            return Optional.empty();
        }

        Set<Character> required = new HashSet<>();
        String all = row1 + row2 + row3;
        for (int i = 0; i < all.length(); i++) {
            char token = all.charAt(i);
            if (token != ' ') {
                required.add(token);
            }
        }

        Map<Character, Material> ingredients = new HashMap<>();
        Map<Character, Integer> amounts = new HashMap<>();
        ConfigurationSection amountsSection = root.getConfigurationSection("amounts");
        for (char token : required) {
            String key = String.valueOf(token);
            String materialId = ingredientsSection.getString(key);
            if (materialId == null) {
                materialId = ingredientsSection.getString(key.toLowerCase(Locale.ROOT));
            }
            Material material = parseMaterialId(materialId);
            if (material == null) {
                plugin.getLogger().warning("Invalid material id in recipes.yml for " + type.id() + " token '" + token + "': " + materialId + ". Using fallback.");
                return Optional.empty();
            }
            ingredients.put(token, material);
            int amount = defaultAmountFor(material);
            if (amountsSection != null) {
                amount = amountsSection.getInt(key, amountsSection.getInt(key.toLowerCase(Locale.ROOT), amount));
            }
            amounts.put(token, Math.max(1, Math.min(64, amount)));
        }
        return Optional.of(new RecipeShape(row1, row2, row3, ingredients, amounts));
    }

    private Material parseMaterialId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        Material material = Material.matchMaterial(normalized);
        if (material == null && normalized.startsWith("minecraft:")) {
            material = Material.matchMaterial(normalized.substring("minecraft:".length()));
        }
        return material;
    }

    private RecipeShape defaultRecipeDefinition(WeaponType type) {
        Map<Character, Material> ingredients = mapOf('E', Material.ECHO_SHARD, 'S', Material.AMETHYST_SHARD, 'G', Material.GOLD_BLOCK, 'N', Material.NETHER_STAR);
        return new RecipeShape("ESE", "GNG", "ESE", ingredients, amountsFor(ingredients));
    }

    private Map<Character, Material> mapOf(
        char a, Material ma,
        char b, Material mb,
        char c, Material mc,
        char d, Material md
    ) {
        Map<Character, Material> map = new HashMap<>();
        map.put(a, ma);
        map.put(b, mb);
        map.put(c, mc);
        map.put(d, md);
        return map;
    }

    private Map<Character, Integer> amountsFor(Map<Character, Material> ingredients) {
        Map<Character, Integer> amounts = new HashMap<>();
        for (Map.Entry<Character, Material> entry : ingredients.entrySet()) {
            amounts.put(entry.getKey(), defaultAmountFor(entry.getValue()));
        }
        return amounts;
    }

    private int defaultAmountFor(Material material) {
        return switch (material) {
            case NETHER_STAR -> 3;
            case TRIAL_KEY, TRIDENT, WARDEN_SPAWN_EGG -> 2;
            case ECHO_SHARD, ENDER_EYE, DIAMOND -> 10;
            case OBSIDIAN, CRYING_OBSIDIAN, FIRE_CHARGE -> 12;
            case AMETHYST_SHARD, BLAZE_ROD, PRISMARINE_SHARD, LIGHTNING_ROD -> 16;
            case GOLD_BLOCK, IRON_BLOCK, REDSTONE_BLOCK, COPPER_BLOCK, BONE_BLOCK, GLOWSTONE -> 24;
            case CHAIN, PACKED_ICE, SOUL_SAND, SOUL_SOIL, SCULK_SENSOR, PRISMARINE -> 32;
            case CHERRY_LEAVES, FLOWERING_AZALEA, HONEYCOMB_BLOCK, MOSS_BLOCK, RESIN_BLOCK, OAK_LOG -> 48;
            case PINK_PETALS, GLOWSTONE_DUST -> 64;
            default -> 16;
        };
    }

    public record RitualOffering(Material material, int amount) {}

    private interface RecipeSlotConsumer {
        void accept(int slot, char token, Material material);
    }

    private record RecipeShape(String row1, String row2, String row3, Map<Character, Material> ingredients, Map<Character, Integer> amounts) {}
}
