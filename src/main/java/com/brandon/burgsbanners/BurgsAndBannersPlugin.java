package com.brandon.burgsbanners;

import com.brandon.burgsbanners.burg.BurgManager;
import com.brandon.burgsbanners.burg.food.FoodScanService;
import com.brandon.burgsbanners.burg.food.FoodScanScheduler;
import com.brandon.burgsbanners.burg.storage.BurgStorage;
import com.brandon.burgsbanners.commands.BurgCommand;
import com.brandon.burgsbanners.dynmap.DynmapHook;
import com.brandon.burgsbanners.listeners.BurgTerritoryListener;
import com.brandon.burgsbanners.mpc.MpcHook;
import com.brandon.burgsbanners.mpc.MultiPolarCurrencyHook;
import org.bukkit.plugin.java.JavaPlugin;

public final class BurgsAndBannersPlugin extends JavaPlugin {

    private BurgStorage burgStorage;
    private BurgManager burgManager;

    private MpcHook mpcHook;
    private FoodScanService foodScanService;
    private FoodScanScheduler foodScanScheduler;
    private DynmapHook dynmapHook;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.burgStorage = new BurgStorage(this);
        this.burgManager = new BurgManager(this, burgStorage);

        // Load burgs from burgs.yml
        burgManager.loadAll();

        // MPC hook (MultiPolarCurrency)
        this.mpcHook = new MultiPolarCurrencyHook(getLogger());

        // Food scan
        this.foodScanService = new FoodScanService(this);

        // Schedule global rescan every 3 hours (configurable)
        this.foodScanScheduler = new FoodScanScheduler(this, burgManager, burgStorage, foodScanService);
        this.foodScanScheduler.start();

        BurgCommand burgCommand = new BurgCommand(this, burgManager, mpcHook, foodScanService);

        if (getCommand("burg") != null) {
            getCommand("burg").setExecutor(burgCommand);
            getCommand("burg").setTabCompleter(burgCommand);
        } else {
            getLogger().severe("Command 'burg' not found in plugin.yml!");
        }

        // Optional alias
        if (getCommand("city") != null) {
            getCommand("city").setExecutor(burgCommand);
            getCommand("city").setTabCompleter(burgCommand);
        }

        getLogger().info("Burgs & Banners enabled.");

        getServer().getPluginManager().registerEvents(
                new BurgTerritoryListener(burgManager),
                this
        );

        dynmapHook = new DynmapHook(this, burgManager);
        dynmapHook.hook();
    }

    // ✅ THIS MUST BE OUTSIDE onEnable()
    public BurgManager getBurgManager() {
        return burgManager;
    }

    @Override
    public void onDisable() {
        if (foodScanScheduler != null) {
            foodScanScheduler.stop();
        }
        if (burgManager != null) {
            burgManager.saveAll();
        }

        if (this.dynmapHook != null) this.dynmapHook.shutdown();
    }
}
