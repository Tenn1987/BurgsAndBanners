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
                long issuerShare = cost / 2;
                long seedShare = cost - issuerShare;

                String cur = burg.getAdoptedCurrencyCode();

                if (mpc.getBalance(p, cur) < cost) {
                    p.sendMessage(Component.text("You need " + cost + " " + cur));
                    return;
                }

                // withdraw from player
                mpc.withdraw(p, cur, cost);

                // issuer gets 500
                UUID treasuryId = burg.getTreasuryUuid();
                mpc.touch(treasuryId, cur);
                mpc.deposit(treasuryId, cur, issuerShare);

                // charter holds seed 500
                ItemStack charter = BurgBellUI.createCharterBell(plugin, burg, seedShare);
                p.getInventory().addItem(charter);

                p.sendMessage(Component.text("Purchased a Burg Charter from " + burg.getName()
                        + " for " + cost + " " + cur + ". (" + issuerShare + " issuer / " + seedShare + " seed)"));
            }

            case "OPEN_SETTINGS" -> {
                if (!isMayorOrOp(p, burg)) {
                    p.sendMessage(Component.text("Only the burg mayor can set taxes."));
                    return;
                }
                BurgBellUI.openSalesMenu(plugin, p, burg);
            }

            case "BACK_MAIN" -> BurgBellUI.openMain(plugin, p, burg, burgManager, mpc);

            case "SET_SALES" -> {
                if (!isMayorOrOp(p, burg)) {
                    p.sendMessage(Component.text("Only the burg mayor can set taxes."));
                    return;
                }

                Double rate = BurgBellUI.getRate(plugin, clicked);
                if (rate == null) return;

                double clamped = clamp(rate, 0.0, Burg.MAX_SALES_TAX);
                burg.setSalesTaxRate(clamped);
                burgManager.save(burg);

                p.sendMessage(Component.text("Sales tax set to " + fmtPct(clamped) + " for " + burg.getName() + "."));
                BurgBellUI.openMcFeeMenu(plugin, p, burg);
            }

            case "SET_MCFEE" -> {
                if (!isMayorOrOp(p, burg)) {
                    p.sendMessage(Component.text("Only the burg mayor can set taxes."));
                    return;
                }

                Double rate = BurgBellUI.getRate(plugin, clicked);
                if (rate == null) return;

                double clamped = clamp(rate, 0.0, Burg.MAX_MONEYCHANGER_FEE);
                burg.setMoneychangerFeeRate(clamped);
                burgManager.save(burg);

                p.sendMessage(Component.text("Moneychanger fee set to " + fmtPct(clamped) + " for " + burg.getName() + "."));
                BurgBellUI.openMain(plugin, p, burg, burgManager, mpc);
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