# Otium Frame 개발 문서

심사용 요약은 [README.md](../README.md) 를 보세요. 이 문서는 동작 상세·빌드·시연·코드 구조를 담습니다.

## 동작 요약

| 차량 상태 | 화면 |
|---|---|
| 기어 **P** (정차, 주행 제한 없음) | 사진 크로스페이드 슬라이드쇼, 영상(무음) 재생, 시계·충전 잔량 |
| P가 아닌 모든 상태(R·N·D, 이동 중, 신호 모름) | 아무것도 재생하지 않음. 재생 중이었다면 화면이 닫히고 원래 화면으로 돌아감 |

- **화면 자동 전환 (오른쪽 아래 슬라이드 토글, 기본값 꺼짐)**: 켜면 백그라운드 서비스가 **P기어 감지만** 합니다.
  사진·영상은 백그라운드에서 돌지 않고, P로 바뀌는 순간에만 프레임 화면을 엽니다. 끄면 서비스가 즉시 멈춥니다.
  (Pleos 에뮬레이터에서는 OS 가 백그라운드 화면 실행을 막습니다)
- **더블 탭으로 닫기**: 빈 화면을 두 번 탭하면 닫히고 원래 화면으로 돌아갑니다. 다음에 P로 들어올 때까지 다시 열리지 않습니다.
- **색 연출**: 사진(영상은 대표 프레임)에서 대표 색을 뽑아 앰비언트 라이트에 RGB 로 적용합니다(에뮬레이터에서 동작 확인).
  차량이 앰비언트 제어를 허용하지 않으면 화면 가장자리 **테두리 색**으로 대신 표현합니다.
- **사진은 폰에서 QR 로 올리고, 설정의 '사진·영상 선택'에서 체크한 것만 재생**합니다(아래 참고). 움직이는 GIF 도 재생됩니다.
- 차량에는 뒤로 가기 버튼이 없으므로 설정·선택·QR 화면마다 '돌아가기/완료' 버튼이 있습니다.
- **안전 설계(fail-safe)**: 신호를 모르면 재생하지 않습니다. 규칙은 `core/FrameModePolicy.kt` 한 곳에 있고 단위 테스트가 있습니다.
  주행 중 표시 허용(distractionOptimized)을 선언하지 않아, 주행 중에는 시스템이 "주행 중에는 사용할 수 없습니다" 화면으로 가립니다.
- **오프라인 동작**: 미디어는 기기 로컬, 설정은 SharedPreferences. 인터넷이 필요 없습니다(폰 업로드는 차량 내부 Wi-Fi 만 사용).

### 백그라운드 서비스 실행 정책 (`ParkWatchService`)

| 구분 | 내용 |
|---|---|
| 실행 | '화면 자동 전환'이 켜져 있을 때만: 토글을 켤 때, 차량 부팅 완료 시, 앱 화면이 열릴 때(종료됐으면 복구) |
| 중단 | 토글을 끌 때 즉시. 시작 시점에 토글이 꺼져 있으면 바로 종료 |
| 하는 일 | 기어·주행 제한 신호 콜백만 구독 (폴링·타이머·네트워크·미디어 없음) |
| 화면 전환 | P단으로 바뀌는 순간에만. 이미 화면이 떠 있으면 아무것도 하지 않음 |

## 폰에서 사진 가져오기 (QR)

설정 → **폰에서 가져오기 (QR)**. 앱이 차량 안에서 작은 업로드 서버(`media/UploadServer.kt`, 포트 8787)를 띄우고 그 주소를 QR 로 보여줍니다.
폰을 차량과 같은 Wi-Fi(차량 핫스팟 또는 폰 핫스팟)에 연결한 뒤 QR 을 찍으면 업로드 페이지가 열리고, 고른 사진·영상이 바로 앱 저장소에 저장돼 재생 목록에 들어갑니다.

