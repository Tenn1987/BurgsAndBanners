package com.brandon.burgsbanners.commands;

import com.brandon.burgsbanners.burg.Burg;
import com.brandon.burgsbanners.burg.BurgManager;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public class BurgTaxCommand implements CommandExecutor, TabCompleter {

    private final BurgManager burgManager;

    public BurgTaxCommand(BurgManager burgManager) {
        this.burgManager = burgManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        // Must be standing inside burg limits
        Burg burg = burgManager.getBurgAt(p.getLocation());
        if (burg == null) {
            p.sendMessage(text("You must be inside burg limits to use this command.", RED));
            return true;
        }

        if (args.length == 0) {
            return usage(p, label);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("sale") || sub.equals("sales")) {

            if (args.length == 1) {
                double r = burg.getSalesTaxRate();
                p.sendMessage(
                        text("Sales tax for ", GOLD)
                                .append(text(burg.getName(), YELLOW))
                                .append(text(": ", GOLD))
                                .append(text(fmtPct(r), AQUA))
                                .append(text(" (max " + fmtPct(Burg.MAX_SALES_TAX) + ")", GRAY))
                );
                return true;
            }

            if (args.length >= 3 && args[1].equalsIgnoreCase("set")) {

                if (!isMayorOrOp(p, burg)) {
                    p.sendMessage(text("Only the burg mayor can set tax.", RED));
                    return true;
                }

                Double parsed = parsePercent(args[2]);
                if (parsed == null) {
                    p.sendMessage(text("Invalid percent. Example: /" + label + " sale set 7  (7%)", RED));
                    return true;
                }

                double clamped = clamp(parsed, 0.0, Burg.MAX_SALES_TAX);
                burg.setSalesTaxRate(clamped);
                burgManager.save(burg);

                p.sendMessage(
                        text("Sales tax set to ", GREEN)
                                .append(text(fmtPct(clamped), AQUA))
                                .append(text(" for ", GREEN))
                                .append(text(burg.getName(), YELLOW))
                                .append(text(".", GREEN))
                );
                return true;
            }

            return usage(p, label);
        }

        return usage(p, label);
    }

    private boolean usage(Player p, String label) {
        p.sendMessage(text("Usage:", GOLD));
        p.sendMessage(text("/" + label + " sale", YELLOW).append(text("  (view current sales tax)", GRAY)));
        p.sendMessage(text("/" + label + " sale set <percent>", YELLOW)
                .append(text("  (mayor only, max " + fmtPct(Burg.MAX_SALES_TAX) + ")", GRAY)));
        return true;
    }

    private boolean isMayorOrOp(Player p, Burg burg) {
        if (p.isOp()) return true;
        return burg.getLeaderUuid() != null && burg.getLeaderUuid().equals(p.getUniqueId());
    }

    /** Accepts "7" => 0.07, accepts "0.07" => 0.07 */
    private Double parsePercent(String s) {
        try {
            double v = Double.parseDouble(s);
            if (!Double.isFinite(v)) return null;
            if (v > 1.0) v = v / 100.0;
            return v;
        } catch (Exception ignored) {
            return null;
        }
    }

    private double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private String fmtPct(double rate) {
        return String.format(Locale.US, "%.1f%%", rate * 100.0);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("sale");
        if (args.length == 2 && (args[0].equalsIgnoreCase("sale") || args[0].equalsIgnoreCase("sales"))) {
            return List.of("set");
        }
        return List.of();
    }
}
