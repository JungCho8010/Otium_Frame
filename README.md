# Otium Frame

## 1. 앱 이름 및 한 줄 소개

**Otium Frame** — Pleos Connect 차량에서 **기어가 P일 때 내 사진·영상으로 화면을 채우는** 개인화 디스플레이 앱.

> *Otium*(라틴어): 여유 속에서 몸과 마음을 정화하는 '풍요로운 휴식'. Otium Frame = 차 안의 프레임 속 휴식.

- 기어 **P**(정차)에서만 사진 크로스페이드 슬라이드쇼·영상(무음)·GIF 를 재생하고, P 를 벗어나면 즉시 닫힙니다.
- 사진·영상은 폰에서 **QR 로 업로드**합니다(차량 내부 Wi-Fi, 인터넷 불필요). 폰 페이지에서 영상 구간·사진 여러 장을 GIF 로 만들 수도 있습니다.
- 사진 대표 색을 뽑아 **앰비언트 라이트**에 적용합니다(미지원 차량은 화면 테두리 색으로 대체).
- 안전 설계: 주행 중 표시를 선언하지 않아 주행 중에는 시스템이 화면을 가리고, 신호를 모르면 재생하지 않습니다.

## 2. 실행 방법

1. Pleos Connect 에뮬레이터(또는 Pleos 차량)를 부팅합니다.
2. APK 를 설치합니다.
   ```bash
   adb install -r apk/정재철_OtiumFrame_release.apk
   ```
3. 런처에서 **Otium Frame** 을 실행합니다.
4. Virtual Console 에서 기어를 **P** 로 두면 샘플 사진·영상이 재생됩니다. **D/R/N** 으로 바꾸면 화면이 닫힙니다.
   - 명령으로 조작하려면: `adb forward tcp:18088 tcp:8088` 후 `curl -X PUT "http://localhost:18088/vehicle/gear?gear=Park"` (Park|Reverse|Neutral|Drive)
5. 내 사진 올리기: 설정 → **폰에서 가져오기 (QR)** → 폰을 같은 Wi-Fi 에 연결하고 QR 을 찍어 업로드 → 설정의 **사진·영상 선택**에서 체크.
6. 화면을 닫으려면 빈 화면을 **더블 탭**합니다.

소스에서 직접 빌드하려면 [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) 를 참고하세요.

## 3. 테스트 환경 및 필요한 설정

| 항목 | 내용 |
|---|---|
| 차량 플랫폼 | Pleos Connect v2.1.0 에뮬레이터 (Android 14 / API 34, Android Automotive OS) |
| Pleos SDK | Vehicle SDK 2.3.2 (Gear · EvBattery · Light 도메인) |
| 사용 권한 | `pleos.car.permission.CAR_DRIVE`(기어), `CAR_ENERGY`(충전·배터리), `CAR_LIGHTS`, `CONTROL_CAR_LIGHTS`(앰비언트) — 설치 시 자동 부여, 별도 설정 없음 |
| 네트워크 | 불필요. 폰 업로드만 차량 내부 Wi-Fi(핫스팟) 사용, 포트 8787 |
| 개발 환경 | Windows 10, Android Studio, JDK 17, AGP 8.8.2, Kotlin 2.0.21, compileSdk 35 / minSdk 28 |
| 에뮬레이터 설정 | [docs/PLEOS_SETUP.md](docs/PLEOS_SETUP.md) (계정·에뮬레이터 설치·Virtual Console) |

## 4. APK 파일 위치

- **[`apk/정재철_OtiumFrame_release.apk`](apk/)** — `pleos` flavor, release 서명본 (서명 열쇠는 저장소에 포함하지 않음)
