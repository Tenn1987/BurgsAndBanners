package com.brandon.burgsbanners.burg;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;

public class Burg {

    private final String id;
    private String name;

    private final UUID worldId;
    private final Location home;

    private UUID leader;
    private final Set<UUID> members = new HashSet<>();

    // Polity
    private PolityStage polityStage;
    private String rulerTitle;

    // Economy (adopted currency + balances only)
    private String adoptedCurrencyCode;
    private final Map<String, Long> treasuryBalances = new HashMap<>();

    // Abstract population roles
    private final EnumMap<PopulationRole, Integer> population = new EnumMap<>(PopulationRole.class);

    // Food scan outputs
    private double baseFoodCapacity;       // BFC
    private double lastFoodPoints;         // last scan FP total
    private long lastScanEpochSeconds;     // seconds since epoch

    // Territory: chunk claims
    private final Set<ChunkClaim> claims = new HashSet<>();

    private Burg(String id, String name, UUID leader, UUID worldId, Location home) {
        this.id = id;
        this.name = name;
        this.leader = leader;
        this.worldId = worldId;
        this.home = home;

        this.members.add(leader);
    }

    public static Burg createFounding(
            String id,
            String name,
            UUID leader,
            World world,
            Location home,
            String adoptedCurrencyCode,
            Set<ChunkClaim> starterClaims
    ) {
        Burg b = new Burg(id, name, leader, world.getUID(), home);

        b.polityStage = PolityStage.BURG;
        b.rulerTitle = "Lord-Mayor";

        b.adoptedCurrencyCode = adoptedCurrencyCode.toUpperCase(Locale.ROOT);
        b.treasuryBalances.put(b.adoptedCurrencyCode, 0L);

        // Initial abstract population
        b.population.put(PopulationRole.MAYOR, 1);
        b.population.put(PopulationRole.FARMER, 1);
        b.population.put(PopulationRole.WOODSMAN, 1);
        b.population.put(PopulationRole.LABORER, 1);
        b.population.put(PopulationRole.MERCHANT, 1);

        // Starter claims
        b.claims.addAll(starterClaims);

        // Food defaults
        b.baseFoodCapacity = 0.0;
        b.lastFoodPoints = 0.0;
        b.lastScanEpochSeconds = 0L;

        return b;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public UUID getLeader() { return leader; }
    public Set<UUID> getMembers() { return members; }

    public UUID getWorldId() { return worldId; }
    public Location getHome() { return home; }

    public PolityStage getPolityStage() { return polityStage; }
    public String getRulerTitle() { return rulerTitle; }

    public String getAdoptedCurrencyCode() { return adoptedCurrencyCode; }
    public Map<String, Long> getTreasuryBalances() { return treasuryBalances; }

    public EnumMap<PopulationRole, Integer> getPopulation() { return population; }
    public int getTotalPopulation() {
        return population.values().stream().mapToInt(Integer::intValue).sum();
    }

    public double getBaseFoodCapacity() { return baseFoodCapacity; }
    public void setBaseFoodCapacity(double baseFoodCapacity) { this.baseFoodCapacity = baseFoodCapacity; }

    public double getLastFoodPoints() { return lastFoodPoints; }
    public void setLastFoodPoints(double lastFoodPoints) { this.lastFoodPoints = lastFoodPoints; }

    public long getLastScanEpochSeconds() { return lastScanEpochSeconds; }
    public void setLastScanEpochSeconds(long lastScanEpochSeconds) { this.lastScanEpochSeconds = lastScanEpochSeconds; }

    public Set<ChunkClaim> getClaims() { return claims; }
    public int getClaimCount() { return claims.size(); }

    public void setName(String name) { this.name = name; }
    public void setLeader(UUID leader) { this.leader = leader; }

    public boolean isMember(UUID uuid) { return members.contains(uuid); }
    public void addMember(UUID uuid) { members.add(uuid); }
    public void removeMember(UUID uuid) { members.remove(uuid); }

    public boolean addClaim(ChunkClaim claim) {
        return claims.add(claim);
    }

    public boolean removeClaim(ChunkClaim claim) {
        return claims.remove(claim);
    }

    public boolean hasClaim(ChunkClaim claim) {
        return claims.contains(claim);
    }
}
