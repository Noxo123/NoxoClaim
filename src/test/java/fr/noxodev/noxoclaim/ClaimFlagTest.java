package fr.noxodev.noxoclaim;

import fr.noxodev.noxoclaim.models.ClaimFlag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClaimFlagTest {
    @Test
    void t01Count() {
        assertEquals(8, ClaimFlag.values().length);
    }

    @Test
    void t02Pvp() {
        assertNotNull(ClaimFlag.PVP);
    }

    @Test
    void t03Explosions() {
        assertNotNull(ClaimFlag.EXPLOSIONS);
    }

    @Test
    void t04Fire() {
        assertNotNull(ClaimFlag.FIRE);
    }

    @Test
    void t05MobGriefing() {
        assertNotNull(ClaimFlag.MOB_GRIEFING);
    }

    @Test
    void t06Fluids() {
        assertNotNull(ClaimFlag.FLUIDS);
    }

    @Test
    void t07Pistons() {
        assertNotNull(ClaimFlag.PISTONS);
    }

    @Test
    void t08EntityProtection() {
        assertNotNull(ClaimFlag.ENTITY_PROTECTION);
    }

    @Test
    void t09Entry() {
        assertNotNull(ClaimFlag.ENTRY);
    }
}
