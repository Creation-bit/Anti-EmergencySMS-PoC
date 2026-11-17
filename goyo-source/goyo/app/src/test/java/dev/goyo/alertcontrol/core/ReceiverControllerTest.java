package dev.goyo.alertcontrol.core;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReceiverControllerTest {
    private static final String A = Targets.PACKAGES.get(0);
    private static final String G = Targets.PACKAGES.get(1);

    private static class MemoryStore implements RestoreStore {
        Map<String, Integer> saved = new LinkedHashMap<>();
        boolean failSave;
        @Override public Map<String, Integer> load() { return new LinkedHashMap<>(saved); }
        @Override public void save(Map<String, Integer> states) throws Exception {
            if (failSave) throw new Exception("disk full");
            saved = new LinkedHashMap<>(states);
        }
    }
    private static class Device implements PackageAccess {
        final Map<String, Integer> states = new LinkedHashMap<>();
        final List<String> writes = new ArrayList<>();
        String denyDisable;
        String denyRestore;
        String silentlyIgnore;
        boolean failAfterWrite;
        boolean hideAfterWrite;
        MemoryStore mustSaveBeforeWrite;
        Device(int a, Integer g) { states.put(A, a); if (g != null) states.put(G, g); }
        @Override public List<ReceiverState> inspect() {
            List<ReceiverState> result = new ArrayList<>();
            if (hideAfterWrite && !writes.isEmpty()) return result;
            states.forEach((name, value) -> result.add(new ReceiverState(name, value)));
            return result;
        }
        @Override public void setState(String name, int state) throws Exception {
            if (mustSaveBeforeWrite != null) assertTrue(mustSaveBeforeWrite.saved.containsKey(name));
            writes.add(name + ":" + state);
            if (state == 3 && name.equals(denyDisable)) throw new SecurityException("protected package");
            if (state != 3 && name.equals(denyRestore)) throw new SecurityException("permission revoked");
            if (!name.equals(silentlyIgnore)) states.put(name, state);
            if (failAfterWrite && state == 3) throw new Exception("lost binder after committing change");
        }
    }

    @Test public void disablesBothAndRestoresExactOriginalStates() throws Exception {
        Device device = new Device(0, 1);
        MemoryStore store = new MemoryStore();
        device.mustSaveBeforeWrite = store;
        ReceiverController controller = new ReceiverController(device, store);
        assertTrue(ReceiverController.allDisabled(controller.disable()));
        assertEquals(Integer.valueOf(0), store.saved.get(A));
        assertEquals(Integer.valueOf(1), store.saved.get(G));
        controller.restore();
        assertEquals(Integer.valueOf(0), device.states.get(A));
        assertEquals(Integer.valueOf(1), device.states.get(G));
        assertTrue(store.saved.isEmpty());
    }

    @Test public void failedDurableSavePreventsAnyDeviceChange() {
        Device device = new Device(0, null);
        MemoryStore store = new MemoryStore(); store.failSave = true;
        assertThrows(Exception.class, () -> new ReceiverController(device, store).disable());
        assertTrue(device.writes.isEmpty());
        assertEquals(Integer.valueOf(0), device.states.get(A));
    }

    @Test public void partialFailureRestoresChangedPackagesAndVerifiesRejectedOne() {
        Device device = new Device(0, 1); device.denyDisable = G;
        MemoryStore store = new MemoryStore();
        assertThrows(Exception.class, () -> new ReceiverController(device, store).disable());
        assertEquals(Integer.valueOf(0), device.states.get(A));
        assertEquals(Integer.valueOf(1), device.states.get(G));
        assertFalse(device.writes.contains(G + ":1"));
        assertEquals(2, store.saved.size());
    }

    @Test public void fullyProtectedButUnchangedPackageDoesNotClaimRollbackFailure() {
        Device device = new Device(0, null);
        device.denyDisable = A;
        device.denyRestore = A;
        MemoryStore store = new MemoryStore();
        Exception failure = assertThrows(Exception.class, () -> new ReceiverController(device, store).disable());
        assertFalse(failure.getMessage().contains("되돌리기도 실패"));
        assertEquals(Integer.valueOf(0), device.states.get(A));
        assertTrue(store.saved.containsKey(A));
    }

    @Test public void lostReplyAfterSuccessfulWriteIsRolledBack() {
        Device device = new Device(0, null); device.failAfterWrite = true;
        MemoryStore store = new MemoryStore();
        assertThrows(Exception.class, () -> new ReceiverController(device, store).disable());
        assertEquals(Integer.valueOf(0), device.states.get(A));
        assertTrue(store.saved.containsKey(A));
    }

    @Test public void silentlyIgnoredWriteNeverReportsSuccess() {
        Device device = new Device(0, null); device.silentlyIgnore = A;
        assertThrows(Exception.class, () -> new ReceiverController(device, new MemoryStore()).disable());
        assertEquals(Integer.valueOf(0), device.states.get(A));
    }

    @Test public void rollbackFailureKeepsRecoveryRecord() {
        Device device = new Device(0, 1); device.denyDisable = G; device.denyRestore = A;
        MemoryStore store = new MemoryStore();
        Exception failure = assertThrows(Exception.class, () -> new ReceiverController(device, store).disable());
        assertTrue(failure.getMessage().contains("되돌리기도 실패"));
        assertEquals(Integer.valueOf(3), device.states.get(A));
        assertEquals(Integer.valueOf(0), store.saved.get(A));
    }

    @Test public void emptyDiscoveryCannotMeanDisabled() {
        Device device = new Device(0, null); device.states.clear();
        assertFalse(ReceiverController.allDisabled(device.inspect()));
        assertThrows(Exception.class, () -> new ReceiverController(device, new MemoryStore()).disable());
        assertTrue(device.writes.isEmpty());
    }

    @Test public void disappearingPackageFailsVerification() {
        Device device = new Device(0, null); device.hideAfterWrite = true;
        MemoryStore store = new MemoryStore();
        assertThrows(Exception.class, () -> new ReceiverController(device, store).disable());
        assertTrue(store.saved.containsKey(A));
    }

    @Test public void alreadyDisabledBySomeoneElseIsNotOwnedOrRestored() throws Exception {
        Device device = new Device(2, 0);
        MemoryStore store = new MemoryStore();
        ReceiverController controller = new ReceiverController(device, store);
        controller.disable();
        assertFalse(store.saved.containsKey(A));
        controller.restore();
        assertEquals(Integer.valueOf(2), device.states.get(A));
        assertEquals(Integer.valueOf(0), device.states.get(G));
    }

    @Test public void repeatedDisableDoesNotOverwriteOriginalState() throws Exception {
        Device device = new Device(1, null);
        MemoryStore store = new MemoryStore();
        ReceiverController controller = new ReceiverController(device, store);
        controller.disable();
        controller.disable();
        assertEquals(Integer.valueOf(1), store.saved.get(A));
        assertEquals(1, device.writes.size());
    }

    @Test public void recoverySurvivesControllerRecreation() throws Exception {
        Device device = new Device(0, null);
        MemoryStore store = new MemoryStore();
        new ReceiverController(device, store).disable();
        new ReceiverController(device, store).restore();
        assertEquals(Integer.valueOf(0), device.states.get(A));
        assertTrue(store.saved.isEmpty());
    }

    @Test public void restoreContinuesOtherPackagesAndKeepsOnlyFailedRecord() throws Exception {
        Device device = new Device(0, 1);
        MemoryStore store = new MemoryStore();
        ReceiverController controller = new ReceiverController(device, store);
        controller.disable();
        device.denyRestore = A;
        assertThrows(Exception.class, controller::restore);
        assertEquals(1, store.saved.size());
        assertTrue(store.saved.containsKey(A));
        assertEquals(Integer.valueOf(1), device.states.get(G));
        device.denyRestore = null;
        controller.restore();
        assertTrue(store.saved.isEmpty());
    }

    @Test public void missingRestorePackageKeepsRecord() throws Exception {
        Device device = new Device(0, null);
        MemoryStore store = new MemoryStore();
        ReceiverController controller = new ReceiverController(device, store);
        controller.disable();
        device.states.clear();
        assertThrows(Exception.class, controller::restore);
        assertTrue(store.saved.containsKey(A));
    }

    @Test public void disabledUntilUsedIsNotAConfirmedBlock() throws Exception {
        Device device = new Device(4, null);
        assertFalse(ReceiverController.allDisabled(device.inspect()));
        MemoryStore store = new MemoryStore();
        ReceiverController controller = new ReceiverController(device, store);
        controller.disable();
        assertEquals(Integer.valueOf(4), store.saved.get(A));
        controller.restore();
        assertEquals(Integer.valueOf(4), device.states.get(A));
    }

    @Test public void restoreWithoutBackupDoesNotEnableOtherApps() {
        Device device = new Device(3, null);
        assertThrows(Exception.class, () -> new ReceiverController(device, new MemoryStore()).restore());
        assertTrue(device.writes.isEmpty());
    }

    @Test public void mixedStateIsNotAllDisabled() {
        assertFalse(ReceiverController.allDisabled(Arrays.asList(new ReceiverState(A, 3), new ReceiverState(G, 0))));
        assertTrue(ReceiverController.allDisabled(Collections.singletonList(new ReceiverState(A, 2))));
    }
}
