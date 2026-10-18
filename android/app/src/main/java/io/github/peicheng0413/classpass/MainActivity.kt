package io.github.peicheng0413.classpass

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Green = Color(0xFF15803D)
private val Red = Color(0xFFB91C1C)
private val Blue = Color(0xFF1D4ED8)
private val Ink = Color(0xFF111111)
private val Star = Color(0xFFB45309)

class MainActivity : ComponentActivity() {
    private lateinit var store: Store
    private lateinit var feedback: Feedback
    private lateinit var session: ScanSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 掃描時螢幕不要自動變暗
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        store = Store(this)
        feedback = Feedback(this)
        session = ScanSession(store, feedback)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Blue, onSurface = Ink, onBackground = Ink)) {
                Surface(Modifier.fillMaxSize(), color = Color.White) {
                    App(store, session)
                }
            }
        }
    }

    override fun onDestroy() {
        feedback.release()
        store.close()
        super.onDestroy()
    }
}

@Composable
private fun App(store: Store, session: ScanSession) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        PrimaryTabRow(selectedTabIndex = tab) {
            listOf("掃描", "紀錄").forEachIndexed { i, label ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = { Text(label, fontSize = 24.sp, fontWeight = FontWeight.Bold) },
                )
            }
        }
        // 切到「紀錄」時掃描畫面會被移除，相機也跟著關掉
        when (tab) {
            0 -> ScanScreen(session)
            else -> RecordsScreen(store)
        }
    }
}

// ---------- 掃描 ----------

@Composable
private fun ScanScreen(session: ScanSession) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    if (!granted) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("需要相機權限才能掃描 QR code。", fontSize = 24.sp)
            Spacer(Modifier.height(24.dp))
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                Text("允許使用相機", fontSize = 22.sp)
            }
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        CameraPreview(onCode = session::onCode, modifier = Modifier.fillMaxSize())

        Text(
            "這次已收 ${session.accepted} 張",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(12.dp)
                .background(Color(0xCC000000), RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )

        val event = session.last
        if (event == null) {
            Text(
                "把學生的 QR code 對準鏡頭",
                color = Color.White,
                fontSize = 24.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        } else {
            // 每來一筆新的結果，3 秒後自動收起；期間相機照樣在掃
            LaunchedEffect(event.id) {
                delay(3_000)
                session.dismiss(event.id)
            }
            ScanResultCard(event, Modifier.align(Alignment.BottomCenter).padding(12.dp))
        }
    }
}

