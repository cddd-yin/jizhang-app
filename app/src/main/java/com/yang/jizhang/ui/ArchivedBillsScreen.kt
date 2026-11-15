package com.yang.jizhang.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yang.jizhang.data.TxDao
import com.yang.jizhang.data.TxEntity
import com.yang.jizhang.widget.WidgetSync
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ArchivedBillsScreen(dao: TxDao, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bills by remember(dao) { dao.watchArchived() }.collectAsStateWithLifecycle(initialValue = emptyList())
    var pendingDelete by remember { mutableStateOf<TxEntity?>(null) }

    fun modify(tx: TxEntity, delete: Boolean) {
        val stamp = tx.archivedAt ?: return
        scope.launch {
            try {
                val changed = if (delete) dao.deleteArchived(tx.id, stamp) else dao.restoreArchived(tx.id, stamp)
                if (changed > 0 && delete) WidgetSync.refreshAsync(context)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Toast.makeText(context, if (delete) "删除失败，请重试" else "恢复失败，请重试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PageTitle("归档账单", Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("返回") }
            }
            if (bills.isEmpty()) {
                EmptyState("暂无归档账单", "在账单上左滑即可归档")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(bills, key = { it.id }) { tx ->
                        val date = remember(tx.time) { DateFormat.getDateInstance().format(Date(tx.time)) }
                        Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text(tx.note.ifBlank { tx.category }, style = MaterialTheme.typography.titleMedium)
                                Text(fmtSigned(tx.amountCents, tx.isIncome), style = MaterialTheme.typography.titleLarge)
                                Text(
                                    listOf(date, tx.category, tx.source).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(onClick = { modify(tx, delete = false) }) { Text("恢复") }
                                    TextButton(onClick = { pendingDelete = tx }) {
                                        Text("彻底删除", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { tx ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("彻底删除账单？") },
            text = { Text("删除后无法恢复，这笔账单也会从统计和预算中移除。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    modify(tx, delete = true)
                }) { Text("彻底删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}
