package dev.goyo.alertcontrol.core;

import java.util.List;
import java.util.Arrays;
import java.util.Collections;

public final class Targets {
    private Targets() {}
    public static final List<String> PACKAGES = Collections.unmodifiableList(Arrays.asList(
            "com.android.cellbroadcastreceiver", "com.google.android.cellbroadcastreceiver"));

    public static void requirePackage(String name) {
        if (!PACKAGES.contains(name)) throw new IllegalArgumentException("지원하지 않는 패키지입니다.");
    }
    public static void requireState(int state) {
        if (state < 0 || state > 4) throw new IllegalArgumentException("잘못된 패키지 상태입니다.");
    }
    public static void requireUser(int userId) {
        if (userId != 0) throw new IllegalArgumentException("기본 사용자(사용자 0)에서만 지원합니다.");
    }
    public static String stateLabel(int state) {
        switch (state) {
            case 0: return "시스템 기본값";
            case 1: return "사용 중";
            case 2: return "비활성화";
            case 3: return "사용자 비활성화";
            case 4: return "실행 전까지 비활성화";
            default: return "알 수 없음";
        }
    }
}
