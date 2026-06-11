package com.legendaryweaponssmp.rituals;

import com.legendaryweaponssmp.structures.RitualStructureBuilder;
import com.legendaryweaponssmp.weapons.WeaponType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class RitualSession {
    private final WeaponType weaponType;
    private final UUID starterId;
    private final Location center;
    private final Location coreLocation;
    private final RitualStructureBuilder.BuildPlan buildPlan;
    private final int totalSeconds;
    private final Set<UUID> playersInZone = new HashSet<>();
    private int remainingSeconds;
    private int emptySeconds;
    private boolean paused;
    private Material expectedCoreMaterial;
    private BukkitTask buildTask;
    private BukkitTask timerTask;
    private BukkitTask fxTask;
    private BossBar bossBar;
    private ItemDisplay coreDisplay;
    private ItemDisplay workbenchDisplay;
    private ItemDisplay display;
    private TextDisplay progressDisplay;
    private final List<Display> forgeDisplays = new ArrayList<>();
    private final List<Display> offeringDisplays = new ArrayList<>();
    private Map<Integer, RitualBoxService.RitualOffering> offeringRequired = new HashMap<>();
    private final Map<Integer, Integer> offeringProvided = new HashMap<>();
    private final Map<Integer, Long> offeringHighlights = new HashMap<>();
    private boolean ritualStarted;
    private boolean revealed;
    private boolean manuallyStopped;
    private boolean dungeonRitual;

    public RitualSession(WeaponType weaponType,
                         UUID starterId,
                         Location center,
                         Location coreLocation,
                         RitualStructureBuilder.BuildPlan buildPlan,
                         int remainingSeconds) {
        this(weaponType, starterId, center, coreLocation, buildPlan, remainingSeconds, remainingSeconds);
    }

    public RitualSession(WeaponType weaponType,
                         UUID starterId,
                         Location center,
                         Location coreLocation,
                         RitualStructureBuilder.BuildPlan buildPlan,
                         int remainingSeconds,
                         int totalSeconds) {
        this.weaponType = weaponType;
        this.starterId = starterId;
        this.center = center;
        this.coreLocation = coreLocation;
        this.buildPlan = buildPlan;
        this.remainingSeconds = remainingSeconds;
        this.totalSeconds = totalSeconds;
    }

    public WeaponType weaponType() {
        return weaponType;
    }

    public UUID starterId() {
        return starterId;
    }

    public Location center() {
        return center;
    }

    public Location coreLocation() {
        return coreLocation;
    }

    public RitualStructureBuilder.BuildPlan buildPlan() {
        return buildPlan;
    }

    public int totalSeconds() {
        return totalSeconds;
    }

    public int remainingSeconds() {
        return remainingSeconds;
    }

    public void setRemainingSeconds(int remainingSeconds) {
        this.remainingSeconds = remainingSeconds;
    }

    public int emptySeconds() {
        return emptySeconds;
    }

    public void setEmptySeconds(int emptySeconds) {
        this.emptySeconds = emptySeconds;
    }

    public boolean paused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public Set<UUID> playersInZone() {
        return playersInZone;
    }

    public Material expectedCoreMaterial() {
        return expectedCoreMaterial;
    }

    public void setExpectedCoreMaterial(Material expectedCoreMaterial) {
        this.expectedCoreMaterial = expectedCoreMaterial;
    }

    public BukkitTask buildTask() {
        return buildTask;
    }

    public void setBuildTask(BukkitTask buildTask) {
        this.buildTask = buildTask;
    }

    public BukkitTask timerTask() {
        return timerTask;
    }

    public void setTimerTask(BukkitTask timerTask) {
        this.timerTask = timerTask;
    }

    public BukkitTask fxTask() {
        return fxTask;
    }

    public void setFxTask(BukkitTask fxTask) {
        this.fxTask = fxTask;
    }

    public BossBar bossBar() {
        return bossBar;
    }

    public void setBossBar(BossBar bossBar) {
        this.bossBar = bossBar;
    }

    public ItemDisplay display() {
        return display;
    }

    public void setDisplay(ItemDisplay display) {
        this.display = display;
    }

    public TextDisplay progressDisplay() {
        return progressDisplay;
    }

    public void setProgressDisplay(TextDisplay progressDisplay) {
        this.progressDisplay = progressDisplay;
    }

    public ItemDisplay coreDisplay() {
        return coreDisplay;
    }

    public void setCoreDisplay(ItemDisplay coreDisplay) {
        this.coreDisplay = coreDisplay;
    }

    public ItemDisplay workbenchDisplay() {
        return workbenchDisplay;
    }

    public void setWorkbenchDisplay(ItemDisplay workbenchDisplay) {
        this.workbenchDisplay = workbenchDisplay;
    }

    public List<Display> forgeDisplays() {
        return forgeDisplays;
    }

    public void addForgeDisplay(Display display) {
        forgeDisplays.add(display);
    }

    public void clearForgeDisplays() {
        forgeDisplays.clear();
    }

    public List<Display> offeringDisplays() {
        return offeringDisplays;
    }

    public void addOfferingDisplay(Display display) {
        offeringDisplays.add(display);
    }

    public void clearOfferingDisplays() {
        offeringDisplays.clear();
    }

    public Map<Integer, RitualBoxService.RitualOffering> offeringRequired() {
        return offeringRequired;
    }

    public void setOfferingRequired(Map<Integer, RitualBoxService.RitualOffering> offeringRequired) {
        this.offeringRequired = new HashMap<>(offeringRequired);
        this.offeringProvided.clear();
    }

    public Map<Integer, Integer> offeringProvided() {
        return offeringProvided;
    }

    public void highlightOfferingSlot(int slot, long untilMillis) {
        offeringHighlights.put(slot, untilMillis);
    }

    public boolean offeringSlotHighlighted(int slot) {
        Long until = offeringHighlights.get(slot);
        return until != null && until > System.currentTimeMillis();
    }

    public void clearExpiredOfferingHighlights() {
        long now = System.currentTimeMillis();
        offeringHighlights.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    public boolean offeringSlotComplete(int slot) {
        RitualBoxService.RitualOffering required = offeringRequired.get(slot);
        if (required == null) {
            return true;
        }
        return offeringProvided.getOrDefault(slot, 0) >= required.amount();
    }

    public boolean offeringsComplete() {
        if (offeringRequired.isEmpty()) {
            return false;
        }
        for (int slot : offeringRequired.keySet()) {
            if (!offeringSlotComplete(slot)) {
                return false;
            }
        }
        return true;
    }

    public boolean ritualStarted() {
        return ritualStarted;
    }

    public void setRitualStarted(boolean ritualStarted) {
        this.ritualStarted = ritualStarted;
    }

    public boolean revealed() {
        return revealed;
    }

    public void setRevealed(boolean revealed) {
        this.revealed = revealed;
    }

    public boolean manuallyStopped() {
        return manuallyStopped;
    }

    public void setManuallyStopped(boolean manuallyStopped) {
        this.manuallyStopped = manuallyStopped;
    }

    public boolean dungeonRitual() {
        return dungeonRitual;
    }

    public void setDungeonRitual(boolean dungeonRitual) {
        this.dungeonRitual = dungeonRitual;
    }
}
