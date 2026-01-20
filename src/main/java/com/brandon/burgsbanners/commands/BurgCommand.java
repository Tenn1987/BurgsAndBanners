package com.brandon.burgsbanners.commands;

import com.brandon.burgsbanners.burg.Burg;
import com.brandon.burgsbanners.burg.BurgManager;
import com.brandon.burgsbanners.burg.ChunkClaim;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class BurgCommand implements CommandExecutor, TabCompleter {

    private final BurgManager burgManager;

    private static final List<String> SUBS = List.of(
            "help", "create", "info", "treasury", "claim", "unclaim"
    );

    public BurgCommand(BurgManager burgManager) {
        this.burgManager = burgManager;
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

            case "create" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(c("&cOnly players can use this command."));
                    return true;
                }

                if (!player.hasPermission("burg.create") && !player.hasPermission("burg.*")) {
                    sender.sendMessage(c("&cYou lack permission: &fburg.create"));
                    return true;
                }

                if (args.length < 2) {
                    sender.sendMessage(c("&cUsage: &f/" + label + " create <name>"));
                    return true;
                }

                Burg existing = burgManager.getBurgByMember(player.getUniqueId());
                if (existing != null) {
                    sender.sendMessage(c("&cYou are already in a burg: &f" + existing.getName()));
                    return true;
                }

                String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
                if (name.length() < 3 || name.length() > 24) {
                    sender.sendMessage(c("&cBurg name must be 3–24 characters."));
                    return true;
                }

                if (burgManager.burgExists(name)) {
                    sender.sendMessage(c("&cThat burg name is already taken."));
                    return true;
                }

                Burg burg = burgManager.createBurg(name, player.getUniqueId());

                sender.sendMessage(c("&aBurg founded: &f" + burg.getName()));
                sender.sendMessage(c("&7Leader: &f" + player.getName()));
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
                    sender.sendMessage(c("&cYou are not part of a burg."));
                    sender.sendMessage(c("&7Try: &f/" + label + " create <name>"));
                    return true;
                }

                sender.sendMessage(c("&6&lBurg: &f" + burg.getName()));
                sender.sendMessage(c("&7Treasury: &f" + fmtMoney(burg.getTreasury())));
                sender.sendMessage(c("&7Claims: &f" + burg.getClaimCount()));
                sender.sendMessage(c("&7Morale: &f" + fmt1(burg.getMorale())));
                sender.sendMessage(c("&7Members: &f" + burg.getMembers().size()));
                return true;
            }

            case "treasury" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(c("&cOnly players can use this command."));
                    return true;
                }

                Burg burg = burgManager.getBurgByMember(player.getUniqueId());
                if (burg == null) {
                    sender.sendMessage(c("&cYou are not part of a burg."));
                    return true;
                }

                // /burg treasury
                if (args.length == 1) {
                    sender.sendMessage(c("&6&lTreasury: &f" + burg.getName()));
                    sender.sendMessage(c("&7Balance: &f" + fmtMoney(burg.getTreasury())));
                    sender.sendMessage(c("&7Upkeep (placeholder): &f" + fmtMoney(burg.estimateUpkeep())));
                    sender.sendMessage(c("&7Income (placeholder): &f" + fmtMoney(burg.estimateIncome())));
                    sender.sendMessage(c("&7Leader can: &f/" + label + " treasury deposit <amount> &7or &fwithdraw <amount>"));
                    return true;
                }

                // leader-only treasury management
                if (!burg.getLeader().equals(player.getUniqueId())) {
                    sender.sendMessage(c("&cOnly the burg leader can manage the treasury."));
                    return true;
                }

                if (!player.hasPermission("burg.treasury.manage") && !player.hasPermission("burg.*")) {
                    sender.sendMessage(c("&cYou lack permission: &fburg.treasury.manage"));
                    return true;
                }

                if (args.length < 3) {
                    sender.sendMessage(c("&cUsage: &f/" + label + " treasury deposit <amount>"));
                    sender.sendMessage(c("&cUsage: &f/" + label + " treasury withdraw <amount>"));
                    return true;
                }

                String action = args[1].toLowerCase(Locale.ROOT);
                Double amount = parsePositiveDouble(args[2]);
                if (amount == null) {
                    sender.sendMessage(c("&cAmount must be a positive number."));
                    return true;
                }

                if (action.equals("deposit")) {
                    burg.deposit(amount);
                    burgManager.save(burg);
                    sender.sendMessage(c("&aDeposited &f" + fmtMoney(amount) + " &ainto the treasury."));
                    sender.sendMessage(c("&7New balance: &f" + fmtMoney(burg.getTreasury())));
                    return true;
                }

                if (action.equals("withdraw")) {
                    if (!burg.withdraw(amount)) {
                        sender.sendMessage(c("&cInsufficient treasury funds."));
                        sender.sendMessage(c("&7Balance: &f" + fmtMoney(burg.getTreasury())));
                        return true;
                    }
                    burgManager.save(burg);
                    sender.sendMessage(c("&eWithdrew &f" + fmtMoney(amount) + " &efrom the treasury."));
                    sender.sendMessage(c("&7New balance: &f" + fmtMoney(burg.getTreasury())));
                    return true;
                }

                sender.sendMessage(c("&cUnknown action. Use deposit or withdraw."));
                return true;
            }

            case "claim" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(c("&cOnly players can use this command."));
                    return true;
                }

                Burg burg = burgManager.getBurgByMember(player.getUniqueId());
                if (burg == null) {
                    sender.sendMessage(c("&cYou are not part of a burg."));
                    return true;
                }

                // leader-only for now (we can add offices later)
                if (!burg.getLeader().equals(player.getUniqueId())) {
                    sender.sendMessage(c("&cOnly the burg leader can claim land (for now)."));
                    return true;
                }

                if (!player.hasPermission("burg.claim") && !player.hasPermission("burg.*")) {
                    sender.sendMessage(c("&cYou lack permission: &fburg.claim"));
                    return true;
                }

                Chunk chunk = player.getLocation().getChunk();
                ChunkClaim claim = new ChunkClaim(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());

                // already claimed by this burg?
                if (burg.hasClaim(claim)) {
                    sender.sendMessage(c("&eThis chunk is already claimed by your burg."));
                    return true;
                }

                // claimed by another burg?
                if (burgManager.isClaimed(claim)) {
                    sender.sendMessage(c("&cThis chunk is already claimed by another burg."));
                    return true;
                }

                // contiguity rule: first claim allowed; otherwise must touch an existing claim (N/S/E/W)
                if (burg.getClaimCount() > 0 && !isContiguous(burg, claim)) {
                    sender.sendMessage(c("&cClaims must be contiguous (touching N/S/E/W)."));
                    return true;
                }

                int cost = burgManager.getClaimCost(burg);
                if (!burg.withdraw(cost)) {
                    sender.sendMessage(c("&cNot enough treasury to claim. Cost: &f" + fmtMoney(cost)));
                    sender.sendMessage(c("&7Balance: &f" + fmtMoney(burg.getTreasury())));
                    return true;
                }

                boolean ok = burgManager.tryAddClaim(burg, claim);
                if (!ok) {
                    // refund if something raced (rare but safe)
                    burg.deposit(cost);
                    burgManager.save(burg);
                    sender.sendMessage(c("&cFailed to claim chunk (it may have been claimed)."));
                    return true;
                }

                sender.sendMessage(c("&aClaimed chunk &f" + claim.getWorldName() + " &7(" + claim.getChunkX() + ", " + claim.getChunkZ() + ")"));
                sender.sendMessage(c("&7Cost: &f" + fmtMoney(cost) + " &7| Claims: &f" + burg.getClaimCount() + " &7| Balance: &f" + fmtMoney(burg.getTreasury())));
                return true;
            }

            case "unclaim" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(c("&cOnly players can use this command."));
                    return true;
                }

                Burg burg = burgManager.getBurgByMember(player.getUniqueId());
                if (burg == null) {
                    sender.sendMessage(c("&cYou are not part of a burg."));
                    return true;
                }

                if (!burg.getLeader().equals(player.getUniqueId())) {
                    sender.sendMessage(c("&cOnly the burg leader can unclaim land (for now)."));
                    return true;
                }

                if (!player.hasPermission("burg.unclaim") && !player.hasPermission("burg.*")) {
                    sender.sendMessage(c("&cYou lack permission: &fburg.unclaim"));
                    return true;
                }

                Chunk chunk = player.getLocation().getChunk();
                ChunkClaim claim = new ChunkClaim(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());

                if (!burg.hasClaim(claim)) {
                    sender.sendMessage(c("&cThis chunk is not claimed by your burg."));
                    return true;
                }

                // Keep it simple for now: allow unclaiming even if it splits territory.
                // Later we can enforce “cannot split claims” if you want.
                boolean ok = burgManager.tryRemoveClaim(burg, claim);
                if (!ok) {
                    sender.sendMessage(c("&cFailed to unclaim chunk."));
                    return true;
                }

                sender.sendMessage(c("&eUnclaimed chunk &f" + claim.getWorldName() + " &7(" + claim.getChunkX() + ", " + claim.getChunkZ() + ")"));
                sender.sendMessage(c("&7Claims: &f" + burg.getClaimCount()));
                return true;
            }

            default -> {
                sender.sendMessage(c("&cUnknown subcommand. Use &f/" + label + " help"));
                return true;
            }
        }
    }

    private boolean isContiguous(Burg burg, ChunkClaim newClaim) {
        // Must touch an existing claim N/S/E/W in the same world
        for (ChunkClaim c : burg.getClaims()) {
            if (!c.getWorldName().equals(newClaim.getWorldName())) continue;

            int dx = Math.abs(c.getChunkX() - newClaim.getChunkX());
            int dz = Math.abs(c.getChunkZ() - newClaim.getChunkZ());

            if ((dx == 1 && dz == 0) || (dx == 0 && dz == 1)) {
                return true;
            }
        }
        return false;
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage(c("&6&lBurgs & Banners"));
        sender.sendMessage(c("&7/" + label + " create <name> &f- Found a burg"));
        sender.sendMessage(c("&7/" + label + " info &f- View your burg"));
        sender.sendMessage(c("&7/" + label + " treasury &f- View treasury"));
        sender.sendMessage(c("&7/" + label + " claim &f- Claim current chunk (leader)"));
        sender.sendMessage(c("&7/" + label + " unclaim &f- Unclaim current chunk (leader)"));
        sender.sendMessage(c("&7Leader: &f/" + label + " treasury deposit|withdraw <amount>"));
    }

    private String c(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private static String fmt1(double v) {
        return String.format(Locale.US, "%.1f", v);
    }

    private static String fmtMoney(double v) {
        return String.format(Locale.US, "%.2f", v);
    }

    private static Double parsePositiveDouble(String s) {
        try {
            double v = Double.parseDouble(s);
            if (v <= 0) return null;
            return v;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String start = args[0].toLowerCase(Locale.ROOT);
            return SUBS.stream().filter(s -> s.startsWith(start)).collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("treasury")) {
            List<String> subs = List.of("deposit", "withdraw");
            String start = args[1].toLowerCase(Locale.ROOT);
            return subs.stream().filter(s -> s.startsWith(start)).collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
