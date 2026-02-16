package com.brandon.burgsbanners.mint;

import com.brandon.burgsbanners.BurgsAndBannersPlugin;
import com.brandon.burgsbanners.burg.Burg;
import com.brandon.burgsbanners.burg.BurgManager;
import com.brandon.multipolarcurrency.MultiPolarCurrencyPlugin;
import com.brandon.multipolarcurrency.economy.currency.BackingType;
import com.brandon.multipolarcurrency.economy.currency.Currency;
import com.brandon.multipolarcurrency.economy.currency.CurrencyManager;
import com.brandon.multipolarcurrency.economy.currency.PhysicalCurrencyFactory;
import com.brandon.multipolarcurrency.economy.wallet.WalletService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CoinsmithGUIListener implements Listener {

    // Session context (no deprecated metadata)
    private static final Map<UUID, Burg> CONTEXT = new ConcurrentHashMap<>();

    public static void bind(UUID playerId, Burg burg) {
        if (playerId != null && burg != null) CONTEXT.put(playerId, burg);
    }

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

    private final MultiPolarCurrencyPlugin mpcPlugin;
    private final CurrencyManager currencyManager;

    public CoinsmithGUIListener(BurgsAndBannersPlugin babPlugin,
                                BurgManager burgManager,
                                MultiPolarCurrencyPlugin mpcPlugin) {
        this.babPlugin = babPlugin;
        this.burgManager = burgManager;
        this.mpcPlugin = mpcPlugin;
        this.currencyManager = (mpcPlugin != null) ? mpcPlugin.getCurrencyManager() : null;
    }

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

        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (title == null || !title.startsWith("Coinsmith")) return;

        Inventory top = event.getView().getTopInventory();
        int raw = event.getRawSlot();
        boolean clickedTop = raw < top.getSize();

        // Cancel by default; allow only INPUT slot changes
        if (clickedTop) {
            if (raw != SLOT_INPUT) {
                event.setCancelled(true);
            } else {
                if (event.getClick() == ClickType.DOUBLE_CLICK) event.setCancelled(true);
            }
        } else {
            // bottom inventory: block shift-click dumping into GUI
            if (event.isShiftClick()) event.setCancelled(true);
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
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (title == null || !title.startsWith("Coinsmith")) return;

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
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (title == null || !title.startsWith("Coinsmith")) return;

        CONTEXT.remove(event.getPlayer().getUniqueId());
    }

    private void handleMint(Player player, Inventory gui) {
        if (mpcPlugin == null || currencyManager == null) {
            player.sendMessage("§cMultiPolarCurrency not found. Mint is offline.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
            return;
        }

        Burg burg = CONTEXT.get(player.getUniqueId());
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
        code = code.trim().toUpperCase(Locale.ROOT);

        Optional<Currency> currencyOpt = currencyManager.getCurrency(code);
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

        // Consume backing
        input.setAmount(input.getAmount() - (int) REQUIRED_BACKING_ITEMS);
        if (input.getAmount() <= 0) gui.setItem(SLOT_INPUT, null);

        // Mint physical coins to player (8 units)
        List<ItemStack> mintedStacks = PhysicalCurrencyFactory.createPhysical(mpcPlugin, currency, MINTED_UNITS);
        for (ItemStack stack : mintedStacks) {
            player.getInventory().addItem(stack);
        }

        // Ensure treasury UUID exists
        if (burg.getTreasuryUuid() == null) {
            burg.setTreasuryUuid(UUID.randomUUID());
        }

        // Credit BaB display ledger
        burg.creditTreasury(code, FEE_UNITS);

        // Deposit into MPC treasury wallet (reflection getter so it compiles even if jar isn't refreshed yet)
        boolean walletOk = false;
        try {
            WalletService ws = reflectWalletService(mpcPlugin);
            if (ws != null) {
                walletOk = ws.deposit(burg.getTreasuryUuid(), code, FEE_UNITS);
            }
        } catch (Throwable ignored) {
            walletOk = false;
        }

        burgManager.save(burg);

        // Output preview
        gui.setItem(SLOT_OUTPUT, makePreview(currency));

        player.sendMessage("§aMinted §f" + MINTED_UNITS + " " + code
                + " §a(§fFee: " + FEE_UNITS + "§a → treasury" + (walletOk ? "" : " §c(wallet deposit failed)") + "§a)");
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.1f);

        if (!walletOk) {
            babPlugin.getLogger().warning("[Coinsmith] Treasury wallet deposit failed for burg "
                    + burg.getName() + " treasury=" + burg.getTreasuryUuid() + " code=" + code + " amount=" + FEE_UNITS);
        }
    }

    private static WalletService reflectWalletService(MultiPolarCurrencyPlugin mpcPlugin) {
        try {
            Method m = mpcPlugin.getClass().getMethod("getWalletService");
            Object o = m.invoke(mpcPlugin);
            if (o instanceof WalletService ws) return ws;
        } catch (Throwable ignored) {
        }
        return null;
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

    private static ItemStack mintButton() {
        ItemStack it = new ItemStack(Material.LIME_WOOL);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("§aMint"));
            meta.lore(List.of(
                    Component.text("§7Consumes: §f" + REQUIRED_BACKING_ITEMS + " backing items"),
                    Component.text("§7Produces: §f" + MINTED_UNITS + " units"),
                    Component.text("§7Fee → Treasury: §f" + FEE_UNITS + " unit")
            ));
            it.setItemMeta(meta);
        }
        return it;
    }

    private static ItemStack outputPlaceholder() {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("§eOutput Preview"));
            meta.lore(List.of(Component.text("§7Put the correct backing material in the input slot.")));
            it.setItemMeta(meta);
        }
        return it;
    }

    private static ItemStack makePreview(Currency currency) {
        List<ItemStack> preview = PhysicalCurrencyFactory.createPhysical(null, currency, 1L);
        if (preview.isEmpty()) return outputPlaceholder();
        ItemStack it = preview.get(0).clone();
        it.setAmount(1);
        return it;
    }
}
