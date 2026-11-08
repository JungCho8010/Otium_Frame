# Pleos SDK 연결 가이드 (Otium Frame)

*2026-10-23 기준, Pleos Playground 공식 문서를 읽고 정리했습니다.*

## 먼저 알아둘 것 (요약)

- **차량 상태 읽기(Vehicle State)는 승인 신청 없이 바로 쓸 수 있습니다.** 프로젝트를 만들면 상태가 'Ready to Use' 입니다.
- **차량 제어(Vehicle Control)는 아직 'Not Available Yet(Coming Soon)'** 이라 신청 버튼이 없습니다.
  그래서 앰비언트 라이트 색 바꾸기는 지금은 실패할 가능성이 높고, 앱은 자동으로 **화면 테두리 색**으로 대신 보여줍니다.
- Pleos Maven 저장소는 **로그인·인증 없이** 열려 있어, PC 에서 바로 SDK 를 내려받아 빌드할 수 있습니다.
- Pleos Vehicle SDK 에는 **기어(P/R/N/D)·차속·주차 브레이크 API 가 없습니다.**
  그래서 Otium Frame 은 이 부분을 **AAOS 표준 Car API** 로 받습니다(아래 4번 참고).

## 1. 계정 만들기 (직접 해주세요)

1. https://pleos.ai/playground 접속 → 오른쪽 위 **Login**
2. 로그인 화면 아래 **Create a Pleos account** → 약관 동의, 정보 입력
3. 가입 후 Playground 에 로그인

## 2. 프로젝트 만들기 (직접 해주세요)

1. 상단 메뉴 **Workspace → My Project → New Project**
2. 입력값 예시
   - Project name: `Otium Frame`
   - Developer name: 본인 이름 또는 팀 이름 (회사명 칸이지만 개인 참가자는 이름을 넣으면 됩니다)
   - Category: `Lifestyle` 권장 (영상이 핵심이면 Entertainment 인데, 미디어 앱 규정이 더 엄격해집니다)
   - About your application: "주차·충전 중 차량 화면을 내 사진·영상으로 채우고, 주행이 시작되면 정지 이미지로 바뀌는 개인화 디스플레이 앱"
3. **Create**
4. Project Information 화면의 **Project ID(Key)** 를 메모해 두세요. 나중에 앱 마켓 심사 요청 때 씁니다.
   (Client ID/Secret 은 TTS·STT 같은 서버 연동 SDK 에만 필요합니다. CRN 은 에뮬레이터 2.1.0 이상에서는 필요 없습니다.)

## 3. SDK 상태 확인 (직접 해주세요)

Project Information 왼쪽 **SDK** 메뉴에서 SDK 별 상태를 확인합니다.

| SDK | 예상 상태 | 할 일 |
|---|---|---|
| Vehicle State | Ready to Use | 없음. 바로 사용 |
| Vehicle Control | Not Available Yet | 버튼이 생기면 Request (앰비언트 색 제어용) |
| FusedLocation, TTS, STT | Not Available Yet | 지금은 쓰지 않음 |
| NaviHelper | Ready to Use | 지금은 쓰지 않음 |

'Request Required' 로 바뀐 SDK 는 **Request** → 신청서 제출 → 'Under Review' → 승인되면 'Ready to Use', 거절되면 'Rejected' 입니다.
문서에 심사 기간은 나와 있지 않습니다.

## 4. 기어·주행 신호에 대해 (문의 권장)

Pleos 문서(API Compatibility & Availability)는 *"Pleos Connect SDK 가 제공하지 않는 속성이 필요하면, 직접 구현하기 전에 기술지원에 검토를 요청하라"* 고 안내합니다.
Otium Frame 은 다음을 AAOS 표준 API 로 받으므로, 공모전 기획서 제출 전에 한 번 문의해 두는 것을 권합니다.

- **주행 제한(CarUxRestrictionsManager)**: Pleos '주행 제한 개발 가이드'가 직접 안내하는 방식이라 문제 없습니다. 영상 정지(NO_VIDEO)를 이것으로 판단합니다.
- **기어(GEAR_SELECTION), 주차 브레이크(PARKING_BRAKE_ON)**: SDK 에 없는 AAOS 속성 → 검토 요청 대상입니다.
  문의 예시: "주차(P단) 상태에서만 사진·영상 화면을 자동으로 띄우기 위해 AAOS GEAR_SELECTION, PARKING_BRAKE_ON 속성(CAR_POWERTRAIN 권한)을 읽고자 합니다. 사용 가능 여부를 확인 부탁드립니다."
- 기어를 쓸 수 없게 되더라도 앱은 안전하게 동작합니다(기어를 모르면 항상 정지 이미지). 이 경우 주행 제한 해제 + 충전 상태로 자동 실행 조건을 바꾸면 됩니다.

## 5. 에뮬레이터 설치 (직접 해주세요, PC)

필요: 최신 Android Studio, JDK 17, Android SDK Platform 34·35, RAM 여유

1. Android Studio → **Tools → SDK Manager → SDK Update Sites → +**
   - Name: `Pleos Connect System Image`
   - URL (Windows/Linux): `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
   - URL (Mac, Apple 칩): `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
