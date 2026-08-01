package com.brandon.burgsbanners.bell;
import com.brandon.burgsbanners.burg.Burg;
import com.brandon.burgsbanners.burg.BurgManager;
import com.brandon.burgsbanners.burg.plot.Plot;
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
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!(title.startsWith("Burg Bell - ")
                || title.startsWith("Sales Tax - ")
                || title.startsWith("Moneychanger Fee - ")
                || title.startsWith("Plots - "))) {
            return;
        }

        event.setCancelled(true);

        // Ignore clicks in the player's own inventory while a bell GUI is open.
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        Burg burg = burgManager.getBurgAt(player.getLocation());
        if (burg == null) {
            player.sendMessage(Component.text("You are no longer inside this burg."));
            return;
        }

        String action = BurgBellUI.getAction(plugin, clicked);
        if (action == null) return;

        switch (action) {
            case "NOOP" -> { }

            case "OPEN_PLOTS" -> BurgBellUI.openPlotMenu(plugin, player, burg);

            case "BUY_LISTED_PLOT" -> buyListedPlot(player, burg, clicked);

            case "VIEW_TAX" -> {
                player.sendMessage(Component.text("Taxes for " + burg.getName()));
                player.sendMessage(Component.text("Sales: " + fmtPct(burg.getSalesTaxRate())
                        + " (max " + fmtPct(Burg.MAX_SALES_TAX) + ")"));
                player.sendMessage(Component.text("Moneychanger: " + fmtPct(burg.getMoneychangerFeeRate())
                        + " (max " + fmtPct(Burg.MAX_MONEYCHANGER_FEE) + ")"));
            }

            case "BUY_CHARTER" -> buyCharter(player, burg);

            case "OPEN_SETTINGS" -> {
                if (!isMayorOrOp(player, burg)) {
                    player.sendMessage(Component.text("Only the burg mayor can set taxes."));
                    return;
                }
                BurgBellUI.openSalesMenu(plugin, player, burg);
            }

            case "BACK_MAIN" ->
                    BurgBellUI.openMain(plugin, player, burg, burgManager, mpc);

            case "ADJUST_SALES" -> {
                if (!isMayorOrOp(player, burg)) {
                    player.sendMessage(Component.text("Only the burg mayor can set taxes."));
                    return;
                }

                Double delta = BurgBellUI.getDelta(plugin, clicked);
                if (delta == null) return;

                double clamped = clamp(
                        burg.getSalesTaxRate() + delta,
                        0.0,
                        Burg.MAX_SALES_TAX);

                burg.setSalesTaxRate(clamped);
                burgManager.save(burg);

                player.sendMessage(Component.text(
                        "Sales tax set to " + fmtPct(clamped) + " for " + burg.getName() + "."));

                BurgBellUI.openSalesMenu(plugin, player, burg);
            }

            case "ADJUST_MCFEE" -> {
                if (!isMayorOrOp(player, burg)) {
                    player.sendMessage(Component.text("Only the burg mayor can set taxes."));
                    return;
                }

                Double delta = BurgBellUI.getDelta(plugin, clicked);
                if (delta == null) return;

                double clamped = clamp(
                        burg.getMoneychangerFeeRate() + delta,
                        0.0,
                        Burg.MAX_MONEYCHANGER_FEE);

                burg.setMoneychangerFeeRate(clamped);
                burgManager.save(burg);

                player.sendMessage(Component.text(
                        "Moneychanger fee set to " + fmtPct(clamped) + " for " + burg.getName() + "."));

                BurgBellUI.openMcFeeMenu(plugin, player, burg);
            }

            default -> plugin.getLogger().warning("Unhandled Burg Bell GUI action: " + action);
        }
    }

    private void buyListedPlot(Player buyer, Burg burg, ItemStack clicked) {
        if (mpc == null || !mpc.isHooked()) {
            buyer.sendMessage(Component.text("MultiPolarCurrency not available."));
            return;
        }

        String plotId = BurgBellUI.getPlotId(plugin, clicked);
        if (plotId == null || plotId.isBlank()) {
            plugin.getLogger().warning("Plot listing had no bb_plot_id tag.");
            buyer.sendMessage(Component.text("This property listing is invalid."));
            return;
        }

        Plot plot = burg.getPlot(plotId);
        if (plot == null) {
            buyer.sendMessage(Component.text("That property no longer exists."));
            BurgBellUI.openPlotMenu(plugin, buyer, burg);
            return;
        }

        if (!plot.isForSale()) {
            buyer.sendMessage(Component.text("That property is no longer for sale."));
            BurgBellUI.openPlotMenu(plugin, buyer, burg);
            return;
        }

        if (plot.hasLien()) {
            buyer.sendMessage(Component.text("That property has an active lien and cannot be sold."));
            return;
        }

        UUID sellerUuid = plot.getOwnerUuid(); // null means municipal inventory
        if (sellerUuid != null && sellerUuid.equals(buyer.getUniqueId())) {
            buyer.sendMessage(Component.text("You already own this property."));
            return;
        }

        long price = plot.getSalePrice();
        String currency = plot.getSaleCurrencyCode();

        if (price <= 0L) {
            buyer.sendMessage(Component.text("That property has an invalid sale price."));
            return;
        }

        if (currency == null || currency.isBlank()) {
            buyer.sendMessage(Component.text("That property has no valid sale currency."));
            return;
        }

        if (mpc.getBalance(buyer, currency) < price) {
            buyer.sendMessage(Component.text("You need " + price + " " + currency + "."));
            return;
        }

        // Bukkit inventory events execute on the main thread, but re-check the listing
        // and seller immediately before moving money and ownership.
        if (!plot.isForSale() || !java.util.Objects.equals(sellerUuid, plot.getOwnerUuid())) {
            buyer.sendMessage(Component.text("That property listing just changed."));
            BurgBellUI.openPlotMenu(plugin, buyer, burg);
            return;
        }

        long tax = sellerUuid == null ? 0L : calculateTransferTax(price, burg.getSalesTaxRate());
        long sellerProceeds = price - tax;

        if (!mpc.withdraw(buyer, currency, price)) {
            buyer.sendMessage(Component.text("Payment failed; no property was transferred."));
            return;
        }

        boolean paymentCompleted;
        if (sellerUuid == null) {
            mpc.touch(burg.getTreasuryUuid(), currency);
            paymentCompleted = mpc.deposit(burg.getTreasuryUuid(), currency, price);
        } else {
            mpc.touch(sellerUuid, currency);
            mpc.touch(burg.getTreasuryUuid(), currency);

            boolean sellerPaid = sellerProceeds == 0L || mpc.deposit(sellerUuid, currency, sellerProceeds);
            boolean taxPaid = tax == 0L || mpc.deposit(burg.getTreasuryUuid(), currency, tax);
            paymentCompleted = sellerPaid && taxPaid;
        }

        if (!paymentCompleted) {
            // Best-effort refund. Do not transfer title when settlement is incomplete.
            mpc.deposit(buyer, currency, price);
            plugin.getLogger().severe("Property settlement failed for plot " + plot.getId()
                    + "; buyer refund attempted. Check MPC balances for partial deposits.");
            buyer.sendMessage(Component.text("Settlement failed; your payment was refunded where possible."));
            return;
        }

        plot.setOwnerUuid(buyer.getUniqueId());
        plot.setForSale(false);
        burgManager.save(burg);

        buyer.sendMessage(Component.text(
                "Purchased " + plot.getName() + " [" + plot.getId() + "] for "
                        + price + " " + currency + "."));

        if (sellerUuid != null) {
            buyer.sendMessage(Component.text("Transfer tax paid to " + burg.getName() + ": "
                    + tax + " " + currency + "."));

            Player seller = org.bukkit.Bukkit.getPlayer(sellerUuid);
            if (seller != null && seller.isOnline()) {
                seller.sendMessage(Component.text(
                        plot.getName() + " [" + plot.getId() + "] sold for " + price + " " + currency
                                + ". Net proceeds: " + sellerProceeds + " " + currency
                                + " after " + tax + " " + currency + " tax."));
            }
        }

        BurgBellUI.openPlotMenu(plugin, buyer, burg);
    }

    private static long calculateTransferTax(long price, double rate) {
        if (price <= 0L || rate <= 0.0) return 0L;
        double rawTax = price * rate;
        if (!Double.isFinite(rawTax)) return price;
        return Math.max(0L, Math.min(price, Math.round(rawTax)));
    }

    private void buyCharter(Player player, Burg burg) {
        if (mpc == null || !mpc.isHooked()) {
            player.sendMessage(Component.text("MultiPolarCurrency not available."));
            return;
        }

        long cost = plugin.getConfig().getLong("founding.charterCost", 1000L);
        String currency = burg.getAdoptedCurrencyCode();

        if (mpc.getBalance(player, currency) < cost) {
            player.sendMessage(Component.text("You need " + cost + " " + currency));
            return;
        }

        mpc.withdraw(player, currency, cost);

        UUID treasuryId = burg.getTreasuryUuid();
        mpc.touch(treasuryId, currency);
        mpc.deposit(treasuryId, currency, cost);

        ItemStack charter = BurgBellUI.createCharterBell(plugin, burg);
        player.getInventory().addItem(charter);

        player.sendMessage(Component.text(
                "Purchased a Burg Charter from " + burg.getName()
                        + " for " + cost + " " + currency + "."));
    }

    private static boolean isMayorOrOp(Player player, Burg burg) {
        if (player.isOp()) return true;
        UUID leader = burg.getLeaderUuid();
        return leader != null && leader.equals(player.getUniqueId());
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String fmtPct(double rate) {
        return String.format("%.1f%%", rate * 100.0);
    }
}