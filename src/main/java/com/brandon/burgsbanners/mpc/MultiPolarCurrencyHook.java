package com.brandon.burgsbanners.mpc;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class MultiPolarCurrencyHook implements MpcHook {

    private final Plugin babPlugin;

    private Plugin mpcPlugin;
    private Object currencyManager;
    private Method existsMethod;
    private Method allMethod;

    public MultiPolarCurrencyHook(Plugin babPlugin) {
        this.babPlugin = babPlugin;
        tryInit();
    }

    private void tryInit() {
        this.mpcPlugin = Bukkit.getPluginManager().getPlugin("MultiPolarCurrency");
        if (mpcPlugin == null || !mpcPlugin.isEnabled()) {
            babPlugin.getLogger().warning("MultiPolarCurrency not found/enabled; currency checks will fail.");
            return;
        }

        try {
            Field f = mpcPlugin.getClass().getDeclaredField("currencyManager");
            f.setAccessible(true);
            this.currencyManager = f.get(mpcPlugin);

            this.existsMethod = currencyManager.getClass().getMethod("exists", String.class);
            this.allMethod = currencyManager.getClass().getMethod("all");

            babPlugin.getLogger().info("Hooked MultiPolarCurrency CurrencyManager successfully.");
        } catch (Exception e) {
            babPlugin.getLogger().severe("Failed to hook MultiPolarCurrency: " + e.getMessage());
            this.currencyManager = null;
            this.existsMethod = null;
            this.allMethod = null;
        }
    }

    @Override
    public boolean currencyExists(String currencyCode) {
        if (currencyManager == null || existsMethod == null) tryInit();
        if (currencyManager == null || existsMethod == null) return false;

        try {
            String code = currencyCode.toUpperCase(Locale.ROOT);
            return (boolean) existsMethod.invoke(currencyManager, code);
        } catch (Exception e) {
            babPlugin.getLogger().warning("currencyExists failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public List<String> suggestCurrencyCodes(String prefix) {
        if (currencyManager == null || allMethod == null) tryInit();
        if (currencyManager == null || allMethod == null) return Collections.emptyList();

        String p = (prefix == null) ? "" : prefix.toUpperCase(Locale.ROOT);

        try {
            @SuppressWarnings("unchecked")
            var all = (java.util.Collection<Object>) allMethod.invoke(currencyManager);

            return all.stream()
                    .map(cur -> {
                        try {
                            return (String) cur.getClass().getMethod("code").invoke(cur);
                        } catch (Exception ignored) { return null; }
                    })
                    .filter(s -> s != null && s.startsWith(p))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            babPlugin.getLogger().warning("suggestCurrencyCodes failed: " + e.getMessage());
            return Collections.emptyList();
        }
    }
}
