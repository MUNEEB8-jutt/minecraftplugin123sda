package com.legendaryweaponssmp.weapons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class WeaponItemFactory {
    private final NamespacedKey weaponKey;
    private final NamespacedKey legendaryKey;

    public WeaponItemFactory(JavaPlugin plugin) {
        this.weaponKey = new NamespacedKey(plugin, "weapon_id");
        this.legendaryKey = new NamespacedKey(plugin, "legendary_item");
    }

    public ItemStack create(WeaponType type) {
        ItemStack item = new ItemStack(type.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(type.displayName(), NamedTextColor.GOLD));
        meta.lore(buildLore(type));
        meta.setItemModel(NamespacedKey.fromString("legendary:" + type.id()));
        meta.setCustomModelData(type.modelData());
        meta.setEnchantmentGlintOverride(false);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        applyDefaultEnchantments(meta, type);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(weaponKey, PersistentDataType.STRING, type.id());
        pdc.set(legendaryKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public Optional<WeaponType> resolve(ItemStack item) {
        Optional<WeaponType> resolvedType = peekType(item);
        if (resolvedType.isEmpty()) {
            return Optional.empty();
        }
        WeaponType type = resolvedType.get();
        String id = type.id();
        ItemMeta meta = item.getItemMeta();
        NamespacedKey modelKey = NamespacedKey.fromString("legendary:" + id);
        boolean changed = false;
        if (item.getType() != type.material()) {
            ItemMeta preserved = meta.clone();
            item.setType(type.material());
            item.setItemMeta(preserved);
            meta = item.getItemMeta();
            changed = true;
        }
        Component expectedName = Component.text(type.displayName(), NamedTextColor.GOLD);
        if (!expectedName.equals(meta.displayName())) {
            meta.displayName(expectedName);
            changed = true;
        }
        List<Component> expectedLore = buildLore(type);
        if (!expectedLore.equals(meta.lore())) {
            meta.lore(expectedLore);
            changed = true;
        }
        if (!meta.hasCustomModelData() || meta.getCustomModelData() != type.modelData()) {
            meta.setCustomModelData(type.modelData());
            changed = true;
        }
        if (!meta.getEnchants().isEmpty()) {
            for (Enchantment enchantment : List.copyOf(meta.getEnchants().keySet())) {
                meta.removeEnchant(enchantment);
            }
            changed = true;
        }
        changed |= applyDefaultEnchantments(meta, type);
        if (!meta.isUnbreakable()) {
            meta.setUnbreakable(true);
            changed = true;
        }
        if (!meta.getItemFlags().contains(ItemFlag.HIDE_UNBREAKABLE) || !meta.getItemFlags().contains(ItemFlag.HIDE_ATTRIBUTES)) {
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
            changed = true;
        }
        meta.setEnchantmentGlintOverride(false);
        if (modelKey != null && !modelKey.equals(meta.getItemModel())) {
            meta.setItemModel(modelKey);
            changed = true;
        }
        if (changed) {
            item.setItemMeta(meta);
        }
        return Optional.of(type);
    }

    public Optional<WeaponType> peekType(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String id = pdc.get(weaponKey, PersistentDataType.STRING);
        if (id == null) {
            return Optional.empty();
        }
        return WeaponType.byId(id);
    }

    public boolean isLegendary(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Boolean value = item.getItemMeta().getPersistentDataContainer().get(legendaryKey, PersistentDataType.BOOLEAN);
        return Boolean.TRUE.equals(value);
    }

    public NamespacedKey weaponKey() {
        return weaponKey;
    }

    public NamespacedKey legendaryKey() {
        return legendaryKey;
    }

    private boolean applyDefaultEnchantments(ItemMeta meta, WeaponType type) {
        boolean changed = false;
        changed |= addEnchant(meta, "unbreaking", 3);
        if (type.material() == org.bukkit.Material.BOW) {
            changed |= addEnchant(meta, "infinity", 1);
        } else if (type.material() == org.bukkit.Material.CROSSBOW) {
            changed |= addEnchant(meta, "mending", 1);
            changed |= addEnchant(meta, "quick_charge", 2);
        } else if (type.material() != org.bukkit.Material.TRIDENT) {
            changed |= addEnchant(meta, "mending", 1);
            changed |= addEnchant(meta, "looting", 2);
        } else {
            changed |= addEnchant(meta, "mending", 1);
        }
        return changed;
    }

    private boolean addEnchant(ItemMeta meta, String key, int level) {
        Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(key));
        if (enchantment == null) {
            return false;
        }
        Integer current = meta.getEnchants().get(enchantment);
        if (current != null && current == level) {
            return false;
        }
        meta.removeEnchant(enchantment);
        meta.addEnchant(enchantment, level, true);
        return true;
    }

    private List<Component> buildLore(WeaponType type) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Legendary Weapon", NamedTextColor.DARK_PURPLE));
        lore.add(Component.text(type.role(), NamedTextColor.GRAY));
        lore.add(Component.text(" ", NamedTextColor.DARK_GRAY));
        lore.add(Component.text("Abilities", NamedTextColor.AQUA));
        lore.add(Component.text("Shift + Left Click: " + type.signatureAbility(), NamedTextColor.GREEN));
        lore.add(Component.text("Shift + Right Click: " + type.ultimateAbility(), NamedTextColor.LIGHT_PURPLE));
        return lore;
    }
}
