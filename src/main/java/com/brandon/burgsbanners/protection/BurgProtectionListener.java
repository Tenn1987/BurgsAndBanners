package com.brandon.burgsbanners.protection;

import com.brandon.burgsbanners.burg.Burg;
import com.brandon.burgsbanners.burg.BurgManager;
import com.brandon.burgsbanners.burg.plot.Plot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BurgProtectionListener implements Listener {

    private final BurgManager burgManager;

    // simple anti-spam for denial messages
    private final Map<UUID, Long> msgCooldown = new HashMap<>();
    private static final long MSG_COOLDOWN_MS = 1200;

    public BurgProtectionListener(BurgManager burgManager) {
        this.burgManager = burgManager;
    }

    /* ================== CORE CHECK ================== */

    private boolean isLeader(Burg burg, Player p) {
        return burg.getLeaderUuid() != null && burg.getLeaderUuid().equals(p.getUniqueId());
    }

    private Plot findPlotAt(Burg burg, Location loc) {
        if (burg.getPlots() == null || burg.getPlots().isEmpty()) return null;
        for (Plot plot : burg.getPlots().values()) {
            if (plot != null && plot.contains(loc)) return plot;
        }
        return null;
    }

    /**
     * Mayor carves it, owner builds on it:
     * - If inside burg claim but NOT in a plot: only leader
     * - If inside a plot:
     *      - leader always allowed
     *      - owner allowed
     *      - otherwise denied
     */
    private boolean canBuildHere(Player p, Location loc) {
        Burg burg = burgManager.getBurgAt(loc);
        if (burg == null) return true; // wilderness

        if (isLeader(burg, p)) return true;

        Plot plot = findPlotAt(burg, loc);
        if (plot == null) {
            return false; // claimed, but not plotted => leader only
        }

        UUID owner = plot.getOwnerUuid();
        return owner != null && owner.equals(p.getUniqueId());
    }

    private void deny(Player p, String msg) {
        long now = System.currentTimeMillis();
        long last = msgCooldown.getOrDefault(p.getUniqueId(), 0L);
        if (now - last < MSG_COOLDOWN_MS) return;
        msgCooldown.put(p.getUniqueId(), now);
        p.sendMessage(msg);
    }

    /* ================== BREAK / PLACE ================== */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (!canBuildHere(p, e.getBlock().getLocation())) {
            e.setCancelled(true);
            deny(p, "§cProtected land. §7Only the plot owner (or mayor) may build here.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (!canBuildHere(p, e.getBlock().getLocation())) {
            e.setCancelled(true);
            deny(p, "§cProtected land. §7Only the plot owner (or mayor) may build here.");
        }
    }

    /* ================== INTERACT (containers, doors, redstone) ================== */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;

        Player p = e.getPlayer();
        Location loc = e.getClickedBlock().getLocation();
        Burg burg = burgManager.getBurgAt(loc);
        if (burg == null) return; // wilderness

        // We only block meaningful interactions.
        Material type = e.getClickedBlock().getType();

        boolean isContainer =
                (e.getClickedBlock().getState() instanceof InventoryHolder)
                        || type.name().contains("CHEST")
                        || type.name().contains("BARREL")
                        || type.name().contains("SHULKER_BOX");

        boolean isDoorOrGate =
                type.name().contains("DOOR")
                        || type.name().contains("TRAPDOOR")
                        || type.name().contains("FENCE_GATE");

        boolean isRedstone =
                type.name().contains("BUTTON")
                        || type.name().contains("LEVER");

        if (!(isContainer || isDoorOrGate || isRedstone)) return;

        if (isLeader(burg, p)) return;

        Plot plot = findPlotAt(burg, loc);

        // Not in a plot => leader only
        if (plot == null) {
            e.setCancelled(true);
            deny(p, "§cProtected land. §7Only the mayor may interact here.");
            return;
        }

        UUID owner = plot.getOwnerUuid();
        if (owner == null || !owner.equals(p.getUniqueId())) {
            e.setCancelled(true);
            deny(p, "§cProtected plot. §7Only the plot owner (or mayor) may interact here.");
        }
    }

    /* ================== HANGINGS (paintings/item frames) ================== */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        if (!(e.getRemover() instanceof Player p)) return;

        Location loc = e.getEntity().getLocation();
        if (!canBuildHere(p, loc)) {
            e.setCancelled(true);
            deny(p, "§cProtected land. §7Only the plot owner (or mayor) may break that.");
        }
    }

    /* ================== ENTITY DAMAGE (item frames/armor stands, etc) ================== */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;

        Entity victim = e.getEntity();

        // Focus on common grief targets
        boolean protectTarget =
                (victim instanceof ItemFrame)
                        || victim.getType().name().contains("ARMOR_STAND")
                        || (victim instanceof StorageMinecart);

        if (!protectTarget) return;

        Location loc = victim.getLocation();
        if (!canBuildHere(p, loc)) {
            e.setCancelled(true);
            deny(p, "§cProtected land. §7Only the plot owner (or mayor) may damage that.");
        }
    }

    /* ================== FIRE (optional but nice) ================== */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (e.getPlayer() == null) return;
        Player p = e.getPlayer();

        // only restrict inside claims
        Burg burg = burgManager.getBurgAt(e.getBlock().getLocation());
        if (burg == null) return;

        if (!canBuildHere(p, e.getBlock().getLocation())) {
            e.setCancelled(true);
            deny(p, "§cProtected land. §7Fire is restricted here.");
        }
    }
}
