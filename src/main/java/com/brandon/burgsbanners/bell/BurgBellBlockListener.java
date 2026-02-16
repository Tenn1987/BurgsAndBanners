package com.brandon.burgsbanners.bell;

import com.brandon.burgsbanners.burg.Burg;
import com.brandon.burgsbanners.burg.BurgManager;
import com.brandon.burgsbanners.mpc.MpcHook;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class BurgBellBlockListener implements Listener {

    private final JavaPlugin plugin;
    private final BurgManager burgManager;
    private final MpcHook mpc;

    public BurgBellBlockListener(JavaPlugin plugin, BurgManager burgManager, MpcHook mpc) {
        this.plugin = plugin;
        this.burgManager = burgManager;
        this.mpc = mpc;
    }

    @EventHandler
    public void onBellClick(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;

        Block b = e.getClickedBlock();
        if (b.getType() != Material.BELL) return;

        Player p = e.getPlayer();

        Burg burg = burgManager.getBurgAt(b.getLocation());
        if (burg == null) return;

        if (mpc == null || !mpc.isHooked()) return;

        e.setCancelled(true);
        BurgBellUI.openMain(plugin, p, burg, burgManager, mpc);
    }
}
