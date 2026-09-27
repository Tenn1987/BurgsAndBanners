package com.brandon.burgsbanners.mint;

import com.brandon.burgsbanners.BurgsAndBannersPlugin;
import com.brandon.burgsbanners.burg.Burg;
import com.brandon.burgsbanners.burg.BurgManager;
import com.brandon.multipolarcurrency.MultiPolarCurrencyPlugin;
import com.brandon.multipolarcurrency.economy.currency.BackingType;
import com.brandon.multipolarcurrency.economy.currency.Currency;
import com.brandon.multipolarcurrency.economy.currency.CurrencyManager;
import com.brandon.multipolarcurrency.economy.currency.PhysicalCurrencyFactory;
import com.brandon.multipolarcurrency.economy.exchange.ExchangeService;
import com.brandon.multipolarcurrency.economy.wallet.WalletService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CoinsmithGUIListener implements Listener {

    private static final Map<UUID, Burg> CONTEXT =
            new ConcurrentHashMap<>();

    public static void bind(UUID playerId, Burg burg) {
        if (playerId != null && burg != null) {
            CONTEXT.put(playerId, burg);
        }
    }

    public static final int SLOT_INPUT = 11;
    public static final int SLOT_OUTPUT = 15;
    public static final int SLOT_MINT = 22;

    private static final long MINTED_UNITS = 8L;
    private static final long FEE_UNITS = 1L;

    private final BurgsAndBannersPlugin babPlugin;
    private final BurgManager burgManager;

    public CoinsmithGUIListener(BurgsAndBannersPlugin babPlugin,
                                BurgManager burgManager,
                                MultiPolarCurrencyPlugin ignoredStartupMpcPlugin) {
        this.babPlugin = babPlugin;
        this.burgManager = burgManager;
    }

    public static void populate(Inventory inv, Currency currency) {
        for (int i = 0; i < inv.getSize(); i++) {
            if (i == SLOT_INPUT) continue;
            inv.setItem(i, filler());
        }

        inv.setItem(SLOT_OUTPUT, outputPlaceholder());
        inv.setItem(SLOT_MINT, mintButton(currency));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title =
                PlainTextComponentSerializer.plainText()
                        .serialize(event.getView().title());

        if (title == null || !title.startsWith("Coinsmith")) return;

        Inventory top = event.getView().getTopInventory();
        int raw = event.getRawSlot();
        boolean clickedTop = raw < top.getSize();

        if (clickedTop) {
            if (raw != SLOT_INPUT) {
                event.setCancelled(true);
            } else if (event.getClick() == ClickType.DOUBLE_CLICK) {
                event.setCancelled(true);
            }
        } else {
            if (event.isShiftClick()) {
                event.setCancelled(true);
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
        String title =
                PlainTextComponentSerializer.plainText()
                        .serialize(event.getView().title());

        if (title == null || !title.startsWith("Coinsmith")) return;

        for (int slot : event.getRawSlots()) {
            if (slot < event.getView()
                    .getTopInventory()
                    .getSize()
                    && slot != SLOT_INPUT) {

                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        String title =
                PlainTextComponentSerializer.plainText()
                        .serialize(event.getView().title());

        if (title == null || !title.startsWith("Coinsmith")) return;

        if (!(event.getPlayer() instanceof Player player)) {
            CONTEXT.remove(event.getPlayer().getUniqueId());
            return;
        }

        Inventory top = event.getView().getTopInventory();
        ItemStack input = top.getItem(SLOT_INPUT);

        if (input != null
                && !input.getType().isAir()
                && input.getAmount() > 0) {

            ItemStack toReturn = input.clone();
            top.setItem(SLOT_INPUT, null);

            Map<Integer, ItemStack> leftovers =
                    player.getInventory().addItem(toReturn);

            if (!leftovers.isEmpty()) {
                leftovers.values().forEach(
                        leftover ->
                                player.getWorld()
                                        .dropItemNaturally(
                                                player.getLocation(),
                                                leftover
                                        )
                );
            }
        }

        CONTEXT.remove(player.getUniqueId());
    }

    private void handleMint(Player player, Inventory gui) {

        MultiPolarCurrencyPlugin mpcPlugin = resolveMpcPlugin();

        if (mpcPlugin == null) {
            player.sendMessage(
                    "§cMultiPolarCurrency not found. Mint is offline."
            );
            player.playSound(
                    player.getLocation(),
                    Sound.ENTITY_VILLAGER_NO,
                    1f,
                    0.8f
            );
            return;
        }

        CurrencyManager currencyManager =
                mpcPlugin.getCurrencyManager();

        if (currencyManager == null) {
            player.sendMessage(
                    "§cMultiPolarCurrency not found. Mint is offline."
            );
            player.playSound(
                    player.getLocation(),
                    Sound.ENTITY_VILLAGER_NO,
                    1f,
                    0.8f
            );
            return;
        }

        Burg burg = CONTEXT.get(player.getUniqueId());

        if (burg == null) {
            player.sendMessage("§cMint error: burg context missing.");
            player.playSound(
                    player.getLocation(),
                    Sound.ENTITY_VILLAGER_NO,
                    1f,
                    0.8f
            );
            return;
        }

        String code = burg.getAdoptedCurrencyCode();

        if (code == null || code.isBlank()) {
            player.sendMessage("§cThis burg has no adopted currency.");
            player.playSound(
                    player.getLocation(),
                    Sound.ENTITY_VILLAGER_NO,
                    1f,
                    0.8f
            );
            return;
        }

        code = code.trim().toUpperCase(Locale.ROOT);

        Optional<Currency> currencyOpt =
                currencyManager.getCurrency(code);

        if (currencyOpt.isEmpty()) {
            player.sendMessage(
                    "§cCurrency not registered in MPC: §f" + code
            );
            player.playSound(
                    player.getLocation(),
                    Sound.ENTITY_VILLAGER_NO,
                    1f,
                    0.8f
            );
            return;
        }

        Currency currency = currencyOpt.get();

        long itemsPerUnit =
                Math.max(1L, currency.unitsPerBackingItem());

        long grossUnits =
                MINTED_UNITS + FEE_UNITS;

        long requiredBackingItems =
                grossUnits * itemsPerUnit;

        if (!currency.enabled()) {
            player.sendMessage(
                    "§cThis currency is disabled: §f" + code
            );
            player.playSound(
                    player.getLocation(),
                    Sound.ENTITY_VILLAGER_NO,
                    1f,
                    0.8f
            );
            return;
        }

        if (!currency.mintable()) {
            player.sendMessage(
                    "§cThis currency is not mintable: §f" + code
            );
            player.playSound(
                    player.getLocation(),
                    Sound.ENTITY_VILLAGER_NO,
                    1f,
                    0.8f
            );
            return;
        }

        if (currency.backingType() != BackingType.COMMODITY) {
            player.sendMessage(
                    "§cThis mint only supports commodity-backed currencies."
            );
            player.playSound(
                    player.getLocation(),
                    Sound.ENTITY_VILLAGER_NO,
                    1f,
                    0.8f
            );
            return;
        }

        if (currency.backingMaterial().isEmpty()) {
            player.sendMessage(
                    "§cThis commodity currency has no backing material configured."
            );
            player.playSound(
                    player.getLocation(),
                    Sound.ENTITY_VILLAGER_NO,
                    1f,
                    0.8f
            );
            return;
        }

        Material backingMat =
                Material.matchMaterial(
                        currency.backingMaterial().get()
                );

        if (backingMat == null || backingMat.isAir()) {
            player.sendMessage(
                    "§cInvalid backing material in MPC: §f"
                            + currency.backingMaterial().get()
            );
            player.playSound(
                    player.getLocation(),
                    Sound.ENTITY_VILLAGER_NO,
                    1f,
                    0.8f
            );
            return;
        }

        ItemStack input = gui.getItem(SLOT_INPUT);

        if (input == null || input.getType().isAir()) {
            player.sendMessage(
                    "§cPut §f" + backingMat.name()
                            + "§c in the input slot."
            );
            player.playSound(
                    player.getLocation(),
                    Sound.ENTITY_VILLAGER_NO,
                    1f,
                    0.8f
            );
            return;
        }

        if (input.getType() != backingMat) {
            player.sendMessage(
                    "§cThis mint requires §f"
                            + backingMat.name()
                            + "§c."
            );
            player.playSound(
                    player.getLocation(),
                    Sound.ENTITY_VILLAGER_NO,
                    1f,
                    0.8f
            );
            return;
        }

        if (input.getAmount() < requiredBackingItems) {
            player.sendMessage(
                    "§cNeed §f"
                            + requiredBackingItems
                            + "§c "
                            + backingMat.name()
                            + "§c to mint §f"
                            + MINTED_UNITS
                            + " "
                            + code
                            + "§c (plus §f"
                            + FEE_UNITS
                            + "§c treasury fee unit)."
            );

            player.playSound(
                    player.getLocation(),
                    Sound.ENTITY_VILLAGER_NO,
                    1f,
                    0.8f
            );
            return;
        }

        // Resolve pricing before consuming backing; a successful mint must be
        // reflected in MPC's supply pressure even when the coins stay physical.
        ExchangeService exchange = reflectExchangeService(mpcPlugin);
        if (exchange == null) {
            player.sendMessage("§cMint is offline: MPC exchange service unavailable.");
            return;
        }

        // Consume backing.
        input.setAmount(
                input.getAmount()
                        - (int) requiredBackingItems
        );

        if (input.getAmount() <= 0) {
            gui.setItem(SLOT_INPUT, null);
        } else {
            gui.setItem(SLOT_INPUT, input);
        }

        // Mint 8 physical units to player.
        List<ItemStack> mintedStacks =
                PhysicalCurrencyFactory.createPhysical(
                        mpcPlugin,
                        currency,
                        MINTED_UNITS
                );

        for (ItemStack stack : mintedStacks) {
            HashMap<Integer, ItemStack> leftovers =
                    player.getInventory().addItem(stack);

            if (!leftovers.isEmpty()) {
                leftovers.values().forEach(
                        leftover ->
                                player.getWorld()
                                        .dropItemNaturally(
                                                player.getLocation(),
                                                leftover
                                        )
                );
            }
        }

        if (burg.getTreasuryUuid() == null) {
            burg.setTreasuryUuid(UUID.randomUUID());
        }

        // BaB display ledger.
        burg.creditTreasury(code, FEE_UNITS);

        // MPC treasury wallet.
        boolean walletOk = false;

        try {
            WalletService ws =
                    reflectWalletService(mpcPlugin);

            if (ws != null) {
                walletOk =
                        ws.deposit(
                                burg.getTreasuryUuid(),
                                code,
                                FEE_UNITS
                        );
            }

        } catch (Throwable ignored) {
            walletOk = false;
        }

        burgManager.save(burg);

        // All nine units are issued: eight physical coins and one treasury unit.
        // The wallet deposit above is an internal credit, not the mint event.
        exchange.recordPressure(code, -0.001 * grossUnits);
        exchange.settle(0.05);

        gui.setItem(
                SLOT_OUTPUT,
                makePreview(currency)
        );

        player.sendMessage(
                "§aMinted §f"
                        + MINTED_UNITS
                        + " "
                        + code
                        + " §a(§fFee: "
                        + FEE_UNITS
                        + "§a → treasury"
                        + (walletOk
                        ? ""
                        : " §c(wallet deposit failed)")
                        + "§a)"
        );

        player.playSound(
                player.getLocation(),
                Sound.BLOCK_ANVIL_USE,
                0.8f,
                1.1f
        );

        if (!walletOk) {
            babPlugin.getLogger().warning(
                    "[Coinsmith] Treasury wallet deposit failed for burg "
                            + burg.getName()
                            + " treasury="
                            + burg.getTreasuryUuid()
                            + " code="
                            + code
                            + " amount="
                            + FEE_UNITS
            );
        }
    }

    private MultiPolarCurrencyPlugin resolveMpcPlugin() {
        Plugin candidate =
                Bukkit.getPluginManager()
                        .getPlugin("MultiPolarCurrency");

        if (!(candidate instanceof MultiPolarCurrencyPlugin mpc)) {
            return null;
        }

        if (!mpc.isEnabled()) {
            return null;
        }

        return mpc;
    }

    private static WalletService reflectWalletService(
            MultiPolarCurrencyPlugin mpcPlugin) {

        try {
            Method m =
                    mpcPlugin.getClass()
                            .getMethod("getWalletService");

            Object o = m.invoke(mpcPlugin);

            if (o instanceof WalletService ws) {
                return ws;
            }

        } catch (Throwable ignored) {
        }

        return null;
    }

    private static ExchangeService reflectExchangeService(
            MultiPolarCurrencyPlugin mpcPlugin) {
        try {
            for (Field field : mpcPlugin.getClass().getDeclaredFields()) {
                if (ExchangeService.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return (ExchangeService) field.get(mpcPlugin);
                }
            }
        } catch (ReflectiveOperationException | SecurityException ignored) {
        }
        return null;
    }

    private static ItemStack filler() {
        ItemStack it =
                new ItemStack(
                        Material.GRAY_STAINED_GLASS_PANE
                );

        ItemMeta meta = it.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(" "));
            it.setItemMeta(meta);
        }

        return it;
    }

    private static ItemStack mintButton(Currency currency) {
        long itemsPerUnit =
                Math.max(
                        1L,
                        currency.unitsPerBackingItem()
                );

        long grossUnits =
                MINTED_UNITS + FEE_UNITS;

        long requiredBackingItems =
                grossUnits * itemsPerUnit;

        ItemStack it =
                new ItemStack(Material.LIME_WOOL);

        ItemMeta meta = it.getItemMeta();

        if (meta != null) {
            meta.displayName(
                    Component.text("§aMint")
            );

            meta.lore(
                    List.of(
                            Component.text(
                                    "§7Consumes: §f"
                                            + requiredBackingItems
                                            + " backing items"
                            ),
                            Component.text(
                                    "§7Produces: §f"
                                            + MINTED_UNITS
                                            + " units"
                            ),
                            Component.text(
                                    "§7Fee → Treasury: §f"
                                            + FEE_UNITS
                                            + " unit"
                            ),
                            Component.text(
                                    "§7Rate: §f"
                                            + itemsPerUnit
                                            + " backing per unit"
                            )
                    )
            );

            it.setItemMeta(meta);
        }

        return it;
    }

    private static ItemStack outputPlaceholder() {
        ItemStack it =
                new ItemStack(Material.PAPER);

        ItemMeta meta = it.getItemMeta();

        if (meta != null) {
            meta.displayName(
                    Component.text("§eOutput Preview")
            );

            meta.lore(
                    List.of(
                            Component.text(
                                    "§7Put the correct backing material in the input slot."
                            )
                    )
            );

            it.setItemMeta(meta);
        }

        return it;
    }

    private static ItemStack makePreview(Currency currency) {
        List<ItemStack> preview =
                PhysicalCurrencyFactory.createPhysical(
                        null,
                        currency,
                        1L
                );

        if (preview.isEmpty()) {
            return outputPlaceholder();
        }

        ItemStack it = preview.get(0).clone();
        it.setAmount(1);

        return it;
    }
}
