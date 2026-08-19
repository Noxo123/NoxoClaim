package fr.noxodev.noxoclaim.models;

import org.bukkit.Location;
import java.util.*;

public final class Claim {
    private final UUID id, owner;
    private final String world;
    private final int minX, minZ, maxX, maxZ;
    private final Set<UUID> members = new HashSet<>();
    private final EnumMap<ClaimFlag, Boolean> flags = new EnumMap<>(ClaimFlag.class);
    private Location home;
    private String name;

    public Claim(UUID id, UUID owner, String world, int x1, int z1, int x2, int z2) {
        this(id, owner, world, x1, z1, x2, z2, "claim-" + id.toString().substring(0, 8));
    }

    public Claim(UUID id, UUID owner, String world, int x1, int z1, int x2, int z2, String name) {
        this.id = id;
        this.owner = owner;
        this.world = world;
        minX = Math.min(x1, x2);
        maxX = Math.max(x1, x2);
        minZ = Math.min(z1, z2);
        maxZ = Math.max(z1, z2);
        this.name = name == null || name.isBlank() ? "claim-" + id.toString().substring(0, 8) : name;
        flags.put(ClaimFlag.PVP, false);
        flags.put(ClaimFlag.EXPLOSIONS, false);
        flags.put(ClaimFlag.FIRE, false);
        flags.put(ClaimFlag.MOB_GRIEFING, false);
        flags.put(ClaimFlag.ENTRY, true);
    }

    public boolean contains(Location l) {
        return l.getWorld() != null && world.equals(l.getWorld().getName())
                && l.getBlockX() >= minX && l.getBlockX() <= maxX
                && l.getBlockZ() >= minZ && l.getBlockZ() <= maxZ;
    }

    public boolean overlaps(Claim c) {
        return world.equals(c.world) && minX <= c.maxX && maxX >= c.minX && minZ <= c.maxZ && maxZ >= c.minZ;
    }

    public long size() { return (long) (maxX - minX + 1) * (maxZ - minZ + 1); }
    public long chunkCount() { return ((long) Math.floorDiv(maxX, 16) - Math.floorDiv(minX, 16) + 1) * ((long) Math.floorDiv(maxZ, 16) - Math.floorDiv(minZ, 16) + 1); }
    public boolean isMember(UUID u) { return owner.equals(u) || members.contains(u); }

    public UUID getId() { return id; }
    public UUID getOwner() { return owner; }
    public String getWorld() { return world; }
    public int getMinX() { return minX; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxZ() { return maxZ; }
    public Set<UUID> getMembers() { return members; }
    public EnumMap<ClaimFlag, Boolean> getFlags() { return flags; }
    public boolean getFlag(ClaimFlag f) { return flags.getOrDefault(f, false); }
    public void setFlag(ClaimFlag f, boolean v) { flags.put(f, v); }
    public void addMember(UUID u) { members.add(u); }
    public void removeMember(UUID u) { members.remove(u); }
    public Location getHome() { return home; }
    public void setHome(Location l) { home = l == null ? null : l.clone(); }
    public String getName() { return name; }
    public void setName(String name) { if (name != null && !name.isBlank()) this.name = name; }
}
