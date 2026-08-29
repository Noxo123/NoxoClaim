package fr.noxodev.noxoclaim.managers;

import fr.noxodev.noxoclaim.models.*;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * High-performance claim registry. Spatial lookups are indexed by world/chunk,
 * while the YAML file remains compatible with the existing storage format.
 */
public final class ClaimManager {
    private final File file;
    private final Map<UUID, Claim> claims = new LinkedHashMap<>();
    private final Map<ChunkKey, Claim> chunkIndex = new HashMap<>();
    private final Map<UUID, Set<UUID>> ownerIndex = new HashMap<>();

    public ClaimManager(File folder) {
        if (!folder.exists()) folder.mkdirs();
        file = new File(folder, "claims.yml");
        load();
        rebuildIndexes();
    }

    public Collection<Claim> all() { return Collections.unmodifiableCollection(claims.values()); }
    public Claim get(UUID id) { return claims.get(id); }

    public Claim at(Location location) {
        if (location == null || location.getWorld() == null) return null;
        return atChunk(location.getWorld().getName(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    public Claim atChunk(String world, int chunkX, int chunkZ) {
        Claim indexed = chunkIndex.get(new ChunkKey(world, chunkX, chunkZ));
        if (indexed != null && indexed.contains(new Location(resolveWorld(world), chunkX * 16 + 8, 0, chunkZ * 16 + 8))) return indexed;
        return null;
    }

    public boolean overlaps(Claim candidate) {
        if (candidate == null) return false;
        int minChunkX = candidate.getMinX() >> 4, maxChunkX = candidate.getMaxX() >> 4;
        int minChunkZ = candidate.getMinZ() >> 4, maxChunkZ = candidate.getMaxZ() >> 4;
        for (int x = minChunkX; x <= maxChunkX; x++) for (int z = minChunkZ; z <= maxChunkZ; z++) {
            Claim existing = chunkIndex.get(new ChunkKey(candidate.getWorld(), x, z));
            if (existing != null && !existing.getId().equals(candidate.getId()) && existing.overlaps(candidate)) return true;
        }
        return false;
    }

    public List<Claim> owned(UUID owner) {
        Set<UUID> ids = ownerIndex.getOrDefault(owner, Set.of());
        List<Claim> result = new ArrayList<>(ids.size());
        for (UUID id : ids) { Claim c = claims.get(id); if (c != null) result.add(c); }
        return List.copyOf(result);
    }

    public void add(Claim claim) {
        if (claim == null || overlaps(claim)) throw new IllegalArgumentException("Claim overlaps an existing claim");
        claims.put(claim.getId(), claim);
        index(claim);
        save();
    }

    public void remove(Claim claim) {
        if (claim == null) return;
        claims.remove(claim.getId());
        unindex(claim);
        save();
    }

    public void rebuildIndexes() {
        chunkIndex.clear(); ownerIndex.clear();
        claims.values().forEach(this::index);
    }

    private void index(Claim c) {
        ownerIndex.computeIfAbsent(c.getOwner(), k -> new HashSet<>()).add(c.getId());
        int minX = c.getMinX() >> 4, maxX = c.getMaxX() >> 4;
        int minZ = c.getMinZ() >> 4, maxZ = c.getMaxZ() >> 4;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) chunkIndex.put(new ChunkKey(c.getWorld(), x, z), c);
    }

    private void unindex(Claim c) {
        int minX = c.getMinX() >> 4, maxX = c.getMaxX() >> 4;
        int minZ = c.getMinZ() >> 4, maxZ = c.getMaxZ() >> 4;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) chunkIndex.remove(new ChunkKey(c.getWorld(), x, z), c);
        Set<UUID> ids = ownerIndex.get(c.getOwner());
        if (ids != null) { ids.remove(c.getId()); if (ids.isEmpty()) ownerIndex.remove(c.getOwner()); }
    }

    private record ChunkKey(String world, int x, int z) {}

    private World resolveWorld(String name) {
        return org.bukkit.Bukkit.getWorld(name);
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Claim c : claims.values()) {
            String p = "claims." + c.getId();
            y.set(p + ".owner", c.getOwner().toString()); y.set(p + ".name", c.getName());
            y.set(p + ".world", c.getWorld()); y.set(p + ".minX", c.getMinX()); y.set(p + ".minZ", c.getMinZ());
            y.set(p + ".maxX", c.getMaxX()); y.set(p + ".maxZ", c.getMaxZ());
            y.set(p + ".members", c.getMembers().stream().map(UUID::toString).toList());
            for (var entry : c.getFlags().entrySet()) y.set(p + ".flags." + entry.getKey(), entry.getValue());
            if (c.getHome() != null) {
                Location h = c.getHome();
                y.set(p + ".home.world", h.getWorld() == null ? c.getWorld() : h.getWorld().getName());
                y.set(p + ".home.x", h.getX()); y.set(p + ".home.y", h.getY()); y.set(p + ".home.z", h.getZ());
                y.set(p + ".home.yaw", h.getYaw()); y.set(p + ".home.pitch", h.getPitch());
            }
        }
        try { y.save(file); } catch (IOException e) { throw new IllegalStateException("Unable to save claims.yml", e); }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = y.getConfigurationSection("claims");
        if (section == null) return;
        for (String id : section.getKeys(false)) try {
            String p = "claims." + id;
            Claim c = new Claim(UUID.fromString(id), UUID.fromString(y.getString(p + ".owner")), y.getString(p + ".world"),
                    y.getInt(p + ".minX"), y.getInt(p + ".minZ"), y.getInt(p + ".maxX"), y.getInt(p + ".maxZ"),
                    y.getString(p + ".name", "claim-" + id.substring(0, 8)));
            for (String member : y.getStringList(p + ".members")) c.addMember(UUID.fromString(member));
            for (ClaimFlag flag : ClaimFlag.values()) if (y.contains(p + ".flags." + flag)) c.setFlag(flag, y.getBoolean(p + ".flags." + flag));
            if (y.contains(p + ".home.x")) {
                World w = resolveWorld(y.getString(p + ".home.world", c.getWorld()));
                if (w != null) c.setHome(new Location(w, y.getDouble(p + ".home.x"), y.getDouble(p + ".home.y"), y.getDouble(p + ".home.z"),
                        (float)y.getDouble(p + ".home.yaw"), (float)y.getDouble(p + ".home.pitch")));
            }
            claims.put(c.getId(), c);
        } catch (Exception ignored) { }
    }
}
