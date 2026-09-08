package fr.noxodev.noxoclaim.hud;

import fr.noxodev.noxoclaim.NoxoClaim;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/** Keeps the NoxoClaim HUD synchronized with player position changes. */
public final class HudEngineListener implements Listener {
    private final NoxoClaim plugin;

    public HudEngineListener(NoxoClaim plugin) { this.plugin = plugin; }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (plugin.isEnabled() && plugin.hudEngine() != null) plugin.hudEngine().show(event.getPlayer());
        }, 10L);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (event.getTo() == null || event.getFrom().getWorld() == null || event.getTo().getWorld() == null) return;
        if (event.getFrom().getWorld().equals(event.getTo().getWorld())
                && event.getFrom().getChunk().getX() == event.getTo().getChunk().getX()
                && event.getFrom().getChunk().getZ() == event.getTo().getChunk().getZ()) return;
        if (plugin.hudEngine() == null || !plugin.hudEngine().isReady()) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.hudEngine().refresh(player));
    }
}
