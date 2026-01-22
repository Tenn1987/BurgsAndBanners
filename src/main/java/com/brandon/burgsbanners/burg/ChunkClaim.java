package com.brandon.burgsbanners.burg;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.Objects;
import java.util.UUID;

public final class ChunkClaim {

    private final UUID worldId;
    private final int chunkX;
    private final int chunkZ;

    public ChunkClaim(UUID worldId, int chunkX, int chunkZ) {
        this.worldId = worldId;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public UUID getWorldId() { return worldId; }
    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }

    public String toKey() {
        // stable string key for maps & YAML: worldUUID:x:z
        return worldId + ":" + chunkX + ":" + chunkZ;
    }

    public static ChunkClaim fromChunk(World world, Chunk chunk) {
        return new ChunkClaim(world.getUID(), chunk.getX(), chunk.getZ());
    }

    public static ChunkClaim fromKey(String key) {
        // format: worldUUID:x:z OR legacy worldName:x:z
        String[] parts = key.split(":");
        if (parts.length != 3) return null;

        String worldPart = parts[0];
        int x = Integer.parseInt(parts[1]);
        int z = Integer.parseInt(parts[2]);

        // Try UUID first
        try {
            UUID worldId = UUID.fromString(worldPart);
            return new ChunkClaim(worldId, x, z);
        } catch (IllegalArgumentException ignored) {
            // Legacy: world name
            World w = Bukkit.getWorld(worldPart);
            if (w == null) return null;
            return new ChunkClaim(w.getUID(), x, z);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChunkClaim that)) return false;
        return chunkX == that.chunkX && chunkZ == that.chunkZ && Objects.equals(worldId, that.worldId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldId, chunkX, chunkZ);
    }

    @Override
    public String toString() {
        return toKey();
    }
}
