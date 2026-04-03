package com.brandon.burgsbanners.mint;

import com.brandon.burgsbanners.BurgsAndBannersPlugin;
import com.brandon.burgsbanners.burg.Burg;
import com.brandon.burgsbanners.burg.BurgManager;
import com.brandon.multipolarcurrency.MultiPolarCurrencyPlugin;
import com.brandon.multipolarcurrency.economy.currency.Currency;
import com.brandon.multipolarcurrency.economy.currency.CurrencyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;

import java.util.Locale;
import java.util.Optional;

public class CoinsmithAnvilListener implements Listener {

    private final BurgsAndBannersPlugin plugin;
    private final BurgManager burgManager;

    private final MultiPolarCurrencyPlugin mpcPlugin;
    private final CurrencyManager currencyManager;

    public CoinsmithAnvilListener(BurgsAndBannersPlugin plugin,
                                  BurgManager burgManager,
                                  MultiPolarCurrencyPlugin mpcPlugin) {
        this.plugin = plugin;
        this.burgManager = burgManager;
        this.mpcPlugin = mpcPlugin;
        this.currencyManager = (mpcPlugin != null) ? mpcPlugin.getCurrencyManager() : null;
    }

    @EventHandler
    public void onRightClickAnvil(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Block anvil = event.getClickedBlock();

        if (!isAnvil(anvil.getType())) return;
        if (!hasCoinsmithSign(anvil)) return;

        Player player = event.getPlayer();
        Burg burg = burgManager.getBurgAt(anvil.getLocation());

        if (burg == null) {
            player.sendMessage("§cNo mint authority here.");
            return;
        }

        event.setCancelled(true);

        if (currencyManager == null) {
            player.sendMessage("§cCurrency system not available.");
            return;
        }

        String code = burg.getAdoptedCurrencyCode();
        if (code == null || code.isBlank()) {
            player.sendMessage("§cThis burg has no adopted currency.");
            return;
        }

        Optional<Currency> currencyOpt = currencyManager.getCurrency(code.trim().toUpperCase(Locale.ROOT));
        if (currencyOpt.isEmpty()) {
            player.sendMessage("§cCurrency not found: §f" + code);
            return;
        }

        Currency currency = currencyOpt.get();

        CoinsmithGUIListener.bind(player.getUniqueId(), burg);

        Inventory inv = Bukkit.createInventory(
                null,
                27,
                Component.text("Coinsmith - " + burg.getName())
        );

        CoinsmithGUIListener.populate(inv, currency);
        player.openInventory(inv);
    }

    private boolean isAnvil(Material mat) {
        return mat == Material.ANVIL
                || mat == Material.CHIPPED_ANVIL
                || mat == Material.DAMAGED_ANVIL;
    }

    private boolean hasCoinsmithSign(Block anvil) {
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

            if (signContainsCoinsmith(sign, Side.FRONT)) return true;
            if (signContainsCoinsmith(sign, Side.BACK)) return true;
        }

        return false;
    }

    private boolean signContainsCoinsmith(Sign sign, Side side) {
        for (int i = 0; i < 4; i++) {
            String line = PlainTextComponentSerializer.plainText()
                    .serialize(sign.getSide(side).line(i));

            if (normalize(line).contains("COINSMITH")) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String s) {
        if (s == null) return "";
        return s.trim().toUpperCase(Locale.ROOT);
    }
}