package fr.noxodev.noxoclaim.effects;

import fr.noxodev.noxoclaim.NoxoClaim;
import fr.noxodev.noxoclaim.models.Claim;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Efficient temporary claim border visualizer. */
public final class ClaimVisualizer {
    private static final Map<UUID, BukkitRunnable> ACTIVE = new ConcurrentHashMap<>();
    private ClaimVisualizer() {}

    public static void show(NoxoClaim plugin, Player player, Claim claim, int durationTicks) {
        showMany(plugin, player, java.util.List.of(claim), durationTicks);
    }

    public static void showMany(NoxoClaim plugin, Player player, java.util.Collection<Claim> claims, int durationTicks) {
        if (!plugin.getConfig().getBoolean("effects.particles.enabled", true)) return;
        stop(player);
        java.util.List<Claim> visible=claims.stream().filter(c->c!=null&&c.getWorld().equals(player.getWorld().getName())).toList();
        if(visible.isEmpty())return;
        World world=player.getWorld();
        Particle particle=parse(plugin.getConfig().getString("map.particles.type",plugin.getConfig().getString("effects.particles.type","END_ROD")));
        double step=Math.max(1.0,plugin.getConfig().getDouble("effects.particles.step",1.0));
        long interval=Math.max(2L,plugin.getConfig().getLong("effects.particles.interval-ticks",10L));
        int duration=Math.max(1,durationTicks);
        BukkitRunnable task=new BukkitRunnable(){int elapsed;public void run(){
            if(!player.isOnline()||elapsed>=duration){stop(player);return;}
            int y=Math.max(player.getLocation().getBlockY(),world.getMinHeight()+1);
            for(Claim c:visible){int minX=c.getMinX(),maxX=c.getMaxX()+1,minZ=c.getMinZ(),maxZ=c.getMaxZ()+1;
                for(double x=minX+.5;x<maxX;x+=step){spawn(world,particle,new Location(world,x,y+.15,minZ+.05));spawn(world,particle,new Location(world,x,y+.15,maxZ-.05));}
                for(double z=minZ+.5;z<maxZ;z+=step){spawn(world,particle,new Location(world,minX+.05,y+.15,z));spawn(world,particle,new Location(world,maxX-.05,y+.15,z));}
            }
            elapsed+=interval;
        }};
        ACTIVE.put(player.getUniqueId(),task);task.runTaskTimer(plugin,0L,interval);
    }

    public static void stop(Player player){BukkitRunnable task=ACTIVE.remove(player.getUniqueId());if(task!=null)task.cancel();}
    private static void spawn(World world,Particle particle,Location location){world.spawnParticle(particle,location,1,0,0,0,0);}
    private static Particle parse(String value){try{return Particle.valueOf(value.toUpperCase());}catch(Exception ignored){return Particle.END_ROD;}}
}
