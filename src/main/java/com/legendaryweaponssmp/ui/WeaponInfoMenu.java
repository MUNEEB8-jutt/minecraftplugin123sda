package com.legendaryweaponssmp.ui;

import com.legendaryweaponssmp.config.ConfigManager;
import com.legendaryweaponssmp.weapons.WeaponItemFactory;
import com.legendaryweaponssmp.weapons.WeaponType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class WeaponInfoMenu implements Listener {
    private static final int PREVIOUS_SLOT = 45;
    private static final int CLOSE_SLOT = 49;
    private static final int NEXT_SLOT = 53;
    private static final Map<WeaponType, AbilityCopy> ABILITY_COPY = abilityCopy();

    private final ConfigManager configManager;
    private final WeaponItemFactory itemFactory;

    public WeaponInfoMenu(ConfigManager configManager, WeaponItemFactory itemFactory) {
        this.configManager = configManager;
        this.itemFactory = itemFactory;
    }

    public void open(Player player) {
        open(player, 0);
    }

    public boolean open(Player player, WeaponType requestedType) {
        List<WeaponType> weapons = configManager.enabledWeaponTypes();
        int index = weapons.indexOf(requestedType);
        if (index < 0) {
            return false;
        }
        open(player, index);
        return true;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof WeaponInfoHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        switch (event.getRawSlot()) {
            case PREVIOUS_SLOT -> open(player, holder.index() - 1);
            case CLOSE_SLOT -> player.closeInventory();
            case NEXT_SLOT -> open(player, holder.index() + 1);
            default -> {
            }
        }
    }

    private void open(Player player, int requestedIndex) {
        List<WeaponType> weapons = configManager.enabledWeaponTypes();
        if (weapons.isEmpty()) {
            player.sendMessage(Component.text("No legendary weapons are currently enabled.", NamedTextColor.RED));
            return;
        }

        int index = Math.floorMod(requestedIndex, weapons.size());
        WeaponType type = weapons.get(index);
        TextColor accent = TextColor.color(type.color().asRGB());
        Inventory inventory = player.getServer().createInventory(
            new WeaponInfoHolder(index),
            54,
            text("Legendary Armory [" + (index + 1) + "/" + weapons.size() + "]", accent, true)
        );

        decorate(inventory, type);
        inventory.setItem(4, item(
            Material.NETHER_STAR,
            text("LEGENDARY WEAPON ARCHIVE", NamedTextColor.GOLD, true),
            List.of(
                line("Weapon " + (index + 1) + " of " + weapons.size(), NamedTextColor.GRAY),
                line("Browse every weapon, role and ability.", NamedTextColor.DARK_GRAY)
            )
        ));
        inventory.setItem(13, weaponPreview(type, index, weapons.size(), accent));
        inventory.setItem(20, abilityCard(type, false, accent));
        inventory.setItem(24, abilityCard(type, true, accent));
        inventory.setItem(30, roleCard(type, accent));
        inventory.setItem(31, statsCard(type, accent));
        inventory.setItem(32, controlsCard());
        inventory.setItem(PREVIOUS_SLOT, item(
            Material.SPECTRAL_ARROW,
            text("< PREVIOUS WEAPON", NamedTextColor.AQUA, true),
            List.of(line(previousName(weapons, index), NamedTextColor.GRAY))
        ));
        inventory.setItem(CLOSE_SLOT, item(
            Material.BARRIER,
            text("CLOSE ARCHIVE", NamedTextColor.RED, true),
            List.of(line("Return to the game", NamedTextColor.GRAY))
        ));
        inventory.setItem(NEXT_SLOT, item(
            Material.SPECTRAL_ARROW,
            text("NEXT WEAPON >", NamedTextColor.AQUA, true),
            List.of(line(nextName(weapons, index), NamedTextColor.GRAY))
        ));
        player.openInventory(inventory);
    }

    private void decorate(Inventory inventory, WeaponType type) {
        ItemStack black = item(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of());
        ItemStack purple = item(Material.PURPLE_STAINED_GLASS_PANE, Component.text(" "), List.of());
        ItemStack accent = item(accentPane(type), Component.text(" "), List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, black);
        }
        for (int slot = 0; slot < 9; slot++) {
            inventory.setItem(slot, purple);
        }
        for (int slot = 9; slot < 18; slot++) {
            inventory.setItem(slot, slot == 13 ? black : accent);
        }
        for (int slot = 18; slot < 27; slot++) {
            inventory.setItem(slot, slot >= 19 && slot <= 25 ? black : purple);
        }
        for (int slot = 27; slot < 36; slot++) {
            inventory.setItem(slot, slot >= 29 && slot <= 33 ? black : purple);
        }
        for (int slot = 36; slot < 45; slot++) {
            inventory.setItem(slot, accent);
        }
        for (int slot = 45; slot < 54; slot++) {
            inventory.setItem(slot, purple);
        }
    }

    private ItemStack weaponPreview(WeaponType type, int index, int total, TextColor accent) {
        ItemStack preview = itemFactory.create(type);
        ItemMeta meta = preview.getItemMeta();
        meta.displayName(text(type.displayName().toUpperCase(Locale.ROOT), accent, true));
        meta.lore(List.of(
            line("LEGENDARY WEAPON", NamedTextColor.GOLD),
            line(type.role(), NamedTextColor.WHITE),
            Component.empty(),
            line("Signature: " + type.signatureAbility(), NamedTextColor.GREEN),
            line("Ultimate: " + type.ultimateAbility(), NamedTextColor.LIGHT_PURPLE),
            Component.empty(),
            line("Archive page " + (index + 1) + " / " + total, NamedTextColor.DARK_GRAY)
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);
        preview.setItemMeta(meta);
        return preview;
    }

    private ItemStack abilityCard(WeaponType type, boolean ultimate, TextColor accent) {
        AbilityCopy copy = ABILITY_COPY.get(type);
        String abilityName = ultimate ? type.ultimateAbility() : type.signatureAbility();
        String description = ultimate ? copy.ultimate() : copy.signature();
        int cooldown = configManager.cooldownSeconds(
            ultimate ? type.rightCooldownKey() : type.leftCooldownKey(),
            ultimate ? 60 : 18
        );
        double damage = configuredDamage(type, ultimate ? "ultimate-damage" : "signature-damage");
        NamedTextColor kindColor = ultimate ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GREEN;
        List<Component> lore = new ArrayList<>();
        lore.add(line(ultimate ? "SHIFT + RIGHT CLICK" : "SHIFT + LEFT CLICK", kindColor));
        lore.add(Component.empty());
        for (String wrapped : wrap(description, 34)) {
            lore.add(line(wrapped, NamedTextColor.GRAY));
        }
        lore.add(Component.empty());
        lore.add(line("Cooldown: " + cooldown + "s", NamedTextColor.AQUA));
        if (damage > 0.0) {
            lore.add(line("Configured damage: " + number(damage), NamedTextColor.RED));
        }
        lore.add(line(ultimate ? "Ultimate ability" : "Signature ability", accent));
        return item(
            ultimate ? Material.NETHER_STAR : Material.AMETHYST_SHARD,
            text(abilityName, kindColor, true),
            lore
        );
    }

    private ItemStack roleCard(WeaponType type, TextColor accent) {
        return item(
            Material.ENDER_EYE,
            text("COMBAT ROLE", accent, true),
            List.of(
                line(type.role(), NamedTextColor.WHITE),
                Component.empty(),
                line(type.isRanged() ? "Weapon class: Ranged" : "Weapon class: Melee", NamedTextColor.GRAY)
            )
        );
    }

    private ItemStack statsCard(WeaponType type, TextColor accent) {
        double normal = configuredDamage(type, "normal-damage");
        double signature = configuredDamage(type, "signature-damage");
        double ultimate = configuredDamage(type, "ultimate-damage");
        int signatureCooldown = configManager.cooldownSeconds(type.leftCooldownKey(), 18);
        int ultimateCooldown = configManager.cooldownSeconds(type.rightCooldownKey(), 60);
        List<Component> lore = new ArrayList<>();
        lore.add(line("Normal damage: " + number(normal), NamedTextColor.WHITE));
        lore.add(line("Signature damage: " + number(signature), NamedTextColor.GREEN));
        lore.add(line("Ultimate damage: " + (ultimate > 0.0 ? number(ultimate) : "Multi-hit"), NamedTextColor.LIGHT_PURPLE));
        lore.add(Component.empty());
        lore.add(line("Signature cooldown: " + signatureCooldown + "s", NamedTextColor.AQUA));
        lore.add(line("Ultimate cooldown: " + ultimateCooldown + "s", NamedTextColor.AQUA));
        return item(Material.KNOWLEDGE_BOOK, text("WEAPON STATS", accent, true), lore);
    }

    private ItemStack controlsCard() {
        return item(
            Material.COMPASS,
            text("ARCHIVE CONTROLS", NamedTextColor.YELLOW, true),
            List.of(
                line("Use the arrows below to browse.", NamedTextColor.GRAY),
                line("/legendary info <weapon_id>", NamedTextColor.AQUA),
                line("opens a weapon directly.", NamedTextColor.GRAY)
            )
        );
    }

    private double configuredDamage(WeaponType type, String key) {
        return configManager.weapons().getDouble("weapons." + type.id() + "." + key, 0.0);
    }

    private Material accentPane(WeaponType type) {
        return switch (type.barColor()) {
            case RED -> Material.RED_STAINED_GLASS_PANE;
            case BLUE -> Material.CYAN_STAINED_GLASS_PANE;
            case GREEN -> Material.LIME_STAINED_GLASS_PANE;
            case YELLOW -> Material.YELLOW_STAINED_GLASS_PANE;
            case PINK -> Material.MAGENTA_STAINED_GLASS_PANE;
            case PURPLE -> Material.PURPLE_STAINED_GLASS_PANE;
            default -> Material.PURPLE_STAINED_GLASS_PANE;
        };
    }

    private ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(noItalic(name));
        meta.lore(lore.stream().map(this::noItalic).toList());
        meta.setEnchantmentGlintOverride(false);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        stack.setItemMeta(meta);
        return stack;
    }

    private Component text(String value, TextColor color, boolean bold) {
        Component component = Component.text(value, color);
        if (bold) {
            component = component.decorate(TextDecoration.BOLD);
        }
        return noItalic(component);
    }

    private Component line(String value, TextColor color) {
        return noItalic(Component.text(value, color));
    }

    private Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    private String number(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private String previousName(List<WeaponType> weapons, int index) {
        return weapons.get(Math.floorMod(index - 1, weapons.size())).displayName();
    }

    private String nextName(List<WeaponType> weapons, int index) {
        return weapons.get(Math.floorMod(index + 1, weapons.size())).displayName();
    }

    private List<String> wrap(String text, int maxLength) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (!current.isEmpty() && current.length() + word.length() + 1 > maxLength) {
                lines.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append(' ');
            }
            current.append(word);
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private static Map<WeaponType, AbilityCopy> abilityCopy() {
        Map<WeaponType, AbilityCopy> copy = new EnumMap<>(WeaponType.class);
        copy.put(WeaponType.QUANTUM_CHRONOBLADE, new AbilityCopy(
            "Dash through targets with a dragon-charged cleave and explosive finisher.",
            "Call a red dragon raid that tears through several hostile battlefield lanes."
        ));
        copy.put(WeaponType.DRAKEFIRE_KATANA, new AbilityCopy(
            "Manifest a gravity arsenal that pressures and strikes nearby enemies.",
            "Ascend with singularity power, pulling enemies into a devastating collapse."
        ));
        copy.put(WeaponType.GOLDFANG_DAGGER, new AbilityCopy(
            "Rush forward in a gilded assassination slash through targets in your path.",
            "Create a heist zone that marks enemies and rewards aggressive takedowns."
        ));
        copy.put(WeaponType.BLOODCHAIN_RIPPER, new AbilityCopy(
            "Launch a brutal chain hook that reels a captured target toward you.",
            "Build a moving slaughter lane that drags and repeatedly damages enemies."
        ));
        copy.put(WeaponType.FROSTNOVA_CHAKRAM, new AbilityCopy(
            "Command falling ice constructs to crush and control a targeted area.",
            "Raise a glacier mirror palace that traps, slows and punishes nearby enemies."
        ));
        copy.put(WeaponType.PETALSTORM_FANBLADE, new AbilityCopy(
            "Surge through the air in a sweeping koi-dragon petal formation.",
            "Unleash a wide heavenbloom festival of petals, support and area pressure."
        ));
        copy.put(WeaponType.STORMBREAKER_RELIC, new AbilityCopy(
            "Vault into the air and slam down with a concentrated thunder impact.",
            "Call the skyforge judgement, striking the battlefield with massive lightning."
        ));
        copy.put(WeaponType.SANGUINE_PIKE, new AbilityCopy(
            "Charge ahead in a crimson joust that pierces enemies along the route.",
            "Form an impalement colosseum that controls and damages trapped opponents."
        ));
        copy.put(WeaponType.VOIDGLASS_LICH_STAFF, new AbilityCopy(
            "Snatch the nearest enemy into a voidglass coffin prison.",
            "Summon a lich king procession that advances as an undead siege force."
        ));
        copy.put(WeaponType.BIFROST_WAND, new AbilityCopy(
            "Seal the nearest enemy inside a radiant prism cell.",
            "Create a celestial tribunal that captures and judges enemies in its arena."
        ));
        copy.put(WeaponType.NECROMANCER_REAPER, new AbilityCopy(
            "Sever a target's soul with an execution-focused scythe strike.",
            "Open a death carousel that repeatedly cuts enemies inside its arena."
        ));
        copy.put(WeaponType.TIMBERLORD_AXE, new AbilityCopy(
            "Send a living beastwood ram forward to smash through enemy lines.",
            "Raise a walking fortress that advances as mobile cover and siege pressure."
        ));
        copy.put(WeaponType.BLOOMSHOT_BLASTER, new AbilityCopy(
            "Fire a living venus volley that blooms into rapid garden artillery.",
            "Trigger a garden apocalypse that overwhelms a wide area with hostile growth."
        ));
        copy.put(WeaponType.HORNHOOK_HARPOON, new AbilityCopy(
            "Fire a monster line that hooks and pulls a distant target.",
            "Anchor enemies with kraken moorings and control movement across the area."
        ));
        copy.put(WeaponType.TEMPEST_SONICBOW, new AbilityCopy(
            "Release three high-speed hunter shots in a focused sonic burst.",
            "Charge and fire a crimson railshot with extreme range and impact."
        ));
        return copy;
    }

    private record AbilityCopy(String signature, String ultimate) {
    }

    private record WeaponInfoHolder(int index) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
