package com.brandon.burgsbanners.mpc;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Level;

public class MultiPolarCurrencyHook implements MpcHook {

    private final JavaPlugin babPlugin;

    private Plugin mpcPlugin;

    // Currency subsystem
    private Object currencyManager;
    private Method existsMethod;   // currencyExists/exists/hasCurrency
    private Method allMethod;      // allCodes/getCurrencies/allCurrencies

    // Wallet/economy subsystem
    private Object walletService;
    private Method balanceMethod;  // getBalance/balance
    private Method withdrawMethod; // withdraw/take/remove

    public MultiPolarCurrencyHook(JavaPlugin babPlugin) {
        this.babPlugin = babPlugin;
        hook();
    }

    private void hook() {
        this.mpcPlugin = Bukkit.getPluginManager().getPlugin("MultiPolarCurrency");

        if (mpcPlugin == null || !mpcPlugin.isEnabled()) {
            babPlugin.getLogger().warning("[B&B] MultiPolarCurrency not found/enabled. Currency checks will fail.");
            return;
        }

        // Try resolve currency manager + wallet service from MPC plugin instance
        Object root = mpcPlugin;

        this.currencyManager = resolveByMethodOrField(root,
                List.of("getCurrencyManager", "currencyManager", "getCurrencies", "currencies"),
                List.of("currencyManager", "currencies", "manager"));

        if (currencyManager != null) {
            Class<?> cm = currencyManager.getClass();

            this.existsMethod = findFirstMethod(cm,
                    List.of("currencyExists", "exists", "hasCurrency", "isValidCurrency"),
                    new Class<?>[]{String.class});

            // "all codes" method candidates: can return Collection<String>, Set<String>, Map, etc.
            this.allMethod = findAnyZeroArgMethod(cm,
                    List.of("allCurrencyCodes", "getCurrencyCodes", "allCodes", "all", "getAll",
                            "currencies", "getCurrencies", "allCurrencies"));
        }

        // Wallet service may live on plugin root or behind a getter
        this.walletService = resolveByMethodOrField(root,
                List.of("getWalletService", "getWalletManager", "getEconomyService", "getAccountService",
                        "walletService", "walletManager", "economyService", "accountService"),
                List.of("walletService", "walletManager", "economyService", "accountService", "wallet", "economy"));

        if (walletService != null) {
            Class<?> ws = walletService.getClass();

            // Balance signatures to try (most likely first)
            this.balanceMethod = findFirstMethod(ws,
                    List.of("getBalance", "balance", "getWalletBalance"),
                    new Class<?>[]{Player.class, String.class});

            if (balanceMethod == null) {
                this.balanceMethod = findFirstMethod(ws,
                        List.of("getBalance", "balance", "getWalletBalance"),
                        new Class<?>[]{UUID.class, String.class});
            }

            if (balanceMethod == null) {
                this.balanceMethod = findFirstMethod(ws,
                        List.of("getBalance", "balance", "getWalletBalance"),
                        new Class<?>[]{String.class, UUID.class}); // some libs flip args
            }

            // Withdraw signatures to try
            this.withdrawMethod = findFirstMethod(ws,
                    List.of("withdraw", "take", "remove", "deduct"),
                    new Class<?>[]{Player.class, String.class, long.class});

            if (withdrawMethod == null) {
                this.withdrawMethod = findFirstMethod(ws,
                        List.of("withdraw", "take", "remove", "deduct"),
                        new Class<?>[]{Player.class, String.class, double.class});
            }

            if (withdrawMethod == null) {
                this.withdrawMethod = findFirstMethod(ws,
                        List.of("withdraw", "take", "remove", "deduct"),
                        new Class<?>[]{UUID.class, String.class, long.class});
            }

            if (withdrawMethod == null) {
                this.withdrawMethod = findFirstMethod(ws,
                        List.of("withdraw", "take", "remove", "deduct"),
                        new Class<?>[]{UUID.class, String.class, double.class});
            }
        }

        babPlugin.getLogger().info("[B&B] MPC hook: "
                + "currencyManager=" + (currencyManager != null)
                + ", exists=" + (existsMethod != null)
                + ", all=" + (allMethod != null)
                + ", wallet=" + (walletService != null)
                + ", balance=" + (balanceMethod != null)
                + ", withdraw=" + (withdrawMethod != null)
        );
    }

    @Override
    public boolean currencyExists(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) return false;
        if (currencyManager == null || existsMethod == null) return false;

