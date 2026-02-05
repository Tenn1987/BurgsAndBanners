package com.brandon.burgsbanners.burg.storage;

import com.brandon.burgsbanners.burg.*;
import com.brandon.burgsbanners.burg.plot.Plot;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class BurgStorage {

    private final JavaPlugin plugin;
    private final File file;

    public BurgStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "burgs.yml");
    }

    // ===== Compatibility API (WHAT BurgManager expects) =====
    public Map<String, Burg> loadAllBurgs() {
        return loadAll();
    }

    public void saveBurg(Burg burg) {
        Map<String, Burg> all = loadAll();
        all.put(burg.getId(), burg);
        saveAll(all);
    }

    // ===== Your existing API =====
    public Map<String, Burg> loadAll() {
        if (!file.exists()) {
            return new LinkedHashMap<>();
        }

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("burgs");
        if (root == null) return new LinkedHashMap<>();

        Map<String, Burg> out = new LinkedHashMap<>();

        for (String id : root.getKeys(false)) {
            ConfigurationSection cs = root.getConfigurationSection(id);
            if (cs == null) continue;

            Burg b = new Burg(id);
            b.setName(cs.getString("name", id));

            String stage = cs.getString("stage", "BURG");
            try { b.setPolityStage(PolityStage.valueOf(stage.toUpperCase(Locale.ROOT))); }
            catch (Exception ignored) { b.setPolityStage(PolityStage.BURG); }

            String leader = cs.getString("leaderUuid", null);
            if (leader != null) {
                try { b.setLeaderUuid(UUID.fromString(leader)); } catch (Exception ignored) { }
            }

            b.setRulerTitle(cs.getString("rulerTitle", "Lord-Mayor"));
            b.setAdoptedCurrencyCode(cs.getString("currency", "SHEKEL"));

            // treasury identity (institutional wallet)
            String treasuryUuid = cs.getString("treasuryUuid", null);
            if (treasuryUuid != null && !treasuryUuid.isBlank()) {
                try { b.setTreasuryUuid(UUID.fromString(treasuryUuid)); } catch (Exception ignored) { }
            }

            // ✅ tax policy
            // stored as a rate (0.05 = 5%), clamp handled in Burg setter
            double salesRate = cs.getDouble("tax.sales", 0.05);
            b.setSalesTaxRate(salesRate);

            // home
            String worldStr = cs.getString("home.world", null);
            if (worldStr != null) {
                try { b.setWorldId(UUID.fromString(worldStr)); } catch (Exception ignored) { }
            }
            int x = cs.getInt("home.x", 0);
            int y = cs.getInt("home.y", 64);
            int z = cs.getInt("home.z", 0);
            if (b.getWorldId() != null) {
                World w = plugin.getServer().getWorld(b.getWorldId());
                if (w != null) b.setHome(new Location(w, x, y, z));
            }

            // members
            List<String> members = cs.getStringList("members");
            for (String m : members) {
                try { b.getMembers().add(UUID.fromString(m)); } catch (Exception ignored) { }
            }

            // claims
            for (String s : cs.getStringList("claims")) {
                ChunkClaim cc = parseClaim(s);
                if (cc != null) b.addClaim(cc);
            }

            // treasury balances
            ConfigurationSection t = cs.getConfigurationSection("treasury");
            if (t != null) {
                for (String code : t.getKeys(false)) {
                    long bal = t.getLong(code, 0L);
                    b.getTreasuryBalances().put(code.toUpperCase(Locale.ROOT), bal);
                }
            }

            // food
            b.setBaseFoodCapacity(cs.getDouble("food.baseCapacity", 0.0));
            b.setLastFoodPoints(cs.getDouble("food.lastPoints", 0.0));
            b.setLastScanEpochSeconds(cs.getLong("food.lastScan", 0L));

            // plots
            ConfigurationSection plots = cs.getConfigurationSection("plots");
            if (plots != null) {
                for (String pid : plots.getKeys(false)) {
                    ConfigurationSection ps = plots.getConfigurationSection(pid);
                    if (ps == null) continue;

                    try {
                        Plot p = new Plot(
                                pid,
                                ps.getString("name", pid),
                                UUID.fromString(ps.getString("world")),
                                ps.getInt("minX"), ps.getInt("minY"), ps.getInt("minZ"),
                                ps.getInt("maxX"), ps.getInt("maxY"), ps.getInt("maxZ")
                        );
                        b.putPlot(p);
                    } catch (Exception ignored) { }
                }
            }

            out.put(id, b);
        }

        return out;
    }

    public void saveAll(Map<String, Burg> burgs) {
        if (!plugin.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }

        YamlConfiguration yml = new YamlConfiguration();
        ConfigurationSection root = yml.createSection("burgs");

        for (Burg b : burgs.values()) {
            ConfigurationSection cs = root.createSection(b.getId());
            cs.set("name", b.getName());
            cs.set("stage", b.getPolityStage().name());
            cs.set("leaderUuid", b.getLeaderUuid() == null ? null : b.getLeaderUuid().toString());
            cs.set("rulerTitle", b.getRulerTitle());
            cs.set("currency", b.getAdoptedCurrencyCode());
            cs.set("treasuryUuid", b.getTreasuryUuid() == null ? null : b.getTreasuryUuid().toString());

            // ✅ tax policy
            cs.set("tax.sales", b.getSalesTaxRate());

            if (b.getWorldId() != null) {
                cs.set("home.world", b.getWorldId().toString());
                World w = plugin.getServer().getWorld(b.getWorldId());
                Location home = (w == null) ? null : b.getHome(w);
                if (home != null) {
                    cs.set("home.x", home.getBlockX());
                    cs.set("home.y", home.getBlockY());
                    cs.set("home.z", home.getBlockZ());
                }
            }

            List<String> members = new ArrayList<>();
            for (UUID u : b.getMembers()) members.add(u.toString());
            cs.set("members", members);

            List<String> claims = new ArrayList<>();
            for (ChunkClaim cc : b.getClaims()) claims.add(formatClaim(cc));
            cs.set("claims", claims);

            ConfigurationSection t = cs.createSection("treasury");
            for (Map.Entry<String, Long> e : b.getTreasuryBalances().entrySet()) {
                t.set(e.getKey().toUpperCase(Locale.ROOT), e.getValue());
            }

            cs.set("food.baseCapacity", b.getBaseFoodCapacity());
            cs.set("food.lastPoints", b.getLastFoodPoints());
            cs.set("food.lastScan", b.getLastScanEpochSeconds());

            if (!b.getPlots().isEmpty()) {
                ConfigurationSection plots = cs.createSection("plots");
                b.getPlots().forEach((pid, p) -> {
                    ConfigurationSection ps = plots.createSection(pid);
                    ps.set("name", p.getName());
                    ps.set("world", p.getWorldId().toString());
                    ps.set("minX", p.getMinX()); ps.set("minY", p.getMinY()); ps.set("minZ", p.getMinZ());
                    ps.set("maxX", p.getMaxX()); ps.set("maxY", p.getMaxY()); ps.set("maxZ", p.getMaxZ());
                });
            }
        }

        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save burgs.yml: " + e.getMessage());
        }
    }

    private String formatClaim(ChunkClaim cc) {
        return cc.getWorldId() + ":" + cc.getChunkX() + ":" + cc.getChunkZ();
    }

    private ChunkClaim parseClaim(String s) {
        if (s == null) return null;
        String[] parts = s.split(":");
        if (parts.length != 3) return null;
        try {
            UUID world = UUID.fromString(parts[0]);
            int x = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            return new ChunkClaim(world, x, z);
        } catch (Exception e) {
            return null;
        }
    }
}
