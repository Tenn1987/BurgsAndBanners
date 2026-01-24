package com.brandon.burgsbanners.mpc;

import org.bukkit.entity.Player;

import java.util.List;

public interface MpcHook {

    boolean currencyExists(String currencyCode);

    List<String> suggestCurrencyCodes(String prefix);

    /**
     * Player wallet balance in integer units for the given currency.
     * (Use long to match your Burg treasury map type.)
     */
    long getBalance(Player player, String currencyCode);

    /**
     * Withdraw amount from player's wallet in integer units.
     * Returns true if successful.
     */
    boolean withdraw(Player player, String currencyCode, long amount);
}
