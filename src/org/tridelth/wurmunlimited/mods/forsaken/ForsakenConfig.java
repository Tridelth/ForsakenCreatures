package org.tridelth.wurmunlimited.mods.forsaken;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

public class ForsakenConfig {
    private static final Logger logger = Logger.getLogger(ForsakenConfig.class.getName());
    public static final String MOD_VERSION = "1.3.6";
    
    static {
        logger.info("ForsakenConfig [" + MOD_VERSION + "] class loaded.");
    }
    
    public static double defaultSkillIncrease = 5.0;
    public static double baseCombatRatingIncrease = 5.0;
    public static float naturalArmourIncrease = 0.05f;
    public static float speedIncrease = 0.05f;
    public static int announcementFrequency = 10;
    public static float forsakenChance = 1.0f;
    public static int teleportThresholdTiles = 70;
    public static int teleportDelay = 5;
    public static int teleportBufferTiles = 10;
    public static int maxHuntDistance = 1000;
    public static int celebrationDuration = 5;
    public static boolean enableCelebration = true;
    public static boolean enableAnnouncements = true;
    public static boolean enableTeleport = true;
    public static boolean enableTrailEffects = true;
    public static boolean enableInventoryRewards = true;
    public static boolean enableSecondNames = true;
    public static boolean enableTitles = true;
    public static boolean debug = false;
    public static boolean rewardEnabled = true;
    public static boolean enableOptIn = false;
    public static boolean minFSReqEnabled = false;
    public static float minimumFSREQ = 21.0f;
    public static boolean enableSkillRetention = false;
    public static boolean retainOnForsakenDeath = true;
    public static boolean retainOnAnyCreatureDeath = false;
    public static String serverName = "Heavenord";
    public static float[][] skillRetentionRanges = new float[3][3];
    static {
        skillRetentionRanges[0] = new float[]{1.0f, 50.0f, 50.0f};
        skillRetentionRanges[1] = new float[]{50.0f, 70.0f, 25.0f};
        skillRetentionRanges[2] = new float[]{70.0f, 100.0f, 10.0f};
    }
    public static int maxLevel = 10;
    public static int maxForsakenPerDay = 100;
    public static int adminPowerLevel = 2;
    public static int creatureEffect = 1;
    public static String creatureParticleName = "none";
    public static int trailEffect1 = 1; // Default to Lightning
    public static int trailEffect2 = 0; // Default to Camp Fire
    public static final Map<Integer, Double> individualSkillIncreases = new java.util.concurrent.ConcurrentHashMap<>();
    
    private static final Map<String, Integer> COMMON_SKILL_NAMES = new LinkedHashMap<>();
    static {
        COMMON_SKILL_NAMES.put("Body Strength", 102);
        COMMON_SKILL_NAMES.put("Body Stamina", 103);
        COMMON_SKILL_NAMES.put("Body Control", 104);
        COMMON_SKILL_NAMES.put("Mind Speed", 100);
        COMMON_SKILL_NAMES.put("Mind Logic", 101);
        COMMON_SKILL_NAMES.put("Soul Strength", 105);
        COMMON_SKILL_NAMES.put("Soul Depth", 106);
        COMMON_SKILL_NAMES.put("Fighting", 1023);
        COMMON_SKILL_NAMES.put("Aggressive Fighting", 10052);
        COMMON_SKILL_NAMES.put("Defensive Fighting", 10051);
        COMMON_SKILL_NAMES.put("Natural Combat", 10088);
    }
    
