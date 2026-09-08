package fr.noxodev.noxoclaim.listeners;

import fr.noxodev.noxoclaim.NoxoClaim;
import fr.noxodev.noxoclaim.effects.ClaimEffects;
import fr.noxodev.noxoclaim.models.Claim;
import fr.noxodev.noxoclaim.models.ClaimFlag;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Central protection, boundary and automatic-claim listener. */
public final class ClaimProtectionListener implements Listener {
    private final NoxoClaim plugin;
    private final Map<UUID, UUID> lastClaimOwners = new HashMap<>();

    public ClaimProtectionListener(NoxoClaim plugin) { this.plugin = plugin; }

    private Claim claimAt(Location location) { return location == null ? null : plugin.claims().at(location); }
    private boolean bypass(Player player) { return player.hasPermission("noxoclaim.bypass"); }
    private boolean protectedAgainst(Claim claim, Player player) { return claim != null && !bypass(player) && !claim.isMember(player.getUniqueId()); }
    private boolean blocksProtected() { return plugin.getConfig().getBoolean("claim.protection.blocks", true); }
    private boolean allowed(Player player, Location location) { return !blocksProtected() || !protectedAgainst(claimAt(location), player); }

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
        if (event.getClickedBlock() != null && !allowed(event.getPlayer(), event.getClickedBlock().getLocation())) event.setCancelled(true);
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
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player attacker)) return;
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
    public void fire(BlockIgniteEvent event) {
        Claim claim = claimAt(event.getBlock().getLocation());
        if (claim != null && !claim.getFlag(ClaimFlag.FIRE)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void burn(BlockBurnEvent event) {
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
        if (source != null && destination != source && !source.getFlag(ClaimFlag.FLUIDS)) { event.setCancelled(true); return; }
        if (destination != null && destination != source && !destination.getFlag(ClaimFlag.FLUIDS)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void move(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getWorld() == event.getTo().getWorld()
                && event.getFrom().getChunk().getX() == event.getTo().getChunk().getX()
                && event.getFrom().getChunk().getZ() == event.getTo().getChunk().getZ()) return;

        Player player = event.getPlayer();
        Claim current = claimAt(event.getTo());
        if (current != null && !current.getFlag(ClaimFlag.ENTRY) && protectedAgainst(current, player)) {
            event.setTo(event.getFrom());
            return;
        }

        UUID currentOwner = current == null ? null : current.getOwner();
        UUID previousOwner = lastClaimOwners.get(player.getUniqueId());
        if (!lastClaimOwners.containsKey(player.getUniqueId()) || !Objects.equals(previousOwner, currentOwner)) {
            lastClaimOwners.put(player.getUniqueId(), currentOwner);
            if (current != null && plugin.getConfig().getBoolean("effects.welcome-title.enabled", true)) ClaimEffects.showWelcome(plugin, player, current);
        }

        if (current == null
                && plugin.getConfig().getBoolean("claim.auto-claim.enabled", true)
                && player.hasPermission(plugin.getConfig().getString("claim.auto-claim.permission", "noxoclaim.autoclaim"))
                && !bypass(player)) {
            boolean firstOnly = plugin.getConfig().getBoolean("claim.auto-claim.first-only", false);
            if (!firstOnly || plugin.claims().owned(player.getUniqueId()).isEmpty()) {
                var command = plugin.getCommand("claim");
                if (command != null && command.getExecutor() != null) command.getExecutor().onCommand(player, command, "claim", new String[0]);
            }
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) { lastClaimOwners.remove(event.getPlayer().getUniqueId()); }
}
