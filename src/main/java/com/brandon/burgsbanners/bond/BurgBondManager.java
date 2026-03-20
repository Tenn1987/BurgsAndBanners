package com.brandon.burgsbanners.bond;

import com.brandon.burgsbanners.burg.Burg;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;

public class BurgBondManager {

    private final JavaPlugin plugin;
    private final BondStorage storage;
    private final Map<UUID, BurgBond> bonds = new HashMap<>();

    public BurgBondManager(JavaPlugin plugin, BondStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public void loadAll() {
        bonds.clear();
        bonds.putAll(storage.loadAll());
    }

    public void saveAll() {
        storage.saveAll(bonds);
    }

    public BurgBond issueBond(Burg burg, UUID playerUuid, long principal, double rate, long durationMillis) {
        long now = System.currentTimeMillis();
        long payout = Math.round(principal * (1.0 + rate));

        BurgBond bond = new BurgBond(
                UUID.randomUUID(),
                burg.getId(),
                playerUuid,
                burg.getAdoptedCurrencyCode(),
                principal,
                payout,
                now,
                now + durationMillis,
                false
        );

        bonds.put(bond.getBondId(), bond);
        return bond;
    }

    public List<BurgBond> getPlayerBonds(UUID playerUuid) {
        return bonds.values().stream()
                .filter(b -> b.getOwnerUuid().equals(playerUuid))
                .sorted(Comparator.comparingLong(BurgBond::getIssuedAt))
                .collect(Collectors.toList());
    }

    public List<BurgBond> getMatureBonds(UUID playerUuid, String burgId) {
        return bonds.values().stream()
                .filter(b -> b.getOwnerUuid().equals(playerUuid))
                .filter(b -> b.getBurgId().equalsIgnoreCase(burgId))
                .filter(b -> !b.isRedeemed())
                .filter(BurgBond::isMature)
                .sorted(Comparator.comparingLong(BurgBond::getIssuedAt))
                .collect(Collectors.toList());
    }

    public boolean redeemBond(Burg burg, BurgBond bond) {
        if (bond == null) return false;
        if (bond.isRedeemed()) return false;
        if (!bond.isMature()) return false;
        if (!burg.getId().equalsIgnoreCase(bond.getBurgId())) return false;

        boolean ok = burg.debitTreasury(bond.getCurrency(), bond.getPayout());
        if (!ok) return false;

        bond.setRedeemed(true);
        return true;
    }

    public long getOutstandingDebt(String burgId, String currency) {
        return bonds.values().stream()
                .filter(b -> b.getBurgId().equalsIgnoreCase(burgId))
                .filter(b -> !b.isRedeemed())
                .filter(b -> b.getCurrency().equalsIgnoreCase(currency))
                .mapToLong(BurgBond::getPayout)
                .sum();
    }

    public Map<UUID, BurgBond> getAllBonds() {
        return Collections.unmodifiableMap(bonds);
    }

    public void debugDumpToLog() {
        plugin.getLogger().info("[Bonds] In-memory bond count = " + bonds.size());
    }
}