    public static long[] bounties = new long[11];
    static {
        for (int i = 1; i <= 10; i++) bounties[i] = i * 20000L;
    }
    public static String[] firstNames = {"Bone", "Skull", "Blood", "Soul", "Heart", "Mind", "Flesh", "Grit", "Death", "Shadow", "Night", "Dark", "Void", "Iron", "Steel", "Stone", "Cold", "Ice", "Fire", "Storm", "Gloom", "Doom", "Vile", "Rotten", "Stinky", "Moldy", "Grumpy", "Spooky", "Creepy", "Ancient", "Cursed", "Wicked", "Foul", "Dread", "Grim", "Morbid", "Ghastly", "Sinister", "Malice", "Spite", "Wrath", "Greed", "Lust", "Glutton", "Lazy", "Pride", "Envy", "Sloth", "Bubbling", "Gurgle"};
    public static String[] secondNames = {"Crusher", "Grinder", "Eater", "Ripper", "Tearer", "Slayer", "Hunter", "Seeker", "Stalker", "Bane", "Breaker", "Render", "Mangier", "Ravager", "Devourer", "Butcher", "Mauler", "Scourge", "Wraith", "Herald", "Belcher", "Flatulent", "Tumbler", "Fumbler", "Grumbler", "Mumbler", "Rumbler", "Bungler", "Dangler", "Strangler", "Mangler", "Tangler", "Wrangler", "Spangler", "Rattler", "Prattler", "Tattler", "Battler", "Scuttler", "Shuffler", "Sniffler", "Snuffler", "Truffler", "Muffler", "Puffer", "Snuffer", "Buffer", "Guffer", "Bluffer", "Fluffer"};
    public static String[] titles = {"The Terrible", "The Brave", "The Undying", "The Eternal", "The Destroyer", "The Conqueror", "The Merciless", "The Mighty", "The Relentless", "The Savage", "The Cruel", "The Wicked", "The Ancient", "The Forsaken", "The Harbinger", "The Corruptor", "The Devourer", "The Unstoppable", "The Dreaded", "The Shadow", "The Smelly", "The Clumsy", "The Hungry", "The Sleepy", "The Loud", "The Ticklish", "The Confused", "The Over-Dramatic", "The Shiny", "The Fluffy", "The Squishy", "The Slightly Annoyed", "The Professional", "The Intern", "The Magnificent", "The Average", "The Unexpected", "The Lost", "The Brave-ish", "The Gentle Soul"};

    public static class RewardItem {
        public final int id;
        public final byte rarity;
        public final byte material;
        public final int quantity;
        public final float quality;

        public RewardItem(int id, byte rarity) {
            this(id, rarity, (byte)0, 1, -1.0f);
        }

        public RewardItem(int id, byte rarity, byte material) {
            this(id, rarity, material, 1, -1.0f);
        }

        public RewardItem(int id, byte rarity, byte material, int quantity) {
            this(id, rarity, material, quantity, -1.0f);
        }

        public RewardItem(int id, byte rarity, byte material, int quantity, float quality) {
            this.id = id;
            this.rarity = rarity;
            this.material = material;
            this.quantity = quantity;
            this.quality = quality;
        }
    }
    public static final Map<Integer, List<RewardItem>> levelRewards = new java.util.concurrent.ConcurrentHashMap<>();

    private static synchronized byte parseRarity(String s) {
        if (s == null) return 0;
        s = s.trim().toLowerCase();
        switch (s) {
            case "none": return 0;
            case "rare": return 1;
            case "supreme": return 2;
            case "fantastic": return 3;
            default:
                try {
                    return Byte.parseByte(s);
                } catch (NumberFormatException e) {
                    return 0;
                }
        }
    }

