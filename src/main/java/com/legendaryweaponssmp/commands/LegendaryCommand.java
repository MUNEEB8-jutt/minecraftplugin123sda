package com.legendaryweaponssmp.commands;

import com.legendaryweaponssmp.config.ConfigManager;
import com.legendaryweaponssmp.core.MessageService;
import com.legendaryweaponssmp.resourcepack.ResourcePackManager;
import com.legendaryweaponssmp.rituals.RitualBoxService;
import com.legendaryweaponssmp.rituals.RitualManager;
import com.legendaryweaponssmp.structures.SchematicPasteService;
import com.legendaryweaponssmp.ui.WeaponInfoMenu;
import com.legendaryweaponssmp.weapons.LegendaryStateStore;
import com.legendaryweaponssmp.weapons.WeaponItemFactory;
import com.legendaryweaponssmp.weapons.WeaponType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class LegendaryCommand implements CommandExecutor, TabCompleter, Listener {
    private static final int[] GRID_SLOTS = new int[]{10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int OUTPUT_SLOT = 24;
    private static final int PREV_SLOT = 36;
    private static final int NEXT_SLOT = 44;
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageService messageService;
    private final WeaponItemFactory itemFactory;
    private final LegendaryStateStore stateStore;
    private final RitualBoxService ritualBoxService;
    private final RitualManager ritualManager;
    private final ResourcePackManager resourcePackManager;
    private final SchematicPasteService schematicPasteService;
    private final WeaponInfoMenu weaponInfoMenu;

    public LegendaryCommand(JavaPlugin plugin,
                            ConfigManager configManager,
                            MessageService messageService,
                            WeaponItemFactory itemFactory,
                            LegendaryStateStore stateStore,
                            RitualBoxService ritualBoxService,
                            RitualManager ritualManager,
                            ResourcePackManager resourcePackManager,
                            WeaponInfoMenu weaponInfoMenu) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageService = messageService;
        this.itemFactory = itemFactory;
        this.stateStore = stateStore;
        this.ritualBoxService = ritualBoxService;
        this.ritualManager = ritualManager;
        this.resourcePackManager = resourcePackManager;
        this.weaponInfoMenu = weaponInfoMenu;
        this.schematicPasteService = new SchematicPasteService(plugin, messageService);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            messageService.send(sender, "&d/legendary info [weapon]");
            messageService.send(sender, "&e/legendary give <player> <weapon|core> <id>");
            messageService.send(sender, "&e/legendary ritual spawn <weapon>");
            messageService.send(sender, "&e/legendary ritual spawn all");
            messageService.send(sender, "&e/legendary ritual reveal <weapon>");
            messageService.send(sender, "&e/legendary ritual reveal dungeon");
            messageService.send(sender, "&e/legendary ritual unreveal [weapon]");
            messageService.send(sender, "&e/legendary ritual stop <weapon>");
            messageService.send(sender, "&e/legendary ritual delete <weapon|dungeon>");
            messageService.send(sender, "&e/legendary ritual setlimit <number>");
            messageService.send(sender, "&e/legendary setweaponlimit <number>");
            messageService.send(sender, "&e/legendary reload");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("info")) {
            handleWeaponInfo(sender, args);
            return true;
        }
        if (!hasAdmin(sender)) {
            messageService.send(sender, "&cYou don't have permission for this subcommand.");
            return true;
        }
        switch (sub) {
            case "give" -> handleGive(sender, args);
            case "ritual" -> handleRitualCommand(sender, args);
            case "setweaponlimit" -> handleSetWeaponLimit(sender, args);
            case "reload" -> handleReload(sender);
            default -> messageService.send(sender, "&cUnknown subcommand. Use /legendary for help.");
        }
        return true;
    }

    private void handleWeaponInfo(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "&cThis command can only be used by players.");
            return;
        }
        if (args.length < 2) {
            weaponInfoMenu.open(player);
            return;
        }
        Optional<WeaponType> typeOpt = WeaponType.byId(args[1]);
        if (typeOpt.isEmpty() || !configManager.isWeaponEnabled(typeOpt.get())) {
            messageService.send(sender, "&cInvalid or disabled weapon id.");
            return;
        }
        weaponInfoMenu.open(player, typeOpt.get());
    }

    private void handleRitualCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messageService.send(sender, "&e/legendary ritual spawn <weapon>");
            messageService.send(sender, "&e/legendary ritual spawn all");
            messageService.send(sender, "&e/legendary ritual reveal <weapon>");
            messageService.send(sender, "&e/legendary ritual reveal dungeon");
            messageService.send(sender, "&e/legendary ritual unreveal [weapon]");
            messageService.send(sender, "&e/legendary ritual stop <weapon>");
            messageService.send(sender, "&e/legendary ritual delete <weapon|dungeon>");
            messageService.send(sender, "&e/legendary ritual setlimit <number>");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "spawn" -> handleSpawnRitual(sender, args, 2);
            case "reveal" -> handleRevealRitual(sender, args, 2);
            case "unreveal" -> handleUnrevealRitual(sender, args, 2);
            case "stop" -> handleStopRitual(sender, args, 2);
            case "delete" -> handleDeleteRitual(sender, args, 2);
            case "setlimit" -> handleSetRitualLimit(sender, args, 2);
            default -> messageService.send(sender, "&cUnknown ritual subcommand. Use /legendary ritual.");
        }
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messageService.send(sender, "&cUsage: /legendary give <player> <weapon|core> <id>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messageService.send(sender, "&cPlayer not found.");
            return;
        }

        // Legacy support: /legendary give <player> <weaponId>
        if (args.length == 3) {
            Optional<WeaponType> legacyTypeOpt = WeaponType.byId(args[2]);
            if (legacyTypeOpt.isEmpty()) {
                messageService.send(sender, "&cInvalid weapon id.");
                return;
            }
            if (!ensureWeaponEnabled(sender, legacyTypeOpt.get())) {
                return;
            }
            giveWeapon(sender, target, legacyTypeOpt.get());
            return;
        }

        String mode = args[2].toLowerCase(Locale.ROOT);
        Optional<WeaponType> typeOpt = WeaponType.byId(args[3]);
        if (typeOpt.isEmpty()) {
            messageService.send(sender, "&cInvalid id. Use a valid weapon/core id.");
            return;
        }
        WeaponType type = typeOpt.get();
        if (!ensureWeaponEnabled(sender, type)) {
            return;
        }
        if (mode.equals("weapon") || mode.equals("weapons") || mode.equals("w")) {
            giveWeapon(sender, target, type);
            return;
        }
        if (mode.equals("core") || mode.equals("cores") || mode.equals("c")) {
            target.getInventory().addItem(ritualBoxService.createItem(type));
            messageService.send(sender, "&aGave " + type.displayName() + " Ritual Core to " + target.getName());
            return;
        }
        messageService.send(sender, "&cType must be &fweapon &cor &fcore&c.");
    }

    private void giveWeapon(CommandSender sender, Player target, WeaponType type) {
        boolean bypassLimit = sender.isOp()
            && (target.getGameMode() == GameMode.CREATIVE
            || ((sender instanceof Player playerSender) && playerSender.getGameMode() == GameMode.CREATIVE));
        int weaponLimit = configManager.weaponLimit();
        if (!bypassLimit && !stateStore.canCreate(type, weaponLimit)) {
            messageService.send(sender, "&c" + type.displayName() + " limit reached (&f" + weaponLimit + "&c).");
            return;
        }
        target.getInventory().addItem(itemFactory.create(type));
        stateStore.markCreated(type, target.getUniqueId());
        if (bypassLimit) {
            messageService.send(sender, "&a[Creative OP Bypass] Gave " + type.displayName() + " to " + target.getName());
            return;
        }
        messageService.send(sender, "&aGave " + type.displayName() + " to " + target.getName());
    }

    private void handleStartRitual(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "&cOnly players can start rituals.");
            return;
        }
        if (args.length < 2) {
            messageService.send(sender, "&cUsage: /legendary startRitual <weapon>");
            return;
        }
        Optional<WeaponType> typeOpt = WeaponType.byId(args[1]);
        if (typeOpt.isEmpty()) {
            messageService.send(sender, "&cInvalid weapon id.");
            return;
        }
        if (!ensureWeaponEnabled(sender, typeOpt.get())) {
            return;
        }
        boolean ok = ritualManager.startRitual(player, typeOpt.get(), player.getLocation());
        if (!ok) {
            messageService.send(sender, "&cUnable to start ritual.");
        }
    }

    private void handleSpawnRitual(CommandSender sender, String[] args, int weaponIndex) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "&cOnly players can spawn rituals.");
            return;
        }
        if (args.length <= weaponIndex) {
            messageService.send(sender, "&cUsage: /legendary ritual spawn <weapon>");
            return;
        }
        if (args[weaponIndex].equalsIgnoreCase("all")) {
            Location spawnOrigin = player.getLocation().clone();
            LocationSnapshot origin = new LocationSnapshot(
                spawnOrigin.getBlockX(),
                spawnOrigin.getBlockY(),
                spawnOrigin.getBlockZ()
            );
            boolean queued = schematicPasteService.pasteDungeon(player, spawnOrigin, () -> {
                int spawned = ritualManager.spawnDungeonRituals(player, spawnOrigin);
                if (spawned <= 0) {
                    messageService.send(player, "&cDungeon pasted, but no weapon ritual altars could be spawned.");
                    return;
                }
                messageService.send(player, "&aSpawned &f" + spawned + " &aweapon dungeon ritual altar(s) near &f"
                    + origin.x() + " " + origin.y() + " " + origin.z()
                    + "&a. Use &f/legendary ritual reveal dungeon &ato reveal the dungeon.");
            });
            if (!queued) {
                messageService.send(sender, "&cUnable to queue dungeon schematic paste.");
            }
            return;
        }
        Optional<WeaponType> typeOpt = WeaponType.byId(args[weaponIndex]);
        if (typeOpt.isEmpty()) {
            messageService.send(sender, "&cInvalid weapon id.");
            return;
        }
        WeaponType type = typeOpt.get();
        if (!ensureWeaponEnabled(sender, type)) {
            return;
        }
        boolean ok = ritualManager.spawnRitual(player, type, player.getLocation());
        if (!ok) {
            messageService.send(sender, "&cUnable to spawn ritual.");
            return;
        }
        messageService.send(sender, "&aSpawned hidden ritual for &f" + type.displayName() + "&a. Use &f/legendary ritual reveal " + type.id() + " &ato reveal it.");
    }

    private void handleRevealRitual(CommandSender sender, String[] args, int weaponIndex) {
        Player player = sender instanceof Player p ? p : null;
        WeaponType type = null;
        if (args.length > weaponIndex) {
            if (args[weaponIndex].equalsIgnoreCase("dungeon")) {
                int revealed = ritualManager.revealDungeonRituals(player);
                if (revealed <= 0) {
                    messageService.send(sender, "&cNo active weapon dungeon location to show.");
                    return;
                }
                messageService.send(sender, "&aWeapon dungeon location shown.");
                return;
            }
            Optional<WeaponType> typeOpt = WeaponType.byId(args[weaponIndex]);
            if (typeOpt.isEmpty()) {
                messageService.send(sender, "&cInvalid ritual name.");
                return;
            }
            type = typeOpt.get();
        }
        Optional<com.legendaryweaponssmp.rituals.RitualSession> alreadyRevealed = ritualManager.revealedSession();
        if (alreadyRevealed.isPresent() && (type == null || alreadyRevealed.get().weaponType() != type)) {
            messageService.send(sender, "&cCannot reveal another ritual while one ritual is revealed.");
            messageService.send(sender, "&7To unreveal it, use &f/legendary ritual unreveal " + alreadyRevealed.get().weaponType().id());
            return;
        }
        Optional<com.legendaryweaponssmp.rituals.RitualSession> revealed = ritualManager.revealRitualSession(player, type);
        if (revealed.isEmpty()) {
            messageService.send(sender, "&cNo active ritual to reveal.");
            return;
        }
        messageService.send(sender, "&aRitual revealed: &f" + revealed.get().weaponType().displayName());
    }

    private void handleUnrevealRitual(CommandSender sender, String[] args, int weaponIndex) {
        WeaponType type = null;
        if (args.length > weaponIndex) {
            Optional<WeaponType> typeOpt = WeaponType.byId(args[weaponIndex]);
            if (typeOpt.isEmpty()) {
                messageService.send(sender, "&cInvalid ritual name.");
                return;
            }
            type = typeOpt.get();
        }
        Optional<com.legendaryweaponssmp.rituals.RitualSession> revealed = ritualManager.revealedSession();
        if (revealed.isEmpty()) {
            messageService.send(sender, "&cNo revealed ritual to unreveal.");
            return;
        }
        WeaponType target = type != null ? type : revealed.get().weaponType();
        if (!ritualManager.unrevealRitual(target)) {
            messageService.send(sender, "&cCannot unreveal that ritual after crafting has started.");
            return;
        }
        messageService.send(sender, "&aRitual unrevealed: &f" + target.displayName());
    }

    private void handleStopRitual(CommandSender sender, String[] args, int weaponIndex) {
        if (args.length <= weaponIndex) {
            messageService.send(sender, "&cUsage: /legendary ritual stop <weapon>");
            return;
        }
        Optional<WeaponType> typeOpt = WeaponType.byId(args[weaponIndex]);
        if (typeOpt.isEmpty()) {
            messageService.send(sender, "&cInvalid ritual name.");
            return;
        }
        if (!ritualManager.stopRitual(typeOpt.get())) {
            messageService.send(sender, "&cThat ritual is not active.");
            return;
        }
        messageService.send(sender, "&eStopped ritual: &f" + typeOpt.get().displayName());
    }

    private void handleDeleteRitual(CommandSender sender, String[] args, int weaponIndex) {
        if (args.length <= weaponIndex) {
            messageService.send(sender, "&cUsage: /legendary ritual delete <weapon|dungeon>");
            return;
        }
        if (args[weaponIndex].equalsIgnoreCase("dungeon")) {
            int deleted = ritualManager.deleteDungeonRituals();
            if (deleted <= 0) {
                messageService.send(sender, "&cNo active dungeon ritual altars were found.");
                return;
            }
            messageService.send(sender, "&aDeleted &f" + deleted + " &adungeon ritual altar(s) and cleared their saved data.");
            return;
        }
        Optional<WeaponType> typeOpt = WeaponType.byId(args[weaponIndex]);
        if (typeOpt.isEmpty()) {
            messageService.send(sender, "&cInvalid ritual name.");
            return;
        }
        stateStore.clear(typeOpt.get());
        ritualManager.clearSavedRitualRecord(typeOpt.get());
        boolean deleted = ritualManager.deleteRitual(typeOpt.get());
        if (deleted) {
            messageService.send(sender, "&aDeleted ritual structure and cleared saved data for &f" + typeOpt.get().displayName());
            return;
        }
        messageService.send(sender, "&aCleared saved ritual data for &f" + typeOpt.get().displayName() + "&a. No active structure was found.");
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messageService.send(sender, "&cUsage: /legendary remove <weapon>");
            return;
        }
        Optional<WeaponType> typeOpt = WeaponType.byId(args[1]);
        if (typeOpt.isEmpty()) {
            messageService.send(sender, "&cInvalid weapon id.");
            return;
        }
        WeaponType type = typeOpt.get();
        removeWeaponItems(type);
        ritualManager.deleteRitual(type);
        stateStore.clear(type);
        messageService.send(sender, "&aRemoved all instances of " + type.displayName());
    }

    private void handleReload(CommandSender sender) {
        configManager.reloadAll();
        ritualBoxService.registerRecipe();
        resourcePackManager.regenerateAndBroadcast();
        messageService.send(sender, "&aLegendaryWeaponsSMP reloaded.");
    }

    private void handleSetWeaponLimit(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messageService.send(sender, "&cUsage: /legendary setweaponlimit <number>");
            messageService.send(sender, "&7Current limit: &f" + configManager.weaponLimit());
            return;
        }
        int limit;
        try {
            limit = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            messageService.send(sender, "&cNumber required.");
            return;
        }
        if (limit < 1) {
            messageService.send(sender, "&cLimit must be at least 1.");
            return;
        }
        configManager.setWeaponLimit(limit);
        long above = configManager.enabledWeaponTypes().stream()
            .filter(type -> {
                LegendaryStateStore.WeaponState state = stateStore.state(type);
                return state != null && state.existingCount() > limit;
            })
            .count();
        messageService.send(sender, "&aGlobal per-weapon limit set to &f" + limit);
        if (above > 0) {
            messageService.send(sender, "&e" + above + " weapon type(s) are currently above this limit.");
        }
    }

    private void handleSetRitualLimit(CommandSender sender, String[] args, int limitIndex) {
        if (args.length <= limitIndex) {
            messageService.send(sender, "&cUsage: /legendary ritual setlimit <number>");
            messageService.send(sender, "&7Current limit: &f" + configManager.ritualLimit());
            return;
        }
        int limit;
        try {
            limit = Integer.parseInt(args[limitIndex]);
        } catch (NumberFormatException ex) {
            messageService.send(sender, "&cNumber required.");
            return;
        }
        if (limit < 1) {
            messageService.send(sender, "&cLimit must be at least 1.");
            return;
        }
        configManager.setRitualLimit(limit);
        messageService.send(sender, "&aActive ritual limit set to &f" + limit);
        int active = ritualManager.activeSessions().size();
        if (active > limit) {
            messageService.send(sender, "&e" + active + " rituals are already active, so no new ritual can start until some finish.");
        }
    }

    private void handleRecipeViewer(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "&cThis command can only be used by players.");
            return;
        }
        WeaponType requested = null;
        if (args.length >= 2) {
            requested = WeaponType.byId(args[1]).orElse(null);
            if (requested == null || !configManager.isWeaponEnabled(requested)) {
                messageService.send(player, "&cInvalid core name.");
                return;
            }
        }
        openRecipeMenu(player, 0, requested);
    }

    private void handleEnableRecipie(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messageService.send(sender, "&cUsage: /legendary enablerecipie <core_name>");
            return;
        }
        Optional<WeaponType> typeOpt = WeaponType.byId(args[1]);
        if (typeOpt.isEmpty()) {
            messageService.send(sender, "&cInvalid core name.");
            return;
        }
        WeaponType type = typeOpt.get();
        if (!ensureWeaponEnabled(sender, type)) {
            return;
        }
        ritualBoxService.setCoreRecipeEnabled(true);
        ritualBoxService.setRecipeEnabled(type, true);
        ritualBoxService.registerRecipe();
        ritualBoxService.unlockCrafting();
        messageService.send(sender, "&aEnabled ritual core recipe for &f" + type.id() + "&a and unlocked core crafting.");
    }

    private void handleDisableRecipie(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messageService.send(sender, "&cUsage: /legendary disablerecipie <core_name>");
            return;
        }
        Optional<WeaponType> typeOpt = WeaponType.byId(args[1]);
        if (typeOpt.isEmpty()) {
            messageService.send(sender, "&cInvalid core name.");
            return;
        }
        WeaponType type = typeOpt.get();
        if (!configManager.isWeaponEnabled(type)) {
            messageService.send(sender, "&eThat weapon is already disabled.");
            return;
        }
        ritualBoxService.setRecipeEnabled(type, false);
        messageService.send(sender, "&eDisabled ritual core recipe for &f" + type.id());
    }

    @EventHandler(ignoreCancelled = true)
    public void onRecipeMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof RecipeMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot == PREV_SLOT) {
            openRecipeMenu(player, holder.index() - 1, null);
            return;
        }
        if (rawSlot == NEXT_SLOT) {
            openRecipeMenu(player, holder.index() + 1, null);
        }
    }

    private void openRecipeMenu(Player player, int requestedIndex, WeaponType requestedType) {
        List<WeaponType> enabled = ritualBoxService.enabledRecipeTypes();
        if (enabled.isEmpty()) {
            messageService.send(player, "&cNo enabled ritual core recipes right now.");
            return;
        }
        int index = requestedIndex;
        if (requestedType != null) {
            int found = enabled.indexOf(requestedType);
            if (found == -1) {
                messageService.send(player, "&eThat core recipe is currently disabled.");
                return;
            }
            index = found;
        }
        if (index < 0) {
            index = enabled.size() - 1;
        }
        if (index >= enabled.size()) {
            index = 0;
        }

        WeaponType type = enabled.get(index);
        Inventory inv = player.getServer().createInventory(new RecipeMenuHolder(index), 45, "Ritual Recipie Viewer");
        fill(inv, Material.GRAY_STAINED_GLASS_PANE, " ");
        inv.setItem(4, named(Material.ENCHANTED_BOOK,
            type.displayName() + " Core Recipe",
            List.of(
                "Enabled recipes: " + enabled.size(),
                "Core: " + type.id(),
                "Use arrows to browse"
            )));

        Map<Integer, Material> grid = ritualBoxService.recipeGrid(type);
        for (int i = 0; i < GRID_SLOTS.length; i++) {
            Material mat = grid.get(i);
            if (mat != null) {
                inv.setItem(GRID_SLOTS[i], named(mat, pretty(mat.name()), List.of()));
            } else {
                inv.setItem(GRID_SLOTS[i], new ItemStack(Material.AIR));
            }
        }

        inv.setItem(22, named(Material.CRAFTING_TABLE, "3x3 Crafting Grid", List.of("Ritual core crafting pattern")));
        inv.setItem(23, named(Material.LIME_STAINED_GLASS_PANE, "=>", List.of("Output slot on right")));
        inv.setItem(OUTPUT_SLOT, ritualBoxService.createItem(type));
        inv.setItem(PREV_SLOT, named(Material.ARROW, "Previous", List.of("Show previous enabled recipe")));
        inv.setItem(NEXT_SLOT, named(Material.ARROW, "Next", List.of("Show next enabled recipe")));
        player.openInventory(inv);
    }

    private void fill(Inventory inv, Material material, String name) {
        ItemStack filler = named(material, name, List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
    }

    private ItemStack named(Material material, String name, List<String> loreText) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.AQUA));
        if (!loreText.isEmpty()) {
            meta.lore(loreText.stream()
                .map(line -> Component.text(line, NamedTextColor.GRAY))
                .collect(Collectors.toList()));
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private String pretty(String input) {
        String[] parts = input.toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private void handleList(CommandSender sender) {
        messageService.send(sender, "&eLegendary status:");
        int weaponLimit = configManager.weaponLimit();
        for (WeaponType type : configManager.enabledWeaponTypes()) {
            LegendaryStateStore.WeaponState state = stateStore.state(type);
            if (state == null) {
                state = new LegendaryStateStore.WeaponState(false, 0, false, null);
            }
            String status = state.inRitual() ? "&bIN_RITUAL" : state.exists() ? "&cEXISTS" : "&aAVAILABLE";
            messageService.send(sender, "&7- &f" + type.id() + " &8: " + status + " &7count:&f " + state.existingCount() + "/" + weaponLimit);
        }
    }

    private void handleRitualInfo(CommandSender sender) {
        if (ritualManager.activeSessions().isEmpty()) {
            messageService.send(sender, "&cNo active rituals.");
            return;
        }
        ritualManager.activeSessions().forEach(session -> {
            messageService.send(sender, "&e" + session.weaponType().displayName() + " &7at &f" +
                session.center().getBlockX() + " " + session.center().getBlockY() + " " + session.center().getBlockZ() +
                " &7remaining: &f" + session.remainingSeconds() + "s");
        });
    }

    private void removeWeaponItems(WeaponType type) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            purgeInventory(player.getInventory(), type);
            if (player.getEnderChest() != null) {
                purgeInventory(player.getEnderChest(), type);
            }
        }
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(Item.class)) {
                Item itemEntity = (Item) entity;
                WeaponType itemType = itemFactory.peekType(itemEntity.getItemStack()).orElse(null);
                if (itemType == type) {
                    itemEntity.remove();
                }
            }
        }
    }

    private void purgeInventory(Inventory inventory, WeaponType type) {
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            WeaponType found = itemFactory.peekType(item).orElse(null);
            if (found == type) {
                inventory.setItem(i, null);
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = hasAdmin(sender)
                ? Arrays.asList("info", "give", "ritual", "setweaponlimit", "reload")
                : List.of("info");
            return subs.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
            return configManager.enabledWeaponTypes().stream()
                .map(WeaponType::id)
                .filter(id -> id.startsWith(args[1].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setweaponlimit")) {
            return Arrays.asList("1", "2", "3", "4", "5", "10", "15", "20").stream()
                .filter(s -> s.startsWith(args[1]))
                .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("ritual")) {
            return Arrays.asList("spawn", "reveal", "unreveal", "stop", "delete", "setlimit").stream()
                .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("ritual") && args[1].equalsIgnoreCase("setlimit")) {
            return Arrays.asList("1", "2", "3", "4", "5", "10", "15", "20").stream()
                .filter(s -> s.startsWith(args[2]))
                .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("ritual") && args[1].equalsIgnoreCase("spawn")) {
            List<String> ids = new ArrayList<>();
            ids.add("all");
            for (WeaponType type : configManager.enabledWeaponTypes()) {
                ids.add(type.id());
            }
            return ids.stream()
                .filter(s -> s.startsWith(args[2].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("ritual")
            && (args[1].equalsIgnoreCase("stop") || args[1].equalsIgnoreCase("delete"))) {
            List<String> ids = new ArrayList<>();
            if (args[1].equalsIgnoreCase("delete") && ritualManager.activeSessions().stream().anyMatch(session -> session.dungeonRitual())) {
                ids.add("dungeon");
            }
            ids.addAll(ritualManager.activeSessions().stream()
                .map(session -> session.weaponType().id())
                .toList());
            return ids.stream()
                .filter(s -> s.startsWith(args[2].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("ritual") && args[1].equalsIgnoreCase("reveal")) {
            List<String> ids = new ArrayList<>();
            boolean hasDungeon = ritualManager.activeSessions().stream()
                .anyMatch(session -> session.dungeonRitual());
            if (hasDungeon) {
                ids.add("dungeon");
            }
            ids.addAll(ritualManager.activeSessions().stream()
                .filter(session -> !session.revealed())
                .map(session -> session.weaponType().id())
                .toList());
            return ids.stream()
                .filter(s -> s.startsWith(args[2].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("ritual") && args[1].equalsIgnoreCase("unreveal")) {
            return ritualManager.activeSessions().stream()
                .filter(session -> session.revealed())
                .map(session -> session.weaponType().id())
                .filter(s -> s.startsWith(args[2].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            if (configManager.enabledWeaponTypes().isEmpty()) {
                return List.of();
            }
            return Arrays.asList("weapon", "core").stream()
                .filter(s -> s.startsWith(args[2].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            List<String> ids = new ArrayList<>();
            for (WeaponType type : configManager.enabledWeaponTypes()) {
                ids.add(type.id());
            }
            return ids.stream().filter(s -> s.startsWith(args[3].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        }
        return List.of();
    }

    private boolean ensureWeaponEnabled(CommandSender sender, WeaponType type) {
        if (configManager.isWeaponEnabled(type)) {
            return true;
        }
        messageService.send(sender, "&cThat weapon is currently disabled.");
        return false;
    }

    private boolean hasAdmin(CommandSender sender) {
        return sender.hasPermission("legendary.admin") || sender.isOp();
    }

    private record RecipeMenuHolder(int index) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record LocationSnapshot(int x, int y, int z) {
    }
}
