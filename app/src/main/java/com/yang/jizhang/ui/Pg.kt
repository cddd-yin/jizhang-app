package com.yang.jizhang.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * 高级感文字组件，抄 wallee（Compose-Expense）的 Pg* 组件族。
 */

/** 大标题（页面级，Sans SemiBold） */
@Composable
fun PgHeadline1(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = MaterialTheme.typography.titleLarge, modifier = modifier)
}

/** 副标题（区块级，Sans Medium） */
@Composable
fun PgHeadline2(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, modifier = modifier)
}

/** 内容标题（行/卡片内） */
@Composable
fun PgContentTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onBackground,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(color = color, fontWeight = fontWeight),
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 日期/元信息小字 */
@Composable
fun PgDateLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onBackground,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(color = color.copy(alpha = 0.6f)),
        modifier = modifier,
    )
}

/**
 * 金额大字：符号（¥）用小字号、数字用大字号；放不下时自动等比缩小。
 */
@Composable
fun PgAmountLabel(
    amount: String,
    modifier: Modifier = Modifier,
    symbol: String = "¥",
    amountFontSize: TextUnit = 40.sp,
    symbolFontSize: TextUnit = 22.sp,
    color: Color = MaterialTheme.colorScheme.onBackground,
) {
    var textSize by remember { mutableStateOf(amountFontSize) }
    var symbolSize by remember { mutableStateOf(symbolFontSize) }

    Text(
        text = AnnotatedString(
            text = symbol + amount,
            spanStyles = listOf(
                AnnotatedString.Range(
                    SpanStyle(fontSize = symbolSize),
                    symbol.length,
                    symbol.length + amount.length,
                )
            ),
        ),
        style = MaterialTheme.typography.headlineMedium.copy(color = color, fontSize = textSize),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
        onTextLayout = { result ->
            if (result.isLineEllipsized(result.lineCount - 1) && textSize.value > 10f) {
                textSize = textSize.times(0.9f)
                symbolSize = symbolSize.times(0.9f)
            }
        },
    )
}

/** 金额格式化：带千分位，如 12,345.67 */
fun fmtMoney(cents: Long): String {
    val abs = Math.abs(cents)
    val fen = (abs % 100).toString().padStart(2, '0')
    return "%,d.$fen".format(abs / 100)
}
