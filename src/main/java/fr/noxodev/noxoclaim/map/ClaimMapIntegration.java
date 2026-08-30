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

* Intégration optionnelle avec un client NoxoClaim possédant
* une interface de carte personnalisée.
*
* <p>Les clients Minecraft vanilla ne sont pas affectés.</p>
*
* <p>Communication via le canal plugin :</p>
*
* <pre>

* noxoclaim:map
* </pre>

*/
public final class ClaimMapIntegration
implements PluginMessageListener {

```
/* ========================================================= */
/* CONSTANTES                                                  */
/* ========================================================= */

public static final String CHANNEL = "noxoclaim:map";

private static final int PROTOCOL_VERSION = 1;

private static final int INITIAL_BUFFER_SIZE = 32_768;

private static final String MESSAGE_HELLO = "HELLO";
private static final String MESSAGE_SYNC = "SYNC";

/* ========================================================= */
/* CHAMPS                                                      */
/* ========================================================= */

private final NoxoClaim plugin;

/**
 * UUID des joueurs ayant confirmé la présence
 * du client NoxoClaim.
 */
private final Set<UUID> clients = new HashSet<>();

/* ========================================================= */
/* CONSTRUCTEUR                                                 */
/* ========================================================= */

public ClaimMapIntegration(NoxoClaim plugin) {
    this.plugin = plugin;

    registerChannels();
}

/**
 * Enregistre les canaux de communication Bukkit.
 */
private void registerChannels() {
    plugin.getServer()
            .getMessenger()
            .registerOutgoingPluginChannel(
                    plugin,
                    CHANNEL
            );

    plugin.getServer()
            .getMessenger()
            .registerIncomingPluginChannel(
                    plugin,
                    CHANNEL,
                    this
            );
}

/* ========================================================= */
/* CLIENTS                                                      */
/* ========================================================= */

/**
 * Vérifie si un joueur utilise le client NoxoClaim.
 *
 * @param player joueur à vérifier
 * @return true si le client a effectué le handshake
 */
public boolean isMapClient(Player player) {
    return player != null
            && clients.contains(player.getUniqueId());
}

/**
 * Retire un joueur de la liste des clients connus.
 */
public void forget(Player player) {
    if (player == null) {
        return;
    }

    clients.remove(player.getUniqueId());
}

/* ========================================================= */
/* HANDSHAKE                                                    */
/* ========================================================= */

/**
 * Effectue le handshake avec le client NoxoClaim.
 *
 * <p>Le serveur annonce sa version de protocole
 * puis envoie immédiatement la carte complète.</p>
 */
public void handshake(Player player) {
    if (player == null || !player.isOnline()) {
        return;
    }

    clients.add(player.getUniqueId());

    send(
            player,
            buildHelloMessage()
    );

    sync(player);
}

/**
 * Construit le message d'initialisation.
 */
private String buildHelloMessage() {
    String version =
            plugin.getDescription().getVersion();

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

/* ========================================================= */
/* SYNCHRONISATION                                              */
/* ========================================================= */

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
 * Envoie un snapshot complet des claims à un joueur.
 */
public void sync(Player player) {
    if (!isMapClient(player)) {
        return;
    }

    String message = buildSnapshot(player);

    send(
            player,
            message
    );
}

/**
 * Construit le snapshot JSON complet.
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

        appendClaim(
                json,
                claim,
                player
        );
    }

    json.append("]}");

    return json.toString();
}

/**
 * Ajoute un claim au JSON du snapshot.
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
            .append(escapeJson(String.valueOf(claim.getId())))
            .append("\",")

            .append("\"world\":\"")
            .append(escapeJson(claim.getWorld()))
            .append("\",")

            .append("\"owner\":\"")
            .append(ownerUuid)
            .append("\",")

            .append("\"ownerName\":\"")
            .append(escapeJson(ownerName))
            .append("\",")

            .append("\"name\":\"")
            .append(escapeJson(claim.getName()))
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

/* ========================================================= */
/* CLAIM EVENTS                                                 */
/* ========================================================= */

/**
 * Informe les clients qu'un claim a été modifié.
 *
 * @param claim claim concerné
 * @param action action effectuée : create, update, delete...
 */
public void claimChanged(
        Claim claim,
        String action
) {
    if (claim == null || action == null) {
        return;
    }

    String message = buildClaimChangedMessage(
            claim,
            action
    );

    for (UUID uuid : List.copyOf(clients)) {

        Player player = Bukkit.getPlayer(uuid);

        if (player == null || !player.isOnline()) {
            clients.remove(uuid);
            continue;
        }

        send(
                player,
                message
        );
    }

    /*
     * On envoie ensuite un snapshot complet.
     *
     * Le délai d'un tick permet de laisser le système
     * de claims terminer sa propre modification avant
     * de récupérer les nouvelles données.
     */
    Bukkit.getScheduler().runTaskLater(
            plugin,
            this::syncAll,
            1L
    );
}

/**
 * Construit l'événement de modification d'un claim.
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
            + escapeJson(String.valueOf(claim.getId()))
            + "\""
            + "}";
}

/* ========================================================= */
/* RÉCEPTION                                                    */
/* ========================================================= */

@Override
public void onPluginMessageReceived(
        String channel,
        Player player,
        byte[] message
) {
    if (!CHANNEL.equals(channel)
            || player == null
            || message == null) {
        return;
    }

    String content =
            new String(
                    message,
                    StandardCharsets.UTF_8
            );

    handleMessage(
            player,
            content
    );
}

/**
 * Traite les messages reçus du client.
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
        sync(player);
    }
}

/**
 * Vérifie si le message correspond à un handshake.
 */
private boolean isHelloMessage(String message) {
    return message.startsWith(MESSAGE_HELLO)
            || message.contains("\"type\":\"hello\"");
}

/**
 * Vérifie si le message demande une synchronisation.
 */
private boolean isSyncMessage(String message) {
    return message.startsWith(MESSAGE_SYNC);
}

/* ========================================================= */
/* ENVOI                                                        */
/* ========================================================= */

/**
 * Envoie un message au client NoxoClaim.
 */
private void send(
        Player player,
        String message
) {
    if (player == null
            || !player.isOnline()
            || message == null) {
        return;
    }

    player.sendPluginMessage(
            plugin,
            CHANNEL,
            message.getBytes(StandardCharsets.UTF_8)
    );
}

/* ========================================================= */
/* JSON                                                         */
/* ========================================================= */

/**
 * Échappe une chaîne afin de pouvoir l'insérer
 * correctement dans un document JSON.
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
```

}
