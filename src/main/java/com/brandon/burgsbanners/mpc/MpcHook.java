package com.brandon.burgsbanners.mpc;

import org.bukkit.entity.Player;

import java.util.List;

/**
 * Thin adapter over MultiPolarCurrency (MPC).
 *
 * Burgs & Banners depends on MPC for:
 * - verifying currencies exist
 * - reading player wallet balances
 * - withdrawing/depositing during founding (and later taxes/sales)
 *
 * This interface is intentionally minimal and player-focused.
 */
public interface MpcHook {

    /** @return true if MPC is present and required reflective members were resolved. */
    boolean isHooked();

    /** @return true if MPC contains a currency with this code (case-insensitive recommended). */
    boolean currencyExists(String currencyCode);

    /** @return currency code suggestions (e.g., for tab complete). */
    List<String> suggestCurrencyCodes(String prefix);

    /** @return player's MPC wallet balance for the given currency code. */
    long getBalance(Player player, String currencyCode);

    /** Withdraw from player's MPC wallet. @return true on success. */
    boolean withdraw(Player player, String currencyCode, long amount);

    /** Deposit into player's MPC wallet. @return true on success. */
    boolean deposit(Player player, String currencyCode, long amount);
}
