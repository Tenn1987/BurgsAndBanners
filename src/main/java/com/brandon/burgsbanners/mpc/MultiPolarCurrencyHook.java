package com.brandon.burgsbanners.mpc;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Logger;

/**
 * Reflection-based hook into MultiPolarCurrency (MPC).
 *
 * This class assumes MPC's main plugin class is:
 *   com.brandon.multipolarcurrency.MultiPolarCurrencyPlugin
 *
 * And it expects MPC to have private fields:
 *   - economy.currency.CurrencyManager currencyManager
 *   - economy.wallet.WalletService walletService
 *
 * If those change, update the reflective lookups below.
 */
public final class MultiPolarCurrencyHook implements MpcHook {

    private static final String MPC_PLUGIN_NAME = "MultiPolarCurrency";
    private static final String MPC_MAIN_CLASS = "com.brandon.multipolarcurrency.MultiPolarCurrencyPlugin";

    private final Logger log;

    private Plugin mpcPlugin;
    private Object currencyManager;   // com.brandon.multipolarcurrency.economy.currency.CurrencyManager
    private Object walletService;     // com.brandon.multipolarcurrency.economy.wallet.WalletService

    // CurrencyManager methods
    private Method cmExists;          // exists(String)
    private Method cmAll;             // all() -> Collection<Currency>
    private Method currencyCode;      // Currency.code() (record accessor)

    // WalletService methods
    private Method wsBalance;         // balance(UUID, String) -> long
    private Method wsWithdraw;        // withdraw(UUID, String, long) -> boolean
    private Method wsDeposit;         // deposit(UUID, String, long) -> void

    private boolean hooked = false;

    public MultiPolarCurrencyHook(Logger log) {
        this.log = (log != null) ? log : Bukkit.getLogger();
        resolve();
    }

    private void resolve() {
        try {
            this.mpcPlugin = Bukkit.getPluginManager().getPlugin(MPC_PLUGIN_NAME);
            if (mpcPlugin == null || !mpcPlugin.isEnabled()) {
                log.warning("[BAB] MPC not present/enabled. Hook disabled.");
                hooked = false;
                return;
            }

            // Defensive: ensure we are talking to the expected plugin implementation.
            if (!mpcPlugin.getClass().getName().equals(MPC_MAIN_CLASS)) {
                log.warning("[BAB] MPC plugin class mismatch. Expected " + MPC_MAIN_CLASS
                        + " but found " + mpcPlugin.getClass().getName() + ". Hook may fail.");
            }

            // Grab private fields from MPC plugin
            this.currencyManager = getField(mpcPlugin, "currencyManager");
            this.walletService = getField(mpcPlugin, "walletService");

            if (currencyManager == null || walletService == null) {
                log.warning("[BAB] Could not resolve MPC currencyManager or walletService via reflection.");
                hooked = false;
                return;
            }

            // CurrencyManager methods
            this.cmExists = currencyManager.getClass().getMethod("exists", String.class);
            this.cmAll = currencyManager.getClass().getMethod("all");

            // Discover Currency record accessor: code()
            // (We don't want to hard compile against MPC here.)
            Collection<?> currencies = safeAllCurrencies();
            if (currencies != null && !currencies.isEmpty()) {
                Object sample = currencies.iterator().next();
                this.currencyCode = sample.getClass().getMethod("code");
            } else {
                // Fall back: try to resolve accessor by name on declared return type if possible.
                // We'll attempt later when we have a Currency instance.
                this.currencyCode = null;
            }

            // WalletService methods (from your MPC sources)
            this.wsBalance = walletService.getClass().getMethod("balance", UUID.class, String.class);
            this.wsWithdraw = walletService.getClass().getMethod("withdraw", UUID.class, String.class, long.class);
            this.wsDeposit = walletService.getClass().getMethod("deposit", UUID.class, String.class, long.class);

            hooked = true;
            log.info("[BAB] Hooked into MultiPolarCurrency via reflection (CurrencyManager + WalletService).");

        } catch (Throwable t) {
            hooked = false;
            log.warning("[BAB] Failed to hook into MultiPolarCurrency: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private Object getField(Object instance, String fieldName) {
        try {
            Field f = instance.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.get(instance);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Collection<?> safeAllCurrencies() {
        try {
            Object result = cmAll.invoke(currencyManager);
            if (result instanceof Collection<?> col) return col;
        } catch (Throwable ignored) { }
        return null;
    }

    @Override
    public boolean isHooked() {
        // If plugin got disabled after boot, treat as not hooked.
        return hooked && mpcPlugin != null && mpcPlugin.isEnabled();
    }

    @Override
    public boolean currencyExists(String currencyCode) {
        if (!isHooked() || currencyCode == null) return false;
        try {
            return (boolean) cmExists.invoke(currencyManager, currencyCode.trim().toUpperCase(Locale.ROOT));
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public List<String> suggestCurrencyCodes(String prefix) {
        if (!isHooked()) return Collections.emptyList();
        String p = (prefix == null) ? "" : prefix.trim().toUpperCase(Locale.ROOT);

        try {
            Collection<?> col = safeAllCurrencies();
            if (col == null || col.isEmpty()) return Collections.emptyList();

            // Resolve currencyCode accessor if not already resolved.
            if (currencyCode == null) {
                Object sample = col.iterator().next();
                currencyCode = sample.getClass().getMethod("code");
            }

            List<String> out = new ArrayList<>();
            for (Object c : col) {
                String code = (String) currencyCode.invoke(c);
                if (code != null && code.toUpperCase(Locale.ROOT).startsWith(p)) {
                    out.add(code.toUpperCase(Locale.ROOT));
                }
            }
            out.sort(String.CASE_INSENSITIVE_ORDER);
            return out;
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    @Override
    public long getBalance(Player player, String currencyCode) {
        if (!isHooked() || player == null || currencyCode == null) return 0L;
        try {
            Object r = wsBalance.invoke(walletService, player.getUniqueId(), currencyCode.trim().toUpperCase(Locale.ROOT));
            if (r instanceof Long l) return l;
            if (r instanceof Number n) return n.longValue();
            return 0L;
        } catch (Throwable t) {
            return 0L;
        }
    }

    @Override
    public boolean withdraw(Player player, String currencyCode, long amount) {
        if (!isHooked() || player == null || currencyCode == null) return false;
        if (amount <= 0) return true;
        try {
            Object r = wsWithdraw.invoke(walletService, player.getUniqueId(), currencyCode.trim().toUpperCase(Locale.ROOT), amount);
            return (r instanceof Boolean b) ? b : false;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean deposit(Player player, String currencyCode, long amount) {
        if (!isHooked() || player == null || currencyCode == null) return false;
        if (amount <= 0) return true;
        try {
            Object r = wsDeposit.invoke(walletService, player.getUniqueId(), currencyCode.trim().toUpperCase(Locale.ROOT), amount);
            // MPC deposit is void in your sources, but tolerate boolean/void.
            return !(r instanceof Boolean) || (Boolean) r;
        } catch (Throwable t) {
            return false;
        }
    }
}
