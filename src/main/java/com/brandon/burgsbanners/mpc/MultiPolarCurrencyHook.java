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
 *
 * The hook is late-resolving:
 * if Burgs & Banners enables before MPC, later calls to isHooked()
 * will retry the connection after MPC becomes available.
 */
public final class MultiPolarCurrencyHook implements MpcHook {

    private static final String MPC_PLUGIN_NAME = "MultiPolarCurrency";
    private static final String MPC_MAIN_CLASS =
            "com.brandon.multipolarcurrency.MultiPolarCurrencyPlugin";

    private final Logger log;

    private Plugin mpcPlugin;
    private Object currencyManager;
    private Object walletService;

    // CurrencyManager methods
    private Method cmExists;
    private Method cmAll;
    private Method currencyCode;

    // WalletService methods
    private Method wsBalance;
    private Method wsWithdraw;
    private Method wsDeposit;
    private Method wsSave;

    private boolean hooked = false;

    public MultiPolarCurrencyHook(Logger log) {
        this.log = (log != null) ? log : Bukkit.getLogger();

        // Try immediately. Failure here is no longer permanent;
        // isHooked() will retry later.
        resolve(true);
    }

    /**
     * Attempts to resolve MPC and its required reflective members.
     *
     * @param logUnavailable whether to log when MPC is not yet enabled
     */
    private synchronized void resolve(boolean logUnavailable) {
        clearResolvedState();

        try {
            this.mpcPlugin =
                    Bukkit.getPluginManager().getPlugin(MPC_PLUGIN_NAME);

            if (mpcPlugin == null || !mpcPlugin.isEnabled()) {
                if (logUnavailable) {
                    log.warning(
                            "[BAB] MPC not present/enabled yet. "
                                    + "Hook will retry when needed."
                    );
                }
                return;
            }

            // Defensive: ensure we are talking to the expected plugin implementation.
            if (!mpcPlugin.getClass().getName().equals(MPC_MAIN_CLASS)) {
                log.warning(
                        "[BAB] MPC plugin class mismatch. Expected "
                                + MPC_MAIN_CLASS
                                + " but found "
                                + mpcPlugin.getClass().getName()
                                + ". Hook may fail."
                );
            }

            // Grab private fields from MPC plugin.
            this.currencyManager =
                    getField(mpcPlugin, "currencyManager");

            this.walletService =
                    getField(mpcPlugin, "walletService");

            if (currencyManager == null || walletService == null) {
                log.warning(
                        "[BAB] Could not resolve MPC currencyManager "
                                + "or walletService via reflection."
                );
                clearResolvedState();
                return;
            }

            // CurrencyManager methods.
            this.cmExists =
                    currencyManager.getClass()
                            .getMethod("exists", String.class);

            this.cmAll =
                    currencyManager.getClass()
                            .getMethod("all");

            // Discover Currency record accessor: code()
            Collection<?> currencies = safeAllCurrencies();

            if (currencies != null && !currencies.isEmpty()) {
                Object sample = currencies.iterator().next();
                this.currencyCode =
                        sample.getClass().getMethod("code");
            } else {
                this.currencyCode = null;
            }

            // WalletService methods.
            this.wsBalance =
                    walletService.getClass()
                            .getMethod(
                                    "balance",
                                    UUID.class,
                                    String.class
                            );

            this.wsWithdraw =
                    walletService.getClass()
                            .getMethod(
                                    "withdraw",
                                    UUID.class,
                                    String.class,
                                    long.class
                            );

            this.wsDeposit =
                    walletService.getClass()
                            .getMethod(
                                    "deposit",
                                    UUID.class,
                                    String.class,
                                    long.class
                            );

            this.wsSave =
                    walletService.getClass()
                            .getMethod("save");

            hooked = true;

            log.info(
                    "[BAB] Hooked into MultiPolarCurrency via reflection "
                            + "(CurrencyManager + WalletService)."
            );

        } catch (Throwable t) {
            clearResolvedState();

            log.warning(
                    "[BAB] Failed to hook into MultiPolarCurrency: "
                            + t.getClass().getSimpleName()
                            + ": "
                            + t.getMessage()
            );
        }
    }

    /**
     * Clears all cached reflective state so a later resolve()
     * starts from a clean slate.
     */
    private void clearResolvedState() {
        hooked = false;

        mpcPlugin = null;
        currencyManager = null;
        walletService = null;

        cmExists = null;
        cmAll = null;
        currencyCode = null;

        wsBalance = null;
        wsWithdraw = null;
        wsDeposit = null;
        wsSave = null;
    }

    private Object getField(Object instance, String fieldName) {
        try {
            Field f =
                    instance.getClass().getDeclaredField(fieldName);

            f.setAccessible(true);
            return f.get(instance);

        } catch (Throwable ignored) {
            return null;
        }
    }

