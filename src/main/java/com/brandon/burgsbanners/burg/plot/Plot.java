package com.brandon.burgsbanners.burg.plot;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public class Plot {

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
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }

    public void setName(String name) { this.name = name; }

    public UUID getOwnerUuid() { return ownerUuid; }
    public void setOwnerUuid(UUID ownerUuid) { this.ownerUuid = ownerUuid; }

    public UUID getLienHolderUuid() { return lienHolderUuid; }
    public void setLienHolderUuid(UUID lienHolderUuid) { this.lienHolderUuid = lienHolderUuid; }

    public boolean hasLien() { return lienHolderUuid != null; }

    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getUID().equals(worldId)) return false;

        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public World getWorld() {
        return Bukkit.getWorld(worldId);
    }
}
