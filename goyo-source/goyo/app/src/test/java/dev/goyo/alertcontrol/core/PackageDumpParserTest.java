package dev.goyo.alertcontrol.core;

import static org.junit.Assert.*;
import org.junit.Test;

public class PackageDumpParserTest {
    @Test public void selectsOnlyRequestedUser() {
        String dump = "Packages:\n  User 10: installed=true hidden=false enabled=3\n  User 0: ceDataInode=234 installed=true hidden=false suspended=false stopped=false enabled=1 instant=false\n";
        assertEquals(1, PackageDumpParser.readState(dump, 0));
    }
    @Test public void defaultAndDisabledStatesAreParsed() {
        for (int state = 0; state <= 4; state++) {
            assertEquals(state, PackageDumpParser.readState("    User 0: installed=true enabled=" + state + "\r\n", 0));
        }
    }
    @Test public void uninstalledForUserIsRejected() {
        assertThrows(IllegalStateException.class, () -> PackageDumpParser.readState("User 0: installed=false enabled=0", 0));
    }
    @Test public void unknownDumpFormatIsRejected() {
        assertThrows(IllegalStateException.class, () -> PackageDumpParser.readState("Error: Permission denied", 0));
        assertThrows(IllegalStateException.class, () -> PackageDumpParser.readState("User 0: installed=true enabled=9", 0));
        assertThrows(IllegalStateException.class, () -> PackageDumpParser.readState("User 0: installed=true enabled=30", 0));
    }
    @Test public void packageMatchIsExact() {
        String name = Targets.PACKAGES.get(0);
        assertTrue(PackageDumpParser.hasPackage("package:" + name + "\n", name));
        assertFalse(PackageDumpParser.hasPackage("package:" + name + ".overlay\n", name));
        assertFalse(PackageDumpParser.hasPackage("error: " + name, name));
    }
    @Test public void arbitraryPackagesAndShellTextAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Targets.requirePackage("com.android.phone"));
        assertThrows(IllegalArgumentException.class, () -> Targets.requirePackage("com.android.cellbroadcastreceiver; reboot"));
        assertThrows(IllegalArgumentException.class, () -> Targets.requirePackage("com.android.cellbroadcastservice"));
    }
    @Test public void secondaryUsersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Targets.requireUser(10));
        assertThrows(IllegalArgumentException.class, () -> Targets.requireUser(-1));
        Targets.requireUser(0);
    }
    @Test public void invalidStatesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ReceiverState(Targets.PACKAGES.get(0), -1));
        assertThrows(IllegalArgumentException.class, () -> new ReceiverState(Targets.PACKAGES.get(0), 5));
    }
}
