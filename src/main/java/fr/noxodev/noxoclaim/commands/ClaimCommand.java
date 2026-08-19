package fr.noxodev.noxoclaim.commands;

import fr.noxodev.noxoclaim.NoxoClaim;
import fr.noxodev.noxoclaim.gui.ClaimGui;
import fr.noxodev.noxoclaim.managers.SelectionManager;
import fr.noxodev.noxoclaim.models.Claim;
import fr.noxodev.noxoclaim.models.ClaimFlag;
import fr.noxodev.noxoclaim.utils.TeleportTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public final class ClaimCommand implements CommandExecutor, TabCompleter {
    private final NoxoClaim plugin;
    private final ClaimGui gui;

    public ClaimCommand(NoxoClaim plugin, ClaimGui gui) { this.plugin = plugin; this.gui = gui; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { plugin.messages().send(sender, "player-only"); return true; }
        if (label.equalsIgnoreCase("chome")) { chome(player, args); return true; }
        if (args.length == 0) { gui.openMain(player); return true; }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "gui", "menu" -> gui.openMain(player);
            case "create" -> createFromSelection(player, args.length >= 2 ? args[1] : "Maison");
            case "wand" -> giveWand(player);
            case "delete", "remove", "abandon" -> delete(player);
            case "info" -> info(player);
            case "trust" -> trust(player, args, true);
            case "untrust" -> trust(player, args, false);
            case "flags" -> flags(player, args);
            case "list" -> gui.openClaims(player);
            case "map" -> gui.openCreateMap(player);
            case "sethome" -> setHome(player);
            case "home", "tp" -> home(player);
            case "help" -> gui.openHelp(player);
            default -> gui.openMain(player);
        }
        return true;
    }

    private void giveWand(Player player) {
        Material material;
        try { material = Material.valueOf(plugin.getConfig().getString("wand.material", "GOLDEN_SHOVEL")); }
        catch (IllegalArgumentException ex) { material = Material.GOLDEN_SHOVEL; }
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b§lNoxoClaim §7• §fSélection");
            meta.setLore(List.of("§7Clic gauche §f→ premier coin", "§7Clic droit §f→ deuxième coin", "", "§8Aucune commande WorldEdit nécessaire."));
            item.setItemMeta(meta);
        }
        player.getInventory().addItem(item);
        player.sendMessage("§b§lNoxoClaim §8» §fOutil de sélection donné.");
    }

    private void createFromSelection(Player player, String name) {
        SelectionManager.Selection selection = plugin.selections().get(player.getUniqueId());
        if (selection == null || selection.first() == null || selection.second() == null || selection.first().getWorld() == null || selection.second().getWorld() == null || !selection.first().getWorld().equals(selection.second().getWorld())) {
            player.sendMessage("§c§lNoxoClaim §8» §fUtilise /claim puis « Personnalisé » pour sélectionner une zone.");
            return;
        }
        Location first = selection.first(), second = selection.second();
        purchase(player, new Claim(UUID.randomUUID(), player.getUniqueId(), player.getWorld().getName(), first.getBlockX(), first.getBlockZ(), second.getBlockX(), second.getBlockZ(), name));
    }

    /** Creates a simple rectangular claim around the player. */
    public boolean createCentered(Player player, int width, int depth, String name) {
        if (width < 1 || depth < 1) return false;
        int centerX = player.getLocation().getBlockX(), centerZ = player.getLocation().getBlockZ();
        int minX = centerX - width / 2, minZ = centerZ - depth / 2;
        int maxX = minX + width - 1, maxZ = minZ + depth - 1;
        return purchase(player, new Claim(UUID.randomUUID(), player.getUniqueId(), player.getWorld().getName(), minX, minZ, maxX, maxZ, name));
    }

    public boolean createChunks(Player player, int minCX, int minCZ, int maxCX, int maxCZ, String name) {
        return purchase(player, new Claim(UUID.randomUUID(), player.getUniqueId(), player.getWorld().getName(), minCX * 16, minCZ * 16, maxCX * 16 + 15, maxCZ * 16 + 15, name));
    }

    private boolean purchase(Player player, Claim claim) {
        if (plugin.claims().owned(player.getUniqueId()).size() >= plugin.getConfig().getInt("claim.max-per-player", 10)) { plugin.messages().send(player, "limit-reached"); return false; }
        long chunks = claim.chunkCount();
        if (chunks > plugin.getConfig().getLong("claim.max-chunks-per-purchase", 100)) { plugin.messages().send(player, "too-many-chunks"); return false; }
        if (claim.size() < plugin.getConfig().getLong("claim.min-size", 25)) { plugin.messages().send(player, "selection-too-small"); return false; }
        if (claim.size() > plugin.getConfig().getLong("claim.max-size", 1000000)) { plugin.messages().send(player, "selection-too-large"); return false; }
        if (plugin.claims().overlaps(claim)) { player.sendMessage("§c§lNoxoClaim §8» §fCette zone chevauche déjà un claim."); return false; }
        if (plugin.economy() == null) { plugin.messages().send(player, "economy-missing"); return false; }

        double cost = chunks * plugin.chunkPrice();
        if (!plugin.charge(player, cost)) {
            player.sendMessage(plugin.messages().get("prefix") + plugin.messages().format("not-enough-money", Map.of("cost", plugin.formatMoney(cost), "chunks", Long.toString(chunks))));
            return false;
        }
        plugin.claims().add(claim);
        plugin.selections().clear(player.getUniqueId());
        player.sendMessage("§a§l✓ Claim créé ! §7" + claim.getName() + " §8• §f" + claim.size() + " blocs");
        return true;
    }

    private void delete(Player player) {
        Claim claim = plugin.claims().at(player.getLocation());
        if (claim == null) { plugin.messages().send(player, "not-found"); return; }
        if (!claim.getOwner().equals(player.getUniqueId()) && !player.hasPermission("noxoclaim.admin")) { plugin.messages().send(player, "not-owner"); return; }
        plugin.claims().remove(claim); player.sendMessage("§a§l✓ Claim supprimé.");
    }

    private void info(Player player) {
        Claim claim = plugin.claims().at(player.getLocation());
        if (claim == null) { plugin.messages().send(player, "not-found"); return; }
        String owner = Bukkit.getOfflinePlayer(claim.getOwner()).getName();
        player.sendMessage("§b§lNoxoClaim");
        player.sendMessage("§7Nom : §f" + claim.getName());
        player.sendMessage("§7Propriétaire : §f" + (owner == null ? "Inconnu" : owner));
        player.sendMessage("§7Taille : §f" + claim.size() + " blocs §8(" + claim.chunkCount() + " chunks)");
        player.sendMessage("§7Membres : §f" + claim.getMembers().size());
    }

    private void trust(Player player, String[] args, boolean add) {
        Claim claim = plugin.claims().at(player.getLocation());
        if (claim == null) { plugin.messages().send(player, "not-found"); return; }
        if (!claim.getOwner().equals(player.getUniqueId())) { plugin.messages().send(player, "not-owner"); return; }
        if (args.length < 2) { player.sendMessage("§cUsage : /claim " + (add ? "trust" : "untrust") + " <joueur>"); return; }
        UUID target = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
        if (add) { claim.addMember(target); player.sendMessage("§a✓ §fJoueur ajouté au claim."); }
        else { claim.removeMember(target); player.sendMessage("§a✓ §fJoueur retiré du claim."); }
        plugin.claims().save();
    }

    private void flags(Player player, String[] args) {
        Claim claim = plugin.claims().at(player.getLocation());
        if (claim == null) { plugin.messages().send(player, "not-found"); return; }
        if (args.length < 2) { gui.openFlags(player, claim); return; }
        if (!claim.getOwner().equals(player.getUniqueId())) { plugin.messages().send(player, "not-owner"); return; }
        try {
            ClaimFlag flag = ClaimFlag.valueOf(args[1].toUpperCase(Locale.ROOT));
            claim.setFlag(flag, args.length < 3 || Boolean.parseBoolean(args[2]));
            plugin.claims().save();
        } catch (IllegalArgumentException ex) { player.sendMessage("§cFlag inconnue."); }
    }

    private void setHome(Player player) {
        Claim claim = plugin.claims().at(player.getLocation());
        if (claim == null) { plugin.messages().send(player, "not-found"); return; }
        if (!claim.getOwner().equals(player.getUniqueId())) { plugin.messages().send(player, "not-owner"); return; }
        claim.setHome(player.getLocation()); plugin.claims().save(); player.sendMessage("§a✓ §fPoint de téléportation enregistré.");
    }

    private void home(Player player) {
        Claim claim = plugin.claims().at(player.getLocation());
        if (claim == null) { plugin.messages().send(player, "not-found"); return; }
        teleport(player, claim);
    }

    private void chome(Player player, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) { gui.openClaims(player); return; }
        Claim claim = plugin.claims().owned(player.getUniqueId()).stream().filter(c -> c.getName().equalsIgnoreCase(args[0])).findFirst().orElse(null);
        if (claim == null) { plugin.messages().send(player, "home-not-found"); return; }
        teleport(player, claim);
    }

    private void teleport(Player player, Claim claim) {
        Location target = claim.getHome();
        if (target == null) {
            World world = Bukkit.getWorld(claim.getWorld());
            if (world == null) { plugin.messages().send(player, "home-not-found"); return; }
            int x = claim.getMinX() + Math.max(0, (claim.getMaxX() - claim.getMinX()) / 2);
            int z = claim.getMinZ() + Math.max(0, (claim.getMaxZ() - claim.getMinZ()) / 2);
            target = new Location(world, x + .5, world.getHighestBlockYAt(x, z) + 1, z + .5);
        }
        new TeleportTask(plugin, player, target, plugin.getConfig().getInt("teleport.delay-seconds", 3), plugin.getConfig().getBoolean("teleport.cancel-on-move", true));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("chome")) {
            if (!(sender instanceof Player player)) return List.of();
            if (args.length == 1) return plugin.claims().owned(player.getUniqueId()).stream().map(Claim::getName).toList();
            return List.of();
        }
        if (args.length == 1) return List.of("menu", "create", "wand", "info", "list", "map", "flags", "trust", "untrust", "abandon", "sethome", "home", "help");
        if (args.length == 2 && args[0].equalsIgnoreCase("flags")) return Arrays.stream(ClaimFlag.values()).map(Enum::name).toList();
        return List.of();
    }
}
