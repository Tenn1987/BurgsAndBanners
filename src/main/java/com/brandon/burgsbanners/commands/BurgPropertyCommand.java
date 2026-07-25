package com.brandon.burgsbanners.commands;

import com.brandon.burgsbanners.burg.Burg;
import com.brandon.burgsbanners.burg.BurgManager;
import com.brandon.burgsbanners.burg.plot.Plot;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;

public class BurgPropertyCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final BurgManager burgManager;

    public BurgPropertyCommand(JavaPlugin plugin, BurgManager burgManager) {
        this.plugin = plugin;
        this.burgManager = burgManager;
    }

    private boolean mayor(Burg b, Player p) {
        return b != null && p.getUniqueId().equals(b.getLeaderUuid());
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        Burg burg = burgManager.getBurgAt(player.getLocation());
        if (burg == null) {
            player.sendMessage("You are not in a burg.");
            return true;
        }

        player.sendMessage(
                "DEBUG Burg=" + burg.getName()
        );

        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            player.sendMessage("Property Registry: " + burg.getName());
            for (Plot plot : burg.getPlots().values()) {
                String state = plot.isForSale()
                        ? "FOR SALE " + plot.getSalePrice() + " " + plot.getSaleCurrencyCode()
                        : (plot.getOwnerUuid() == null ? "Municipal" : "Private");
                player.sendMessage(plot.getId() + " (" + plot.getName() + ") - " + state);
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("sell")) {
            if (!mayor(burg, player)) return true;
            if (args.length < 3) return true;

            Plot plot = burg.getPlot(args[1]);
            if (plot == null) return true;

            long price;
            try {
                price = Long.parseLong(args[2]);
            } catch (NumberFormatException e) {
                return true;
            }

            plot.setSalePrice(price);
            plot.setSaleCurrencyCode(burg.getAdoptedCurrencyCode());
            plot.setForSale(true);
            burgManager.save(burg);

            player.sendMessage("Listed " + plot.getName() + " for " + price + " " + burg.getAdoptedCurrencyCode());
            return true;
        }

        if (args[0].equalsIgnoreCase("withdraw")) {
            if (!mayor(burg, player)) return true;
            if (args.length < 2) return true;

            Plot plot = burg.getPlot(args[1]);
            if (plot == null) return true;

            plot.setForSale(false);
            burgManager.save(burg);
            player.sendMessage("Removed " + plot.getName() + " from market.");
            return true;
        }

        return true;
    }

    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) return List.of("list", "sell", "withdraw");

        if (args.length == 2 && sender instanceof Player p) {
            Burg b = burgManager.getBurgAt(p.getLocation());
            if (b != null) return b.getPlots().keySet().stream().sorted().toList();
        }

        return Collections.emptyList();
    }
}