package dev.goyo.alertcontrol;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.goyo.alertcontrol.core.ReceiverController;
import dev.goyo.alertcontrol.core.ReceiverState;
import rikka.shizuku.Shizuku;

/** Application-scoped state keeps an in-flight operation alive across Activity recreation. */
final class ControlModel {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<Runnable> observers = new ArrayList<>();
    final int userId = Process.myUid() / 100000;
    final PreferenceRestoreStore restoreStore;
    private final Shizuku.UserServiceArgs args;
    private IReceiverService service;
    private int bindingGeneration;
    boolean busy;
    boolean connecting;
    boolean inspected;
    boolean hasRestore;
    boolean storageError;
    List<ReceiverState> states = Collections.emptyList();
    String message = "Shizuku를 연결하면 수신 앱의 상태를 확인할 수 있어요.";
    String detail = "";

    ControlModel(Context context) {
        restoreStore = new PreferenceRestoreStore(context, userId);
        args = new Shizuku.UserServiceArgs(new ComponentName(context, ReceiverService.class))
                .daemon(false).processNameSuffix("receiver_control")
                .debuggable(BuildConfig.DEBUG).version(BuildConfig.VERSION_CODE);
        reloadRestore();
        Shizuku.addBinderReceivedListenerSticky(() -> {
            if (hasPermission() && !busy) bind();
            else publish();
        });
        Shizuku.addBinderDeadListener(() -> {
            service = null;
            connecting = false;
            inspected = false;
            bindingGeneration++;
            message = "Shizuku 연결이 끊겼습니다. 다시 연결해 현재 상태를 확인하세요.";
            publish();
        });
        Shizuku.addRequestPermissionResultListener((code, result) -> {
            if (code != 41) return;
            if (result == PackageManager.PERMISSION_GRANTED) bind();
            else {
                message = "Shizuku 권한이 허용되지 않았습니다. 기기 상태는 변경하지 않았어요.";
                publish();
            }
        });
    }

    void observe(Runnable observer) { observers.add(observer); observer.run(); }
    void removeObserver(Runnable observer) { observers.remove(observer); }
    private void publish() { for (Runnable observer : new ArrayList<>(observers)) observer.run(); }

    boolean connected() { return service != null && service.asBinder().isBinderAlive() && hasPermission(); }
    boolean hasPermission() {
        try {
            return Shizuku.pingBinder() && !Shizuku.isPreV11()
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (RuntimeException e) { return false; }
    }

    void connect() {
        if (busy || connecting) return;
        detail = "";
        if (userId != 0) {
            message = "기본 사용자에서 실행해 주세요. 보조 사용자와 업무 프로필은 지원하지 않습니다.";
            publish();
            return;
        }
        try {
            if (!Shizuku.pingBinder()) {
                message = "먼저 Shizuku 앱에서 서비스를 시작해 주세요.";
            } else if (Shizuku.isPreV11()) {
                message = "최신 Shizuku로 업데이트해 주세요.";
            } else if (hasPermission()) {
                bind();
                return;
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                message = "Shizuku 앱의 승인된 앱 관리에서 고요의 권한을 허용해 주세요.";
            } else {
                Shizuku.requestPermission(41);
                message = "Shizuku 권한 요청에서 허용을 선택해 주세요.";
            }
        } catch (RuntimeException e) {
            message = "Shizuku에 연결하지 못했습니다.";
            detail = describe(e);
        }
        publish();
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            main.post(() -> {
                bindingGeneration++;
                connecting = false;
                service = IReceiverService.Stub.asInterface(binder);
                if (!busy) execute(0);
            });
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            main.post(() -> {
                service = null;
                inspected = false;
                connecting = false;
                message = "권한 서비스 연결이 끊겼습니다. 다시 연결해 주세요.";
                publish();
            });
        }
    };

    private void bind() {
        if (userId != 0 || busy || connecting) return;
        if (connected()) { execute(0); return; }
        connecting = true;
        message = "Shizuku 권한 서비스를 연결하고 있어요.";
        int generation = ++bindingGeneration;
        publish();
        try {
            Shizuku.bindUserService(args, connection);
            main.postDelayed(() -> {
                if (connecting && bindingGeneration == generation) {
                    connecting = false;
                    try { Shizuku.unbindUserService(args, connection, true); }
                    catch (RuntimeException ignored) { /* A dead binder needs no cleanup. */ }
                    message = "연결 시간이 초과되었습니다. Shizuku를 다시 시작한 뒤 연결해 주세요.";
                    publish();
                }
            }, 15000);
        } catch (RuntimeException e) {
            connecting = false;
            message = "Shizuku 권한 서비스를 시작하지 못했습니다.";
            detail = describe(e);
            publish();
        }
    }

