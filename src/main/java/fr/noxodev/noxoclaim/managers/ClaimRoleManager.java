package fr.noxodev.noxoclaim.managers;

import fr.noxodev.noxoclaim.models.Claim;
import fr.noxodev.noxoclaim.models.ClaimRole;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Stores claim roles separately so existing claims.yml files remain backward compatible. */
public final class ClaimRoleManager {
    private final File file;
    private final Map<UUID, Map<UUID, ClaimRole>> roles = new HashMap<>();

    public ClaimRoleManager(JavaPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "claim-roles.yml");
        load();
    }

    public void apply(Claim claim) {
        Map<UUID, ClaimRole> claimRoles = roles.get(claim.getId());
        if (claimRoles == null) return;
        for (Map.Entry<UUID, ClaimRole> entry : claimRoles.entrySet()) claim.setMemberRole(entry.getKey(), entry.getValue());
    }

    public ClaimRole role(Claim claim, UUID player) { return claim == null ? null : claim.getRole(player); }

    public void setRole(Claim claim, UUID player, ClaimRole role) {
        if (claim == null || player == null || role == null || role == ClaimRole.OWNER) return;
        claim.setMemberRole(player, role);
        roles.computeIfAbsent(claim.getId(), ignored -> new HashMap<>()).put(player, role);
        save();
    }

    public void remove(Claim claim, UUID player) {
        if (claim == null || player == null) return;
        claim.removeMember(player);
        Map<UUID, ClaimRole> claimRoles = roles.get(claim.getId());
        if (claimRoles != null) {
            claimRoles.remove(player);
            if (claimRoles.isEmpty()) roles.remove(claim.getId());
        }
        save();
    }

    public void purge(Claim claim) {
        if (claim != null && roles.remove(claim.getId()) != null) save();
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Map<UUID, ClaimRole>> claim : roles.entrySet()) {
            for (Map.Entry<UUID, ClaimRole> member : claim.getValue().entrySet()) yaml.set("claims." + claim.getKey() + "." + member.getKey(), member.getValue().name());
        }
        try { yaml.save(file); } catch (IOException e) { throw new IllegalStateException("Impossible de sauvegarder claim-roles.yml", e); }
    }

    private void load() {
        if (!file.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection claims = yaml.getConfigurationSection("claims");
        if (claims == null) return;
        for (String claimId : claims.getKeys(false)) {
            try {
                UUID cid = UUID.fromString(claimId);
                ConfigurationSection members = claims.getConfigurationSection(claimId);
                if (members == null) continue;
                for (String memberId : members.getKeys(false)) {
                    try { roles.computeIfAbsent(cid, ignored -> new HashMap<>()).put(UUID.fromString(memberId), ClaimRole.valueOf(members.getString(memberId, "MEMBER"))); }
                    catch (IllegalArgumentException ignored) { }
                }
            } catch (IllegalArgumentException ignored) { }
        }
    }
}
