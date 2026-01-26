package com.brandon.burgsbanners.commands;

import com.brandon.burgsbanners.burg.Burg;
import com.brandon.burgsbanners.burg.BurgManager;
import com.brandon.burgsbanners.burg.ChunkClaim;
import com.brandon.burgsbanners.burg.food.FoodScanService;
import com.brandon.burgsbanners.burg.plot.Plot;
import com.brandon.burgsbanners.burg.plot.PlotSelection;
import com.brandon.burgsbanners.mpc.MpcHook;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * /burg (alias /city)
 *
 * Focused on:
 * - found (must adopt existing MPC currency; optional initialFunding)
 * - info / treasury
 * - claim / unclaim
 * - leave (only OP can orphan leadership)
 * - plot: pos1/pos2/create/show/list
 */
public class BurgCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final BurgManager burgManager;
    private final MpcHook mpc;
    private final FoodScanService foodScanService;

    // per-player plot selections (no persistence)
    private final Map<UUID, PlotSelection> plotSelections = new HashMap<>();

    private static final List<String> SUBS = List.of(
            "help", "found", "info", "treasury", "claim", "unclaim", "leave", "plot"
    );

    public BurgCommand(JavaPlugin plugin, BurgManager burgManager, MpcHook mpc, FoodScanService foodScanService) {
        this.plugin = plugin;
        this.burgManager = burgManager;
        this.mpc = mpc;
        this.foodScanService = foodScanService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            help(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "help" -> { help(sender, label); return true; }
            case "found" -> { return handleFound(sender, label, args); }
            case "info" -> { return handleInfo(sender, label, args); }
            case "treasury" -> { return handleTreasury(sender); }
            case "claim" -> { return handleClaim(sender); }
            case "unclaim" -> { return handleUnclaim(sender); }
            case "leave" -> { return handleLeave(sender); }
            case "plot" -> { return handlePlot(sender, label, args); }
            default -> {
                sender.sendMessage(c("&cUnknown subcommand. Try &f/" + label + " help"));
                return true;
            }
        }
    }

    private boolean handleInfo(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(c("&cOnly players can use this command."));
            return true;
        }

        // NOTE: currently "info" is self-only (no player IDs yet)
        Burg burg = burgManager.getBurgByMember(player.getUniqueId());
        if (burg == null) {
            sender.sendMessage(c("&cYou are not in a burg."));
            return true;
        }

        sender.sendMessage(c("&6== &eBurg Info &6=="));
        sender.sendMessage(c("&7Name: &f" + burg.getName()));
        sender.sendMessage(c("&7Stage: &f" + burg.getPolityStage()));
        sender.sendMessage(c("&7Ruler: &f" + burg.getLeaderUuid() + " &7(&f" + burg.getRulerTitle() + "&7)"));
        sender.sendMessage(c("&7Currency: &f" + burg.getAdoptedCurrencyCode()));
        sender.sendMessage(c("&7Claims: &f" + burg.getClaimCount()));
        sender.sendMessage(c("&7Population: &f" + burg.getTotalPopulation() + " &7(abstract)"));
        sender.sendMessage(c("&7Food Capacity: &f" + String.format(Locale.US, "%.2f", burg.getBaseFoodCapacity())));

        if (mpc != null && mpc.isHooked()) {
            long bal = mpc.getBalance(player, burg.getAdoptedCurrencyCode());
            sender.sendMessage(c("&7Your wallet (" + burg.getAdoptedCurrencyCode() + "): &f" + bal));
        } else {
            sender.sendMessage(c("&cMultiPolarCurrency is not hooked/ready."));
        }
        return true;
    }

    private boolean handleTreasury(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(c("&cOnly players can use this command."));
            return true;
        }
        Burg burg = burgManager.getBurgByMember(player.getUniqueId());
        if (burg == null) {
            sender.sendMessage(c("&cYou are not in a burg."));
            return true;
        }

        sender.sendMessage(c("&6== &eTreasury &6=="));
        if (burg.getTreasuryBalances().isEmpty()) {
            sender.sendMessage(c("&7(empty)"));
        } else {
            burg.getTreasuryBalances().forEach((code, bal) ->
                    sender.sendMessage(c("&7" + code + ": &f" + bal))
            );
        }
        return true;
    }

    private boolean handleClaim(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(c("&cOnly players can use this command."));
            return true;
        }
        Burg burg = burgManager.getBurgByMember(player.getUniqueId());
        if (burg == null) {
            sender.sendMessage(c("&cYou are not in a burg."));
            return true;
        }
        ChunkClaim claim = ChunkClaim.fromChunk(player.getWorld(), player.getLocation().getChunk());
        if (burgManager.tryAddClaim(burg, claim)) {
            sender.sendMessage(c("&aClaimed chunk: &f" + claim));
        } else {
            sender.sendMessage(c("&cCannot claim here. Already claimed."));
        }
        return true;
    }

    private boolean handleUnclaim(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(c("&cOnly players can use this command."));
            return true;
        }
        Burg burg = burgManager.getBurgByMember(player.getUniqueId());
        if (burg == null) {
            sender.sendMessage(c("&cYou are not in a burg."));
            return true;
        }
        ChunkClaim claim = ChunkClaim.fromChunk(player.getWorld(), player.getLocation().getChunk());
        if (burgManager.tryRemoveClaim(burg, claim)) {
            sender.sendMessage(c("&aUnclaimed chunk: &f" + claim));
        } else {
            sender.sendMessage(c("&cCannot unclaim here."));
        }
        return true;
    }

    private boolean handleFound(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(c("&cOnly players can use this command."));
            return true;
        }

        if (!player.hasPermission("burg.found") && !player.hasPermission("burg.*")) {
            sender.sendMessage(c("&cYou lack permission: &fburg.found"));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(c("&cUsage: &f/" + label + " found <name> <currencyCode>"));
            return true;
        }

        // Must not already be in a burg
        Burg existing = burgManager.getBurgByMember(player.getUniqueId());
        if (existing != null) {
            sender.sendMessage(c("&cYou are already in a burg: &f" + existing.getName()));
            return true;
        }

        // Validate name (3-24)
        String name = args[1].trim();
        if (name.length() < 3 || name.length() > 24) {
            sender.sendMessage(c("&cBurg name must be 3–24 characters."));
            return true;
        }
        if (burgManager.burgExists(name)) {
            sender.sendMessage(c("&cThat burg name is already taken."));
            return true;
        }

        String currencyCode = args[2].trim().toUpperCase(Locale.ROOT);

        // Validate MPC is hooked + currency exists
        if (mpc == null || !mpc.isHooked()) {
            sender.sendMessage(c("&cMultiPolarCurrency is not hooked/ready. Cannot found right now."));
            return true;
        }
        if (!mpc.currencyExists(currencyCode)) {
            sender.sendMessage(c("&cCurrency does not exist in MultiPolarCurrency: &f" + currencyCode));
            List<String> all = mpc.suggestCurrencyCodes("");
            if (!all.isEmpty()) sender.sendMessage(c("&7Available currencies: &f" + String.join("&7, &f", all)));
            return true;
        }

        // Founding funding (deducted from founder's MPC wallet)
        long initialFunding = plugin.getConfig().getLong("founding.initialFunding", 0L);
        if (initialFunding < 0) initialFunding = 0;

        if (initialFunding > 0) {
            long bal = mpc.getBalance(player, currencyCode);
            if (bal < initialFunding) {
                sender.sendMessage(c("&cFounding requires initial funding of &f" + initialFunding + " " + currencyCode));
                sender.sendMessage(c("&7Your wallet: &f" + bal + " " + currencyCode));
                return true;
            }
        }

        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            sender.sendMessage(c("&cWorld is null; cannot found here."));
            return true;
        }

        // Must not be inside ANY existing claim
        Chunk currentChunk = loc.getChunk();
        ChunkClaim here = ChunkClaim.fromChunk(world, currentChunk);
        if (burgManager.isClaimed(here)) {
            sender.sendMessage(c("&cYou are standing inside an existing burg's claimed territory."));
            return true;
        }

        // Starter NxN chunk claim (radius 1 => 3x3)
        int starterRadius = plugin.getConfig().getInt("founding.starterChunkRadius", 1);
        if (starterRadius < 0) starterRadius = 0;

        Set<ChunkClaim> starterClaims = new HashSet<>();
        for (int dx = -starterRadius; dx <= starterRadius; dx++) {
            for (int dz = -starterRadius; dz <= starterRadius; dz++) {
                Chunk c = world.getChunkAt(currentChunk.getX() + dx, currentChunk.getZ() + dz);
                ChunkClaim cc = ChunkClaim.fromChunk(world, c);

                if (burgManager.isClaimed(cc)) {
                    sender.sendMessage(c("&cStarter claim overlaps another burg. Move farther and try again."));
                    return true;
                }
                starterClaims.add(cc);
            }
        }

        // Create burg
        Burg burg = burgManager.createBurgFounding(
                name,
                player.getUniqueId(),
                world,
                loc,
                currencyCode,
                starterClaims
        );

        // Deduct + credit treasury
        if (initialFunding > 0) {
            boolean ok = mpc.withdraw(player, currencyCode, initialFunding);
            if (!ok) {
                sender.sendMessage(c("&cPayment failed. Burg was not founded."));
                plugin.getLogger().warning("Founding funding withdraw failed for " + player.getName()
                        + " amount=" + initialFunding + " " + currencyCode);
                // pre-alpha: no delete/rollback helper; OP can wipe burgs.yml if needed
                return true;
            }
            burg.getTreasuryBalances().merge(currencyCode, initialFunding, Long::sum);
        }

        // Food scan
        sender.sendMessage(c("&7Scanning starter territory for food capacity..."));
        FoodScanService.ScanResult scan = foodScanService.scanBurgClaims(burg, world);
        burg.setBaseFoodCapacity(scan.baseFoodCapacity());
        burg.setLastFoodPoints(scan.totalFoodPoints());
        burg.setLastScanEpochSeconds(System.currentTimeMillis() / 1000L);

        burgManager.save(burg);

        sender.sendMessage(c("&aBurg founded: &f" + burg.getName()));
        sender.sendMessage(c("&7Leader: &f" + player.getName() + " &7(&fLord-Mayor&7)"));
        sender.sendMessage(c("&7Currency adopted: &f" + currencyCode));
        if (initialFunding > 0) sender.sendMessage(c("&7Founding funded: &f" + initialFunding + " " + currencyCode));
        sender.sendMessage(c("&7Starter claims: &f" + starterClaims.size() + " &7chunks"));
        sender.sendMessage(c("&7Food Capacity (BFC): &f" + String.format(Locale.US, "%.2f", burg.getBaseFoodCapacity())));
        sender.sendMessage(c("&7Try: &f/" + label + " info"));
        return true;
    }

    private boolean handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(c("&cOnly players can use this command."));
            return true;
        }

        Burg burg = burgManager.getBurgByMember(player.getUniqueId());
        if (burg == null) {
            sender.sendMessage(c("&cYou are not in a burg."));
            return true;
        }

        UUID leader = burg.getLeaderUuid();
        boolean isLeader = leader != null && leader.equals(player.getUniqueId());

        if (isLeader && !player.isOp()) {
            sender.sendMessage(c("&cYou are the Lord-Mayor of &f" + burg.getName() + "&c."));
            sender.sendMessage(c("&7You cannot leave while ruling. (Only OP can orphan leadership during testing.)"));
            return true;
        }

        boolean removed = burg.getMembers().remove(player.getUniqueId());
        if (!removed) {
            sender.sendMessage(c("&cYou could not be removed (already not a member?)."));
            return true;
        }

        burgManager.onMemberLeft(burg, player.getUniqueId());

        if (isLeader) {
            sender.sendMessage(c("&eYou have left &f" + burg.getName() + "&e as OP."));
            sender.sendMessage(c("&cWarning: this burg is now orphaned (leader left)."));
        } else {
            sender.sendMessage(c("&aYou have left the burg: &f" + burg.getName()));
        }
        return true;
    }

    private boolean handlePlot(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(c("&cOnly players can use this command."));
            return true;
        }

        Burg burg = burgManager.getBurgByMember(player.getUniqueId());
        if (burg == null) {
            sender.sendMessage(c("&cYou are not in a burg."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(c("&cUsage: &f/" + label + " plot <pos1|pos2|create|show|list> ..."));
            return true;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "pos1" -> {
                Block b = player.getTargetBlockExact(200);
                if (b == null) {
                    sender.sendMessage(c("&cLook at a block within 200 blocks."));
                    return true;
                }
                PlotSelection sel = plotSelections.computeIfAbsent(player.getUniqueId(), k -> new PlotSelection());
                sel.setPos1(b.getLocation());
                sender.sendMessage(c("&aPlot pos1 set: &f" + fmtLoc(b.getLocation())));
                return true;
            }
            case "pos2" -> {
                Block b = player.getTargetBlockExact(200);
                if (b == null) {
                    sender.sendMessage(c("&cLook at a block within 200 blocks."));
                    return true;
                }
                PlotSelection sel = plotSelections.computeIfAbsent(player.getUniqueId(), k -> new PlotSelection());
                sel.setPos2(b.getLocation());
                sender.sendMessage(c("&aPlot pos2 set: &f" + fmtLoc(b.getLocation())));
                return true;
            }
            case "list" -> {
                if (burg.getPlots().isEmpty()) {
                    sender.sendMessage(c("&7No plots yet."));
                    return true;
                }
                sender.sendMessage(c("&6== &ePlots in " + burg.getName() + " &6=="));
                burg.getPlots().values().stream()
                        .sorted(Comparator.comparing(Plot::getId, String.CASE_INSENSITIVE_ORDER))
                        .forEach(p -> sender.sendMessage(c("&7- &f" + p.getId() + " &7(" + p.getName() + ")")));
                return true;
            }
            case "create" -> {
                if (args.length < 3) {
                    sender.sendMessage(c("&cUsage: &f/" + label + " plot create <id> [displayName...]"));
                    return true;
                }

                String id = args[2].trim().toLowerCase(Locale.ROOT);
                if (!id.matches("[a-z0-9_-]{2,24}")) {
                    sender.sendMessage(c("&cPlot id must be 2–24 chars: a-z, 0-9, _, -"));
                    return true;
                }
                if (burg.getPlot(id) != null) {
                    sender.sendMessage(c("&cThat plot id already exists."));
                    return true;
                }

                PlotSelection sel = plotSelections.get(player.getUniqueId());
                Location p1 = (sel == null) ? null : sel.getPos1();
                Location p2 = (sel == null) ? null : sel.getPos2();
                if (p1 == null || p2 == null) {
                    sender.sendMessage(c("&cSet pos1 and pos2 first: &f/" + label + " plot pos1 &7and &f/" + label + " plot pos2"));
                    return true;
                }

                World w1 = p1.getWorld();
                World w2 = p2.getWorld();
                if (w1 == null || w2 == null || !w1.getUID().equals(w2.getUID())) {
                    sender.sendMessage(c("&cpos1 and pos2 must be in the same world."));
                    return true;
                }

                // Must be inside your burg claims
                if (!isInsideClaims(burg, p1) || !isInsideClaims(burg, p2)) {
                    sender.sendMessage(c("&cBoth corners must be inside your burg's claimed chunks."));
                    return true;
                }

                String displayName = (args.length >= 4)
                        ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)).trim()
                        : id;

                int minX = Math.min(p1.getBlockX(), p2.getBlockX());
                int maxX = Math.max(p1.getBlockX(), p2.getBlockX());
                int minY = Math.min(p1.getBlockY(), p2.getBlockY());
                int maxY = Math.max(p1.getBlockY(), p2.getBlockY());
                int minZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
                int maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ());

                Plot plot = new Plot(
                        id,
                        displayName,
                        w1.getUID(),
                        minX, minY, minZ,
                        maxX, maxY, maxZ
                );

                // No overlaps within burg (simple AABB in same world)
                for (Plot existing : burg.getPlots().values()) {
                    if (overlaps(existing, plot)) {
                        sender.sendMessage(c("&cThis plot overlaps existing plot: &f" + existing.getId()));
                        return true;
                    }
                }

                burg.putPlot(plot);
                burgManager.save(burg);

                sender.sendMessage(c("&aCreated plot &f" + plot.getId() + "&a (" + plot.getName() + ")"));
                sender.sendMessage(c("&7Use &f/" + label + " plot show " + plot.getId() + " &7to visualize."));
                return true;
            }
            case "show" -> {
                if (args.length < 3) {
                    sender.sendMessage(c("&cUsage: &f/" + label + " plot show <id>"));
                    return true;
                }
                String id = args[2].trim().toLowerCase(Locale.ROOT);
                Plot plot = burg.getPlot(id);
                if (plot == null) {
                    sender.sendMessage(c("&cPlot not found: &f" + id));
                    return true;
                }
                World w = Bukkit.getWorld(plot.getWorldId());
                if (w == null) {
                    sender.sendMessage(c("&cPlot world not loaded."));
                    return true;
                }
                showPlotParticles(player, plot, w);
                sender.sendMessage(c("&aShowing plot borders for &f" + plot.getId() + "&a for 8 seconds."));
                return true;
            }

            default -> {
                sender.sendMessage(c("&cUnknown plot subcommand. Use: pos1, pos2, create, show, list"));
                return true;
            }
        }
    }

    // -------- plot helpers --------

    private boolean isInsideClaims(Burg burg, Location loc) {
        World w = loc.getWorld();
        if (w == null) return false;
        ChunkClaim cc = ChunkClaim.fromChunk(w, loc.getChunk());
        return burg.hasClaim(cc);
    }

    /** AABB overlap test in same world. */
    private boolean overlaps(Plot a, Plot b) {
        if (a == null || b == null) return false;
        if (a.getWorldId() == null || b.getWorldId() == null) return false;
        if (!a.getWorldId().equals(b.getWorldId())) return false;

        boolean x = a.getMinX() <= b.getMaxX() && a.getMaxX() >= b.getMinX();
        boolean y = a.getMinY() <= b.getMaxY() && a.getMaxY() >= b.getMinY();
        boolean z = a.getMinZ() <= b.getMaxZ() && a.getMaxZ() >= b.getMinZ();
        return x && y && z;
    }

    private void showPlotParticles(Player viewer, Plot plot, World world) {
        int seconds = 8;
        int periodTicks = 10; // 0.5s
        int runs = (seconds * 20) / periodTicks;

        int minX = plot.getMinX();
        int maxX = plot.getMaxX();
        int minY = plot.getMinY();
        int maxY = plot.getMaxY();
        int minZ = plot.getMinZ();
        int maxZ = plot.getMaxZ();

        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!viewer.isOnline()) { cancel(); return; }
                if (n++ >= runs) { cancel(); return; }

                int y = clamp(viewer.getLocation().getBlockY(), minY, maxY);

                // edges along X at z=minZ and z=maxZ
                for (int x = minX; x <= maxX; x += 2) {
                    world.spawnParticle(Particle.HAPPY_VILLAGER, x + 0.5, y + 1.1, minZ + 0.5, 1, 0, 0, 0, 0);
                    world.spawnParticle(Particle.HAPPY_VILLAGER, x + 0.5, y + 1.1, maxZ + 0.5, 1, 0, 0, 0, 0);
                }
                // edges along Z at x=minX and x=maxX
                for (int z = minZ; z <= maxZ; z += 2) {
                    world.spawnParticle(Particle.HAPPY_VILLAGER, minX + 0.5, y + 1.1, z + 0.5, 1, 0, 0, 0, 0);
                    world.spawnParticle(Particle.HAPPY_VILLAGER, maxX + 0.5, y + 1.1, z + 0.5, 1, 0, 0, 0, 0);
                }
            }
        }.runTaskTimer(plugin, 0L, periodTicks);
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private String fmtLoc(Location l) {
        if (l == null || l.getWorld() == null) return "(null)";
        return l.getWorld().getName() + " " + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage(c("&6== &eBurgs & Banners &6=="));
        sender.sendMessage(c("&f/" + label + " found <name> <currencyCode> &7- Found a new burg"));
        sender.sendMessage(c("&f/" + label + " info &7- View your burg"));
        sender.sendMessage(c("&f/" + label + " treasury &7- View treasury balances"));
        sender.sendMessage(c("&f/" + label + " claim &7- Claim current chunk"));
        sender.sendMessage(c("&f/" + label + " unclaim &7- Unclaim current chunk"));
        sender.sendMessage(c("&f/" + label + " leave &7- Leave your burg"));
        sender.sendMessage(c("&f/" + label + " plot <pos1|pos2|create|show|list> &7- Manage plots"));
    }

    private String c(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            return SUBS.stream().filter(s -> s.startsWith(p)).collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("plot")) {
            List<String> subs = List.of("pos1","pos2","create","show","list");
            String p = args[1].toLowerCase(Locale.ROOT);
            return subs.stream().filter(s -> s.startsWith(p)).collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("found")) {
            return (mpc != null) ? mpc.suggestCurrencyCodes(args[2]) : Collections.emptyList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("plot") && args[1].equalsIgnoreCase("show")) {
            if (!(sender instanceof Player player)) return Collections.emptyList();
            Burg burg = burgManager.getBurgByMember(player.getUniqueId());
            if (burg == null) return Collections.emptyList();
            String p = args[2].toLowerCase(Locale.ROOT);
            return burg.getPlots().keySet().stream()
                    .filter(k -> k.toLowerCase(Locale.ROOT).startsWith(p))
                    .sorted()
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
