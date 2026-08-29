package fr.noxodev.noxoclaim.commands;

import fr.noxodev.noxoclaim.NoxoClaim;
import org.bukkit.Bukkit;
import org.bukkit.command.*;

import java.util.*;

public final class ClaimAdminCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of(
            "list", "delete", "deleteall", "save", "reload", "status", "info",
            "update", "updates", "check", "debug"
    );

    private final NoxoClaim plugin;

    public ClaimAdminCommand(NoxoClaim plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("noxoclaim.admin")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            help(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> plugin.claims().all().forEach(x ->
                    sender.sendMessage("§7" + x.getId() + " §fowner=" + x.getOwner() + " size=" + x.size()));

            case "delete", "remove" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /claimadmin delete <uuid>");
                    return true;
                }
                try {
                    var claim = plugin.claims().get(UUID.fromString(args[1]));
                    if (claim == null) sender.sendMessage("§cClaim introuvable.");
                    else {
                        plugin.claims().remove(claim);
                        sender.sendMessage("§aClaim supprimé.");
                    }
                } catch (IllegalArgumentException e) {
                    sender.sendMessage("§cUUID invalide.");
                }
            }

            case "deleteall" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /claimadmin deleteall <uuid joueur>");
                    return true;
                }
                try {
                    UUID uuid = UUID.fromString(args[1]);
                    var owned = new ArrayList<>(plugin.claims().owned(uuid));
                    owned.forEach(plugin.claims()::remove);
                    sender.sendMessage("§a" + owned.size() + " claim(s) supprimé(s).");
                } catch (IllegalArgumentException e) {
                    sender.sendMessage("§cUUID joueur invalide.");
                }
            }

            case "save" -> {
                plugin.claims().save();
                sender.sendMessage("§a[NoxoClaim] Claims sauvegardés.");
            }

            case "reload" -> {
                if (!sender.hasPermission("noxoclaim.admin.reload")) {
                    plugin.messages().send(sender, "no-permission");
                    return true;
                }
                plugin.reloadConfig();
                sender.sendMessage("§a[NoxoClaim] Configuration rechargée. Les données en mémoire n'ont pas été réinitialisées.");
            }

            case "status", "info" -> sendStatus(sender);

            case "update", "updates", "check" -> {
                if (!sender.hasPermission("noxoclaim.admin.update")) {
                    plugin.messages().send(sender, "no-permission");
                    return true;
                }
                if (plugin.updateChecker() == null) {
                    sender.sendMessage("§c[NoxoClaim] Le système de mise à jour n'est pas disponible.");
                    return true;
                }
                if (args.length > 2) {
                    sender.sendMessage("§cUsage: /claimadmin update [commit]");
                    sender.sendMessage("§7Exemple: /claimadmin update 2c9cc6ab4a8f22ce6c4c29267934ba3da5765ded");
                    return true;
                }

                String requestedCommit = args.length == 2 ? args[1].trim() : null;
                if (requestedCommit != null && !requestedCommit.matches("(?i)[0-9a-f]{40}")) {
                    sender.sendMessage("§c[NoxoClaim] Commit invalide. Utilise le SHA complet de 40 caractères.");
                    return true;
                }
                plugin.updateChecker().checkManual(sender, requestedCommit);
            }

            case "debug" -> {
                sender.sendMessage("§b§lNoxoClaim Debug");
                sender.sendMessage("§7Version: §f" + plugin.getDescription().getVersion());
                sender.sendMessage("§7Serveur: §f" + Bukkit.getVersion());
                sender.sendMessage("§7Minecraft: §f" + Bukkit.getMinecraftVersion());
                sender.sendMessage("§7Claims en mémoire: §f" + plugin.claims().all().size());
                sender.sendMessage("§7Vault provider: " + (plugin.economy() == null ? "§cAbsent" : "§a" + plugin.economy().getName()));
                sender.sendMessage("§7PlugManX: " + (plugin.isPlugManXAvailable() ? "§aDétecté" : "§7Absent"));
                sender.sendMessage("§7Update checker: " + (plugin.updateChecker() == null ? "§cAbsent" : "§aActif"));
                sender.sendMessage("§7Update state: " + updateState());
            }

            default -> help(sender);
        }
        return true;
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
        s.sendMessage("§7/claimadmin update [commit]");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream().filter(x -> x.startsWith(input)).toList();
        }
        return List.of();
    }
}
