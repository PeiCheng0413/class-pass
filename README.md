# class-pass：小遊戲過關 QR code

學生在網頁小遊戲過關後，畫面會出現一張 QR code。老師用 Android 手機連續掃描，就能收到全班的成績，最後匯出成 Excel 能開的 CSV。

- 一位學生、一個遊戲，只要掃**一張** QR code，裡面包含所有已過關的關卡。
- 學生只需要輸入班級和座號，不用輸入姓名。
- 手機完全離線也能用，成績只存在老師自己的手機裡。

任何老師都可以用，不需要申請。

---

## 一、收成績的老師：安裝掃描 app

1. 用 Android 手機（Android 8.0 以上）打開
   https://github.com/PeiCheng0413/class-pass/releases/latest
2. 下載 `class-pass-版本.apk`，點開安裝。手機會要求允許「安裝不明來源的應用程式」，請允許。
3. 打開「過關掃描」，允許使用相機。

上課時：

- **掃描**：鏡頭對準學生螢幕上的 QR code。掃到會嗶一聲並震動，畫面用大字顯示「701 班 7 號」和各關成績。不用按任何按鈕，直接掃下一位就好。
- **綠色**表示收到；**紅色**表示這張 QR code 無效（被改過，或不是這套系統產生的）。
- 同一位學生重複掃，每一關只會保留最好的成績。
- **紀錄**頁可以依遊戲篩選。按「匯出 CSV」可以傳到 Line、Email 或雲端硬碟，用 Excel 打開中文不會亂碼。
- 「清除全部」會刪掉手機裡所有紀錄，而且無法復原，請先匯出再清除。

---

## 二、做遊戲的老師：讓遊戲顯示 QR code

### 方法 A：引用 classpass.js（推薦）

1. 把這個 repo 的兩個檔案複製到你的網站，維持相同的相對位置：
   - `web/classpass.js`
   - `web/vendor/qrcode.js`（放在 classpass.js 同一層的 `vendor/` 資料夾）
2. 在遊戲頁面加入：

   ```html
   <script src="classpass.js"></script>
   ```

3. 學生做完時（例如按下「交成績」按鈕，或最後一關結束時）呼叫一次：

   ```js
   ClassPass.show({
     game: 'wang-fraction-pizza',          // 遊戲代號，見下方說明
     levels: [                              // 只放已經過關的關卡
       { level: 'level-1', title: '第一關', score: 3, max: 3 },
       { level: 'level-2', title: '切披薩', score: 4, max: 5 },
     ],
   });
   ```

   第一次使用時，畫面會先請學生輸入班級和座號，之後同一個網站的小遊戲都會沿用。

其他用法：

- `ClassPass.showPassed('遊戲代號')`：再叫出一次 QR code。
- `ClassPass.editStudent()`：修改班級座號。
- 也可以每過一關呼叫一次 `ClassPass.show({ game, level, title, score, max })`，各關會自動累積在同一張 QR code 裡。

需要注意：

- 網站必須是 **https**，例如 GitHub Pages；本機測試用 `localhost` 也可以。用 `file://` 直接打開檔案無法產生簽章。
- 可以用 `web/demo.html` 先試試看，再用 app 掃描確認。

### 遊戲代號與關卡代號

- 只能用英文字母、數字和 `-`，最長 40 字。
- **遊戲代號請在前面加上你的名字或學校縮寫**，例如 `wang-fraction-pizza`，避免跟其他老師的遊戲撞名。撞名的話，兩個遊戲的成績會混在一起。
- 關卡名稱是給人看的，可以用中文，最多 10 字。
- 分數和滿分都是整數。滿分 5 以下時，學生畫面和 app 會顯示成星星。

### 方法 B：自己產生 QR code

如果你的遊戲不是網頁（例如 Scratch、Python、App Inventor），可以照下面的格式自己產生 QR code。完整規則見 [docs/PROTOCOL.md](docs/PROTOCOL.md)。

**格式**（一行文字，UTF-8）：

