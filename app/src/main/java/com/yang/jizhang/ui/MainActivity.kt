package com.yang.jizhang.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ChevronLeft
import androidx.compose.material.icons.automirrored.rounded.ChevronRight
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yang.jizhang.JizhangApp
import com.yang.jizhang.data.Source
import com.yang.jizhang.data.TxEntity
import com.yang.jizhang.importer.CsvImporter
import com.yang.jizhang.CategoryRules
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { JizhangTheme { AppRoot() } }
    }
}

fun fmtCents(cents: Long): String {
    val abs = Math.abs(cents)
    return "¥${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
}

fun listenerEnabled(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context)
        .contains(context.packageName)

private fun dayKey(millis: Long): Int {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    return c.get(Calendar.YEAR) * 10000 + c.get(Calendar.MONTH) * 100 + c.get(Calendar.DAY_OF_MONTH)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { (context.applicationContext as JizhangApp).db.txDao() }

    val today = remember { Calendar.getInstance() }
    var year by rememberSaveable { mutableIntStateOf(today.get(Calendar.YEAR)) }
    var month by rememberSaveable { mutableIntStateOf(today.get(Calendar.MONTH) + 1) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<TxEntity?>(null) }
    var adding by remember { mutableStateOf(false) }

    val range = remember(year, month) { CategoryRules.monthRange(year, month) }
    val txs by dao.watchRange(range[0], range[1] - 1)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val n = runCatching { CsvImporter.import(context, uri, Source.IMPORT) }.getOrDefault(0)
            Toast.makeText(
                context,
                if (n > 0) "导入成功，新增 $n 条" else "导入失败：请选择支付宝/微信导出的账单 CSV",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val expenseTotal = txs.filter { !it.isIncome }.sumOf { it.amountCents }
    val incomeTotal = txs.filter { it.isIncome }.sumOf { it.amountCents }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("轻记账", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { picker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")) }) {
                        Icon(Icons.Rounded.UploadFile, contentDescription = "导入账单 CSV")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Outlined.ReceiptLong, null) },
                    label = { Text("明细") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Outlined.PieChart, null) },
                    label = { Text("统计") },
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("记一笔") },
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (!listenerEnabled(context)) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "未开启通知监听，点此设置以实时记账",
                            modifier = Modifier.weight(1f),
                            fontSize = 13.sp,
                        )
                        TextButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }) { Text("去开启") }
                    }
                }
            }

            MonthHeader(
                year = year, month = month,
                expense = expenseTotal, income = incomeTotal,
                onPrev = {
                    if (month == 1) { year--; month = 12 } else month--
                },
                onNext = {
                    if (month == 12) { year++; month = 1 } else month++
                },
            )

            when (tab) {
                0 -> TxList(txs = txs, onItemClick = { editing = it })
                else -> StatsTab(txs = txs)
            }
        }
    }

    if (adding) {
        TxDialog(
            tx = null,
            onClose = { adding = false },
            onSave = { amount, income, category, note ->
                scope.launch {
                    dao.insertIgnore(
                        TxEntity(
                            amountCents = amount, isIncome = income, category = category,
                            note = note, source = Source.MANUAL,
                            time = System.currentTimeMillis(),
                            dedupeKey = "MANUAL|${System.nanoTime()}",
                        )
                    )
                }
                adding = false
            },
        )
    }
    editing?.let { tx ->
        TxDialog(
            tx = tx,
            onClose = { editing = null },
            onSave = { amount, income, category, note ->
                scope.launch { dao.update(tx.copy(amountCents = amount, isIncome = income, category = category, note = note)) }
                editing = null
            },
            onDelete = {
                scope.launch { dao.delete(tx) }
                editing = null
            },
        )
    }
}

@Composable
private fun MonthHeader(
    year: Int, month: Int, expense: Long, income: Long,
    onPrev: () -> Unit, onNext: () -> Unit,
) {
    Surface(tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Rounded.ChevronLeft, "上一月") }
                Text("$year 年 $month 月", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Rounded.ChevronRight, "下一月") }
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("支出", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(fmtCents(expense), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text("收入", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(fmtCents(income), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            }
        }
    }
}

@Composable
fun TxList(txs: List<TxEntity>, onItemClick: (TxEntity) -> Unit) {
    if (txs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("本月暂无记录\n点击右下角「记一笔」开始", color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 24.sp)
        }
        return
    }
    val dayFormat = remember { SimpleDateFormat("M月d日 EEEE", Locale.CHINA) }
    val groups = remember(txs) {
        txs.groupBy { dayKey(it.time) }
            .map { (key, list) ->
                Triple(
                    dayFormat.format(list.first().time),
                    list,
                    Pair(
                        list.filter { !it.isIncome }.sumOf { it.amountCents },
                        list.filter { it.isIncome }.sumOf { it.amountCents },
                    )
                )
            }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        groups.forEach { (label, list, sums) ->
            item(key = "head_$label${list.first().id}") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    if (sums.first > 0) Text("支 ${fmtCents(sums.first)}  ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (sums.second > 0) Text("收 ${fmtCents(sums.second)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(list, key = { it.id }) { tx ->
                TxRow(tx, onClick = { onItemClick(tx) })
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun TxRow(tx: TxEntity, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(CategoryRules.icon(tx.category), fontSize = 22.sp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                tx.note.ifBlank { tx.category },
                maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp,
            )
            Text(
                "${tx.category} · ${tx.source}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            (if (tx.isIncome) "+" else "-") + fmtCents(tx.amountCents),
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = if (tx.isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
