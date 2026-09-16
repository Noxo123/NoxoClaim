package fr.noxodev.noxoclaim.effects;

import fr.noxodev.noxoclaim.NoxoClaim;
import fr.noxodev.noxoclaim.models.Claim;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Efficient temporary claim border visualizer. */
public final class ClaimVisualizer {
    private static final Map<UUID, BukkitRunnable> ACTIVE = new ConcurrentHashMap<>();

    private ClaimVisualizer() {}

    public static void show(NoxoClaim plugin, Player player, Claim claim, int durationTicks) {
        showMany(plugin, player, List.of(claim), durationTicks);
    }

    public static void showMany(NoxoClaim plugin, Player player, Collection<Claim> claims, int durationTicks) {
        if (!plugin.getConfig().getBoolean("effects.particles.enabled", true)) return;

        stop(player);

        List<Claim> visible = claims.stream()
                .filter(c -> c != null && c.getWorld().equals(player.getWorld().getName()))
                .toList();
        if (visible.isEmpty()) return;

        World world = player.getWorld();
        Particle particle = parse(plugin.getConfig().getString(
                "map.particles.type",
                plugin.getConfig().getString("effects.particles.type", "END_ROD")));
        double step = Math.max(2.0, plugin.getConfig().getDouble("effects.particles.step", 1.0));
        long interval = Math.max(4L, plugin.getConfig().getLong("effects.particles.interval-ticks", 10L));
        int duration = Math.max(1, durationTicks);
        double configuredRadius = plugin.getConfig().getDouble("effects.particles.radius-view-distance", 64.0);
        double radius = Math.max(8.0, Math.min(64.0, configuredRadius));
        double radiusSquared = radius * radius;

        BukkitRunnable task = new BukkitRunnable() {
            int elapsed;

            @Override
            public void run() {
                if (!player.isOnline() || elapsed >= duration) {
                    stop(player);
                    return;
                }

                Location playerLocation = player.getLocation();
                int y = Math.max(playerLocation.getBlockY(), world.getMinHeight() + 1);
                double playerX = playerLocation.getX();
                double playerZ = playerLocation.getZ();

                for (Claim claim : visible) {
                    spawnBorder(world, particle, claim, y, playerX, playerZ, radiusSquared, step);
                }
                elapsed += interval;
            }
        };

        ACTIVE.put(player.getUniqueId(), task);
        task.runTaskTimer(plugin, 0L, interval);
    }

    private static void spawnBorder(World world, Particle particle, Claim claim, int y,
                                    double playerX, double playerZ, double radiusSquared, double step) {
        double minX = claim.getMinX() + 0.5;
        double maxX = claim.getMaxX() + 1.0 - 0.05;
        double minZ = claim.getMinZ() + 0.5;
        double maxZ = claim.getMaxZ() + 1.0 - 0.05;
        double py = y + 0.15;

        for (double x = minX; x <= maxX; x += step) {
            if (distanceSquared(x, minZ, playerX, playerZ) <= radiusSquared) spawn(world, particle, x, py, minZ);
            if (distanceSquared(x, maxZ, playerX, playerZ) <= radiusSquared) spawn(world, particle, x, py, maxZ);
        }
        for (double z = minZ; z <= maxZ; z += step) {
            if (distanceSquared(minX, z, playerX, playerZ) <= radiusSquared) spawn(world, particle, minX, py, z);
            if (distanceSquared(maxX, z, playerX, playerZ) <= radiusSquared) spawn(world, particle, maxX, py, z);
        }
    }

    private static double distanceSquared(double x1, double z1, double x2, double z2) {
        double dx = x1 - x2;
        double dz = z1 - z2;
        return dx * dx + dz * dz;
    }

    public static void stop(Player player) {
        BukkitRunnable task = ACTIVE.remove(player.getUniqueId());
        if (task != null) task.cancel();
    }

    private static void spawn(World world, Particle particle, double x, double y, double z) {
        world.spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0);
    }

    private static Particle parse(String value) {
        try {
            return Particle.valueOf(value.toUpperCase());
        } catch (Exception ignored) {
            return Particle.END_ROD;
        }
    }
}
