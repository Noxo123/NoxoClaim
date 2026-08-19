package fr.noxodev.noxoclaim.commands;

import fr.noxodev.noxoclaim.NoxoClaim;
import fr.noxodev.noxoclaim.gui.ClaimGui;
import fr.noxodev.noxoclaim.models.Claim;
import fr.noxodev.noxoclaim.models.ClaimFlag;
import fr.noxodev.noxoclaim.utils.TeleportTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public final class ClaimCommand implements CommandExecutor, TabCompleter {
    private final NoxoClaim plugin;
    private final ClaimGui gui;

    public ClaimCommand(NoxoClaim plugin, ClaimGui gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }

        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("hclaim") || name.equals("claim") || name.equals("map")) {
            if (args.length == 0) {
                gui.openMain(player);
                return true;
            }
            return handleSubcommand(player, args);
        }
        if (name.equals("uclaim")) {
            unclaimCurrentChunk(player);
            return true;
        }
        if (name.equals("chome")) {
            if (args.length == 0 || args[0].equalsIgnoreCase("list")) gui.openClaims(player);
            else teleportToNamedClaim(player, args[0]);
            return true;
        }
        return true;
    }

    private boolean handleSubcommand(Player player, String[] args) {
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> gui.openClaims(player);
            case "info" -> info(player);
            case "flags" -> flags(player, args);
            case "trust" -> trust(player, args, true);
            case "untrust" -> trust(player, args, false);
            case "sethome" -> setHome(player);
            case "home", "tp" -> home(player);
            case "help" -> gui.openHelp(player);
            case "gui", "menu", "map" -> gui.openMain(player);
            case "unclaim", "delete", "remove", "abandon" -> unclaimCurrentChunk(player);
            default -> gui.openMain(player);
        }
        return true;
    }

    /** Claims exactly the chunk where the player is standing. */
    public boolean claimCurrentChunk(Player player) {
        int chunkX = player.getChunk().getX();
        int chunkZ = player.getChunk().getZ();
        String world = player.getWorld().getName();

        Claim existing = plugin.claims().atChunk(world, chunkX, chunkZ);
        if (existing != null) {
            if (existing.getOwner().equals(player.getUniqueId()))
                player.sendMessage("§e§lNoxoClaim §8» §fCe chunk est déjà à toi.");
            else
                player.sendMessage("§c§lNoxoClaim §8» §fCe chunk appartient déjà à §e" + ownerName(existing) + "§f.");
            return false;
        }

        if (plugin.claims().owned(player.getUniqueId()).size() >= plugin.getConfig().getInt("claim.max-per-player", 10)) {
            plugin.messages().send(player, "limit-reached");
            return false;
        }
        if (plugin.economy() == null) {
            plugin.messages().send(player, "economy-missing");
            return false;
        }

        int minX = chunkX * 16;
        int minZ = chunkZ * 16;
        int maxX = minX + 15;
        int maxZ = minZ + 15;
        Claim claim = new Claim(UUID.randomUUID(), player.getUniqueId(), world, minX, minZ, maxX, maxZ, "Chunk " + chunkX + ", " + chunkZ);
        applyDefaultFlags(claim);

        double cost = plugin.chunkPrice();
        if (!plugin.charge(player, cost)) {
            player.sendMessage(plugin.messages().get("prefix") + plugin.messages().format("not-enough-money", Map.of(
                    "cost", plugin.formatMoney(cost), "chunks", "1")));
            return false;
        }

        plugin.claims().add(claim);
        player.sendMessage("§a§l✓ Chunk claimé ! §7" + world + " §8• §f" + chunkX + ", " + chunkZ);
        return true;
    }

    private void applyDefaultFlags(Claim claim) {
        for (ClaimFlag flag : ClaimFlag.values()) {
            String path = "claim.default-flags." + flag.name().toLowerCase(Locale.ROOT).replace('_', '-');
            claim.setFlag(flag, plugin.getConfig().getBoolean(path, claim.getFlag(flag)));
        }
    }

    /** Unclaims exactly the chunk where the player is standing. */
    public boolean unclaimCurrentChunk(Player player) {
        Claim claim = plugin.claims().atChunk(player.getWorld().getName(), player.getChunk().getX(), player.getChunk().getZ());
        if (claim == null) {
            player.sendMessage("§c§lNoxoClaim §8» §fCe chunk n'est pas claimé.");
            return false;
        }
        if (!claim.getOwner().equals(player.getUniqueId()) && !player.hasPermission("noxoclaim.admin")) {
            plugin.messages().send(player, "not-owner");
            return false;
        }
        plugin.claims().remove(claim);
        player.sendMessage("§a§l✓ Chunk unclaimé ! §7Tu peux maintenant l'utiliser librement.");
        return true;
    }

    private void info(Player player) {
        Claim claim = plugin.claims().atChunk(player.getWorld().getName(), player.getChunk().getX(), player.getChunk().getZ());
        if (claim == null) {
            player.sendMessage("§e§lNoxoClaim §8» §fCe chunk est libre.");
            return;
        }
        player.sendMessage("§b§lNoxoClaim §8• §fInformations");
        player.sendMessage("§7Chunk : §f" + player.getChunk().getX() + " / " + player.getChunk().getZ());
        player.sendMessage("§7Propriétaire : §f" + ownerName(claim));
        player.sendMessage("§7Membres : §f" + claim.getMembers().size());
    }

    private void trust(Player player, String[] args, boolean add) {
        Claim claim = currentClaim(player);
        if (claim == null) return;
        if (!claim.getOwner().equals(player.getUniqueId())) { plugin.messages().send(player, "not-owner"); return; }
        if (args.length < 2) {
            player.sendMessage("§cUsage : /hclaim " + (add ? "trust" : "untrust") + " <joueur>");
            return;
        }
        UUID target = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
        if (add) { claim.addMember(target); player.sendMessage("§a✓ §fJoueur ajouté au chunk."); }
        else { claim.removeMember(target); player.sendMessage("§a✓ §fJoueur retiré du chunk."); }
        plugin.claims().save();
    }

    private void flags(Player player, String[] args) {
        Claim claim = currentClaim(player);
        if (claim == null) return;
        if (args.length < 2) { gui.openFlags(player, claim); return; }
        if (!claim.getOwner().equals(player.getUniqueId())) { plugin.messages().send(player, "not-owner"); return; }
        try {
            ClaimFlag flag = ClaimFlag.valueOf(args[1].toUpperCase(Locale.ROOT));
            claim.setFlag(flag, args.length < 3 || Boolean.parseBoolean(args[2]));
            plugin.claims().save();
        } catch (IllegalArgumentException ex) { player.sendMessage("§cFlag inconnue."); }
    }

    private void setHome(Player player) {
        Claim claim = currentClaim(player);
        if (claim == null) return;
        if (!claim.getOwner().equals(player.getUniqueId())) { plugin.messages().send(player, "not-owner"); return; }
        claim.setHome(player.getLocation());
        plugin.claims().save();
        player.sendMessage("§a✓ §fPoint de téléportation enregistré.");
    }

    private void home(Player player) {
        Claim claim = currentClaim(player);
        if (claim == null) return;
        teleport(player, claim);
    }

    private void teleportToNamedClaim(Player player, String name) {
        Claim claim = plugin.claims().owned(player.getUniqueId()).stream()
                .filter(c -> c.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
        if (claim == null) { plugin.messages().send(player, "home-not-found"); return; }
        teleport(player, claim);
    }

    private void teleport(Player player, Claim claim) {
        Location target = claim.getHome();
        if (target == null) {
            World world = Bukkit.getWorld(claim.getWorld());
            if (world == null) { plugin.messages().send(player, "home-not-found"); return; }
            int x = claim.getMinX() + 8;
            int z = claim.getMinZ() + 8;
            target = new Location(world, x + .5, world.getHighestBlockYAt(x, z) + 1, z + .5);
        }
        new TeleportTask(plugin, player, target,
                plugin.getConfig().getInt("teleport.delay-seconds", 3),
                plugin.getConfig().getBoolean("teleport.cancel-on-move", true));
    }

    private Claim currentClaim(Player player) {
        Claim claim = plugin.claims().atChunk(player.getWorld().getName(), player.getChunk().getX(), player.getChunk().getZ());
        if (claim == null) player.sendMessage("§e§lNoxoClaim §8» §fCe chunk n'est pas claimé.");
        return claim;
    }

    private String ownerName(Claim claim) {
        String name = Bukkit.getOfflinePlayer(claim.getOwner()).getName();
        return name == null ? "Inconnu" : name;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("chome")) {
            if (!(sender instanceof Player player)) return List.of();
            return args.length == 1 ? plugin.claims().owned(player.getUniqueId()).stream().map(Claim::getName).toList() : List.of();
        }
        if (command.getName().equalsIgnoreCase("uclaim") || args.length == 0) return List.of();
        if (args.length == 1) return List.of("list", "info", "flags", "trust", "untrust", "sethome", "home", "help", "unclaim");
        if (args.length == 2 && args[0].equalsIgnoreCase("flags")) return Arrays.stream(ClaimFlag.values()).map(Enum::name).toList();
        return List.of();
    }
}
