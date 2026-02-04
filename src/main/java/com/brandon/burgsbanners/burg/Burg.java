package com.brandon.burgsbanners.burg;

import com.brandon.burgsbanners.burg.plot.Plot;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;

public class Burg {

    private final String id;                // stable key for YAML (use name or uuid-ish)
    private String name;

    private PolityStage polityStage = PolityStage.BURG;

    // ✅ NEW: real institutional treasury identity (used by MPC wallets)
    private UUID treasuryUuid;

    private UUID leaderUuid;                // may be null if orphaned by OP during testing
    private String rulerTitle = "Lord-Mayor";

    private String adoptedCurrencyCode = "SHEKEL";

    private UUID worldId;                   // for home/territory association
    private int homeX, homeY, homeZ;

    private final Set<UUID> members = new HashSet<>();
    private final Set<ChunkClaim> claims = new HashSet<>();

    // balances in whole units (matches BurgStorage + safe for YAML)
    private final Map<String, Long> treasuryBalances = new HashMap<>();

    // abstract population
    private final Map<PopulationRole, Integer> population = new EnumMap<>(PopulationRole.class);

    // food scan stats
    private double baseFoodCapacity = 0.0;
    private double lastFoodPoints = 0.0;
    private long lastScanEpochSeconds = 0L;

    // plots by id
    private final Map<String, Plot> plots = new LinkedHashMap<>();

    public Burg(String id) {
        this.id = id;
    }

    // ---- Factory used by BurgManager ----
    public static Burg createFounding(String id,
                                      String name,
                                      UUID leaderUuid,
                                      World world,
                                      Location home,
                                      String currencyCode,
                                      Set<ChunkClaim> starterClaims) {

        Burg b = new Burg(id);
        b.name = name;
        b.polityStage = PolityStage.BURG;

        // ✅ NEW: institutional treasury gets its own UUID at founding
        b.treasuryUuid = UUID.randomUUID();

        b.leaderUuid = leaderUuid;
        b.rulerTitle = "Lord-Mayor";
        b.adoptedCurrencyCode = currencyCode == null ? "SHEKEL" : currencyCode.toUpperCase(Locale.ROOT);

        b.worldId = world.getUID();
        b.homeX = home.getBlockX();
        b.homeY = home.getBlockY();
        b.homeZ = home.getBlockZ();

        b.members.add(leaderUuid);
        if (starterClaims != null) b.claims.addAll(starterClaims);

        // initial abstract population
        b.population.put(PopulationRole.MAYOR, 1);
        b.population.put(PopulationRole.FARMER, 1);
        b.population.put(PopulationRole.WOODSMAN, 1);
        b.population.put(PopulationRole.LABORER, 1);
        b.population.put(PopulationRole.MERCHANT, 1);

        // treasury starts empty unless founding funding is added by command/manager
        return b;
    }

    // ---- Core getters/setters ----
    public String getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public PolityStage getPolityStage() { return polityStage; }
    public void setPolityStage(PolityStage polityStage) { this.polityStage = polityStage; }

    // ✅ NEW
    public UUID getTreasuryUuid() { return treasuryUuid; }
    public void setTreasuryUuid(UUID treasuryUuid) { this.treasuryUuid = treasuryUuid; }

    public UUID getLeaderUuid() { return leaderUuid; }
    public void setLeaderUuid(UUID leaderUuid) { this.leaderUuid = leaderUuid; }

    public String getRulerTitle() { return rulerTitle; }
    public void setRulerTitle(String rulerTitle) { this.rulerTitle = rulerTitle; }

    public String getAdoptedCurrencyCode() { return adoptedCurrencyCode; }
    public void setAdoptedCurrencyCode(String adoptedCurrencyCode) {
        this.adoptedCurrencyCode = adoptedCurrencyCode == null ? "SHEKEL" : adoptedCurrencyCode.toUpperCase(Locale.ROOT);
    }

    public UUID getWorldId() { return worldId; }
    public void setWorldId(UUID worldId) { this.worldId = worldId; }

    public Location getHome(World world) {
        return new Location(world, homeX + 0.5, homeY, homeZ + 0.5);
    }

    public void setHome(Location home) {
        if (home == null || home.getWorld() == null) return;
        this.worldId = home.getWorld().getUID();
        this.homeX = home.getBlockX();
        this.homeY = home.getBlockY();
        this.homeZ = home.getBlockZ();
    }

    public Set<UUID> getMembers() { return members; }
    public Set<ChunkClaim> getClaims() { return claims; }

    // ---- These two are required by your BurgManager compile errors ----
    public boolean addClaim(ChunkClaim claim) { return claims.add(claim); }
    public boolean removeClaim(ChunkClaim claim) { return claims.remove(claim); }

    public boolean hasClaim(ChunkClaim claim) { return claims.contains(claim); }
    public int getClaimCount() { return claims.size(); }

    public Map<String, Long> getTreasuryBalances() { return treasuryBalances; }

    public long getTreasuryBalance(String code) {
        if (code == null) return 0;
        return treasuryBalances.getOrDefault(code.toUpperCase(Locale.ROOT), 0L);
    }

    public void creditTreasury(String code, long amount) {
        if (amount <= 0 || code == null) return;
        String c = code.toUpperCase(Locale.ROOT);
        treasuryBalances.put(c, getTreasuryBalance(c) + amount);
    }

    public boolean debitTreasury(String code, long amount) {
        if (amount <= 0 || code == null) return false;
        String c = code.toUpperCase(Locale.ROOT);
        long bal = getTreasuryBalance(c);
        if (bal < amount) return false;
        treasuryBalances.put(c, bal - amount);
        return true;
    }

    public Map<PopulationRole, Integer> getPopulation() { return population; }

    public int getTotalPopulation() {
        return population.values().stream().mapToInt(Integer::intValue).sum();
    }

    public double getBaseFoodCapacity() { return baseFoodCapacity; }
    public void setBaseFoodCapacity(double baseFoodCapacity) { this.baseFoodCapacity = baseFoodCapacity; }

    public double getLastFoodPoints() { return lastFoodPoints; }
    public void setLastFoodPoints(double lastFoodPoints) { this.lastFoodPoints = lastFoodPoints; }

    public long getLastScanEpochSeconds() { return lastScanEpochSeconds; }
    public void setLastScanEpochSeconds(long lastScanEpochSeconds) { this.lastScanEpochSeconds = lastScanEpochSeconds; }

    // ---- Plots (used by your BurgCommand compile errors) ----
    public Plot getPlot(String id) {
        if (id == null) return null;
        return plots.get(id.toLowerCase(Locale.ROOT));
    }

    public Map<String, Plot> getPlots() { return plots; }

    public void putPlot(Plot plot) {
        if (plot == null || plot.getId() == null) return;
        plots.put(plot.getId().toLowerCase(Locale.ROOT), plot);
    }
}
