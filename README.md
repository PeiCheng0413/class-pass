# class-pass

學生玩網頁小遊戲過關後，畫面上會出現一張 QR code；老師用 Android app 連續掃描收成績，最後匯出 CSV。

- 協定：[docs/PROTOCOL.md](docs/PROTOCOL.md)（一個學生、一個遊戲一張 QR，裝所有已過關關卡；只用班級＋座號識別）
- 需求與已定案事項：[docs/REQUIREMENTS.md](docs/REQUIREMENTS.md)
- 網頁端小檔：`web/classpass.js`；測試頁 `web/demo.html`
- 老師端 app：`android/`

## 網頁端

```html
<script src="classpass.js"></script>   <!-- vendor/qrcode.js 要放在同一層的 vendor/ 底下 -->
<script>
  // 遊戲最後一次交出所有關卡，一堂課每位學生只掃一次（每關只留最好的成績）
  ClassPass.show({ game: 'robot-farm-practice', levels: [
    { level: 'check-dry', title: '澆水', score: 3, max: 3 },
    { level: 'harvest', title: '收成', score: 4, max: 5 },
  ]});
  // 也可以每過一關呼叫一次，各關會累積在同一張 QR code 裡
  ClassPass.show({ game: 'robot-farm-practice', level: 'check-dry', title: '澆水', score: 3, max: 3 });
  // 隨時再叫出 QR code
  ClassPass.showPassed('robot-farm-practice');
</script>
```

第一次顯示 QR code 時會請學生輸入班級、座號；同一個網域的小遊戲共用。
Web Crypto 只能在 https 或 localhost 下使用。

本機測試：

```bash
python3 -m http.server 8765 --directory web
```

開 http://localhost:8765/demo.html，按「簽章自我檢查」確認簽章與測試向量一致。

## Android app

需要 JDK 17 與 Android SDK（`android/local.properties` 的 `sdk.dir`，這個檔不進 git）。Gradle 用 wrapper 固定版本。

```bash
cd android
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew testDebugUnitTest assembleDebug
```

產出的 APK 在 `android/app/build/outputs/apk/debug/app-debug.apk`。手機開 USB 偵錯後安裝：

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

- 掃描：相機一直開著，掃到就自動存。同一張 QR 還在鏡頭前（或離開不到 5 秒）不會重複處理。
- 紀錄：同一個學生同一關只留最好的成績；可依遊戲篩選、匯出 CSV（UTF-8 BOM）、清除全部。
- 完全離線：ML Kit 用模型內建在 app 裡的版本。
