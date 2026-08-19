package fr.noxodev.noxoclaim.listeners;
import fr.noxodev.noxoclaim.NoxoClaim; import fr.noxodev.noxoclaim.models.*; import org.bukkit.event.*; import org.bukkit.event.block.*; import org.bukkit.event.entity.*; import org.bukkit.event.player.*; import org.bukkit.entity.Player;
public final class ClaimProtectionListener implements Listener {private final NoxoClaim p;public ClaimProtectionListener(NoxoClaim p){this.p=p;}private boolean allowed(Player x,org.bukkit.Location l){Claim c=p.claims().at(l);return c==null||x.hasPermission("noxoclaim.bypass")||c.isMember(x.getUniqueId());}
@EventHandler public void breakBlock(BlockBreakEvent e){if(!allowed(e.getPlayer(),e.getBlock().getLocation()))e.setCancelled(true);}
@EventHandler public void place(BlockPlaceEvent e){if(!allowed(e.getPlayer(),e.getBlock().getLocation()))e.setCancelled(true);}
@EventHandler public void interact(PlayerInteractEvent e){if(e.getClickedBlock()!=null&&!allowed(e.getPlayer(),e.getClickedBlock().getLocation()))e.setCancelled(true);}
@EventHandler public void pvp(EntityDamageByEntityEvent e){if(e.getEntity() instanceof Player victim&&e.getDamager() instanceof Player attacker){Claim c=p.claims().at(victim.getLocation());if(c!=null&&!c.getFlag(ClaimFlag.PVP)&&!c.isMember(attacker.getUniqueId()))e.setCancelled(true);}}
@EventHandler public void explode(EntityExplodeEvent e){Claim c=p.claims().at(e.getLocation());if(c!=null&&!c.getFlag(ClaimFlag.EXPLOSIONS))e.blockList().removeIf(b->c.contains(b.getLocation()));}
}