```
CP1|遊戲代號|班級|座號|關卡清單|簽章
```

**關卡清單**：每一關寫成 `關卡代號~關卡名稱~分數~滿分~完成時間`，關卡之間用 `;` 隔開。

- 座號：1–999 的整數，不要補零（`07` 要寫成 `7`）。
- 完成時間：Unix 時間（從 1970 年起算的秒數）。
- 班級和關卡名稱裡不可以有 `|`、`;`、`~` 或換行。

**簽章**：

1. 從 `CP1` 開始，一直到關卡清單後面那個 `|`（**包含**這個 `|`），把這一段文字用 UTF-8 編碼。
2. 用下面的關鍵字算 HMAC-SHA256。
3. 把結果轉成 base64url（`+` 換成 `-`、`/` 換成 `_`，去掉 `=`），取前 16 個字元，接在最後面。

**關鍵字（金鑰）**：

```
7wtYq6EDHQJxEa6gbWgOLRac-2bzsmJv
```

QR code 請用 byte 模式，錯誤更正等級用 M。

**Python 範例**：

```python
import base64, hashlib, hmac, time

KEY = b"7wtYq6EDHQJxEa6gbWgOLRac-2bzsmJv"

def classpass_text(game, cls, seat, levels, now=None):
    """levels: [(關卡代號, 關卡名稱, 分數, 滿分), ...]；now 省略就用現在時間"""
    now = now or int(time.time())
    items = ";".join(f"{code}~{title}~{score}~{max_}~{now}" for code, title, score, max_ in levels)
    body = f"CP1|{game}|{cls}|{int(seat)}|{items}|"
    mac = hmac.new(KEY, body.encode("utf-8"), hashlib.sha256).digest()
    return body + base64.urlsafe_b64encode(mac).decode().rstrip("=")[:16]

print(classpass_text("wang-fraction-pizza", "701", 7, [("level-1", "第一關", 3, 3)]))
```

把印出來的文字用任何 QR code 產生器（byte 模式）轉成圖片即可。

**驗證用的範例**：執行

```python
print(classpass_text("demo", "8年3班", 23, [("level-2", "level-2", 85, 100)], now=1760003600))
```

應該印出下面這一行，最後 16 個字元就是簽章。用其他程式語言實作時，也可以拿這行來核對。

```
CP1|demo|8年3班|23|level-2~level-2~85~100~1760003600|627Pc4prOkVRT51j
```

---

## 關於防偽

關鍵字公開寫在這裡，懂程式的人可以自己做出假的 QR code。這套系統只是防止學生隨手改分數，目前國中生很難破解。如果真的有學生做得出來，那也是一種才華。

---

## 開發與維護

- 協定完整規則：[docs/PROTOCOL.md](docs/PROTOCOL.md)
- 需求與已定案事項：[docs/REQUIREMENTS.md](docs/REQUIREMENTS.md)
- 網頁端：`web/classpass.js`，測試頁 `web/demo.html`
- 老師端 app：`android/`（Kotlin＋Compose＋CameraX＋ML Kit）

### 網頁端

用法見上方「方法 A」。本機測試：

```bash
python3 -m http.server 8765 --directory web
```

開 http://localhost:8765/demo.html，按「簽章自我檢查」確認簽章與測試向量一致。

### Android app

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

#### 正式版（release）

```bash
./gradlew assembleRelease   # 產出 android/app/build/outputs/apk/release/app-release.apk
```

簽章金鑰是 `~/.android/class-pass-release.jks`，密碼在 `~/.gradle/gradle.properties`（`CLASSPASS_*`），兩者都不進 repo。
**換電腦時兩個檔都要帶走並備份**：金鑰遺失的話，之後的版本無法覆蓋安裝，老師得先解除安裝 app，手機裡的紀錄會被清掉。
發佈時建 GitHub Release，把 APK 改名成 `class-pass-<版本>.apk` 上傳；記得先在 `app/build.gradle.kts` 調高 `versionCode` 和 `versionName`。