    void refresh() {
        if (busy || connecting) return;
        if (connected()) execute(0);
        else if (hasPermission()) bind();
        else { inspected = false; reloadRestore(); publish(); }
    }

    // 0 = inspect, 1 = disable, 2 = restore. Only explicit UI taps request mutations.
    void execute(int operation) {
        if (busy || connecting) return;
        if (!connected()) { inspected = false; connect(); return; }
        if (userId != 0) return;
        if (operation == 1 && storageError) return;
        final IReceiverService remote = service;
        busy = true;
        detail = "";
        message = operation == 0 ? "기기에서 현재 상태를 확인하고 있어요."
                : operation == 1 ? "이전 상태를 저장하고 수신 앱을 비활성화하고 있어요."
                : "저장된 이전 상태로 복원하고 있어요.";
        publish();
        executor.execute(() -> {
            RemotePackageAccess access = new RemotePackageAccess(remote, userId);
            ReceiverController controller = new ReceiverController(access, restoreStore);
            List<ReceiverState> result = null;
            Exception error = null;
            try {
                if (operation == 1) result = controller.disable();
                else if (operation == 2) result = controller.restore();
                else result = access.inspect();
            } catch (Exception e) {
                error = e;
                try { result = access.inspect(); } catch (Exception ignored) { /* Unknown, never success. */ }
            }
            final List<ReceiverState> finished = result;
            final Exception failure = error;
            main.post(() -> {
                busy = false;
                inspected = finished != null && connected() && service == remote;
                states = inspected ? finished : Collections.emptyList();
                reloadRestore();
                if (failure != null) {
                    message = failure.getMessage() == null ? "작업에 실패했습니다." : failure.getMessage();
                    detail = describe(failure);
                } else if (!inspected) {
                    message = "작업 후 연결이 끊겼습니다. 다시 연결해 결과를 확인하세요.";
                } else if (operation == 2) {
                    message = "저장된 이전 상태로 복원하고 결과를 확인했습니다.";
                } else if (states.isEmpty()) {
                    message = "지원 대상 수신 앱을 찾지 못했습니다. 이 기기는 자동 제어를 지원하지 않습니다.";
                } else if (ReceiverController.allDisabled(states)) {
                    message = "확인된 수신 앱이 비활성화되어 있습니다. 모뎀의 수신 여부는 확인할 수 없습니다.";
                } else {
                    message = "수신 앱을 찾았습니다. 아래 버튼으로 비활성화할 수 있어요.";
                }
                publish();
            });
        });
    }

    private void reloadRestore() {
        try { hasRestore = !restoreStore.load().isEmpty(); storageError = false; }
        catch (Exception e) {
            storageError = true;
            hasRestore = true;
            detail = "복원 기록을 읽지 못했습니다. 앱 데이터를 삭제하지 말고 오류 정보를 확인하세요.\n" + describe(e);
        }
    }

    String recoveryCommands() {
        try {
            StringBuilder result = new StringBuilder();
            restoreStore.load().forEach((name, state) -> {
                String[] verbs = {"default-state", "enable", "disable", "disable-user", "disable-until-used"};
                result.append("adb shell pm ").append(verbs[state]).append(" --user ").append(userId).append(' ').append(name).append('\n');
            });
            return result.length() == 0 ? "저장된 복원 기록이 없습니다." : result.toString();
        } catch (Exception e) { return describe(e); }
    }

    private static String describe(Throwable error) {
        StringBuilder result = new StringBuilder();
        Throwable current = error;
        for (int i = 0; current != null && i < 4; i++, current = current.getCause()) {
            if (result.length() > 0) result.append("\n");
            result.append(current.getClass().getSimpleName()).append(": ").append(current.getMessage());
            for (Throwable suppressed : current.getSuppressed()) {
                result.append("\n되돌리기: ").append(suppressed.getMessage());
            }
        }
        return result.toString();
    }
}
