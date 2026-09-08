package fr.noxodev.noxoclaim.gui;

import fr.noxodev.noxoclaim.NoxoClaim;
import fr.noxodev.noxoclaim.models.Claim;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** In-game administration dashboard for NoxoClaim. */
public final class ClaimAdminGui implements Listener {
    private static final String TITLE = "§1NoxoClaim §8• §bAdministration";
    private static final String DETAIL = "§1NoxoClaim §8• §bClaim";
    private static final int PAGE_SIZE = 45;

    private final NoxoClaim plugin;

    public ClaimAdminGui(NoxoClaim plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        open(player, 0);
    }

    private void open(Player player, int page) {
        if (!player.hasPermission("noxoclaim.admin.dashboard")) {
            plugin.messages().send(player, "no-permission");
            return;
        }

        List<Claim> claims = sortedClaims();
        int maxPage = Math.max(0, (claims.size() - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, maxPage));

        Inventory inv = Bukkit.createInventory(null, 54, TITLE + " §7(" + (page + 1) + "/" + (maxPage + 1) + ")");
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, claims.size());

        for (int i = from; i < to; i++) {
            Claim claim = claims.get(i);
            inv.setItem(i - from, claimItem(claim));
        }

        inv.setItem(45, button(Material.ARROW, "§e← Page précédente"));
        inv.setItem(46, button(Material.COMPASS, "§bActualiser"));
        inv.setItem(49, statsItem(claims));
        inv.setItem(52, button(Material.BARRIER, "§cFermer"));
        inv.setItem(53, button(Material.ARROW, "§ePage suivante →"));
        player.openInventory(inv);
    }

    private List<Claim> sortedClaims() {
        return plugin.claims().all().stream()
                .sorted(Comparator.comparing(Claim::getWorld).thenComparing(Claim::getName).thenComparing(Claim::getId))
                .toList();
    }

    private ItemStack claimItem(Claim claim) {
        ItemStack item = new ItemStack(Material.MAP);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§b" + safe(claim.getName()));
        String owner = Bukkit.getOfflinePlayer(claim.getOwner()).getName();
        if (owner == null) owner = claim.getOwner().toString().substring(0, 8);
        meta.setLore(List.of(
                "§7Propriétaire: §f" + owner,
                "§7Monde: §f" + claim.getWorld(),
                "§7Taille: §f" + claim.size() + " blocs",
                "§7Chunks: §f" + claim.chunkCount(),
                "§7Membres: §f" + claim.getMembers().size(),
                "§8" + claim.getId(),
                "",
                "§eClique §7pour gérer"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack statsItem(List<Claim> claims) {
        long chunks = claims.stream().mapToLong(Claim::chunkCount).sum();
        return button(Material.BOOK, "§bStatistiques", List.of(
                "§7Claims: §f" + claims.size(),
                "§7Chunks protégés: §f" + chunks,
                "§7Joueurs propriétaires: §f" + claims.stream().map(Claim::getOwner).distinct().count()
        ));
    }

    private void openDetail(Player player, Claim claim) {
        Inventory inv = Bukkit.createInventory(null, 27, DETAIL);
        String owner = Bukkit.getOfflinePlayer(claim.getOwner()).getName();
        if (owner == null) owner = claim.getOwner().toString();

        inv.setItem(4, button(Material.MAP, "§b" + safe(claim.getName()), List.of(
                "§7ID: §f" + claim.getId(),
                "§7Propriétaire: §f" + owner,
                "§7Monde: §f" + claim.getWorld(),
                "§7Zone: §f" + claim.getMinX() + "," + claim.getMinZ() + " → " + claim.getMaxX() + "," + claim.getMaxZ(),
                "§7Membres: §f" + claim.getMembers().size()
        )));
        inv.setItem(11, button(Material.ENDER_PEARL, "§eTéléporter", List.of("§7Téléporte l'admin au centre du claim")));
        inv.setItem(13, button(Material.BOOK, "§bVoir les détails", List.of("§7Les informations sont affichées dans le chat")));
        inv.setItem(15, button(Material.TNT, "§cSupprimer le claim", List.of("§7Maintiens Shift en cliquant pour confirmer")));
        inv.setItem(22, button(Material.ARROW, "§eRetour à la liste"));
        inv.setItem(26, button(Material.BARRIER, "§cFermer"));
        player.openInventory(inv);
    }

    private void sendDetails(Player player, Claim claim) {
        player.sendMessage("§b§lNoxoClaim §8• §fDétails");
        player.sendMessage("§7Nom: §f" + claim.getName());
        player.sendMessage("§7ID: §f" + claim.getId());
        player.sendMessage("§7Monde: §f" + claim.getWorld());
        player.sendMessage("§7Propriétaire: §f" + Bukkit.getOfflinePlayer(claim.getOwner()).getName());
        player.sendMessage("§7Taille: §f" + claim.size() + " blocs §8/ §f" + claim.chunkCount() + " chunks");
        player.sendMessage("§7Membres: §f" + claim.getMembers().size());
    }

    private void teleport(Player player, Claim claim) {
        var world = Bukkit.getWorld(claim.getWorld());
        if (world == null) {
            player.sendMessage("§cMonde introuvable: " + claim.getWorld());
            return;
        }
        int x = claim.getMinX() + (claim.getMaxX() - claim.getMinX()) / 2;
        int z = claim.getMinZ() + (claim.getMaxZ() - claim.getMinZ()) / 2;
        player.teleport(new org.bukkit.Location(world, x + .5, world.getHighestBlockYAt(x, z) + 1, z + .5));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!title.startsWith(TITLE) && !title.equals(DETAIL)) return;
        event.setCancelled(true);
        if (!player.hasPermission("noxoclaim.admin.dashboard")) return;

        if (title.startsWith(TITLE)) {
            int page = parsePage(title);
            if (event.getRawSlot() == 45) open(player, Math.max(0, page - 1));
            else if (event.getRawSlot() == 46) open(player, page);
            else if (event.getRawSlot() == 52) player.closeInventory();
            else if (event.getRawSlot() == 53) open(player, page + 1);
            else if (event.getRawSlot() >= 0 && event.getRawSlot() < 45) {
                int index = page * PAGE_SIZE + event.getRawSlot();
                List<Claim> claims = sortedClaims();
                if (index < claims.size()) openDetail(player, claims.get(index));
            }
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        String name = clicked.getItemMeta() == null ? "" : ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
        Claim claim = findClaimFromDetail(player);
        if (event.getRawSlot() == 22) {
            open(player);
        } else if (event.getRawSlot() == 26) {
            player.closeInventory();
        } else if (event.getRawSlot() == 13 && claim != null) {
            sendDetails(player, claim);
        } else if (event.getRawSlot() == 11 && claim != null) {
            teleport(player, claim);
        } else if (event.getRawSlot() == 15 && claim != null && event.isShiftClick()) {
            plugin.claims().remove(claim);
            plugin.mapIntegration().claimChanged(claim, "removed");
            player.sendMessage("§aClaim supprimé depuis le dashboard.");
            open(player);
        }
    }

    private Claim findClaimFromDetail(Player player) {
        ItemStack item = player.getOpenInventory().getItem(4);
        if (item == null || item.getItemMeta() == null) return null;
        String display = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        return plugin.claims().all().stream().filter(c -> display.equals(c.getName())).findFirst().orElse(null);
    }

    private int parsePage(String title) {
        int start = title.lastIndexOf('(');
        int slash = title.indexOf('/', start);
        if (start < 0 || slash < 0) return 0;
        try { return Math.max(0, Integer.parseInt(title.substring(start + 1, slash)) - 1); }
        catch (NumberFormatException ignored) { return 0; }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        // Intentionally stateless: the dashboard is rebuilt from ClaimManager on every open.
    }

    private ItemStack button(Material material, String name) { return button(material, name, List.of()); }
    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(new ArrayList<>(lore));
        item.setItemMeta(meta);
        return item;
    }

    private String safe(String value) { return value == null || value.isBlank() ? "Sans nom" : value; }
}
