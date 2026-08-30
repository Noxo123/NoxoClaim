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


Les clients Minecraft vanilla ne sont pas affectés.



Communication via le canal noxoclaim.
*/
public final class ClaimMapIntegration implements PluginMessageListener {

public static final String CHANNEL = "noxoclaim";

private static final int PROTOCOL_VERSION = 1;
private static final int INITIAL_BUFFER_SIZE = 32768;

private final NoxoClaim plugin;
private final Set<UUID> clients = new HashSet<>();

public ClaimMapIntegration(NoxoClaim plugin) {
this.plugin = plugin;
registerChannels();
}

private void registerChannels() {
plugin.getServer()
.getMessenger()
.registerOutgoingPluginChannel(plugin, CHANNEL);

 plugin.getServer()
         .getMessenger()
         .registerIncomingPluginChannel(plugin, CHANNEL, this);

}

public boolean isMapClient(Player player) {
return player != null
&& clients.contains(player.getUniqueId());
}

public void forget(Player player) {
if (player == null) {
return;
}

 clients.remove(player.getUniqueId());

}

public void handshake(Player player) {
if (player == null || !player.isOnline()) {
return;
}

 clients.add(player.getUniqueId());

 send(player, buildHelloMessage());
 sync(player);

}

private String buildHelloMessage() {
String version = plugin.getDescription().getVersion();

 return "{"
         + "\"type\":\"hello\","
         + "\"protocol\":" + PROTOCOL_VERSION + ","
         + "\"plugin\":\"NoxoClaim\","
         + "\"version\":\"" + escapeJson(version) + "\""
         + "}";

}

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

public void sync(Player player) {
if (!isMapClient(player)) {
return;
}

 send(player, buildSnapshot(player));

}

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

 int minChunkX = Math.floorDiv(claim.getMinX(), 16);
 int minChunkZ = Math.floorDiv(claim.getMinZ(), 16);
 int maxChunkX = Math.floorDiv(claim.getMaxX(), 16);
 int maxChunkZ = Math.floorDiv(claim.getMaxZ(), 16);

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

public void claimChanged(
Claim claim,
String action
) {
if (claim == null || action == null) {
return;
}

 String message =
         buildClaimChangedMessage(claim, action);

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

private String buildClaimChangedMessage(
Claim claim,
String action
) {
return "{"
+ ""type":"claim_"
+ escapeJson(action)
+ "","
+ ""id":""
+ escapeJson(String.valueOf(claim.getId()))
+ """
+ "}";
}

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

 String content = new String(
         message,
         StandardCharsets.UTF_8
 );

 handleMessage(player, content);

}

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

private boolean isHelloMessage(String message) {
return message.startsWith("HELLO")
|| message.contains(""type":"hello"");
}

private boolean isSyncMessage(String message) {
return message.startsWith("SYNC");
}

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
