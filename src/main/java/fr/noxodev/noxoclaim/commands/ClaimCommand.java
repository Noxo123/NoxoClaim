package fr.noxodev.noxoclaim.commands;

import fr.noxodev.noxoclaim.NoxoClaim;
import fr.noxodev.noxoclaim.gui.ClaimGui;
import fr.noxodev.noxoclaim.models.*;
import fr.noxodev.noxoclaim.managers.SelectionManager;
import fr.noxodev.noxoclaim.utils.TeleportTask;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;

public final class ClaimCommand implements CommandExecutor, TabCompleter {
    private final NoxoClaim p; private final ClaimGui gui;
    public ClaimCommand(NoxoClaim p, ClaimGui gui) { this.p = p; this.gui = gui; }

    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!(s instanceof Player x)) { p.messages().send(s, "player-only"); return true; }
        if (label.equalsIgnoreCase("chome")) { chome(x, a); return true; }
        if (a.length == 0) { p.messages().send(x, "usage"); return true; }
        switch (a[0].toLowerCase()) {
            case "wand" -> wand(x);
            case "gui" -> gui.open(x, a.length >= 2 ? a[1] : "mon-claim");
            case "create" -> create(x, a.length >= 2 ? a[1] : "mon-claim");
            case "delete", "remove" -> delete(x);
            case "info" -> info(x);
            case "trust", "untrust" -> trust(x, a);
            case "flags" -> flags(x, a);
            case "list" -> list(x);
            case "sethome" -> sethome(x);
            case "home", "tp" -> home(x);
            default -> p.messages().send(x, "usage");
        }
        return true;
    }

    private void wand(Player x) {
        org.bukkit.inventory.ItemStack i = new org.bukkit.inventory.ItemStack(Material.valueOf(p.getConfig().getString("wand.material", "GOLDEN_SHOVEL")));
        var m = i.getItemMeta(); m.setDisplayName("§bWand de claim"); i.setItemMeta(m); x.getInventory().addItem(i);
        p.messages().send(x, "wand-given");
    }

    private void create(Player x, String name) {
        SelectionManager.Selection s = p.selections().get(x.getUniqueId());
        if (s == null || s.first() == null || s.second() == null || s.first().getWorld() != s.second().getWorld()) { p.messages().send(x, "selection-required"); return; }
        Claim c = new Claim(UUID.randomUUID(), x.getUniqueId(), x.getWorld().getName(), s.first().getBlockX(), s.first().getBlockZ(), s.second().getBlockX(), s.second().getBlockZ(), name);
        purchase(x, c);
    }

    public boolean createChunks(Player x, int minCX, int minCZ, int maxCX, int maxCZ, String name) {
        Claim c = new Claim(UUID.randomUUID(), x.getUniqueId(), x.getWorld().getName(), minCX * 16, minCZ * 16, maxCX * 16 + 15, maxCZ * 16 + 15, name);
        return purchase(x, c);
    }

    private boolean purchase(Player x, Claim c) {
        if (p.claims().owned(x.getUniqueId()).size() >= p.getConfig().getInt("claim.max-per-player", 10)) { p.messages().send(x, "limit-reached"); return false; }
        long chunks = c.chunkCount();
        if (chunks > p.getConfig().getLong("claim.max-chunks-per-purchase", 100)) { p.messages().send(x, "too-many-chunks"); return false; }
        if (c.size() < p.getConfig().getLong("claim.min-size", 25)) { p.messages().send(x, "selection-too-small"); return false; }
        if (c.size() > p.getConfig().getLong("claim.max-size", 1000000) || p.claims().overlaps(c)) { p.messages().send(x, "selection-too-large"); return false; }
        double cost = chunks * p.chunkPrice();
        if (p.economy() == null) { p.messages().send(x, "economy-missing"); return false; }
        if (!p.charge(x, cost)) { x.sendMessage(p.messages().get("prefix") + p.messages().format("not-enough-money", Map.of("cost", p.formatMoney(cost), "chunks", Long.toString(chunks)))); return false; }
        p.claims().add(c); p.selections().clear(x.getUniqueId());
        x.sendMessage(p.messages().get("prefix") + p.messages().format("created-paid", Map.of("name", c.getName(), "cost", p.formatMoney(cost), "chunks", Long.toString(chunks))));
        return true;
    }

    private void delete(Player x) {
        Claim c = p.claims().at(x.getLocation()); if (c == null) { p.messages().send(x, "not-found"); return; }
        if (!c.getOwner().equals(x.getUniqueId()) && !x.hasPermission("noxoclaim.admin")) { p.messages().send(x, "not-owner"); return; }
        p.claims().remove(c); p.messages().send(x, "deleted");
    }

    private void info(Player x) {
        Claim c = p.claims().at(x.getLocation()); if (c == null) { p.messages().send(x, "not-found"); return; }
        x.sendMessage("§bClaim §f" + c.getName() + " §7| propriétaire: §f" + Bukkit.getOfflinePlayer(c.getOwner()).getName() + " §7| chunks: §f" + c.chunkCount());
    }

    private void trust(Player x, String[] a) {
        Claim c = p.claims().at(x.getLocation()); if (c == null) { p.messages().send(x, "not-found"); return; }
        if (!c.getOwner().equals(x.getUniqueId())) { p.messages().send(x, "not-owner"); return; }
        if (a.length < 2) { x.sendMessage("§c/claim trust|untrust <joueur>"); return; }
        UUID u = Bukkit.getOfflinePlayer(a[1]).getUniqueId();
        if (a[0].equalsIgnoreCase("trust")) { c.addMember(u); p.messages().send(x, "trusted"); } else { c.removeMember(u); p.messages().send(x, "untrusted"); }
        p.claims().save();
    }

    private void flags(Player x, String[] a) {
        Claim c = p.claims().at(x.getLocation()); if (c == null) { p.messages().send(x, "not-found"); return; }
        if (a.length < 2) { c.getFlags().forEach((f,v) -> x.sendMessage("§7" + f + " = " + v)); return; }
        if (!c.getOwner().equals(x.getUniqueId())) { p.messages().send(x, "not-owner"); return; }
        try { ClaimFlag f = ClaimFlag.valueOf(a[1].toUpperCase()); c.setFlag(f, a.length < 3 || Boolean.parseBoolean(a[2])); p.claims().save(); }
        catch (IllegalArgumentException e) { x.sendMessage("§cFlag inconnue."); }
    }

    private void list(Player x) { x.sendMessage("§bVos claims: §f" + p.claims().owned(x.getUniqueId()).size()); p.claims().owned(x.getUniqueId()).forEach(c -> x.sendMessage("§7- §f" + c.getName() + " §7(" + c.chunkCount() + " chunks)")); }

    private void sethome(Player x) {
        Claim c = p.claims().at(x.getLocation()); if (c == null) { p.messages().send(x, "not-found"); return; }
        if (!c.getOwner().equals(x.getUniqueId())) { p.messages().send(x, "not-owner"); return; }
        c.setHome(x.getLocation()); p.claims().save(); x.sendMessage(p.messages().get("prefix") + p.messages().format("home-set", Map.of("name", c.getName())));
    }

    private void home(Player x) {
        Claim c = p.claims().at(x.getLocation()); if (c == null) { p.messages().send(x, "not-found"); return; }
        teleport(x, c);
    }

    private void chome(Player x, String[] a) {
        if (a.length < 1) { x.sendMessage("§c/chome <nom> §7ou §c/chome list"); return; }
        if (a[0].equalsIgnoreCase("list")) { list(x); return; }
        Claim c = p.claims().owned(x.getUniqueId()).stream().filter(v -> v.getName().equalsIgnoreCase(a[0])).findFirst().orElse(null);
        if (c == null) { p.messages().send(x, "home-not-found"); return; }
        teleport(x, c);
    }

    private void teleport(Player x, Claim c) {
        Location target = c.getHome();
        if (target == null) {
            World w = Bukkit.getWorld(c.getWorld());
            if (w == null) { p.messages().send(x, "home-not-found"); return; }
            int bx = c.getMinX() + 8, bz = c.getMinZ() + 8;
            target = new Location(w, bx + .5, w.getHighestBlockYAt(bx, bz) + 1, bz + .5);
        }
        new TeleportTask(p, x, target, p.getConfig().getInt("teleport.delay-seconds", 3), p.getConfig().getBoolean("teleport.cancel-on-move", true));
    }

    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if (c.getName().equalsIgnoreCase("chome")) {
            if (!(s instanceof Player player)) return List.of();
            if (args.length == 1) return p.claims().owned(player.getUniqueId()).stream().map(Claim::getName).toList();
            return List.of();
        }
        if (args.length == 1) return List.of("wand","create","delete","info","trust","untrust","flags","list","gui","home","sethome");
        if (args.length == 2 && args[0].equalsIgnoreCase("flags")) return Arrays.stream(ClaimFlag.values()).map(Enum::name).toList();
        return List.of();
    }
}
