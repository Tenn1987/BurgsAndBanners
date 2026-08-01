package com.brandon.burgsbanners.bell;
import com.brandon.burgsbanners.burg.Burg;
import com.brandon.burgsbanners.burg.BurgManager;
import com.brandon.burgsbanners.burg.plot.Plot;
import com.brandon.burgsbanners.mpc.MpcHook;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class BurgBellUI {

    private BurgBellUI() {}

    public static final String KEY_ACTION = "bb_action";
    public static final String KEY_RATE = "bb_rate";
    public static final String KEY_DELTA = "bb_delta";
    public static final String KEY_PLOT_ID = "bb_plot_id";

    public static final String KEY_CHARTER_ISSUER = "charter_issuer";
    public static final String KEY_CHARTER_CURRENCY = "charter_currency";
    public static final String KEY_CHARTER_SEED = "bb_charter_seed";

    public static void openMain(JavaPlugin plugin, BurgManager burgManager, MpcHook mpc, Player p, Burg burg) {
        openMain(plugin, p, burg, burgManager, mpc);
    }

    public static void openMain(JavaPlugin plugin, Player p, Burg burg, BurgManager burgManager, MpcHook mpc) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("Burg Bell - " + burg.getName()));
        fill(inv);

        inv.setItem(11, button(plugin, Material.PAPER,
                Component.text("View Taxes"),
                List.of(Component.text("See current tax rates.")),
                "VIEW_TAX", null, null));

        long cost = plugin.getConfig().getLong("founding.charterCost", 500L);
        long issuerShare = cost / 2;
        long seedShare = cost - issuerShare;

        inv.setItem(13, button(plugin, Material.BELL,
                Component.text("Buy Burg Charter"),
                List.of(
                        Component.text("Cost: " + cost + " " + burg.getAdoptedCurrencyCode()),
                        Component.text(issuerShare + " → Issuer Treasury"),
                        Component.text(seedShare + " → New Town Treasury")
                ),
                "BUY_CHARTER", null, null));

        inv.setItem(15, button(plugin, Material.COMPARATOR,
                Component.text("Tax Settings (Mayor)"),
                List.of(Component.text("Set sales tax and moneychanger fee.")),
                "OPEN_SETTINGS", null, null));

        long listings = burg.getPlots().values().stream().filter(Plot::isForSale).count();
        inv.setItem(17, button(plugin, Material.OAK_SIGN,
                Component.text("Property Ledger"),
                List.of(
                        Component.text("Browse burg properties for sale."),
                        Component.text("Active listings: " + listings)
                ),
                "OPEN_PLOTS", null, null));

        p.openInventory(inv);
    }

    public static void openSalesMenu(JavaPlugin plugin, Player p, Burg burg) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("Sales Tax - " + burg.getName()));
        fill(inv);

        inv.setItem(11, deltaButton(plugin, Material.RED_DYE, "-5%", "ADJUST_SALES", -0.05));
        inv.setItem(12, deltaButton(plugin, Material.YELLOW_DYE, "-1%", "ADJUST_SALES", -0.01));

        inv.setItem(13, button(plugin, Material.PAPER,
                Component.text("Current: " + String.format("%.1f%%", burg.getSalesTaxRate() * 100.0)),
                List.of(
                        Component.text("Min: 0.0%"),
                        Component.text("Max: " + String.format("%.1f%%", Burg.MAX_SALES_TAX * 100.0))
                ),
                "NOOP", null, null));

        inv.setItem(14, deltaButton(plugin, Material.YELLOW_DYE, "+1%", "ADJUST_SALES", 0.01));
        inv.setItem(15, deltaButton(plugin, Material.LIME_DYE, "+5%", "ADJUST_SALES", 0.05));
        inv.setItem(22, button(plugin, Material.ARROW, Component.text("Back"), List.of(), "BACK_MAIN", null, null));

        p.openInventory(inv);
    }

    public static void openMcFeeMenu(JavaPlugin plugin, Player p, Burg burg) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("Moneychanger Fee - " + burg.getName()));
        fill(inv);

        inv.setItem(11, deltaButton(plugin, Material.RED_DYE, "-5%", "ADJUST_MCFEE", -0.05));
        inv.setItem(12, deltaButton(plugin, Material.YELLOW_DYE, "-1%", "ADJUST_MCFEE", -0.01));

        inv.setItem(13, button(plugin, Material.PAPER,
                Component.text("Current: " + String.format("%.1f%%", burg.getMoneychangerFeeRate() * 100.0)),
                List.of(
                        Component.text("Min: 0.0%"),
                        Component.text("Max: " + String.format("%.1f%%", Burg.MAX_MONEYCHANGER_FEE * 100.0))
                ),
                "NOOP", null, null));

        inv.setItem(14, deltaButton(plugin, Material.YELLOW_DYE, "+1%", "ADJUST_MCFEE", 0.01));
        inv.setItem(15, deltaButton(plugin, Material.LIME_DYE, "+5%", "ADJUST_MCFEE", 0.05));
        inv.setItem(22, button(plugin, Material.ARROW, Component.text("Back"), List.of(), "BACK_MAIN", null, null));

        p.openInventory(inv);
    }

    /**
     * Displays every plot currently marked for sale in this burg.
     * Each listing carries the stable command/UI plot id in its PDC.
     */
    public static void openPlotMenu(JavaPlugin plugin, Player p, Burg burg) {
        Inventory inv = Bukkit.createInventory(null, 54, Component.text("Plots - " + burg.getName()));
        fill(inv);

        List<Plot> listings = burg.getPlots().values().stream()
                .filter(Plot::isForSale)
                .sorted(Comparator.comparing(Plot::getId, String.CASE_INSENSITIVE_ORDER))
                .toList();

        int[] listingSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        if (listings.isEmpty()) {
            inv.setItem(22, button(plugin, Material.BARRIER,
                    Component.text("No Properties For Sale"),
                    List.of(Component.text("The burg currently has no active listings.")),
                    "NOOP", null, null));
        } else {
            int count = Math.min(listings.size(), listingSlots.length);
            for (int i = 0; i < count; i++) {
                Plot plot = listings.get(i);
                inv.setItem(listingSlots[i], plotListing(plugin, plot));
            }

            if (listings.size() > listingSlots.length) {
                inv.setItem(49, button(plugin, Material.BOOK,
                        Component.text("More Listings Exist"),
                        List.of(Component.text("Showing " + listingSlots.length + " of " + listings.size() + "."),
                                Component.text("Pagination can be added later.")),
                        "NOOP", null, null));
            }
        }

        inv.setItem(45, button(plugin, Material.BOOK,
                Component.text("Property Ledger"),
                List.of(
                        Component.text("For-sale properties: " + listings.size()),
                        Component.text("Select a listing to purchase it.")
                ),
                "NOOP", null, null));

        inv.setItem(53, button(plugin, Material.ARROW,
                Component.text("Back"),
                List.of(),
                "BACK_MAIN", null, null));

        p.openInventory(inv);
    }

    private static ItemStack plotListing(JavaPlugin plugin, Plot plot) {
        String displayName = plot.getName() == null || plot.getName().isBlank()
                ? "Property " + plot.getId()
                : plot.getName();

        int width = Math.abs(plot.getMaxX() - plot.getMinX()) + 1;
        int depth = Math.abs(plot.getMaxZ() - plot.getMinZ()) + 1;

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Property ID: " + plot.getId()));
        lore.add(Component.text("Price: " + plot.getSalePrice() + " " + plot.getSaleCurrencyCode()));
        lore.add(Component.text(plot.getOwnerUuid() == null ? "Seller: Burg treasury" : "Seller: Private owner"));
        lore.add(Component.text("Size: " + width + " × " + depth));
        lore.add(Component.text("Bounds: X " + plot.getMinX() + " to " + plot.getMaxX()));
        lore.add(Component.text("        Z " + plot.getMinZ() + " to " + plot.getMaxZ()));
        lore.add(Component.text("Click to purchase."));

        Material material = plot.hasLien() ? Material.IRON_BARS : Material.OAK_DOOR;
        return button(plugin, material, Component.text(displayName), lore,
                "BUY_LISTED_PLOT", null, plot.getId());
    }

    public static ItemStack createCharterBell(JavaPlugin plugin, Burg issuerBurg) {
        ItemStack bell = new ItemStack(Material.BELL);
        ItemMeta meta = bell.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Burg Charter"));
            meta.lore(List.of(
                    Component.text("Issuer: " + issuerBurg.getName()),
                    Component.text("Currency: " + issuerBurg.getAdoptedCurrencyCode()),
                    Component.text("Redeem to found a new burg")
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, KEY_CHARTER_ISSUER),
                    PersistentDataType.STRING,
                    issuerBurg.getName());

            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, KEY_CHARTER_CURRENCY),
                    PersistentDataType.STRING,
                    issuerBurg.getAdoptedCurrencyCode());

            bell.setItemMeta(meta);
        }
        return bell;
    }

    public static long getCharterSeed(JavaPlugin plugin, ItemStack stack) {
        if (stack == null || stack.getType() != Material.BELL) return 0L;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return 0L;
        Long value = meta.getPersistentDataContainer().get(
                new NamespacedKey(plugin, KEY_CHARTER_SEED),
                PersistentDataType.LONG);
        return value == null ? 0L : value;
    }

    public static String getAction(JavaPlugin plugin, ItemStack clicked) {
        if (clicked == null || clicked.getType().isAir()) return null;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(
                new NamespacedKey(plugin, KEY_ACTION),
                PersistentDataType.STRING);
    }

    public static String getPlotId(JavaPlugin plugin, ItemStack clicked) {
        if (clicked == null || clicked.getType().isAir()) return null;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(
                new NamespacedKey(plugin, KEY_PLOT_ID),
                PersistentDataType.STRING);
    }

    public static Double getRate(JavaPlugin plugin, ItemStack clicked) {
        if (clicked == null || clicked.getType().isAir()) return null;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(
                new NamespacedKey(plugin, KEY_RATE),
                PersistentDataType.DOUBLE);
    }

    public static Double getDelta(JavaPlugin plugin, ItemStack clicked) {
        if (clicked == null || clicked.getType().isAir()) return null;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(
                new NamespacedKey(plugin, KEY_DELTA),
                PersistentDataType.DOUBLE);
    }

    private static ItemStack deltaButton(JavaPlugin plugin, Material mat, String label, String action, double delta) {
        ItemStack item = button(plugin, mat,
                Component.text(label),
                List.of(Component.text("Click to adjust")),
                action, null, null);

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, KEY_DELTA),
                    PersistentDataType.DOUBLE,
                    delta);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack button(JavaPlugin plugin,
                                    Material material,
                                    Component name,
                                    List<Component> lore,
                                    String action,
                                    Double rate,
                                    String plotId) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(name);
            if (lore != null && !lore.isEmpty()) meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, KEY_ACTION),
                    PersistentDataType.STRING,
                    action);

            if (rate != null) {
                meta.getPersistentDataContainer().set(
                        new NamespacedKey(plugin, KEY_RATE),
                        PersistentDataType.DOUBLE,
                        rate);
            }

            if (plotId != null) {
                meta.getPersistentDataContainer().set(
                        new NamespacedKey(plugin, KEY_PLOT_ID),
                        PersistentDataType.STRING,
                        plotId);
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    private static void fill(Inventory inv) {
        ItemStack filler = filler();
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
    }

    private static ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            item.setItemMeta(meta);
        }
        return item;
    }
}