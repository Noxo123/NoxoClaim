package fr.noxodev.noxoclaim.managers;

import fr.noxodev.noxoclaim.models.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

/** High-performance claim registry with O(1) chunk lookups and atomic persistence. */
public final class ClaimManager {
    private final File file;
    private final File backupFile;
    private final Map<UUID, Claim> claims = new LinkedHashMap<>();
    private final Map<ChunkKey, Claim> chunkIndex = new HashMap<>();
    private final Map<UUID, Set<UUID>> ownerIndex = new HashMap<>();
    private long revision;

    public ClaimManager(File folder) {
        if (!folder.exists() && !folder.mkdirs()) throw new IllegalStateException("Unable to create claims folder");
        file = new File(folder, "claims.yml");
        backupFile = new File(folder, "claims.yml.bak");
        load();
        rebuildIndexes();
    }

    public Collection<Claim> all() { return Collections.unmodifiableCollection(new ArrayList<>(claims.values())); }
    public Claim get(UUID id) { return id == null ? null : claims.get(id); }
    public long revision() { return revision; }

    public Claim at(Location location) {
        if (location == null || location.getWorld() == null) return null;
        return atChunk(location.getWorld().getName(), Math.floorDiv(location.getBlockX(), 16), Math.floorDiv(location.getBlockZ(), 16));
    }

    public Claim atChunk(String world, int chunkX, int chunkZ) {
        return world == null ? null : chunkIndex.get(new ChunkKey(world, chunkX, chunkZ));
    }

    public boolean overlaps(Claim candidate) {
        if (candidate == null) return false;
        int minChunkX = Math.floorDiv(candidate.getMinX(), 16), maxChunkX = Math.floorDiv(candidate.getMaxX(), 16);
        int minChunkZ = Math.floorDiv(candidate.getMinZ(), 16), maxChunkZ = Math.floorDiv(candidate.getMaxZ(), 16);
        for (int x = minChunkX; x <= maxChunkX; x++) for (int z = minChunkZ; z <= maxChunkZ; z++) {
            Claim existing = chunkIndex.get(new ChunkKey(candidate.getWorld(), x, z));
            if (existing != null && !existing.getId().equals(candidate.getId()) && existing.overlaps(candidate)) return true;
        }
        return false;
    }

    public List<Claim> owned(UUID owner) {
        if (owner == null) return List.of();
        Set<UUID> ids = ownerIndex.getOrDefault(owner, Set.of());
        List<Claim> result = new ArrayList<>(ids.size());
        for (UUID id : ids) { Claim c = claims.get(id); if (c != null) result.add(c); }
        return List.copyOf(result);
    }

    public void add(Claim claim) {
        Objects.requireNonNull(claim, "claim");
        validateClaim(claim);
        if (claims.containsKey(claim.getId())) throw new IllegalArgumentException("Claim ID already exists");
        if (overlaps(claim)) throw new IllegalArgumentException("Claim overlaps an existing claim");
        claims.put(claim.getId(), claim);
        index(claim, chunkIndex, ownerIndex);
        revision++;
        save();
    }

    public void remove(Claim claim) {
        if (claim == null || claims.remove(claim.getId()) == null) return;
        unindex(claim, chunkIndex, ownerIndex);
        revision++;
        save();
    }

    /** Rebuilds indexes in temporary maps and publishes them only after validation. */
    public void rebuildIndexes() {
        Map<ChunkKey, Claim> newChunkIndex = new HashMap<>();
        Map<UUID, Set<UUID>> newOwnerIndex = new HashMap<>();
        List<UUID> invalid = new ArrayList<>();

        for (Claim claim : claims.values()) {
            try {
                validateClaim(claim);
                index(claim, newChunkIndex, newOwnerIndex);
            } catch (RuntimeException ex) {
                invalid.add(claim.getId());
                Bukkit.getLogger().warning("[NoxoClaim] Claim invalide ignoré lors de l'indexation: " + claim.getId() + " (" + ex.getMessage() + ")");
            }
        }

        chunkIndex.clear();
        chunkIndex.putAll(newChunkIndex);
        ownerIndex.clear();
        ownerIndex.putAll(newOwnerIndex);
        if (!invalid.isEmpty()) Bukkit.getLogger().warning("[NoxoClaim] " + invalid.size() + " claim(s) n'ont pas été indexé(s).");
    }

    private void validateClaim(Claim claim) {
        if (claim == null) throw new IllegalArgumentException("Claim is null");
        if (claim.getWorld().isBlank()) throw new IllegalArgumentException("Claim world is blank");
        if (claim.getMinX() > claim.getMaxX() || claim.getMinZ() > claim.getMaxZ()) throw new IllegalArgumentException("Invalid claim bounds");
        if (claim.size() <= 0 || claim.chunkCount() <= 0) throw new IllegalArgumentException("Invalid claim size");
    }

