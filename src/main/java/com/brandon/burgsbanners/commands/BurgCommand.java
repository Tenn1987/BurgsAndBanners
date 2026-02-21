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

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

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

    private Component c(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
    }

    private boolean isMayor(Burg burg, UUID player) {
        return burg != null && burg.getLeaderUuid() != null && burg.getLeaderUuid().equals(player);
    }

    /** A burg is considered orphaned if it has no leader, or the leader is not a current member. */
    private boolean isOrphaned(Burg burg) {
        if (burg == null) return true;
        UUID leader = burg.getLeaderUuid();
        return leader == null || burg.getMembers() == null || !burg.getMembers().contains(leader);
    }

    /**
     * Safely set leader UUID. Uses a public setter if present, otherwise falls back to a field named "leaderUuid".
     */
    private boolean trySetLeader(Burg burg, UUID newLeader) {
        if (burg == null) return false;
        try {
            var m = burg.getClass().getMethod("setLeaderUuid", UUID.class);
            m.invoke(burg, newLeader);
            return true;
        } catch (NoSuchMethodException ignored) {
            try {
                var f = burg.getClass().getDeclaredField("leaderUuid");
                f.setAccessible(true);
                f.set(burg, newLeader);
                return true;
            } catch (Exception e) {
                plugin.getLogger().warning("Could not set burg leader (no setLeaderUuid + no leaderUuid field): " + e.getMessage());
                return false;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not set burg leader: " + e.getMessage());
            return false;
        }
    }

    /** Picks a successor for mayor. Prefers online members, then any remaining member. */
    private UUID pickSuccessor(Burg burg, UUID exclude) {
        if (burg == null || burg.getMembers() == null) return null;

        for (UUID u : burg.getMembers()) {
            if (u == null || u.equals(exclude)) continue;
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline()) return u;
        }
        for (UUID u : burg.getMembers()) {
            if (u == null || u.equals(exclude)) continue;
            return u;
        }
        return null;
    }

    private String nameOf(UUID uuid) {
        if (uuid == null) return "(none)";
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) return p.getName();
        OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
        return (off.getName() != null ? off.getName() : uuid.toString());
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
            List<String> subs = List.of("pos1", "pos2", "create", "show", "list", "assign", "unassign");
            String p = args[1].toLowerCase(Locale.ROOT);
            return subs.stream().filter(s -> s.startsWith(p)).collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("found") && mpc != null && mpc.isHooked()) {
            return mpc.suggestCurrencyCodes(args[2]);
        }

        if (args.length >= 3 && args[0].equalsIgnoreCase("plot")) {
            String sub = args[1].toLowerCase(Locale.ROOT);

            if (sub.equals("show") || sub.equals("assign") || sub.equals("unassign")) {
                if (!(sender instanceof Player player)) return Collections.emptyList();
                Burg burg = burgManager.getBurgByMember(player.getUniqueId());
                if (burg == null) return Collections.emptyList();

                String p = args[2].toLowerCase(Locale.ROOT);
                return burg.getPlots().keySet().stream()
                        .filter(k -> k.toLowerCase(Locale.ROOT).startsWith(p))
                        .sorted()
                        .collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }

    /* ================= HELP ================= */

    private void help(CommandSender sender, String label) {
        sender.sendMessage(c("&6== &eBurg Commands &6=="));
        sender.sendMessage(c("&e/" + label + " found <name> <currency>"));
        sender.sendMessage(c("&e/" + label + " info"));
        sender.sendMessage(c("&e/" + label + " treasury"));
        sender.sendMessage(c("&e/" + label + " claim"));
        sender.sendMessage(c("&e/" + label + " unclaim"));
        sender.sendMessage(c("&e/" + label + " join"));
        sender.sendMessage(c("&e/" + label + " leave"));
        sender.sendMessage(c("&e/" + label + " plot pos1|pos2|create|show|list|assign|unassign"));
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

        UUID treasuryId = burg.getTreasuryUuid();
        mpc.touch(treasuryId, currency);

        if (initialFunding > 0) {
            mpc.withdraw(player, currency, initialFunding);
            mpc.deposit(treasuryId, currency, initialFunding);
        }

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

        Burg current = burgManager.getBurgByMember(player.getUniqueId());
        if (current != null) {
            player.sendMessage(c("&cYou are already in a burg."));
            player.sendMessage(c("&7Leave first: &f/" + label + " leave"));
            return true;
        }

        Burg here = burgManager.getBurgAt(player.getLocation());
        if (here == null) {
            player.sendMessage(c("&cYou must be inside burg limits to join."));
            return true;
        }

        if (!here.getMembers().add(player.getUniqueId())) {
            player.sendMessage(c("&cYou are already a member."));
            return true;
        }

        // Auto-promote if the burg is orphaned (no valid leader).
        if (isOrphaned(here)) {
            if (trySetLeader(here, player.getUniqueId())) {
                player.sendMessage(c("&eThis burg was orphaned. You have been auto-promoted to &6Mayor&e."));
            }
        }

        burgManager.save(here);
        player.sendMessage(c("&aJoined burg: &f" + here.getName()));
        return true;
    }

    /* ================= LEAVE ================= */

    private boolean handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(c("&cPlayers only."));
            return true;
        }

        Burg burg = burgManager.getBurgByMember(player.getUniqueId());
        if (burg == null) {
            sender.sendMessage(c("&cYou are not in a burg."));
            return true;
        }

        UUID me = player.getUniqueId();

        // Mayors leaving is sensitive: require OP (server authority) to prevent griefy leadership dumps.
        if (isMayor(burg, me) && !player.isOp()) {
            sender.sendMessage(c("&cMayors cannot use &f/burg leave&c. Use &f/burg abdicate&c to pass leadership first, or ask an OP."));
            return true;
        }

        if (isMayor(burg, me)) {
            // OP mayor is allowed to leave: auto-promote successor (or orphan if nobody remains).
            burg.getMembers().remove(me);

            UUID successor = pickSuccessor(burg, me);
            if (successor == null) {
                trySetLeader(burg, null);
                burgManager.save(burg);
                sender.sendMessage(c("&eYou left &f" + burg.getName() + "&e. The burg is now &6orphaned&e."));
                return true;
            }

            trySetLeader(burg, successor);
            burgManager.save(burg);

            sender.sendMessage(c("&eYou left &f" + burg.getName() + "&e. &6" + nameOf(successor) + "&e is now Mayor."));
            Player succP = Bukkit.getPlayer(successor);
            if (succP != null) {
                succP.sendMessage(c("&aYou have been promoted to &6Mayor&a of &f" + burg.getName()));
            }
            return true;
        }

        burg.getMembers().remove(me);
        burgManager.save(burg);
        sender.sendMessage(c("&aYou left &f" + burg.getName()));
        return true;
    }

    /* ================= INFO / TREASURY ================= */

    private boolean handleInfo(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(c("&cPlayers only."));
            return true;
        }

        Burg burg = burgManager.getBurgByMember(player.getUniqueId());
        if (burg == null) {
            sender.sendMessage(c("&cYou are not in a burg."));
            return true;
        }

        sender.sendMessage(c("&6== &eBurg Info &6=="));
        sender.sendMessage(c("&eName: &f" + burg.getName()));
        sender.sendMessage(c("&eLeader: &f" + burg.getLeaderUuid()));
        sender.sendMessage(c("&eMembers: &f" + burg.getMembers().size()));
        sender.sendMessage(c("&eClaims: &f" + burg.getClaims().size()));
        sender.sendMessage(c("&ePlots: &f" + burg.getPlots().size()));
        return true;
    }

    private boolean handleTreasury(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(c("&cPlayers only."));
            return true;
        }

        Burg burg = burgManager.getBurgByMember(player.getUniqueId());
        if (burg == null) {
            sender.sendMessage(c("&cYou are not in a burg."));
            return true;
        }

        String code = burg.getAdoptedCurrencyCode();
        long bal = mpc.getBalance(burg.getTreasuryUuid(), code);

        sender.sendMessage(c("&6== &eTreasury &6=="));
        sender.sendMessage(c("&eBurg: &f" + burg.getName()));
        sender.sendMessage(c("&eBalance: &f" + bal + " " + code));
        return true;
    }

    /* ================= CLAIM / UNCLAIM ================= */

    private boolean handleClaim(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(c("&cPlayers only."));
            return true;
        }

        Burg burg = burgManager.getBurgByMember(player.getUniqueId());
        if (burg == null) {
            sender.sendMessage(c("&cYou are not in a burg."));
            return true;
        }
        if (!isMayor(burg, player.getUniqueId())) {
            sender.sendMessage(c("&cOnly the mayor can claim."));
            return true;
        }

        Chunk chunk = player.getLocation().getChunk();
        ChunkClaim claim = ChunkClaim.fromChunk(chunk.getWorld(), chunk);

        if (!burgManager.tryAddClaim(burg, claim)) {
            sender.sendMessage(c("&cThat chunk is already claimed."));
            return true;
        }

        sender.sendMessage(c("&aClaimed chunk &f(" + claim.getChunkX() + "," + claim.getChunkZ() + ")"));
        return true;
    }

    private boolean handleUnclaim(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(c("&cPlayers only."));
            return true;
        }

        Burg burg = burgManager.getBurgByMember(player.getUniqueId());
        if (burg == null) {
            sender.sendMessage(c("&cYou are not in a burg."));
            return true;
        }
        if (!isMayor(burg, player.getUniqueId())) {
            sender.sendMessage(c("&cOnly the mayor can unclaim."));
            return true;
        }

        Chunk chunk = player.getLocation().getChunk();
        ChunkClaim claim = ChunkClaim.fromChunk(chunk.getWorld(), chunk);

        if (!burgManager.tryRemoveClaim(burg, claim)) {
            sender.sendMessage(c("&cThat chunk is not yours."));
            return true;
        }

        sender.sendMessage(c("&aUnclaimed chunk &f(" + claim.getChunkX() + "," + claim.getChunkZ() + ")"));
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
            sender.sendMessage(c("&cYou are not in a burg."));
            return true;
        }

        UUID me = player.getUniqueId();
        if (!isMayor(burg, me)) {
            sender.sendMessage(c("&cOnly the mayor can abdicate."));
            return true;
        }

        UUID successor = pickSuccessor(burg, me);
        if (successor == null) {
            sender.sendMessage(c("&cNo eligible successor. If you want this burg to become orphaned, an OP can remove the leader."));
            return true;
        }

        if (!trySetLeader(burg, successor)) {
            sender.sendMessage(c("&cFailed to set new mayor (leader field/setter not found)."));
            return true;
        }

        burgManager.save(burg);
        sender.sendMessage(c("&aYou abdicated. &6" + nameOf(successor) + "&a is now Mayor of &f" + burg.getName()));

        Player succP = Bukkit.getPlayer(successor);
        if (succP != null) {
            succP.sendMessage(c("&aYou have been promoted to &6Mayor&a of &f" + burg.getName()));
        }
        return true;
    }

    /* ================= PLOTS ================= */

    private boolean handlePlot(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(c("&cPlayers only."));
            return true;
        }

        Burg burg = burgManager.getBurgByMember(player.getUniqueId());
        if (burg == null) {
            sender.sendMessage(c("&cYou are not in a burg."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(c("&cUsage: /" + label + " plot <pos1|pos2|create|show|list|assign|unassign>"));
            return true;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);

        switch (sub) {

            case "pos1" -> {
                if (!isMayor(burg, player.getUniqueId())) {
                    sender.sendMessage(c("&cOnly the mayor can set plot corners."));
                    return true;
                }

                Block b = player.getTargetBlockExact(200);
                if (b == null) {
                    sender.sendMessage(c("&cLook at a block within 200 blocks."));
                    return true;
                }

                PlotSelection sel = plotSelections.computeIfAbsent(player.getUniqueId(), k -> new PlotSelection());
                sel.setPos1(b.getLocation());
                sender.sendMessage(c("&aPlot pos1 set."));
                return true;
            }

            case "pos2" -> {
                if (!isMayor(burg, player.getUniqueId())) {
                    sender.sendMessage(c("&cOnly the mayor can set plot corners."));
                    return true;
                }

                Block b = player.getTargetBlockExact(200);
                if (b == null) {
                    sender.sendMessage(c("&cLook at a block within 200 blocks."));
                    return true;
                }

                PlotSelection sel = plotSelections.computeIfAbsent(player.getUniqueId(), k -> new PlotSelection());
                sel.setPos2(b.getLocation());
                sender.sendMessage(c("&aPlot pos2 set."));
                return true;
            }

            case "list" -> {
                if (burg.getPlots().isEmpty()) {
                    sender.sendMessage(c("&7No plots yet."));
                    return true;
                }
                sender.sendMessage(c("&6== &ePlots in " + burg.getName() + " &6=="));
                for (Plot p : burg.getPlots().values()) {
                    String owner = (p.getOwnerUuid() == null) ? "none" : p.getOwnerUuid().toString();
                    sender.sendMessage(c("&7- &f" + p.getId() + " &7(" + p.getName() + ") &8owner:&7 " + owner));
                }
                return true;
            }

            case "create" -> {
                if (!isMayor(burg, player.getUniqueId())) {
                    sender.sendMessage(c("&cOnly the mayor can create plots."));
                    return true;
                }

                if (args.length < 3) {
                    sender.sendMessage(c("&cUsage: /" + label + " plot create <id> [displayName...]"));
                    return true;
                }

                String id = args[2].trim().toLowerCase(Locale.ROOT);
                if (!id.matches("[a-z0-9_-]{2,24}")) {
                    sender.sendMessage(c("&cPlot id must be 2-24 chars: a-z, 0-9, _, -"));
                    return true;
                }
                if (burg.getPlot(id) != null) {
                    sender.sendMessage(c("&cThat plot id already exists."));
                    return true;
                }

                PlotSelection sel = plotSelections.get(player.getUniqueId());
                if (sel == null || sel.getPos1() == null || sel.getPos2() == null) {
                    sender.sendMessage(c("&cSet pos1 and pos2 first."));
                    return true;
                }

                Location p1 = sel.getPos1();
                Location p2 = sel.getPos2();
                if (p1.getWorld() == null || p2.getWorld() == null || !p1.getWorld().getUID().equals(p2.getWorld().getUID())) {
                    sender.sendMessage(c("&cpos1 and pos2 must be in the same world."));
                    return true;
                }

                // ensure both corners are inside claimed chunks (simple check)
                if (burgManager.getBurgAt(p1) != burg || burgManager.getBurgAt(p2) != burg) {
                    sender.sendMessage(c("&cBoth corners must be inside your claimed land."));
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
                        UUID.randomUUID(),
                        id,
                        displayName,
                        p1.getWorld().getUID(),
                        minX, minY, minZ,
                        maxX, maxY, maxZ
                );

                // prevent overlaps
                for (Plot existing : burg.getPlots().values()) {
                    if (existing.getWorldId().equals(plot.getWorldId())
                            && existing.getMinX() <= plot.getMaxX() && existing.getMaxX() >= plot.getMinX()
                            && existing.getMinY() <= plot.getMaxY() && existing.getMaxY() >= plot.getMinY()
                            && existing.getMinZ() <= plot.getMaxZ() && existing.getMaxZ() >= plot.getMinZ()) {
                        sender.sendMessage(c("&cOverlaps existing plot: &f" + existing.getId()));
                        return true;
                    }
                }

                burg.putPlot(plot);
                burgManager.save(burg);

                sender.sendMessage(c("&aCreated plot &f" + plot.getId() + "&a with UUID &7" + plot.getPlotUuid()));
                return true;
            }

            case "assign" -> {
                if (!isMayor(burg, player.getUniqueId())) {
                    sender.sendMessage(c("&cOnly the mayor can assign plot ownership."));
                    return true;
                }

                if (args.length < 4) {
                    sender.sendMessage(c("&cUsage: /" + label + " plot assign <plotId> <playerName>"));
                    return true;
                }

                String plotId = args[2].toLowerCase(Locale.ROOT);
                Plot plot = burg.getPlot(plotId);
                if (plot == null) {
                    sender.sendMessage(c("&cUnknown plot: &f" + plotId));
                    return true;
                }

                OfflinePlayer target = Bukkit.getOfflinePlayer(args[3]);
                if (target == null || target.getUniqueId() == null) {
                    sender.sendMessage(c("&cCould not resolve player: &f" + args[3]));
                    return true;
                }

                plot.setOwnerUuid(target.getUniqueId());
                burgManager.save(burg);

                sender.sendMessage(c("&aAssigned plot &f" + plot.getId() + " &ato &f" + target.getName()));
                return true;
            }

            case "unassign" -> {
                if (!isMayor(burg, player.getUniqueId())) {
                    sender.sendMessage(c("&cOnly the mayor can unassign plot ownership."));
                    return true;
                }

                if (args.length < 3) {
                    sender.sendMessage(c("&cUsage: /" + label + " plot unassign <plotId>"));
                    return true;
                }

                String plotId = args[2].toLowerCase(Locale.ROOT);
                Plot plot = burg.getPlot(plotId);
                if (plot == null) {
                    sender.sendMessage(c("&cUnknown plot: &f" + plotId));
                    return true;
                }

                plot.setOwnerUuid(null);
                burgManager.save(burg);

                sender.sendMessage(c("&aUnassigned plot &f" + plot.getId()));
                return true;
            }

            case "show" -> {
                if (args.length < 3) {
                    sender.sendMessage(c("&cUsage: /" + label + " plot show <id>"));
                    return true;
                }

                String id = args[2].trim().toLowerCase(Locale.ROOT);
                Plot plot = burg.getPlot(id);
                if (plot == null) {
                    sender.sendMessage(c("&cNo plot found with id: &f" + id));
                    return true;
                }

                visualizePlot(player, plot);
                sender.sendMessage(c("&aVisualized plot: &f" + plot.getId() + " &7(" + plot.getName() + ")"));
                return true;
            }

            default -> {
                sender.sendMessage(c("&cUsage: /" + label + " plot <pos1|pos2|create|show|list|assign|unassign>"));
                return true;
            }
        }
    }

    /* ================= VISUALIZER ================= */

    private void visualizePlot(Player player, Plot plot) {
        World world = Bukkit.getWorld(plot.getWorldId());
        if (world == null) {
            player.sendMessage(c("&cWorld not loaded for this plot."));
            return;
        }

        int minX = plot.getMinX();
        int maxX = plot.getMaxX();
        int minY = plot.getMinY();
        int maxY = plot.getMaxY();
        int minZ = plot.getMinZ();
        int maxZ = plot.getMaxZ();

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                if (t++ > 80) {
                    cancel();
                    return;
                }

                for (int x = minX; x <= maxX; x++) {
                    spawn(player, world, x, minY, minZ);
                    spawn(player, world, x, minY, maxZ);
                    spawn(player, world, x, maxY, minZ);
                    spawn(player, world, x, maxY, maxZ);
                }
                for (int z = minZ; z <= maxZ; z++) {
                    spawn(player, world, minX, minY, z);
                    spawn(player, world, maxX, minY, z);
                    spawn(player, world, minX, maxY, z);
                    spawn(player, world, maxX, maxY, z);
                }
                for (int y = minY; y <= maxY; y++) {
                    spawn(player, world, minX, y, minZ);
                    spawn(player, world, maxX, y, minZ);
                    spawn(player, world, minX, y, maxZ);
                    spawn(player, world, maxX, y, maxZ);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void spawn(Player player, World world, double x, double y, double z) {
        Particle particle;
        try {
            particle = Particle.valueOf("HAPPY_VILLAGER");
        } catch (IllegalArgumentException ex) {
            particle = Particle.valueOf("VILLAGER_HAPPY");
        }
        player.spawnParticle(particle, x + 0.5, y + 0.5, z + 0.5, 1, 0, 0, 0, 0);
    }

}
