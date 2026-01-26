package com.brandon.burgsbanners.burg.plot;

import org.bukkit.Location;

import java.util.UUID;

public class Plot {
    private final String id;
    private final String name;
    private final UUID worldId;

    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;

    public Plot(String id, String name, UUID worldId,
                int minX, int minY, int minZ,
                int maxX, int maxY, int maxZ) {
        this.id = id;
        this.name = name;
        this.worldId = worldId;
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public UUID getWorldId() { return worldId; }

    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }

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
}
