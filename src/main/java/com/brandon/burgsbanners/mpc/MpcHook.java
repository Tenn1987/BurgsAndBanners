package com.brandon.burgsbanners.mpc;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Thin adapter over MultiPolarCurrency (MPC).
 *
 * MPC is the single source of truth for money. Burg treasuries are MPC wallets keyed by UUID.
 */
public interface MpcHook {

    /** @return true if MPC is present and required reflective members were resolved. */
    boolean isHooked();

    /** @return true if MPC contains a currency with this code (case-insensitive recommended). */
    boolean currencyExists(String currencyCode);

    /** @return currency code suggestions (e.g., for tab complete). */
    List<String> suggestCurrencyCodes(String prefix);

    /* =========================
       UUID-first operations
       ========================= */

    /** @return MPC wallet balance for the given UUID and currency code. */
    long getBalance(UUID accountId, String currencyCode);

    /** Withdraw from UUID wallet. @return true on success (fails if insufficient). */
    boolean withdraw(UUID accountId, String currencyCode, long amount);

    /** Deposit into UUID wallet. @return true on success. */
    boolean deposit(UUID accountId, String currencyCode, long amount);

    /**
     * Ensure a wallet entry exists for (UUID, currency). MPC is lazy, so this "touches" the wallet.
     * Implementations should call balance(accountId, code) and (optionally) save().
     */
    default void touch(UUID accountId, String currencyCode) {
        getBalance(accountId, currencyCode);
    }

    /* =========================
       Player convenience overloads
       ========================= */

    default long getBalance(Player player, String currencyCode) {
        return (player == null) ? 0L : getBalance(player.getUniqueId(), currencyCode);
    }

    default boolean withdraw(Player player, String currencyCode, long amount) {
        return player != null && withdraw(player.getUniqueId(), currencyCode, amount);
    }

    default boolean deposit(Player player, String currencyCode, long amount) {
        return player != null && deposit(player.getUniqueId(), currencyCode, amount);
    }
}
