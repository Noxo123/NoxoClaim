package fr.noxodev.noxoclaim.managers;

import fr.noxodev.noxoclaim.models.Claim;
import fr.noxodev.noxoclaim.models.ClaimFlag;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ClaimManager {
    private record ChunkKey(String world, int x, int z) {}

    private final File file;
    private final Map<UUID, Claim> claims = new LinkedHashMap<>();
    private final Map<ChunkKey, Claim> chunkIndex = new LinkedHashMap<>();

    public ClaimManager(File folder) {
        file = new File(folder, "claims.yml");
        load();
    }

    public Collection<Claim> all() {
        return claims.values();
    }

    public Claim get(UUID id) {
        return claims.get(id);
    }

    public Claim at(Location location) {
        if (location == null || location.getWorld() == null) return null;
        return atChunk(location.getWorld().getName(), location.getChunk().getX(), location.getChunk().getZ());
    }

    public Claim atChunk(String world, int chunkX, int chunkZ) {
        return chunkIndex.get(new ChunkKey(world, chunkX, chunkZ));
    }

    public boolean overlaps(Claim candidate) {
        int minChunkX = Math.floorDiv(candidate.getMinX(), 16);
        int maxChunkX = Math.floorDiv(candidate.getMaxX(), 16);
        int minChunkZ = Math.floorDiv(candidate.getMinZ(), 16);
        int maxChunkZ = Math.floorDiv(candidate.getMaxZ(), 16);

        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                Claim existing = atChunk(candidate.getWorld(), x, z);
                if (existing != null && existing.getId() != candidate.getId() && existing.overlaps(candidate)) return true;
            }
        }
        return false;
    }

    public List<Claim> owned(UUID owner) {
        return claims.values().stream().filter(c -> c.getOwner().equals(owner)).toList();
    }

    public void add(Claim claim) {
        Claim previous = claims.put(claim.getId(), claim);
        if (previous != null) unindex(previous);
        index(claim);
        save();
    }

    public void remove(Claim claim) {
        if (claims.remove(claim.getId()) != null) {
            unindex(claim);
            save();
        }
    }

    private void index(Claim claim) {
        int minChunkX = Math.floorDiv(claim.getMinX(), 16);
        int maxChunkX = Math.floorDiv(claim.getMaxX(), 16);
        int minChunkZ = Math.floorDiv(claim.getMinZ(), 16);
        int maxChunkZ = Math.floorDiv(claim.getMaxZ(), 16);

        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                chunkIndex.put(new ChunkKey(claim.getWorld(), x, z), claim);
            }
        }
    }

    private void unindex(Claim claim) {
        int minChunkX = Math.floorDiv(claim.getMinX(), 16);
        int maxChunkX = Math.floorDiv(claim.getMaxX(), 16);
        int minChunkZ = Math.floorDiv(claim.getMinZ(), 16);
        int maxChunkZ = Math.floorDiv(claim.getMaxZ(), 16);

        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                chunkIndex.remove(new ChunkKey(claim.getWorld(), x, z), claim);
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Claim claim : claims.values()) {
            String path = "claims." + claim.getId();
            yaml.set(path + ".owner", claim.getOwner().toString());
            yaml.set(path + ".name", claim.getName());
            yaml.set(path + ".world", claim.getWorld());
            yaml.set(path + ".minX", claim.getMinX());
            yaml.set(path + ".minZ", claim.getMinZ());
            yaml.set(path + ".maxX", claim.getMaxX());
            yaml.set(path + ".maxZ", claim.getMaxZ());
            yaml.set(path + ".members", claim.getMembers().stream().map(UUID::toString).toList());
            for (Map.Entry<ClaimFlag, Boolean> entry : claim.getFlags().entrySet()) {
                yaml.set(path + ".flags." + entry.getKey(), entry.getValue());
            }
            if (claim.getHome() != null) {
                Location home = claim.getHome();
                yaml.set(path + ".home.world", home.getWorld() == null ? claim.getWorld() : home.getWorld().getName());
                yaml.set(path + ".home.x", home.getX());
                yaml.set(path + ".home.y", home.getY());
                yaml.set(path + ".home.z", home.getZ());
                yaml.set(path + ".home.yaw", home.getYaw());
                yaml.set(path + ".home.pitch", home.getPitch());
            }
        }

        try {
            yaml.save(file);
        } catch (IOException exception) {
            Bukkit.getLogger().severe("Impossible de sauvegarder claims.yml: " + exception.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("claims");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            try {
                String path = "claims." + id;
                String ownerString = yaml.getString(path + ".owner");
                String world = yaml.getString(path + ".world");
                if (ownerString == null || world == null) throw new IllegalArgumentException("owner/world manquant");

                Claim claim = new Claim(
                        UUID.fromString(id),
                        UUID.fromString(ownerString),
                        world,
                        yaml.getInt(path + ".minX"),
                        yaml.getInt(path + ".minZ"),
                        yaml.getInt(path + ".maxX"),
                        yaml.getInt(path + ".maxZ"),
                        yaml.getString(path + ".name", "claim-" + id.substring(0, 8))
                );

                for (String member : yaml.getStringList(path + ".members")) {
                    claim.addMember(UUID.fromString(member));
                }
                for (ClaimFlag flag : ClaimFlag.values()) {
                    if (yaml.contains(path + ".flags." + flag)) {
                        claim.setFlag(flag, yaml.getBoolean(path + ".flags." + flag));
                    }
                }

                if (yaml.contains(path + ".home.x")) {
                    org.bukkit.World homeWorld = Bukkit.getWorld(yaml.getString(path + ".home.world", world));
                    if (homeWorld != null) {
                        claim.setHome(new Location(
                                homeWorld,
                                yaml.getDouble(path + ".home.x"),
                                yaml.getDouble(path + ".home.y"),
                                yaml.getDouble(path + ".home.z"),
                                (float) yaml.getDouble(path + ".home.yaw"),
                                (float) yaml.getDouble(path + ".home.pitch")
                        ));
                    }
                }

                claims.put(claim.getId(), claim);
                index(claim);
            } catch (Exception exception) {
                Bukkit.getLogger().warning("Claim ignoré dans claims.yml (" + id + "): " + exception.getMessage());
            }
        }
    }
}
