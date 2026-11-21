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
    val language = LocalVoyagerLanguage.current
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
                Toast.makeText(context, if (delete) language("删除失败，请重试", "Could not delete the transaction. Try again.") else language("恢复失败，请重试", "Could not restore the transaction. Try again."), Toast.LENGTH_SHORT).show()
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
                PageTitle(language("归档账单", "Archived transactions"), Modifier.weight(1f))
                TextButton(onClick = onClose) { Text(language("返回", "Back")) }
            }
            if (bills.isEmpty()) {
                EmptyState(language("暂无归档账单", "No archived transactions"), language("在账单上左滑即可归档", "Swipe left on a transaction to archive it."))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(bills, key = { it.id }) { tx ->
                        val date = remember(tx.time, language) {
                            val formatter = if (language == VoyagerLanguage.ENGLISH) {
                                DateFormat.getDateInstance(DateFormat.DEFAULT, language.locale)
                            } else DateFormat.getDateInstance()
                            formatter.format(Date(tx.time))
                        }
                        Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text(tx.note.ifBlank { language.category(tx.category) }, style = MaterialTheme.typography.titleMedium)
                                Text(fmtSigned(tx.amountCents, tx.isIncome), style = MaterialTheme.typography.displayMedium,
                                    color = if (tx.isIncome) incomeColor() else MaterialTheme.colorScheme.onSurface)
                                Text(
                                    listOf(date, language.category(tx.category), language.sourceLabel(tx.source)).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(onClick = { modify(tx, delete = false) }) { Text(language("恢复", "Restore")) }
                                    TextButton(onClick = { pendingDelete = tx }) {
                                        Text(language("彻底删除", "Delete permanently"), color = MaterialTheme.colorScheme.error)
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
            title = { Text(language("彻底删除账单？", "Delete this transaction permanently?")) },
            text = { Text(language("删除后无法恢复，这笔账单也会从统计和预算中移除。", "This removes the transaction from statistics and budgets. You can't restore it.")) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    modify(tx, delete = true)
                }) { Text(language("彻底删除", "Delete permanently"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(language("取消", "Cancel")) } },
        )
    }
}
