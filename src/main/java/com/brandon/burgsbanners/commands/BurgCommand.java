package com.brandon.burgsbanners.commands;

import com.brandon.burgsbanners.burg.Burg;
import com.brandon.burgsbanners.burg.BurgManager;
import com.brandon.burgsbanners.burg.ChunkClaim;
import com.brandon.burgsbanners.burg.food.FoodScanService;
import com.brandon.burgsbanners.burg.plot.Plot;
import com.brandon.burgsbanners.burg.plot.PlotSelection;
import com.brandon.burgsbanners.mpc.MpcHook;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.stream.Collectors;

public class BurgCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final BurgManager burgManager;
    private final FoodScanService foodScanService;
    private final MpcHook mpc;

    // per-player plot selections (no persistence)
    private final Map<UUID, PlotSelection> plotSelections = new HashMap<>();

    private static final List<String> SUBS = List.of(
            "found", "info", "treasury", "claim", "unclaim", "join", "leave", "abdicate", "plot"
    );

    public BurgCommand(JavaPlugin plugin, BurgManager burgManager, FoodScanService foodScanService, MpcHook mpc) {
        this.plugin = plugin;
        this.burgManager = burgManager;
        this.foodScanService = foodScanService;
        this.mpc = mpc;
    }

    private String c(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = (args.length == 0) ? "" : args[0].toLowerCase(Locale.ROOT);

        return switch (sub) {
            case "found" -> handleFound(sender, label, args);
            case "info" -> handleInfo(sender);
            case "treasury" -> handleTreasury(sender);
            case "claim" -> handleClaim(sender);
            case "unclaim" -> handleUnclaim(sender);
            case "join" -> handleJoin(sender, label, args);
            case "leave" -> handleLeave(sender);
            case "abdicate" -> handleAbdicate(sender);
            case "plot" -> handlePlot(sender, label, args);
            default -> {
                help(sender, label);
                yield true;
            }
        };
    }

    /* ================= TAB COMPLETE ================= */

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            return SUBS.stream().filter(s -> s.startsWith(p)).collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("plot")) {
            List<String> subs = List.of("pos1", "pos2", "create", "show", "list");
            String p = args[1].toLowerCase(Locale.ROOT);
            return subs.stream().filter(s -> s.startsWith(p)).collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("found") && mpc != null && mpc.isHooked()) {
            return mpc.suggestCurrencyCodes(args[2]);
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

    /* ================= FOUND ================= */

    private boolean handleFound(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(c("&cPlayers only."));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(c("&cUsage: /" + label + " found <name> <currency>"));
            return true;
        }

        if (burgManager.getBurgByMember(player.getUniqueId()) != null) {
            sender.sendMessage(c("&cYou are already in a burg."));
            return true;
        }

        String name = args[1].trim();
        if (name.length() < 3 || name.length() > 24 || burgManager.burgExists(name)) {
            sender.sendMessage(c("&cInvalid or duplicate burg name."));
            return true;
        }

        if (mpc == null || !mpc.isHooked()) {
            sender.sendMessage(c("&cMultiPolarCurrency not available."));
            return true;
        }

        String currency = args[2].toUpperCase(Locale.ROOT);
        if (!mpc.currencyExists(currency)) {
            sender.sendMessage(c("&cUnknown currency: &f" + currency));
            return true;
        }

        long initialFunding = plugin.getConfig().getLong("founding.initialFunding", 0L);
        if (initialFunding > 0 && mpc.getBalance(player, currency) < initialFunding) {
            sender.sendMessage(c("&cYou need &f" + initialFunding + " " + currency));
            return true;
        }

        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            sender.sendMessage(c("&cWorld not loaded."));
            return true;
        }

        Chunk baseChunk = loc.getChunk();
        Set<ChunkClaim> starterClaims = new HashSet<>();
        int r = plugin.getConfig().getInt("founding.starterChunkRadius", 1);

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                Chunk c = world.getChunkAt(baseChunk.getX() + dx, baseChunk.getZ() + dz);
                ChunkClaim cc = ChunkClaim.fromChunk(world, c);
                if (burgManager.isClaimed(cc)) {
                    sender.sendMessage(c("&cStarter area overlaps another burg."));
                    return true;
                }
                starterClaims.add(cc);
            }
        }

        Burg burg = burgManager.createBurgFounding(
                name,
                player.getUniqueId(),
                world,
                loc,
                currency,
                starterClaims
        );

        // Ensure treasury wallet exists (touch/create)
        UUID treasuryId = burg.getTreasuryUuid();
        mpc.touch(treasuryId, currency);

        if (initialFunding > 0) {
            mpc.withdraw(player, currency, initialFunding);
            mpc.deposit(treasuryId, currency, initialFunding);
        }

        // Food scan
        FoodScanService.ScanResult scan = foodScanService.scanBurgClaims(burg, world);
        burg.setBaseFoodCapacity(scan.baseFoodCapacity());
        burg.setLastFoodPoints(scan.totalFoodPoints());
        burg.setLastScanEpochSeconds(System.currentTimeMillis() / 1000L);

        burgManager.save(burg);

        sender.sendMessage(c("&aFounded burg &f" + name + "&a using &f" + currency));
        return true;
    }

    /* ================= JOIN ================= */

    private boolean handleJoin(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(c("&cPlayers only."));
            return true;
        }

        // Already in a burg?
        Burg current = burgManager.getBurgByMember(player.getUniqueId());
        if (current != null) {
            player.sendMessage(c("&cYou are already in a burg."));
            player.sendMessage(c("&7Leave first: &f/" + label + " leave"));
            return true;
        }

        // Must stand inside the burg you want to join
        Burg here = burgManager.getBurgAt(player.getLocation());
        if (here == null) {
            player.sendMessage(c("&cYou must be inside burg limits to join."));
            return true;
        }

        // Optional: /burg join <name> must match where you're standing
        if (args.length >= 2) {
            String want = args[1];
            boolean match = here.getName().equalsIgnoreCase(want) || here.getId().equalsIgnoreCase(want);
            if (!match) {
                player.sendMessage(c("&cYou are standing in &f" + here.getName() + "&c."));
                player.sendMessage(c("&7To join this burg: &f/" + label + " join"));
                return true;
            }
        }

        boolean ok = burgManager.tryJoinMember(here, player.getUniqueId());
        if (!ok) {
            player.sendMessage(c("&cJoin failed."));
            return true;
        }

        player.sendMessage(c("&aJoined burg: &f" + here.getName()));
        return true;
    }

    /* ================= INFO ================= */

    private boolean handleInfo(CommandSender sender) {
        if (!(sender instanceof Player player)) return true;

        Burg burg = burgManager.getBurgByMember(player.getUniqueId());
        if (burg == null) {
            sender.sendMessage(c("&cYou are not in a burg."));
            return true;
        }

        sender.sendMessage(c("&6== &e" + burg.getName() + " &6=="));
        sender.sendMessage(c("&7Currency: &f" + burg.getAdoptedCurrencyCode()));
        sender.sendMessage(c("&7Claims: &f" + burg.getClaims().size()));
        sender.sendMessage(c("&7Members: &f" + burg.getMembers().size()));
        sender.sendMessage(c("&7Food: &f" + burg.getLastFoodPoints()));
        return true;
    }

    /* ================= TREASURY ================= */

    private boolean handleTreasury(CommandSender sender) {
        if (!(sender instanceof Player player)) return true;

        Burg burg = burgManager.getBurgByMember(player.getUniqueId());
        if (burg == null) {
            sender.sendMessage(c("&cYou are not in a burg."));
            return true;
        }

        if (mpc == null || !mpc.isHooked()) {
            sender.sendMessage(c("&cMPC unavailable."));
            return true;
        }

        String currency = burg.getAdoptedCurrencyCode();
        UUID treasuryId = burg.getTreasuryUuid();

        // migrate legacy once (if you still have that map in Burg)
        if (!burg.getTreasuryBalances().isEmpty()) {
            for (var e : burg.getTreasuryBalances().entrySet()) {
                if (e.getValue() > 0) {
                    mpc.deposit(treasuryId, e.getKey(), e.getValue());
                }
            }
            burg.getTreasuryBalances().clear();
            burgManager.save(burg);
        }

        long bal = mpc.getBalance(treasuryId, currency);
        sender.sendMessage(c("&6Treasury: &f" + bal + " " + currency));
        return true;
    }

    /* ================= CLAIM / UNCLAIM ================= */

    private boolean handleClaim(CommandSender sender) {
        sender.sendMessage(c("&7Claim logic unchanged."));
        return true;
    }

    private boolean handleUnclaim(CommandSender sender) {
        sender.sendMessage(c("&7Unclaim logic unchanged."));
        return true;
    }

    /* ================= LEAVE ================= */

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
            sender.sendMessage(c("&7If you are OP: &f/burg abdicate &7then &f/burg leave"));
            return true;
        }

        // If OP leader leaves without abdicate, orphan the burg cleanly
        if (isLeader && player.isOp()) {
            burg.setLeaderUuid(null);
            burgManager.save(burg);
        }

        boolean removed = burg.getMembers().remove(player.getUniqueId());
        if (!removed) {
            sender.sendMessage(c("&cYou could not be removed (already not a member?)."));
            return true;
        }

        burgManager.onMemberLeft(burg, player.getUniqueId());

        sender.sendMessage(c("&aYou have left the burg: &f" + burg.getName()));
        if (isLeader) {
            sender.sendMessage(c("&cWarning: this burg is now orphaned (no leader)."));
        }
        return true;
    }

    /* ================= ABDICATE ================= */

    private boolean handleAbdicate(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(c("&cPlayers only."));
            return true;
        }

        Burg burg = burgManager.getBurgByMember(player.getUniqueId());
        if (burg == null) {
            player.sendMessage(c("&cYou are not in a burg."));
            return true;
        }

        UUID leader = burg.getLeaderUuid();
        boolean isLeader = (leader != null && leader.equals(player.getUniqueId()));
        if (!isLeader) {
            player.sendMessage(c("&cYou are not the mayor of &f" + burg.getName() + "&c."));
            return true;
        }

        if (!player.isOp()) {
            player.sendMessage(c("&cOnly OP can abdicate during testing."));
            return true;
        }

        burg.setLeaderUuid(null);
        burgManager.save(burg);

        player.sendMessage(c("&eYou have abdicated leadership of &f" + burg.getName() + "&e."));
        player.sendMessage(c("&7You may now leave: &f/burg leave"));
        return true;
    }

    /* ================= PLOT ================= */

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
        sender.sendMessage(c("&f/" + label + " join [name] &7- Join the burg you are standing in"));
        sender.sendMessage(c("&f/" + label + " abdicate &7- OP only: step down so you can leave"));
        sender.sendMessage(c("&f/" + label + " leave &7- Leave your burg"));
        sender.sendMessage(c("&f/" + label + " plot <pos1|pos2|create|show|list> &7- Manage plots"));
    }
}