- 올린 파일은 `files/uploads/` 에 쌓이고 바로 체크(재생) 상태가 됩니다. 설정 → **사진·영상 선택** 에서 체크를 바꾸거나 선택 삭제할 수 있고, 체크한 것이 없으면 샘플이 재생됩니다.
- 업로드 페이지(`app/src/main/assets/upload.html`)에서 **GIF 만들기**도 됩니다: 영상을 고르고 시작·끝(최대 8초)을 슬라이더로 잡아 GIF 로, 또는 사진 여러 장을 골라 한 장당 초를 정해 GIF 로. 인코딩(미디언컷 256색 + 디더링 + LZW)은 폰 브라우저가 하고, 만든 GIF 를 미리 본 뒤 '차량에 올리기'를 누르면 차량에 저장됩니다(가로 640px, 10fps). 차량 앱은 받기만 하므로 차량 CPU 부담이 없습니다.
- QR 화면이 떠 있는 동안만 서버가 돕니다. 주소에는 매번 바뀌는 토큰이 들어가고, 사진(JPG·PNG·WEBP·BMP·GIF·HEIC)과 영상(MP4·MOV 등 video/*)만, 파일당 200MB 까지 받습니다. 아이폰 HEVC .mov 는 변환 없이 그대로 올리며 에뮬레이터에서 재생을 확인했습니다.
- 외부 서버·계정 없음. 사진이 차 밖으로 나가지 않습니다. 차량 내부 통신이라 HTTPS 는 아닙니다.
- QR 인코더(`core/QrCode.kt`)는 외부 라이브러리 없이 작성했고, 기준 구현(segno)과 모듈 단위로 비교하는 테스트가 있습니다.

**에뮬레이터에서 실제 폰으로 시연하기** (디버그 빌드 전용): 에뮬레이터는 PC 뒤에 숨어 있어 폰이 직접 못 닿습니다.
QR 화면을 연 상태에서 아래를 실행하면 QR 이 PC 주소로 바뀌고, PC 가 중계합니다.
```bash
bash tools/phone-demo.sh          # adb forward + tools/relay.py + 주소 덮어쓰기 (PC 와 폰이 같은 Wi-Fi)
bash tools/phone-demo.sh --stop   # 덮어쓰기 해제
```
폰에서 연결이 안 되면 Windows 방화벽에서 python 의 인바운드 18787 포트를 허용하세요.

## 빌드 종류 (product flavor)

| flavor | 차량 신호 | 용도 |
|---|---|---|
| `mock` (기본) | 화면 속 **DEMO 패널** + adb 브로드캐스트 | SDK 없이 바로 빌드·시연 |
| `pleos` | Pleos Vehicle SDK 2.3.2 + AAOS 주행 제한 | 실차/Pleos 에뮬레이터 (2026-11-01 에뮬레이터 검증 완료) |

## 빌드 방법

- JDK **17** 필요. Android Studio 번들 JDK 가 25 라면 Kotlin 플러그인이 거부하므로 JDK 17 을 따로 두고 `JAVA_HOME` 으로 지정합니다.
- SDK Platform 35, Build-Tools 35 는 첫 빌드 때 Gradle 이 자동 설치합니다.
- `pleos` flavor 는 Pleos SDK 가 AndroidX 를 끌어와 `android.useAndroidX=true` 가 필요합니다(앱 코드는 AndroidX 를 쓰지 않음).

```bash
./gradlew assembleMockDebug testMockDebugUnitTest      # → app/build/outputs/apk/mock/debug/app-mock-debug.apk
./gradlew assemblePleosDebug                           # → app/build/outputs/apk/pleos/debug/app-pleos-debug.apk
./gradlew assemblePleosRelease                         # → app/build/outputs/apk/pleos/release/ (루트에 keystore.properties 가 있으면 서명)
```

Google Maven 이 막힌 환경에서는 `tools/build-offline.sh` 로 mock 디버그 APK 를 만들 수 있습니다(aapt2 · kotlinc · dx · apksigner).

## 시연 방법 (mock 빌드)

```bash
adb install -r OtiumFrame-mock-debug.apk
adb shell am broadcast -a com.otiumframe.MOCK_SIGNAL --es gear D --ef speed 30          # 주행 시작
adb shell am broadcast -a com.otiumframe.MOCK_SIGNAL --es gear P --ef speed 0           # 주차
adb shell am broadcast -a com.otiumframe.MOCK_SIGNAL --ez charging true --ei battery 72 # 충전 시작
```
왼쪽 아래 **DEMO** 버튼으로도 바꿀 수 있습니다. 사진을 고르지 않으면 앱에 포함된 샘플(사진 3장, 영상 1개)이 재생됩니다.

## Pleos 에뮬레이터에서 확인하기 (`pleos` flavor)

가입·프로젝트 생성·에뮬레이터 설치·체크리스트는 [docs/PLEOS_SETUP.md](docs/PLEOS_SETUP.md) 에 있습니다.
Virtual Console 은 에뮬레이터 안의 8088 포트 HTTP 서버라 명령으로도 조작할 수 있습니다(브라우저 세션 하트비트가 있어야 활성화).

```bash
adb forward tcp:18088 tcp:8088
while true; do curl -s -X POST "http://localhost:18088/browser/session?client=cli" >/dev/null; sleep 1; done &   # 하트비트
curl -X PUT "http://localhost:18088/vehicle/gear?gear=Drive"                      # Park|Reverse|Neutral|Drive
curl -X PUT "http://localhost:18088/vehicle/ev-battery?field=charge_switch&value=true"
curl -X PUT "http://localhost:18088/vehicle/ev-battery?field=soc&value=72"
```

| 신호 | 출처 | 권한 |
|---|---|---|
| 주행 제한(영상 정지) | AAOS `CarUxRestrictionsManager` | 없음 |
| 기어 P/R/N/D | Pleos `Gear.registerGearSelection` | `pleos.car.permission.CAR_DRIVE` |
| 충전 중, 배터리 % | Pleos `EvBattery` | `CAR_ENERGY` |
| 앰비언트 색 | Pleos `Light.setAmbientLightBrightnessColor` (RGB, 지원 영역 조회 후) | `CAR_LIGHTS`, `CONTROL_CAR_LIGHTS` |

에뮬레이터 운전자 계정(user 10)의 갤러리에 테스트 사진을 넣으려면 `adb push` 가 아니라 MediaStore 를 써야 합니다:
```bash
adb push photo.jpg /data/local/tmp/
adb shell content insert --user 10 --uri content://media/external/images/media --bind _display_name:s:photo.jpg --bind mime_type:s:image/jpeg
adb shell content query --user 10 --uri content://media/external/images/media --projection _id:_display_name   # ID 확인
adb shell "content write --user 10 --uri content://media/external/images/media/<ID> < /data/local/tmp/photo.jpg"
```

## 코드 구조

```
app/src/main/java/com/otiumframe/
  core/FrameModePolicy.kt     차량 신호 → REST/DRIVING, 자동 실행 판단 (순수 Kotlin, 테스트 있음)
  core/ColorExtractor.kt      사진 대표 색 추출 (라이브러리 없음, 테스트 있음)
  core/QrCode.kt              QR 인코더 (바이트 모드, L, 버전 1~4, 테스트 있음)
  vehicle/VehicleSignalSource 차량 신호 인터페이스 (mock / pleos 구현)
  media/MediaRepository.kt    재생 목록 (files/uploads 의 파일 + 체크 상태, 없으면 샘플)
  media/UploadServer.kt       폰 업로드용 HTTP 서버 (ServerSocket, 라이브러리 없음)
  assets/upload.html          폰 업로드 페이지 (올리기 + 영상 구간/사진 여러 장 → GIF 만들기, JS GIF 인코더 내장)
  ui/FrameActivity.kt         메인 프레임 화면 (슬라이드쇼·GIF·영상, 주행 전환, 색 연출, DEMO 패널)
  ui/SettingsActivity.kt      사진·영상 선택 / 폰에서 가져오기 / 모두 삭제, 간격, 옵션, 돌아가기
  ui/SelectActivity.kt        올린 사진·영상 격자에서 체크, 선택 삭제
  ui/UploadActivity.kt        QR 화면 (서버 실행·중지, 받은 파일 수)
  service/ParkWatchService.kt P기어 감지 → 화면 전환 (포그라운드 서비스)
app/src/mock/   Mock 차량 (DEMO 패널, adb 브로드캐스트)
app/src/pleos/  Pleos Vehicle SDK + AAOS 주행 제한 어댑터, 권한
tools/          오프라인 빌드, 폰 시연 중계 스크립트
```

외부 라이브러리 의존성이 없습니다(앱 코드 기준). 어떤 빌드 환경에서도 재현됩니다.
