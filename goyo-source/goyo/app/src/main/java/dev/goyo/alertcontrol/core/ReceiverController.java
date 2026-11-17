package dev.goyo.alertcontrol.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All writes have a durable, write-ahead restore record and are verified by a fresh read. */
public final class ReceiverController {
    private final PackageAccess access;
    private final RestoreStore store;

    public ReceiverController(PackageAccess access, RestoreStore store) {
        this.access = access;
        this.store = store;
    }

    public static boolean allDisabled(List<ReceiverState> states) {
        if (states.isEmpty()) return false;
        for (ReceiverState state : states) if (!state.isDisabled()) return false;
        return true;
    }

    public synchronized List<ReceiverState> disable() throws Exception {
        List<ReceiverState> before = access.inspect();
        if (before.isEmpty()) throw new IllegalStateException("지원 대상 수신 앱을 찾지 못했습니다.");
        Map<String, Integer> restore = new LinkedHashMap<>(store.load());
        List<ReceiverState> changes = new ArrayList<>();
        for (ReceiverState state : before) {
            if (!state.isDisabled()) {
                changes.add(state);
                restore.putIfAbsent(state.packageName, state.state);
            }
        }
        if (changes.isEmpty()) return before;
        // Must complete synchronously before the first remote mutation.
        store.save(restore);
        List<ReceiverState> attempted = new ArrayList<>();
        try {
            for (ReceiverState state : changes) {
                // Include a timed-out/failed call because it may have changed the device.
                attempted.add(state);
                access.setState(state.packageName, 3);
            }
            List<ReceiverState> after = access.inspect();
            for (ReceiverState previous : before) {
                ReceiverState now = find(after, previous.packageName);
                if (now == null || !now.isDisabled()) {
                    throw new IllegalStateException("기기가 비활성화를 적용하지 않았습니다.");
                }
            }
            if (!allDisabled(after)) throw new IllegalStateException("일부 수신 앱이 활성 상태입니다.");
            return after;
        } catch (Exception failure) {
            boolean rollbackFailed = false;
            for (int i = attempted.size() - 1; i >= 0; i--) {
                ReceiverState previous = attempted.get(i);
                try {
                    ReceiverState current = find(access.inspect(), previous.packageName);
                    if (current == null || current.state != previous.state) {
                        access.setState(previous.packageName, previous.state);
                    }
                    ReceiverState verified = find(access.inspect(), previous.packageName);
                    if (verified == null || verified.state != previous.state) {
                        throw new IllegalStateException("되돌리기 검증에 실패했습니다.");
                    }
                } catch (Exception rollback) {
                    rollbackFailed = true;
                    failure.addSuppressed(rollback);
                }
            }
            // Keep the restore record even after rollback. A later explicit restore is safe.
            throw new Exception(rollbackFailed
                    ? "변경을 완료하지 못했고 일부 되돌리기도 실패했습니다. 권한을 연결한 뒤 이전 상태 복원을 실행하세요."
                    : "기기가 변경을 거부했거나 검증에 실패했습니다. 시도한 변경은 되돌렸습니다.", failure);
        }
    }

    public synchronized List<ReceiverState> restore() throws Exception {
        Map<String, Integer> remaining = new LinkedHashMap<>(store.load());
        if (remaining.isEmpty()) throw new IllegalStateException("저장된 복원 기록이 없습니다.");
        Exception failure = null;
        for (Map.Entry<String, Integer> entry : new LinkedHashMap<>(remaining).entrySet()) {
            try {
                ReceiverState current = find(access.inspect(), entry.getKey());
                if (current == null) throw new IllegalStateException("복원할 패키지를 찾지 못했습니다: " + entry.getKey());
                if (current.state != entry.getValue()) access.setState(entry.getKey(), entry.getValue());
                ReceiverState verified = find(access.inspect(), entry.getKey());
                if (verified == null || verified.state != entry.getValue()) {
                    throw new IllegalStateException("복원 결과를 확인하지 못했습니다: " + entry.getKey());
                }
                remaining.remove(entry.getKey());
                store.save(remaining);
            } catch (Exception e) {
                if (failure == null) failure = new Exception("일부 복원이 완료되지 않았습니다. 기록을 보존했으니 다시 시도하세요.", e);
                else failure.addSuppressed(e);
            }
        }
        if (failure != null) throw failure;
        return access.inspect();
    }

    private static ReceiverState find(List<ReceiverState> states, String name) {
        for (ReceiverState state : states) if (state.packageName.equals(name)) return state;
        return null;
    }
}
