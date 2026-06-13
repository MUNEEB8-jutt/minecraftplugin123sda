package com.legendaryweaponssmp.core;

import com.legendaryweaponssmp.abilities.AbilityListener;
import com.legendaryweaponssmp.abilities.CooldownManager;
import com.legendaryweaponssmp.animations.AnimationService;
import com.legendaryweaponssmp.commands.LegendaryCommand;
import com.legendaryweaponssmp.config.ConfigManager;
import com.legendaryweaponssmp.hud.BetterHudIntegration;
import com.legendaryweaponssmp.particles.ParticleService;
import com.legendaryweaponssmp.resourcepack.ResourcePackListener;
import com.legendaryweaponssmp.resourcepack.ResourcePackManager;
import com.legendaryweaponssmp.rituals.RitualBoxService;
import com.legendaryweaponssmp.rituals.RitualManager;
import com.legendaryweaponssmp.rituals.RitualMobProtectionListener;
import com.legendaryweaponssmp.rituals.RitualProtectionListener;
import com.legendaryweaponssmp.structures.RitualStructureBuilder;
import com.legendaryweaponssmp.ui.RitualSelectionMenu;
import com.legendaryweaponssmp.weapons.LegendaryItemListener;
import com.legendaryweaponssmp.weapons.LegendaryStateStore;
import com.legendaryweaponssmp.weapons.WeaponItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class LegendaryWeaponsPlugin extends JavaPlugin {
    private ConfigManager configManager;
    private MessageService messageService;
    private WeaponItemFactory weaponItemFactory;
    private LegendaryStateStore stateStore;
    private CooldownManager cooldownManager;
    private ParticleService particleService;
    private AnimationService animationService;
    private RitualBoxService ritualBoxService;
    private RitualManager ritualManager;
    private ResourcePackManager resourcePackManager;
    private BetterHudIntegration betterHudIntegration;
    private AbilityListener abilityListener;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        this.configManager = new ConfigManager(this);
        this.messageService = new MessageService(configManager);
        this.weaponItemFactory = new WeaponItemFactory(this);
        this.stateStore = new LegendaryStateStore(this);
        this.cooldownManager = new CooldownManager(this);
        this.betterHudIntegration = new BetterHudIntegration(this, configManager);
        this.particleService = new ParticleService(this, configManager);
        this.animationService = new AnimationService(this);
        this.ritualBoxService = new RitualBoxService(this, configManager);
        this.ritualManager = new RitualManager(this, configManager, messageService, stateStore, weaponItemFactory, particleService, animationService, new RitualStructureBuilder());
        this.ritualBoxService.attachRitualManager(ritualManager);
        this.ritualManager.attachRitualBoxService(ritualBoxService);
        this.cooldownManager.attachBetterHudIntegration(betterHudIntegration);
        this.ritualManager.attachBetterHudIntegration(betterHudIntegration);
        this.resourcePackManager = new ResourcePackManager(this, configManager, messageService);
        this.betterHudIntegration.attachResourcePackManager(resourcePackManager);
        this.resourcePackManager.attachHudIntegration(betterHudIntegration);

        ritualBoxService.registerRecipe();
        ritualBoxService.unlockCrafting();
        cooldownManager.start();

        resourcePackManager.initialize();
        betterHudIntegration.initialize();

        this.abilityListener = new AbilityListener(this, configManager, messageService, weaponItemFactory, cooldownManager, particleService, animationService);
        Bukkit.getPluginManager().registerEvents(abilityListener, this);
        Bukkit.getPluginManager().registerEvents(new RitualProtectionListener(this, ritualManager, messageService), this);
        Bukkit.getPluginManager().registerEvents(new RitualMobProtectionListener(this, ritualManager, configManager), this);
        Bukkit.getPluginManager().registerEvents(ritualBoxService, this);
        Bukkit.getPluginManager().registerEvents(new LegendaryItemListener(this, configManager, weaponItemFactory, stateStore, messageService), this);
        Bukkit.getPluginManager().registerEvents(new ResourcePackListener(resourcePackManager, configManager, messageService, ritualManager), this);
        Bukkit.getPluginManager().registerEvents(new RitualSelectionMenu(configManager, ritualManager, ritualBoxService, stateStore, messageService), this);

        PluginCommand command = getCommand("legendary");
        if (command != null) {
            LegendaryCommand executor = new LegendaryCommand(
                this,
                configManager,
                messageService,
                weaponItemFactory,
                stateStore,
                ritualBoxService,
                ritualManager,
                resourcePackManager
            );
            command.setExecutor(executor);
            command.setTabCompleter(executor);
            Bukkit.getPluginManager().registerEvents(executor, this);
        }

        Bukkit.getScheduler().runTask(this, ritualManager::restoreSavedRituals);
        ritualManager.startAutosave();
    }

    @Override
    public void onDisable() {
        if (ritualManager != null) {
            ritualManager.saveAndPauseAll();
        }
        if (abilityListener != null) {
            abilityListener.shutdown();
        }
        if (cooldownManager != null) {
            cooldownManager.stop();
        }
        if (betterHudIntegration != null) {
            betterHudIntegration.shutdown();
        }
        if (animationService != null) {
            animationService.stopAll();
        }
        if (resourcePackManager != null) {
            resourcePackManager.stop();
        }
    }
}
