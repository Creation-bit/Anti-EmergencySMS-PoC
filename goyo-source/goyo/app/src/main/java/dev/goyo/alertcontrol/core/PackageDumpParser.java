package dev.goyo.alertcontrol.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PackageDumpParser {
    private PackageDumpParser() {}

    public static boolean hasPackage(String packageList, String name) {
        Targets.requirePackage(name);
        for (String line : packageList.split("\\R")) {
            if (line.trim().equals("package:" + name)) return true;
        }
        return false;
    }

    public static int readState(String dump, int userId) {
        Targets.requireUser(userId);
        Matcher user = Pattern.compile("(?m)^\\s*User " + userId + ":([^\\r\\n]*)$").matcher(dump);
        if (!user.find()) throw new IllegalStateException("사용자별 패키지 상태를 읽지 못했습니다.");
        String row = user.group(1);
        if (!Pattern.compile("\\binstalled=true\\b").matcher(row).find()) {
            throw new IllegalStateException("현재 사용자에 설치되지 않은 패키지입니다.");
        }
        Matcher state = Pattern.compile("\\benabled=([0-4])(?:\\s|$)").matcher(row);
        if (!state.find()) throw new IllegalStateException("지원하지 않는 패키지 상태 형식입니다.");
        return Integer.parseInt(state.group(1));
    }
}
