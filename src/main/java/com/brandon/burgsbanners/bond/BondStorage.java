package com.brandon.burgsbanners.bond;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BondStorage {

    private final JavaPlugin plugin;
    private final File file;

    public BondStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bonds.yml");
    }

    public Map<UUID, BurgBond> loadAll() {
        Map<UUID, BurgBond> out = new HashMap<>();

        if (!file.exists()) {
            return out;
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        if (!cfg.isConfigurationSection("bonds")) {
            return out;
        }

        for (String key : cfg.getConfigurationSection("bonds").getKeys(false)) {
            String path = "bonds." + key + ".";

            try {
                UUID bondId = UUID.fromString(key);
                String burgId = cfg.getString(path + "burgId");
                UUID ownerUuid = UUID.fromString(cfg.getString(path + "ownerUuid"));
                String currency = cfg.getString(path + "currency", "SHEKEL");
                long principal = cfg.getLong(path + "principal");
                long payout = cfg.getLong(path + "payout");
                long issuedAt = cfg.getLong(path + "issuedAt");
                long maturesAt = cfg.getLong(path + "maturesAt");
                boolean redeemed = cfg.getBoolean(path + "redeemed", false);

                if (burgId == null) {
                    plugin.getLogger().warning("[Bonds] Skipping bond " + key + " (missing burgId)");
                    continue;
                }

                BurgBond bond = new BurgBond(
                        bondId, burgId, ownerUuid, currency,
                        principal, payout, issuedAt, maturesAt, redeemed
                );
                out.put(bondId, bond);

            } catch (Exception ex) {
                plugin.getLogger().warning("[Bonds] Failed to load bond " + key + ": " + ex.getMessage());
            }
        }

        plugin.getLogger().info("[Bonds] Loaded " + out.size() + " bond(s).");
        return out;
    }

    public void saveAll(Map<UUID, BurgBond> bonds) {
        YamlConfiguration cfg = new YamlConfiguration();

        for (BurgBond bond : bonds.values()) {
            String path = "bonds." + bond.getBondId() + ".";
            cfg.set(path + "burgId", bond.getBurgId());
            cfg.set(path + "ownerUuid", bond.getOwnerUuid().toString());
            cfg.set(path + "currency", bond.getCurrency());
            cfg.set(path + "principal", bond.getPrincipal());
            cfg.set(path + "payout", bond.getPayout());
            cfg.set(path + "issuedAt", bond.getIssuedAt());
            cfg.set(path + "maturesAt", bond.getMaturesAt());
            cfg.set(path + "redeemed", bond.isRedeemed());
        }

        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("[Bonds] Could not save bonds.yml: " + e.getMessage());
        }
    }
}