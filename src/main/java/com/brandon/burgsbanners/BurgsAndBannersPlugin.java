package com.brandon.burgsbanners;

import com.brandon.burgsbanners.burg.BurgManager;
import com.brandon.burgsbanners.burg.storage.BurgStorage;
import com.brandon.burgsbanners.commands.BurgCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class BurgsAndBannersPlugin extends JavaPlugin {

    private BurgStorage burgStorage;
    private BurgManager burgManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.burgStorage = new BurgStorage(this);
        this.burgManager = new BurgManager(this, burgStorage);

        // load burgs from burgs.yml
        burgManager.loadAll();

        BurgCommand burgCommand = new BurgCommand(burgManager);

        if (getCommand("burg") != null) {
            getCommand("burg").setExecutor(burgCommand);
            getCommand("burg").setTabCompleter(burgCommand);
        } else {
            getLogger().severe("Command 'burg' not found in plugin.yml!");
        }

        getLogger().info("Burgs & Banners enabled.");
    }

    @Override
    public void onDisable() {
        // Save all burgs on shutdown
        if (burgManager != null) {
            burgManager.saveAll();
        }
    }
}
