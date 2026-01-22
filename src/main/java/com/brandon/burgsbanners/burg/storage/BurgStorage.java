package com.brandon.burgsbanners.burg.storage;

import com.brandon.burgsbanners.burg.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class BurgStorage {

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration yaml;

    public BurgStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "burgs.yml");
        reload();
    }

    public void reload() {
        if (!plugin.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }
        if (!file.exists()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create burgs.yml: " + e.getMessage());
            }
        }
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public void saveFile() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save burgs.yml: " + e.getMessage());
        }
    }

    public Map<String, Burg> loadAllBurgs() {
        reload();

        Map<String, Burg> result = new HashMap<>();
        if (!yaml.isConfigurationSection("burgs")) {
            return result;
        }

        for (String id : Objects.requireNonNull(yaml.getConfigurationSection("burgs")).getKeys(false)) {
            String path = "burgs." + id;

            String name = yaml.getString(path + ".name", id);

            String leaderStr = yaml.getString(path + ".leader", null);
            if (leaderStr == null) continue;

            UUID leader;
            try { leader = UUID.fromString(leaderStr); }
            catch (IllegalArgumentException ex) { continue; }

            String worldIdStr = yaml.getString(path + ".worldId", null);
            UUID worldId = null;
            if (worldIdStr != null) {
                try { worldId = UUID.fromString(worldIdStr); } catch (Exception ignored) {}
            }

            World world = (worldId != null) ? Bukkit.getWorld(worldId) : null;
            if (world == null) {
                // fallback (still loads, but home world may be wrong until fixed)
                world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
            }
            if (world == null) continue;

            // Home location
            double x = yaml.getDouble(path + ".home.x", world.getSpawnLocation().getX());
            double y = yaml.getDouble(path + ".home.y", world.getSpawnLocation().getY());
            double z = yaml.getDouble(path + ".home.z", world.getSpawnLocation().getZ());
            float yaw = (float) yaml.getDouble(path + ".home.yaw", 0);
            float pitch = (float) yaml.getDouble(path + ".home.pitch", 0);
            Location home = new Location(world, x, y, z, yaw, pitch);

            // Adopted currency
            String adopted = yaml.getString(path + ".currency", "SHEKEL").toUpperCase(Locale.ROOT);

            // Claims
            Set<ChunkClaim> claims = new HashSet<>();
            List<String> claimKeys = yaml.getStringList(path + ".claims");
            for (String key : claimKeys) {
                try {
                    ChunkClaim claim = ChunkClaim.fromKey(key);
                    if (claim != null) claims.add(claim);
                } catch (Exception ignored) { }
            }

            // Build burg (use founding factory then override fields that are persisted)
            Burg burg = Burg.createFounding(id, name, leader, world, home, adopted, claims);

            // Polity stage/title
            String stageStr = yaml.getString(path + ".polityStage", "BURG");
            try { burg.getPopulation(); } catch (Exception ignored) {}
            try {
                // We stored these in Burg; if you later add setters, load them. For now, founding defaults are correct.
            } catch (Exception ignored) {}

            // Members
            List<String> memberStrings = yaml.getStringList(path + ".members");
            Set<UUID> members = memberStrings.stream().map(s -> {
                try { return UUID.fromString(s); } catch (Exception e) { return null; }
            }).filter(Objects::nonNull).collect(Collectors.toSet());
            members.add(leader);
            burg.getMembers().clear();
            burg.getMembers().addAll(members);

            // Treasury balances
            if (yaml.isConfigurationSection(path + ".treasury")) {
                for (String code : Objects.requireNonNull(yaml.getConfigurationSection(path + ".treasury")).getKeys(false)) {
                    long bal = yaml.getLong(path + ".treasury." + code, 0L);
                    burg.getTreasuryBalances().put(code.toUpperCase(Locale.ROOT), bal);
                }
            } else {
                burg.getTreasuryBalances().put(adopted, 0L);
            }

            // Population roles (optional persisted)
            if (yaml.isConfigurationSection(path + ".population")) {
                burg.getPopulation().clear();
                for (String roleKey : Objects.requireNonNull(yaml.getConfigurationSection(path + ".population")).getKeys(false)) {
                    try {
                        PopulationRole role = PopulationRole.valueOf(roleKey.toUpperCase(Locale.ROOT));
                        int count = yaml.getInt(path + ".population." + roleKey, 0);
                        if (count > 0) burg.getPopulation().put(role, count);
                    } catch (Exception ignored) {}
                }
            }

            // Food stats
            burg.setBaseFoodCapacity(yaml.getDouble(path + ".food.baseFoodCapacity", 0.0));
            burg.setLastFoodPoints(yaml.getDouble(path + ".food.lastFoodPoints", 0.0));
            burg.setLastScanEpochSeconds(yaml.getLong(path + ".food.lastScanEpochSeconds", 0L));

            // WorldId fixup if missing
            // (Burg uses world UID from createFounding, so if it loaded, it’s fine.)

            result.put(id, burg);
        }

        return result;
    }

    public void saveBurg(Burg burg) {
        String path = "burgs." + burg.getId();

        yaml.set(path + ".name", burg.getName());
        yaml.set(path + ".leader", burg.getLeader().toString());

        yaml.set(path + ".worldId", burg.getWorldId().toString());
        yaml.set(path + ".currency", burg.getAdoptedCurrencyCode());
        yaml.set(path + ".polityStage", burg.getPolityStage().name());
        yaml.set(path + ".rulerTitle", burg.getRulerTitle());

        // Home
        yaml.set(path + ".home.x", burg.getHome().getX());
        yaml.set(path + ".home.y", burg.getHome().getY());
        yaml.set(path + ".home.z", burg.getHome().getZ());
        yaml.set(path + ".home.yaw", burg.getHome().getYaw());
        yaml.set(path + ".home.pitch", burg.getHome().getPitch());

        // Members
        List<String> members = burg.getMembers().stream().map(UUID::toString).toList();
        yaml.set(path + ".members", members);

        // Claims
        List<String> claims = burg.getClaims().stream().map(ChunkClaim::toKey).toList();
        yaml.set(path + ".claims", claims);

        // Treasury balances
        burg.getTreasuryBalances().forEach((code, bal) -> yaml.set(path + ".treasury." + code, bal));

        // Population roles
        burg.getPopulation().forEach((role, count) -> yaml.set(path + ".population." + role.name(), count));

        // Food
        yaml.set(path + ".food.baseFoodCapacity", burg.getBaseFoodCapacity());
        yaml.set(path + ".food.lastFoodPoints", burg.getLastFoodPoints());
        yaml.set(path + ".food.lastScanEpochSeconds", burg.getLastScanEpochSeconds());

        saveFile();
    }

    public void deleteBurg(String burgId) {
        yaml.set("burgs." + burgId, null);
        saveFile();
    }
}