2. **SDK Platforms** 탭 → 아래 **Show Package Details** 체크 → API 34 의 Pleos Connect system image 설치 → Android Studio 재시작
3. **Tools → Device Manager → Create device** → Form Factor **Automotive** → **Pleos Connect v2.1.0**
4. Advanced Settings: Default boot **Cold**, RAM **4 GB** → API 34 / Pleos Connect v2.1.0 APIs 선택 → Finish
5. 예전 Pleos 에뮬레이터가 있으면 먼저 지우세요.

## 6. Otium Frame 을 Pleos 빌드로 실행

1. `otium-frame-source.zip` 압축 해제 → Android Studio 로 폴더 열기 → Gradle Sync
   (SDK `ai.pleos.playground:Vehicle:2.3.2` 가 Pleos Maven 에서 자동으로 내려받아집니다.)
2. Pleos 에뮬레이터를 먼저 켜고(Cold boot), 부팅이 끝날 때까지 기다립니다.
3. **Build Variants** 에서 `pleosDebug` 선택 → 대상 기기를 Pleos 에뮬레이터로 → **Run**
4. 처음 실행하면 알림 권한을 물어봅니다. 허용해 주세요(자동 전환 서비스 알림용).
5. 에뮬레이터 **Virtual Console → Emulation Controls** 에서 아래 순서대로 확인합니다.

| # | 하는 일 | 기대 결과 |
|---|---|---|
| 1 | Motion › Gear Selection = **P** 인 상태로 앱 실행 | 샘플 사진·영상 슬라이드쇼, 위쪽에 "화면을 두 번 탭하면 닫혀요" 안내 |
| 2 | 오른쪽 아래 **화면 자동 전환** 토글 켜기 | 알림 영역에 "P기어 감지" 알림이 생김 (서비스 실행) |
| 3 | Gear Selection = **D** | 영상·슬라이드쇼가 멈추고 화면이 닫혀 원래 화면으로 돌아감 |
| 4 | Gear Selection = **P** | 몇 초 안에 Otium Frame 화면이 자동으로 열림 |
| 5 | 빈 화면을 **두 번 탭** | 화면이 닫힘. P 상태가 계속돼도 다시 열리지 않음 |
| 6 | D → P 다시 | 자동으로 다시 열림 |
| 7 | 토글 끄기 → D → P | 알림이 사라지고, P가 돼도 열리지 않음 (서비스 중단 확인) |
| 8 | 기어 **N** 또는 **R** 로 앱 직접 실행 | 재생 없이 "기어를 P에 두면 사진·영상이 재생돼요" 안내만 표시 |
| 9 | Energy › Battery SOC, Charge Switch = On | 시계 아래 "충전 중 · NN%" |
| 10 | Convenience › Ambient Color 확인 | 사진 색에 맞춰 바뀌거나, 제어가 막혀 있으면 화면 테두리 색 표시 |

6. 문제가 생기면 Android Studio **Logcat** 에서 `Otium` 으로 필터링한 로그를 보내주세요.
   `OtiumParkWatch`(서비스), `OtiumAaosSignals`(기어·주행 제한), `OtiumPleosVehicle`(배터리·앰비언트) 태그로 남습니다.

> 4번에서 화면이 안 열리면: Android 10 이상은 백그라운드에서 화면을 여는 것을 제한합니다.
> Logcat 에 `자동 전환 실패` 또는 `Background activity start` 경고가 보이면 알려주세요. Pleos 측 정책 확인이나 다른 방식(알림 탭 등)을 검토하겠습니다.

## 7. SDK 접근 후 해볼 수 있는 개선

Pleos 문서의 메서드 상세 페이지(매개변수 형태)가 로그인 없이는 비어 있어, Pleos SDK 호출은 **메서드 이름으로 찾아 부르는 방식**(공식 샘플 앱과 같은 kotlin-reflect 방식)으로 만들었습니다.
Android Studio 에서 SDK 가 내려받아지면 자동완성으로 정확한 시그니처가 보이므로, 원하면 아래 3곳을 타입이 있는 코드로 바꿀 수 있습니다.

- `EvBattery.registerEvBatteryStateOfCharge` (배터리 %)
- `EvBattery.getEvChargeSwitchState` (충전 중 여부)
- `Light.setAmbientLightBrightnessColor` (앰비언트 색)

## 참고한 공식 문서

- 프로젝트 생성·SDK 신청: https://document.pleos.ai/en/docs/pleos-only/create-my-project
- 시작하기: https://document.pleos.ai/en/docs/pleos-only/getting-started
- 에뮬레이터 설치: https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
- Virtual Console: https://document.pleos.ai/en/docs/pleos-only/app-test-guide/virtual-console-guide
- Vehicle SDK 의존성: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup
- Vehicle SDK 권한: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission
- API 호환성(미제공 속성 검토 요청): https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability
- 주행 제한 개발 가이드: https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide
- 공식 샘플 앱: https://github.com/Pleos-Playground/pleos-sample-vehicle-app
