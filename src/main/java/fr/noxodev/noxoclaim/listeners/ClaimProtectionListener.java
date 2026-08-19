package fr.noxodev.noxoclaim.listeners;

import fr.noxodev.noxoclaim.NoxoClaim;
import fr.noxodev.noxoclaim.models.Claim;
import fr.noxodev.noxoclaim.models.ClaimFlag;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public final class ClaimProtectionListener implements Listener {
    private final NoxoClaim plugin;

    public ClaimProtectionListener(NoxoClaim plugin) {
        this.plugin = plugin;
    }

    private Claim claimAt(org.bukkit.Location location) {
        return location == null ? null : plugin.claims().at(location);
    }

    private boolean bypass(Player player) {
        return player.hasPermission("noxoclaim.bypass");
    }

    private boolean allowed(Player player, org.bukkit.Location location) {
        Claim claim = claimAt(location);
        return claim == null || bypass(player) || claim.isMember(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent event) {
        if (!allowed(event.getPlayer(), event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void place(BlockPlaceEvent event) {
        if (!allowed(event.getPlayer(), event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void interact(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null && !allowed(event.getPlayer(), event.getClickedBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void bucketEmpty(PlayerBucketEmptyEvent event) {
        if (!allowed(event.getPlayer(), event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void bucketFill(PlayerBucketFillEvent event) {
        if (!allowed(event.getPlayer(), event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void pvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Entity damager = event.getDamager();
        if (!(damager instanceof Player attacker)) return;

        Claim claim = claimAt(victim.getLocation());
        if (claim != null && !claim.getFlag(ClaimFlag.PVP) && !bypass(attacker)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void explode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> {
            Claim claim = claimAt(block.getLocation());
            return claim != null && !claim.getFlag(ClaimFlag.EXPLOSIONS);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void blockBurn(BlockBurnEvent event) {
        Claim claim = claimAt(event.getBlock().getLocation());
        if (claim != null && !claim.getFlag(ClaimFlag.FIRE)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void ignite(BlockIgniteEvent event) {
        Claim claim = claimAt(event.getBlock().getLocation());
        if (claim != null && !claim.getFlag(ClaimFlag.FIRE)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void mobGrief(EntityChangeBlockEvent event) {
        Claim claim = claimAt(event.getBlock().getLocation());
        if (claim != null && !claim.getFlag(ClaimFlag.MOB_GRIEFING)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fluidFlow(BlockFromToEvent event) {
        Claim source = claimAt(event.getBlock().getLocation());
        Claim destination = claimAt(event.getToBlock().getLocation());
        if (destination != null && destination != source && source == null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void entry(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()
                && event.getFrom().getWorld() == event.getTo().getWorld()) return;

        Claim destination = claimAt(event.getTo());
        if (destination != null && !destination.getFlag(ClaimFlag.ENTRY)
                && !bypass(event.getPlayer()) && !destination.isMember(event.getPlayer().getUniqueId())) {
            event.setTo(event.getFrom());
        }
    }
}
