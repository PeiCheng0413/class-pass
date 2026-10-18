# class-pass｜需求整理

整理日期：2026-10-18。來源：浮葉農場（robot-farm）對話中的討論，以下決定都已經跟使用者確認過。

## 目的

老師會陸續做很多網頁小遊戲給國中生玩，「玩到通關」本身就是一種評分。
需要一個**通用的 Android 掃描 app**：學生過關後，遊戲畫面顯示一個 QR code；老師用 app 連續掃描，app 驗證是真的過關紀錄後存在手機裡，最後一次匯出 CSV。
所有小遊戲共用同一套 QR 格式與簽章方式，新遊戲只要照格式產生 QR code，app 不用改就能收。

第一個接上的遊戲是浮葉農場的上課練習頁：https://peicheng0413.github.io/robot-farm/practice.html （repo `PeiCheng0413/robot-farm`）。

## 已定案

- **專案**：獨立 repo `class-pass`，放在 `~/Documents/GitHub/class-pass`（GitHub：`PeiCheng0413/class-pass`，公開；金鑰寫在 repo 裡也無妨，2026-10-18 使用者確認）。
- **防偽**：固定一種簽章方式，**全部遊戲共用一個關鍵字（金鑰）**。使用者知道金鑰會跟著網頁送到學生端，懂技術的人挖得出來，但判斷國中七年級學生目前破解不了，接受這個風險。不要再建議改成伺服器驗證或重跑遊戲程式。
- **學生身分**：學生在遊戲裡自己輸入班級、座號（2026-10-18 改為不存姓名）。網頁端小檔內建輸入框，同一台電腦（同一個網域）的小遊戲共用，只要輸入一次。
- **QR code 出現時機**：過關後直接顯示；之後也能隨時再叫出已過關各關的 QR code。
- **老師端**：原生 Android app（Kotlin）。使用者曾考慮過可安裝的網頁 app（PWA），最後還是要原生 app。
- **紀錄**：全部存在老師手機裡，最後一次匯出 CSV。
- **開發工具**：只裝命令列工具（JDK 17、Android 命令列工具、Gradle，用 Homebrew 安裝），不裝 Android Studio。用指令編譯 APK，使用者用 USB 加 adb 安裝，或把 APK 傳到手機。使用者的 Mac 已經有 adb（`/opt/homebrew/bin/adb`）。
  - 已裝好（2026-10-18）：JDK 17（`/opt/homebrew/opt/openjdk@17`，系統預設 `java` 還找不到，要設 `JAVA_HOME`）、Gradle 9.8.1、Android 命令列工具（`sdkmanager`，SDK 在 `/opt/homebrew/share/android-commandlinetools`，已有 platforms `android-37.0`、build-tools `36.0.0`／`37.0.0`）。Gradle 用 wrapper 固定版本，別直接用 brew 的 9.x。

## 要做的三樣東西

### 1. 協定文件 `docs/PROTOCOL.md`

> **已定案，以 `docs/PROTOCOL.md` 為準。** 下面是最初的草案，留著當紀錄。2026-10-18 的變更：一個遊戲一張 QR，裝所有已過關關卡（老師掃一次就收齊）；每關帶關卡名稱；不存姓名。

草案：

```
CP1|遊戲代號|關卡代號|班級|座號|姓名|分數|滿分|完成時間|簽章
```

- `CP1`：協定版本，日後要換金鑰或格式就升版號，app 可同時認得新舊版。
- 遊戲代號、關卡代號：英數字與 `-`，例如 `robot-farm-practice`、`check-dry`。
- 班級、座號、姓名：學生輸入，去掉 `|` 與換行；文字用 UTF-8（姓名是中文）。
- 分數／滿分：整數，例如星等 `3`／`3`，或分數 `85`／`100`。
- 完成時間：Unix 秒。
- 簽章：HMAC-SHA256（金鑰，簽章前面整段文字含最後一個 `|`），取 base64url 前 16 個字元。
- 金鑰草案：`7wtYq6EDHQJxEa6gbWgOLRac-2bzsmJv`（隨機產生；要換請在定案前換）。

### 2. 網頁端小檔 `web/classpass.js`

給所有小遊戲引用，例如 `ClassPass.show({ game, level, score, max })` 就顯示簽好章的 QR code。

- 內建班級、座號的輸入框（第一次用時跳出，可修改），存在 localStorage。
- 用 Web Crypto（`crypto.subtle`）算 HMAC；GitHub Pages 是 https，可以用。
- QR code 用 qrcode-generator 1.4.4（MIT 授權，已放在 `web/vendor/qrcode.js`，sha1 `4c79be1679603ef9691b98a90617aa1e534e93ef`）。中文要用 UTF-8：`qrcode.stringToBytes = qrcode.stringToBytesFuncs['UTF-8']`。
- 附一個 `web/demo.html` 讓老師測試。
- 零 node 依賴，原生 JS。

### 3. Android app `android/`

- **連續掃描**：相機一直開著，不用每掃一個就按一次。
- **掃到時的回饋**：嗶一聲加震動，畫面大字顯示「701 班 7 號」和這次收到的各關成績（例如「澆水 ★★★」），約一秒後自動等下一個。
- **重複掃描**：同一個 QR code 幾秒內重複被掃到只記一次。同一個學生、同一遊戲、同一關，只留最好的成績（一張 QR 拆成一關一筆存）。
- **驗證失敗**：簽章不對或格式不對時，響另一種聲音、顯示紅色。
- **紀錄頁**：列出已收紀錄，可依遊戲篩選；可以清除全部紀錄，清除前要確認。
- **匯出 CSV**：用長表格式，欄位是「遊戲、關卡代號、關卡名稱、班級、座號、分數、滿分、完成時間、掃描時間」。檔案加 UTF-8 BOM，Excel 開中文才不會亂碼。透過 Android 分享選單傳到 Line、Email 或雲端硬碟。
- **離線**：完全離線也能用。
- **建議技術**（新對話可調整）：
  - 畫面：Kotlin＋Jetpack Compose。
  - 相機：CameraX。
  - 辨識：ML Kit 條碼掃描，用模型內建在 app 裡的版本，才能離線。
  - 存檔：內建的 SQLite。
  - 分享檔案：FileProvider。
  - 版本：minSdk 26 左右。
- **相容性測試**：寫一個單元測試，用網頁端算出的固定範例當測試資料，確認 Kotlin 的驗簽結果和 JS 一致。

## 浮葉農場那一側（之後回 robot-farm 做）

- 練習頁 `practice.html` 過關時呼叫 classpass.js 顯示 QR code。遊戲代號暫定 `robot-farm-practice`，關卡代號用 `game/lessons.js` 裡的 `id`，分數用星等（滿分 3）。
- robot-farm 的 `vendor/qrcode/` 已放好 qrcode-generator，**還沒 commit**。之後看是直接引用 class-pass 的 classpass.js，還是把小檔複製進 robot-farm，再決定這個資料夾要不要留。

## 使用者協作習慣（摘要，完整的在 `~/.claude/CLAUDE.md`）

- 全程繁體中文。
- 跨檔案重構、新增依賴、刪除東西、選方案的分岔，都要先攤開選項讓使用者決定。
- 不可逆的動作一定要先確認。
- 字要大、對比要夠；使用者包含年長者。
- git 原則上做到 staged 為止。
