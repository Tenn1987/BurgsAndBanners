package com.brandon.burgsbanners.burg;

import com.brandon.burgsbanners.burg.storage.BurgStorage;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class BurgManager {

    private final JavaPlugin plugin;
    private final BurgStorage storage;

    private final Map<String, Burg> burgsById = new HashMap<>();
    private final Map<UUID, String> memberToBurgId = new HashMap<>();

    // Global index: claim key -> burgId
    private final Map<String, String> claimKeyToBurgId = new HashMap<>();

    public BurgManager(JavaPlugin plugin, BurgStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public void loadAll() {
        burgsById.clear();
        memberToBurgId.clear();
        claimKeyToBurgId.clear();

        Map<String, Burg> loaded = storage.loadAllBurgs();
        burgsById.putAll(loaded);

        int migratedTreasuries = 0;

        for (Burg burg : loaded.values()) {

            // ✅ MIGRATION: ensure every burg has a persistent treasury UUID
            if (burg.getTreasuryUuid() == null) {
                burg.setTreasuryUuid(UUID.randomUUID());
                storage.saveBurg(burg);
                migratedTreasuries++;
            }

            for (UUID member : burg.getMembers()) {
                memberToBurgId.put(member, burg.getId());
            }
            for (ChunkClaim claim : burg.getClaims()) {
                claimKeyToBurgId.put(claim.toKey(), burg.getId());
            }
        }

        plugin.getLogger().info("Loaded " + burgsById.size() + " burg(s).");
        if (migratedTreasuries > 0) {
            plugin.getLogger().info("Migrated " + migratedTreasuries + " burg treasury UUID(s).");
        }
    }

    public void saveAll() {
        for (Burg burg : burgsById.values()) {
            storage.saveBurg(burg);
        }
    }

    public void save(Burg burg) {
        storage.saveBurg(burg);
    }

    public Burg getBurgByMember(UUID playerId) {
        String id = memberToBurgId.get(playerId);
        return (id == null) ? null : burgsById.get(id);
    }

    public boolean burgExists(String name) {
        return burgsById.values().stream().anyMatch(b -> b.getName().equalsIgnoreCase(name));
    }

    public boolean isClaimed(ChunkClaim claim) {
        return claimKeyToBurgId.containsKey(claim.toKey());
    }

    /**
     * O(1) lookup: which burg owns this claim?
     * Returns null for wilderness.
     */
    public Burg getBurgByClaim(ChunkClaim claim) {
        String id = claimKeyToBurgId.get(claim.toKey());
        return (id == null) ? null : burgsById.get(id);
    }

    /**
     * Convenience: burg at player location (or null if wilderness).
     */
    public Burg getBurgAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;

        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;

        ChunkClaim claim = new ChunkClaim(loc.getWorld().getUID(), cx, cz);
        return getBurgByClaim(claim);
    }

    /**
     * ✅ The ONE true treasury wallet identity for the burg at this location.
     * Returns null in wilderness.
     */
    public UUID treasuryIdAt(Location loc) {
        Burg b = getBurgAt(loc);
        return (b == null) ? null : b.getTreasuryUuid();
    }

    public boolean tryAddClaim(Burg burg, ChunkClaim claim) {
        String owner = claimKeyToBurgId.get(claim.toKey());
        if (owner != null) return false;

        boolean added = burg.addClaim(claim);
        if (!added) return false;

        claimKeyToBurgId.put(claim.toKey(), burg.getId());
        storage.saveBurg(burg);
        return true;
    }

    public boolean tryRemoveClaim(Burg burg, ChunkClaim claim) {
        String owner = claimKeyToBurgId.get(claim.toKey());
        if (owner == null) return false;
        if (!owner.equals(burg.getId())) return false;

        boolean removed = burg.removeClaim(claim);
        if (!removed) return false;

        claimKeyToBurgId.remove(claim.toKey());
        storage.saveBurg(burg);
        return true;
    }

    /**
     * Founding flow: creates BURG stage city-state with adopted MPC currency,
     * starter claims, government roles, treasury balances-only.
     */
    public Burg createBurgFounding(
            String name,
            UUID leader,
            World world,
            Location home,
            String adoptedCurrencyCode,
            Set<ChunkClaim> starterClaims
    ) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        Burg burg = Burg.createFounding(id, name, leader, world, home, adoptedCurrencyCode, starterClaims);

        // Ensure treasury UUID exists (should, but belt+suspenders)
        if (burg.getTreasuryUuid() == null) {
            burg.setTreasuryUuid(UUID.randomUUID());
        }

        burgsById.put(id, burg);

        // Index members + claims
        for (UUID member : burg.getMembers()) {
            memberToBurgId.put(member, burg.getId());
        }
        for (ChunkClaim claim : burg.getClaims()) {
            claimKeyToBurgId.put(claim.toKey(), burg.getId());
        }

        storage.saveBurg(burg);
        return burg;
    }

    public void onMemberLeft(Burg burg, UUID member) {
        memberToBurgId.remove(member);
        storage.saveBurg(burg);
    }
}
