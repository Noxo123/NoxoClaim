package fr.noxodev.noxoclaim;

import fr.noxodev.noxoclaim.models.Claim;
import fr.noxodev.noxoclaim.models.ClaimFlag;
import org.bukkit.Location;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClaimTest {
    private static UUID owner;
    private static UUID id;
    private static Claim c;

    @BeforeAll
    static void init() {
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
    @Test void t15FlagSet() { assertTrue(c.getFlag(ClaimFlag.PVP)); }
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
}