@Composable
private fun ScanResultCard(event: ScanEvent, modifier: Modifier = Modifier) {
    val color = if (event is ScanEvent.Accepted) Green else Red
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column {
            Text(
                if (event is ScanEvent.Accepted) "✓ 已收到" else "✗ 無效的 QR code",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().background(color).padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Column(Modifier.padding(16.dp)) {
                when (event) {
                    is ScanEvent.Rejected -> Text(event.reason, fontSize = 24.sp, color = Red)
                    is ScanEvent.Accepted -> {
                        val p = event.pass
                        Text("${p.cls} 班 ${p.seat} 號", fontSize = 40.sp, fontWeight = FontWeight.Bold)
                        Text(p.game, fontSize = 18.sp, color = Color(0xFF444444))
                        Spacer(Modifier.height(8.dp))
                        p.levels.zip(event.outcomes).forEach { (lv, outcome) ->
                            LevelRow(lv.title, lv.score, lv.max, tag = when (outcome) {
                                SaveOutcome.NEW -> "新"
                                SaveOutcome.IMPROVED -> "進步"
                                SaveOutcome.UNCHANGED -> ""
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelRow(title: String, score: Int, max: Int, tag: String = "") {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 24.sp, modifier = Modifier.weight(1f))
        if (tag.isNotEmpty()) {
            Text(
                tag,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(end = 10.dp)
                    .background(Green, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        ScoreText(score, max)
    }
}

/** 滿分 5 以下用星星，其他用「85 / 100」。 */
@Composable
private fun ScoreText(score: Int, max: Int) {
    if (max <= 5) {
        Text("★".repeat(score) + "☆".repeat(max - score), fontSize = 26.sp, color = Star)
    } else {
        Text("$score / $max", fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

// ---------- 紀錄 ----------

@Composable
private fun RecordsScreen(store: Store) {
    val context = LocalContext.current
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    // 清除後遞增，讓下面的 remember 重新讀資料庫
    var version by remember { mutableIntStateOf(0) }
    val games = remember(version) { store.games() }
    if (filter != null && filter !in games) filter = null
    val records = remember(filter, version) { store.all(filter) }
    // 依「遊戲＋班級＋座號」分組，一個學生一張卡片
    val groups = remember(records) { records.groupBy { Triple(it.game, it.cls, it.seat) }.toList() }
    var confirmClear by remember { mutableStateOf(false) }
    // 要刪除的學生（遊戲、班級、座號）與筆數；null 表示沒有開確認視窗
    var toDelete by remember { mutableStateOf<Pair<Triple<String, String, Int>, Int>?>(null) }

    Column(Modifier.fillMaxSize()) {
        if (games.size > 1) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterButton("全部", filter == null) { filter = null }
                games.forEach { g -> FilterButton(g, filter == g) { filter = g } }
            }
        }
        Text(
            "共 ${groups.size} 位學生、${records.size} 筆",
            fontSize = 22.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        HorizontalDivider()

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (groups.isEmpty()) {
                item { Text("還沒有紀錄。到「掃描」開始收成績。", fontSize = 22.sp, modifier = Modifier.padding(8.dp)) }
            }
            items(groups, key = { it.first.toString() }) { (key, list) ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F4)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${key.second} 班 ${key.third} 號", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            if (filter == null && games.size > 1) {
                                Text(key.first, fontSize = 18.sp, modifier = Modifier.padding(start = 12.dp))
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { toDelete = key to list.size }) {
                                Text("刪除", fontSize = 20.sp, color = Red)
                            }
                        }
                        // 各關中最近一次收到新成績的時間；有用時就接在後面（附加資料以學生為單位，各列都一樣）
                        val elapsed = list.first().elapsed
                        Text(
                            "收到時間 " + formatScanTime(list.maxOf { it.scannedAt }) +
                                (elapsed?.let { "　用時 " + formatElapsed(it) } ?: ""),
                            fontSize = 18.sp,
                            color = Color(0xFF444444),
                        )
                        list.forEach { LevelRow(it.title, it.score, it.max) }
                    }
                }
            }
        }

        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { confirmClear = true },
                enabled = records.isNotEmpty() || games.isNotEmpty(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Red),
                modifier = Modifier.weight(1f).height(60.dp),
            ) { Text("清除全部", fontSize = 22.sp) }
            Button(
                onClick = { shareCsv(context, records, filter) },
                enabled = records.isNotEmpty(),
                modifier = Modifier.weight(1f).height(60.dp),
            ) { Text("匯出 CSV", fontSize = 22.sp) }
        }
    }

    toDelete?.let { (key, count) ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("刪除 ${key.second} 班 ${key.third} 號？", fontSize = 26.sp) },
            text = { Text("${key.first} 的 $count 筆紀錄會刪除，無法復原。", fontSize = 22.sp) },
            confirmButton = {
                TextButton(onClick = {
                    store.deleteStudent(key.first, key.second, key.third)
                    version++
                    toDelete = null
                }) { Text("刪除", fontSize = 22.sp, color = Red) }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("取消", fontSize = 22.sp) }
            },
        )
    }

    if (confirmClear) {
        val total = remember { store.all().size }
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清除全部紀錄？", fontSize = 26.sp) },
            text = { Text("手機裡全部 $total 筆紀錄（所有遊戲）都會刪除，無法復原。還沒匯出的話請先匯出。", fontSize = 22.sp) },
            confirmButton = {
                TextButton(onClick = {
                    store.clear()
                    version++
                    confirmClear = false
                }) { Text("清除", fontSize = 22.sp, color = Red) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消", fontSize = 22.sp) }
            },
        )
    }
}

@Composable
private fun FilterButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label, fontSize = 20.sp) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label, fontSize = 20.sp, color = Ink) }
    }
}

/** 今天的只顯示「14:32」，其他日子顯示「10/17 14:32」。 */
private fun formatScanTime(sec: Long): String {
    val t = Instant.ofEpochSecond(sec).atZone(ZoneId.systemDefault())
    val pattern = if (t.toLocalDate() == LocalDate.now()) "HH:mm" else "M/d HH:mm"
    return DateTimeFormatter.ofPattern(pattern).format(t)
}

/** 寫成 CSV 放在 cache/exports/，再透過 Android 分享選單送到 Line、Email 或雲端硬碟。 */
private fun shareCsv(context: Context, records: List<Record>, game: String?) {
    val dir = File(context.cacheDir, "exports")
    dir.mkdirs()
    dir.listFiles()?.forEach { it.delete() }
    val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(LocalDateTime.now())
    val file = File(dir, "classpass-${game ?: "all"}-$stamp.csv")
    file.writeText(toCsv(records), Charsets.UTF_8)

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, file.name)
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "匯出 CSV"))
}
