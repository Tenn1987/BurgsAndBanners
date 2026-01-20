package com.brandon.burgsbanners.burg;

import com.brandon.burgsbanners.burg.storage.BurgStorage;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class BurgManager {

    private final JavaPlugin plugin;
    private final BurgStorage storage;

    private final Map<String, Burg> burgsById = new HashMap<>();
    private final Map<UUID, String> memberToBurgId = new HashMap<>();

    // Global index: chunk key -> burgId
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

        for (Burg burg : loaded.values()) {
            for (UUID member : burg.getMembers()) {
                memberToBurgId.put(member, burg.getId());
            }
            for (ChunkClaim claim : burg.getClaims()) {
                claimKeyToBurgId.put(claim.toKey(), burg.getId());
            }
        }

        plugin.getLogger().info("Loaded " + burgsById.size() + " burg(s).");
    }

    public void saveAll() {
        for (Burg burg : burgsById.values()) {
            storage.saveBurg(burg);
        }
    }

    public Burg getBurgByMember(UUID playerId) {
        String id = memberToBurgId.get(playerId);
        return (id == null) ? null : burgsById.get(id);
    }

    public boolean burgExists(String name) {
        return burgsById.values().stream().anyMatch(b -> b.getName().equalsIgnoreCase(name));
    }

    public Burg createBurg(String name, UUID leader) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        Burg burg = new Burg(id, name, leader);
        burgsById.put(id, burg);

        indexMembers(burg);
        storage.saveBurg(burg);

        return burg;
    }

    private void indexMembers(Burg burg) {
        for (UUID member : burg.getMembers()) {
            memberToBurgId.put(member, burg.getId());
        }
    }

    public void save(Burg burg) {
        storage.saveBurg(burg);
    }

    public int getClaimCost(Burg burg) {
        // placeholder scaling rule
        return 50 + (burg.getClaimCount() * 25);
    }

    public String getOwnerBurgId(ChunkClaim claim) {
        return claimKeyToBurgId.get(claim.toKey());
    }

    public boolean isClaimed(ChunkClaim claim) {
        return claimKeyToBurgId.containsKey(claim.toKey());
    }

    public boolean tryAddClaim(Burg burg, ChunkClaim claim) {
        // already claimed by someone (including self)
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
}