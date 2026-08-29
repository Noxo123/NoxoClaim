package fr.noxodev.noxoclaim.listeners;

import fr.noxodev.noxoclaim.NoxoClaim;
import fr.noxodev.noxoclaim.effects.ClaimEffects;
import fr.noxodev.noxoclaim.effects.ClaimVisualizer;
import fr.noxodev.noxoclaim.models.*;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.UUID;

/** Central protection and automatic-claim listener. */
public final class ClaimProtectionListener implements Listener {
    private final NoxoClaim plugin;
    public ClaimProtectionListener(NoxoClaim plugin){this.plugin=plugin;}

    private boolean allowed(Player player, Location location){
        Claim c=plugin.claims().at(location);
        return c==null || player.hasPermission("noxoclaim.bypass") || c.isMember(player.getUniqueId());
    }
    private boolean protectedAgainst(Claim c, Player p){return c!=null&&!p.hasPermission("noxoclaim.bypass")&&!c.isMember(p.getUniqueId());}

    @EventHandler public void breakBlock(BlockBreakEvent e){if(!allowed(e.getPlayer(),e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler public void place(BlockPlaceEvent e){if(!allowed(e.getPlayer(),e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler public void interact(PlayerInteractEvent e){if(e.getClickedBlock()!=null&&!allowed(e.getPlayer(),e.getClickedBlock().getLocation()))e.setCancelled(true);}

    @EventHandler public void pvp(EntityDamageByEntityEvent e){
        if(e.getEntity() instanceof Player victim&&e.getDamager() instanceof Player attacker){
            Claim c=plugin.claims().at(victim.getLocation());
            if(c!=null&&!c.getFlag(ClaimFlag.PVP)&&protectedAgainst(c,attacker))e.setCancelled(true);
        }
    }
    @EventHandler public void explode(EntityExplodeEvent e){Claim c=plugin.claims().at(e.getLocation());if(c!=null&&!c.getFlag(ClaimFlag.EXPLOSIONS))e.blockList().removeIf(b->c.contains(b.getLocation()));}
    @EventHandler public void fire(BlockIgniteEvent e){Claim c=plugin.claims().at(e.getBlock().getLocation());if(c!=null&&!c.getFlag(ClaimFlag.FIRE))e.setCancelled(true);}
    @EventHandler public void burn(BlockBurnEvent e){Claim c=plugin.claims().at(e.getBlock().getLocation());if(c!=null&&!c.getFlag(ClaimFlag.FIRE))e.setCancelled(true);}
    @EventHandler public void mobGrief(EntityChangeBlockEvent e){Claim c=plugin.claims().at(e.getBlock().getLocation());if(c!=null&&!c.getFlag(ClaimFlag.MOB_GRIEFING))e.setCancelled(true);}

    @EventHandler public void move(PlayerMoveEvent e){
        if(e.getTo()==null)return;
        if(e.getFrom().getWorld()==e.getTo().getWorld()&&e.getFrom().getChunk().getX()==e.getTo().getChunk().getX()&&e.getFrom().getChunk().getZ()==e.getTo().getChunk().getZ())return;
        Player p=e.getPlayer(); Claim previous=plugin.claims().at(e.getFrom()); Claim current=plugin.claims().at(e.getTo());
        if(current!=null&&!current.getFlag(ClaimFlag.ENTRY)&&protectedAgainst(current,p)){
            e.setTo(e.getFrom());
            return;
        }
        if(current!=null&&previous!=current&&plugin.getConfig().getBoolean("effects.welcome-title.enabled",true)) ClaimEffects.showWelcome(plugin,p,current);
        if(current==null && plugin.getConfig().getBoolean("claim.auto-claim.enabled",true) && p.hasPermission(plugin.getConfig().getString("claim.auto-claim.permission","noxoclaim.autoclaim")) && !p.hasPermission("noxoclaim.bypass")){
            if(!plugin.getConfig().getBoolean("claim.auto-claim.first-only",false) || plugin.claims().owned(p.getUniqueId()).isEmpty()){
                plugin.getCommand("claim").getExecutor().onCommand(p,plugin.getCommand("claim"),"claim",new String[0]);
            }
        }
    }
}
