package fr.noxodev.noxoclaim.managers;

import fr.noxodev.noxoclaim.models.*;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.*;
import java.util.*;

public final class ClaimManager {
    private final File file;
    private final Map<UUID, Claim> claims = new LinkedHashMap<>();

    public ClaimManager(File folder) { file = new File(folder, "claims.yml"); load(); }
    public Collection<Claim> all() { return claims.values(); }
    public Claim get(UUID id) { return claims.get(id); }
    public Claim at(Location l) { return claims.values().stream().filter(c -> c.contains(l)).findFirst().orElse(null); }
    public Claim atChunk(String world, int chunkX, int chunkZ) {
        int x = chunkX * 16 + 8, z = chunkZ * 16 + 8;
        return claims.values().stream().filter(c -> c.getWorld().equals(world) && x >= c.getMinX() && x <= c.getMaxX() && z >= c.getMinZ() && z <= c.getMaxZ()).findFirst().orElse(null);
    }
    public boolean overlaps(Claim n) { return claims.values().stream().anyMatch(n::overlaps); }
    public List<Claim> owned(UUID u) { return claims.values().stream().filter(c -> c.getOwner().equals(u)).toList(); }
    public void add(Claim c) { claims.put(c.getId(), c); save(); }
    public void remove(Claim c) { claims.remove(c.getId()); save(); }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Claim c : claims.values()) {
            String p = "claims." + c.getId();
            y.set(p + ".owner", c.getOwner().toString());
            y.set(p + ".name", c.getName());
            y.set(p + ".world", c.getWorld());
            y.set(p + ".minX", c.getMinX()); y.set(p + ".minZ", c.getMinZ());
            y.set(p + ".maxX", c.getMaxX()); y.set(p + ".maxZ", c.getMaxZ());
            y.set(p + ".members", c.getMembers().stream().map(UUID::toString).toList());
            for (var e : c.getFlags().entrySet()) y.set(p + ".flags." + e.getKey(), e.getValue());
            if (c.getHome() != null) {
                Location h = c.getHome();
                y.set(p + ".home.world", h.getWorld() == null ? c.getWorld() : h.getWorld().getName());
                y.set(p + ".home.x", h.getX()); y.set(p + ".home.y", h.getY()); y.set(p + ".home.z", h.getZ());
                y.set(p + ".home.yaw", h.getYaw()); y.set(p + ".home.pitch", h.getPitch());
            }
        }
        try { y.save(file); } catch (IOException e) { e.printStackTrace(); }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = y.getConfigurationSection("claims");
        if (s == null) return;
        for (String id : s.getKeys(false)) try {
            String p = "claims." + id;
            Claim c = new Claim(UUID.fromString(id), UUID.fromString(y.getString(p + ".owner")), y.getString(p + ".world"),
                    y.getInt(p + ".minX"), y.getInt(p + ".minZ"), y.getInt(p + ".maxX"), y.getInt(p + ".maxZ"),
                    y.getString(p + ".name", "claim-" + id.substring(0, 8)));
            for (String m : y.getStringList(p + ".members")) c.addMember(UUID.fromString(m));
            for (ClaimFlag f : ClaimFlag.values()) if (y.contains(p + ".flags." + f)) c.setFlag(f, y.getBoolean(p + ".flags." + f));
            if (y.contains(p + ".home.x")) {
                org.bukkit.World w = org.bukkit.Bukkit.getWorld(y.getString(p + ".home.world", c.getWorld()));
                if (w != null) c.setHome(new Location(w, y.getDouble(p + ".home.x"), y.getDouble(p + ".home.y"), y.getDouble(p + ".home.z"), (float)y.getDouble(p + ".home.yaw"), (float)y.getDouble(p + ".home.pitch")));
            }
            claims.put(c.getId(), c);
        } catch (Exception ignored) { }
    }
}
