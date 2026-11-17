# 고요 — Android 재난 알림 제어

Shizuku 권한으로 **시스템 재난문자 수신 앱(CellBroadcastReceiver)을 비활성화하고, 변경 전 상태로 복원**하는 한국어 Android 앱입니다.

**모뎀·기지국 단계의 원천 수신 차단 앱은 아닙니다.** Android 공개 API에는 일반 앱이 해당 무선 수신을 전면 차단하는 기능이 없습니다. 이 앱은 확인된 수신 앱의 실행을 끄는 방식이며, 제조사별 별도 전달 경로·필수 경보·시스템 정책까지 차단한다고 보장하지 않습니다.

## 설치 및 사용

1. `dist/goyo-1.0.0.apk`를 휴대폰으로 옮겨 설치합니다.
2. [공식 Shizuku](https://shizuku.rikka.app/download/)를 설치하고 시작합니다. Android 11 이상은 무선 디버깅, 이전 버전은 PC의 ADB로 시작할 수 있습니다. [공식 설정 안내](https://shizuku.rikka.app/guide/setup/)
3. **고요 → Shizuku 연결**에서 권한을 허용합니다.
4. 탐지된 수신 앱을 확인하고 **수신 앱 비활성화**를 누릅니다.
5. 실제 패키지 상태가 확인되어야 **수신 앱 꺼짐**으로 표시됩니다.
6. 다시 사용하려면 **이전 상태 복원**을 누릅니다.

비활성화하면 해당 수신 앱이 표시하는 모든 종류의 재난 알림에 영향을 줍니다. 종류별 선택 차단은 지원하지 않습니다. 종류별 설정은 앱의 **안드로이드 재난문자 설정 열기**를 이용하세요.

## 지원 범위

| 항목 | 지원 내용 |
| --- | --- |
| OS | Android 8.0(API 26) 이상에 설치 가능, compile/target SDK 36 |
| 권한 | Shizuku/Sui가 제공하는 shell(2000) 또는 root(0) 권한 |
| 사용자 | 기본 사용자 0만 지원. 업무 프로필·보조 사용자는 변경하지 않음 |
| 대상 | `com.android.cellbroadcastreceiver`, `com.google.android.cellbroadcastreceiver` 중 실제 설치된 시스템 앱 |
| 보호된 패키지 | OS에서 거부하면 실패로 표시하고 시도한 변경을 되돌림 |
| 미지원 OEM | 대상이 없으면 자동 제어 미지원으로 표시. 임의의 메시지 앱을 비활성화하지 않음 |
| 재부팅 | 패키지 상태는 OS가 관리. 비루팅 Shizuku는 보통 다시 시작해야 상태 확인·복원 가능 |
| 업데이트 | OS/통신사/CellBroadcast 업데이트 후 상태를 다시 확인할 것 |

제조사별 실기기 및 실제 재난문자 송출을 통한 검증은 수행하지 않았습니다. `수신 앱 꺼짐`은 대상 패키지의 상태 확인 결과이며, 실제 무선 수신 차단 시험 결과가 아닙니다.

## 복원과 앱 삭제

- 변경 전에 기존 `enabled` 값을 앱 내부 저장소에 **동기 저장**합니다.
- 기본값(0), 명시적 활성화(1) 등 원래 상태를 그대로 복원합니다.
- 기존에 다른 방법으로 비활성화된 패키지는 복원 대상에 추가하지 않습니다.
- 일부 변경 실패 시 시도한 모든 변경을 역순으로 되돌립니다. 되돌리기 실패도 화면에 표시합니다.
- 복원은 패키지별로 확인하고, 실패·누락된 패키지의 기록은 남겨 재시도할 수 있습니다.
- 앱 재실행·화면 회전 후에도 복원 기록이 남습니다. 기기 간 백업·이전은 제외합니다.
- **앱 삭제/데이터 초기화가 시스템 앱을 자동으로 되살리지 않습니다. 먼저 복원하세요.**

Shizuku가 작동하지 않으면 **복원 명령 보기**에서 저장된 원래 값에 해당하는 ADB 명령을 복사할 수 있습니다. 앱을 삭제하기 전 명령을 별도 보관할 수 있습니다.

복원 기록까지 지운 경우의 수동 활성화 예시는 아래와 같습니다. 설치된 대상에만 실행하세요. 이 명령은 `enabled=1`로 활성화하며, 지워진 원래 상태를 복원하는 것은 아닙니다.

```text
adb shell pm enable --user 0 com.android.cellbroadcastreceiver
adb shell pm enable --user 0 com.google.android.cellbroadcastreceiver
```

제조사 정책으로 ADB 변경 자체가 거부되면 이 복원 방법도 지원되지 않을 수 있습니다.

## 빌드

- JDK 17 이상(이 작업에서는 Zulu JDK 21 사용)
- Android SDK Platform 36, Build Tools 35.0.0
- Gradle 8.13 / Android Gradle Plugin 8.13.2
- Shizuku API / Provider 13.1.5

Android Studio에서 프로젝트 폴더를 열거나, SDK 위치를 `local.properties`의 `sdk.dir`에 지정하고 실행합니다.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleRelease
```

macOS/Linux:

```sh
sh ./gradlew testDebugUnitTest lintDebug assembleRelease
```

`app/build/outputs/apk/release/app-release-unsigned.apk`는 서명 전 산출물입니다. 배포하려면 본인의 키로 서명하세요. 제공 APK는 이 프로젝트에서 생성한 로컬 서명 키로 서명한 개인 설치용 빌드입니다. 서명 키/비밀번호/SDK 캐시는 소스 ZIP에 포함하지 않습니다.

## 구현과 검증

- `ReceiverService`: Shizuku UserService. 알려진 두 패키지와 상태 0~4만 허용. 요청 앱 UID와 기본 사용자를 검증합니다.
- 명령 인자를 배열로 전달하며 `sh -c`, 임의 셸 입력, 패키지 삭제는 사용하지 않습니다.
- 프로세스 출력 크기와 실행 시간을 제한하고, 종료 코드와 변경 후 상태를 확인합니다.
- `ReceiverController`: 변경 전 기록, 부분 실패 시 되돌리기, 재실행 후 복원.
- `ControlModel`: 앱 범위 작업 큐로 화면 재생성 시 중복 변경 방지. Binder 사망 시 확인 상태 해제.
- 인터넷·SMS·알림 접근·접근성 권한을 요구하지 않습니다. 외부 안내 링크는 사용자가 눌렀을 때 브라우저로 엽니다.
- `app/src/test`: 보호 패키지, 응답 유실, 변경 무시, 빈/누락 패키지, 저장 실패, 부분 복원, 재생성, 사용자·패키지 제한에 대한 JVM 단위 테스트.

실제 기기에서 확인할 항목은 [DEVICE_TESTS.md](DEVICE_TESTS.md)에 정리했습니다. 빌드 결과는 `dist/BUILD-REPORT.md`를 참고하세요.

## 근거 자료

- [Android CellBroadcast 구조와 시스템 앱 역할](https://source.android.com/docs/core/ota/modular-system/cellbroadcast)
- [Shizuku API 및 UserService 공식 문서](https://github.com/RikkaApps/Shizuku-API)
- [AGP 8.13 호환성](https://developer.android.com/build/releases/agp-8-13-0-release-notes)

Shizuku API는 MIT 라이선스입니다. [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)를 참고하세요.
