package com.yang.jizhang.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yang.jizhang.CategoryRules
import com.yang.jizhang.util.CategoryStore

/**
 * 分类管理（v3.12.0）：内置 11 类（不可删）+ 自定义分类增删。
 * 设置 → 数据管理 → 管理分类 打开。
 */
@Composable
fun CategoryManageDialog(onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var custom by remember { mutableStateOf(CategoryStore.custom(context)) }
    var newName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onClose,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("管理分类", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "内置分类（不可删除）",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                CategoryRules.ALL_CATEGORIES.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        row.forEach { cat ->
                            Row(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(vertical = 7.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(CategoryRules.icon(cat), fontSize = 13.sp)
                                Spacer(Modifier.width(4.dp))
                                Text(cat, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    "自定义分类（${custom.size}/20，删除不影响已有账单）",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                if (custom.isEmpty()) {
                    Text(
                        "还没有自定义分类，在记一笔页点 ➕ 也能添加",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                custom.forEach { cat ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(CategoryRules.icon(cat), fontSize = 16.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(cat, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text(
                            "删除",
                            fontSize = 13.sp,
                            color = expenseColor(),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    CategoryStore.remove(context, cat)
                                    custom = CategoryStore.custom(context)
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = {
                        if (it.length <= 6) newName = it
                        error = null
                    },
                    label = { Text("新分类名（1-6 字）") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    isError = error != null,
                    supportingText = error?.let { { Text(it, fontSize = 12.sp) } },
                    keyboardOptions = KeyboardOptions.Default,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = newName.isNotBlank(),
                onClick = {
                    val err = CategoryStore.add(context, newName)
                    if (err == null) {
                        custom = CategoryStore.custom(context)
                        newName = ""
                    } else {
                        error = err
                    }
                },
            ) { Text("添加", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("关闭") }
        },
    )
}
