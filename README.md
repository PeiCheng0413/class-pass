# class-pass：小遊戲過關 QR code

學生玩網頁小遊戲過關後，畫面會出現一張 QR code。老師用 Android 手機一個接一個掃，就能收齊全班成績，最後匯出成 Excel 檔。

- 每位學生只要掃**一張**，所有關卡的成績都在裡面。
- 學生只要輸入班級和座號。
- 不需要網路，成績只存在老師的手機裡。

---

## 收成績的老師

### 安裝 app

1. 用 Android 手機打開 https://github.com/PeiCheng0413/class-pass/releases/latest
2. 下載 `.apk` 檔並點開安裝。手機問要不要允許「安裝不明來源的應用程式」時，選允許。
3. 打開「過關掃描」，允許使用相機。

### 上課時

- 鏡頭對準學生螢幕上的 QR code，聽到「嗶」一聲就收好了，直接掃下一位。
- **綠色**：收到了。**紅色**：這張 QR code 不能用。
- 同一位學生掃很多次也沒關係，每一關只會留下最好的成績。
- 到「紀錄」頁按「匯出 CSV」，可以傳到 Line、Email 或雲端硬碟，用 Excel 打開。
- 「清除全部」會刪掉手機裡所有成績，請先匯出再清除。

---

## 做遊戲的老師

### 方法一：在網頁遊戲加上兩個檔案（推薦）

1. 下載這兩個檔案，放進你的網站：
   - `web/classpass.js`
   - `web/vendor/qrcode.js`（放在 classpass.js 旁邊的 `vendor` 資料夾裡）
2. 在遊戲頁面加上：

   ```html
   <script src="classpass.js"></script>
   ```

3. 學生玩完時呼叫一次，畫面就會跳出 QR code：

   ```js
   ClassPass.show({
     game: 'wang-fraction-pizza',
     levels: [
       { level: 'level-1', title: '第一關', score: 3, max: 3 },
       { level: 'level-2', title: '切披薩', score: 4, max: 5 },
     ],
   });
   ```

   - `game`：遊戲代號，只能用英文、數字和 `-`。請在前面加上你的名字或學校縮寫，以免跟別的老師撞名。
   - `levels`：放已經過關的關卡。`level` 是關卡代號（規則同遊戲代號），`title` 可以寫中文，`score` 是分數，`max` 是滿分。
   - 第一次使用時，畫面會請學生輸入班級和座號，之後就不用再輸入。
   - 想讓學生之後再叫出 QR code，呼叫 `ClassPass.showPassed('你的遊戲代號')`。
   - 想記錄學生完成整個遊戲花了多久，可以多傳 `elapsed: 秒數`。用時只放進 QR code，不會顯示在學生畫面上；老師的 app 會顯示，也會匯出到 CSV。

網站要放在 https 網址上（例如 GitHub Pages）才能使用。想先試試看效果，可以打開 `web/demo.html`。

### 方法二：自己產生 QR code

如果遊戲不是網頁（例如用 Python 寫的），可以自己組出下面這行文字，再轉成 QR code：

```
CP2|遊戲代號|班級|座號|關卡清單|附加資料|簽章
```

- 關卡清單：每一關寫成 `關卡代號~關卡名稱~分數~滿分~完成時間`，關卡之間用 `;` 隔開。完成時間用 Unix 秒數。
- 附加資料：可以留空。要記錄用時就寫 `elapsed=秒數`。
- 簽章：用下面的關鍵字，對前面整段文字（包含最後一個 `|`）算 HMAC-SHA256，再取 base64url 編碼的前 16 個字元。

關鍵字：

```
7wtYq6EDHQJxEa6gbWgOLRac-2bzsmJv
```

Python 範例：

```python
import base64, hashlib, hmac, time

KEY = b"7wtYq6EDHQJxEa6gbWgOLRac-2bzsmJv"

def classpass_text(game, cls, seat, levels, elapsed=None):
    """levels: [(關卡代號, 關卡名稱, 分數, 滿分), ...]；elapsed: 用時（秒），可省略"""
    now = int(time.time())
    items = ";".join(f"{code}~{title}~{score}~{max_}~{now}" for code, title, score, max_ in levels)
    extras = "" if elapsed is None else f"elapsed={round(elapsed)}"
    body = f"CP2|{game}|{cls}|{int(seat)}|{items}|{extras}|"
    mac = hmac.new(KEY, body.encode("utf-8"), hashlib.sha256).digest()
    return body + base64.urlsafe_b64encode(mac).decode().rstrip("=")[:16]

print(classpass_text("wang-fraction-pizza", "701", 7, [("level-1", "第一關", 3, 3)], elapsed=754))
```
