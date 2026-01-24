package com.brandon.burgsbanners.commands;

import com.brandon.burgsbanners.burg.*;
import com.brandon.burgsbanners.burg.food.FoodScanService;
import com.brandon.burgsbanners.mpc.MpcHook;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;

public class BurgCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final BurgManager burgManager;
    private final MpcHook mpc;
    private final FoodScanService foodScanService;

    private static final List<String> SUBS = List.of(
            "help", "found", "info", "treasury", "claim", "unclaim", "leave"
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
            case "help" -> {
                help(sender, label);
                return true;
            }

            case "found" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(c("&cOnly players can use this command."));
                    return true;
                }

                // For pre-pre alpha you relaxed perms; keep the check if you want.
                if (!player.hasPermission("burg.found") && !player.hasPermission("burg.*") && !player.isOp()) {
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

                // Validate MPC currency exists
                if (!mpc.currencyExists(currencyCode)) {
                    sender.sendMessage(c("&cCurrency does not exist in MultiPolarCurrency: &f" + currencyCode));
                    return true;
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

                // Starter claim radius
                int starterRadius = plugin.getConfig().getInt("founding.starterChunkRadius", 1);

                Set<ChunkClaim> starterClaims = new HashSet<>();
                for (int dx = -starterRadius; dx <= starterRadius; dx++) {
                    for (int dz = -starterRadius; dz <= starterRadius; dz++) {
                        Chunk c = world.getChunkAt(currentChunk.getX() + dx, currentChunk.getZ() + dz);
                        ChunkClaim cc = ChunkClaim.fromChunk(world, c);

                        // any overlap = fail
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

                sender.sendMessage(c("&7Scanning starter territory for food capacity..."));
                FoodScanService.ScanResult scan = foodScanService.scanBurgClaims(burg, world);

                burg.setBaseFoodCapacity(scan.baseFoodCapacity());
                burg.setLastFoodPoints(scan.totalFoodPoints());
                burg.setLastScanEpochSeconds(System.currentTimeMillis() / 1000L);

                burgManager.save(burg);

                sender.sendMessage(c("&aBurg founded: &f" + burg.getName()));
                sender.sendMessage(c("&7Leader: &f" + player.getName() + " &7(&fLord-Mayor&7)"));
                sender.sendMessage(c("&7Currency adopted: &f" + currencyCode));
                sender.sendMessage(c("&7Starter claims: &f" + starterClaims.size() + " &7chunks"));
                sender.sendMessage(c("&7Base Food Capacity (BFC): &f" + String.format(Locale.US, "%.2f", burg.getBaseFoodCapacity())));
                sender.sendMessage(c("&7Try: &f/" + label + " info"));
                return true;
            }

            case "info" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(c("&cOnly players can use this command."));
                    return true;
                }
                Burg burg = burgManager.getBurgByMember(player.getUniqueId());
                if (burg == null) {
                    sender.sendMessage(c("&cYou are not in a burg."));
                    return true;
                }

                sender.sendMessage(c("&6== &eBurg Info &6=="));
                sender.sendMessage(c("&7Name: &f" + burg.getName()));
                sender.sendMessage(c("&7Stage: &f" + burg.getPolityStage()));
                sender.sendMessage(c("&7Ruler: &f" + burg.getLeader() + " &7(&f" + burg.getRulerTitle() + "&7)"));
                sender.sendMessage(c("&7Currency: &f" + burg.getAdoptedCurrencyCode()));
                sender.sendMessage(c("&7Claims: &f" + burg.getClaimCount()));
                sender.sendMessage(c("&7Population: &f" + burg.getTotalPopulation() + " &7(abstract)"));
                sender.sendMessage(c("&7BFC: &f" + String.format(Locale.US, "%.2f", burg.getBaseFoodCapacity())));
                return true;
            }

            case "treasury" -> {
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
                burg.getTreasuryBalances().forEach((code, bal) -> {
                    sender.sendMessage(c("&7" + code + ": &f" + bal));
                });
                return true;
            }

            case "claim" -> {
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

            case "unclaim" -> {
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

            case "leave" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(c("&cOnly players can use this command."));
                    return true;
                }

                Burg burg = burgManager.getBurgByMember(player.getUniqueId());
                if (burg == null) {
                    sender.sendMessage(c("&cYou are not in a burg."));
                    return true;
                }

                // Leader rule: only OP can orphan the mayorship
                UUID leader = burg.getLeader();
                boolean isLeader = leader != null && leader.equals(player.getUniqueId());

                if (isLeader && !player.isOp()) {
                    sender.sendMessage(c("&cYou are the Lord-Mayor of &f" + burg.getName() + "&c."));
                    sender.sendMessage(c("&7You cannot leave while ruling. (Only OP can orphan leadership during testing.)"));
                    return true;
                }

                boolean removed = burg.getMembers().remove(player.getUniqueId());
                if (!removed) {
                    // Shouldn't happen if indices are consistent, but keep it safe.
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

            default -> {
                sender.sendMessage(c("&cUnknown subcommand. Try &f/" + label + " help"));
                return true;
            }
        }
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage(c("&6== &eBurgs & Banners &6=="));
        sender.sendMessage(c("&f/" + label + " found <name> <currencyCode> &7- Found a new burg"));
        sender.sendMessage(c("&f/" + label + " info &7- View your burg"));
        sender.sendMessage(c("&f/" + label + " treasury &7- View balances"));
        sender.sendMessage(c("&f/" + label + " claim &7- Claim current chunk"));
        sender.sendMessage(c("&f/" + label + " unclaim &7- Unclaim current chunk"));
        sender.sendMessage(c("&f/" + label + " leave &7- Leave your burg (leader requires OP)"));
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

        if (args.length == 3 && args[0].equalsIgnoreCase("found")) {
            return mpc.suggestCurrencyCodes(args[2]);
        }

        return Collections.emptyList();
    }
}
