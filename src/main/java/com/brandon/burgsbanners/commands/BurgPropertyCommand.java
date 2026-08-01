package com.brandon.burgsbanners.commands;

import com.brandon.burgsbanners.burg.Burg;
import com.brandon.burgsbanners.burg.BurgManager;
import com.brandon.burgsbanners.burg.plot.Plot;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class BurgPropertyCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final BurgManager burgManager;

    public BurgPropertyCommand(JavaPlugin plugin, BurgManager burgManager) {
        this.plugin = plugin;
        this.burgManager = burgManager;
    }

    private boolean mayorOrOp(Burg burg, Player player) {
        if (player.isOp()) return true;
        return burg != null
                && burg.getLeaderUuid() != null
                && player.getUniqueId().equals(burg.getLeaderUuid());
    }

    private boolean owns(Plot plot, Player player) {
        return plot != null
                && plot.getOwnerUuid() != null
                && plot.getOwnerUuid().equals(player.getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        Burg burg = burgManager.getBurgAt(player.getLocation());
        if (burg == null) {
            player.sendMessage("You must be standing inside a burg.");
            return true;
        }

        String action = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);

        return switch (action) {
            case "list" -> listProperties(player, burg, false);
            case "my" -> listProperties(player, burg, true);
            case "sell" -> listForSale(player, burg, label, args);
            case "withdraw", "unlist" -> withdrawListing(player, burg, label, args);
            default -> {
                sendUsage(player, label);
                yield true;
            }
        };
    }

    private boolean listProperties(Player player, Burg burg, boolean mineOnly) {
        List<Plot> plots = burg.getPlots().values().stream()
                .filter(plot -> !mineOnly || player.getUniqueId().equals(plot.getOwnerUuid()))
                .sorted((a, b) -> a.getId().compareToIgnoreCase(b.getId()))
                .toList();

        player.sendMessage((mineOnly ? "Your Properties in " : "Property Registry: ") + burg.getName());

        if (plots.isEmpty()) {
            player.sendMessage(mineOnly ? "You do not own any property here." : "No properties are registered.");
            return true;
        }

        for (Plot plot : plots) {
            String ownership;
            if (plot.getOwnerUuid() == null) {
                ownership = "Municipal";
            } else if (plot.getOwnerUuid().equals(player.getUniqueId())) {
                ownership = "Yours";
            } else {
                ownership = "Private: " + ownerName(plot.getOwnerUuid());
            }

            String market = plot.isForSale()
                    ? " | FOR SALE " + plot.getSalePrice() + " " + plot.getSaleCurrencyCode()
                    : "";

            String lien = plot.hasLien() ? " | LIEN" : "";
            player.sendMessage(plot.getId() + " (" + plot.getName() + ") - " + ownership + market + lien);
        }
        return true;
    }

    private boolean listForSale(Player player, Burg burg, String label, String[] args) {
        if (args.length < 3) {
            player.sendMessage("Usage: /" + label + " sell <plotId> <price>");
            return true;
        }

        Plot plot = burg.getPlot(args[1]);
        if (plot == null) {
            player.sendMessage("Unknown property: " + args[1]);
            return true;
        }

        boolean municipal = plot.getOwnerUuid() == null;
        boolean authorized = municipal ? mayorOrOp(burg, player) : owns(plot, player) || player.isOp();
        if (!authorized) {
            player.sendMessage(municipal
                    ? "Only the mayor can list municipal property."
                    : "Only the property owner can list this property.");
            return true;
        }

        if (plot.hasLien()) {
            player.sendMessage("This property has an active lien and cannot be listed.");
            return true;
        }

        long price;
        try {
            price = Long.parseLong(args[2]);
        } catch (NumberFormatException ex) {
            player.sendMessage("Price must be a whole number.");
            return true;
        }

        if (price <= 0L) {
            player.sendMessage("Price must be greater than zero.");
            return true;
        }

        String currency = burg.getAdoptedCurrencyCode();
        plot.setSalePrice(price);
        plot.setSaleCurrencyCode(currency);
        plot.setForSale(true);
        burgManager.save(burg);

        player.sendMessage("Listed " + plot.getName() + " [" + plot.getId() + "] for "
                + price + " " + currency + ".");
        if (!municipal && burg.getSalesTaxRate() > 0.0) {
            player.sendMessage("A " + String.format("%.1f%%", burg.getSalesTaxRate() * 100.0)
                    + " burg transfer tax will be withheld when sold.");
        }
        return true;
    }

    private boolean withdrawListing(Player player, Burg burg, String label, String[] args) {
        if (args.length < 2) {
            player.sendMessage("Usage: /" + label + " withdraw <plotId>");
            return true;
        }

        Plot plot = burg.getPlot(args[1]);
        if (plot == null) {
            player.sendMessage("Unknown property: " + args[1]);
            return true;
        }

        boolean municipal = plot.getOwnerUuid() == null;
        boolean authorized = municipal ? mayorOrOp(burg, player) : owns(plot, player) || player.isOp();
        if (!authorized) {
            player.sendMessage(municipal
                    ? "Only the mayor can withdraw municipal property."
                    : "Only the property owner can withdraw this listing.");
            return true;
        }

        if (!plot.isForSale()) {
            player.sendMessage("That property is not currently listed.");
            return true;
        }

        plot.setForSale(false);
        burgManager.save(burg);
        player.sendMessage("Removed " + plot.getName() + " [" + plot.getId() + "] from the market.");
        return true;
    }

    private String ownerName(UUID ownerUuid) {
        OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerUuid);
        return owner.getName() == null ? ownerUuid.toString().substring(0, 8) : owner.getName();
    }

    private void sendUsage(Player player, String label) {
        player.sendMessage("Property commands:");
        player.sendMessage("/" + label + " list");
        player.sendMessage("/" + label + " my");
        player.sendMessage("/" + label + " sell <plotId> <price>");
        player.sendMessage("/" + label + " withdraw <plotId>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("list", "my", "sell", "withdraw").stream()
                    .filter(value -> value.startsWith(prefix))
                    .toList();
        }

        if (args.length == 2 && sender instanceof Player player) {
            Burg burg = burgManager.getBurgAt(player.getLocation());
            if (burg == null) return Collections.emptyList();

            String action = args[0].toLowerCase(Locale.ROOT);
            String prefix = args[1].toLowerCase(Locale.ROOT);

            return burg.getPlots().values().stream()
                    .filter(plot -> {
                        if (action.equals("sell") || action.equals("withdraw") || action.equals("unlist")) {
                            if (plot.getOwnerUuid() == null) return mayorOrOp(burg, player);
                            return owns(plot, player) || player.isOp();
                        }
                        return true;
                    })
                    .map(Plot::getId)
                    .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted()
                    .toList();
        }

        return Collections.emptyList();
    }
}