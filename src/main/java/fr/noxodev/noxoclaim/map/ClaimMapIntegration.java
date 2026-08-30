package fr.noxodev.noxoclaim.map;

import fr.noxodev.noxoclaim.NoxoClaim;
import fr.noxodev.noxoclaim.models.Claim;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**

Intégration optionnelle avec le client NoxoClaim.





*/
public final class ClaimMapIntegration implements PluginMessageListener {

public static final String CHANNEL = "noxoclaim:map";

private static final int PROTOCOL_VERSION = 1;
private static final int INITIAL_BUFFER_SIZE = 32768;

private final NoxoClaim plugin;
private final Set<UUID> clients = new HashSet<>();

public ClaimMapIntegration(NoxoClaim plugin) {
    this.plugin = plugin;
    registerChannels();
}

/**
 * Enregistre les canaux Bukkit.
 */
private void registerChannels() {
    plugin.getServer()
            .getMessenger()
            .registerOutgoingPluginChannel(plugin, CHANNEL);

    plugin.getServer()
            .getMessenger()
            .registerIncomingPluginChannel(
                    plugin,
                    CHANNEL,
                    this
            );
}

/**
 * Vérifie si le joueur utilise le client NoxoClaim.
 *
 * @param player joueur
 * @return true si le joueur utilise le client NoxoClaim
 */
public boolean isMapClient(Player player) {
    return player != null
            && clients.contains(player.getUniqueId());
}

/**
 * Oublie un joueur.
 *
 * @param player joueur
 */
public void forget(Player player) {
    if (player == null) {
        return;
    }

    clients.remove(player.getUniqueId());
}

/**
 * Effectue le handshake avec le client NoxoClaim.
 *
 * @param player joueur
 */
public void handshake(Player player) {
    if (player == null || !player.isOnline()) {
        return;
    }

    clients.add(player.getUniqueId());

    send(player, buildHelloMessage());
    sync(player);
}

/**
 * Construit le message HELLO.
 *
 * @return message JSON
 */
private String buildHelloMessage() {
    String version = plugin.getDescription().getVersion();

    return "{"
            + "\"type\":\"hello\","
            + "\"protocol\":"
            + PROTOCOL_VERSION
            + ","
            + "\"plugin\":\"NoxoClaim\","
            + "\"version\":\""
            + escapeJson(version)
            + "\""
            + "}";
}

/**
 * Synchronise tous les clients NoxoClaim connectés.
 */
public void syncAll() {
    for (UUID uuid : List.copyOf(clients)) {
        Player player = Bukkit.getPlayer(uuid);

        if (player == null || !player.isOnline()) {
            clients.remove(uuid);
            continue;
        }

        sync(player);
    }
}

/**
 * Envoie la totalité des claims à un joueur.
 *
 * @param player joueur
 */
public void sync(Player player) {
    if (!isMapClient(player)) {
        return;
    }

    send(player, buildSnapshot(player));
}

/**
 * Construit le snapshot complet des claims.
 *
 * @param player joueur
 * @return snapshot JSON
 */
private String buildSnapshot(Player player) {
    StringBuilder json =
            new StringBuilder(INITIAL_BUFFER_SIZE);

    json.append("{")
            .append("\"type\":\"snapshot\",")
            .append("\"protocol\":")
            .append(PROTOCOL_VERSION)
            .append(",")
            .append("\"claims\":[");

    boolean first = true;

    for (Claim claim : plugin.claims().all()) {
        if (!first) {
            json.append(',');
        }

        first = false;

        appendClaim(json, claim, player);
    }

    json.append("]}");

    return json.toString();
}

/**
 * Ajoute un claim au JSON.
 *
 * @param json builder JSON
 * @param claim claim
 * @param player joueur destinataire
 */
private void appendClaim(
        StringBuilder json,
        Claim claim,
        Player player
) {
    UUID ownerUuid = claim.getOwner();

    String ownerName = Optional
            .ofNullable(
                    Bukkit.getOfflinePlayer(ownerUuid).getName()
            )
            .orElse("Unknown");

    int minChunkX =
            Math.floorDiv(claim.getMinX(), 16);

    int minChunkZ =
            Math.floorDiv(claim.getMinZ(), 16);

    int maxChunkX =
            Math.floorDiv(claim.getMaxX(), 16);

    int maxChunkZ =
            Math.floorDiv(claim.getMaxZ(), 16);

    boolean mine =
            ownerUuid.equals(player.getUniqueId());

    json.append("{")

            .append("\"id\":\"")
            .append(
                    escapeJson(
                            String.valueOf(claim.getId())
                    )
            )
            .append("\",")

            .append("\"world\":\"")
            .append(
                    escapeJson(
                            claim.getWorld()
                    )
            )
            .append("\",")

            .append("\"owner\":\"")
            .append(ownerUuid)
            .append("\",")

            .append("\"ownerName\":\"")
            .append(
                    escapeJson(ownerName)
            )
            .append("\",")

            .append("\"name\":\"")
            .append(
                    escapeJson(
                            claim.getName()
                    )
            )
            .append("\",")

            .append("\"minChunkX\":")
            .append(minChunkX)
            .append(",")

            .append("\"minChunkZ\":")
            .append(minChunkZ)
            .append(",")

            .append("\"maxChunkX\":")
            .append(maxChunkX)
            .append(",")

            .append("\"maxChunkZ\":")
            .append(maxChunkZ)
            .append(",")

            .append("\"mine\":")
            .append(mine)

            .append("}");
}

/**
 * Informe les clients qu'un claim a changé.
 *
 * @param claim claim modifié
 * @param action action effectuée
 */
public void claimChanged(
        Claim claim,
        String action
) {
    if (claim == null || action == null || action.isBlank()) {
        return;
    }

    String message =
            buildClaimChangedMessage(
                    claim,
                    action
            );

    for (UUID uuid : List.copyOf(clients)) {
        Player player = Bukkit.getPlayer(uuid);

        if (player == null || !player.isOnline()) {
            clients.remove(uuid);
            continue;
        }

        send(player, message);
    }

    Bukkit.getScheduler().runTaskLater(
            plugin,
            this::syncAll,
            1L
    );
}

/**
 * Construit le message de modification d'un claim.
 *
 * @param claim claim modifié
 * @param action action
 * @return message JSON
 */
private String buildClaimChangedMessage(
        Claim claim,
        String action
) {
    return "{"
            + "\"type\":\"claim_"
            + escapeJson(action)
            + "\","
            + "\"id\":\""
            + escapeJson(
                    String.valueOf(claim.getId())
            )
            + "\""
            + "}";
}

/**
 * Réception des messages du client.
 *
 * @param channel canal
 * @param player joueur
 * @param message données reçues
 */
@Override
public void onPluginMessageReceived(
        String channel,
        Player player,
        byte[] message
) {
    if (!CHANNEL.equals(channel)
            || player == null
            || message == null
            || message.length == 0) {
        return;
    }

    String content =
            new String(
                    message,
                    StandardCharsets.UTF_8
            );

    handleMessage(player, content);
}

/**
 * Traite un message reçu.
 *
 * @param player joueur
 * @param message message
 */
private void handleMessage(
        Player player,
        String message
) {
    if (message == null || message.isBlank()) {
        return;
    }

    if (isHelloMessage(message)) {
        handshake(player);
        return;
    }

    if (isSyncMessage(message)) {
        if (!isMapClient(player)) {
            clients.add(player.getUniqueId());
        }

        sync(player);
    }
}

/**
 * Vérifie un message HELLO.
 *
 * @param message message reçu
 * @return true si le message est un HELLO
 */
private boolean isHelloMessage(String message) {
    return message.startsWith("HELLO")
            || message.contains("\"type\":\"hello\"");
}

/**
 * Vérifie un message SYNC.
 *
 * @param message message reçu
 * @return true si le message est un SYNC
 */
private boolean isSyncMessage(String message) {
    return message.startsWith("SYNC")
            || message.contains("\"type\":\"sync\"");
}

/**
 * Envoie un message au client.
 *
 * @param player joueur
 * @param message message JSON
 */
private void send(
        Player player,
        String message
) {
    if (player == null
            || !player.isOnline()
            || message == null
            || message.isEmpty()) {
        return;
    }

    player.sendPluginMessage(
            plugin,
            CHANNEL,
            message.getBytes(
                    StandardCharsets.UTF_8
            )
    );
}

/**
 * Échappe une chaîne pour l'intégration JSON.
 *
 * @param value valeur
 * @return chaîne échappée
 */
private static String escapeJson(String value) {
    if (value == null) {
        return "";
    }

    return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\f", "\\f")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
}

}
