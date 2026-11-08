#!/bin/bash
# 에뮬레이터에서 '폰에서 가져오기(QR)' 를 실제 폰으로 시연하기 위한 준비.
#   폰 → PC(LAN IP):18787 → relay.py → adb forward → 에뮬레이터 8787(앱 업로드 서버)
# 사용법: bash tools/phone-demo.sh            (PC 와 폰이 같은 Wi-Fi 에 있어야 함)
#         bash tools/phone-demo.sh --stop     (덮어쓰기 해제)
set -e
ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe}"
PORT=18787
if [ "$1" = "--stop" ]; then
  "$ADB" -e shell am broadcast -a com.otiumframe.UPLOAD_HOST --es host "" >/dev/null
  echo "덮어쓰기 해제됨 (QR 이 에뮬레이터 주소로 돌아감)"; exit 0
fi
PCIP=$(ipconfig | grep -o -E "IPv4[^:]*: *[0-9.]+" | grep -o -E "[0-9.]+$" | grep -v "^127\." | head -1)
[ -n "$PCIP" ] || { echo "PC 의 IPv4 주소를 찾지 못함"; exit 1; }
"$ADB" -e forward tcp:$PORT tcp:8787 >/dev/null
if ! netstat -ano | grep -q ":$PORT .*LISTENING.*" ; then :; fi
# relay: 이미 떠 있으면 bind 실패로 그냥 종료됨
(nohup python -I "$(dirname "$0")/relay.py" $PORT $PORT >/dev/null 2>&1 &)
sleep 1
# QR 화면이 열려 있어야 브로드캐스트를 받는다(디버그 빌드 전용)
"$ADB" -e shell am broadcast -a com.otiumframe.UPLOAD_HOST --es host "$PCIP:$PORT" >/dev/null
echo "QR 주소 → http://$PCIP:$PORT/<token>  (폰을 PC 와 같은 Wi-Fi 에 연결하고 QR 을 찍으세요)"
echo "폰에서 연결이 안 되면 Windows 방화벽에서 python 의 인바운드 $PORT 포트를 허용하세요."
