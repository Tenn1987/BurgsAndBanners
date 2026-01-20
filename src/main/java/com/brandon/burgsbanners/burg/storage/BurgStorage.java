package com.brandon.burgsbanners.burg.storage;

import com.brandon.burgsbanners.burg.Burg;
import com.brandon.burgsbanners.burg.ChunkClaim;
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
            try {
                leader = UUID.fromString(leaderStr);
            } catch (IllegalArgumentException ex) {
                continue;
            }

            Burg burg = new Burg(id, name, leader);

            // Members
            List<String> memberStrings = yaml.getStringList(path + ".members");
            Set<UUID> members = memberStrings.stream().map(s -> {
                try { return UUID.fromString(s); } catch (Exception e) { return null; }
            }).filter(Objects::nonNull).collect(Collectors.toSet());

            members.add(leader);
            burg.getMembers().clear();
            burg.getMembers().addAll(members);

            // Economy & status
            burg.deposit(yaml.getDouble(path + ".treasury", 0.0));
            burg.setMorale(yaml.getDouble(path + ".morale", 50.0));
            burg.setBanner(yaml.getBoolean(path + ".banner", false));

            // Claims
            List<String> claimKeys = yaml.getStringList(path + ".claims");
            for (String key : claimKeys) {
                try {
                    ChunkClaim claim = ChunkClaim.fromKey(key);
                    if (claim != null) burg.addClaim(claim);
                } catch (Exception ignored) {
                }
            }

            result.put(id, burg);
        }

        return result;
    }

    public void saveBurg(Burg burg) {
        String path = "burgs." + burg.getId();

        yaml.set(path + ".name", burg.getName());
        yaml.set(path + ".leader", burg.getLeader().toString());

        List<String> members = burg.getMembers().stream().map(UUID::toString).toList();
        yaml.set(path + ".members", members);

        yaml.set(path + ".treasury", burg.getTreasury());
        yaml.set(path + ".morale", burg.getMorale());
        yaml.set(path + ".banner", burg.hasBanner());

        List<String> claims = burg.getClaims().stream().map(ChunkClaim::toKey).toList();
        yaml.set(path + ".claims", claims);

        saveFile();
    }

    public void deleteBurg(String burgId) {
        yaml.set("burgs." + burgId, null);
        saveFile();
    }
}
