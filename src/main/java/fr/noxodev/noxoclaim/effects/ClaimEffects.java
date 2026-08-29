package fr.noxodev.noxoclaim.effects;

import fr.noxodev.noxoclaim.NoxoClaim;
import fr.noxodev.noxoclaim.models.Claim;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/** Lightweight visual effects for claim creation and claim inspection. */
public final class ClaimEffects {
    private ClaimEffects() {}

    public static void onClaim(NoxoClaim plugin, Player player) {
        Claim claim = plugin.claims().at(player.getLocation());
        if (claim != null && plugin.getConfig().getBoolean("effects.particles.enabled", true))
            ClaimVisualizer.show(plugin, player, claim, plugin.getConfig().getInt("effects.particles.duration-ticks", 100));
        if (claim != null) showWelcome(plugin, player, claim);
    }

    public static void showWelcome(NoxoClaim plugin, Player player, Claim claim) {
        if (!plugin.getConfig().getBoolean("effects.welcome-title.enabled", true)) return;
        String owner = org.bukkit.Bukkit.getOfflinePlayer(claim.getOwner()).getName();
        if (owner == null) owner = "Inconnu";
        String title = color(plugin.getConfig().getString("effects.welcome-title.title", "&b&lBienvenue !"));
        String subtitle = color(plugin.getConfig().getString("effects.welcome-title.subtitle", "&7Bienvenue dans le territoire de &f%owner%&7 !").replace("%owner%", owner));
        player.sendTitle(title, subtitle,
                plugin.getConfig().getInt("effects.welcome-title.fade-in", 10),
                plugin.getConfig().getInt("effects.welcome-title.stay", 40),
                plugin.getConfig().getInt("effects.welcome-title.fade-out", 15));
    }

    private static String color(String s) { return org.bukkit.ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
}
