package org.tridelth.wurmunlimited.mods.forsaken;

import org.gotti.wurmunlimited.modsupport.ModSupportDb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ForsakenDatabase {
    private static final Logger logger = Logger.getLogger(ForsakenDatabase.class.getName());
    private static final String TABLE_NAME = "ForsakenCreatures";
    private static final String VICTIMS_TABLE = "ForsakenVictims";
    private static final String LIMIT_TABLE = "ForsakenDailyLimit";
    private static final String SETTINGS_TABLE = "ForsakenPlayerSettings";

    public static void init() {
        try (Connection con = ModSupportDb.getModSupportDb()) {
            if (!ModSupportDb.hasTable(con, TABLE_NAME)) {
                if (ForsakenConfig.debug) logger.info(TABLE_NAME + " table not found in ModSupport. Creating table now.");
                String sql = "CREATE TABLE " + TABLE_NAME + " (WURMID LONG PRIMARY KEY, KILLS INT NOT NULL DEFAULT 0, ORIGINAL_NAME TEXT, FORSAKEN_NAME TEXT)";
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.execute();
                }
            } else {
                // Check if we need to add columns
                try (java.sql.ResultSet rs = con.getMetaData().getColumns(null, null, TABLE_NAME, "ORIGINAL_NAME")) {
                    if (!rs.next()) {
                        if (ForsakenConfig.debug) logger.info("Adding ORIGINAL_NAME column to " + TABLE_NAME);
                        try (PreparedStatement ps = con.prepareStatement("ALTER TABLE " + TABLE_NAME + " ADD COLUMN ORIGINAL_NAME TEXT")) {
                            ps.execute();
                        }
                    }
                }
                try (java.sql.ResultSet rs = con.getMetaData().getColumns(null, null, TABLE_NAME, "FORSAKEN_NAME")) {
                    if (!rs.next()) {
                        if (ForsakenConfig.debug) logger.info("Adding FORSAKEN_NAME column to " + TABLE_NAME);
                        try (PreparedStatement ps = con.prepareStatement("ALTER TABLE " + TABLE_NAME + " ADD COLUMN FORSAKEN_NAME TEXT")) {
                            ps.execute();
                        }
                    }
                }
            }

            // Ensure victims table exists
            if (!ModSupportDb.hasTable(con, VICTIMS_TABLE)) {
                if (ForsakenConfig.debug) logger.info(VICTIMS_TABLE + " table not found in ModSupport. Creating table now.");
                String sqlVictims = "CREATE TABLE " + VICTIMS_TABLE + " (WURMID LONG NOT NULL, VICTIM TEXT NOT NULL)";
                try (PreparedStatement ps = con.prepareStatement(sqlVictims)) {
                    ps.execute();
                }
            }

            // Ensure limit table exists
            if (!ModSupportDb.hasTable(con, LIMIT_TABLE)) {
                if (ForsakenConfig.debug) logger.info(LIMIT_TABLE + " table not found in ModSupport. Creating table now.");
                String sqlLimit = "CREATE TABLE " + LIMIT_TABLE + " (ID INT PRIMARY KEY, COUNT INT, LAST_RESET LONG)";
                try (PreparedStatement ps = con.prepareStatement(sqlLimit)) {
                    ps.execute();
                }
                // Initialize with ID 1
                try (PreparedStatement ps = con.prepareStatement("INSERT INTO " + LIMIT_TABLE + " (ID, COUNT, LAST_RESET) VALUES (1, 0, ?)")) {
                    ps.setLong(1, System.currentTimeMillis());
                    ps.execute();
                }
            }
            
            // Ensure settings table exists
            if (!ModSupportDb.hasTable(con, SETTINGS_TABLE)) {
                if (ForsakenConfig.debug) logger.info(SETTINGS_TABLE + " table not found in ModSupport. Creating table now.");
                String sqlSettings = "CREATE TABLE " + SETTINGS_TABLE + " (WURMID LONG PRIMARY KEY, OPTIN INT NOT NULL DEFAULT 0)";
                try (PreparedStatement ps = con.prepareStatement(sqlSettings)) {
                    ps.execute();
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to initialize Forsaken database: " + e.getMessage(), e);
        }
    }

    public static void saveDailyLimit(int count, long lastReset) {
        try (Connection con = ModSupportDb.getModSupportDb()) {
            String sql = "INSERT OR REPLACE INTO " + LIMIT_TABLE + " (ID, COUNT, LAST_RESET) VALUES(1, ?, ?)";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, count);
                ps.setLong(2, lastReset);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to save daily limit: " + e.getMessage(), e);
        }
    }

    public static int loadDailyCount() {
        try (Connection con = ModSupportDb.getModSupportDb()) {
            String sql = "SELECT COUNT FROM " + LIMIT_TABLE + " WHERE ID = 1";
            try (PreparedStatement ps = con.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("COUNT");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load daily count: " + e.getMessage(), e);
        }
        return 0;
    }

    public static long loadLastResetTime() {
        try (Connection con = ModSupportDb.getModSupportDb()) {
            String sql = "SELECT LAST_RESET FROM " + LIMIT_TABLE + " WHERE ID = 1";
            try (PreparedStatement ps = con.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("LAST_RESET");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load last reset time: " + e.getMessage(), e);
        }
        return System.currentTimeMillis();
    }

    public static void saveForsaken(long wurmId, int kills, String originalName, String forsakenName) {
        try (Connection con = ModSupportDb.getModSupportDb()) {
            String sql = "INSERT OR REPLACE INTO " + TABLE_NAME + " (WURMID, KILLS, ORIGINAL_NAME, FORSAKEN_NAME) VALUES(?, ?, ?, ?)";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setLong(1, wurmId);
                ps.setInt(2, kills);
                ps.setString(3, originalName);
                ps.setString(4, forsakenName);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to save Forsaken creature " + wurmId + ": " + e.getMessage(), e);
        }
    }

    public static void deleteForsaken(long wurmId) {
        try (Connection con = ModSupportDb.getModSupportDb()) {
            String sql = "DELETE FROM " + TABLE_NAME + " WHERE WURMID = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setLong(1, wurmId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to delete Forsaken creature " + wurmId + ": " + e.getMessage(), e);
        }
    }

    public static void addVictim(long killerId, String victimName) {
        try (Connection con = ModSupportDb.getModSupportDb()) {
            String sql = "INSERT INTO " + VICTIMS_TABLE + " (WURMID, VICTIM) VALUES(?, ?)";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setLong(1, killerId);
                ps.setString(2, victimName);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to add victim '" + victimName + "' for " + killerId + ": " + e.getMessage(), e);
        }
    }

    public static Map<Long, java.util.List<String>> loadAllVictims() {
        Map<Long, java.util.List<String>> map = new HashMap<>();
        try (Connection con = ModSupportDb.getModSupportDb()) {
            String sql = "SELECT WURMID, VICTIM FROM " + VICTIMS_TABLE;
            try (PreparedStatement ps = con.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("WURMID");
                    String name = rs.getString("VICTIM");
                    map.computeIfAbsent(id, k -> new java.util.ArrayList<>()).add(name);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load Forsaken victims: " + e.getMessage(), e);
        }
        return map;
    }

    public static void deleteVictimsFor(long killerId) {
        try (Connection con = ModSupportDb.getModSupportDb()) {
            String sql = "DELETE FROM " + VICTIMS_TABLE + " WHERE WURMID = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setLong(1, killerId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to delete victims for " + killerId + ": " + e.getMessage(), e);
        }
    }

    public static void savePlayerOptIn(long wurmId, int status) {
        if (ForsakenConfig.debug) logger.info("Saving player opt-in status for " + wurmId + ": " + status);
        try (Connection con = ModSupportDb.getModSupportDb()) {
            String sql = "INSERT OR REPLACE INTO " + SETTINGS_TABLE + " (WURMID, OPTIN) VALUES(?, ?)";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setLong(1, wurmId);
                ps.setInt(2, status);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to save player opt-in status " + wurmId + ": " + e.getMessage(), e);
        }
    }

    public static Map<Long, Integer> loadAllPlayerOptIns() {
        Map<Long, Integer> map = new HashMap<>();
        try (Connection con = ModSupportDb.getModSupportDb()) {
            String sql = "SELECT WURMID, OPTIN FROM " + SETTINGS_TABLE;
            try (PreparedStatement ps = con.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getLong("WURMID"), rs.getInt("OPTIN"));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load player opt-in settings: " + e.getMessage(), e);
        }
        return map;
    }

    public static Map<Long, Integer> loadForsakenKills() {
        Map<Long, Integer> killsMap = new HashMap<>();
        try (Connection con = ModSupportDb.getModSupportDb()) {
            String sql = "SELECT WURMID, KILLS FROM " + TABLE_NAME;
            try (PreparedStatement ps = con.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    killsMap.put(rs.getLong("WURMID"), rs.getInt("KILLS"));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load Forsaken kills: " + e.getMessage(), e);
        }
        return killsMap;
    }

    public static Map<Long, String> loadOriginalNames() {
        Map<Long, String> namesMap = new HashMap<>();
        try (Connection con = ModSupportDb.getModSupportDb()) {
            String sql = "SELECT WURMID, ORIGINAL_NAME FROM " + TABLE_NAME;
            try (PreparedStatement ps = con.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("ORIGINAL_NAME");
                    if (name != null) {
                        namesMap.put(rs.getLong("WURMID"), name);
                    }
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load original names: " + e.getMessage(), e);
        }
        return namesMap;
    }

    public static Map<Long, String> loadForsakenNames() {
        Map<Long, String> namesMap = new HashMap<>();
        try (Connection con = ModSupportDb.getModSupportDb()) {
            String sql = "SELECT WURMID, FORSAKEN_NAME FROM " + TABLE_NAME;
            try (PreparedStatement ps = con.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("FORSAKEN_NAME");
                    if (name != null) {
                        namesMap.put(rs.getLong("WURMID"), name);
                    }
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load Forsaken names: " + e.getMessage(), e);
        }
        return namesMap;
    }
}
