package fr.noxodev.noxoclaim.gui;

import fr.noxodev.noxoclaim.NoxoClaim;
import fr.noxodev.noxoclaim.commands.ClaimCommand;
import fr.noxodev.noxoclaim.models.Claim;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;

public final class ClaimGui implements Listener {
    private static final String TITLE = "§8NoxoClaim §7• Carte";
    private final NoxoClaim p;
    private final Map<UUID, MapState> states = new HashMap<>();
    private final Map<Integer, int[]> slots = new HashMap<>();

    public ClaimGui(NoxoClaim p) { this.p = p; p.getServer().getPluginManager().registerEvents(this, p); }

    public void open(Player player, String name) {
        MapState state = states.computeIfAbsent(player.getUniqueId(), u -> new MapState());
        state.name = name;
        state.anchor = null; state.end = null;
        draw(player, state);
    }

    private void draw(Player player, MapState state) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);
        Chunk center = player.getLocation().getChunk();
        for (int row = 0; row < 5; row++) for (int col = 0; col < 5; col++) {
            int cx = center.getX() + col - 2, cz = center.getZ() + row - 2, slot = 10 + row * 9 + col;
            slots.put(slot, new int[]{cx, cz});
            Claim claim = p.claims().atChunk(player.getWorld().getName(), cx, cz);
            boolean selected = state.anchor != null && state.end != null && cx >= Math.min(state.anchor[0], state.end[0]) && cx <= Math.max(state.anchor[0], state.end[0]) && cz >= Math.min(state.anchor[1], state.end[1]) && cz <= Math.max(state.anchor[1], state.end[1]);
            if (selected && claim == null) item(inv, slot, Material.YELLOW_CONCRETE, "§eChunk sélectionné", List.of("§7Cliquez sur Confirmer pour acheter."));
            else if (claim != null && claim.getOwner().equals(player.getUniqueId())) item(inv, slot, Material.LIME_CONCRETE, "§aÀ vous §f• " + claim.getName(), List.of("§7Cliquez pour vous téléporter."));
            else if (claim != null) item(inv, slot, Material.RED_CONCRETE, "§cOccupé", List.of("§7Ce chunk appartient à un autre joueur."));
            else item(inv, slot, Material.GRAY_CONCRETE, "§7Libre", List.of("§7Cliquez pour sélectionner ce chunk."));
        }
        item(inv, 45, Material.PAPER, "§bNom : §f" + state.name, List.of("§7Nom utilisé lors de l'achat."));
        long chunks = state.anchor != null && state.end != null ? ((long)Math.abs(state.end[0] - state.anchor[0]) + 1) * ((long)Math.abs(state.end[1] - state.anchor[1]) + 1) : 0;
        double cost = chunks * p.chunkPrice();
        item(inv, 49, Material.GOLD_INGOT, "§6Prix : §f" + p.formatMoney(cost), List.of("§7" + chunks + " chunk(s)", "§7Prix : " + p.formatMoney(p.chunkPrice()) + " / chunk"));
        item(inv, 53, Material.EMERALD_BLOCK, "§aConfirmer l'achat", List.of("§7Achat de " + chunks + " chunk(s)", "§7Nom : " + state.name));
        item(inv, 48, Material.BARRIER, "§cAnnuler", List.of("§7Fermer la carte"));
        player.openInventory(inv);
    }

    private void item(Inventory inv, int slot, Material mat, String name, List<String> lore) {
        ItemStack i = new ItemStack(mat); ItemMeta m = i.getItemMeta(); m.setDisplayName(name); m.setLore(lore); i.setItemMeta(m); inv.setItem(slot, i);
    }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals(TITLE) || !(e.getWhoClicked() instanceof Player player)) return;
        e.setCancelled(true);
        MapState state = states.computeIfAbsent(player.getUniqueId(), u -> new MapState());
        if (e.getRawSlot() == 48) { player.closeInventory(); return; }
        if (e.getRawSlot() == 53) {
            if (state.anchor == null || state.end == null) { player.sendMessage("§cSélectionnez d'abord deux chunks."); return; }
            int minX = Math.min(state.anchor[0], state.end[0]), maxX = Math.max(state.anchor[0], state.end[0]);
            int minZ = Math.min(state.anchor[1], state.end[1]), maxZ = Math.max(state.anchor[1], state.end[1]);
            if (((long)maxX - minX + 1) * ((long)maxZ - minZ + 1) > p.getConfig().getLong("claim.max-chunks-per-purchase", 100)) { player.sendMessage("§cTrop de chunks sélectionnés."); return; }
            if (((ClaimCommand)p.getCommand("claim").getExecutor()).createChunks(player, minX, minZ, maxX, maxZ, state.name)) player.closeInventory();
            else draw(player, state);
            return;
        }
        int[] pos = slots.get(e.getRawSlot());
        if (pos == null) return;
        Claim claim = p.claims().atChunk(player.getWorld().getName(), pos[0], pos[1]);
        if (claim != null) {
            if (claim.getOwner().equals(player.getUniqueId())) {
                Location target = claim.getHome();
                if (target == null) target = new Location(player.getWorld(), pos[0] * 16 + 8.5, player.getWorld().getHighestBlockYAt(pos[0] * 16 + 8, pos[1] * 16 + 8) + 1, pos[1] * 16 + 8.5);
                player.closeInventory();
                new fr.noxodev.noxoclaim.utils.TeleportTask(p, player, target, p.getConfig().getInt("teleport.delay-seconds", 3), p.getConfig().getBoolean("teleport.cancel-on-move", true));
            } else player.sendMessage("§cCe chunk est déjà occupé.");
            return;
        }
        if (state.anchor == null) state.anchor = pos;
        else if (state.end == null) state.end = pos;
        else state.end = pos;
        draw(player, state);
    }

    private static final class MapState { String name = "mon-claim"; int[] anchor, end; }
}
