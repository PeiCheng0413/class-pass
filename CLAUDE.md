# class-pass 維護筆記

README 是寫給其他老師看的使用說明；只跟維護者有關的事情寫在這裡。
協定以 `docs/PROTOCOL.md` 為準，已定案的需求見 `docs/REQUIREMENTS.md`。

## 地雷區

- QR 格式已經公開給其他老師使用。目前網頁端一律產生 CP2，app 同時認得 CP1 與 CP2；要再改格式就升版，app 必須同時認得新舊版。
- CP2 附加資料（2026-10-18 定案）：`key=value` 明文（不遮蔽），`elapsed` 留最小；其他 key 後蓋前、原樣匯出。對話框不顯示附加資料。
- 金鑰公開也沒關係（使用者已確認），不要再建議改成伺服器驗證。
- README 是給其他老師看的：寫得易懂就好，不要放防偽弱點、技術細節或開發步驟（使用者 2026-10-18 要求）。

## 開發

- 網頁端本機測試：`python3 -m http.server 8765 --directory web`，打開 `demo.html`，按「簽章自我檢查」核對測試向量。
- Android 需要 JDK 17 和 Android SDK（`android/local.properties` 的 `sdk.dir`，這個檔不進 git）；Gradle 用 wrapper 固定版本。

  ```bash
  cd android
  export JAVA_HOME=/opt/homebrew/opt/openjdk@17
  ./gradlew testDebugUnitTest assembleDebug   # app/build/outputs/apk/debug/app-debug.apk
  ```

- 技術：Kotlin＋Compose＋CameraX＋ML Kit（模型內建，離線可用）＋SQLite。同一張 QR 還在鏡頭前、或離開不到 5 秒，都不會重複處理。

## 發佈正式版（release）

```bash
cd android
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew testDebugUnitTest assembleRelease   # 產出 app/build/outputs/apk/release/app-release.apk
```

- 簽章金鑰在 `~/.android/class-pass-release.jks`，密碼在 `~/.gradle/gradle.properties`（`CLASSPASS_*`），兩者都不進 repo。
- **換電腦時兩個檔都要帶走並備份。** 金鑰遺失的話，之後的版本無法覆蓋安裝，老師得先解除安裝 app，手機裡的紀錄會被清掉。
- 發佈前先調高 `android/app/build.gradle.kts` 的 `versionCode` 和 `versionName`。
- 建 GitHub Release，把 APK 改名成 `class-pass-<版本>.apk` 上傳。README 的下載連結指向 `releases/latest`，不用改。
- 把 APK 傳到使用者的 Pixel 時用 LocalSend；Tailscale Taildrop 會卡住。
