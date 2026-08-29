package fr.noxodev.noxoclaim.hud;

import fr.noxodev.noxoclaim.NoxoClaim;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Keeps the NoxoClaim HUD enabled for players joining after HUDEngine starts. */
public final class HudEngineListener implements Listener {
    private final NoxoClaim plugin;

    public HudEngineListener(NoxoClaim plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (plugin.isEnabled() && plugin.hudEngine() != null) plugin.hudEngine().show(event.getPlayer());
        }, 10L);
    }
}
