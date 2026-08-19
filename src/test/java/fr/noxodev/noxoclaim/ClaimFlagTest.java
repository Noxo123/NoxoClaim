package fr.noxodev.noxoclaim;
import fr.noxodev.noxoclaim.models.ClaimFlag; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class ClaimFlagTest {@Test void t01Count(){assertEquals(5,ClaimFlag.values().length);}@Test void t02Pvp(){assertNotNull(ClaimFlag.PVP);}@Test void t03Explosions(){assertNotNull(ClaimFlag.EXPLOSIONS);}@Test void t04Entry(){assertNotNull(ClaimFlag.ENTRY);}}
