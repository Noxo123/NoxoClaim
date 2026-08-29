package fr.noxodev.noxoclaim.effects;

import fr.noxodev.noxoclaim.NoxoClaim;
import fr.noxodev.noxoclaim.models.Claim;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/** Visualises the exact 16x16 claim border without flooding the server. */
public final class ClaimVisualizer {
    private ClaimVisualizer() {}

    public static void show(NoxoClaim plugin, Player player, Claim claim, int durationTicks) {
        World world = player.getWorld();
        if (!world.getName().equals(claim.getWorld())) return;
        Particle particle = parse(plugin.getConfig().getString("map.particles.type", "END_ROD"));
        int minX = claim.getMinX(), maxX = claim.getMaxX() + 1;
        int minZ = claim.getMinZ(), maxZ = claim.getMaxZ() + 1;
        int y = Math.max(player.getLocation().getBlockY(), world.getMinHeight() + 1);
        new BukkitRunnable() {
            int ticks;
            @Override public void run() {
                if (!player.isOnline() || ticks >= durationTicks) { cancel(); return; }
                for (double x = minX + 0.5; x <= maxX - 0.5; x += 1.0) {
                    spawn(world, particle, new Location(world, x, y + 0.15, minZ + 0.05));
                    spawn(world, particle, new Location(world, x, y + 0.15, maxZ - 0.05));
                }
                for (double z = minZ + 0.5; z <= maxZ - 0.5; z += 1.0) {
                    spawn(world, particle, new Location(world, minX + 0.05, y + 0.15, z));
                    spawn(world, particle, new Location(world, maxX - 0.05, y + 0.15, z));
                }
                ticks += 5;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private static void spawn(World world, Particle particle, Location location) {
        world.spawnParticle(particle, location, 1, 0, 0, 0, 0);
    }

    private static Particle parse(String value) {
        try { return Particle.valueOf(value.toUpperCase()); }
        catch (Exception ignored) { return Particle.END_ROD; }
    }
}
