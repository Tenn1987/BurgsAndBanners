package com.brandon.burgsbanners.mint;

import com.brandon.burgsbanners.BurgsAndBannersPlugin;
import com.brandon.burgsbanners.burg.Burg;
import com.brandon.burgsbanners.burg.BurgManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;

import java.util.Locale;

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

        Block anvil = event.getClickedBlock();

        // Allow all anvil types
        if (!isAnvil(anvil.getType())) return;

        // Require sign
        if (!hasCoinsmithSign(anvil)) return;

        Player player = event.getPlayer();
        Burg burg = burgManager.getBurgAt(anvil.getLocation());

        if (burg == null) {
            player.sendMessage("§cNo mint authority here.");
            return;
        }

        event.setCancelled(true);

        // Bind burg context without using deprecated Metadata API
        CoinsmithGUIListener.bind(player.getUniqueId(), burg);

        Inventory inv = Bukkit.createInventory(
                null,
                27,
                Component.text("Coinsmith - " + burg.getName())
        );

        CoinsmithGUIListener.populate(inv);
        player.openInventory(inv);
    }

    private boolean isAnvil(Material mat) {
        return mat == Material.ANVIL
                || mat == Material.CHIPPED_ANVIL
                || mat == Material.DAMAGED_ANVIL;
    }

    private boolean hasCoinsmithSign(Block anvil) {
        // Check all 4 sides + above
        Block[] candidates = new Block[] {
                anvil.getRelative(1, 0, 0),
                anvil.getRelative(-1, 0, 0),
                anvil.getRelative(0, 0, 1),
                anvil.getRelative(0, 0, -1),
                anvil.getRelative(0, 1, 0)
        };

        for (Block b : candidates) {
            if (!Tag.SIGNS.isTagged(b.getType())) continue;
            if (!(b.getState() instanceof Sign sign)) continue;

            for (int i = 0; i < 4; i++) {
                String line = strip(sign.getLine(i));
                if (line.contains("COINSMITH")) return true;
            }
        }
        return false;
    }

    private String strip(String s) {
        if (s == null) return "";
        return org.bukkit.ChatColor.stripColor(s)
                .trim()
                .toUpperCase(Locale.ROOT);
    }
}
