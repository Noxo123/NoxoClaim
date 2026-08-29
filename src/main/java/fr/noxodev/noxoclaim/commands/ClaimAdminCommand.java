package fr.noxodev.noxoclaim.commands;

import fr.noxodev.noxoclaim.NoxoClaim;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import java.util.*;

public final class ClaimAdminCommand implements CommandExecutor, TabCompleter {
    private final NoxoClaim plugin;
    public ClaimAdminCommand(NoxoClaim plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("noxoclaim.admin")) { plugin.messages().send(sender, "no-permission"); return true; }
        if (args.length == 0) { help(sender); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> plugin.claims().all().forEach(x -> sender.sendMessage("§7" + x.getId() + " §fowner=" + x.getOwner() + " size=" + x.size()));
            case "delete", "remove" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /claimadmin delete <uuid>"); return true; }
                try { var claim = plugin.claims().get(UUID.fromString(args[1])); if (claim == null) sender.sendMessage("§cClaim introuvable."); else { plugin.claims().remove(claim); sender.sendMessage("§aClaim supprimé."); } }
                catch (IllegalArgumentException e) { sender.sendMessage("§cUUID invalide."); }
            }
            case "deleteall" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /claimadmin deleteall <uuid>"); return true; }
                try { UUID uuid = UUID.fromString(args[1]); plugin.claims().owned(uuid).forEach(plugin.claims()::remove); sender.sendMessage("§aClaims du joueur supprimés."); }
                catch (IllegalArgumentException e) { sender.sendMessage("§cUUID invalide."); }
            }
            case "save" -> { plugin.claims().save(); sender.sendMessage("§aClaims sauvegardés."); }
            case "reload" -> {
                if (!sender.hasPermission("noxoclaim.admin.reload")) { plugin.messages().send(sender, "no-permission"); return true; }
                plugin.reloadConfig();
                sender.sendMessage("§a[NoxoClaim] Configuration rechargée. Les claims restent en mémoire.");
            }
            case "status", "info" -> {
                sender.sendMessage("§b§lNoxoClaim — Status");
                sender.sendMessage("§7Version: §f" + plugin.getDescription().getVersion());
                sender.sendMessage("§7Paper: §f" + Bukkit.getVersion());
                sender.sendMessage("§7Vault: " + (plugin.economy() != null ? "§aOK" : "§cAbsent"));
                sender.sendMessage("§7PlugManX: " + (plugin.isPlugManXAvailable() ? "§aDétecté" : "§7Absent"));
                sender.sendMessage("§7Claims: §f" + plugin.claims().all().size());
                var update = plugin.updateInfo();
                sender.sendMessage("§7Update: " + (update == null ? "§eNon vérifiée" : update.available() ? "§eDisponible (" + update.latestVersion() + ")" : "§aÀ jour"));
            }
            case "update", "updates" -> {
                if (!sender.hasPermission("noxoclaim.admin.update")) { plugin.messages().send(sender, "no-permission"); return true; }
                if (plugin.updateChecker() == null) { sender.sendMessage("§c[NoxoClaim] Le système de mise à jour n'est pas disponible."); return true; }
                plugin.updateChecker().checkManual(sender);
            }
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender s) {
        s.sendMessage("§b§lNoxoClaim Admin");
        s.sendMessage("§7/claimadmin list");
        s.sendMessage("§7/claimadmin delete <uuid>");
        s.sendMessage("§7/claimadmin deleteall <uuid>");
        s.sendMessage("§7/claimadmin save");
        s.sendMessage("§7/claimadmin reload");
        s.sendMessage("§7/claimadmin status");
        s.sendMessage("§7/claimadmin update");
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("list", "delete", "deleteall", "save", "reload", "status", "info", "update").stream().filter(x -> x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
}
