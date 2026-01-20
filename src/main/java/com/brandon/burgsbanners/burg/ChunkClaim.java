package com.brandon.burgsbanners.burg;

import java.util.Objects;

public final class ChunkClaim {

    private final String worldName;
    private final int chunkX;
    private final int chunkZ;

    public ChunkClaim(String worldName, int chunkX, int chunkZ) {
        this.worldName = worldName;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public String getWorldName() { return worldName; }
    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }

    public String toKey() {
        // stable string key for maps & YAML
        return worldName + ":" + chunkX + ":" + chunkZ;
    }

    public static ChunkClaim fromKey(String key) {
        // format: world:x:z
        String[] parts = key.split(":");
        if (parts.length != 3) return null;
        String world = parts[0];
        int x = Integer.parseInt(parts[1]);
        int z = Integer.parseInt(parts[2]);
        return new ChunkClaim(world, x, z);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChunkClaim that)) return false;
        return chunkX == that.chunkX && chunkZ == that.chunkZ && Objects.equals(worldName, that.worldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldName, chunkX, chunkZ);
    }

    @Override
    public String toString() {
        return toKey();
    }
}
