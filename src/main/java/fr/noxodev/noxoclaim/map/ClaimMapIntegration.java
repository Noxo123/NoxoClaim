package fr.noxodev.noxoclaim.map;

import fr.noxodev.noxoclaim.NoxoClaim;
import fr.noxodev.noxoclaim.models.Claim;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Optional client-map bridge. Vanilla clients are completely unaffected. */
public final class ClaimMapIntegration implements PluginMessageListener {
    public static final String CHANNEL = "noxoclaim:map";
    private final NoxoClaim plugin;
    private final Set<UUID> clients = new HashSet<>();

    public ClaimMapIntegration(NoxoClaim plugin) {
        this.plugin = plugin;
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
    }
    public boolean isMapClient(Player p) { return clients.contains(p.getUniqueId()); }
    public void forget(Player p) { clients.remove(p.getUniqueId()); }
    public void handshake(Player p) {
        clients.add(p.getUniqueId());
        send(p, "{\"type\":\"hello\",\"protocol\":1,\"plugin\":\"NoxoClaim\",\"version\":\"" + esc(plugin.getDescription().getVersion()) + "\"}");
        sync(p);
    }
    public void syncAll() {
        for (UUID id : List.copyOf(clients)) { Player p=Bukkit.getPlayer(id); if(p==null) clients.remove(id); else sync(p); }
    }
    public void sync(Player p) {
        if(!isMapClient(p)) return;
        StringBuilder j=new StringBuilder(32768).append("{\"type\":\"snapshot\",\"protocol\":1,\"claims\":[");
        boolean first=true;
        for(Claim c:plugin.claims().all()) {
            if(!first) j.append(','); first=false;
            String owner=Optional.ofNullable(Bukkit.getOfflinePlayer(c.getOwner()).getName()).orElse("Unknown");
            j.append("{\"id\":\"").append(c.getId()).append("\",\"world\":\"").append(esc(c.getWorld()))
             .append("\",\"owner\":\"").append(c.getOwner()).append("\",\"ownerName\":\"").append(esc(owner))
             .append("\",\"name\":\"").append(esc(c.getName())).append("\",\"minChunkX\":").append(Math.floorDiv(c.getMinX(),16))
             .append(",\"minChunkZ\":").append(Math.floorDiv(c.getMinZ(),16)).append(",\"maxChunkX\":").append(Math.floorDiv(c.getMaxX(),16))
             .append(",\"maxChunkZ\":").append(Math.floorDiv(c.getMaxZ(),16)).append(",\"mine\":").append(c.getOwner().equals(p.getUniqueId())).append('}');
        }
        send(p,j.append("]}").toString());
    }
    public void claimChanged(Claim c,String action) {
        String msg="{\"type\":\"claim_"+action+"\",\"id\":\""+c.getId()+"\"}";
        for(UUID id:List.copyOf(clients)){Player p=Bukkit.getPlayer(id);if(p==null)clients.remove(id);else send(p,msg);}
        Bukkit.getScheduler().runTaskLater(plugin,this::syncAll,1L);
    }
    @Override public void onPluginMessageReceived(String channel,Player p,byte[] message){
        if(!CHANNEL.equals(channel))return;
        String s=new String(message,StandardCharsets.UTF_8);
        if(s.startsWith("HELLO")||s.contains("\"type\":\"hello\""))handshake(p); else if(s.startsWith("SYNC"))sync(p);
    }
    private void send(Player p,String s){p.sendPluginMessage(plugin,CHANNEL,s.getBytes(StandardCharsets.UTF_8));}
    private static String esc(String s){return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n");}
}
