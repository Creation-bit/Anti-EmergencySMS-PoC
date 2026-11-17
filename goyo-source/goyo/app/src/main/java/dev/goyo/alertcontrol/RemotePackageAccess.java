package dev.goyo.alertcontrol;

import android.os.Bundle;
import java.util.ArrayList;
import java.util.List;
import dev.goyo.alertcontrol.core.PackageAccess;
import dev.goyo.alertcontrol.core.ReceiverState;

final class RemotePackageAccess implements PackageAccess {
    private final IReceiverService service;
    private final int userId;

    RemotePackageAccess(IReceiverService service, int userId) {
        this.service = service;
        this.userId = userId;
    }

    private static void requireSuccess(Bundle result) {
        if (result == null) throw new IllegalStateException("Shizuku 응답이 없습니다.");
        if (result.containsKey("error")) throw new IllegalStateException(result.getString("error"));
    }

    @Override public List<ReceiverState> inspect() throws Exception {
        Bundle result = service.inspect(userId);
        requireSuccess(result);
        ArrayList<String> names = result.getStringArrayList("packages");
        int[] states = result.getIntArray("states");
        if (names == null || states == null || names.size() != states.length) {
            throw new IllegalStateException("수신 앱 상태 응답이 올바르지 않습니다.");
        }
        List<ReceiverState> list = new ArrayList<>();
        for (int i = 0; i < states.length; i++) list.add(new ReceiverState(names.get(i), states[i]));
        return list;
    }

    @Override public void setState(String name, int state) throws Exception {
        requireSuccess(service.change(name, userId, state));
    }
}
