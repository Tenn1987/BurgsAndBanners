package com.brandon.burgsbanners.listeners;

import com.brandon.burgsbanners.burg.Burg;
import com.brandon.burgsbanners.burg.BurgManager;
import com.brandon.burgsbanners.burg.ChunkClaim;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BurgTerritoryListener implements Listener {

    private final BurgManager burgManager;

    // Track last burgId (not name) to prevent spam and to survive renames later
    private final Map<UUID, String> lastBurgIdByPlayer = new ConcurrentHashMap<>();

    public BurgTerritoryListener(BurgManager burgManager) {
        this.burgManager = burgManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // Only react when player changes chunk
        if (!changedChunk(from, to)) return;

        World world = to.getWorld();
        if (world == null) return;

        ChunkClaim claim = ChunkClaim.fromChunk(world, to.getChunk());

        Burg owner = burgManager.getBurgByClaim(claim);
        String currentBurgId = (owner == null) ? null : owner.getId();

        String last = lastBurgIdByPlayer.get(player.getUniqueId());
        if (Objects.equals(last, currentBurgId)) return;

        // Update state
        if (currentBurgId == null) lastBurgIdByPlayer.remove(player.getUniqueId());
        else lastBurgIdByPlayer.put(player.getUniqueId(), currentBurgId);

        // Message
        if (owner == null) {
            player.sendActionBar(Component.text("Entering the Wilds", NamedTextColor.DARK_GRAY));
        } else {
            player.sendActionBar(
                    Component.text("Welcome to ", NamedTextColor.GOLD)
                            .append(Component.text(owner.getName(), NamedTextColor.WHITE))
            );
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastBurgIdByPlayer.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        lastBurgIdByPlayer.remove(event.getPlayer().getUniqueId());
    }

    private boolean changedChunk(Location a, Location b) {
        if (a.getWorld() == null || b.getWorld() == null) return true;
        if (!a.getWorld().getUID().equals(b.getWorld().getUID())) return true;

        return (a.getBlockX() >> 4) != (b.getBlockX() >> 4)
                || (a.getBlockZ() >> 4) != (b.getBlockZ() >> 4);
    }
}