        try {
            Object res = existsMethod.invoke(currencyManager, currencyCode.toUpperCase(Locale.ROOT));
            return (res instanceof Boolean b) && b;
        } catch (Exception e) {
            babPlugin.getLogger().log(Level.WARNING, "[B&B] MPC currencyExists() reflection failed", e);
            return false;
        }
    }

    @Override
    public List<String> suggestCurrencyCodes(String prefix) {
        String p = (prefix == null) ? "" : prefix.toUpperCase(Locale.ROOT);

        Set<String> all = new HashSet<>();
        all.addAll(readAllCurrencyCodesSafe());

        if (p.isBlank()) {
            return all.stream().sorted().limit(50).toList();
        }
        return all.stream()
                .filter(code -> code.startsWith(p))
                .sorted()
                .limit(50)
                .toList();
    }

    @Override
    public long getBalance(Player player, String currencyCode) {
        if (player == null || currencyCode == null || currencyCode.isBlank()) return 0L;
        if (walletService == null || balanceMethod == null) return 0L;

        String code = currencyCode.toUpperCase(Locale.ROOT);

        try {
            Object res;

            Class<?>[] params = balanceMethod.getParameterTypes();
            if (params.length == 2 && params[0] == Player.class) {
                res = balanceMethod.invoke(walletService, player, code);
            } else if (params.length == 2 && params[0] == UUID.class) {
                res = balanceMethod.invoke(walletService, player.getUniqueId(), code);
            } else if (params.length == 2 && params[0] == String.class) {
                res = balanceMethod.invoke(walletService, code, player.getUniqueId());
            } else {
                return 0L;
            }

            return toLong(res);
        } catch (Exception e) {
            babPlugin.getLogger().log(Level.WARNING, "[B&B] MPC getBalance() reflection failed", e);
            return 0L;
        }
    }

    @Override
    public boolean withdraw(Player player, String currencyCode, long amount) {
        if (player == null || currencyCode == null || currencyCode.isBlank()) return false;
        if (amount <= 0) return true; // nothing to withdraw
        if (walletService == null || withdrawMethod == null) return false;

        String code = currencyCode.toUpperCase(Locale.ROOT);

        try {
            Object res;

            Class<?>[] params = withdrawMethod.getParameterTypes();

            // Player-based signatures
            if (params.length == 3 && params[0] == Player.class) {
                if (params[2] == long.class) {
                    res = withdrawMethod.invoke(walletService, player, code, amount);
                } else {
                    res = withdrawMethod.invoke(walletService, player, code, (double) amount);
                }
            }
            // UUID-based signatures
            else if (params.length == 3 && params[0] == UUID.class) {
                if (params[2] == long.class) {
                    res = withdrawMethod.invoke(walletService, player.getUniqueId(), code, amount);
                } else {
                    res = withdrawMethod.invoke(walletService, player.getUniqueId(), code, (double) amount);
                }
            } else {
                return false;
            }

            // Some APIs return boolean, others return void, others return a txn object.
            if (res == null) return true;
            if (res instanceof Boolean b) return b;

            // If it returns something else, assume success (best effort).
            return true;

        } catch (Exception e) {
            babPlugin.getLogger().log(Level.WARNING, "[B&B] MPC withdraw() reflection failed", e);
            return false;
        }
    }

    // ---- helpers ----

    private Set<String> readAllCurrencyCodesSafe() {
        if (currencyManager == null || allMethod == null) return Set.of();

        try {
            Object res = allMethod.invoke(currencyManager);
            if (res == null) return Set.of();

            // Common shapes: Collection<String>, Set<String>
            if (res instanceof Collection<?> c) {
                Set<String> out = new HashSet<>();
                for (Object o : c) {
                    if (o != null) out.add(o.toString().toUpperCase(Locale.ROOT));
                }
                return out;
            }

            // Map of code -> currency
            if (res instanceof Map<?, ?> m) {
                Set<String> out = new HashSet<>();
                for (Object k : m.keySet()) {
                    if (k != null) out.add(k.toString().toUpperCase(Locale.ROOT));
                }
                return out;
            }

            // Fallback: single string
            return Set.of(res.toString().toUpperCase(Locale.ROOT));

        } catch (Exception e) {
            babPlugin.getLogger().log(Level.WARNING, "[B&B] MPC currency code list reflection failed", e);
            return Set.of();
        }
    }

    private Object resolveByMethodOrField(Object root, List<String> methodNames, List<String> fieldNames) {
        if (root == null) return null;

        // Try methods first
        for (String m : methodNames) {
            try {
                Method meth = root.getClass().getMethod(m);
                meth.setAccessible(true);
                Object v = meth.invoke(root);
                if (v != null) return v;
            } catch (Exception ignored) {}
        }

        // Try fields next
        for (String f : fieldNames) {
            try {
                Field field = findFieldRecursive(root.getClass(), f);
                if (field == null) continue;
                field.setAccessible(true);
                Object v = field.get(root);
                if (v != null) return v;
            } catch (Exception ignored) {}
        }

        return null;
    }

    private Field findFieldRecursive(Class<?> type, String name) {
        Class<?> c = type;
        while (c != null && c != Object.class) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {}
            c = c.getSuperclass();
        }
        return null;
    }

    private Method findFirstMethod(Class<?> type, List<String> names, Class<?>[] params) {
        for (String n : names) {
            try {
                Method m = type.getMethod(n, params);
                m.setAccessible(true);
                return m;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private Method findAnyZeroArgMethod(Class<?> type, List<String> names) {
        for (String n : names) {
            try {
                Method m = type.getMethod(n);
                m.setAccessible(true);
                return m;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private long toLong(Object res) {
        if (res == null) return 0L;
        if (res instanceof Long l) return l;
        if (res instanceof Integer i) return i.longValue();
        if (res instanceof Double d) return (long) Math.floor(d);
        if (res instanceof Float f) return (long) Math.floor(f);
        if (res instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(res.toString());
        } catch (Exception ignored) {
            return 0L;
        }
    }
}