    private Collection<?> safeAllCurrencies() {
        try {
            if (cmAll == null || currencyManager == null) {
                return null;
            }

            Object result =
                    cmAll.invoke(currencyManager);

            if (result instanceof Collection<?> col) {
                return col;
            }

        } catch (Throwable ignored) {
        }

        return null;
    }

    @Override
    public boolean isHooked() {

        // Existing live hook is still valid.
        if (hooked
                && mpcPlugin != null
                && mpcPlugin.isEnabled()) {
            return true;
        }

        /*
         * Late binding:
         *
         * B&B may have started before MPC. Retry now rather than
         * permanently retaining the failed startup state.
         *
         * Do not log "not available" on every retry.
         */
        resolve(false);

        return hooked
                && mpcPlugin != null
                && mpcPlugin.isEnabled();
    }

    @Override
    public boolean currencyExists(String currencyCode) {
        if (!isHooked() || currencyCode == null) {
            return false;
        }

        try {
            return (boolean) cmExists.invoke(
                    currencyManager,
                    currencyCode.trim()
                            .toUpperCase(Locale.ROOT)
            );

        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public List<String> suggestCurrencyCodes(String prefix) {
        if (!isHooked()) {
            return Collections.emptyList();
        }

        String p =
                (prefix == null)
                        ? ""
                        : prefix.trim()
                        .toUpperCase(Locale.ROOT);

        try {
            Collection<?> col = safeAllCurrencies();

            if (col == null || col.isEmpty()) {
                return Collections.emptyList();
            }

            // Resolve Currency.code() if currencies were absent
            // during the original hook.
            if (currencyCode == null) {
                Object sample = col.iterator().next();

                currencyCode =
                        sample.getClass()
                                .getMethod("code");
            }

            List<String> out = new ArrayList<>();

            for (Object c : col) {
                String code =
                        (String) currencyCode.invoke(c);

                if (code != null
                        && code.toUpperCase(Locale.ROOT)
                        .startsWith(p)) {

                    out.add(
                            code.toUpperCase(Locale.ROOT)
                    );
                }
            }

            out.sort(String.CASE_INSENSITIVE_ORDER);

            return out;

        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    /* =========================
       UUID operations
       ========================= */

    @Override
    public long getBalance(
            UUID accountId,
            String currencyCode) {

        if (!isHooked()
                || accountId == null
                || currencyCode == null) {

            return 0L;
        }

        try {
            Object r =
                    wsBalance.invoke(
                            walletService,
                            accountId,
                            currencyCode.trim()
                                    .toUpperCase(Locale.ROOT)
                    );

            if (r instanceof Long l) {
                return l;
            }

            if (r instanceof Number n) {
                return n.longValue();
            }

            return 0L;

        } catch (Throwable t) {
            return 0L;
        }
    }

    @Override
    public boolean withdraw(
            UUID accountId,
            String currencyCode,
            long amount) {

        if (!isHooked()
                || accountId == null
                || currencyCode == null) {

            return false;
        }

        if (amount <= 0) {
            return true;
        }

        try {
            Object r =
                    wsWithdraw.invoke(
                            walletService,
                            accountId,
                            currencyCode.trim()
                                    .toUpperCase(Locale.ROOT),
                            amount
                    );

            boolean ok =
                    (r instanceof Boolean b)
                            ? b
                            : false;

            if (ok) {
                safeSave();
            }

            return ok;

        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean deposit(
            UUID accountId,
            String currencyCode,
            long amount) {

        if (!isHooked()
                || accountId == null
                || currencyCode == null) {

            return false;
        }

        if (amount <= 0) {
            return true;
        }

        try {
            Object r =
                    wsDeposit.invoke(
                            walletService,
                            accountId,
                            currencyCode.trim()
                                    .toUpperCase(Locale.ROOT),
                            amount
                    );

            boolean ok =
                    !(r instanceof Boolean)
                            || (Boolean) r;

            if (ok) {
                safeSave();
            }

            return ok;

        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void touch(
            UUID accountId,
            String currencyCode) {

        getBalance(accountId, currencyCode);
        safeSave();
    }

    private void safeSave() {
        try {
            if (wsSave != null
                    && walletService != null) {

                wsSave.invoke(walletService);
            }

        } catch (Throwable ignored) {
        }
    }

    /* =========================
       Player convenience overrides
       ========================= */

    @Override
    public long getBalance(
            Player player,
            String currencyCode) {

        return (player == null)
                ? 0L
                : getBalance(
                player.getUniqueId(),
                currencyCode
        );
    }

    @Override
    public boolean withdraw(
            Player player,
            String currencyCode,
            long amount) {

        return player != null
                && withdraw(
                player.getUniqueId(),
                currencyCode,
                amount
        );
    }

    @Override
    public boolean deposit(
            Player player,
            String currencyCode,
            long amount) {

        return player != null
                && deposit(
                player.getUniqueId(),
                currencyCode,
                amount
        );
    }
}