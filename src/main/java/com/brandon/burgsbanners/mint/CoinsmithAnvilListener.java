package com.brandon.burgsbanners.mint;

import com.brandon.burgsbanners.burg.Burg;
import com.brandon.burgsbanners.burg.BurgManager;
import com.brandon.burgsbanners.BurgsAndBannersPlugin;
import com.brandon.multipolarcurrency.economy.currency.BackingType;
import com.brandon.multipolarcurrency.economy.currency.Currency;
import com.brandon.multipolarcurrency.economy.currency.PhysicalCurrencyFactory;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.entity.Player;

public class CoinsmithAnvilListener implements Listener {

    private final BurgsAndBannersPlugin plugin;
    private final BurgManager burgManager;

    public CoinsmithAnvilListener(BurgsAndBannersPlugin plugin, BurgManager burgManager) {
        this.plugin = plugin;
        this.burgManager = burgManager;
    }

    @EventHandler
    public void onRightClickAnvil(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != Material.ANVIL) return;

        Player player = event.getPlayer();
        Burg burg = burgManager.getBurgAt(event.getClickedBlock().getLocation());

        if (burg == null) {
            player.sendMessage("§cNo mint authority here.");
            return;
        }

        event.setCancelled(true);

        Inventory inv = Bukkit.createInventory(null, 27, CoinsmithGUIListener.GUI_PREFIX + " - " + burg.getName());
        CoinsmithGUIListener.populate(inv);

        // store Burg object so the GUI listener can mint into THIS burg treasury
        player.setMetadata(CoinsmithGUIListener.META_BURG, new FixedMetadataValue(plugin, burg));

        player.openInventory(inv);
    }
}
