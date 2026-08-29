package fr.noxodev.noxoclaim.commands;

import fr.noxodev.noxoclaim.NoxoClaim;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public final class ClaimAdminCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of(
            "list", "delete", "deleteall", "save", "reload", "status", "info",
            "update", "updates", "check", "debug", "hud"
    );
    private static final List<String> HUD_SUBCOMMANDS = List.of("status", "install", "reinstall", "enable", "disable", "refresh");

    private final NoxoClaim plugin;

    public ClaimAdminCommand(NoxoClaim plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("noxoclaim.admin")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) { help(sender); return true; }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> plugin.claims().all().forEach(x -> sender.sendMessage("§7" + x.getId() + " §fowner=" + x.getOwner() + " size=" + x.size()));
            case "delete", "remove" -> delete(sender, args);
            case "deleteall" -> deleteAll(sender, args);
            case "save" -> { plugin.claims().save(); sender.sendMessage("§a[NoxoClaim] Claims sauvegardés."); }
            case "reload" -> {
                if (!sender.hasPermission("noxoclaim.admin.reload")) { plugin.messages().send(sender, "no-permission"); return true; }
                plugin.reloadConfig();
                sender.sendMessage("§a[NoxoClaim] Configuration rechargée. Les données en mémoire n'ont pas été réinitialisées.");
            }
            case "status", "info" -> sendStatus(sender);
            case "update", "updates", "check" -> update(sender, args);
            case "hud" -> hud(sender, args);
            case "debug" -> {
                sender.sendMessage("§b§lNoxoClaim Debug");
                sender.sendMessage("§7Version: §f" + plugin.getDescription().getVersion());
                sender.sendMessage("§7Serveur: §f" + Bukkit.getVersion());
                sender.sendMessage("§7Minecraft: §f" + Bukkit.getMinecraftVersion());
                sender.sendMessage("§7Claims en mémoire: §f" + plugin.claims().all().size());
                sender.sendMessage("§7Vault provider: " + (plugin.economy() == null ? "§cAbsent" : "§a" + plugin.economy().getName()));
                sender.sendMessage("§7PlugManX: " + (plugin.isPlugManXAvailable() ? "§aDétecté" : "§7Absent"));
                sender.sendMessage("§7HUDEngine: " + hudState());
                sender.sendMessage("§7Update checker: " + (plugin.updateChecker() == null ? "§cAbsent" : "§aActif"));
                sender.sendMessage("§7Update state: " + updateState());
            }
            default -> help(sender);
        }
        return true;
    }

    private void delete(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage("§cUsage: /claimadmin delete <uuid>"); return; }
        try {
            var claim = plugin.claims().get(UUID.fromString(args[1]));
            if (claim == null) sender.sendMessage("§cClaim introuvable.");
            else { plugin.claims().remove(claim); sender.sendMessage("§aClaim supprimé."); }
        } catch (IllegalArgumentException e) { sender.sendMessage("§cUUID invalide."); }
    }

    private void deleteAll(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage("§cUsage: /claimadmin deleteall <uuid joueur>"); return; }
        try {
            UUID uuid = UUID.fromString(args[1]);
            var owned = new ArrayList<>(plugin.claims().owned(uuid));
            owned.forEach(plugin.claims()::remove);
            sender.sendMessage("§a" + owned.size() + " claim(s) supprimé(s).");
        } catch (IllegalArgumentException e) { sender.sendMessage("§cUUID joueur invalide."); }
    }

    private void update(CommandSender sender, String[] args) {
        if (!sender.hasPermission("noxoclaim.admin.update")) { plugin.messages().send(sender, "no-permission"); return; }
        if (plugin.updateChecker() == null) { sender.sendMessage("§c[NoxoClaim] Le système de mise à jour n'est pas disponible."); return; }
        if (args.length > 2) { sender.sendMessage("§cUsage: /claimadmin update [commit]"); return; }
        String requestedCommit = args.length == 2 ? args[1].trim() : null;
        if (requestedCommit != null && !requestedCommit.matches("(?i)[0-9a-f]{40}")) {
            sender.sendMessage("§c[NoxoClaim] Commit invalide. Utilise le SHA complet de 40 caractères."); return;
        }
        plugin.updateChecker().checkManual(sender, requestedCommit);
    }

    private void hud(CommandSender sender, String[] args) {
        if (!sender.hasPermission("noxoclaim.admin.hud")) { plugin.messages().send(sender, "no-permission"); return; }
        if (args.length == 1 || args[1].equalsIgnoreCase("status")) { hudStatus(sender); return; }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "install" -> {
                sender.sendMessage("§b[NoxoClaim] Vérification/installation de HUDEngine...");
                fr.noxodev.noxoclaim.hud.HudEngineInstaller.ensureInstalled(plugin);
                sender.sendMessage("§a[NoxoClaim] Installation demandée. Si HUDEngine vient d'être installé, redémarre le serveur.");
            }
            case "reinstall" -> {
                sender.sendMessage("§b[NoxoClaim] Réinstallation de HUDEngine...");
                fr.noxodev.noxoclaim.hud.HudEngineInstaller.ensureInstalled(plugin);
                sender.sendMessage("§a[NoxoClaim] Réinstallation demandée. Redémarre le serveur si nécessaire.");
            }
            case "enable" -> { plugin.getConfig().set("hudengine.enabled", true); plugin.saveConfig(); sender.sendMessage("§a[NoxoClaim] Intégration HUDEngine activée. Utilise /plugman reload NoxoClaim ou redémarre."); }
            case "disable" -> { plugin.getConfig().set("hudengine.enabled", false); plugin.saveConfig(); if (plugin.hudEngine() != null && sender instanceof Player p) plugin.hudEngine().hide(p); sender.sendMessage("§e[NoxoClaim] Intégration HUDEngine désactivée."); }
            case "refresh" -> {
                if (sender instanceof Player p && plugin.hudEngine() != null && plugin.hudEngine().isReady()) { plugin.hudEngine().refresh(p); sender.sendMessage("§a[NoxoClaim] HUD actualisé."); }
                else sender.sendMessage("§c[NoxoClaim] HUD indisponible ou commande console.");
            }
            default -> hudHelp(sender);
        }
    }

    private void hudStatus(CommandSender sender) {
        boolean pluginPresent = Bukkit.getPluginManager().getPlugin("HUDEngine") != null;
        boolean ready = plugin.hudEngine() != null && plugin.hudEngine().isReady();
        sender.sendMessage("§b§lNoxoClaim — HUDEngine");
        sender.sendMessage("§7Plugin: " + (pluginPresent ? "§aInstallé" : "§cAbsent"));
        sender.sendMessage("§7Intégration: " + (plugin.getConfig().getBoolean("hudengine.enabled", true) ? "§aActivée" : "§cDésactivée"));
        sender.sendMessage("§7API: " + (ready ? "§aDisponible" : "§eNon disponible"));
        sender.sendMessage("§7Mini-map: " + (ready ? "§aPrête" : "§eEn attente"));
    }

    private String hudState() {
        if (plugin.hudEngine() == null) return "§cAbsent";
        return plugin.hudEngine().isReady() ? "§aActif" : "§eNon disponible";
    }

    private void hudHelp(CommandSender sender) {
        sender.sendMessage("§b§lNoxoClaim HUD");
        sender.sendMessage("§7/claimadmin hud §f— statut");
        sender.sendMessage("§7/claimadmin hud install");
        sender.sendMessage("§7/claimadmin hud reinstall");
        sender.sendMessage("§7/claimadmin hud enable");
        sender.sendMessage("§7/claimadmin hud disable");
        sender.sendMessage("§7/claimadmin hud refresh");
    }

    private String updateState() {
        var update = plugin.updateInfo();
        if (update == null) return "§eNon vérifié";
        return update.available() ? "§eDisponible (§f" + update.latestVersion() + "§e)" : "§aÀ jour";
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage("§b§lNoxoClaim — Status");
        sender.sendMessage("§7Version: §f" + plugin.getDescription().getVersion());
        sender.sendMessage("§7Paper: §f" + Bukkit.getVersion());
        sender.sendMessage("§7Minecraft: §f" + Bukkit.getMinecraftVersion());
        sender.sendMessage("§7Vault: " + (plugin.economy() != null ? "§aOK (" + plugin.economy().getName() + ")" : "§cAucun provider"));
        sender.sendMessage("§7PlugManX: " + (plugin.isPlugManXAvailable() ? "§aDétecté" : "§7Absent"));
        sender.sendMessage("§7HUDEngine: " + hudState());
        sender.sendMessage("§7Claims: §f" + plugin.claims().all().size());
        var update = plugin.updateInfo();
        sender.sendMessage("§7Update: " + (update == null ? "§eNon vérifiée" : update.available() ? "§eDisponible (" + update.latestVersion() + ")" : "§aÀ jour"));
    }

    private void help(CommandSender s) {
        s.sendMessage("§b§lNoxoClaim Admin");
        s.sendMessage("§7/claimadmin list");
        s.sendMessage("§7/claimadmin delete <uuid>");
        s.sendMessage("§7/claimadmin deleteall <uuid joueur>");
        s.sendMessage("§7/claimadmin save");
        s.sendMessage("§7/claimadmin reload");
        s.sendMessage("§7/claimadmin status");
        s.sendMessage("§7/claimadmin debug");
        s.sendMessage("§7/claimadmin hud [status|install|reinstall|enable|disable|refresh]");
        s.sendMessage("§7/claimadmin update [commit]");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream().filter(x -> x.startsWith(input)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("hud")) {
            String input = args[1].toLowerCase(Locale.ROOT);
            return HUD_SUBCOMMANDS.stream().filter(x -> x.startsWith(input)).toList();
        }
        return List.of();
    }
}
