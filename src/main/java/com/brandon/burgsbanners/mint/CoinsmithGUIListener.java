package com.brandon.burgsbanners.mint;

import com.brandon.burgsbanners.BurgsAndBannersPlugin;
import com.brandon.burgsbanners.burg.Burg;
import com.brandon.burgsbanners.burg.BurgManager;

import com.brandon.multipolarcurrency.economy.currency.BackingType;
import com.brandon.multipolarcurrency.economy.currency.Currency;
import com.brandon.multipolarcurrency.economy.currency.PhysicalCurrencyFactory;


import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.*;

public class CoinsmithGUIListener implements Listener {

    public static final String GUI_PREFIX = "§6Coinsmith";
    public static final String META_BURG = "bab_coinsmith_burg";

    // Slots
    public static final int SLOT_INPUT = 11;
    public static final int SLOT_OUTPUT = 15;
    public static final int SLOT_MINT = 22;

    // Rule: 9 backing items -> 8 units minted; 1 unit fee to treasury
    private static final long REQUIRED_BACKING_ITEMS = 9L;
    private static final long MINTED_UNITS = 8L;
    private static final long FEE_UNITS = 1L;

    private final BurgsAndBannersPlugin babPlugin;
    private final BurgManager burgManager;

    // MUST be MPC plugin instance so PDC keys match what /wallet expects
    private final JavaPlugin mpcPlugin;

    public CoinsmithGUIListener(BurgsAndBannersPlugin babPlugin, BurgManager burgManager) {
        this.babPlugin = babPlugin;
        this.burgManager = burgManager;
        this.mpcPlugin = resolveMpcPlugin();
    }

    // Build GUI contents (called by anvil listener)
    public static void populate(Inventory inv) {
        for (int i = 0; i < inv.getSize(); i++) {
            if (i == SLOT_INPUT) continue;
            inv.setItem(i, filler());
        }
        inv.setItem(SLOT_OUTPUT, outputPlaceholder());
        inv.setItem(SLOT_MINT, mintButton());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView() == null || event.getView().getTitle() == null) return;

        String title = event.getView().getTitle();
        if (!title.startsWith(GUI_PREFIX)) return;

        Inventory top = event.getView().getTopInventory();
        if (top == null) return;

        int raw = event.getRawSlot();
        boolean clickedTop = raw < top.getSize();

        // Cancel by default; allow only INPUT slot changes
        if (clickedTop) {
            if (raw != SLOT_INPUT) {
                event.setCancelled(true);
            } else {
                // still block double-click weirdness
                if (event.getClick() == ClickType.DOUBLE_CLICK) event.setCancelled(true);
            }
        } else {
            // bottom inv: block shift-click dumping into GUI
            if (event.isShiftClick()) {
                event.setCancelled(true);
                tryShiftMoveToInput(player, top, event.getCurrentItem());
            }
            return;
        }

        if (raw == SLOT_OUTPUT) {
            event.setCancelled(true);
            return;
        }

        if (raw == SLOT_MINT) {
            event.setCancelled(true);
            handleMint(player, top);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView() == null || event.getView().getTitle() == null) return;
        if (!event.getView().getTitle().startsWith(GUI_PREFIX)) return;

        // Only allow dragging into INPUT slot
        for (int slot : event.getRawSlots()) {
            if (slot < event.getView().getTopInventory().getSize() && slot != SLOT_INPUT) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getView() == null || event.getView().getTitle() == null) return;
        if (!event.getView().getTitle().startsWith(GUI_PREFIX)) return;

