package fr.noxodev.noxoclaim.commands;

import fr.noxodev.noxoclaim.NoxoClaim;
import fr.noxodev.noxoclaim.update.UpdateChecker;
import fr.noxodev.noxoclaim.update.UpdateInfo;
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
            case "reload" -> { plugin.reloadConfig(); sender.sendMessage("§aConfiguration rechargée."); }
            case "update", "updates" -> {
                UpdateInfo info = plugin.updateInfo();
                if (info == null) { sender.sendMessage("§7Vérification des mises à jour en cours..."); new UpdateChecker(plugin).check(false); return true; }
                if (!info.available()) sender.sendMessage("§aNoxoClaim est à jour (§f" + info.currentVersion() + "§a).");
                else { sender.sendMessage("§eNouvelle version : §a" + info.latestVersion() + " §7(actuelle " + info.currentVersion() + ")"); if (info.releaseUrl() != null) sender.sendMessage("§7Release : §f" + info.releaseUrl()); }
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
        s.sendMessage("§7/claimadmin update");
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("list", "delete", "deleteall", "save", "reload", "update").stream().filter(x -> x.startsWith(args[0].toLowerCase())).toList();
        return List.of();
    }
}
