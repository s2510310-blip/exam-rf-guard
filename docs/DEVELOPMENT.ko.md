# 빌드·구조·검증 안내

앱이 하는 일과 한계는 [README](../README.md)에 있습니다. 이 문서는 코드를 확인하거나 고치려는 사람을 위한 설명입니다.

## 빌드 환경

- JDK 17 이상(검증에는 JDK 21 사용), Android SDK Platform 35와 Build Tools
- 컴파일·대상 SDK 35, 최소 SDK 29(Android 10)
- 도구 버전은 [버전 카탈로그](../gradle/libs.versions.toml)와 [wrapper 설정](../gradle/wrapper/gradle-wrapper.properties)에 고정돼 있습니다.

프로젝트 루트의 `local.properties`에 SDK 경로를 지정합니다. Android Studio가 만들어 주기도 합니다. 이 파일은 Git에서 제외합니다.

```properties
sdk.dir=C:/Android/Sdk
```

```shell
./gradlew testDebugUnitTest assembleDebug lintDebug
```

| 결과 | 경로 |
| --- | --- |
| APK | `app/build/outputs/apk/debug/app-debug.apk` |
| 테스트 보고서 | `app/build/reports/tests/testDebugUnitTest/index.html` |
| Lint 보고서 | `app/build/reports/lint-results-debug.html` |

디버그 빌드는 준비 화면에서 비행기 모드 단계를 건너뜁니다. 비행기 모드를 켜면 무선 ADB가 끊기기 때문입니다. 측정 화면에는 "비행기 모드가 꺼져 있습니다" 경고가 그대로 뜨며, 디버그 빌드는 실제 시험 감독에 쓰면 안 됩니다. 배포판은 비행기 모드를 켜야 측정을 시작할 수 있습니다.

직접 빌드한 APK는 배포판과 서명이 달라 배포판 위에 덮어 깔리지 않습니다. 정식 서명 빌드는 [RELEASE.ko.md](RELEASE.ko.md)를 참고하세요.

## 구성

| 구성 | 역할 |
| --- | --- |
| `MainActivity` | 화면 전환(감독관 화면·위치 찾기·안내), 권한 요청, 화면 켜 두기 |
| `AppViewModel` | 측정·감시 상태, 빈 교실 기록 저장, 화면 밖 일시 중지와 재개, 비행기 모드·무음 확인 |
| `scanner/BleScanner` | BLE 알림 신호 연속 수신. 실제 패킷 시각을 검사하고, 시스템이 탐색을 낮추기 전에 다시 겁니다. |
| `scanner/WifiScanner` | 30초 간격 탐색 요청, 완료 알림에서 새 결과만 받기 |
| `core/DeviceTracker`, `ObservationPeriods` | 무선 종류+주소별 누적 관측, 수신 구간, 최근/대기/기록 상태 |
| `core/SignalGroups` | 주소 대신 이름·종류로 묶기, 빈 교실 때보다 늘어난 묶음 판정 |
| `core/SignalSnapshot`, `SnapshotStore` | 빈 교실 기록과 그 JSON 저장 |
| `core/SignalHints`, `SignalDetails` | 알림 신호에서 종류 단서와 부가 정보 읽기 |
| `core/DeviceIdentity`, `AudioNameCatalog`, `WearableNameCatalog` | 회사 코드·주소 등록부·이름 규칙으로 제조사와 제품군 후보 찾기 |
| `core/RssiFilter`, `DistanceEstimate` | 수신 세기 평활, 가정에 기반한 거리 어림 |
| `core/Geiger`, `SilentMode` | 위치 찾기 진동, 측정·감시 중 방해 금지 |
| `sim/RadioSimulator` | 가상 전파. 화면에서는 켤 수 없고 자동 테스트만 씁니다. |
| `ui/GuardScreen` | 감독관 화면, 위치 찾기, 안내 |

## 수신과 저장의 규칙

- `radioKey`는 무선 종류와 대문자로 맞춘 주소의 조합입니다. BLE와 와이파이는 주소 문자열이 같아도 별개입니다.
- 콜백에서 바로 추적기에 기록하고 화면용 집계는 100ms마다 갱신합니다. 화면 갱신 속도가 탐지율을 뜻하지는 않습니다.
- BLE는 탐색 시작 전·미래·0 이하의 시각을 가진 패킷을 버립니다. 늦게 도착한 패킷은 실제 관측 시각을 유지합니다.
- 필터 없는 BLE 탐색은 시작 몇 분 뒤 시스템이 결과를 거의 주지 않는 모드로 바꿉니다. 4분마다, 또는 받던 신호가 30초 넘게 끊기면 다시 겁니다(30초마다 확인합니다). 시작 뒤 한 번도 못 받았다면 4분 주기만 적용됩니다(`BleScanner.checkScan`의 `silentMs != null`).
- 와이파이는 완료 알림이 온 탐색에서 이번 측정 시작 이후의 결과만 받습니다. 같은 주소의 같거나 이전 시각은 다시 세지 않습니다.
- BLE 15초, 와이파이 90초를 넘는 공백을 수신 구간의 경계로 봅니다.
- 화면이 꺼지거나 앱이 뒤로 가면(`ON_STOP`) 수신을 멈추고, 돌아오면(`ON_START`) 같은 감시를 이어 갑니다. 비어 있던 횟수와 시간은 `ScanSession`에 남습니다. 백그라운드 수신 서비스는 없습니다.
- 묶음 판정 규칙은 [README의 설명](../README.md#같은-기기를-새-전파로-세지-않는-방법)과 [설계 문서](design/same-device-grouping.md)를 참고하세요.

빈 교실 기록은 마지막 하나만 앱 전용 저장소에 JSON으로 둡니다. 임시 파일에 쓰고 동기화한 뒤 교체합니다. 파일 형식 번호는 2이며, 이전 배포판이 쓴 파일(지금은 쓰지 않는 항목이 더 들어 있음)도 읽습니다. 백업과 기기 간 이전 대상에서 제외합니다.

## 거리 모델

평활한 수신 세기와 아래 모델을 씁니다.

```text
d = 10 ^ ((A - RSSI) / (10 × n))
n = 2.7
A = 1m 기준 수신 세기. 현재는 항상 −59 dBm 가정값
```

BLE 알림 신호의 송신 출력 값은 1m 기준 수신 세기가 아니므로 쓰지 않습니다. 그래서 지금 실제 수신 경로는 모두 가정값으로 계산합니다. 결과는 어림이며 거리 정확도를 보장하지 않습니다. 근거는 [Bluetooth SIG의 RSSI 설명](https://www.bluetooth.com/blog/proximity-and-rssi/)을 참고하세요.

## 검증 범위

자동 테스트는 묶음 판정, 추적, 저장, 식별, 스캐너 콜백, 감독관 화면 흐름(가상 전파)을 다룹니다. 다음은 자동 테스트가 대신하지 못합니다.

- 실제 무선 환경에서의 수신률과 누락률
- 기종·Android 버전별 권한 화면과 시스템의 탐색 제한
- 발열과 배터리 소모

실기기 결과를 알려 줄 때는 기종, Android 버전, 앱 버전, 켠 기기와 그 상태, 잡힌 시각과 안 잡힌 시각을 구분해 적어 주세요. 개인 이름이나 실제 현장을 알 수 있는 자료는 그대로 올리지 마세요.

## 저장소에 올리지 않는 것

`local.properties`, 서명 키, 빌드 출력, 실제 현장에서 받은 기록. 사용자에게 보이는 설명에는 근거 없는 탐지율, AI 판정, 정확한 위치 보장을 넣지 않습니다.
