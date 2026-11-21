package com.yang.jizhang.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yang.jizhang.data.PendingBill
import com.yang.jizhang.engine.payment.ScreenPaymentPolicy
import java.text.DateFormat
import java.util.Date

/**
 * v3.0.0 全局弹窗：待确认账单（自动记账识别不确定时）与 CSV 导出菜单。
 */

@Composable
internal fun PendingBillsDialog(
    bills: List<PendingBill>,
    feedback: String?,
    onSelect: (Long) -> Unit,
    onClose: () -> Unit,
) {
    val language = LocalVoyagerLanguage.current
    AlertDialog(
        onDismissRequest = onClose,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(language("待确认账单", "Pending review")) },
        text = {
            Column {
                feedback?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 8.dp))
                }
                if (bills.isEmpty()) {
                    Text(language("暂无待确认账单", "No transactions to review"))
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        items(bills, key = { it.id }) { bill ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onSelect(bill.id) }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(bill.merchant.ifBlank { language.sourceLabel(bill.source) }, fontWeight = FontWeight.SemiBold)
                                    Text(pendingBillDetails(bill, language), style = MaterialTheme.typography.bodySmall)
                                }
                                Text(fmtSigned(bill.amountCents, bill.isIncome), modifier = Modifier.padding(start = 8.dp))
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(language("关闭", "Close")) } },
    )
}

private fun pendingBillDetails(bill: PendingBill, language: VoyagerLanguage): String {
    val formatter = if (language == VoyagerLanguage.ENGLISH) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, language.locale)
    } else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    return "${language.sourceLabel(bill.source)} · ${formatter.format(Date(bill.transactionTime))}"
}

@Composable
internal fun PendingConfirmDialog(
    pending: PendingBill,
    busy: Boolean,
    error: String?,
    possibleDuplicate: Pair<Long, Boolean>?,
    onClose: () -> Unit,
    onConfirm: (amountCents: Long, isIncome: Boolean) -> Unit,
    onIgnore: () -> Unit,
) {
    val language = LocalVoyagerLanguage.current
    var amountText by rememberSaveable(pending.id) {
        mutableStateOf(
            "${pending.amountCents / 100}.${(pending.amountCents % 100).toString().padStart(2, '0')}"
        )
    }
    var isIncome by rememberSaveable(pending.id) { mutableStateOf(pending.isIncome) }
    val amountCents = amountText.toBigDecimalOrNull()?.movePointRight(2)?.toLong() ?: 0L
    val confirmsDuplicate = possibleDuplicate == (amountCents to isIncome)

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(pending.merchant.ifBlank { language("待确认账单", "Pending review") }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(pendingBillDetails(pending, language), style = MaterialTheme.typography.bodyMedium)
                Text(
                    when (val reason = pending.reason) {
                        null -> if (ScreenPaymentPolicy.isScreenEvent(pending.eventKey)) {
                            language("来自支付成功页。请核对金额后入账。", "Detected on a payment success screen. Check the amount before saving.")
                        } else language("请核对金额与收支方向", "Check the amount and whether this is income or an expense.")
                        "金额疑似被截断" -> language(reason, "Part of the amount may be missing.")
                        "识别置信度低" -> language(reason, "The amount could not be identified reliably.")
                        ScreenPaymentPolicy.POSSIBLE_DUPLICATE_REASON -> language(reason, "This may match a payment detected on screen.")
                        else -> reason
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                pending.rawSnippet?.takeIf(String::isNotBlank)?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(4.dp).selectableGroup()) {
                        listOf(language("支出", "Expense") to false, language("收入", "Income") to true).forEach { (label, income) ->
                            Box(
                                Modifier.weight(1f).clipSegment(isIncome == income)
                                    .selectable(selected = isIncome == income, enabled = !busy, role = Role.RadioButton) {
                                        isIncome = income
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    label,
                                    color = if (isIncome == income) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { value ->
                        val normalized = value.replace('，', '.').replace(',', '.').replace('。', '.')
                        if (normalized.length <= 10 && pendingAmountInput.matches(normalized)) amountText = normalized
                    },
                    enabled = !busy,
                    label = { Text(language("金额（元）", "Amount (CNY)")) },
                    prefix = { Text("¥") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                if (confirmsDuplicate) {
                    Text(
                        language("账本中已有时间相近的同渠道、同金额支出。如果是另一笔，可以仍然入账。", "The ledger has an expense for the same amount and payment provider around this time. Save only if this is a separate payment."),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                TextButton(enabled = !busy, onClick = onIgnore) {
                    Text(language("忽略此条", "Ignore transaction"), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = amountCents > 0 && !busy, onClick = { onConfirm(amountCents, isIncome) }) {
                val label = when {
                    busy -> language("处理中…", "Processing…")
                    confirmsDuplicate -> language("仍然入账", "Save anyway")
                    else -> language("确认入账", "Confirm transaction")
                }
                Text(label, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(language("稍后处理", "Review later")) } },
    )
}

private val pendingAmountInput = Regex("""[0-9]*(\.[0-9]{0,2})?""")

@Composable
private fun Modifier.clipSegment(selected: Boolean): Modifier =
    if (selected) this.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
    else this

/** 导出菜单：本月 / 全部 */
@Composable
fun ExportDialog(
    monthLabel: String,
    onDismiss: () -> Unit,
    onExportMonth: () -> Unit,
    onExportAll: () -> Unit,
) {
    val language = LocalVoyagerLanguage.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(language("导出账单", "Export transactions"), fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                ExportOption(language("本月账单（$monthLabel）", "This month ($monthLabel)"), onExportMonth)
                ExportOption(language("全部账单", "All transactions"), onExportAll)
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(language("取消", "Cancel")) }
        },
    )
}

@Composable
private fun ExportOption(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text("›", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
