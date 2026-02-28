package org.tridelth.wurmunlimited.mods.forsaken;

import com.wurmonline.server.HistoryManager;
import com.wurmonline.server.Items;
import com.wurmonline.server.NoSuchItemException;
import com.wurmonline.server.Message;
import com.wurmonline.server.MessageServer;
import com.wurmonline.server.Players;
import com.wurmonline.server.Server;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.Creatures;
import com.wurmonline.server.creatures.CreatureStatus;
import com.wurmonline.server.economy.Economy;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.Skills;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.zones.Zones;
import com.wurmonline.server.villages.Villages;
import com.wurmonline.server.villages.Village;
import com.wurmonline.server.creatures.ai.PathTile;
import com.wurmonline.server.creatures.ai.CreatureAIData;
import com.wurmonline.server.creatures.Communicator;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.nio.ByteBuffer;

public class ForsakenManager {
    private static final Logger logger = Logger.getLogger(ForsakenManager.class.getName());
    private static final Map<Long, Integer> forsakenKills = new ConcurrentHashMap<>();
    private static final Map<Long, String> originalNames = new ConcurrentHashMap<>();
    private static final Map<Long, String> forsakenNames = new ConcurrentHashMap<>();
    private static final Map<Long, Long> processedDeaths = new ConcurrentHashMap<>();
    private static final Map<Long, List<String>> victimsByKiller = new ConcurrentHashMap<>();
    private static final Map<Long, Long> lastTeleportTime = new ConcurrentHashMap<>();
    private static final Map<Long, Long> lastVisualEffectSent = new ConcurrentHashMap<>();
    private static final Map<Long, Long> pendingRemoval = new ConcurrentHashMap<>();
    private static final Map<Long, PendingTransfer> pendingTransfers = new ConcurrentHashMap<>();
    private static final Map<Long, float[]> lastKnownPos = new ConcurrentHashMap<>();
    private static final Map<Long, String> lastKnownName = new ConcurrentHashMap<>();
    private static final byte FORSAKEN_REWARD_AUX = 123;
    private static final int[] woodMaterials = new int[] { 14, 37, 38, 39, 40, 41, 42, 43, 44, 45, 63, 64, 65, 66, 46, 47, 48, 49, 50, 51 };
    
    private static class PendingTransfer {
        final List<com.wurmonline.server.items.Item> items = new ArrayList<>();
        final long startTime;
        final String ownerName;
        final float x, y, z;
        final int tileX, tileY;
        final boolean surface;

        PendingTransfer(String ownerName, float x, float y, float z, int tileX, int tileY, boolean surface) {
            this.startTime = System.currentTimeMillis();
            this.ownerName = ownerName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.tileX = tileX;
            this.tileY = tileY;
            this.surface = surface;
        }
    }

    private static byte getRandomWood() {
        return (byte)woodMaterials[rand.nextInt(woodMaterials.length)];
    }
    
    private static class TempEffectData {
        float x, y, z;
        byte layer;
        long expiry;
        short effectId;
        String name;
        float maxDistanceSq = 160000.0f; // Default 100 tiles (400m)
        long sourceItemId = -1L;
        long sourceCreatureId = -1L;

        TempEffectData(float x, float y, float z, byte layer, long expiry, short effectId, String name) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.layer = layer;
            this.expiry = expiry;
            this.effectId = effectId;
            this.name = name;
        }

        TempEffectData(float x, float y, float z, byte layer, long expiry, short effectId, String name, float maxDistanceTiles) {
            this(x, y, z, layer, expiry, effectId, name);
            float dist = maxDistanceTiles * 4.0f;
            this.maxDistanceSq = dist * dist;
        }

