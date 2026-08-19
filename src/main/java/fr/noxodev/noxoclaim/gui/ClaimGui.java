package fr.noxodev.noxoclaim.gui;

import fr.noxodev.noxoclaim.NoxoClaim;
import fr.noxodev.noxoclaim.commands.ClaimCommand;
import fr.noxodev.noxoclaim.models.Claim;
import fr.noxodev.noxoclaim.models.ClaimFlag;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/** Simple player-first GUI. Players never need WorldEdit or coordinates. */
public final class ClaimGui implements Listener {
    private static final String MAIN = "§8§lNoxoClaim §7• Menu";
    private static final String CREATE = "§8§lNoxoClaim §7• Créer";
    private static final String CLAIMS = "§8§lNoxoClaim §7• Mes claims";
    private static final String FLAGS = "§8§lNoxoClaim §7• Protection";
    private static final String HELP = "§8§lNoxoClaim §7• Aide";

    private final NoxoClaim plugin;
    private final Map<UUID, Claim> flagClaims = new HashMap<>();

    public ClaimGui(NoxoClaim plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void openMain(Player player) {
        Inventory inv = Bukkit.createInventory(null, 45, MAIN);
        fill(inv, Material.GRAY_STAINED_GLASS_PANE, " ");
        button(inv, 10, Material.GRASS_BLOCK, "§a§lCréer mon claim", "§7Crée un terrain automatiquement", "§7autour de ta position.", "", "§eClique pour choisir une taille.");
        button(inv, 13, Material.BOOK, "§b§lMes claims", "§7Voir et gérer tes terrains.");
        button(inv, 16, Material.SHIELD, "§6§lProtection", "§7Modifier les protections du claim actuel.");
        button(inv, 31, Material.COMPASS, "§d§lCarte", "§7Voir les claims autour de toi.");
        button(inv, 33, Material.PAPER, "§f§lAide", "§7Tout est expliqué ici.");
        button(inv, 40, Material.BARRIER, "§c§lFermer", "§7Fermer le menu.");
        player.openInventory(inv);
    }

    public void openCreate(Player player) {
        Inventory inv = Bukkit.createInventory(null, 45, CREATE);
        fill(inv, Material.BLACK_STAINED_GLASS_PANE, " ");
        button(inv, 10, Material.OAK_PLANKS, "§a§lPetit", "§732 × 32 blocs", "§7Simple pour une petite maison.", "", "§eClique pour créer.");
        button(inv, 13, Material.BRICKS, "§b§lMoyen", "§764 × 64 blocs", "§7Parfait pour une base.", "", "§eClique pour créer.");
        button(inv, 16, Material.QUARTZ_BLOCK, "§6§lGrand", "§7128 × 128 blocs", "§7Pour une grosse base.", "", "§eClique pour créer.");
        button(inv, 22, Material.GOLDEN_SHOVEL, "§d§lPersonnalisé", "§7Sélectionne deux coins avec", "§7l'outil NoxoClaim.", "", "§ePas besoin de WorldEdit.");
        button(inv, 40, Material.ARROW, "§fRetour", "§7Retour au menu.");
        button(inv, 44, Material.BARRIER, "§cFermer", "§7Fermer le menu.");
        player.openInventory(inv);
    }

    public void openClaims(Player player) {
        List<Claim> claims = plugin.claims().owned(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, CLAIMS);
        fill(inv, Material.GRAY_STAINED_GLASS_PANE, " ");
        if (claims.isEmpty()) {
            button(inv, 22, Material.BARRIER, "§c§lAucun claim", "§7Tu n'as encore aucun terrain.", "", "§eRetourne au menu pour en créer un.");
        } else {
            int slot = 10;
            for (Claim claim : claims) {
                if (slot >= 44) break;
                button(inv, slot++, Material.LIME_WOOL, "§a§l" + claim.getName(), "§7Taille : §f" + claim.size() + " blocs", "§7Chunks : §f" + claim.chunkCount(), "§7Membres : §f" + claim.getMembers().size(), "", "§eClique pour te téléporter.");
            }
        }
        button(inv, 49, Material.ARROW, "§fRetour", "§7Retour au menu.");
        button(inv, 53, Material.BARRIER, "§cFermer", "§7Fermer le menu.");
        player.openInventory(inv);
    }

    public void openFlags(Player player, Claim claim) {
        if (claim == null) return;
        flagClaims.put(player.getUniqueId(), claim);
        Inventory inv = Bukkit.createInventory(null, 45, FLAGS);
        fill(inv, Material.GRAY_STAINED_GLASS_PANE, " ");
        button(inv, 10, claim.getFlag(ClaimFlag.PVP) ? Material.LIME_DYE : Material.GRAY_DYE, "§c⚔ PvP", state(claim.getFlag(ClaimFlag.PVP)));
        button(inv, 12, claim.getFlag(ClaimFlag.FIRE) ? Material.LIME_DYE : Material.GRAY_DYE, "§6🔥 Feu", state(claim.getFlag(ClaimFlag.FIRE)));
        button(inv, 14, claim.getFlag(ClaimFlag.EXPLOSIONS) ? Material.LIME_DYE : Material.GRAY_DYE, "§e💥 Explosions", state(claim.getFlag(ClaimFlag.EXPLOSIONS)));
        button(inv, 16, claim.getFlag(ClaimFlag.MOB_GRIEFING) ? Material.LIME_DYE : Material.GRAY_DYE, "§5👹 Grief des mobs", state(claim.getFlag(ClaimFlag.MOB_GRIEFING)));
        button(inv, 22, claim.getFlag(ClaimFlag.ENTRY) ? Material.LIME_DYE : Material.GRAY_DYE, "§b🚪 Entrée", state(claim.getFlag(ClaimFlag.ENTRY)));
        button(inv, 40, Material.ARROW, "§fRetour", "§7Retour au menu.");
        player.openInventory(inv);
    }

    public void openCreateMap(Player player) { openCreate(player); }

    public void openHelp(Player player) {
        Inventory inv = Bukkit.createInventory(null, 45, HELP);
        fill(inv, Material.BLACK_STAINED_GLASS_PANE, " ");
        button(inv, 11, Material.COMPASS, "§b§lComment créer ?", "§71. Clique sur Créer mon claim", "§72. Choisis Petit, Moyen ou Grand", "§73. Le terrain est créé autour de toi", "", "§aAucune coordonnée nécessaire.");
        button(inv, 15, Material.GOLDEN_SHOVEL, "§d§lTerrain personnalisé", "§7Pour une forme précise :", "§7utilise l'outil NoxoClaim.", "§7Clic gauche = coin 1", "§7Clic droit = coin 2");
        button(inv, 22, Material.SHIELD, "§6§lProtections", "§7Ouvre ton claim puis Protection", "§7pour activer/désactiver PvP, feu,", "§7explosions et grief des mobs.");
        button(inv, 29, Material.PLAYER_HEAD, "§a§lMembres", "§7Utilise les commandes /claim trust", "§7et /claim untrust pour les gérer.");
        button(inv, 40, Material.ARROW, "§fRetour", "§7Retour au menu.");
        player.openInventory(inv);
    }

    private String state(boolean enabled) { return enabled ? "§aActivé" : "§cDésactivé"; }

    private void fill(Inventory inv, Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(name); item.setItemMeta(meta); }
        for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null) inv.setItem(i, item.clone());
    }

    private void button(Inventory inv, int slot, Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        inv.setItem(slot, item);
    }

    private boolean isTitle(String title) { return title.equals(MAIN) || title.equals(CREATE) || title.equals(CLAIMS) || title.equals(FLAGS) || title.equals(HELP); }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!isTitle(event.getView().getTitle())) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;

        String title = event.getView().getTitle();
        if (title.equals(MAIN)) {
            if (slot == 10) openCreate(player);
            else if (slot == 13) openClaims(player);
            else if (slot == 16) {
                Claim claim = plugin.claims().at(player.getLocation());
                if (claim == null) player.sendMessage("§c§lNoxoClaim §8» §fTu n'es pas dans ton claim.");
                else if (!claim.getOwner().equals(player.getUniqueId())) player.sendMessage("§c§lNoxoClaim §8» §fTu n'es pas propriétaire de ce claim.");
                else openFlags(player, claim);
            } else if (slot == 31) openCreateMap(player);
            else if (slot == 33) openHelp(player);
            else if (slot == 40) player.closeInventory();
            return;
        }

        if (title.equals(CREATE)) {
            if (slot == 10) create(player, 32, "Maison");
            else if (slot == 13) create(player, 64, "Base");
            else if (slot == 16) create(player, 128, "Grand terrain");
            else if (slot == 22) {
                player.closeInventory();
                if (plugin.getCommand("claim") != null && plugin.getCommand("claim").getExecutor() instanceof ClaimCommand) {
                    player.performCommand("claim wand");
                }
                player.sendMessage("§b§lNoxoClaim §8» §fClique gauche sur le premier coin puis clic droit sur le deuxième.");
                player.sendMessage("§7Ensuite utilise §f/claim create§7 pour finaliser.");
            } else if (slot == 40) openMain(player);
            else if (slot == 44) player.closeInventory();
            return;
        }

        if (title.equals(CLAIMS)) {
            if (slot == 49) { openMain(player); return; }
            if (slot == 53) { player.closeInventory(); return; }
            List<Claim> claims = plugin.claims().owned(player.getUniqueId());
            int index = slot - 10;
            if (index >= 0 && index < claims.size() && slot < 44) {
                Claim claim = claims.get(index);
                player.closeInventory();
                LocationHolder.teleport(player, claim, plugin);
            }
            return;
        }

        if (title.equals(FLAGS)) {
            Claim claim = flagClaims.get(player.getUniqueId());
            if (claim == null || !claim.getOwner().equals(player.getUniqueId())) { openMain(player); return; }
            ClaimFlag flag = switch (slot) {
                case 10 -> ClaimFlag.PVP;
                case 12 -> ClaimFlag.FIRE;
                case 14 -> ClaimFlag.EXPLOSIONS;
                case 16 -> ClaimFlag.MOB_GRIEFING;
                case 22 -> ClaimFlag.ENTRY;
                default -> null;
            };
            if (flag != null) { claim.setFlag(flag, !claim.getFlag(flag)); plugin.claims().save(); openFlags(player, claim); }
            else if (slot == 40) openMain(player);
            return;
        }

        if (title.equals(HELP)) {
            if (slot == 40) openMain(player);
        }
    }

    private void create(Player player, int size, String name) {
        player.closeInventory();
        if (plugin.getCommand("claim") == null || !(plugin.getCommand("claim").getExecutor() instanceof ClaimCommand command)) {
            player.sendMessage("§cNoxoClaim n'est pas correctement chargé.");
            return;
        }
        command.createCentered(player, size, size, name);
    }

    private static final class LocationHolder {
        private static void teleport(Player player, Claim claim, NoxoClaim plugin) {
            org.bukkit.Location target = claim.getHome();
            if (target == null) {
                org.bukkit.World world = Bukkit.getWorld(claim.getWorld());
                if (world == null) return;
                int x = claim.getMinX() + (claim.getMaxX() - claim.getMinX()) / 2;
                int z = claim.getMinZ() + (claim.getMaxZ() - claim.getMinZ()) / 2;
                target = new org.bukkit.Location(world, x + .5, world.getHighestBlockYAt(x, z) + 1, z + .5);
            }
            new fr.noxodev.noxoclaim.utils.TeleportTask(plugin, player, target, plugin.getConfig().getInt("teleport.delay-seconds", 3), plugin.getConfig().getBoolean("teleport.cancel-on-move", true));
        }
    }
}
