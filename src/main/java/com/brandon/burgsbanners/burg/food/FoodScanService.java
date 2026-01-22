package com.brandon.burgsbanners.burg.food;

import com.brandon.burgsbanners.burg.Burg;
import com.brandon.burgsbanners.burg.ChunkClaim;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class FoodScanService {

    public record ScanResult(double totalFoodPoints, double baseFoodCapacity) {}

    private final JavaPlugin plugin;

    // Weights (locked gameplay numbers)
    private static final double FP_WOODS = 0.05;
    private static final double FP_GRASS = 0.10;
    private static final double FP_FARMLAND = 0.50;
    private static final double FP_CROP = 1.00;
    private static final double FP_IRRIGATION_BONUS = 1.00;
    private static final double FP_FISH_WATER = 0.20;

    public FoodScanService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public ScanResult scanBurgClaims(Burg burg, World world) {
        int step = plugin.getConfig().getInt("foodScan.sampleStep", 4);
        int fishBodyThreshold = plugin.getConfig().getInt("foodScan.fishBodyThreshold", 20);
        int fishNodeCap = plugin.getConfig().getInt("foodScan.fishBfsNodeCap", 256);

        double fp = 0.0;

        Set<Long> visitedWater = new HashSet<>();

        for (ChunkClaim claim : burg.getClaims()) {
            if (!claim.getWorldId().equals(world.getUID())) continue;

            int baseX = claim.getChunkX() << 4;
            int baseZ = claim.getChunkZ() << 4;

            for (int dx = 0; dx < 16; dx += step) {
                for (int dz = 0; dz < 16; dz += step) {
                    int x = baseX + dx;
                    int z = baseZ + dz;

                    Block top = world.getHighestBlockAt(x, z);
                    Material m = top.getType();

                    // fishable surface water
                    if (m == Material.WATER && isOpenToSkyWater(top)) {
                        long key = packKey(top.getX(), top.getY(), top.getZ());
                        if (!visitedWater.contains(key)) {
                            int size = bfsSurfaceWater(top, visitedWater, fishNodeCap);
                            if (size >= fishBodyThreshold) {
                                fp += size * FP_FISH_WATER;
                            }
                        }
                        continue;
                    }

                    // crops
                    if (isCropBlock(top)) {
                        fp += FP_CROP;
                        if (hasWaterNearby(top, 4)) fp += FP_IRRIGATION_BONUS;
                        continue;
                    }

                    if (m == Material.FARMLAND) { fp += FP_FARMLAND; continue; }
                    if (m == Material.GRASS_BLOCK) { fp += FP_GRASS; continue; }

                    if (Tag.LOGS.isTagged(m) || Tag.LEAVES.isTagged(m)) {
                        fp += FP_WOODS;
                    }
                }
            }
        }

        // For now: base food capacity = scanned FP baseline
        return new ScanResult(fp, fp);
    }

    private boolean isCropBlock(Block b) {
        Material m = b.getType();
        if (Tag.CROPS.isTagged(m)) return true;
        return b.getBlockData() instanceof Ageable;
    }

    private boolean hasWaterNearby(Block crop, int radius) {
        World w = crop.getWorld();
        int y = crop.getY();
        int cx = crop.getX();
        int cz = crop.getZ();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (w.getBlockAt(cx + dx, y, cz + dz).getType() == Material.WATER) return true;
            }
        }
        return false;
    }

    private boolean isOpenToSkyWater(Block b) {
        Block top = b.getWorld().getHighestBlockAt(b.getX(), b.getZ());
        return top.getY() == b.getY() && top.getType() == Material.WATER;
    }

    private int bfsSurfaceWater(Block start, Set<Long> visited, int cap) {
        int y = start.getY();
        World w = start.getWorld();

        ArrayDeque<Block> q = new ArrayDeque<>();
        q.add(start);
        visited.add(packKey(start.getX(), y, start.getZ()));

        int count = 0;

        while (!q.isEmpty() && count < cap) {
            Block cur = q.poll();
            count++;

            int x = cur.getX();
            int z = cur.getZ();

            for (int[] d : DIR4) {
                int nx = x + d[0];
                int nz = z + d[1];
                Block nb = w.getBlockAt(nx, y, nz);

                if (nb.getType() != Material.WATER) continue;
                if (!isOpenToSkyWater(nb)) continue;

                long key = packKey(nx, y, nz);
                if (visited.add(key)) q.add(nb);
            }
        }

        return count;
    }

    private static final int[][] DIR4 = new int[][]{
            {1,0},{-1,0},{0,1},{0,-1}
    };

    private long packKey(int x, int y, int z) {
        return (((long) x) & 0x3FFFFFFL) << 38
                | (((long) z) & 0x3FFFFFFL) << 12
                | (((long) y) & 0xFFFL);
    }
}