        TempEffectData(float x, float y, float z, byte layer, long expiry, short effectId, String name, float maxDistanceTiles, long sourceCreatureId) {
            this(x, y, z, layer, expiry, effectId, name, maxDistanceTiles);
            this.sourceCreatureId = sourceCreatureId;
        }
    }

    private static class TrailPoint {
        final List<Long> effectIds;
        final float x, y;
        final long startTime;
        long lastRefresh;

        TrailPoint(List<Long> effectIds, float x, float y) {
            this.effectIds = effectIds;
            this.x = x;
            this.y = y;
            this.startTime = System.currentTimeMillis();
            this.lastRefresh = this.startTime;
        }
    }

    private static final Map<Long, TempEffectData> tempEffects = new ConcurrentHashMap<>();
    private static long lastTempSync = 0;
    private static final Map<Long, Map<Long, VisualState>> perPlayerVisuals = new ConcurrentHashMap<>();
    private static final Map<Long, List<TrailPoint>> creatureTrailPoints = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> villageFireworkIndex = new ConcurrentHashMap<>();
    private static final Map<Long, Long> lastHuntedMessage = new ConcurrentHashMap<>();
    private static final List<PendingFirework> pendingFireworks = Collections.synchronizedList(new ArrayList<>());
    private static int forsakenCountToday = 0;
    private static long lastResetTime = 0;
    private static volatile boolean initialized = false;
    private static long celebrationEndTime = 0;
    private static long lastCelebrationFire = 0;
    private static long effectIdCounter = 0;
    private static boolean loggedVisualError = false;
    private static long lastAnnouncement = 0;
    private static long lastHunt = 0;
    private static int celebrationFireworkIndex = 0; // Removed usage
    private static final Random rand = new Random();

    private static class VisualState {
        float x, y, z;
        byte effectId;
        byte layer;
        long lastUpdate;

        VisualState(float x, float y, float z, byte effectId, byte layer) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.effectId = effectId;
            this.layer = layer;
            this.lastUpdate = System.currentTimeMillis();
        }

        boolean shouldUpdate(float nx, float ny, float nz, byte nEffectId, byte nLayer) {
            long now = System.currentTimeMillis();
            // Force refresh every 10 or 20 seconds to ensure effects stay active or cycle
            // Xmas lights (4) refreshed every configured celebration duration
            long refreshInterval = (nEffectId == 1) ? 20000L : (nEffectId == 4 ? ForsakenConfig.celebrationDuration * 60000L : 10000L);
            if (now - lastUpdate > refreshInterval) return true;
            
            if (this.effectId != nEffectId) return true;
            if (this.layer != nLayer) return true;
            
            // If it's lightning, don't update just because of movement (it's a momentary effect)
            if (nEffectId == 1) return false;

            float dx = x - nx;
            float dy = y - ny;
            float dz = z - nz;
            return (dx * dx + dy * dy + dz * dz) > 1.0f; // Moved more than 1 meter
        }
    }

    private static class PendingFirework {
        long time;
        Village village;
        short type;
        float x, y, z;
        byte layer;

        PendingFirework(long time, Village village, short type, float x, float y, float z, byte layer) {
            this.time = time;
            this.village = village;
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
            this.layer = layer;
        }
    }

    private static final Map<Long, Integer> playerOptInStatus = new ConcurrentHashMap<>();
    private static final Map<Long, Long> lastOptInReminder = new ConcurrentHashMap<>();
    private static final Map<Long, Map<Integer, Double>> playerSkillsBeforeDeath = new ConcurrentHashMap<>();

    public synchronized static void load() {
        if (initialized) return;

        // Ensure configuration is loaded for this class context (handles multiple classloaders)
        if (!ForsakenConfig.isLoaded()) {
            ForsakenConfig.load();
        }
        
        // Ensure database is initialized
        ForsakenDatabase.init();

        forsakenKills.clear();
        forsakenKills.putAll(ForsakenDatabase.loadForsakenKills());
        originalNames.clear();
        originalNames.putAll(ForsakenDatabase.loadOriginalNames());
        forsakenNames.clear();
        forsakenNames.putAll(ForsakenDatabase.loadForsakenNames());
        victimsByKiller.clear();
        victimsByKiller.putAll(ForsakenDatabase.loadAllVictims());
        forsakenCountToday = ForsakenDatabase.loadDailyCount();
        lastResetTime = ForsakenDatabase.loadLastResetTime();
        playerOptInStatus.clear();
        playerOptInStatus.putAll(ForsakenDatabase.loadAllPlayerOptIns());
        
        // If lastResetTime is not yet initialized in DB, or is 0, set to now
        if (lastResetTime <= 0) {
            lastResetTime = System.currentTimeMillis();
            ForsakenDatabase.saveDailyLimit(forsakenCountToday, lastResetTime);
        }
        
        int classHash = System.identityHashCode(ForsakenConfig.class);
        logger.info("[" + ForsakenConfig.MOD_VERSION + "] Loaded " + forsakenKills.size() + " Forsaken creatures and " + playerOptInStatus.size() + " player settings from database. Daily count: " + forsakenCountToday + " / " + ForsakenConfig.maxForsakenPerDay + " (ConfigID: " + classHash + ")");
        if (forsakenCountToday > ForsakenConfig.maxForsakenPerDay) {
            logger.info("[" + ForsakenConfig.MOD_VERSION + "] Note: Daily limit (" + ForsakenConfig.maxForsakenPerDay + ") is already exceeded by current count (" + forsakenCountToday + ").");
        }
        
        initialized = true;
        
        // Apply traits to existing creatures after a short delay to ensure everything is initialized
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                for (Long id : forsakenKills.keySet()) {
                    try {
                        Creature c = Creatures.getInstance().getCreatureOrNull(id);
                        if (c != null && !c.isDead()) {
                            applyForsakenTraits(c);
                            updateInventoryRewards(c);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }, 5000L);
    }

    private static boolean isAdminKill() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (int i = 0; i < Math.min(stack.length, 15); i++) {
            String mn = stack[i].getMethodName().toLowerCase();
            if (mn.contains("wizkill") || mn.contains("devkill")) {
                return true;
            }
        }
        return false;
    }

    public static void checkForsaken(Creature victim) {
        if (!initialized || victim == null) return;
        
        // Safety check to ensure we only process actual deaths
        if (!victim.isDead()) {
            // Some hooks fire just before isDead is set, but we should be careful with combat hooks
        }
        
        if (victim.isPlayer()) {
            // Player death logic
        }
        
        // Detect admin-triggered deaths (WIZKILL/devKill) to avoid awarding bounty to last opponent
        Creature killer = isAdminKill() ? null : findKiller(victim);
        checkForsaken(victim, killer);
    }

    public static void checkForsaken(long victimId, long killerId) {
        try {
            Creature victim = Creatures.getInstance().getCreatureOrNull(victimId);
            if (victim == null) victim = Players.getInstance().getPlayerOrNull(victimId);
            
            Creature killer = Creatures.getInstance().getCreatureOrNull(killerId);
            if (killer == null) killer = Players.getInstance().getPlayerOrNull(killerId);
            
            checkForsaken(victim, killer);
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Error in checkForsaken (ID redirect): " + t.getMessage(), t);
        }
    }

    private static boolean wasAttackedByGuards(Creature victim) {
        try {
            Map<Long, Long> attackers = ReflectionUtil.getPrivateField(victim, ReflectionUtil.getField(Creature.class, "attackers"));
            if (attackers != null && !attackers.isEmpty()) {
                long now = System.currentTimeMillis();
                long thirtyMinutesAgo = now - (30 * 60 * 1000L);
                for (Map.Entry<Long, Long> entry : attackers.entrySet()) {
                    if (entry.getValue() > thirtyMinutesAgo) {
                        Creature attacker = Creatures.getInstance().getCreatureOrNull(entry.getKey().longValue());
                        if (attacker != null && !attacker.isPlayer()) {
                            if (attacker.isGuard() || attacker.isSpiritGuard()) {
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static Creature findKiller(Creature victim) {
        Creature directKiller = findDirectKiller(victim);
        if (directKiller == null) return null;
        
        // Attribution logic: If the killer is a pet or charmed creature, return the owner
        try {
            Creature dominator = directKiller.getDominator();
            if (dominator != null && dominator.isPlayer()) {
                if (ForsakenConfig.debug) logger.info("FORSAKEN_DEBUG: Attributing kill by " + directKiller.getName() + " to owner " + dominator.getName());
                return dominator;
            }
        } catch (Throwable ignored) {}
        
        return directKiller;
    }

    private static Creature findDirectKiller(Creature victim) {
        Creature killer = null;
        Class creatureClass = Creature.class;

        // Strategy -1: Prioritize existing Forsaken among attackers (Group Kill Handling)
        try {
            Map<Long, Long> attackers = ReflectionUtil.getPrivateField(victim, ReflectionUtil.getField(creatureClass, "attackers"));
            if (attackers != null && !attackers.isEmpty()) {
                for (Long id : attackers.keySet()) {
                    Creature potential = Creatures.getInstance().getCreatureOrNull(id);
                    if (potential != null && isForsaken(potential)) {
                        return potential;
                    }
                }
            }
        } catch (Exception ignored) {}

        // Strategy 0: lastOpponent field
        try {
            killer = (Creature) ReflectionUtil.getPrivateField(victim, ReflectionUtil.getField(creatureClass, "lastOpponent"));
            if (killer != null) {
                return killer;
            }
        } catch (Exception ignored) {}

        // Strategy 1: Creature.getHitsetter() (Legacy)
        try {
            killer = (Creature) ReflectionUtil.callPrivateMethod(victim, ReflectionUtil.getMethod(creatureClass, "getHitsetter", new Class[0]), new Object[0]);
            if (killer != null) {
                return killer;
            }
        } catch (Exception ignored) {}

        // Strategy 2: Creature.getHitSetter()
        try {
            killer = (Creature) ReflectionUtil.callPrivateMethod(victim, ReflectionUtil.getMethod(creatureClass, "getHitSetter", new Class[0]), new Object[0]);
            if (killer != null) {
                return killer;
            }
        } catch (Exception ignored) {}

        // Strategy 3: Status.getHitsetter()
        try {
            Object status = ReflectionUtil.callPrivateMethod(victim, ReflectionUtil.getMethod(creatureClass, "getStatus", new Class[0]), new Object[0]);
            if (status != null) {
                long killerId = -10L;
                try {
                    killerId = (Long) ReflectionUtil.callPrivateMethod(status, ReflectionUtil.getMethod(status.getClass(), "getHitsetter", new Class[0]), new Object[0]);
                } catch (Exception e) {
                    try {
                        killerId = (Long) ReflectionUtil.callPrivateMethod(status, ReflectionUtil.getMethod(status.getClass(), "getHitSetter", new Class[0]), new Object[0]);
                    } catch (Exception e2) {}
                }
                
                if (killerId != -10L && killerId != -1L) {
                    killer = Creatures.getInstance().getCreatureOrNull(killerId);
                    if (killer == null) killer = Players.getInstance().getPlayerOrNull(killerId);
                    if (killer != null) {
                        return killer;
                    }
                }
            }
        } catch (Exception ignored) {}

        // Strategy 4: Attackers map
        try {
            Map<Long, Long> attackers = ReflectionUtil.getPrivateField(victim, ReflectionUtil.getField(creatureClass, "attackers"));
            if (attackers != null && !attackers.isEmpty()) {
                long latestTime = 0;
                long killerId = -1;
                for (Map.Entry<Long, Long> entry : attackers.entrySet()) {
                    if (entry.getValue() > latestTime) {
                        latestTime = entry.getValue();
                        killerId = entry.getKey();
                    }
                }
                if (killerId != -1) {
                    killer = Creatures.getInstance().getCreatureOrNull(killerId);
                    if (killer == null) killer = Players.getInstance().getPlayerOrNull(killerId);
                    if (killer != null) {
                        return killer;
                    }
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    public static void checkForsaken(Creature victim, Creature killer) {
        if (victim == null) return;
        
        long victimId = victim.getWurmId();
        long now = System.currentTimeMillis();
        
        if (victim.isPlayer() && ForsakenConfig.enableSkillRetention) {
            boolean shouldRetain = false;
            // Check if killer is a creature (non-player)
            if (killer != null && !killer.isPlayer()) {
                if (ForsakenConfig.retainOnAnyCreatureDeath || (ForsakenConfig.retainOnForsakenDeath && isForsaken(killer))) {
                    shouldRetain = true;
                }
            }
            
            if (!shouldRetain) {
                // Double check if any recent attacker was a Forsaken beast
                try {
                    Map<Long, Long> attackers = org.gotti.wurmunlimited.modloader.ReflectionUtil.getPrivateField(victim, org.gotti.wurmunlimited.modloader.ReflectionUtil.getField(Creature.class, "attackers"));
                    if (attackers != null && ForsakenConfig.retainOnForsakenDeath) {
                        for (Long id : attackers.keySet()) {
                            if (isForsaken(id)) {
                                shouldRetain = true;
                                break;
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
            
            if (shouldRetain) {
                recordSkillsBeforeDeath((Player)victim);
            }
        }

        if (isForsaken(victim)) {
            if (ForsakenConfig.debug) logger.info("FORSAKEN_DEBUG: checkForsaken called for " + victim.getName() + " (isForsaken=true). Killer: " + (killer != null ? killer.getName() : "null"));
        }
        
        // Robust death processing: only once per 10 seconds per ID
        if (processedDeaths.containsKey(victimId) && now - processedDeaths.get(victimId) < 10000L) {
            if (isForsaken(victim) && ForsakenConfig.debug) logger.info("FORSAKEN_DEBUG: checkForsaken for " + victim.getName() + " returning early (already processed recently)");
            return;
        }
        
        // Double check if victim is player/forsaken to avoid unnecessary processing
        if (!victim.isPlayer() && !isForsaken(victim)) {
            return;
        }

        processedDeaths.put(victimId, now);
        
        // Record last known state for reward items
        lastKnownPos.put(victimId, new float[] { victim.getPosX(), victim.getPosY(), victim.getPositionZ(), (float)victim.getTileX(), (float)victim.getTileY(), victim.isOnSurface() ? 1.0f : 0.0f });
        lastKnownName.put(victimId, victim.getName());
        
        if (victim.isPlayer()) {
            // Group check: If slayer is not Forsaken, but someone else in the group IS, switch to them.
            if (killer != null && !isForsaken(killer)) {
                try {
                    Map<Long, Long> attackers = ReflectionUtil.getPrivateField(victim, ReflectionUtil.getField(Creature.class, "attackers"));
                    if (attackers != null && attackers.size() > 1) {
                        for (Long id : attackers.keySet()) {
                            Creature potential = Creatures.getInstance().getCreatureOrNull(id);
                            if (potential != null && isForsaken(potential)) {
                                killer = potential;
                                break;
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (killer != null && !killer.isPlayer()) {
                // A player was killed by a creature
                
                // Exclude uniques
                if (killer.isUnique()) {
                    if (ForsakenConfig.debug) logger.info("Creature " + killer.getName() + " is Unique. Skipping Forsaken check.");
                    return;
                }
                
                // Exclude creatures on deeds
                try {
                    Village v = Villages.getVillageForCreature(killer);
                    if (v != null) {
                        if (ForsakenConfig.debug) logger.info("Creature " + killer.getName() + " is on deed " + v.getName() + ". Skipping Forsaken check.");
                        return;
                    }
                } catch (Throwable t) {
                    Village v = Villages.getVillage(killer.getTileX(), killer.getTileY(), killer.isOnSurface());
                    if (v != null) {
                        if (ForsakenConfig.debug) logger.info("Creature " + killer.getName() + " is on deed " + v.getName() + " (fallback check). Skipping Forsaken check.");
                        return;
                    }
                }

                recordVictim(killer, victim.getName());
                if (!isForsaken(killer)) {
                    if (forsakenCountToday >= ForsakenConfig.maxForsakenPerDay) {
                        if (ForsakenConfig.debug) logger.info("Daily Forsaken limit reached (" + ForsakenConfig.maxForsakenPerDay + "). Not making " + killer.getName() + " Forsaken.");
                        return;
                    }
                    if (rand.nextFloat() < ForsakenConfig.forsakenChance) {
                        makeForsaken(killer);
                    } else {
                        if (ForsakenConfig.debug) logger.info("Creature " + killer.getName() + " killed a player but failed forsaken chance check.");
                    }
                } else {
                    upgradeForsaken(killer);
                }
            } else if (killer != null) {
                if (ForsakenConfig.debug) logger.info("Player killed by another player or non-creature: " + killer.getName());
            }
        } else {
            // A creature died
            if (isForsaken(victim)) {
                if (killer != null && killer.isPlayer() && killer.getPower() < ForsakenConfig.adminPowerLevel) {
                    if (wasAttackedByGuards(victim)) {
                        if (ForsakenConfig.debug) logger.info("Player " + killer.getName() + " denied bounty for " + victim.getName() + " due to guard involvement.");
                        killer.getCommunicator().sendNormalServerMessage("The guards have already weakened this Forsaken. You receive no bounty.");
                        
                        String message = "The guards have weakened the Forsaken " + victim.getName() + "! No bounty is awarded.";
                        broadcastGlobal(message, 255, 128, 0);

                        // Delete rewards when guards were involved
                        clearInventoryRewards(victim, true);

                        // Cleanup without awarding bounty
                        long id = victim.getWurmId();
                        pendingRemoval.put(id, now);
                        try { removeForsakenVisuals(id); } catch (Throwable ignored) {}
                        startCelebration();
                    } else {
                        awardBounty(victim, killer);
                    }
                } else {
                    // Died to something else, just remove from tracking later
                    long id = victim.getWurmId();
                    String killerName = (killer != null ? killer.getName() : "unknown sources");
                    
                    String message;
                    if ((killer != null && (killer.isGuard() || killer.isSpiritGuard() || killer.getPower() >= ForsakenConfig.adminPowerLevel)) || isAdminKill()) {
                        message = "The guards or the gods have finally brought down the Forsaken " + victim.getName() + "!";
                        // Delete rewards when guards or admins killed it
                        clearInventoryRewards(victim, true);
                    } else {
                        message = "The Forsaken " + victim.getName() + " has been slain by " + killerName + "!";
                    }
                    broadcastGlobal(message, 255, 128, 0);
                    
                    if (ForsakenConfig.debug) logger.info("Forsaken creature " + victim.getName() + " died to non-player (" + killerName + "). Tracking will be removed in 10s.");
                    
                    try { removeForsakenVisuals(id); } catch (Throwable ignored) {}
                    pendingRemoval.put(id, now);
                    startCelebration();
                }
            }
        }
    }

    public static boolean isForsaken(Creature creature) {
        return creature != null && forsakenKills.containsKey(creature.getWurmId());
    }

    public static boolean isForsaken(long creatureId) {
        return forsakenKills.containsKey(creatureId);
    }

    public static boolean isForsakenStatus(CreatureStatus status) {
        if (status == null) return false;
        try {
            // First check the most common fields to find the owner Creature
            Class<?> clazz = status.getClass();
            while (clazz != null && clazz != Object.class) {
                // Try direct field access for known names
                try {
                    java.lang.reflect.Field f = clazz.getDeclaredField("creature");
                    f.setAccessible(true);
                    Creature cret = (Creature) f.get(status);
                    if (cret != null && isForsaken(cret)) return true;
                } catch (NoSuchFieldException e) {}
                
                try {
                    java.lang.reflect.Field f = clazz.getDeclaredField("statusHolder");
                    f.setAccessible(true);
                    Creature cret = (Creature) f.get(status);
                    if (cret != null && isForsaken(cret)) return true;
                } catch (NoSuchFieldException e) {}

                // Fallback: search for any field that is of type Creature
                for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                    if (f.getType().equals(com.wurmonline.server.creatures.Creature.class)) {
                        try {
                            f.setAccessible(true);
                            Creature cret = (Creature) f.get(status);
                            if (cret != null && isForsaken(cret)) return true;
                        } catch (Throwable ignored) {}
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Throwable t) {}
        return false;
    }

    private static String generateRandomName() {
        String firstName = ForsakenConfig.firstNames[rand.nextInt(ForsakenConfig.firstNames.length)];
        StringBuilder sb = new StringBuilder(firstName);
        
        if (ForsakenConfig.enableSecondNames) {
            String secondName = ForsakenConfig.secondNames[rand.nextInt(ForsakenConfig.secondNames.length)];
            sb.append(" ").append(secondName);
        }
        
        if (ForsakenConfig.enableTitles) {
            String title = ForsakenConfig.titles[rand.nextInt(ForsakenConfig.titles.length)];
            sb.append(" ").append(title);
        }
        
        return sb.toString().trim();
    }

    private static void makeForsaken(Creature creature) {
        makeForsaken(creature, null);
    }

    private static void makeForsaken(Creature creature, String forcedName) {
        try {
            long id = creature.getWurmId();
            String oldName = creature.getName();
            if (ForsakenConfig.debug) logger.info("Making " + oldName + " (" + id + ") Forsaken!");
            
            forsakenKills.put(id, 1);
            originalNames.put(id, oldName);
            
            String uniqueName = forcedName;
            if (uniqueName == null) {
                uniqueName = generateRandomName();
            }
            
            forsakenNames.put(id, uniqueName);
            ForsakenDatabase.saveForsaken(id, 1, oldName, uniqueName);
            
            forsakenCountToday++;
            ForsakenDatabase.saveDailyLimit(forsakenCountToday, lastResetTime);
            
            creature.setName(uniqueName);
            try {
                creature.refreshVisible();
            } catch (Throwable ignored) {}
            
            applyForsakenTraits(creature);
            upgradeStats(creature);
            updateInventoryRewards(creature);
            
            String bountyStr = getBountyString(creature);
            String message = "A " + creature.getTemplate().getName() + " has become Forsaken! It is now known as " + uniqueName + "!" + (bountyStr.isEmpty() ? "" : " Bounty: " + bountyStr);
            broadcastGlobal(message, 255, 0, 0);
            HistoryManager.addHistory(uniqueName, "has begun a rampage at " + creature.getTileX() + ", " + creature.getTileY() + "!");
            
            if (ForsakenConfig.debug) logger.info(oldName + " (" + id + ") has become Forsaken: " + uniqueName);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error in makeForsaken: " + e.getMessage(), e);
        }
    }

    private static void upgradeForsaken(Creature creature) {
        if (creature == null) return;
        try {
            long id = creature.getWurmId();
            int kills = forsakenKills.getOrDefault(id, 0) + 1;
            
            if (ForsakenConfig.debug) logger.info("FORSAKEN_DEBUG: upgradeForsaken called for " + creature.getName() + " to Level " + kills);
            
            if (kills > ForsakenConfig.maxLevel) {
                revertForsaken(creature);
                return;
            }
            
            forsakenKills.put(id, kills);
            String origName = originalNames.getOrDefault(id, creature.getTemplate().getName());
            String fName = forsakenNames.getOrDefault(id, creature.getName());
            ForsakenDatabase.saveForsaken(id, kills, origName, fName);
            
            upgradeStats(creature);
            applyForsakenTraits(creature);
            updateInventoryRewards(creature);
            
            String bountyStr = getBountyString(creature);
            String message = creature.getName() + " the Forsaken has claimed another victim! It grows stronger (Level " + kills + ")..." + (bountyStr.isEmpty() ? "" : " Bounty: " + bountyStr);
            broadcastGlobal(message, 255, 69, 0);
            
            if (ForsakenConfig.debug) logger.info(creature.getName() + " (" + id + ") now has " + kills + " kills.");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error in upgradeForsaken: " + e.getMessage(), e);
        }
    }

    private static void awardBounty(Creature forsaken, Creature killer) {
        long id = forsaken.getWurmId();
        int kills = forsakenKills.getOrDefault(id, 0);
        if (ForsakenConfig.debug) logger.info("FORSAKEN_DEBUG: awardBounty called for " + forsaken.getName() + " (Level " + kills + ") killed by " + (killer != null ? killer.getName() : "unknown"));
        
        pendingRemoval.put(id, System.currentTimeMillis());
        
        try { removeForsakenVisuals(id); } catch (Throwable ignored) {}
        startCelebration();
        
        if (kills > 0) {
            if (!ForsakenConfig.rewardEnabled) {
                return;
            }
            int level = Math.min(kills, ForsakenConfig.maxLevel);
            long ironAmount = ForsakenConfig.bounties[level];
            
            // Add status multipliers from BountyMod logic
            double statusMod = 1.0;
            try {
                if (forsaken.getStatus().isChampion()) statusMod *= 2.0;
                
                String name = forsaken.getName().toLowerCase();
                if (name.contains("fierce")) statusMod *= 1.5;
                if (name.contains("angry")) statusMod *= 1.4;
                if (name.contains("raging")) statusMod *= 1.6;
                if (name.contains("slow")) statusMod *= 0.95;
                if (name.contains("alert")) statusMod *= 1.2;
                if (name.contains("greenish")) statusMod *= 1.7;
                if (name.contains("lurking")) statusMod *= 1.1;
                if (name.contains("sly")) statusMod *= 0.8;
                if (name.contains("hardened")) statusMod *= 1.3;
                if (name.contains("scared")) statusMod *= 0.85;
                if (name.contains("diseased")) statusMod *= 0.9;
            } catch (Throwable ignored) {}
            
            ironAmount = (long)(ironAmount * statusMod);
            
            try {
                killer.addMoney(ironAmount);
                
                String coinMessage = Economy.getEconomy().getChangeFor(ironAmount).getChangeString();
                killer.getCommunicator().sendSafeServerMessage("You have been awarded " + coinMessage + " for slaying the Forsaken " + forsaken.getName() + "!");
                
                String message = killer.getName() + " has slain the Forsaken " + forsaken.getName() + " and claimed a bounty of " + coinMessage + "!";
                broadcastGlobal(message, 0, 255, 0);
                
                if (level >= ForsakenConfig.maxLevel) {
                    killer.getCommunicator().sendSafeServerMessage("The soul of the Forsaken " + forsaken.getName() + " was truly powerful. You feel a surge of energy!");
                    // Award something extra for max level Forsaken?
                    // Maybe some karma?
                    try {
                        ReflectionUtil.callPrivateMethod(killer, ReflectionUtil.getMethod(killer.getClass(), "modifyKarma", new Class[]{int.class}), new Object[]{1000});
                    } catch (Throwable ignored) {}
                }
                
                HistoryManager.addHistory(killer.getName(), "has slain the Forsaken " + forsaken.getName() + " at " + forsaken.getTileX() + ", " + forsaken.getTileY());
            } catch (java.io.IOException e) {
                logger.log(Level.WARNING, "Failed to award bounty to " + killer.getName() + ": " + e.getMessage(), e);
            }
        }
    }

    private static String getBountyString(Creature creature) {
        String bountyStr = "";
        if (ForsakenConfig.rewardEnabled) {
            try {
                long id = creature.getWurmId();
                int kills = forsakenKills.getOrDefault(id, 1);
                int level = Math.min(kills, ForsakenConfig.maxLevel);
                long ironAmount = ForsakenConfig.bounties[level];
                
                double statusMod = 1.0;
                try {
                    if (creature.getStatus().isChampion()) statusMod *= 2.0;
                    
                    String name = creature.getName().toLowerCase();
                    if (name.contains("fierce")) statusMod *= 1.5;
                    if (name.contains("angry")) statusMod *= 1.4;
                    if (name.contains("raging")) statusMod *= 1.6;
                    if (name.contains("slow")) statusMod *= 0.95;
                    if (name.contains("alert")) statusMod *= 1.2;
                    if (name.contains("greenish")) statusMod *= 1.7;
                    if (name.contains("lurking")) statusMod *= 1.1;
                    if (name.contains("sly")) statusMod *= 0.8;
                    if (name.contains("hardened")) statusMod *= 1.3;
                    if (name.contains("scared")) statusMod *= 0.85;
                    if (name.contains("diseased")) statusMod *= 0.9;
                } catch (Throwable ignored) {}
                
                ironAmount = (long)(ironAmount * statusMod);
                if (ironAmount > 0) {
                    bountyStr = Economy.getEconomy().getChangeFor(ironAmount).getChangeString();
                }
            } catch (Throwable t) {
                logger.warning("Error calculating bounty string: " + t.getMessage());
            }
        }
        
        if (ForsakenConfig.enableInventoryRewards) {
            if (bountyStr.isEmpty()) return "(Treasure)";
            return bountyStr + " (Treasure)";
        }
        
        return bountyStr;
    }

    private static void revertForsaken(Creature creature) {
        if (creature == null) return;
        if (ForsakenConfig.debug) logger.info("FORSAKEN_DEBUG: revertForsaken called for " + creature.getName());
        long id = creature.getWurmId();
        int kills = forsakenKills.getOrDefault(id, 0);
        forsakenKills.remove(id);
        String oldName = originalNames.remove(id);
        lastTeleportTime.remove(id);
        ForsakenDatabase.deleteForsaken(id);
        
        String forsakenName = creature.getName();
        if (oldName == null) oldName = creature.getTemplate().getName();
        creature.setName(oldName);
        ForsakenDatabase.deleteVictimsFor(id);
        victimsByKiller.remove(id);
        
        // Revert stats - Level 1 to max level gave upgrades per level
        revertStats(creature, kills);
        
        try {
            CreatureAIData aiData = creature.getCreatureAIData();
            if (aiData != null) {
                aiData.setSizeModifier(1.0f);
            }
            restoreVisualSize(creature);
        } catch (Throwable ignored) {}
        
        String message = "The Forsaken " + forsakenName + " has satisfied its bloodlust and reverted to a normal " + oldName + ".";
        broadcastGlobal(message, 0, 255, 255);
        startCelebration();
        
        if (ForsakenConfig.debug) logger.info(forsakenName + " (" + id + ") has reverted to normal state (" + oldName + ") after Level " + kills + ".");
    }

    private static void revertStats(Creature creature, int totalLevels) {
        try {
            if (totalLevels <= 0) return;
            if (ForsakenConfig.debug) logger.info("Reverting stats for " + creature.getName() + " (levels: " + totalLevels + ")");
            Skills skills = creature.getSkills();
            if (skills != null) {
                for (Skill skill : skills.getSkills()) {
                    int id = skill.getNumber();
                    double increasePerLevel = ForsakenConfig.individualSkillIncreases.getOrDefault(id, ForsakenConfig.defaultSkillIncrease);
                    double reduction = totalLevels * increasePerLevel;
                    double current = skill.getKnowledge();
                    skill.setKnowledge(Math.max(1.0, current - reduction), false);
                }
            }
            
            // Clear inventory rewards
            clearInventoryRewards(creature, false);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error in revertStats for " + creature.getName() + ": " + e.getMessage(), e);
        }
    }

    private static void setForsakenLevel(Creature creature, int targetLevel) {
        if (creature == null) return;
        try {
            long id = creature.getWurmId();
            int currentLevel = forsakenKills.getOrDefault(id, 0);
            
            if (targetLevel <= 0) {
                if (currentLevel > 0) {
                    revertForsaken(creature);
                }
                return;
            }
            
            if (targetLevel > ForsakenConfig.maxLevel) {
                targetLevel = ForsakenConfig.maxLevel;
            }

            if (currentLevel == 0) {
                // Not yet Forsaken - make it level 1 quietly
                String oldName = creature.getName();
                forsakenKills.put(id, 1);
                originalNames.put(id, oldName);
                String uniqueName = generateRandomName();
                forsakenNames.put(id, uniqueName);
                creature.setName(uniqueName);
                forsakenCountToday++;
                ForsakenDatabase.saveDailyLimit(forsakenCountToday, lastResetTime);
                try { creature.refreshVisible(); } catch (Throwable ignored) {}
                currentLevel = 1;
            }

            // Always revert ALL added stats from previous levels to be safe
            revertStats(creature, currentLevel);
            
            // Set new level
            forsakenKills.put(id, targetLevel);
            String origName = originalNames.getOrDefault(id, creature.getTemplate().getName());
            String fName = forsakenNames.getOrDefault(id, creature.getName());
            ForsakenDatabase.saveForsaken(id, targetLevel, origName, fName);
            
            // Apply stats for new level
            for (int i = 0; i < targetLevel; i++) {
                upgradeStats(creature);
            }
            
            // Refresh traits and rewards
            applyForsakenTraits(creature);
            updateInventoryRewards(creature);
            
            // Force visual refresh for size changes
            try { creature.refreshVisible(); } catch (Throwable ignored) {}
            
            if (ForsakenConfig.debug) logger.info("FORSAKEN_DEBUG: " + creature.getName() + " (" + id + ") level set to " + targetLevel);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error in setForsakenLevel: " + e.getMessage(), e);
        }
    }

    public static String getShortStackTrace() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        StringBuilder sb = new StringBuilder();
        for (int i = 2; i < Math.min(stack.length, 12); i++) {
            sb.append(stack[i].getMethodName()).append("->");
        }
        return sb.toString();
    }

    public static boolean isDebugItem(long wurmId) {
        try {
            com.wurmonline.server.items.Item item = com.wurmonline.server.Items.getItem(wurmId);
            return item != null && (item.getAuxData() == FORSAKEN_REWARD_AUX || item.getAuxData() == 69);
        } catch (Throwable t) { return false; }
    }

    public static boolean shouldCancelItemDestruction(long itemId) {
        try {
            com.wurmonline.server.items.Item item = com.wurmonline.server.Items.getItem(itemId);
            if (item == null) return false;
            
            if (item.getAuxData() != FORSAKEN_REWARD_AUX && item.getAuxData() != 69) {
                return false;
            }
            
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            boolean inDeathPath = false;
            String methodUsed = "";
            for (int i = 0; i < Math.min(stack.length, 20); i++) {
                String mn = stack[i].getMethodName().toLowerCase();
                if (mn.contains("clearinventoryrewards") || mn.contains("revertstats") || mn.contains("revertforsaken") || mn.contains("handleadminforsakencommand")) {
                    return false;
                }
                if (mn.equals("die") || mn.equals("setdead") || mn.equals("setdeatheffects") || mn.equals("destroy") || mn.equals("checkforsaken") || mn.equals("awardbounty") || mn.equals("permanentlydelete")) {
                    inDeathPath = true;
                    methodUsed = mn;
                    break;
                }
            }

            if (inDeathPath) {
                if (ForsakenConfig.debug) logger.info("FORSAKEN_DEBUG: BLOCKED destruction of reward item " + item.getName() + " (ID: " + itemId + ") during " + methodUsed);
                
                long ownerId = item.getOwnerId();
                if (ownerId != -10L) {
                    Creature owner = Creatures.getInstance().getCreatureOrNull(ownerId);
                    String ownerName = owner != null ? owner.getName() : lastKnownName.get(ownerId);
                    float[] pos = lastKnownPos.get(ownerId);
                    
                    if (ownerName != null && pos != null) {
                        // Try immediate transfer
                        com.wurmonline.server.items.Item corpse = findCorpseFor(ownerId, ownerName, (int)pos[3], (int)pos[4], pos[5] > 0.5f);
                        if (corpse != null) {
                            if (corpse.insertItem(item, true)) {
                                if (ForsakenConfig.debug) logger.info("FORSAKEN_DEBUG: Manually moved " + item.getName() + " to corpse " + corpse.getName());
                                return true;
                            }
                        }
                        
                        // If immediate failed, queue for later
                        if (ForsakenConfig.debug) logger.info("FORSAKEN_DEBUG: Queueing " + item.getName() + " for delayed corpse transfer (owner: " + ownerName + ")");
                        PendingTransfer transfer = pendingTransfers.computeIfAbsent(ownerId, k -> new PendingTransfer(ownerName, pos[0], pos[1], pos[2], (int)pos[3], (int)pos[4], pos[5] > 0.5f));
                        transfer.items.add(item);
                        return true;
                    }
                }
                return true;
            }
        } catch (Throwable t) {
            logger.warning("Error in shouldCancelItemDestruction: " + t.getMessage());
        }
        return false;
    }

    private static com.wurmonline.server.items.Item findCorpseFor(long ownerId, String ownerName, int tileX, int tileY, boolean surface) {
        if (ownerId == -10L) return null;
        try {
            com.wurmonline.server.zones.Zone zone = Zones.getZone(tileX, tileY, surface);
            if (zone == null) return null;
            
            com.wurmonline.server.items.Item[] tileItems = (com.wurmonline.server.items.Item[]) ReflectionUtil.callPrivateMethod(zone, ReflectionUtil.getMethod(zone.getClass(), "getAllItems"), new Object[0]);
            if (tileItems != null) {
                String ownerNameLower = ownerName != null ? ownerName.toLowerCase() : null;
                for (com.wurmonline.server.items.Item zi : tileItems) {
                    if (zi.getTemplateId() == 272) {
                        try {
                            long lastOwner = ReflectionUtil.getPrivateField(zi, ReflectionUtil.getField(zi.getClass(), "lastOwnerId"));
                            if (lastOwner == ownerId) return zi;
                        } catch (Exception e) {
                            try {
                                long lastOwner = ReflectionUtil.getPrivateField(zi, ReflectionUtil.getField(zi.getClass(), "lastOwner"));
                                if (lastOwner == ownerId) return zi;
                            } catch (Exception e2) {}
                        }
                        if (ownerNameLower != null && zi.getName().toLowerCase().contains(ownerNameLower)) {
                            return zi;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void clearInventoryRewards(Creature creature, boolean force) {
        try {
            if (creature == null) return;
            
            // Critical check: Never clear rewards if the creature is dead or currently dying
            boolean isDying = false;
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            String stackTrace = "";
            for (int i = 0; i < Math.min(stack.length, 10); i++) {
                String mn = stack[i].getMethodName().toLowerCase();
                stackTrace += stack[i].getMethodName() + "->";
                if (mn.equals("die") || mn.equals("setdead") || mn.equals("setdeatheffects") || mn.equals("destroy") || mn.equals("checkforsaken") || mn.equals("awardbounty") || mn.equals("permanentlydelete")) {
                    isDying = true;
                }
            }

            if (!force && (creature.isDead() || isDying)) {
                if (ForsakenConfig.debug) logger.info("FORSAKEN_DEBUG: Skipping clearInventoryRewards for " + creature.getName() + " because it is dead or dying (isDead=" + creature.isDead() + ", isDying=" + isDying + "). Stack: " + stackTrace);
                return;
            }
            
            if (ForsakenConfig.debug) logger.info("FORSAKEN_DEBUG: clearInventoryRewards proceeding for " + creature.getName() + (force ? " (FORCED)" : "") + ". Stack: " + stackTrace);
            
            com.wurmonline.server.items.Item inventory = creature.getInventory();
            if (inventory == null) return;
            
            Set<com.wurmonline.server.items.Item> items = inventory.getItems();
            if (items != null) {
                List<com.wurmonline.server.items.Item> toDestroy = new ArrayList<>();
                for (com.wurmonline.server.items.Item item : items) {
                    if (item.getAuxData() == FORSAKEN_REWARD_AUX || item.getAuxData() == 69) {
                        toDestroy.add(item);
                    }
                }
                if (!toDestroy.isEmpty()) {
                    if (ForsakenConfig.debug) logger.info("FORSAKEN_DEBUG: Clearing " + toDestroy.size() + " Forsaken rewards from " + creature.getName() + " (caller: " + (stack.length > 2 ? stack[2].getMethodName() : "unknown") + ")");
                    for (com.wurmonline.server.items.Item item : toDestroy) {
                        if (ForsakenConfig.debug) logger.info("  Destroying Forsaken item: " + item.getName() + " (ID: " + item.getWurmId() + ")");
                        com.wurmonline.server.Items.destroyItem(item.getWurmId());
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Error clearing inventory rewards for " + creature.getName() + ": " + e.getMessage());
        }
    }

    private static void upgradeStats(Creature creature) {
        try {
            if (ForsakenConfig.debug) logger.info("Upgrading stats for " + creature.getName());
            Skills skills = creature.getSkills();
            if (skills != null) {
                for (Skill skill : skills.getSkills()) {
                    int id = skill.getNumber();
                    double increase = ForsakenConfig.individualSkillIncreases.getOrDefault(id, ForsakenConfig.defaultSkillIncrease);
                    double current = skill.getKnowledge();
                    skill.setKnowledge(current + increase, false);
                }
            } else {
                logger.warning("No skills found for " + creature.getName());
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error in upgradeStats for " + creature.getName() + ": " + e.getMessage(), e);
        }
    }

    private static void updateInventoryRewards(Creature creature) {
        if (!ForsakenConfig.enableInventoryRewards) return;
        try {
            if (creature == null || creature.isDead()) return;
            long id = creature.getWurmId();
            int kills = forsakenKills.getOrDefault(id, 0);
            if (kills <= 0) return;
            int level = Math.min(kills, ForsakenConfig.maxLevel);

            // 1. Clear old mod-spawned items
            clearInventoryRewards(creature, false);
            
            com.wurmonline.server.items.Item inventory = creature.getInventory();
            if (inventory == null) return;
            
            // 2. Add new items for this level
            List<ForsakenConfig.RewardItem> rewards = ForsakenConfig.levelRewards.get(level);
            if ((rewards == null || rewards.isEmpty()) && ForsakenConfig.levelRewards.isEmpty()) {
                logger.warning("[" + ForsakenConfig.MOD_VERSION + "] Rewards map is empty during update for " + creature.getName() + ". Attempting emergency reload.");
                ForsakenConfig.load();
                rewards = ForsakenConfig.levelRewards.get(level);
            }
            
            if (rewards != null && !rewards.isEmpty()) {
                for (ForsakenConfig.RewardItem ri : rewards) {
                    for (int i = 0; i < ri.quantity; i++) {
                        try {
                            float ql = ri.quality;
                            if (ql < 0) {
                                ql = 50.0f + (rand.nextFloat() * 40.0f);
                            }
                            com.wurmonline.server.items.Item item;
                            
                            // Using robust creation method with creator for identification
                            item = com.wurmonline.server.items.ItemFactory.createItem(ri.id, ql, ri.rarity, "ForsakenMod");
                            
                            if (ri.material > 0) {
                                item.setMaterial(ri.material);
                            } else {
                                // If no material specified, and it's wooden, pick a random wood type
                                if (item.getTemplate() != null && item.getTemplate().isWood()) {
                                    item.setMaterial(getRandomWood());
                                }
                            }
                            
                            item.setAuxData(FORSAKEN_REWARD_AUX);
                            if (inventory.insertItem(item)) {
                                if (ForsakenConfig.debug) logger.info("Gave reward item " + item.getName() + " (ID: " + item.getWurmId() + ", QL: " + item.getQualityLevel() + ") to " + creature.getName());
                            } else {
                                logger.warning("Failed to insert reward item " + item.getName() + " into " + creature.getName() + "'s inventory.");
                                com.wurmonline.server.Items.destroyItem(item.getWurmId());
                            }
                        } catch (Exception e) {
                            logger.warning("Failed to create reward item " + ri.id + " for Forsaken " + creature.getName() + ": " + e.getMessage());
                        }
                    }
                }
            } else {
                if (ForsakenConfig.debug) logger.info("No rewards configured for Level " + level + " for creature " + creature.getName());
            }
        } catch (Exception e) {
            logger.warning("Error updating inventory rewards for " + creature.getName() + ": " + e.getMessage());
        }
    }

    private static void applyForsakenTraits(Creature creature) {
        try {
            if (creature == null) return;
            
            // Ensure the name is correct for Forsaken
            if (isForsaken(creature)) {
                String expectedName = forsakenNames.get(creature.getWurmId());
                if (expectedName != null && !creature.getName().contains(expectedName)) {
                    creature.setName(expectedName);
                    if (ForsakenConfig.debug) logger.info("Restored name of Forsaken " + expectedName);
                    try { creature.refreshVisible(); } catch (Throwable ignored) {}
                }
            }

            if (creature.shouldStandStill) {
                creature.shouldStandStill = false;
            }

            // Reset age and fat/disease/modtype to neutral values to avoid color interference from conditions like "young" or "fat"
            try {
                CreatureStatus status = creature.getStatus();
                if (status != null) {
                    Class<?> sc = status.getClass();
                    String[] ageFields = {"age", "a", "creatureAge"};
                    String[] fatFields = {"fat", "f", "creatureFat"};
                    String[] disFields = {"disease", "d", "creatureDisease"};
                    
                    Class<?> current = sc;
                    while (current != null && current != Object.class) {
                        for (String af : ageFields) {
                            try {
                                java.lang.reflect.Field f = ReflectionUtil.getField(current, af);
                                ReflectionUtil.setPrivateField(status, f, 3); // Mature (avoid "aged" or "ancient")
                                break;
                            } catch (NoSuchFieldException ignored) {}
                        }
                        for (String ff : fatFields) {
                            try {
                                java.lang.reflect.Field f = ReflectionUtil.getField(current, ff);
                                ReflectionUtil.setPrivateField(status, f, (byte)50); // Normal (avoid "starving" or "fat")
                                break;
                            } catch (NoSuchFieldException ignored) {}
                        }
                        for (String df : disFields) {
                            try {
                                java.lang.reflect.Field f = ReflectionUtil.getField(current, df);
                                ReflectionUtil.setPrivateField(status, f, (byte)0); // Healthy
                                break;
                            } catch (NoSuchFieldException ignored) {}
                        }
                        current = current.getSuperclass();
                    }
                }
            } catch (Throwable ignored) {}

            CreatureAIData aiData = creature.getCreatureAIData();
            if (aiData != null) {
                if (aiData.getSizeModifier() != 10.0f) {
                    aiData.setSizeModifier(10.0f);
                    if (ForsakenConfig.debug) logger.info("Applied 10x size modifier to Forsaken " + creature.getName());
                    // Ensure clients also see the visual size change
                    ensureVisualSize(creature);
                }
            }
        } catch (Throwable t) {
            logger.warning("Failed to apply traits to " + creature.getName() + ": " + t.getMessage());
        }
    }


    public static float getForsakenSizeMod(CreatureStatus status, float originalSize) {
        try {
            // Attempt to find the creature field - some versions use 'creature', some 'statusHolder'
            Creature cret = null;
            try {
                cret = (Creature) ReflectionUtil.getPrivateField(status, ReflectionUtil.getField(status.getClass(), "creature"));
            } catch (Exception e) {
                try {
                    cret = (Creature) ReflectionUtil.getPrivateField(status, ReflectionUtil.getField(status.getClass(), "statusHolder"));
                } catch (Exception e2) {}
            }
            
            if (cret != null && isForsaken(cret) && !cret.isDead()) {
                return 10.0f;
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Error in getForsakenSizeMod: " + t.getMessage());
        }
        return originalSize;
    }

    public static float getForsakenBCRMod(Creature cret, float originalBCR) {
        try {
            if (ForsakenConfig.baseCombatRatingIncrease <= 0) return originalBCR;
            
            if (cret != null && isForsaken(cret)) {
                int level = forsakenKills.getOrDefault(cret.getWurmId(), 0);
                if (level > 0) {
                    return originalBCR + (float)(level * ForsakenConfig.baseCombatRatingIncrease);
                }
            }
        } catch (Throwable t) {
            // No logging here to avoid spamming the log during combat calculations
        }
        return originalBCR;
    }

    public static float getForsakenArmourMod(Creature cret, float originalArmour) {
        try {
            if (ForsakenConfig.naturalArmourIncrease <= 0) return originalArmour;
            if (cret != null && isForsaken(cret)) {
                int level = forsakenKills.getOrDefault(cret.getWurmId(), 0);
                if (level > 0) {
                    return originalArmour + (level * ForsakenConfig.naturalArmourIncrease);
                }
            }
        } catch (Throwable t) {}
        return originalArmour;
    }

    public static float getForsakenSpeedMod(Creature cret, float originalSpeed) {
        try {
            if (ForsakenConfig.speedIncrease <= 0) return originalSpeed;
            if (cret != null && isForsaken(cret)) {
                int level = forsakenKills.getOrDefault(cret.getWurmId(), 0);
                if (level > 0) {
                    return originalSpeed + (level * ForsakenConfig.speedIncrease);
                }
            }
        } catch (Throwable t) {}
        return originalSpeed;
    }


    private static void ensureVisualSize(Creature creature) {
        if (creature == null) return;
        broadcastForsakenVisuals(creature);
    }

    private static void restoreVisualSize(Creature creature) {
        try {
            if (creature == null) return;
            int baseX = creature.getTemplate().getSizeModX() & 0xFF;
            int baseY = creature.getTemplate().getSizeModY() & 0xFF;
            int baseZ = creature.getTemplate().getSizeModZ() & 0xFF;
            byte bx = (byte)Math.max(1, Math.min(127, baseX));
            byte by = (byte)Math.max(1, Math.min(127, baseY));
            byte bz = (byte)Math.max(1, Math.min(127, baseZ));
            for (Player p : Players.getInstance().getPlayers()) {
                try {
                    p.getCommunicator().sendResize(creature.getWurmId(), bx, by, bz);
                } catch (Throwable ignored) {}
            }
            removeForsakenVisuals(creature.getWurmId());
            if (ForsakenConfig.debug) logger.info("Restored visual size and removed effect for " + creature.getName() + ".");
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Failed to restore visual size for " + creature.getName() + ": " + t.getMessage(), t);
        }
    }

    public static void sendForsakenVisuals(Player player) {
        if (player == null || player.getCommunicator() == null) return;
        
        if (!initialized) {
            load();
        }
        
        if (ForsakenConfig.enableOptIn) {
            int status = playerOptInStatus.getOrDefault(player.getWurmId(), 0);
            if (status == 0) {
                long now = System.currentTimeMillis();
                Long last = lastOptInReminder.get(player.getWurmId());
                if (last == null || now - last > 30000L) {
                    player.getCommunicator().sendAlertServerMessage("--- Forsaken Mod Opt-In Required ---");
                    player.getCommunicator().sendSafeServerMessage("Forsaken creatures hunt players across the map. You must decide if you wish to participate.");
                    player.getCommunicator().sendSafeServerMessage("Type /forsaken on to opt-in (allowing hunting/bounties) or /forsaken off to remain safe.");
                    lastOptInReminder.put(player.getWurmId(), now);
                }
            }
        }

        for (Long id : forsakenKills.keySet()) {
            try {
                Creature c = Server.getInstance().getCreature(id);
                if (c != null && !c.isDead()) {
                    sendForsakenEffect(player.getCommunicator(), c);
                }
            } catch (Exception ignored) {}
        }
    }

    public static void onPlayerLogout(Player player) {
        if (player != null) {
            perPlayerVisuals.remove(player.getWurmId());
        }
    }

    private static void sendForsakenEffect(Communicator comm, Creature c) {
        if (comm == null || c == null) return;
        try {
            long creatureId = c.getWurmId();
            float x = c.getPosX();
            float y = c.getPosY();
            float z = c.getPositionZ();
            byte layer = (byte) c.getLayer();

            Player player = comm.getPlayer();
            if (player == null) return;
            long playerId = player.getWurmId();

            // Distance in tiles (1 tile = 4 meters)
            float dx = player.getPosX() - x;
            float dy = player.getPosY() - y;
            float distTiles = (float)Math.sqrt(dx*dx + dy*dy) / 4.0f;
            
            // Distance-based effect selection
            short effectId;
            if (distTiles > 100.0f) {
                effectId = 25; // Red Light beam for distance
            } else {
                effectId = (short)ForsakenConfig.creatureEffect;
            }

            Map<Long, VisualState> playerMap = perPlayerVisuals.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
            VisualState lastState = playerMap.get(creatureId);

            boolean effectChanged = (lastState == null || lastState.effectId != effectId);

            // Only update if state changed or moved significantly
            if (lastState == null || lastState.shouldUpdate(x, y, z, (byte)effectId, layer)) {
                long posId = -creatureId;
                long surfaceId = -(creatureId + 1L);
                long stableEffectId = -(creatureId + 2L);

                if (effectId == 25) {
                    // If transitioning from lightning, or moved significantly, clear and re-add beam
                    if (lastState != null && (lastState.effectId != 25 || lastState.shouldUpdate(x, y, z, (byte)25, layer))) {
                        comm.sendRemoveEffect(posId);
                        comm.sendRemoveEffect(surfaceId);
                        comm.sendRemoveEffect(stableEffectId);
                    }
                    comm.sendAddEffect(posId, (byte)25, x, y, z, layer);
                    
                    if (layer < 0) {
                        float surfaceZ = 0.0f;
                        try {
                            surfaceZ = Zones.calculateHeight(x, y, true);
                        } catch (Throwable t) {
                            surfaceZ = c.getPositionZ() + 100.0f;
                        }
                        comm.sendAddEffect(surfaceId, (byte)25, x, y, surfaceZ, (byte) 0);
                    } else {
                        comm.sendRemoveEffect(surfaceId);
                    }
                } else if (effectId != -1 && effectId != 25) {
                    // Transitioning from beam?
                    if (lastState != null && lastState.effectId == 25) {
                        comm.sendRemoveEffect(posId);
                        comm.sendRemoveEffect(surfaceId);
                    }
                    
                    if (effectId == 27 && (ForsakenConfig.creatureParticleName == null || ForsakenConfig.creatureParticleName.equalsIgnoreCase("none"))) {
                        comm.sendRemoveEffect(stableEffectId);
                    } else {
                        // Use a stable ID for the creature's own effect to ensure it moves smoothly
                        if (effectId == 27) {
                            comm.sendAddEffect(stableEffectId, (short) 27, x, y, z, layer, ForsakenConfig.creatureParticleName, 10.0f, 0.0f);
                        } else {
                            comm.sendAddEffect(stableEffectId, (byte)effectId, x, y, z, layer);
                        }
                    }
                } else if (effectId == -1) {
                    comm.sendRemoveEffect(posId);
                    comm.sendRemoveEffect(surfaceId);
                    comm.sendRemoveEffect(stableEffectId);
                }

                playerMap.put(creatureId, new VisualState(x, y, z, (byte)effectId, layer));

                // Per-player sizing logic: ONLY send if effect changed (implies multiplier change) or first time
                if (effectChanged) {
                    // Match the 10x size modifier used on the server to avoid position/collision desync
                    float multiplier = 10.0f;
                    int baseX = c.getTemplate().getSizeModX() & 0xFF;
                    int baseY = c.getTemplate().getSizeModY() & 0xFF;
                    int baseZ = c.getTemplate().getSizeModZ() & 0xFF;
                    byte scaledX = (byte)Math.min(127, Math.max(1, (int)Math.round(baseX * multiplier)));
                    byte scaledY = (byte)Math.min(127, Math.max(1, (int)Math.round(baseY * multiplier)));
                    byte scaledZ = (byte)Math.min(127, Math.max(1, (int)Math.round(baseZ * multiplier)));
                    comm.sendResize(creatureId, scaledX, scaledY, scaledZ);
                }
            }
        } catch (Throwable t) {
            if (!loggedVisualError) {
                logger.warning("Error in sendForsakenEffect: " + t.getMessage());
                loggedVisualError = true;
            }
        }
    }

    private static void broadcastForsakenVisuals(Creature c) {
        if (c == null) return;
        for (Player p : Players.getInstance().getPlayers()) {
            if (p != null && p.getCommunicator() != null) {
                sendForsakenEffect(p.getCommunicator(), c);
            }
        }
    }

    private static void removeForsakenVisuals(long creatureId) {
        lastVisualEffectSent.remove(creatureId);
        // Clear from all player visual caches
        for (Map<Long, VisualState> playerMap : perPlayerVisuals.values()) {
            playerMap.remove(creatureId);
        }
        
        // Clear all temp effects associated with this creature (including trail effects)
        for (Map.Entry<Long, TempEffectData> entry : tempEffects.entrySet()) {
            if (entry.getValue().sourceCreatureId == creatureId) {
                removeEffectFromAll(entry.getKey());
            }
        }
        
        // Clear trail points and all associated effects
        List<TrailPoint> points = creatureTrailPoints.remove(creatureId);
        if (points != null) {
            synchronized (points) {
                for (TrailPoint tp : points) {
                    for (Long id : tp.effectIds) {
                        removeEffectFromAll(id);
                    }
                }
                points.clear();
            }
        }

        for (Player p : Players.getInstance().getPlayers()) {
            try {
                if (p != null && p.getCommunicator() != null) {
                    p.getCommunicator().sendRemoveEffect(creatureId);
                    p.getCommunicator().sendRemoveEffect(-creatureId);
                    p.getCommunicator().sendRemoveEffect(-(creatureId + 1L));
                    p.getCommunicator().sendRemoveEffect(-(creatureId + 2L));
                    // Cleanup legacy variations
                    p.getCommunicator().sendRemoveEffect(creatureId | 0x1000000000000000L);
                    p.getCommunicator().sendRemoveEffect(creatureId | 0x2000000000000000L);
                    p.getCommunicator().sendRemoveEffect(creatureId | 0x4000000000000000L);
                    
                    long itemEffectId = (creatureId & 0x00FFFFFFFFFFFFFFL) | (2L << 56);
                    p.getCommunicator().sendRemoveEffect(itemEffectId);
                    p.getCommunicator().sendRemoveEffect(itemEffectId + 1L);
                    
                    long globalEffectId = (creatureId & 0x00FFFFFFFFFFFFFFL) | (17L << 56);
                    p.getCommunicator().sendRemoveEffect(globalEffectId);
                    p.getCommunicator().sendRemoveEffect(globalEffectId + 1L);
                    p.getCommunicator().sendRemoveEffect(creatureId + 100000000L);
                    p.getCommunicator().sendRemoveEffect(creatureId + 100000001L);
                }
            } catch (Exception ignored) {}
        }
    }

    private static void ensureVisualEffect(Creature creature) {
        if (creature == null || !isForsaken(creature)) return;
        long now = System.currentTimeMillis();
        Long last = lastVisualEffectSent.get(creature.getWurmId());
        // Refresh every 500ms. Positional effects are updated here to keep up with movement.
        // Higher frequency ensures the beam stays centered on the moving Forsaken.
        if (last == null || now - last > 500L) {
            broadcastForsakenVisuals(creature);
            lastVisualEffectSent.put(creature.getWurmId(), now);
            
            // Add trail effects if players are nearby and enabled
            if (ForsakenConfig.enableTrailEffects) {
                handleTrailEffects(creature);
            }
        }
    }

    private static void handleTrailEffects(Creature c) {
        if (!isForsaken(c)) return;
        float x = c.getPosX();
        float y = c.getPosY();
        long cid = c.getWurmId();
        
        // Check if ANY player is within 100 tiles
        boolean playerNearby = false;
        for (Player p : Players.getInstance().getPlayers()) {
            if (p != null && p.getCommunicator() != null) {
                float dx = p.getPosX() - x;
                float dy = p.getPosY() - y;
                // 100 tiles * 4 meters = 400 meters
                if (dx*dx + dy*dy < 400.0f * 400.0f) {
                    playerNearby = true;
                    break;
                }
            }
        }
        
        if (!playerNearby) return;
        
        List<TrailPoint> points = creatureTrailPoints.computeIfAbsent(cid, k -> Collections.synchronizedList(new LinkedList<>()));
        TrailPoint activePoint = null;
        synchronized (points) {
            if (!points.isEmpty()) {
                activePoint = points.get(points.size() - 1);
            }
        }
        
        long now = System.currentTimeMillis();
        boolean shouldSpawn = false;
        if (activePoint == null) {
            shouldSpawn = true;
        } else {
            float dx = x - activePoint.x;
            float dy = y - activePoint.y;
            // Spawn new group if moved > 3 meters, or every 4 minutes if standing still
            if (dx*dx + dy*dy > 3.0f * 3.0f || now - activePoint.startTime > 240000L) {
                shouldSpawn = true;
            } else if (now - activePoint.lastRefresh > 8000L) {
                // Refresh ONLY particle effects for the active point every 8 seconds to keep them visible
                // Standard effects stay until removed, so they don't need refreshing.
                activePoint.lastRefresh = now;
                for (Long id : activePoint.effectIds) {
                    TempEffectData data = tempEffects.get(id);
                    if (data != null && data.effectId == 27) {
                        for (Player p : Players.getInstance().getPlayers()) {
                            if (p != null && p.getCommunicator() != null) {
                                float pdx = p.getPosX() - data.x;
                                float pdy = p.getPosY() - data.y;
                                if (pdx*pdx + pdy*pdy < 400.0f * 400.0f) {
                                    sendTempEffectToPlayer(p, id, data);
                                }
                            }
                        }
                    }
                }
            }
        }
        
        if (shouldSpawn) {
            if (activePoint != null) {
                // Previous point's effects now expire in 1 second
                for (Long id : activePoint.effectIds) {
                    TempEffectData data = tempEffects.get(id);
                    if (data != null) {
                        data.expiry = now + 1000L;
                    }
                }
            }
            
            // Spawn new point
            List<Long> newIds = spawnTrailAt(c, now);
            if (!newIds.isEmpty()) {
                points.add(new TrailPoint(newIds, x, y));
            }
            
            // Prune point history (max 20 points tracked per creature)
            synchronized (points) {
                while (points.size() > 20) {
                    TrailPoint old = points.remove(0);
                    // Ensure pruned point's effects are shortened
                    for (Long id : old.effectIds) {
                        TempEffectData data = tempEffects.get(id);
                        if (data != null && data.expiry > now + 1000L) {
                            data.expiry = now + 1000L;
                        }
                    }
                }
            }
        }
    }

    private static List<Long> spawnTrailAt(Creature c, long now) {
        if (!isForsaken(c)) return Collections.emptyList();
        float x = c.getPosX();
        float y = c.getPosY();
        float z = c.getPositionZ();
        byte layer = (byte) c.getLayer();
        long cid = c.getWurmId();
        
        // Initial long expiry (1 hour). Will be shortened to 10s when creature moves away.
        long expiry = now + 3600000L;
        
        List<Long> newEffects = new ArrayList<>();
        // Spawn 1 instance of each enabled trail slot
        if (ForsakenConfig.trailEffect1 != -1) {
            newEffects.add(spawnTempEffect((short) ForsakenConfig.trailEffect1, null, x, y, z, layer, expiry, cid));
        }
        if (ForsakenConfig.trailEffect2 != -1) {
            newEffects.add(spawnTempEffect((short) ForsakenConfig.trailEffect2, null, x, y, z, layer, expiry, cid));
        }
        
        // Final safety check
        if (!isForsaken(c)) {
            for (Long id : newEffects) {
                removeEffectFromAll(id);
            }
            return Collections.emptyList();
        }

        return newEffects;
    }

    private static long spawnTempEffect(short effectId, String name, float x, float y, float z, byte layer, long expiry, long sourceCreatureId) {
        // Use a random distance between 2 and 10 meters for better spread around the creature
        float dist = 2.0f + rand.nextFloat() * 8.0f;
        double angle = rand.nextDouble() * 2.0 * Math.PI;
        float ex = x + (float)(Math.cos(angle) * dist);
        float ey = y + (float)(Math.sin(angle) * dist);
        float ez = z;
        try {
            ez = Zones.calculateHeight(ex, ey, layer >= 0);
        } catch (Throwable ignored) {}
        
        long id = generateTempEffectId();
        TempEffectData data = new TempEffectData(ex, ey, ez, layer, expiry, effectId, name, 100.0f, sourceCreatureId);
        tempEffects.put(id, data);
        
        for (Player p : Players.getInstance().getPlayers()) {
            if (p != null && p.getCommunicator() != null) {
                float dx = p.getPosX() - ex;
                float dy = p.getPosY() - ey;
                // Only send to nearby players initially
                if (dx*dx + dy*dy < 400.0f * 400.0f) {
                    sendTempEffectToPlayer(p, id, data);
                    
                    // Update per-player visual cache
                    Map<Long, VisualState> playerMap = perPlayerVisuals.computeIfAbsent(p.getWurmId(), k -> new ConcurrentHashMap<>());
                    playerMap.put(id, new VisualState(ex, ey, ez, (byte)effectId, layer));
                }
            }
        }
        return id;
    }

    private static void sendTempEffectToPlayer(Player p, long id, TempEffectData data) {
        if (p == null || p.getCommunicator() == null) return;
        if (data.effectId == 27) {
            // Particle effect - use 10.0f duration (refreshed every 8s while active)
            p.getCommunicator().sendAddEffect(id, (short) 27, data.x, data.y, data.z, data.layer, data.name != null ? data.name : "rift01", 10.0f, 0.0f);
        } else {
            // Standard effect
            p.getCommunicator().sendAddEffect(id, (byte) data.effectId, data.x, data.y, data.z, data.layer);
        }
    }

    private static synchronized long generateTempEffectId() {
        // Use very high negative IDs for temporary effects to ensure they are treated as global positional objects.
        // Base starting at -3 billion to avoid collision with -creatureId.
        return - (3000000000L + (++effectIdCounter));
    }

    private static void removeEffectFromAll(long effectId) {
        tempEffects.remove(effectId);
        // Clear from all player visual caches
        for (Map<Long, VisualState> playerMap : perPlayerVisuals.values()) {
            playerMap.remove(effectId);
        }
        for (Player p : Players.getInstance().getPlayers()) {
            if (p != null && p.getCommunicator() != null) {
                p.getCommunicator().sendRemoveEffect(effectId);
            }
        }
    }

    private static void cleanTempEffects(long now) {
        if (tempEffects.isEmpty()) return;
        
        Iterator<Map.Entry<Long, TempEffectData>> it = tempEffects.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, TempEffectData> entry = it.next();
            TempEffectData data = entry.getValue();
            boolean shouldRemove = now > data.expiry;
            
            if (!shouldRemove && data.sourceItemId != -1L) {
                try {
                    com.wurmonline.server.items.Item item = Items.getItem(data.sourceItemId);
                    if (item.getOwnerId() != -10L || item.getZoneId() == -1) {
                        shouldRemove = true;
                    }
                } catch (NoSuchItemException e) {
                    shouldRemove = true;
                }
            }
            
            if (!shouldRemove && data.sourceCreatureId != -1L) {
                try {
                    Creature c = Creatures.getInstance().getCreatureOrNull(data.sourceCreatureId);
                    if (c == null || c.isDead()) {
                        shouldRemove = true;
                    }
                } catch (Exception e) {
                    shouldRemove = true;
                }
            }

            if (shouldRemove) {
                long id = entry.getKey();
                // Clear from all player visual caches
                for (Map<Long, VisualState> playerMap : perPlayerVisuals.values()) {
                    playerMap.remove(id);
                }
                for (Player p : Players.getInstance().getPlayers()) {
                    if (p != null && p.getCommunicator() != null) {
                        p.getCommunicator().sendRemoveEffect(id);
                    }
                }
                it.remove();
            }
        }
    }

    private static void syncTempEffects(long now) {
        for (Player p : Players.getInstance().getPlayers()) {
            if (p == null || p.getCommunicator() == null) continue;
            long pid = p.getWurmId();
            float px = p.getPosX();
            float py = p.getPosY();
            
            Map<Long, VisualState> pMap = perPlayerVisuals.computeIfAbsent(pid, k -> new ConcurrentHashMap<>());
            
            for (Map.Entry<Long, TempEffectData> entry : tempEffects.entrySet()) {
                long eid = entry.getKey();
                TempEffectData data = entry.getValue();
                
                // Beams/Lightning main creature effects are handled separately in sendForsakenEffect
                // but ID 4 (celebration lights) and trail effects use tempEffects.
                
                float dx = px - data.x;
                float dy = py - data.y;
                boolean inRange = (dx*dx + dy*dy < data.maxDistanceSq);
                
                VisualState state = pMap.get(eid);
                if (inRange && state == null) {
                    sendTempEffectToPlayer(p, eid, data);
                    pMap.put(eid, new VisualState(data.x, data.y, data.z, (byte)data.effectId, data.layer));
                } else if (!inRange && state != null) {
                    p.getCommunicator().sendRemoveEffect(eid);
                    pMap.remove(eid);
                }
            }
        }
    }

    private static void startCelebration() {
        if (!ForsakenConfig.enableCelebration) return;
        celebrationEndTime = System.currentTimeMillis() + (ForsakenConfig.celebrationDuration * 60000L);
        lastCelebrationFire = 0;
    }

    private static void fireCelebrationFireworks() {
        Village[] vills = Villages.getVillages();
        if (vills == null) return;
        
        long now = System.currentTimeMillis();
        short[] fireworkIds = {5, 6, 7, 8, 9};
        
        for (Village v : vills) {
            try {
                float tx = v.getTokenX() * 4.0f + 2.0f;
                float ty = v.getTokenY() * 4.0f + 2.0f;
                
                boolean surfaced = true;
                try {
                    surfaced = (boolean) ReflectionUtil.callPrivateMethod(v, ReflectionUtil.getMethod(v.getClass(), "isSurfaced", new Class[0]), new Object[0]);
                } catch (Throwable t) {
                    try {
                        surfaced = (boolean) ReflectionUtil.callPrivateMethod(v, ReflectionUtil.getMethod(v.getClass(), "isOnSurface", new Class[0]), new Object[0]);
                    } catch (Throwable t2) {}
                }

                float fz = 0.0f;
                try {
                    fz = Zones.calculateHeight(tx, ty, surfaced);
                } catch (Throwable t) {}

                // 1. Constant effect 4 on the deed token
                // Use a very high stable negative ID based on village ID
                long beamId = - (4000000000L + v.getId());
                // Set maxDistance to 20000 tiles (global) for xmas lights as requested
                tempEffects.put(beamId, new TempEffectData(tx, ty, fz, (byte)(surfaced ? 0 : -1), celebrationEndTime, (short)4, null, 20000.0f)); 
                for (Player p : Players.getInstance().getPlayers()) {
                    if (p != null && p.getCommunicator() != null) {
                        // Global send for xmas lights
                        long playerId = p.getWurmId();
                        Map<Long, VisualState> playerMap = perPlayerVisuals.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
                        VisualState lastState = playerMap.get(beamId);
                        
                        if (lastState == null || lastState.shouldUpdate(tx, ty, fz, (byte) 4, (byte)(surfaced ? 0 : -1))) {
                            p.getCommunicator().sendAddEffect(beamId, (byte) 4, tx, ty, fz, (byte)(surfaced ? 0 : -1));
                            playerMap.put(beamId, new VisualState(tx, ty, fz, (byte) 4, (byte)(surfaced ? 0 : -1)));
                        }
                    }
                }

                // 2. Random fireworks 5, 6, 7, 8, 9 spread up to 10m from the deed token
                int fwIndex = villageFireworkIndex.getOrDefault(v.getId(), 0);
                for (int i = 0; i < 3; i++) {
                    double angle = rand.nextDouble() * 2.0 * Math.PI;
                    // Random distance between 2 and 10 meters for better spread
                    float dist = 2.0f + rand.nextFloat() * 8.0f;
                    float fx = tx + (float)(Math.cos(angle) * dist);
                    float fy = ty + (float)(Math.sin(angle) * dist);
                    
                    float efz = 0.0f;
                    try {
                        efz = Zones.calculateHeight(fx, fy, surfaced);
                    } catch (Throwable t) {}
                    
                    // Pick a cycling firework type for each shot
                    short fwType = fireworkIds[(fwIndex + i) % fireworkIds.length];
                    // Stagger fireworks across the 5 second interval (0ms to 4500ms)
                    long fireTime = now + rand.nextInt(4500);
                    pendingFireworks.add(new PendingFirework(fireTime, v, fwType, fx, fy, efz, (byte)(surfaced ? 0 : -1)));
                }
                villageFireworkIndex.put(v.getId(), (fwIndex + 3) % fireworkIds.length);
            } catch (Throwable ignored) {}
        }
    }

    private static void processPendingFireworks(long now) {
        if (pendingFireworks.isEmpty()) return;
        
        synchronized (pendingFireworks) {
            Iterator<PendingFirework> it = pendingFireworks.iterator();
            while (it.hasNext()) {
                PendingFirework pf = it.next();
                if (now >= pf.time) {
                    long fireworkId = generateTempEffectId();
                    tempEffects.put(fireworkId, new TempEffectData(pf.x, pf.y, pf.z, pf.layer, now + 10000L, pf.type, null, 200.0f)); // 200 tile visibility
                    for (Player p : Players.getInstance().getPlayers()) {
                        if (p != null && p.getCommunicator() != null) {
                            float pdx = p.getPosX() - pf.x;
                            float pdy = p.getPosY() - pf.y;
                            // Only send to players within 200 tiles (800m)
                            if (pdx * pdx + pdy * pdy < 640000.0f) {
                                p.getCommunicator().sendAddEffect(fireworkId, pf.type, pf.x, pf.y, pf.z, pf.layer);
                                Map<Long, VisualState> playerMap = perPlayerVisuals.computeIfAbsent(p.getWurmId(), k -> new ConcurrentHashMap<>());
                                playerMap.put(fireworkId, new VisualState(pf.x, pf.y, pf.z, (byte)pf.type, pf.layer));
                            }
                        }
                    }
                    it.remove();
                }
            }
        }
    }

    public static void resetDailyLimit(Player admin) {
        forsakenCountToday = 0;
        lastResetTime = System.currentTimeMillis();
        ForsakenDatabase.saveDailyLimit(forsakenCountToday, lastResetTime);
        logger.info("Daily Forsaken limit reset" + (admin != null ? " by " + admin.getName() : "") + ".");
        if (admin != null) {
            admin.getCommunicator().sendSafeServerMessage("Daily Forsaken limits have been reset (Count: 0).");
        }
    }

    public static void poll() {
        if (!initialized) return;
        long now = System.currentTimeMillis();
        
        // Handle pending removals (slain/reverted Forsaken)
        if (!pendingRemoval.isEmpty()) {
            Iterator<Map.Entry<Long, Long>> it = pendingRemoval.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Long, Long> entry = it.next();
                if (now - entry.getValue() > 10000L) {
                    long id = entry.getKey();
                    forsakenKills.remove(id);
                    originalNames.remove(id);
                    lastTeleportTime.remove(id);
                    lastKnownPos.remove(id);
                    lastKnownName.remove(id);
                    ForsakenDatabase.deleteForsaken(id);
                    ForsakenDatabase.deleteVictimsFor(id);
                    victimsByKiller.remove(id);
                    if (ForsakenConfig.debug) logger.info("FORSAKEN_DEBUG: Final tracking removal for " + id);
                    it.remove();
                }
            }
        }

        // Handle pending corpse transfers
        if (!pendingTransfers.isEmpty()) {
            Iterator<Map.Entry<Long, PendingTransfer>> it = pendingTransfers.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Long, PendingTransfer> entry = it.next();
                long ownerId = entry.getKey();
                PendingTransfer transfer = entry.getValue();
                
                // Try to find the corpse
                com.wurmonline.server.items.Item corpse = findCorpseFor(ownerId, transfer.ownerName, transfer.tileX, transfer.tileY, transfer.surface);
                
                if (corpse != null) {
                    Iterator<com.wurmonline.server.items.Item> itemIt = transfer.items.iterator();
                    while (itemIt.hasNext()) {
                        com.wurmonline.server.items.Item item = itemIt.next();
                        if (corpse.insertItem(item, true)) {
                            if (ForsakenConfig.debug) logger.info("FORSAKEN_DEBUG: Delayed transfer successful: " + item.getName() + " moved to " + corpse.getName());
                            itemIt.remove();
                        }
                    }
                }
                
                if (transfer.items.isEmpty() || now - transfer.startTime > 30000L) {
                    if (!transfer.items.isEmpty()) {
                        if (ForsakenConfig.debug) logger.warning("FORSAKEN_DEBUG: Delayed transfer failed after 30s for " + transfer.items.size() + " items. Dropping on ground.");
                        for (com.wurmonline.server.items.Item item : transfer.items) {
                            try {
                                Creature owner = Creatures.getInstance().getCreatureOrNull(ownerId);
                                if (owner != null) {
                                    item.putItemInfrontof(owner);
                                } else {
                                    // Drop at last known position
                                    item.setPosX(transfer.x);
                                    item.setPosY(transfer.y);
                                    item.setPosZ(transfer.z);
                                    com.wurmonline.server.zones.Zone zone = com.wurmonline.server.zones.Zones.getZone(transfer.tileX, transfer.tileY, transfer.surface);
                                    if (zone != null) {
                                        zone.addItem(item);
                                    }
                                    if (ForsakenConfig.debug) logger.info("FORSAKEN_DEBUG: Dropped " + item.getName() + " on ground at " + transfer.tileX + ", " + transfer.tileY);
                                }
                            } catch (Throwable t) {
                                if (ForsakenConfig.debug) logger.warning("FORSAKEN_DEBUG: Failed to drop item on ground: " + t.getMessage());
                            }
                        }
                    }
                    it.remove();
                }
            }
        }

        // Daily limit reset check (24 hours)
        if (lastResetTime > 0 && now - lastResetTime > 86400000L) {
            resetDailyLimit(null);
        }

        if (now - lastAnnouncement > ForsakenConfig.announcementFrequency * 60000L) {
            announceForsaken();
            lastAnnouncement = now;
        }
        
        // Periodic sync of temporary effects (distance culling)
        if (now - lastTempSync > 2000L) {
            syncTempEffects(now);
            lastTempSync = now;
        }
        
        // Handle celebration fireworks
        if (now < celebrationEndTime) {
            if (now - lastCelebrationFire > 5000L) {
                fireCelebrationFireworks();
                lastCelebrationFire = now;
            }
        }
        
        // Hunt every poll (teleport time 0)
        huntPlayers();
        
        // Process staggered fireworks
        processPendingFireworks(now);
        
        // Cleanup temporary trail effects
        cleanTempEffects(now);
    }

    private static void announceForsaken() {
        if (forsakenKills.isEmpty()) return;
        
        for (Long id : forsakenKills.keySet()) {
            try {
                Creature c = Server.getInstance().getCreature(id);
                if (c != null && !c.isDead()) {
                    String bountyStr = getBountyString(c);
                    String message = "The Forsaken " + c.getName() + " is on a rampage at " + c.getTileX() + ", " + c.getTileY() + "!" + (bountyStr.isEmpty() ? "" : " Bounty: " + bountyStr);
                    broadcastGlobal(message, 200, 0, 0);
                }
            } catch (Exception ignored) {}
        }
    }

    private static void huntPlayers() {
        if (forsakenKills.isEmpty()) return;
        
        long now = System.currentTimeMillis();
        // Clean up processedDeaths map occasionally
        if (rand.nextInt(100) == 0) {
            processedDeaths.entrySet().removeIf(entry -> now - entry.getValue() > 60000L);
        }

        for (Long id : forsakenKills.keySet()) {
            try {
                Creature c = Server.getInstance().getCreature(id);
                if (c == null || c.isDead()) continue;

                // applyForsakenTraits(c); // Removed from poll: causes robotic movement due to refreshVisible()
                ensureVisualEffect(c);
                
                Player nearest = getNearestPlayer(c);
                if (nearest != null) {
                    float dx = nearest.getPosX() - c.getPosX();
                    float dy = nearest.getPosY() - c.getPosY();
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    
                    // Message logic: Notify the player they are being hunted
                    long pid = nearest.getWurmId();
                    Long lastMsg = lastHuntedMessage.get(pid);
                    if (lastMsg == null || now - lastMsg > 60000L) {
                        nearest.getCommunicator().sendAlertServerMessage("You feel a chill down your spine... " + c.getName() + " is actively hunting you!");
                        lastHuntedMessage.put(pid, now);
                    }

                    // Persistent engagement: Re-apply target
                    if (c.getTarget() != nearest) {
                        c.setTarget(nearest.getWurmId(), true);
                        try {
                            c.setOpponent(nearest);
                        } catch (Throwable ignored) {}
                    }
                    
                    // If fighting but not moving, force a poll on the action stack to kickstart movement/AI
                    // For distances within 150 tiles, we force a natural path to the player
                    if (dist > 5.0f) {
                        // Ensure it's not set to stand still
                        if (c.shouldStandStill) {
                            c.shouldStandStill = false;
                        }

                        // Kickstart AI if standing still
                        if (!c.isMoving() || rand.nextInt(5) == 0) {
                            try {
                                c.getActions().poll(c);
                            } catch (Throwable ignored) {}
                        }
                        
                        // Force pathing if not moving or pathing, or occasionally even if pathing (to update target location)
                        if (dist <= 800.0f && (!c.isMoving() || !c.isPathing() || rand.nextInt(10) == 0)) {
                            try {
                                int tx = nearest.getTileX();
                                int ty = nearest.getTileY();
                                boolean surfaced = nearest.isOnSurface();
                                int floorLevel = nearest.getFloorLevel();
                                int tile = surfaced ? Server.surfaceMesh.getTile(tx, ty) : Server.caveMesh.getTile(tx, ty);
                                
                                PathTile pTile = new PathTile(tx, ty, tile, surfaced, floorLevel);
                                c.startPathingToTile(pTile);
                            } catch (Throwable ignored) {}
                        }
                    }

                    // Teleport logic: Jump closer
                    if (ForsakenConfig.enableTeleport) {
                        float thresholdDist = ForsakenConfig.teleportThresholdTiles * 4.0f;
                        // User requested to not teleport as close as 15 tiles to avoid bypass of defenses.
                        // Increased to 35 tiles, which is far enough to be outside most bases but close enough for AI to walk.
                        float landDist = 35.0f * 4.0f;
                        float minJumpDist = ForsakenConfig.teleportBufferTiles * 4.0f;

                        // Only teleport if we are further than (threshold + buffer) tiles from ALL players.
                        float currentNearestDist = getDistToNearestPlayer(c.getPosX(), c.getPosY(), c.getLayer());

                        if (currentNearestDist > thresholdDist + minJumpDist) {
                            // Check cooldown
                            long lastTele = lastTeleportTime.getOrDefault(id, 0L);
                            if (now - lastTele < ForsakenConfig.teleportDelay * 1000L) {
                                continue;
                            }

                            // We are far from everyone. Teleport towards our target.
                            float targetDistMeters = landDist;
                            if (targetDistMeters < 10.0f * 4.0f) targetDistMeters = 10.0f * 4.0f;

                            float actualJumpDist = dist - targetDistMeters;

                            if (actualJumpDist >= minJumpDist) {
                                // Pick an angle (towards creature's current position)
                                double baseAngle = Math.atan2(c.getPosY() - nearest.getPosY(), c.getPosX() - nearest.getPosX());

                                int targetX = -1;
                                int targetY = -1;

                                // Try a few angles to avoid rock/deeds/players
                                for (int i = 0; i < 8; i++) {
                                    double attemptAngle = baseAngle + (i * Math.PI / 4.0);
                                    float testX = nearest.getPosX() + (float) (Math.cos(attemptAngle) * targetDistMeters);
                                    float testY = nearest.getPosY() + (float) (Math.sin(attemptAngle) * targetDistMeters);

                                    int tx = (int) testX / 4;
                                    int ty = (int) testY / 4;

                                    // Bounds check
                                    if (tx < 0 || ty < 0 || tx >= Zones.worldTileSizeX || ty >= Zones.worldTileSizeY)
                                        continue;

                                    // Safe Distance Check: Never land within 10 tiles of ANY player
                                    if (getDistToNearestPlayer(testX, testY, nearest.getLayer()) < 10.0f * 4.0f) { // 1 tile slack
                                        continue;
                                    }

                                    // Deed check: "the creature should never teleport onto a deeded tile"
                                    try {
                                        Village v = Villages.getVillage(tx, ty, nearest.isOnSurface());
                                        if (v != null) {
                                            continue; // Found a deed, skip this angle
                                        }
                                    } catch (Throwable ignored) {
                                    }

                                    // Rock check in cave
                                    if (nearest.getLayer() < 0) {
                                        try {
                                            byte tile = Tiles.decodeType(Server.caveMesh.getTile(tx, ty));
                                            if (Tiles.isSolidCave(tile)) continue;
                                        } catch (Throwable ignored) {
                                        }
                                    }

                                    targetX = tx;
                                    targetY = ty;
                                    break;
                                }

                                if (targetX != -1) {
                                    // Calculate Z and move using intraTeleport
                                    try {
                                        float targetPosX = targetX * 4.0f + 2.0f;
                                        float targetPosY = targetY * 4.0f + 2.0f;
                                        float targetPosZ = 0.0f;
                                        try {
                                            targetPosZ = Zones.calculateHeight(targetPosX, targetPosY, nearest.isOnSurface());
                                        } catch (Throwable t) {
                                            targetPosZ = nearest.getPositionZ();
                                        }

                                        c.intraTeleport(targetPosX, targetPosY, targetPosZ, c.getStatus().getRotation(), nearest.getLayer(), "hunting");
                                        lastTeleportTime.put(id, now);

                                        // Immediate visual update after teleport to clear stale beam at old location
                                        removeForsakenVisuals(id);
                                        broadcastForsakenVisuals(c);
                                        lastVisualEffectSent.put(id, System.currentTimeMillis());

                                        if (ForsakenConfig.debug) logger.info(c.getName() + " jumped " + (int) (actualJumpDist / 4.0f) + " tiles closer to " + nearest.getName() + " (to " + targetX + ", " + targetY + " - New dist: " + (int) (targetDistMeters / 4.0f) + " tiles)");
                                    } catch (Throwable t) {
                                        // Fallback to basic teleport if intraTeleport fails
                                        c.setTeleportPoints((short) targetX, (short) targetY, nearest.getLayer(), 0);
                                        c.teleport(true);
                                        lastTeleportTime.put(id, now);

                                        // Immediate visual update after teleport
                                        removeForsakenVisuals(id);
                                        broadcastForsakenVisuals(c);
                                        lastVisualEffectSent.put(id, System.currentTimeMillis());
                                        if (ForsakenConfig.debug) logger.info(c.getName() + " jumped closer to " + nearest.getName() + " (teleport fallback to " + targetX + ", " + targetY + ")");
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private static float getDistToNearestPlayer(float x, float y, int layer) {
        float minDistSq = Float.MAX_VALUE;
        for (Player p : Players.getInstance().getPlayers()) {
            if (p.isDead()) continue;
            if (p.getPower() > 1) continue; // Skip GMs
            if (p.getLayer() != layer) continue;
            
            if (ForsakenConfig.enableOptIn) {
                int status = playerOptInStatus.getOrDefault(p.getWurmId(), 0);
                if (status != 1) continue; // Only target players who explicitly opted in (status 1)
            }
            if (ForsakenConfig.minFSReqEnabled) {
                Skill fighting = p.getSkills().getSkillOrLearn(1023);
                if (fighting.getKnowledge() < ForsakenConfig.minimumFSREQ) continue;
            }

            float dx = p.getPosX() - x;
            float dy = p.getPosY() - y;
            float distSq = dx * dx + dy * dy;
            if (distSq < minDistSq) minDistSq = distSq;
        }
        if (minDistSq == Float.MAX_VALUE) return Float.MAX_VALUE;
        return (float) Math.sqrt(minDistSq);
    }

    private static Player getNearestPlayer(Creature c) {
        Player nearest = null;
        float minDist = Float.MAX_VALUE;
        
        for (Player p : Players.getInstance().getPlayers()) {
            if (p.isDead()) continue;
            if (p.getPower() > 1) continue; // Forsaken should not target anyone with power > 1
            
            if (ForsakenConfig.enableOptIn) {
                int status = playerOptInStatus.getOrDefault(p.getWurmId(), 0);
                if (status != 1) continue; // Only target players who explicitly opted in (status 1)
            }
            if (ForsakenConfig.minFSReqEnabled) {
                Skill fighting = p.getSkills().getSkillOrLearn(1023);
                if (fighting.getKnowledge() < ForsakenConfig.minimumFSREQ) continue;
            }
            
            float dx = p.getPosX() - c.getPosX();
            float dy = p.getPosY() - c.getPosY();
            float dz = p.getLayer() != c.getLayer() ? 1000 : 0; // Penalize different layers but allow hunting across them
            float dist = dx * dx + dy * dy + dz * dz;
            
            if (dist < minDist) {
                minDist = dist;
                nearest = p;
            }
        }
        return nearest;
    }

    private static void broadcastGlobal(String message, int r, int g, int b) {
        try {
            if (ForsakenConfig.enableAnnouncements) {
                MessageServer.broadCastSafe(message, (byte) 1);
            }
            
            Message mess;
            for (Player rec : Players.getInstance().getPlayers()) {
                if (rec != null && rec.getCommunicator() != null) {
                    if (!ForsakenConfig.enableAnnouncements && rec.getPower() < ForsakenConfig.adminPowerLevel) {
                        continue;
                    }
                    mess = new Message(null, (byte) 16, "Server", message, r, g, b);
                    rec.getCommunicator().sendMessage(mess);
                }
            }
        } catch (Exception e) {
            logger.warning("Error in broadcastGlobal: " + e.getMessage());
        }
    }

    private static void recordVictim(Creature killer, String victimName) {
        try {
            if (killer == null || victimName == null) return;
            long id = killer.getWurmId();
            List<String> list = victimsByKiller.computeIfAbsent(id, k -> new ArrayList<>());
            list.add(victimName);
            ForsakenDatabase.addVictim(id, victimName);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to record victim '" + victimName + "' for " + (killer != null ? killer.getName() : "null") + ": " + e.getMessage(), e);
        }
    }

    public static boolean handleForsakenCommand(ByteBuffer byteBuffer, Player player) {
        if (!initialized) return false;
        try {
            // Parse message string from the communicator buffer (same pattern as WyvernMods)
            ByteBuffer dup = byteBuffer.duplicate();
            byte[] arr = new byte[dup.get() & 255];
            dup.get(arr);
            String message = new String(arr, "UTF-8");
            // Skip second string (window title)
            byte[] skip = new byte[dup.get() & 255];
            dup.get(skip);

            if (message == null) return false;
            String lower = message.trim().toLowerCase();
            if (lower.equals("/forsaken on") || lower.equals("/forsaken off")) {
                if (!ForsakenConfig.enableOptIn) {
                    player.getCommunicator().sendSafeServerMessage("The Forsaken opt-in system is currently disabled.");
                    return true;
                }
                boolean optIn = lower.equals("/forsaken on");
                int status = optIn ? 1 : 2;
                playerOptInStatus.put(player.getWurmId(), status);
                ForsakenDatabase.savePlayerOptIn(player.getWurmId(), status);
                player.getCommunicator().sendSafeServerMessage("Forsaken creature hunts: " + (optIn ? "ON (You can now be a slayer of the Forsaken!)" : "OFF (You will hide in the shadow of self doubt)"));
                return true;
            }
            
            if (lower.startsWith("/forsaken")) {
                if (player == null || player.getPower() < ForsakenConfig.adminPowerLevel) {
                    if (player != null) player.getCommunicator().sendSafeServerMessage("You do not have permission to use this command.");
                    return true; // consume command
                }
                if (lower.equals("/forsaken list")) {
                    listForsaken(player);
                    return true; // handled
                } else if (lower.equals("/forsaken clear")) {
                    clearAllForsaken(player);
                    return true; // handled
                } else if (lower.equals("/forsaken resetlimits")) {
                    resetDailyLimit(player);
                    return true; // handled
                } else if (lower.equals("/forsaken status")) {
                    player.getCommunicator().sendSafeServerMessage("-- Forsaken Mod Status [" + ForsakenConfig.MOD_VERSION + "] --");
                    player.getCommunicator().sendSafeServerMessage("Config Loaded: " + ForsakenConfig.isLoaded());
                    player.getCommunicator().sendSafeServerMessage("Config Path: " + ForsakenConfig.getConfigPath());
                    player.getCommunicator().sendSafeServerMessage("Reward Levels Configured: " + ForsakenConfig.levelRewards.size());
                    for (Integer level : new TreeSet<>(ForsakenConfig.levelRewards.keySet())) {
                        player.getCommunicator().sendSafeServerMessage("  Level " + level + ": " + ForsakenConfig.levelRewards.get(level).size() + " item types");
                    }
                    player.getCommunicator().sendSafeServerMessage("Daily Forsaken: " + forsakenCountToday + " / " + ForsakenConfig.maxForsakenPerDay);
                    player.getCommunicator().sendSafeServerMessage("Active Forsaken: " + forsakenKills.size());
                    player.getCommunicator().sendSafeServerMessage("Opt-In System: " + (ForsakenConfig.enableOptIn ? "Enabled" : "Disabled"));
                    player.getCommunicator().sendSafeServerMessage("Skill Retention: " + (ForsakenConfig.enableSkillRetention ? "Enabled" : "Disabled"));
                    return true; // handled
                } else if (lower.equals("/forsaken reload")) {
                    ForsakenConfig.load();
                    player.getCommunicator().sendSafeServerMessage("Forsaken configuration reloaded from file.");
                    return true; // handled
                } else if (lower.equals("/forsaken announcements")) {
                    ForsakenConfig.enableAnnouncements = !ForsakenConfig.enableAnnouncements;
                    ForsakenConfig.save();
                    player.getCommunicator().sendSafeServerMessage("Forsaken announcements for non-admins: " + (ForsakenConfig.enableAnnouncements ? "ON" : "OFF"));
                    return true; // handled
                } else if (lower.equals("/forsaken verbose")) {
                    ForsakenConfig.debug = !ForsakenConfig.debug;
                    ForsakenConfig.save();
                    player.getCommunicator().sendSafeServerMessage("Forsaken verbose debug mode: " + (ForsakenConfig.debug ? "ON" : "OFF"));
                    return true; // handled
                } else if (lower.startsWith("/forsaken level")) {
                    Creature target = player.getTarget();
                    if (target == null) {
                        player.getCommunicator().sendSafeServerMessage("You must target a creature first.");
                        return true;
                    }
                    if (target.isPlayer()) {
                        player.getCommunicator().sendSafeServerMessage("Players cannot be Forsaken.");
                        return true;
                    }
                    
                    String[] parts = message.trim().split("\\s+");
                    if (parts.length >= 3) {
                        try {
                            int targetLevel = Integer.parseInt(parts[2]);
                            setForsakenLevel(target, targetLevel);
                            
                            if (isForsaken(target)) {
                                int finalLevel = forsakenKills.getOrDefault(target.getWurmId(), 0);
                                player.getCommunicator().sendSafeServerMessage(target.getName() + " level set to " + finalLevel + ".");
                            } else {
                                player.getCommunicator().sendSafeServerMessage(target.getName() + " is no longer Forsaken.");
                            }
                        } catch (NumberFormatException e) {
                            player.getCommunicator().sendSafeServerMessage("Invalid level number: " + parts[2] + ". Usage: /forsaken level <number>");
                        }
                    } else {
                        player.getCommunicator().sendSafeServerMessage("Usage: /forsaken level <number>");
                    }
                    return true; // handled
                } else if (lower.equals("/forsaken celebrate")) {
                    startCelebration();
                    player.getCommunicator().sendSafeServerMessage("Celebration event triggered manually.");
                    broadcastGlobal("A grand celebration has been called to honor the hunters of " + ForsakenConfig.serverName + "!", 0, 255, 255);
                    return true; // handled
                } else if (lower.startsWith("/forsaken spawn")) {
                    if (lower.length() > 16) {
                        spawnForsaken(player, message.substring(16).trim());
                    } else {
                        player.getCommunicator().sendSafeServerMessage("Usage: /forsaken spawn <TemplateName>");
                    }
                    return true;
                } else if (lower.equals("/forsaken") || lower.equals("/forsaken .") || lower.equals("/forsaken help")) {
                    sendHelp(player);
                    return true;
                } else {
                    player.getCommunicator().sendSafeServerMessage("Unknown forsaken command. Type /forsaken . for help.");
                    return true; // consume
                }
            }
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Error in handleForsakenCommand: " + t.getMessage(), t);
        }
        return false;
    }


    public static void spawnForsaken(Player admin, String templateName) {
        try {
            com.wurmonline.server.creatures.CreatureTemplate temp = null;
            try {
                int id = Integer.parseInt(templateName);
                temp = com.wurmonline.server.creatures.CreatureTemplateFactory.getInstance().getTemplate(id);
            } catch (NumberFormatException e) {
                temp = com.wurmonline.server.creatures.CreatureTemplateFactory.getInstance().getTemplate(templateName);
            }
            
            if (temp == null) {
                admin.getCommunicator().sendSafeServerMessage("Template '" + templateName + "' not found.");
                return;
            }
            
            String uniqueName = generateRandomName();
            
            Creature c = Creature.doNew(temp.getTemplateId(), admin.getPosX(), admin.getPosY(), admin.getStatus().getRotation(), admin.getLayer(), uniqueName, (byte) 0);
            makeForsaken(c, uniqueName);
            admin.getCommunicator().sendSafeServerMessage("Successfully spawned a Forsaken " + temp.getName() + " (ID: " + c.getWurmId() + ") as " + uniqueName + " at your position.");
        } catch (Throwable t) {
            admin.getCommunicator().sendSafeServerMessage("Error spawning Forsaken: " + t.getMessage());
            logger.log(Level.SEVERE, "Error spawning Forsaken: " + t.getMessage(), t);
        }
    }

    public static void listForsaken(Player admin) {
        try {
            if (admin == null) return;
            if (forsakenKills.isEmpty()) {
                admin.getCommunicator().sendSafeServerMessage("No Forsaken creatures are currently active.");
                return;
            }
            admin.getCommunicator().sendSafeServerMessage("-- Active Forsaken Creatures --");
            for (Map.Entry<Long, Integer> e : forsakenKills.entrySet()) {
                long id = e.getKey();
                int level = e.getValue();
                Creature c = null;
                try {
                    c = Server.getInstance().getCreature(id);
                } catch (Exception ignored) {}
                if (c == null || c.isDead()) continue;
                String pos = c.getTileX() + ", " + c.getTileY() + (c.getLayer() < 0 ? " (cave)" : "");
                List<String> victims = victimsByKiller.getOrDefault(id, Collections.emptyList());
                String victimsStr = victims.isEmpty() ? "None" : String.join(", ", victims);
                admin.getCommunicator().sendSafeServerMessage(
                        c.getName() + " [Level " + level + "] at (" + pos + ") - Killed: " + victimsStr
                );
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Error listing Forsaken creatures: " + t.getMessage(), t);
        }
    }

    public static void clearAllForsaken(Player admin) {
        try {
            if (admin == null) return;

            // Clean up temporary effects immediately
            if (!tempEffects.isEmpty()) {
                List<Long> ids = new ArrayList<>(tempEffects.keySet());
                for (Long id : ids) {
                    removeEffectFromAll(id);
                }
            }

            if (forsakenKills.isEmpty()) {
                admin.getCommunicator().sendSafeServerMessage("No Forsaken creatures are currently active.");
                return;
            }
            
            List<Long> ids = new ArrayList<>(forsakenKills.keySet());
            int count = 0;
            for (Long id : ids) {
                try {
                    Creature c = null;
                    try {
                        c = Server.getInstance().getCreature(id);
                    } catch (Exception ignored) {}
                    
                    if (c != null) {
                        revertForsaken(c);
                        count++;
                    } else {
                        // Creature not found or offline, clean up tracking anyway
                        forsakenKills.remove(id);
                        originalNames.remove(id);
                        lastTeleportTime.remove(id);
                        ForsakenDatabase.deleteForsaken(id);
                        ForsakenDatabase.deleteVictimsFor(id);
                        victimsByKiller.remove(id);
                        removeForsakenVisuals(id);
                        count++;
                    }
                } catch (Exception e) {
                    logger.warning("Error clearing Forsaken ID " + id + ": " + e.getMessage());
                }
            }
            // Cleanup any attached test effects on the admin
            admin.getCommunicator().sendRemoveEffect(admin.getWurmId());
            admin.getCommunicator().sendSafeServerMessage("Successfully cleared " + count + " Forsaken creatures.");
            if (count > 0) {
                startCelebration();
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Error in clearAllForsaken: " + t.getMessage(), t);
        }
    }


    public static void sendHelp(Player player) {
        player.getCommunicator().sendSafeServerMessage("-- Forsaken Mod Commands --");
        player.getCommunicator().sendSafeServerMessage("/forsaken on - Opt-in to Forsaken hunts.");
        player.getCommunicator().sendSafeServerMessage("/forsaken off - Opt-out of Forsaken hunts.");
        if (player.getPower() >= ForsakenConfig.adminPowerLevel) {
            player.getCommunicator().sendSafeServerMessage("-- Forsaken Mod Admin Commands --");
            player.getCommunicator().sendSafeServerMessage("/forsaken spawn <Template> - Spawn a new Forsaken.");
            player.getCommunicator().sendSafeServerMessage("/forsaken level <Level> - Sets the targeted creature's Forsaken level.");
            player.getCommunicator().sendSafeServerMessage("/forsaken announcements - Toggles world/player announcements for non-admins.");
            player.getCommunicator().sendSafeServerMessage("/forsaken verbose - Toggles verbose debug logging in console.");
            player.getCommunicator().sendSafeServerMessage("/forsaken list - Lists all active Forsaken.");
            player.getCommunicator().sendSafeServerMessage("/forsaken clear - Reverts all Forsaken.");
            player.getCommunicator().sendSafeServerMessage("/forsaken resetlimits - Manually resets daily spawn counters.");
            player.getCommunicator().sendSafeServerMessage("/forsaken celebrate - Triggers a manual celebration event.");
            player.getCommunicator().sendSafeServerMessage("/forsaken status - Show current mod configuration and status.");
            player.getCommunicator().sendSafeServerMessage("/forsaken reload - Reloads configuration from file.");
        }
        player.getCommunicator().sendSafeServerMessage("/forsaken help (or .) - Shows this help message.");
    }

    public static void recordSkillsBeforeDeath(Player player) {
        if (player == null) return;
        Map<Integer, Double> skills = new HashMap<>();
        for (Skill s : player.getSkills().getSkills()) {
            skills.put(s.getNumber(), s.getKnowledge());
        }
        playerSkillsBeforeDeath.put(player.getWurmId(), skills);
        if (ForsakenConfig.debug) logger.info("Recorded " + skills.size() + " skills before death for " + player.getName());
    }

    public static void handleSkillRetention(Creature creature) {
        if (creature == null || !creature.isPlayer() || !ForsakenConfig.enableSkillRetention) return;
        Player player = (Player)creature;
        long pid = player.getWurmId();
        Map<Integer, Double> before = playerSkillsBeforeDeath.remove(pid);
        if (before == null) return;
        
        // Find Fighting Skill to determine retention percentage
        Skill fighting = player.getSkills().getSkillOrLearn(1023);
        double currentFS = fighting.getKnowledge();
        float retentionPercent = 0.0f;
        
        for (int i = 0; i < 3; i++) {
            if (currentFS >= ForsakenConfig.skillRetentionRanges[i][0] && currentFS <= ForsakenConfig.skillRetentionRanges[i][1]) {
                retentionPercent = ForsakenConfig.skillRetentionRanges[i][2];
                break;
            }
        }
        
        if (retentionPercent <= 0) {
            if (ForsakenConfig.debug) logger.info("No skill retention for " + player.getName() + " (FS: " + currentFS + ")");
            return;
        }
        
        int restoredCount = 0;
        for (Map.Entry<Integer, Double> entry : before.entrySet()) {
            int skillNum = entry.getKey();
            double knowledgeBefore = entry.getValue();
            try {
                Skill s = player.getSkills().getSkill(skillNum);
                double knowledgeAfter = s.getKnowledge();
                if (knowledgeAfter < knowledgeBefore) {
                    double loss = knowledgeBefore - knowledgeAfter;
                    double restored = loss * (retentionPercent / 100.0);
                    if (restored > 0) {
                        s.setKnowledge(knowledgeAfter + restored, false);
                        restoredCount++;
                    }
                }
            } catch (com.wurmonline.server.skills.NoSuchSkillException ignored) {}
        }
        
        if (restoredCount > 0) {
            player.getCommunicator().sendSafeServerMessage("Due to your Fighting Skill experience, you managed to retain " + retentionPercent + "% of your lost skills!");
            if (ForsakenConfig.debug) logger.info("Restored " + restoredCount + " skills for " + player.getName() + " (" + retentionPercent + "%)");
        }
    }
}
