package com.brandon.burgsbanners.burg.food;

import com.brandon.burgsbanners.burg.Burg;
import com.brandon.burgsbanners.burg.BurgManager;
import com.brandon.burgsbanners.burg.storage.BurgStorage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class FoodScanScheduler {

    private final JavaPlugin plugin;
    private final BurgManager burgManager;
    private final BurgStorage storage;
    private final FoodScanService scanService;

    private BukkitTask task;

    public FoodScanScheduler(JavaPlugin plugin, BurgManager burgManager, BurgStorage storage, FoodScanService scanService) {
        this.plugin = plugin;
        this.burgManager = burgManager;
        this.storage = storage;
        this.scanService = scanService;
    }

    public void start() {
        long hours = plugin.getConfig().getLong("foodScan.rescanHours", 3);
        long periodTicks = hours * 60L * 60L * 20L;

        this.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Burg b : storage.loadAllBurgs().values()) {
                World w = Bukkit.getWorld(b.getWorldId());
                if (w == null) continue;

                FoodScanService.ScanResult r = scanService.scanBurgClaims(b, w);
                b.setBaseFoodCapacity(r.baseFoodCapacity());
                b.setLastFoodPoints(r.totalFoodPoints());
                b.setLastScanEpochSeconds(System.currentTimeMillis() / 1000L);
                storage.saveBurg(b);
            }
            plugin.getLogger().info("Food rescan complete.");
        }, periodTicks, periodTicks);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }
}
