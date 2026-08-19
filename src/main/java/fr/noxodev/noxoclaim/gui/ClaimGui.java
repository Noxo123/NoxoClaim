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

/** Player-first GUI. Everything is based on the current Minecraft chunk. */
public final class ClaimGui implements Listener {
    private static final String MAIN = "§8§lNoxoClaim §7• Menu";
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
        Claim claim = plugin.claims().atChunk(player.getWorld().getName(), player.getChunk().getX(), player.getChunk().getZ());
        boolean owned = claim != null && claim.getOwner().equals(player.getUniqueId());
        boolean occupied = claim != null;

        if (!occupied) {
            button(inv, 13, Material.DIRT, "§a§lClaim ce chunk", "§7Le chunk actuel sera protégé.", "", "§7Chunk : §f" + player.getChunk().getX() + " / " + player.getChunk().getZ(), "§7Taille : §f16 × 16 blocs", "", "§eClique pour claim");
        } else if (owned) {
            button(inv, 13, Material.DIRT, "§a§lMon chunk", "§7Tu possèdes ce chunk.", "", "§7Chunk : §f" + player.getChunk().getX() + " / " + player.getChunk().getZ(), "§a✓ Protégé par NoxoClaim", "", "§eClique pour gérer");
        } else {
            button(inv, 13, Material.DIRT, "§c§lChunk protégé", "§7Ce chunk appartient à quelqu'un d'autre.", "", "§7Propriétaire : §f" + ownerName(claim), "§7Chunk : §f" + player.getChunk().getX() + " / " + player.getChunk().getZ());
        }

        button(inv, 10, Material.BOOK, "§b§lMes claims", "§7Voir tous tes chunks claimés.");
        button(inv, 16, Material.SHIELD, "§6§lProtections", "§7Gérer les protections du chunk actuel.");
        button(inv, 31, Material.COMPASS, "§d§lInfos du chunk", "§7Voir le propriétaire et l'état.");
        button(inv, 33, Material.PAPER, "§f§lAide", "§7Comprendre NoxoClaim en quelques secondes.");
        button(inv, 40, Material.BARRIER, "§c§lFermer", "§7Fermer le menu.");
        player.openInventory(inv);
    }

    public void openClaims(Player player) {
        List<Claim> claims = plugin.claims().owned(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, CLAIMS);
        fill(inv, Material.GRAY_STAINED_GLASS_PANE, " ");
        if (claims.isEmpty()) {
            button(inv, 22, Material.DIRT, "§e§lAucun claim", "§7Va dans un chunk libre et utilise", "§f/hclaim§7 pour en créer un.");
        } else {
            int slot = 10;
            for (Claim claim : claims) {
                if (slot >= 44) break;
                int cx = Math.floorDiv(claim.getMinX(), 16);
                int cz = Math.floorDiv(claim.getMinZ(), 16);
                button(inv, slot++, Material.DIRT, "§a§lChunk " + cx + " / " + cz,
                        "§7Monde : §f" + claim.getWorld(), "§7Taille : §f16 × 16", "§7Membres : §f" + claim.getMembers().size(), "", "§eClique pour te téléporter.");
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

    public void openHelp(Player player) {
        Inventory inv = Bukkit.createInventory(null, 45, HELP);
        fill(inv, Material.BLACK_STAINED_GLASS_PANE, " ");
        button(inv, 10, Material.DIRT, "§a§l1. Claim", "§7Va dans le chunk que tu veux protéger.", "§7Fais simplement §f/hclaim§7.");
        button(inv, 13, Material.BARRIER, "§c§l2. Unclaim", "§7Retourne dans ton chunk.", "§7Fais simplement §f/uclaim§7.");
        button(inv, 16, Material.BOOK, "§b§l3. Gérer", "§7Utilise §f/hclaim§7 pour ouvrir ce menu.", "§7Tout se fait sans outil.");
        button(inv, 22, Material.SHIELD, "§6§lProtection", "§7Les joueurs qui ne sont pas membres", "§7ne peuvent pas modifier ton chunk.");
        button(inv, 29, Material.PLAYER_HEAD, "§d§lMembres", "§7Ajoute un joueur avec :", "§f/hclaim trust <joueur>");
        button(inv, 40, Material.ARROW, "§fRetour", "§7Retour au menu.");
        player.openInventory(inv);
    }

    private String state(boolean enabled) { return enabled ? "§aActivé" : "§cDésactivé"; }
    private String ownerName(Claim claim) {
        String name = Bukkit.getOfflinePlayer(claim.getOwner()).getName();
        return name == null ? "Inconnu" : name;
    }

    private void fill(Inventory inv, Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(name); item.setItemMeta(meta); }
        for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null) inv.setItem(i, item.clone());
    }

    private void button(Inventory inv, int slot, Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(name); meta.setLore(Arrays.asList(lore)); item.setItemMeta(meta); }
        inv.setItem(slot, item);
    }

    private boolean isTitle(String title) { return title.equals(MAIN) || title.equals(CLAIMS) || title.equals(FLAGS) || title.equals(HELP); }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!isTitle(event.getView().getTitle())) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;
        String title = event.getView().getTitle();

        if (title.equals(MAIN)) {
            if (slot == 13) {
                Claim claim = plugin.claims().atChunk(player.getWorld().getName(), player.getChunk().getX(), player.getChunk().getZ());
                if (claim == null) {
                    execute(player, "hclaim");
                } else if (claim.getOwner().equals(player.getUniqueId())) {
                    openFlags(player, claim);
                } else {
                    player.sendMessage("§c§lNoxoClaim §8» §fCe chunk appartient à §e" + ownerName(claim) + "§f.");
                }
            } else if (slot == 10) openClaims(player);
            else if (slot == 16) {
                Claim claim = plugin.claims().atChunk(player.getWorld().getName(), player.getChunk().getX(), player.getChunk().getZ());
                if (claim == null) player.sendMessage("§e§lNoxoClaim §8» §fCe chunk n'est pas claimé.");
                else if (!claim.getOwner().equals(player.getUniqueId())) player.sendMessage("§c§lNoxoClaim §8» §fTu n'es pas propriétaire de ce chunk.");
                else openFlags(player, claim);
            } else if (slot == 31) execute(player, "hclaim info");
            else if (slot == 33) openHelp(player);
            else if (slot == 40) player.closeInventory();
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
                org.bukkit.Location target = claim.getHome();
                if (target == null) {
                    org.bukkit.World world = Bukkit.getWorld(claim.getWorld());
                    if (world != null) {
                        int x = claim.getMinX() + 8, z = claim.getMinZ() + 8;
                        target = new org.bukkit.Location(world, x + .5, world.getHighestBlockYAt(x, z) + 1, z + .5);
                    }
                }
                if (target != null) new fr.noxodev.noxoclaim.utils.TeleportTask(plugin, player, target,
                        plugin.getConfig().getInt("teleport.delay-seconds", 3), plugin.getConfig().getBoolean("teleport.cancel-on-move", true));
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

        if (title.equals(HELP) && slot == 40) openMain(player);
    }

    private void execute(Player player, String command) {
        player.performCommand(command);
        Bukkit.getScheduler().runTask(plugin, () -> openMain(player));
    }
}