    private static synchronized byte parseMaterial(String s) {
        if (s == null) return 0;
        s = s.trim().toLowerCase();
        if (s.equals("none")) return 0;
        try {
            return Byte.parseByte(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean loaded = false;

    public static boolean isLoaded() {
        return loaded;
    }
    
    private static int parseEffect(String val, int defaultVal) {
        if (val == null) return defaultVal;
        if (val.trim().equalsIgnoreCase("none")) return -1;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    public static synchronized void load(Properties props) {
        if (props == null) return;
        
        // Parse debug flag first so we can use it for logging the rest of the update
        try {
            debug = Boolean.parseBoolean(props.getProperty("debug", String.valueOf(debug)));
        } catch (Exception ignored) {}

        if (debug) logger.info("[" + MOD_VERSION + "] Updating ForsakenConfig from properties.");
        
        // Count items to see if this is a substantial update or just a Mod Loader properties merge
        int itemKeysCount = 0;
        try {
            int tempMax = Integer.parseInt(props.getProperty("max_level", String.valueOf(maxLevel)));
            for (int i = 1; i <= Math.max(tempMax, 20); i++) {
                if (props.getProperty("bounty_level_" + i + "_items") != null) {
                    itemKeysCount++;
                }
            }
        } catch (Exception ignored) {}
        
        if (debug) logger.info("[" + MOD_VERSION + "]   Reward keys found: " + itemKeysCount);
        
        try {
            defaultSkillIncrease = Double.parseDouble(props.getProperty("Default_Skill_Increase", props.getProperty("skill_increase", String.valueOf(defaultSkillIncrease))));
        } catch (Exception e) { logger.warning("[" + MOD_VERSION + "] Error parsing Default_Skill_Increase: " + e.getMessage()); }
        
        try {
            baseCombatRatingIncrease = Double.parseDouble(props.getProperty("Base Combat Rating", props.getProperty("Base_Combat_Rating", String.valueOf(baseCombatRatingIncrease))));
        } catch (Exception e) { logger.warning("[" + MOD_VERSION + "] Error parsing Base Combat Rating: " + e.getMessage()); }
        
        try {
            naturalArmourIncrease = Float.parseFloat(props.getProperty("Natural Armour", props.getProperty("Natural_Armour", String.valueOf(naturalArmourIncrease))));
        } catch (Exception e) { logger.warning("[" + MOD_VERSION + "] Error parsing Natural Armour: " + e.getMessage()); }
        
        try {
            speedIncrease = Float.parseFloat(props.getProperty("speed", String.valueOf(speedIncrease)));
        } catch (Exception e) { logger.warning("[" + MOD_VERSION + "] Error parsing speed: " + e.getMessage()); }
        
        try {
            announcementFrequency = Integer.parseInt(props.getProperty("announcement_frequency", String.valueOf(announcementFrequency)));
        } catch (Exception e) { logger.warning("[" + MOD_VERSION + "] Error parsing announcement_frequency: " + e.getMessage()); }
        
        try {
            forsakenChance = Float.parseFloat(props.getProperty("forsaken_chance", String.valueOf(forsakenChance)));
        } catch (Exception e) { logger.warning("[" + MOD_VERSION + "] Error parsing forsaken_chance: " + e.getMessage()); }
        
        try {
            teleportThresholdTiles = Integer.parseInt(props.getProperty("teleport_threshold_tiles", String.valueOf(teleportThresholdTiles)));
        } catch (Exception e) { logger.warning("[" + MOD_VERSION + "] Error parsing teleport_threshold_tiles: " + e.getMessage()); }
        
        try {
            teleportDelay = Integer.parseInt(props.getProperty("teleport_delay", String.valueOf(teleportDelay)));
        } catch (Exception e) { logger.warning("[" + MOD_VERSION + "] Error parsing teleport_delay: " + e.getMessage()); }
        
        try {
            teleportBufferTiles = Integer.parseInt(props.getProperty("teleport_buffer_tiles", String.valueOf(teleportBufferTiles)));
        } catch (Exception e) { logger.warning("[" + MOD_VERSION + "] Error parsing teleport_buffer_tiles: " + e.getMessage()); }
        
        try {
            maxHuntDistance = Integer.parseInt(props.getProperty("max_hunt_distance", String.valueOf(maxHuntDistance)));
        } catch (Exception e) { logger.warning("[" + MOD_VERSION + "] Error parsing max_hunt_distance: " + e.getMessage()); }
        
        try {
            enableCelebration = Boolean.parseBoolean(props.getProperty("enableCelebration", String.valueOf(enableCelebration)));
            enableAnnouncements = Boolean.parseBoolean(props.getProperty("enable_announcements", String.valueOf(enableAnnouncements)));
            celebrationDuration = Integer.parseInt(props.getProperty("celebration_duration", String.valueOf(celebrationDuration)));
            enableTeleport = Boolean.parseBoolean(props.getProperty("enable_teleport", String.valueOf(enableTeleport)));
            enableTrailEffects = Boolean.parseBoolean(props.getProperty("enableTrailEffects", String.valueOf(enableTrailEffects)));
            enableInventoryRewards = Boolean.parseBoolean(props.getProperty("enableInventoryRewards", String.valueOf(enableInventoryRewards)));
            enableSecondNames = Boolean.parseBoolean(props.getProperty("enableSecondNames", String.valueOf(enableSecondNames)));
            enableTitles = Boolean.parseBoolean(props.getProperty("enableTitles", String.valueOf(enableTitles)));
            rewardEnabled = Boolean.parseBoolean(props.getProperty("rewardEnabled", String.valueOf(rewardEnabled)));
            enableOptIn = Boolean.parseBoolean(props.getProperty("enable_opt_in", String.valueOf(enableOptIn)));
            minFSReqEnabled = Boolean.parseBoolean(props.getProperty("min_fs_req_enabled", String.valueOf(minFSReqEnabled)));
            minimumFSREQ = Float.parseFloat(props.getProperty("MinimumFSREQ", String.valueOf(minimumFSREQ)));
            enableSkillRetention = Boolean.parseBoolean(props.getProperty("enable_skill_retention", String.valueOf(enableSkillRetention)));
            retainOnForsakenDeath = Boolean.parseBoolean(props.getProperty("retain_on_forsaken_death", String.valueOf(retainOnForsakenDeath)));
            retainOnAnyCreatureDeath = Boolean.parseBoolean(props.getProperty("retain_on_any_creature_death", String.valueOf(retainOnAnyCreatureDeath)));
            serverName = props.getProperty("server_name", "Heavenord").trim();
        } catch (Exception e) { logger.warning("[" + MOD_VERSION + "] Error parsing boolean or basic settings: " + e.getMessage()); }
        
        try {
            for (int i = 0; i < 3; i++) {
                String rangeVal = props.getProperty("skill_retention_range_" + (i + 1));
                if (rangeVal != null && !rangeVal.trim().isEmpty()) {
                    String[] parts = rangeVal.split(":");
                    if (parts.length >= 3) {
                        skillRetentionRanges[i][0] = Float.parseFloat(parts[0]);
                        skillRetentionRanges[i][1] = Float.parseFloat(parts[1]);
                        skillRetentionRanges[i][2] = Float.parseFloat(parts[2]);
                    }
                }
            }
        } catch (Exception e) { logger.warning("[" + MOD_VERSION + "] Error parsing skill retention ranges: " + e.getMessage()); }
        
        try {
            maxLevel = Integer.parseInt(props.getProperty("max_level", String.valueOf(maxLevel)));
            if (bounties.length != maxLevel + 1) {
                bounties = new long[maxLevel + 1];
                for (int i = 1; i <= maxLevel; i++) bounties[i] = i * 20000L; // Default fallback
            }
        } catch (Exception e) { logger.warning("[" + MOD_VERSION + "] Error parsing max_level: " + e.getMessage()); }
        
        try {
            String dailyMax = props.getProperty("max_forsaken_per_day");
            if (dailyMax != null) {
                int oldVal = maxForsakenPerDay;
                maxForsakenPerDay = Integer.parseInt(dailyMax.trim());
                int classHash = System.identityHashCode(ForsakenConfig.class);
                if (debug) logger.info("[" + MOD_VERSION + "]   max_forsaken_per_day set to " + maxForsakenPerDay + " (was " + oldVal + ", ConfigID: " + classHash + ")");
            } else {
                if (debug) logger.info("[" + MOD_VERSION + "]   max_forsaken_per_day not in properties, keeping " + maxForsakenPerDay);
            }
        } catch (Exception e) { logger.warning("[" + MOD_VERSION + "] Error parsing max_forsaken_per_day: " + e.getMessage()); }
        
        try {
            adminPowerLevel = Integer.parseInt(props.getProperty("admin_power_level", String.valueOf(adminPowerLevel)));
        } catch (Exception e) { logger.warning("[" + MOD_VERSION + "] Error parsing admin_power_level: " + e.getMessage()); }
        
        try {
            creatureEffect = parseEffect(props.getProperty("creatureEffect"), creatureEffect);
            creatureParticleName = props.getProperty("creatureParticleName", creatureParticleName);
            trailEffect1 = parseEffect(props.getProperty("trailEffect1"), trailEffect1);
            trailEffect2 = parseEffect(props.getProperty("trailEffect2"), trailEffect2);
        } catch (Exception e) { logger.warning("[" + MOD_VERSION + "] Error parsing effects: " + e.getMessage()); }
        
        try {
            for (int i = 1; i <= maxLevel; i++) {
                String bountyVal = props.getProperty("bounty_level_" + i);
                if (bountyVal != null) {
                    try {
                        bounties[i] = Long.parseLong(bountyVal.trim());
                    } catch (NumberFormatException e) {
                        logger.warning("[" + MOD_VERSION + "] Error parsing bounty_level_" + i + ": " + bountyVal);
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("[" + MOD_VERSION + "] Error in bounty parsing loop: " + e.getMessage());
        }
            
        try {
            for (int i = 1; i <= maxLevel; i++) {
                String val = props.getProperty("bounty_level_" + i + "_items");
                if (val != null) {
                    if (debug) logger.info("[" + MOD_VERSION + "] Found bounty_level_" + i + "_items in properties: '" + val + "'");
                    List<RewardItem> items = new ArrayList<>();
                    if (!val.trim().isEmpty()) {
                        String[] parts = val.split(",");
                        for (String p : parts) {
                            String[] pair = p.trim().split(":");
                            try {
                                int id = Integer.parseInt(pair[0]);
                                byte rarity = (pair.length > 1) ? parseRarity(pair[1]) : 0;
                                byte material = (pair.length > 2) ? parseMaterial(pair[2]) : 0;
                                int quantity = (pair.length > 3) ? Integer.parseInt(pair[3]) : 1;
                                float quality = -1.0f;
                                if (pair.length > 4) {
                                    String qStr = pair[4].trim().toLowerCase();
                                    if (!qStr.equals("random")) {
                                        try {
                                            quality = Float.parseFloat(qStr);
                                        } catch (NumberFormatException e) {
                                            quality = -1.0f;
                                        }
                                    }
                                }
                                RewardItem ri = new RewardItem(id, rarity, material, quantity, quality);
                                items.add(ri);
                                if (debug) logger.info("[" + MOD_VERSION + "]   Parsed Reward Level " + i + ": Template " + id + ", Rarity " + rarity + ", Material " + material + ", Quantity " + quantity + ", Quality " + (quality < 0 ? "random" : quality));
                            } catch (Exception e) {
                                logger.warning("[" + MOD_VERSION + "]   Error parsing reward item '" + p + "' for level " + i + ": " + e.getMessage());
                            }
                        }
                    }
                    if (!items.isEmpty()) {
                        levelRewards.put(i, items);
                        if (debug) logger.info("[" + MOD_VERSION + "]   Level " + i + " rewards updated. Count: " + items.size() + ". Map size: " + levelRewards.size());
                    } else if (val.trim().isEmpty()) {
                        // We do NOT clear automatically here to avoid clearing rewards during merges (e.g. Mod Loader config phase)
                        // Fresh file loads will call levelRewards.clear() before load(props).
                        if (debug) logger.info("[" + MOD_VERSION + "]   Level " + i + " rewards entry was empty in properties, but NOT clearing existing map entry. Map size: " + levelRewards.size());
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("[" + MOD_VERSION + "] Error in reward items parsing loop: " + e.getMessage());
        }
            
        try {
            // Also check for named common skills
            for (Map.Entry<String, Integer> entry : COMMON_SKILL_NAMES.entrySet()) {
                String propKey = entry.getKey();
                String val = props.getProperty(propKey);
                if (val == null) {
                    val = props.getProperty(propKey.replace(" ", "_"));
                }
                if (val != null) {
                    try {
                        double value = Double.parseDouble(val.trim());
                        individualSkillIncreases.put(entry.getValue(), value);
                        if (debug) logger.info("[" + MOD_VERSION + "]     Parsed Named Skill Increase - " + propKey + ": " + value);
                    } catch (NumberFormatException e) {
                        logger.warning("[" + MOD_VERSION + "]     Error parsing named skill increase '" + propKey + "': " + val);
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("[" + MOD_VERSION + "] Error parsing individual skill increases: " + e.getMessage());
        }
            
        try {
            String fn = props.getProperty("first_names");
            if (fn != null && !fn.isEmpty()) {
                firstNames = fn.split(",\\s*");
            }
            
            String sn = props.getProperty("second_names");
            if (sn != null && !sn.isEmpty()) {
                secondNames = sn.split(",\\s*");
            }
            
            String ti = props.getProperty("titles");
            if (ti != null && !ti.isEmpty()) {
                titles = ti.split(",\\s*");
            }
        } catch (Exception e) {
            logger.warning("[" + MOD_VERSION + "] Error parsing names: " + e.getMessage());
        }
            
        loaded = true;
        if (debug) logger.info("[" + MOD_VERSION + "] Forsaken configuration update complete. levelRewards size: " + levelRewards.size());
    }

    private static String configPath = null;
    
    public static String getConfigPath() {
        return configPath;
    }

    public static synchronized void load() {
        if (debug) {
            logger.info("[" + MOD_VERSION + "] Loading Forsaken configuration.");
            logger.info("[" + MOD_VERSION + "] Current working directory: " + new File(".").getAbsolutePath());
        }
        Properties props = new Properties();
        
        // Search paths (relative to current directory)
        String[] paths = {
            "mods/forsaken/forsaken.config", 
            "mods/forsaken.config", 
            "forsaken.config", 
            "../mods/forsaken/forsaken.config",
            "../mods/forsaken.config"
        };
        
        File configFile = null;
        
        // Try searching by path
        for (String p : paths) {
            File f = new File(p);
            if (f.exists()) {
                configFile = f;
                configPath = f.getPath();
                if (debug) logger.info("[" + MOD_VERSION + "] Found configuration file at path: " + f.getAbsolutePath());
                break;
            }
        }
        
        // If not found, try next to the JAR file
        if (configFile == null) {
            try {
                java.net.URL jarLocation = ForsakenConfig.class.getProtectionDomain().getCodeSource().getLocation();
                if (jarLocation != null) {
                    File jarFile = new File(jarLocation.toURI());
                    File jarDir = jarFile.getParentFile();
                    if (jarDir != null) {
                        File f = new File(jarDir, "forsaken.config");
                        if (f.exists()) {
                            configFile = f;
                            configPath = f.getPath();
                            if (debug) logger.info("[" + MOD_VERSION + "] Found configuration file next to JAR at: " + f.getAbsolutePath());
                        }
                    }
                }
            } catch (Exception e) {
                logger.warning("[" + MOD_VERSION + "] Error checking jar directory: " + e.getMessage());
            }
        }
        
        if (configFile != null) {
            if (debug) logger.info("[" + MOD_VERSION + "] Loading configuration from " + configFile.getAbsolutePath());
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
                levelRewards.clear(); // Clear existing rewards before fresh load from file
                individualSkillIncreases.clear(); // Clear existing individual skill increases
                load(props);
            } catch (IOException e) {
                logger.warning("[" + MOD_VERSION + "] Error loading forsaken.config, using defaults: " + e.getMessage());
            }
        } else {
            if (debug) logger.info("[" + MOD_VERSION + "] forsaken.config not found. Checked multiple paths and JAR directory. Using default settings.");
        }
    }

    public static synchronized void save() {
        String path = configPath != null ? configPath : "mods/forsaken.config";
        File configFile = new File(path);
        File parent = configFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        
        try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(configFile))) {
            pw.println("# Forsaken Mod Configuration [" + MOD_VERSION + "]");
            pw.println();
            
            pw.println("# --- General Settings ---");
            pw.println("# Server name used in announcements");
            pw.println("server_name=" + serverName);
            pw.println("# Announcement frequency in minutes");
            pw.println("announcement_frequency=" + announcementFrequency);
            pw.println("# Enable or disable announcements to non-admin players");
            pw.println("enable_announcements=" + enableAnnouncements);
            pw.println("# Chance for a creature to become Forsaken upon killing a player (0.0 to 1.0)");
            pw.println("forsaken_chance=" + forsakenChance);
            pw.println("# Maximum level a Forsaken creature can reach (default 10)");
            pw.println("max_level=" + maxLevel);
            pw.println("# Maximum amount of Forsaken creatures that can be born/spawned in a 24-hour period (Real Time)");
            pw.println("max_forsaken_per_day=" + maxForsakenPerDay);
            pw.println("# Minimum power level required to use /forsaken admin commands (0=Player, 2=Game Master, 5=Arch)");
            pw.println("admin_power_level=" + adminPowerLevel);
            pw.println("# Show verbose debug logs (e.g. item rewards) in the console");
            pw.println("debug=" + debug);
            pw.println();
            
            pw.println("# --- Player Retention & Opt-in Settings ---");
            pw.println("# Enable or disable the opt-in requirement for Forsaken hunts");
            pw.println("enable_opt_in=" + enableOptIn);
            pw.println("# Enable or disable the minimum Fighting Skill requirement for targeting");
            pw.println("min_fs_req_enabled=" + minFSReqEnabled);
            pw.println("# Minimum Fighting Skill required to be targeted by a Forsaken");
            pw.println("MinimumFSREQ=" + minimumFSREQ);
            pw.println("# Enable or disable skill retention on death (deaths caused by creatures or Forsaken)");
            pw.println("enable_skill_retention=" + enableSkillRetention);
            pw.println("# If true, skill retention is applied to deaths caused by Forsaken creatures");
            pw.println("retain_on_forsaken_death=" + retainOnForsakenDeath);
            pw.println("# If true, any creature kill triggers retention.");
            pw.println("retain_on_any_creature_death=" + retainOnAnyCreatureDeath);
            pw.println("# Skill retention ranges (MinFS:MaxFS:RetentionPercentage)");
            for (int i = 0; i < 3; i++) {
                pw.println("skill_retention_range_" + (i + 1) + "=" + skillRetentionRanges[i][0] + ":" + skillRetentionRanges[i][1] + ":" + skillRetentionRanges[i][2]);
            }
            pw.println();

            pw.println("# --- Skill Increases ---");
            pw.println("# Default increase for any other skills (e.g. Weapon skills)");
            pw.println("# Set to 0.0 if you only want the named skills below to increase");
            pw.println("Default_Skill_Increase=" + defaultSkillIncrease);
            pw.println();
            
            pw.println("# --- Common Skill Increases (overrides Default_Skill_Increase) ---");
            pw.println("# Base Combat Rating by default for example dragon = 100, Wild Cat=2");
            pw.println("Base Combat Rating=" + baseCombatRatingIncrease);
            pw.println("# Natural Armour by default is around 0.0 to 0.7 for most creatures");
            pw.println("Natural Armour=" + naturalArmourIncrease);
            pw.println("# Speed is by default around 0.5 - 1.5 overall so 0.1 is a huge increase in itself");
            pw.println("speed=" + speedIncrease);
            pw.println();
            
            for (Map.Entry<String, Integer> entry : COMMON_SKILL_NAMES.entrySet()) {
                String name = entry.getKey();
                int id = entry.getValue();
                double value = individualSkillIncreases.getOrDefault(id, defaultSkillIncrease);
                pw.println(name + "=" + value);
            }
            pw.println();
            
            pw.println("# --- Teleport Settings ---");
            pw.println("# Enable or disable Forsaken teleporting towards players");
            pw.println("enable_teleport=" + enableTeleport);
            pw.println("# Distance in tiles before the creature starts teleporting towards the player (to avoid fences etc)");
            pw.println("teleport_threshold_tiles=" + teleportThresholdTiles);
            pw.println("# Buffer distance in tiles to allow creature to drift away before jumping back to threshold");
            pw.println("teleport_buffer_tiles=" + teleportBufferTiles);
            pw.println("# Delay in seconds between consecutive teleports for the same creature");
            pw.println("teleport_delay=" + teleportDelay);
            pw.println("# Maximum distance the AI will hunt for players before giving up (default 1000 tiles)");
            pw.println("max_hunt_distance=" + maxHuntDistance);
            pw.println();
            
            pw.println("# --- Reward & Bounty Settings ---");
            pw.println("# Enable or disable rewards for slaying Forsaken");
            pw.println("rewardEnabled=" + rewardEnabled);
            pw.println("# Bounty levels (1-" + maxLevel + ") in iron coins (1 silver = 10000 iron)");
            pw.println("# You can add more levels by increasing max_level and adding bounty_level_X lines");
            for (int i = 1; i <= maxLevel; i++) {
                if (i < bounties.length) {
                    pw.println("bounty_level_" + i + "=" + bounties[i]);
                }
            }
            pw.println();

            pw.println("# --- Forsaken Inventory Reward Settings ---");
            pw.println("# Enable or disable inventory rewards for Forsaken creatures");
            pw.println("enableInventoryRewards=" + enableInventoryRewards);
            pw.println("# List of items to spawn in the Forsaken's inventory per level (1-" + maxLevel + ")");
            pw.println("# Format: templateId:rarity:material:quantity:quality");
            pw.println("# Rarity: 0=none, 1=rare, 2=supreme, 3=fantastic (can use names or IDs)");
            pw.println("# Material: (Optional) ID of the material or 'none'. Check Material IDs.txt for a full list of IDs (e.g., 11=iron, 34=tin, 56=adamantine, 67=seryll)");
            pw.println("# Quality: (Optional) 1-100 or 'random'. If set to random or omitted, it will be 50-90.");
            pw.println("# Example: bounty_level_1_items=72:none:none:3:random,20:rare:13:1:80 (3 Leather, 1 Pickaxe:rare:tin:80QL)");
            pw.println("# Check (https://docs.google.com/spreadsheets/d/1XxCk2JVCTj3bblYuaHRZQT2garVo8y3SjcrIZoWBohc/edit?gid=148568124#gid=148568124) for item IDs");
            for (int i = 1; i <= maxLevel; i++) {
                List<RewardItem> items = levelRewards.get(i);
                if (items != null && !items.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (int j = 0; j < items.size(); j++) {
                        RewardItem ri = items.get(j);
                        sb.append(ri.id).append(":").append(ri.rarity).append(":").append(ri.material).append(":").append(ri.quantity);
                        if (ri.quality >= 0) {
                            sb.append(":").append(ri.quality);
                        } else {
                            sb.append(":random");
                        }
                        if (j < items.size() - 1) sb.append(",");
                    }
                    pw.println("bounty_level_" + i + "_items=" + sb.toString());
                } else {
                    pw.println("# bounty_level_" + i + "_items=");
                }
            }
            pw.println();
            
            pw.println("# --- Visuals & Celebration Settings ---");
            pw.println("# Enable or disable celebration fireworks after a Forsaken is slain");
            pw.println("enableCelebration=" + enableCelebration);
            pw.println("# Duration of the celebration in minutes");
            pw.println("celebration_duration=" + celebrationDuration);
            pw.println("# Enable or disable trail slot effects");
            pw.println("enableTrailEffects=" + enableTrailEffects);
            pw.println("# Trail slot effects");
            pw.println("# Check Effects.txt for a full list of effect IDs and particle names");
            pw.println("creatureEffect=" + (creatureEffect == -1 ? "none" : creatureEffect));
            pw.println("creatureParticleName=" + creatureParticleName);
            pw.println("trailEffect1=" + (trailEffect1 == -1 ? "none" : trailEffect1));
            pw.println("trailEffect2=" + (trailEffect2 == -1 ? "none" : trailEffect2));
            pw.println();
            
            pw.println("# --- Naming Settings ---");
            pw.println("# Enable or disable second names and titles");
            pw.println("enableSecondNames=" + enableSecondNames);
            pw.println("enableTitles=" + enableTitles);
            pw.println("# List of first names");
            pw.print("first_names=");
            for (int i = 0; i < firstNames.length; i++) {
                pw.print(firstNames[i]);
                if (i < firstNames.length - 1) pw.print(", ");
            }
            pw.println();
            
            pw.println("# List of second names");
            pw.print("second_names=");
            for (int i = 0; i < secondNames.length; i++) {
                pw.print(secondNames[i]);
                if (i < secondNames.length - 1) pw.print(", ");
            }
            pw.println();
            
            pw.println("# List of titles");
            pw.print("titles=");
            for (int i = 0; i < titles.length; i++) {
                pw.print(titles[i]);
                if (i < titles.length - 1) pw.print(", ");
            }
            pw.println();
            
            logger.info("Forsaken configuration saved to " + configFile.getAbsolutePath());
        } catch (IOException e) {
            logger.warning("Error saving forsaken.config: " + e.getMessage());
        }
    }
}
