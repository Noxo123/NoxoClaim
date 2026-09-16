package fr.noxodev.noxoclaim;

import fr.noxodev.noxoclaim.models.Claim;
import fr.noxodev.noxoclaim.models.ClaimFlag;
import org.bukkit.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClaimTest {
    private UUID owner;
    private UUID id;
    private Claim c;

    @BeforeEach
    void init() {
        owner = UUID.randomUUID();
        id = UUID.randomUUID();
        c = new Claim(id, owner, "world", -10, -20, 10, 20);
    }

    @Test void t01Id() { assertEquals(id, c.getId()); }
    @Test void t02Owner() { assertEquals(owner, c.getOwner()); }
    @Test void t03World() { assertEquals("world", c.getWorld()); }
    @Test void t04MinX() { assertEquals(-10, c.getMinX()); }
    @Test void t05MaxX() { assertEquals(10, c.getMaxX()); }
    @Test void t06MinZ() { assertEquals(-20, c.getMinZ()); }
    @Test void t07MaxZ() { assertEquals(20, c.getMaxZ()); }
    @Test void t08Size() { assertEquals(861, c.size()); }
    @Test void t09ChunkCount() { assertEquals(8, c.chunkCount()); }
    @Test void t10MemberOwner() { assertTrue(c.isMember(owner)); }
    @Test void t11MemberOther() { assertFalse(c.isMember(UUID.randomUUID())); }
    @Test void t12AddMember() { UUID u = UUID.randomUUID(); c.addMember(u); assertTrue(c.isMember(u)); }
    @Test void t13RemoveMember() { UUID u = UUID.randomUUID(); c.addMember(u); c.removeMember(u); assertFalse(c.isMember(u)); }
    @Test void t14FlagDefault() { assertFalse(c.getFlag(ClaimFlag.PVP)); }
    @Test void t15FlagSet() { c.setFlag(ClaimFlag.PVP, true); assertTrue(c.getFlag(ClaimFlag.PVP)); }
    @Test void t16EntryDefault() { assertTrue(c.getFlag(ClaimFlag.ENTRY)); }
    @Test void t17NullWorldLocationIsOutside() { assertFalse(c.contains(new Location(null, 0, 0, 0))); }
    @Test void t18Outside() { assertFalse(c.contains(new Location(null, 11, 0, 0))); }
    @Test void t19Overlap() { Claim x = new Claim(UUID.randomUUID(), UUID.randomUUID(), "world", 0, 0, 20, 30); assertTrue(c.overlaps(x)); }
    @Test void t20NoOverlap() { Claim x = new Claim(UUID.randomUUID(), UUID.randomUUID(), "world", 50, 50, 60, 60); assertFalse(c.overlaps(x)); }
    @Test void t21OtherWorld() { Claim x = new Claim(UUID.randomUUID(), UUID.randomUUID(), "nether", 0, 0, 20, 30); assertFalse(c.overlaps(x)); }
    @Test void t22ReverseCoords() { Claim x = new Claim(UUID.randomUUID(), owner, "world", 20, 20, -10, -20); assertEquals(-10, x.getMinX()); }
    @Test void t23Fire() { assertFalse(c.getFlag(ClaimFlag.FIRE)); }
    @Test void t24Explosions() { assertFalse(c.getFlag(ClaimFlag.EXPLOSIONS)); }
    @Test void t25MobGrief() { assertFalse(c.getFlag(ClaimFlag.MOB_GRIEFING)); }
    @Test void t26Fluids() { assertFalse(c.getFlag(ClaimFlag.FLUIDS)); }
    @Test void t27MembersAreReadOnly() { assertThrows(UnsupportedOperationException.class, () -> c.getMembers().add(UUID.randomUUID())); }
    @Test void t28FlagsAreCopied() { var flags = c.getFlags(); flags.put(ClaimFlag.PVP, true); assertFalse(c.getFlag(ClaimFlag.PVP)); }
    @Test void t29NullLocation() { assertFalse(c.contains(null)); }
    @Test void t30NullMemberIsNotMember() { assertFalse(c.isMember(null)); }
    @Test void t31OwnerCannotBecomeRegularMember() { c.addMember(owner); assertEquals(0, c.getMembers().size()); assertTrue(c.isMember(owner)); }
    @Test void t32TouchingClaimsOverlap() { Claim x = new Claim(UUID.randomUUID(), UUID.randomUUID(), "world", 10, 20, 30, 40); assertTrue(c.overlaps(x)); }
    @Test void t33SeparatedByOneBlockDoesNotOverlap() { Claim x = new Claim(UUID.randomUUID(), UUID.randomUUID(), "world", 11, 21, 30, 40); assertFalse(c.overlaps(x)); }
    @Test void t34EmptyWorldIsRejected() { assertThrows(IllegalArgumentException.class, () -> new Claim(UUID.randomUUID(), owner, "", 0, 0, 1, 1)); }
}
