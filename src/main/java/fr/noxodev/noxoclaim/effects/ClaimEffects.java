package fr.noxodev.noxoclaim.effects;

import fr.noxodev.noxoclaim.NoxoClaim;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public final class ClaimEffects {
    private ClaimEffects() {}

    public static void onClaim(NoxoClaim plugin, Player player) {
        if (plugin.getConfig().getBoolean("effects.particles.enabled", true)) animate(plugin, player.getLocation(), player);
        if (plugin.getConfig().getBoolean("effects.welcome-title.enabled", true)) {
            String title = plugin.getConfig().getString("effects.welcome-title.title", "§b§lBienvenue !");
            String subtitle = plugin.getConfig().getString("effects.welcome-title.subtitle", "§7Ce chunk vous appartient maintenant ✨");
            player.sendTitle(title, subtitle,
                    plugin.getConfig().getInt("effects.welcome-title.fade-in", 10),
                    plugin.getConfig().getInt("effects.welcome-title.stay", 50),
                    plugin.getConfig().getInt("effects.welcome-title.fade-out", 15));
        }
    }

    private static void animate(NoxoClaim plugin, Location origin, Player player) {
        World world = origin.getWorld(); if (world == null) return;
        int cx = origin.getBlockX() >> 4, cz = origin.getBlockZ() >> 4;
        int minX = cx * 16, maxX = minX + 16, minZ = cz * 16, maxZ = minZ + 16;
        Particle particle = parse(plugin.getConfig().getString("effects.particles.type", "HAPPY_VILLAGER"));
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (!player.isOnline() || tick++ >= plugin.getConfig().getInt("effects.particles.duration-ticks", 80)) { cancel(); return; }
                double y = player.getLocation().getY() + 0.2 + Math.sin(tick * 0.18) * 0.15;
                double progress = (tick % 40) / 40.0;
                double x = minX + progress * 16.0, z = minZ + progress * 16.0;
                world.spawnParticle(particle, x, y, minZ + 0.2, 2, 0, 0, 0, 0);
                world.spawnParticle(particle, maxX - 0.2, y, minZ + 0.2, 2, 0, 0, 0, 0);
                world.spawnParticle(particle, x, y, maxZ - 0.2, 2, 0, 0, 0, 0);
                world.spawnParticle(particle, minX + 0.2, y, z, 2, 0, 0, 0, 0);
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private static Particle parse(String name) {
        try { return Particle.valueOf(name.toUpperCase()); }
        catch (Exception ignored) { return Particle.HAPPY_VILLAGER; }
    }
}
