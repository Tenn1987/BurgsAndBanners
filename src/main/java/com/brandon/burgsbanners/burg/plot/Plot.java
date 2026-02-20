package com.brandon.burgsbanners.burg.plot;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public class Plot {

    // At least 64 vertical blocks of protection (we use +/-32 inclusive = 65 blocks)
    private static final int VERTICAL_HALF_SPAN = 32;

    // Permanent identity (bank collateral key)
    private final UUID plotUuid;

    // Command/UI id (e.g. "market")
    private final String id;

    // Display name (e.g. "Grand Market")
    private String name;

    private final UUID worldId;

    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;

    // Ownership + collateral hooks
    private UUID ownerUuid;       // Player who owns/builds here (set by mayor)
    private UUID lienHolderUuid;  // Bank/burg holding lien (future)

    public Plot(UUID plotUuid,
                String id,
                String name,
                UUID worldId,
                int minX, int minY, int minZ,
                int maxX, int maxY, int maxZ) {

        this.plotUuid = plotUuid;
        this.id = id;
        this.name = name;
        this.worldId = worldId;

        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;

        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public UUID getPlotUuid() { return plotUuid; }
    public String getId() { return id; }
    public String getName() { return name; }
    public UUID getWorldId() { return worldId; }

    public int getMinX() { return minX; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxZ() { return maxZ; }

    /**
     * IMPORTANT:
     * These return the EFFECTIVE Y range (expanded to at least 64 blocks tall).
     * This keeps BurgCommand overlap checks consistent with protection behavior.
     */
    public int getMinY() { return effectiveMinY(); }
    public int getMaxY() { return effectiveMaxY(); }

    public void setName(String name) { this.name = name; }

    public UUID getOwnerUuid() { return ownerUuid; }
    public void setOwnerUuid(UUID ownerUuid) { this.ownerUuid = ownerUuid; }

    public UUID getLienHolderUuid() { return lienHolderUuid; }
    public void setLienHolderUuid(UUID lienHolderUuid) { this.lienHolderUuid = lienHolderUuid; }

    public boolean hasLien() { return lienHolderUuid != null; }

    /**
     * True if the location is inside the plot.
     * X/Z uses stored bounds.
     * Y is expanded to at least 64 blocks tall (actually 65 with +/-32).
     */
    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getUID().equals(worldId)) return false;

        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        if (x < minX || x > maxX) return false;
        if (z < minZ || z > maxZ) return false;

        int eMinY = effectiveMinY();
        int eMaxY = effectiveMaxY();

        return y >= eMinY && y <= eMaxY;
    }

    /**
     * Expands legacy 1-block-tall plots into a vertical column
     * centered around the stored Y range midpoint.
     */
    private int effectiveMinY() {
        int rawMin = Math.min(minY, maxY);
        int rawMax = Math.max(minY, maxY);

        int rawHeight = (rawMax - rawMin) + 1;
        int min = rawMin;

        if (rawHeight < 64) {
            int mid = (rawMin + rawMax) / 2;
            min = mid - VERTICAL_HALF_SPAN;
        }

        World w = getWorld();
        if (w != null) {
            min = Math.max(min, w.getMinHeight());
        }

        return min;
    }

    private int effectiveMaxY() {
        int rawMin = Math.min(minY, maxY);
        int rawMax = Math.max(minY, maxY);

        int rawHeight = (rawMax - rawMin) + 1;
        int max = rawMax;

        if (rawHeight < 64) {
            int mid = (rawMin + rawMax) / 2;
            max = mid + VERTICAL_HALF_SPAN;
        }

        World w = getWorld();
        if (w != null) {
            // getMaxHeight() is exclusive top; highest blockY is maxHeight - 1
            max = Math.min(max, w.getMaxHeight() - 1);
        }

        return max;
    }

    public World getWorld() {
        return Bukkit.getWorld(worldId);
    }
}