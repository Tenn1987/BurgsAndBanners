package com.brandon.burgsbanners.burg.plot;

import org.bukkit.Location;

public class Plot {

    private final String worldName;
    private final int centerX;
    private final int centerY;
    private final int centerZ;
    private final int radius;

    public Plot(String worldName, int centerX, int centerY, int centerZ, int radius) {
        this.worldName = worldName;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.radius = radius;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getCenterX() {
        return centerX;
    }

    public int getCenterY() {
        return centerY;
    }

    public int getCenterZ() {
        return centerZ;
    }

    public int getRadius() {
        return radius;
    }

    /**
     * Checks if a location is inside this plot.
     * Horizontal: square radius
     * Vertical: 64 blocks total (32 down, 32 up from centerY)
     */
    public boolean contains(Location loc) {

        if (loc == null || loc.getWorld() == null) return false;

        if (!loc.getWorld().getName().equalsIgnoreCase(worldName)) {
            return false;
        }

        int dx = Math.abs(loc.getBlockX() - centerX);
        int dz = Math.abs(loc.getBlockZ() - centerZ);

        // Horizontal square protection
        if (dx > radius || dz > radius) {
            return false;
        }

        return dx <= radius && dz <= radius;
    }
}