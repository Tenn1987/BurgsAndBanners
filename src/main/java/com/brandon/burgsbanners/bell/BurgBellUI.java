package com.brandon.burgsbanners.bell;

import com.brandon.burgsbanners.burg.Burg;
import com.brandon.burgsbanners.burg.BurgManager;
import com.brandon.burgsbanners.mpc.MpcHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
import java.util.List;
import java.util.UUID;

/**
 * Burg Bell UI (Adventure-safe: no ChatColor, no deprecated InventoryView#getTitle, no deprecated ItemMeta#setDisplayName(String))
 */
public final class BurgBellUI {

    private BurgBellUI() {}

    public static final String KEY_ACTION = "bb_action";
    public static final String KEY_RATE = "bb_rate";

    // Charter persistent data
    public static final String KEY_CHARTER_ISSUER = "charter_issuer";
    public static final String KEY_CHARTER_CURRENCY = "charter_currency";
    public static final String KEY_CHARTER_SEED = "bb_charter_seed";

    /**
     * Compatibility overload for existing BurgCommand call order.
     */
    public static void openMain(JavaPlugin plugin, BurgManager burgManager, MpcHook mpc, Player p, Burg burg) {
        openMain(plugin, p, burg, burgManager, mpc);
    }

    public static void openMain(JavaPlugin plugin, Player p, Burg burg, BurgManager burgManager, MpcHook mpc) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("Burg Bell - " + burg.getName()));

        // Fillers
        ItemStack filler = filler();
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        // View tax
        inv.setItem(11, button(plugin, Material.PAPER,
                Component.text("View Taxes"),
                List.of(Component.text("See current tax rates.")),
                "VIEW_TAX", null));

        // Buy charter
        long cost = plugin.getConfig().getLong("founding.charterCost", 1000L);
        long issuerShare = cost / 2;
        long seedShare = cost - issuerShare;

        inv.setItem(13, button(plugin, Material.BELL,
                Component.text("Buy Burg Charter"),
                List.of(
                        Component.text("Cost: " + cost + " " + burg.getAdoptedCurrencyCode()),
                        Component.text(issuerShare + " → Issuer Treasury"),
                        Component.text(seedShare + " → New Town Treasury")
                ),
                "BUY_CHARTER", null));

        // Settings
        inv.setItem(15, button(plugin, Material.COMPARATOR,
                Component.text("Tax Settings (Mayor)"),
                List.of(Component.text("Set sales tax and moneychanger fee.")),
                "OPEN_SETTINGS", null));

        p.openInventory(inv);
    }

    public static void openSalesMenu(JavaPlugin plugin, Player p, Burg burg) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("Sales Tax - " + burg.getName()));
        ItemStack filler = filler();
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        inv.setItem(10, rateButton(plugin, Material.LIME_DYE, "0%", "SET_SALES", 0.00));
        inv.setItem(11, rateButton(plugin, Material.LIME_DYE, "1%", "SET_SALES", 0.01));
        inv.setItem(12, rateButton(plugin, Material.LIME_DYE, "2%", "SET_SALES", 0.02));
        inv.setItem(13, rateButton(plugin, Material.YELLOW_DYE, "3%", "SET_SALES", 0.03));
        inv.setItem(14, rateButton(plugin, Material.YELLOW_DYE, "4%", "SET_SALES", 0.04));
        inv.setItem(15, rateButton(plugin, Material.RED_DYE, "5%", "SET_SALES", 0.05));

        inv.setItem(22, button(plugin, Material.ARROW, Component.text("Back"), List.of(), "BACK_MAIN", null));

        p.openInventory(inv);
    }

    public static void openMcFeeMenu(JavaPlugin plugin, Player p, Burg burg) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("Moneychanger Fee - " + burg.getName()));
        ItemStack filler = filler();
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        inv.setItem(10, rateButton(plugin, Material.LIME_DYE, "0%", "SET_MCFEE", 0.00));
        inv.setItem(11, rateButton(plugin, Material.LIME_DYE, "1%", "SET_MCFEE", 0.01));
        inv.setItem(12, rateButton(plugin, Material.YELLOW_DYE, "2%", "SET_MCFEE", 0.02));
        inv.setItem(13, rateButton(plugin, Material.YELLOW_DYE, "3%", "SET_MCFEE", 0.03));
        inv.setItem(14, rateButton(plugin, Material.RED_DYE, "4%", "SET_MCFEE", 0.04));
        inv.setItem(15, rateButton(plugin, Material.RED_DYE, "5%", "SET_MCFEE", 0.05));

        inv.setItem(22, button(plugin, Material.ARROW, Component.text("Back"), List.of(), "BACK_MAIN", null));

        p.openInventory(inv);
    }

    public static ItemStack createCharterBell(JavaPlugin plugin, Burg issuerBurg, long seedFund) {
        ItemStack bell = new ItemStack(Material.BELL);
        ItemMeta meta = bell.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Burg Charter"));
            meta.lore(List.of(
                    Component.text("Issuer: " + issuerBurg.getName()),
                    Component.text("Currency: " + issuerBurg.getAdoptedCurrencyCode()),
                    Component.text("Seed Treasury: " + seedFund)
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

            NamespacedKey issuerKey = new NamespacedKey(plugin, KEY_CHARTER_ISSUER);
            NamespacedKey currencyKey = new NamespacedKey(plugin, KEY_CHARTER_CURRENCY);
            NamespacedKey seedKey = new NamespacedKey(plugin, KEY_CHARTER_SEED);

            meta.getPersistentDataContainer().set(issuerKey, PersistentDataType.STRING, issuerBurg.getName());
            meta.getPersistentDataContainer().set(currencyKey, PersistentDataType.STRING, issuerBurg.getAdoptedCurrencyCode());
            meta.getPersistentDataContainer().set(seedKey, PersistentDataType.LONG, seedFund);

            bell.setItemMeta(meta);
        }
        return bell;
    }

    public static long getCharterSeed(JavaPlugin plugin, ItemStack stack) {
        if (stack == null || stack.getType() != Material.BELL) return 0L;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return 0L;
        NamespacedKey seedKey = new NamespacedKey(plugin, KEY_CHARTER_SEED);
        Long v = meta.getPersistentDataContainer().get(seedKey, PersistentDataType.LONG);
        return v == null ? 0L : v;
    }

    public static String getAction(JavaPlugin plugin, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return null;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return null;
        NamespacedKey k = new NamespacedKey(plugin, KEY_ACTION);
        return meta.getPersistentDataContainer().get(k, PersistentDataType.STRING);
    }

    public static Double getRate(JavaPlugin plugin, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return null;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return null;
        NamespacedKey k = new NamespacedKey(plugin, KEY_RATE);
        return meta.getPersistentDataContainer().get(k, PersistentDataType.DOUBLE);
    }

    private static ItemStack rateButton(JavaPlugin plugin, Material mat, String label, String action, double rate) {
        return button(plugin, mat,
                Component.text(label),
                List.of(Component.text("Click to apply")),
                action, rate);
    }

    private static ItemStack button(JavaPlugin plugin, Material mat, Component name, List<Component> lore, String action, Double rate) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            if (lore != null && !lore.isEmpty()) meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

            NamespacedKey actionKey = new NamespacedKey(plugin, KEY_ACTION);
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);

            if (rate != null) {
                NamespacedKey rateKey = new NamespacedKey(plugin, KEY_RATE);
                meta.getPersistentDataContainer().set(rateKey, PersistentDataType.DOUBLE, rate);
            }

            it.setItemMeta(meta);
        }
        return it;
    }

    private static ItemStack filler() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            it.setItemMeta(meta);
        }
        return it;
    }
}