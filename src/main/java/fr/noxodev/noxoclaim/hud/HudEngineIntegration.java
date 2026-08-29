package fr.noxodev.noxoclaim.hud;

import fr.noxodev.noxoclaim.NoxoClaim;
import fr.noxodev.noxoclaim.models.Claim;
import io.github.nacvark.hudengine.api.HudEngine;
import io.github.nacvark.hudengine.api.HudEngineProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Optional;

/** Optional HUDEngine bridge. NoxoClaim keeps working when HUDEngine is unavailable. */
public final class HudEngineIntegration {
    public static final String HUD_KEY = "noxoclaim:minimap";
    private final NoxoClaim plugin;
    private HudEngine engine;
    private boolean ready;

    public HudEngineIntegration(NoxoClaim plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("hudengine.enabled", true)) {
            plugin.getLogger().info("HUDEngine : intégration désactivée dans la configuration.");
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, this::resolve, 20L);
    }

    private void resolve() {
        Optional<HudEngine> found = HudEngineProvider.find();
        if (found.isEmpty()) {
            plugin.getLogger().info("HUDEngine : non disponible. NoxoClaim continue sans HUD.");
            return;
        }
        engine = found.get();
        if (!engine.isRunning()) {
            plugin.getLogger().warning("HUDEngine est chargé mais son moteur n'est pas opérationnel.");
            return;
        }

        engine.values().register("noxoclaim:map", this::renderMap);
        engine.values().register("noxoclaim:chunk", p -> {
            Claim claim = plugin.claims().at(p.getLocation());
            return claim == null ? "Libre" : claim.getName();
        });
        ready = true;
        plugin.getLogger().info("HUDEngine : intégration NoxoClaim activée.");
        for (Player player : Bukkit.getOnlinePlayers()) show(player);
    }

    public boolean isReady() { return ready && engine != null && engine.isRunning(); }

    public void show(Player player) {
        if (!isReady() || !engine.hasHud(HUD_KEY)) return;
        engine.player(player).show(HUD_KEY);
        engine.player(player).refresh();
    }

    public void hide(Player player) {
        if (!isReady()) return;
        engine.player(player).hide(HUD_KEY);
        engine.player(player).refresh();
    }

    public void refresh(Player player) {
        if (!isReady()) return;
        engine.player(player).refresh();
    }

    private String renderMap(Player player) {
        int centerX = player.getLocation().getBlockX() >> 4;
        int centerZ = player.getLocation().getBlockZ() >> 4;
        StringBuilder map = new StringBuilder();
        for (int dz = -4; dz <= 4; dz++) {
            if (dz != -4) map.append('\n');
            for (int dx = -4; dx <= 4; dx++) {
                if (dx != -4) map.append(' ');
                if (dx == 0 && dz == 0) {
                    map.append('●');
                    continue;
                }
                Claim claim = plugin.claims().atChunk(player.getWorld().getName(), centerX + dx, centerZ + dz);
                if (claim == null) map.append('·');
                else if (claim.getOwner().equals(player.getUniqueId())) map.append('■');
                else map.append('□');
            }
        }
        return map.toString();
    }
}
