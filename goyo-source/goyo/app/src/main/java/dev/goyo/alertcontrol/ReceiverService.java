package dev.goyo.alertcontrol;

import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

import dev.goyo.alertcontrol.core.PackageDumpParser;
import dev.goyo.alertcontrol.core.Targets;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Shizuku-only Binder, intentionally not exported as an Android Service. */
public final class ReceiverService extends IReceiverService.Stub {
    private static final String[] STATE_COMMANDS = {
            "default-state", "enable", "disable", "disable-user", "disable-until-used"
    };
    private int ownerUid = -1;

    public ReceiverService() {}

    private void enforceCaller(int userId) throws Exception {
        Targets.requireUser(userId);
        int caller = Binder.getCallingUid();
        if (caller / 100000 != userId) throw new SecurityException("다른 사용자에 대한 변경은 허용되지 않습니다.");
        int self = Process.myUid();
        if (self != 0 && self != 2000) throw new SecurityException("Shizuku 권한이 필요합니다.");
        if (ownerUid != caller) {
            String packages = run("/system/bin/cmd", "package", "list", "packages", "-U", "--user", "0", BuildConfig.APPLICATION_ID);
            boolean matches = false;
            for (String line : packages.split("\\R")) {
                if (line.trim().equals("package:" + BuildConfig.APPLICATION_ID + " uid:" + caller)) matches = true;
            }
            if (!matches) throw new SecurityException("요청 앱을 확인할 수 없습니다.");
            ownerUid = caller;
        }
    }

    @Override public synchronized Bundle inspect(int userId) {
        try {
            enforceCaller(userId);
            String installed = systemPackages(userId);
            List<String> names = new ArrayList<>();
            List<Integer> values = new ArrayList<>();
            for (String name : Targets.PACKAGES) {
                if (PackageDumpParser.hasPackage(installed, name)) {
                    names.add(name);
                    values.add(readState(name, userId));
                }
            }
            int[] states = new int[values.size()];
            for (int i = 0; i < values.size(); i++) states[i] = values.get(i);
            Bundle reply = new Bundle();
            reply.putStringArrayList("packages", new ArrayList<>(names));
            reply.putIntArray("states", states);
            reply.putInt("uid", Process.myUid());
            return reply;
        } catch (Exception e) { return error(e); }
    }

    @Override public synchronized Bundle change(String name, int userId, int state) {
        try {
            enforceCaller(userId);
            Targets.requirePackage(name);
            Targets.requireState(state);
            if (!PackageDumpParser.hasPackage(systemPackages(userId), name)) {
                throw new SecurityException("확인된 시스템 수신 앱만 변경할 수 있습니다.");
            }
            readState(name, userId);
            run("/system/bin/pm", STATE_COMMANDS[state], "--user", Integer.toString(userId), name);
            int actual = readState(name, userId);
            if (actual != state) throw new IllegalStateException("시스템이 요청 상태를 적용하지 않았습니다.");
            Bundle reply = new Bundle();
            reply.putInt("state", actual);
            return reply;
        } catch (Exception e) { return error(e); }
    }

    private static String systemPackages(int userId) throws Exception {
        return run("/system/bin/cmd", "package", "list", "packages", "-s", "--user", Integer.toString(userId));
    }

    private static int readState(String name, int userId) throws Exception {
        return PackageDumpParser.readState(run("/system/bin/dumpsys", "package", name), userId);
    }

    private static Bundle error(Exception e) {
        Bundle reply = new Bundle();
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        reply.putString("error", message.length() > 2500 ? message.substring(0, 2500) : message);
        return reply;
    }

    private static String run(String... command) throws Exception {
        // Separate argv, never sh -c or caller-supplied shell text.
        java.lang.Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        AtomicReference<Exception> readError = new AtomicReference<>();
        Thread reader = new Thread(() -> {
            try (InputStream stream = process.getInputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                boolean overflow = false;
                while ((count = stream.read(buffer)) != -1) {
                    if (bytes.size() + count <= 256 * 1024) bytes.write(buffer, 0, count);
                    else overflow = true;
                }
                if (overflow) readError.set(new IllegalStateException("시스템 응답이 너무 큽니다."));
            } catch (Exception e) { readError.set(e); }
        }, "goyo-command-output");
        reader.setDaemon(true);
        reader.start();
        try {
            if (!process.waitFor(12, TimeUnit.SECONDS)) {
                throw new IllegalStateException("시스템 응답 시간이 초과되었습니다. 상태를 다시 확인하세요.");
            }
            reader.join(2000);
            if (reader.isAlive()) throw new IllegalStateException("시스템 응답을 끝까지 읽지 못했습니다.");
            if (readError.get() != null) throw readError.get();
            String output = bytes.toString(StandardCharsets.UTF_8.name()).trim();
            if (process.exitValue() != 0) throw new IllegalStateException(output.isEmpty() ? "시스템 명령이 거부되었습니다." : output);
            return output;
        } finally {
            process.destroy();
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    @Override public void destroy() { System.exit(0); }
}
