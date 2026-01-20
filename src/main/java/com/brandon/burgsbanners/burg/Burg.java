package com.brandon.burgsbanners.burg;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Burg {

    private final String id;
    private String name;

    private UUID leader;
    private final Set<UUID> members = new HashSet<>();

    private double treasury;
    private double morale; // design: 0..100 later
    private boolean banner;

    // Territory: chunk claims
    private final Set<ChunkClaim> claims = new HashSet<>();

    public Burg(String id, String name, UUID leader) {
        this.id = id;
        this.name = name;
        this.leader = leader;
        this.members.add(leader);

        this.treasury = 0.0;
        this.morale = 50.0;
        this.banner = false;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public UUID getLeader() { return leader; }
    public Set<UUID> getMembers() { return members; }

    public double getTreasury() { return treasury; }
    public double getMorale() { return morale; }
    public boolean hasBanner() { return banner; }

    public Set<ChunkClaim> getClaims() { return claims; }
    public int getClaimCount() { return claims.size(); }

    public void setName(String name) { this.name = name; }
    public void setLeader(UUID leader) { this.leader = leader; }

    public boolean isMember(UUID uuid) { return members.contains(uuid); }
    public void addMember(UUID uuid) { members.add(uuid); }
    public void removeMember(UUID uuid) { members.remove(uuid); }

    public void setMorale(double morale) { this.morale = morale; }

    public void deposit(double amount) {
        if (amount <= 0) return;
        treasury += amount;
    }

    public boolean withdraw(double amount) {
        if (amount <= 0) return true;
        if (treasury < amount) return false;
        treasury -= amount;
        return true;
    }

    public void setBanner(boolean banner) {
        this.banner = banner;
    }

    public boolean addClaim(ChunkClaim claim) {
        return claims.add(claim);
    }

    public boolean removeClaim(ChunkClaim claim) {
        return claims.remove(claim);
    }

    public boolean hasClaim(ChunkClaim claim) {
        return claims.contains(claim);
    }

    // placeholders
    public double estimateUpkeep() {
        double base = getClaimCount() * 5.0;
        if (banner) base += 10.0;
        return base;
    }

    public double estimateIncome() {
        return Math.max(0, getClaimCount() * 2.0);
    }
}
