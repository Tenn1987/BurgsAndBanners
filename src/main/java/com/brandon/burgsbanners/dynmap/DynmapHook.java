package com.brandon.burgsbanners.dynmap;

import com.brandon.burgsbanners.burg.Burg;
import com.brandon.burgsbanners.burg.BurgManager;
import com.brandon.burgsbanners.burg.ChunkClaim;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Dynmap hook using reflection ONLY.
 * - No compile-time dependency on Dynmap.
 * - No changes required to BurgManager (reads burg list via reflection).
 *
 * Safely degrades: if Dynmap isn't present or any call fails, nothing breaks.
 */
public class DynmapHook {

    private final JavaPlugin plugin;
    private final BurgManager burgManager;

    private Object dynmapApi;
    private Object markerApi;
    private Object markerSet;

    private final Map<String, Object> burgMarkers = new HashMap<>();

    public DynmapHook(JavaPlugin plugin, BurgManager burgManager) {
        this.plugin = plugin;
        this.burgManager = burgManager;
    }

    /* =====================
       Lifecycle
       ===================== */

    public boolean hook() {
        try {
            Plugin dynmapPlugin = Bukkit.getPluginManager().getPlugin("dynmap");
            if (dynmapPlugin == null) {
                plugin.getLogger().info("[BAB] Dynmap not present, skipping hook.");
                return false;
            }

            // DynmapAPI api = ((DynmapCommonAPI)dynmapPlugin).getAPI();
            dynmapApi = invoke(dynmapPlugin, "getAPI");
            if (dynmapApi == null) {
                plugin.getLogger().warning("[BAB] Dynmap API unavailable.");
                return false;
            }

            markerApi = invoke(dynmapApi, "getMarkerAPI");
            if (markerApi == null) {
                plugin.getLogger().warning("[BAB] Dynmap MarkerAPI unavailable.");
                return false;
            }

            markerSet = invoke(markerApi, "getMarkerSet", "burgs");
            if (markerSet == null) {
                markerSet = invoke(markerApi, "createMarkerSet", "burgs", "Burgs & Banners", null, false);
            }
            if (markerSet == null) {
                plugin.getLogger().warning("[BAB] Dynmap MarkerSet could not be created.");
                return false;
            }

            invoke(markerSet, "setLayerPriority", 10);
            invoke(markerSet, "setHideByDefault", false);

            redrawAll();
            plugin.getLogger().info("[BAB] Dynmap hook enabled.");
            return true;

        } catch (Throwable t) {
            plugin.getLogger().warning("[BAB] Dynmap hook failed safely: " + t.getMessage());
            return false;
        }
    }

    public void shutdown() {
        try {
            for (Object marker : burgMarkers.values()) {
                invoke(marker, "deleteMarker");
            }
        } catch (Throwable ignored) {
        } finally {
            burgMarkers.clear();
        }
    }

    /* =====================
       Drawing
       ===================== */

    public void redrawAll() {
        try {
            shutdown();
            for (Burg burg : getAllBurgsSafely()) {
                drawBurg(burg);
            }
        } catch (Throwable ignored) {
        }
    }

    public void drawBurg(Burg burg) {
        try {
            if (burg == null) return;
            Set<ChunkClaim> claims = burg.getClaims();
            if (claims == null || claims.isEmpty()) return;

            World world = Bukkit.getWorld(burg.getWorldId());
            if (world == null) return;

            // NOTE: This is NOT a true polygon union of chunks.
            // It's a simple "lots of rectangles" area marker which is good enough for pre-alpha visualization.
            double[] x = new double[claims.size() * 4];
            double[] z = new double[claims.size() * 4];

            int i = 0;
            for (ChunkClaim c : claims) {
                int cx = c.getX() << 4;
                int cz = c.getZ() << 4;

                x[i] = cx;       z[i++] = cz;
                x[i] = cx + 16;  z[i++] = cz;
                x[i] = cx + 16;  z[i++] = cz + 16;
                x[i] = cx;       z[i++] = cz + 16;
            }

            String markerId = "burg_" + burg.getId();

            Object marker = invoke(
                    markerSet,
                    "createAreaMarker",
                    markerId,
                    burg.getName(),
                    false,
                    world.getName(),
                    x,
                    z,
                    false
            );
            if (marker == null) return;

            // Style (hardcoded for now)
            invoke(marker, "setLineStyle", 2, 0.8, 0x00AAFF);
            invoke(marker, "setFillStyle", 0.25, 0x00AAFF);

            String desc =
                    "<b>" + esc(burg.getName()) + "</b><br/>" +
                    "Ruler: " + esc(burg.getRulerTitle()) + "<br/>" +
                    "Currency: " + esc(burg.getAdoptedCurrencyCode()) + "<br/>" +
                    "Claims: " + claims.size();
            invoke(marker, "setDescription", desc);

            burgMarkers.put(burg.getId(), marker);

        } catch (Throwable ignored) {
        }
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /* =====================
       Burg enumeration (no BurgManager changes required)
       ===================== */

    @SuppressWarnings("unchecked")
    private Collection<Burg> getAllBurgsSafely() {
        try {
            Field f = BurgManager.class.getDeclaredField("burgsById");
            f.setAccessible(true);
            Object val = f.get(burgManager);
            if (val instanceof Map<?, ?> map) {
                List<Burg> out = new ArrayList<>();
                for (Object o : map.values()) {
                    if (o instanceof Burg b) out.add(b);
                }
                return out;
            }
        } catch (Throwable ignored) {
        }
        return Collections.emptyList();
    }

    /* =====================
       Reflection helper (primitive-friendly)
       ===================== */

    private Object invoke(Object target, String methodName, Object... args) {
        if (target == null) return null;
        try {
            Method m = findCompatibleMethod(target.getClass(), methodName, args);
            if (m == null) return null;
            m.setAccessible(true);
            return m.invoke(target, args);
        } catch (Throwable t) {
            // keep quiet; this hook must never break the plugin
            return null;
        }
    }

    private Method findCompatibleMethod(Class<?> cls, String name, Object[] args) {
        Method[] methods = cls.getMethods(); // public methods only (Dynmap API is public)
        outer:
        for (Method m : methods) {
            if (!m.getName().equals(name)) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length != (args == null ? 0 : args.length)) continue;

            for (int i = 0; i < p.length; i++) {
                Object a = args[i];
                if (a == null) {
                    // null can't go into primitive params
                    if (p[i].isPrimitive()) continue outer;
                    continue;
                }
                Class<?> at = a.getClass();
                if (p[i].isPrimitive()) {
                    if (!isWrapperForPrimitive(at, p[i])) continue outer;
                } else {
                    if (!p[i].isAssignableFrom(at)) continue outer;
                }
            }
            return m;
        }
        return null;
    }

    private boolean isWrapperForPrimitive(Class<?> wrapper, Class<?> primitive) {
        return (primitive == boolean.class && wrapper == Boolean.class)
                || (primitive == int.class && wrapper == Integer.class)
                || (primitive == long.class && wrapper == Long.class)
                || (primitive == double.class && wrapper == Double.class)
                || (primitive == float.class && wrapper == Float.class)
                || (primitive == short.class && wrapper == Short.class)
                || (primitive == byte.class && wrapper == Byte.class)
                || (primitive == char.class && wrapper == Character.class);
    }
}
