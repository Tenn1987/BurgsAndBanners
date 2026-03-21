package com.brandon.burgsbanners.bell;

import com.brandon.burgsbanners.burg.Burg;
import com.brandon.burgsbanners.burg.BurgManager;
import com.brandon.burgsbanners.mpc.MpcHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class BurgBellGUIListener implements Listener {

    private final JavaPlugin plugin;
    private final BurgManager burgManager;
    private final MpcHook mpc;

    public BurgBellGUIListener(JavaPlugin plugin, BurgManager burgManager, MpcHook mpc) {
        this.plugin = plugin;
        this.burgManager = burgManager;
        this.mpc = mpc;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        String title = PlainTextComponentSerializer.plainText().serialize(e.getView().title());
        if (!(title.startsWith("Burg Bell - ")
                || title.startsWith("Sales Tax - ")
                || title.startsWith("Moneychanger Fee - "))) {
            return;
        }

        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null) return;

        Burg burg = burgManager.getBurgAt(p.getLocation());
        if (burg == null) return;

        String action = BurgBellUI.getAction(plugin, clicked);
        if (action == null) return;

        switch (action) {
            case "NOOP" -> { }

            case "VIEW_TAX" -> {
                p.sendMessage(Component.text("Taxes for " + burg.getName()));
                p.sendMessage(Component.text("Sales: " + fmtPct(burg.getSalesTaxRate()) + " (max " + fmtPct(Burg.MAX_SALES_TAX) + ")"));
                p.sendMessage(Component.text("Moneychanger: " + fmtPct(burg.getMoneychangerFeeRate()) + " (max " + fmtPct(Burg.MAX_MONEYCHANGER_FEE) + ")"));
            }

            case "BUY_CHARTER" -> {
                if (mpc == null || !mpc.isHooked()) {
                    p.sendMessage(Component.text("MultiPolarCurrency not available."));
                    return;
                }

                long cost = plugin.getConfig().getLong("founding.charterCost", 1000L);
                String cur = burg.getAdoptedCurrencyCode();

                if (mpc.getBalance(p, cur) < cost) {
                    p.sendMessage(Component.text("You need " + cost + " " + cur));
                    return;
                }

                // withdraw from player
                mpc.withdraw(p, cur, cost);

                // all 1000 goes to selling burg treasury
                UUID treasuryId = burg.getTreasuryUuid();
                mpc.touch(treasuryId, cur);
                mpc.deposit(treasuryId, cur, cost);

                ItemStack charter = BurgBellUI.createCharterBell(plugin, burg);
                p.getInventory().addItem(charter);

                p.sendMessage(Component.text("Purchased a Burg Charter from " + burg.getName()
                        + " for " + cost + " " + cur + "."));
            }

            case "OPEN_SETTINGS" -> {
                if (!isMayorOrOp(p, burg)) {
                    p.sendMessage(Component.text("Only the burg mayor can set taxes."));
                    return;
                }
                BurgBellUI.openSalesMenu(plugin, p, burg);
            }

            case "BACK_MAIN" -> BurgBellUI.openMain(plugin, p, burg, burgManager, mpc);

            case "ADJUST_SALES" -> {
                if (!isMayorOrOp(p, burg)) {
                    p.sendMessage(Component.text("Only the burg mayor can set taxes."));
                    return;
                }

                Double delta = BurgBellUI.getDelta(plugin, clicked);
                if (delta == null) return;

                double clamped = clamp(burg.getSalesTaxRate() + delta, 0.0, Burg.MAX_SALES_TAX);
                burg.setSalesTaxRate(clamped);
                burgManager.save(burg);

                p.sendMessage(Component.text("Sales tax set to " + fmtPct(clamped) + " for " + burg.getName() + "."));
                BurgBellUI.openSalesMenu(plugin, p, burg);
            }

            case "ADJUST_MCFEE" -> {
                if (!isMayorOrOp(p, burg)) {
                    p.sendMessage(Component.text("Only the burg mayor can set taxes."));
                    return;
                }

                Double delta = BurgBellUI.getDelta(plugin, clicked);
                if (delta == null) return;

                double clamped = clamp(burg.getMoneychangerFeeRate() + delta, 0.0, Burg.MAX_MONEYCHANGER_FEE);
                burg.setMoneychangerFeeRate(clamped);
                burgManager.save(burg);

                p.sendMessage(Component.text("Moneychanger fee set to " + fmtPct(clamped) + " for " + burg.getName() + "."));
                BurgBellUI.openMcFeeMenu(plugin, p, burg);
            }
        }
    }

    private static boolean isMayorOrOp(Player p, Burg burg) {
        if (p.isOp()) return true;
        UUID leader = burg.getLeaderUuid();
        return leader != null && leader.equals(p.getUniqueId());
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String fmtPct(double rate) {
        return String.format("%.1f%%", rate * 100.0);
    }
}