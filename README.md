<p align="center">
  <img src="docs/images/icon.png" width="96" height="96" alt="KoriyoSCAN 아이콘">
</p>

# KoriyoSCAN

> **이 저장소는 [reform-dev-team/exam-rf-guard](https://github.com/reform-dev-team/exam-rf-guard)의 포크입니다.**
> 포크한 사람: 배성훈 · 대구에 사는 고등학교 2학년 개발자. 시험 감독 앱의 탐지 규칙을 읽다가 찾은 오류를 고치며 기여하고 있습니다.

시험 감독관용 Android 앱입니다. 수험생이 들어오기 전 빈 교실의 와이파이·블루투스 전파를 기록해 두고, 시험 중에 그 기록에 없던 전파가 잡히면 화면으로 알립니다.

> **이 앱은 모든 기기를 탐지하지 못합니다.** 전파를 차단하지 않고, 부정행위 여부를 판정하지 않으며, 육안 감독을 대체하지 않습니다. 실제 시험장에서의 수신률·누락률은 검증하지 않았습니다.

| 항목 | 내용 |
| --- | --- |
| 지원 환경 | Android 10(API 29) 이상. 아이폰은 지원하지 않습니다. |
| 보는 것 | 와이파이 접속점(공유기·핫스팟)과 저전력 블루투스(BLE) 알림 신호 |
| 데이터 | 휴대폰 안에만 저장합니다. 앱에 인터넷 권한이 없습니다. |
| 측정 조건 | 비행기 모드가 켜져 있어야 시작됩니다. 측정·감시 중에는 휴대폰을 자동으로 무음(방해 금지)으로 바꿉니다. |
| 화면 | 감독관 화면, 위치 찾기, 안내 |

<p align="center">
  <img src="docs/images/screen-1-ready.png" width="250" alt="빈 교실 기록 화면">
  <img src="docs/images/screen-2-found.png" width="250" alt="시험 중 감시 화면에 새 전파 1개가 뜬 모습">
  <img src="docs/images/screen-3-find.png" width="250" alt="위치 찾기 화면">
</p>
<p align="center"><sub>자동 테스트가 가상 전파로 그린 실제 앱 화면입니다. 왼쪽부터 빈 교실 기록, 시험 중 감시, 위치 찾기.</sub></p>

## 상태

- **현장 검증 전입니다.** 자동 테스트는 통과하지만 실제 시험장에서 얼마나 잡고 얼마나 놓치는지는 측정하지 않았습니다.
- 시험 주관 기관의 지침이 감독관의 휴대폰 반입이나 사용을 제한할 수 있습니다. 쓰기 전에 해당 시험의 지침을 확인해야 합니다.
- 실기기에서 써 본 결과는 [Issues](https://github.com/reform-dev-team/exam-rf-guard/issues)로 알려 주세요. 적는 방법은 [제보](#제보)에 있습니다.

## 사용 순서

**준비**

앱을 열면 "시작 전 준비" 화면이 아래 순서대로 하나씩 안내합니다. 모두 마쳐야 측정을 시작할 수 있습니다.

1. 근처 기기·위치 권한 허용
2. 방해 금지 권한 허용
3. 비행기 모드 켜기
4. 와이파이 다시 켜기
5. 블루투스 다시 켜기
6. 위치(GPS) 켜기

충전기를 꽂아 두는 것이 좋습니다. 측정·감시 중에는 화면이 꺼지지 않습니다.

**측정과 감시**

1. **빈 교실 기록** — 수험생 입실 전 2분 동안 잡히는 전파를 "원래 있던 것"으로 저장합니다. 감독관 본인의 시계와 이어폰은 이때 켜 두면 함께 기록되어 나중에 알리지 않습니다.
2. **시험 중 감시** — 빈 교실 기록에 없던 전파가 잡히면 목록에 올립니다. 사라지면 목록에서 빠집니다.
3. **위치 찾기** — 한 전파를 골라 걸으면 수신 세기가 막대와 진동 간격으로 바뀝니다.

측정과 감시는 앱 화면이 켜져 있는 동안에만 동작합니다. 화면을 끄거나 다른 앱으로 가면 수신이 멈추고, 돌아오면 이어서 감시하며 비어 있던 횟수와 시간을 표시합니다. 측정·감시 중에는 앱이 휴대폰을 무음(방해 금지)으로 바꾸고 끝나면 되돌립니다. 위치 찾기의 진동은 그대로 울립니다.

**제대로 잡히는지 확인하기**

빈 교실 기록을 저장한 뒤 차례로 해 봅니다.

1. 무선 이어폰 케이스를 엽니다. 30초쯤 뒤 블루투스 칸에 떠야 합니다.
2. 다른 휴대폰의 핫스팟을 켭니다. 1분쯤 안에 와이파이 칸에 떠야 합니다.
3. 15분 넘게 그대로 둡니다. 개수가 늘지 않아야 합니다.

## 동작 방식

### 비행기 모드와 무음을 강제하는 이유

시험실에서 감독관의 휴대폰이 울리거나, 전화 때문에 감시가 끊기는 일을 막기 위해서입니다.

- **비행기 모드가 꺼져 있으면 측정을 시작할 수 없습니다.** 전화나 문자가 오면 감시 화면이 가려지고 수신이 멈추기 때문입니다. 앱이 비행기 모드를 직접 켤 수는 없어, 사용자가 켠 뒤 와이파이와 블루투스만 다시 켜야 합니다.
- **방해 금지 권한이 없어도 측정을 시작할 수 없습니다.** 권한이 있으면 측정·감시를 시작할 때 전화·알림·알람이 울리지 않게 휴대폰을 무음으로 바꾸고, 끝내거나 처음으로 되돌리면 원래 설정으로 돌립니다.
- 앱을 잠시 벗어난 동안에도 무음은 유지합니다. 시험이 끝난 것이 아니기 때문입니다.
- 위치 찾기 진동은 무음 중에도 울립니다.

### 같은 기기를 새 전파로 세지 않는 방법

블루투스 주소는 10~15분마다 바뀌고 공유기 한 대는 주소를 여러 개 씁니다. 주소만으로 비교하면 아무것도 켜지 않아도 "새 전파"가 계속 늘어납니다. 그래서 주소 대신 다음 기준으로 묶습니다.

- 기기가 알린 이름이 있으면 이름으로, 없으면 블루투스 회사 코드와 종류 단서로 묶습니다.
- 빈 교실 때 같은 묶음이 몇 개였는지 세어 두고, 그보다 늘어났을 때만 알립니다.
- 새 주소는 30초 넘게 이어져야 늘어난 것으로 봅니다. 주소가 바뀌는 순간 옛 주소와 겹치는 것을 거르기 위해서입니다.
- 빈 교실에 있던 공유기와 주소 다섯 자리가 같은 와이파이는 같은 공유기로 봅니다.
- 전원을 끈 아이폰과 에어태그의 '나의 찾기' 신호는 알리지 않고 개수만 따로 보여 줍니다. 꺼서 제출한 휴대폰도 이 신호를 내기 때문입니다.
- 가장 셀 때도 −85 dBm보다 약한 전파는 "멀리서 약하게 잡히는 것"으로 따로 모읍니다.

자세한 설계 배경은 [docs/design/same-device-grouping.md](docs/design/same-device-grouping.md)에 있습니다.

## 탐지 범위

앱이 보는 것은 기기가 주변에 내보내는 **알림 신호**뿐입니다. 이미 연결된 기기끼리 주고받는 통신은 보지 못합니다.

| 구분 | 예 |
| --- | --- |
| 탐지 | 자체 와이파이를 여는 카메라, 핫스팟을 켠 휴대폰, 휴대용 와이파이. 빈 교실 기록에 없던 이름만 알리며, 이름과 설정에 따라 알리지 않는 경우가 있습니다. |
| 조건부 탐지 | 무선 이어폰, 스마트워치, 스마트 안경, 블루투스를 켠 휴대폰. 연결된 뒤 알림 신호를 멈추는 제품은 연결 전에만 보입니다. |
| 탐지 불가 | 블루투스·와이파이를 끄고 LTE·5G만 쓰는 기기, 선으로 연결하거나 저장만 하는 카메라, 목걸이형·귓속 수신기, 구형(클래식) 블루투스만 쓰는 기기, 교실 밖의 기기 |

위 목록은 전부가 아닙니다. 다음 사항도 함께 알아 두어야 합니다.

- 교실 안팎을 구분하지 못합니다. 휴대폰에 닿는 전파는 벽 너머의 것도 잡힙니다.
- 와이파이 탐색은 30초 간격입니다. 켠 뒤 화면에 뜨기까지 30초~1분이 걸릴 수 있습니다.
- 가장 셀 때도 −85 dBm보다 약한 전파는 따로 모으고 새 전파로 세지 않습니다.
- **아무것도 잡히지 않아도 기기가 없다는 뜻이 아닙니다.**
- **새 전파는 확인이 필요하다는 표시일 뿐 증거가 아닙니다.** 옆 교실, 복도, 감독관 본인의 기기도 잡힙니다.
- 종류·제조사·모델 표시는 기기가 스스로 알린 값에서 읽은 후보입니다. 바꾸거나 흉내 낼 수 있습니다.
- 거리는 수신 세기로 어림한 값이며 벽과 사람에 따라 달라집니다.
- 주소나 세기만으로 소유자나 좌석을 특정할 수 없습니다.

## 권한

| 권한 | 쓰는 곳 |
| --- | --- |
| 근처 기기(블루투스 탐색·연결) | BLE 알림 신호 수신, 블루투스 상태 확인 |
| 정확한 위치, 근처 Wi-Fi 기기 | Android가 와이파이 탐색 결과에 요구합니다. 위치를 기록하지 않습니다. |
| Wi-Fi 상태 확인·변경 | 와이파이 탐색 요청 |
| 방해 금지 접근 | 측정·감시 중 전화·알림·알람 무음 |
| 진동 | 위치 찾기 |

인터넷 권한은 선언하지 않습니다. 제조사 조회는 앱에 포함된 등록부로 합니다.

## 저장하는 데이터

- 마지막 빈 교실 기록 하나를 앱 전용 저장소에 JSON으로 저장합니다. 측정 시각, 잡힌 전파의 주소·이름·수신 세기·회사 코드가 들어갑니다.
- 주변 기기의 이름에는 개인 이름이 들어 있을 수 있습니다(예: "○○의 AirPods"). 이 기록은 기기 백업 대상에서 제외하며 앱 밖으로 내보내는 기능이 없습니다.
- 앱을 지우면 함께 지워집니다.

## 빌드

JDK 17 이상과 Android SDK Platform 35가 필요합니다. 프로젝트 루트의 `local.properties`에 `sdk.dir`을 지정합니다.

```shell
./gradlew testDebugUnitTest assembleDebug lintDebug
```

APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생깁니다. 구조와 수신 규칙은 [docs/DEVELOPMENT.ko.md](docs/DEVELOPMENT.ko.md), 정식 서명 빌드는 [docs/RELEASE.ko.md](docs/RELEASE.ko.md), 버전별 변경은 [CHANGELOG.md](CHANGELOG.md)를 참고하세요.

`local.properties`, 서명 키, 실제 현장에서 받은 기록은 저장소에 올리지 않습니다.

## 제보

기능보다 관측 중심으로 적어 주세요. "이 조건에서 이 신호가 누락되거나 잘못 표시됐다"는 형태가 도움이 됩니다.

1. 앱 버전, 휴대폰 모델, Android 버전
2. 누른 순서, 기대한 결과, 실제 결과
3. 화면에 뜬 오류 문구
4. 화면을 첨부한다면 이름·주소·장소를 가려 주세요.

## 기여

코드 기여는 저장소를 포크한 뒤 `main`을 대상으로 Pull Request를 보내 주세요. PR마다 단위 테스트·빌드·Lint가 자동으로 돌고, 통과한 뒤 관리자가 검토해 머지합니다. 절차와 PR 기준은 [CONTRIBUTING.md](CONTRIBUTING.md)에 있습니다.

## 출처

제조사 정보는 앱에 포함한 공개 등록부(2026년 11월 16일 수집)를 씁니다. 실시간 조회가 아닙니다.

- [IEEE OUI 등록부](https://standards-oui.ieee.org/oui/oui.csv)
- [Bluetooth SIG 회사 식별자](https://www.bluetooth.com/specifications/assigned-numbers/)
- [앱에 포함한 등록부의 출처와 보관 필드](app/src/main/assets/REGISTRY-SOURCES.txt)
- [이름 기반 제품 후보의 제품명 출처](docs/DEVICE-NAME-SOURCES.md)
- [Android BLE 기기 탐색](https://developer.android.com/develop/connectivity/bluetooth/ble/find-ble-devices), [Android Wi-Fi 탐색과 제한](https://developer.android.com/develop/connectivity/wifi/wifi-scan)

앱 아이콘의 캐릭터는 화성시 캐릭터 코리요입니다. 원저작자는 화성시이며 [공공누리 제1유형](https://www.kogl.or.kr/info/licenseType1.do)(출처표시) 조건으로 이용합니다. 아이콘의 3D 그림은 [KoriyoZIP](https://koriyozip.com/ko/)의 3D 스타일 재구성 이미지를 가공한 것으로 화성시의 공식 3D 원본이 아니며, 화성시나 KoriyoZIP이 이 앱을 제작·보증했다는 의미도 아닙니다.

제품명과 회사명은 관측 결과를 설명하기 위해 사용합니다. 해당 제조사가 이 앱을 인증하거나 보증했다는 의미는 아닙니다.

## 라이선스

소스 코드는 [Apache License 2.0](LICENSE)으로 공개합니다. 누구나 사용·수정·재배포할 수 있으며, 저작권 표시와 라이선스 문구를 유지하고 고친 파일에는 고쳤다는 표시를 남겨야 합니다.

이 라이선스는 코드에만 적용됩니다. 앱 아이콘(코리요), 앱에 포함한 등록부, "KoriyoSCAN"이라는 이름에는 각 권리자의 조건이 적용됩니다. 자세한 내용은 [NOTICE](NOTICE)에 있습니다.

## English

KoriyoSCAN is an Android app for exam proctors. It records the Wi-Fi access points and Bluetooth Low Energy advertisements present in an empty room, then flags signals that were not in that record once the exam is under way, and helps walk toward one by signal strength.

It cannot detect every device. It only sees Wi-Fi access points and BLE advertisements, so it misses devices that use cellular data only, wired or record-only cameras, Bluetooth Classic devices, and anything that stops advertising once connected. It does not block signals and does not decide whether anyone cheated. Field accuracy has not been measured.

Records stay on the phone; the app declares no internet permission. The UI and the rest of this document are in Korean. Licensed under Apache-2.0; see [NOTICE](NOTICE) for the icon and bundled registry data.

Contributions are welcome through a fork and a pull request against `main`; CI must pass and a maintainer reviews before merging. See [CONTRIBUTING.md](CONTRIBUTING.md) (in Korean).