        player.removeMetadata(META_BURG, babPlugin);
    }

    private void handleMint(Player player, Inventory gui) {
        if (mpcPlugin == null) {
            player.sendMessage("§cMultiPolarCurrency not found. Mint is offline.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
            return;
        }

        Burg burg = getBurgFromPlayer(player);
        if (burg == null) {
            player.sendMessage("§cMint error: burg context missing.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
            return;
        }

        String code = burg.getAdoptedCurrencyCode();
        if (code == null || code.isBlank()) {
            player.sendMessage("§cThis burg has no adopted currency.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
            return;
        }
        code = code.toUpperCase(Locale.ROOT);

        Optional<Currency> currencyOpt = resolveCurrencyFromMpc(code);
        if (currencyOpt.isEmpty()) {
            player.sendMessage("§cCurrency not registered in MPC: §f" + code);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
            return;
        }

        Currency currency = currencyOpt.get();

        if (!currency.enabled()) {
            player.sendMessage("§cThis currency is disabled: §f" + code);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
            return;
        }

        if (!currency.mintable()) {
            player.sendMessage("§cThis currency is not mintable: §f" + code);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
            return;
        }

        if (currency.backingType() != BackingType.COMMODITY) {
            player.sendMessage("§cThis mint only supports commodity-backed currencies.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
            return;
        }

        if (currency.backingMaterial().isEmpty()) {
            player.sendMessage("§cThis commodity currency has no backing material configured.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
            return;
        }

        Material backingMat = Material.matchMaterial(currency.backingMaterial().get());
        if (backingMat == null || backingMat.isAir()) {
            player.sendMessage("§cInvalid backing material in MPC: §f" + currency.backingMaterial().get());
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
            return;
        }

        ItemStack input = gui.getItem(SLOT_INPUT);
        if (input == null || input.getType().isAir()) {
            player.sendMessage("§cPut §f" + backingMat.name() + "§c in the input slot.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
            return;
        }

        if (input.getType() != backingMat) {
            player.sendMessage("§cThis mint requires §f" + backingMat.name() + "§c.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
            return;
        }

        if (input.getAmount() < REQUIRED_BACKING_ITEMS) {
            player.sendMessage("§cNeed §f" + REQUIRED_BACKING_ITEMS + "§c " + backingMat.name()
                    + " to mint §f" + MINTED_UNITS + " " + code + "§c.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
            return;
        }

        // consume backing
        input.setAmount((int) (input.getAmount() - REQUIRED_BACKING_ITEMS));
        if (input.getAmount() <= 0) gui.setItem(SLOT_INPUT, null);

        // mint physical currency via MPC
        List<ItemStack> mintedStacks = PhysicalCurrencyFactory.createPhysical(mpcPlugin, currency, MINTED_UNITS);
        for (ItemStack s : mintedStacks) {
            player.getInventory().addItem(s);
        }

        // fee to treasury (in currency units)
        burg.creditTreasury(code, FEE_UNITS);
        burgManager.save(burg);

        // update preview
        gui.setItem(SLOT_OUTPUT, makePreview(currency));

        player.sendMessage("§aMinted §f" + MINTED_UNITS + " " + code + " §a(§fFee: " + FEE_UNITS + "§a → treasury)");
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.1f);
    }

    private Burg getBurgFromPlayer(Player player) {
        if (!player.hasMetadata(META_BURG)) return null;
        for (MetadataValue v : player.getMetadata(META_BURG)) {
            if (v.getOwningPlugin() == babPlugin) {
                Object obj = v.value();
                if (obj instanceof Burg b) return b;
            }
        }
        return null;
    }

    private void tryShiftMoveToInput(Player player, Inventory gui, ItemStack clicked) {
        if (clicked == null || clicked.getType().isAir()) return;

        ItemStack input = gui.getItem(SLOT_INPUT);
        if (input == null || input.getType().isAir()) {
            gui.setItem(SLOT_INPUT, clicked.clone());
            clicked.setAmount(0);
        }
    }

    private JavaPlugin resolveMpcPlugin() {
        Plugin p = Bukkit.getPluginManager().getPlugin("MultiPolarCurrency");
        if (p instanceof JavaPlugin jp) return jp;

        // fallback name if you renamed it
        Plugin alt = Bukkit.getPluginManager().getPlugin("MPC");
        if (alt instanceof JavaPlugin jp2) return jp2;

        babPlugin.getLogger().warning("[Coinsmith] MultiPolarCurrency plugin not found.");
        return null;
    }

    /**
     * Uses reflection so BaB doesn't need to know MPC main class type.
     * Requires MPC plugin to have getCurrencyManager() returning CurrencyManager.
     */
    private Optional<Currency> resolveCurrencyFromMpc(String code) {
        if (mpcPlugin == null) return Optional.empty();

        try {
            Method getCM = mpcPlugin.getClass().getMethod("getCurrencyManager");
            Object cmObj = getCM.invoke(mpcPlugin);
            if (cmObj == null) return Optional.empty();

            Method getCurrency = cmObj.getClass().getMethod("getCurrency", String.class);
            Object optObj = getCurrency.invoke(cmObj, code);

            if (optObj instanceof Optional) {
                @SuppressWarnings("unchecked")
                Optional<Currency> out = (Optional<Currency>) optObj;
                return out;
            }
        } catch (NoSuchMethodException e) {
            babPlugin.getLogger().warning("[Coinsmith] MPC plugin lacks getCurrencyManager(). Add it or use your MpcHook.");
        } catch (Exception e) {
            babPlugin.getLogger().warning("[Coinsmith] Currency lookup failed: " + e.getMessage());
        }

        return Optional.empty();
    }

    private static ItemStack filler() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            it.setItemMeta(meta);
        }
        return it;
    }

    private static ItemStack mintButton() {
        ItemStack it = new ItemStack(Material.LIME_WOOL);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§aMint");
            meta.setLore(List.of(
                    "§7Consumes: §f" + REQUIRED_BACKING_ITEMS + " backing items",
                    "§7Produces: §f" + MINTED_UNITS + " units",
                    "§7Fee → Treasury: §f" + FEE_UNITS + " unit"
            ));
            it.setItemMeta(meta);
        }
        return it;
    }

    private static ItemStack outputPlaceholder() {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§eOutput Preview");
            meta.setLore(List.of("§7Put the correct backing material in the input slot."));
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack makePreview(Currency currency) {
        List<ItemStack> preview = PhysicalCurrencyFactory.createPhysical(mpcPlugin, currency, 1L);
        if (preview.isEmpty()) return outputPlaceholder();
        ItemStack it = preview.get(0).clone();
        it.setAmount(1);
        return it;
    }
}