    /** Checks every target chunk before mutating either index, so a failed insertion leaves no partial state. */
    private void index(Claim c, Map<ChunkKey, Claim> targetChunks, Map<UUID, Set<UUID>> targetOwners) {
        int minX = Math.floorDiv(c.getMinX(), 16), maxX = Math.floorDiv(c.getMaxX(), 16);
        int minZ = Math.floorDiv(c.getMinZ(), 16), maxZ = Math.floorDiv(c.getMaxZ(), 16);
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            ChunkKey key = new ChunkKey(c.getWorld(), x, z);
            Claim previous = targetChunks.get(key);
            if (previous != null && !previous.getId().equals(c.getId())) {
                throw new IllegalStateException("Overlapping claims detected while indexing: " + previous.getId() + " / " + c.getId());
            }
        }
        targetOwners.computeIfAbsent(c.getOwner(), k -> new HashSet<>()).add(c.getId());
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) targetChunks.put(new ChunkKey(c.getWorld(), x, z), c);
    }

    private void unindex(Claim c, Map<ChunkKey, Claim> targetChunks, Map<UUID, Set<UUID>> targetOwners) {
        int minX = Math.floorDiv(c.getMinX(), 16), maxX = Math.floorDiv(c.getMaxX(), 16);
        int minZ = Math.floorDiv(c.getMinZ(), 16), maxZ = Math.floorDiv(c.getMaxZ(), 16);
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) targetChunks.remove(new ChunkKey(c.getWorld(), x, z), c);
        Set<UUID> ids = targetOwners.get(c.getOwner());
        if (ids != null) { ids.remove(c.getId()); if (ids.isEmpty()) targetOwners.remove(c.getOwner()); }
    }

    private record ChunkKey(String world, int x, int z) {}

    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Claim c : claims.values()) {
            String p = "claims." + c.getId();
            y.set(p + ".owner", c.getOwner().toString());
            y.set(p + ".name", c.getName());
            y.set(p + ".world", c.getWorld());
            y.set(p + ".minX", c.getMinX()); y.set(p + ".minZ", c.getMinZ());
            y.set(p + ".maxX", c.getMaxX()); y.set(p + ".maxZ", c.getMaxZ());
            y.set(p + ".members", c.getMembers().stream().map(UUID::toString).toList());
            for (var entry : c.getFlags().entrySet()) y.set(p + ".flags." + entry.getKey().name().toLowerCase(Locale.ROOT), entry.getValue());
            Location h = c.getHome();
            if (h != null) {
                y.set(p + ".home.world", h.getWorld() == null ? c.getWorld() : h.getWorld().getName());
                y.set(p + ".home.x", h.getX()); y.set(p + ".home.y", h.getY()); y.set(p + ".home.z", h.getZ());
                y.set(p + ".home.yaw", h.getYaw()); y.set(p + ".home.pitch", h.getPitch());
            }
        }
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            y.save(temp);
            if (file.isFile()) Files.copy(file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailure) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            if (temp.isFile()) temp.delete();
            throw new IllegalStateException("Unable to save claims.yml (backup: " + backupFile.getAbsolutePath() + ")", failure);
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = y.getConfigurationSection("claims");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            try {
                UUID claimId = UUID.fromString(id);
                String ownerValue = y.getString("claims." + id + ".owner");
                String world = y.getString("claims." + id + ".world");
                if (ownerValue == null || world == null || world.isBlank()) throw new IllegalArgumentException("missing owner/world");
                String p = "claims." + id;
                Claim c = new Claim(claimId, UUID.fromString(ownerValue), world,
                        y.getInt(p + ".minX"), y.getInt(p + ".minZ"), y.getInt(p + ".maxX"), y.getInt(p + ".maxZ"),
                        y.getString(p + ".name", "claim-" + id.substring(0, 8)));
                for (String member : y.getStringList(p + ".members")) {
                    try { c.addMember(UUID.fromString(member)); } catch (IllegalArgumentException ignored) {
                        Bukkit.getLogger().warning("[NoxoClaim] UUID membre invalide dans le claim " + id + ": " + member);
                    }
                }
                for (ClaimFlag flag : ClaimFlag.values()) {
                    String flagPath = p + ".flags." + flag.name().toLowerCase(Locale.ROOT);
                    if (y.contains(flagPath)) c.setFlag(flag, y.getBoolean(flagPath));
                    else if (y.contains(p + ".flags." + flag)) c.setFlag(flag, y.getBoolean(p + ".flags." + flag));
                }
                if (y.contains(p + ".home.x")) {
                    World w = Bukkit.getWorld(y.getString(p + ".home.world", c.getWorld()));
                    if (w != null) c.setHome(new Location(w, y.getDouble(p + ".home.x"), y.getDouble(p + ".home.y"), y.getDouble(p + ".home.z"),
                            (float) y.getDouble(p + ".home.yaw"), (float) y.getDouble(p + ".home.pitch")));
                }
                validateClaim(c);
                claims.put(c.getId(), c);
            } catch (RuntimeException ex) {
                Bukkit.getLogger().warning("[NoxoClaim] Impossible de charger le claim " + id + ": " + ex.getMessage());
            }
        }
    }
}
