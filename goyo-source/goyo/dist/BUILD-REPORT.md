# 고요 1.0.0 빌드 결과

- 빌드 날짜: 2026-11-17 (Asia/Seoul)
- APK: `goyo-1.0.0.apk` / 66,476 bytes
- 애플리케이션 ID: `dev.goyo.alertcontrol`
- Android 최소 API 26 / target·compile API 36
- JDK: Zulu 21.0.8
- Gradle 8.13 / AGP 8.13.2 / Build Tools 35.0.0
- Shizuku API·Provider 13.1.5 / AndroidX annotation 1.3.0
- Release 빌드, debuggable 플래그 없음
- APK Signature Scheme v2·v3 검증 통과 / RSA 3072
- zipalign 4-byte 정렬 검증 통과
- 선언 권한: `moe.shizuku.manager.permission.API_V23`만 있음
- 라이브러리 라이선스가 APK assets에 포함됨

## 실행한 검사

`testDebugUnitTest lintDebug assembleRelease`: 성공.

JVM 단위 테스트 25개 모두 통과: 패키지 파서 8개, 비활성화·복원 제어 17개.

Android Lint: 오류 0개, 경고 2개. 두 경고는 Gradle 및 annotation의 새 버전 안내입니다. Gradle은 AGP 8.13 호환 버전으로 고정했고 annotation은 Shizuku의 런타임 버전 제약과 일치시켰습니다.

최종 라이선스 파일 추가 후 `assembleRelease`를 다시 실행했습니다. APK 생성 후 서명·정렬·manifest·ZIP 무결성을 검증했습니다.

## 검증하지 않은 범위

실제 Android 기기/에뮬레이터의 화면 실행, Shizuku 서비스와의 실기기 통신, 제조사별 패키지 변경 정책, 실제 재난문자 수신 및 모뎀 차단 여부는 검증하지 않았습니다. 이 앱은 확인된 수신 앱을 비활성화하며, 원천 무선 수신 차단을 보장하지 않습니다.

## APK SHA-256

```text
791f1ba465d47410e31416af781df7637a96cefd6d5017aa51c710aac141466a
```

서명 인증서 SHA-256:

```text
7e1db71b371fa295d4ef2c875043d531a95d66456b0829ab9148900873163cd8
```

로컬 서명 키는 작업 폴더의 `.tooling/signing`에만 보관하며, 배포 ZIP에는 포함하지 않았습니다. 같은 앱의 후속 업데이트를 만들 때는 이 키를 유지해야 합니다.
