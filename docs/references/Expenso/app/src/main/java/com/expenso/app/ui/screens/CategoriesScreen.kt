package com.expenso.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.expenso.app.data.Category
import com.expenso.app.data.ExpensoViewModel
import com.expenso.app.ui.components.IconMapper
import com.expenso.app.ui.components.AnimatedEntranceItem
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CategoriesScreen(
    viewModel: ExpensoViewModel,
    onNavigateToAddCategory: () -> Unit,
    onEditCategory: (Category) -> Unit,
    onDismiss: () -> Unit
) {
    val categories by viewModel.allCategories.collectAsState()
    var selectedCatType by remember { mutableStateOf("expense") }
    var selectedCategoryIds by remember { mutableStateOf(emptySet<Int>()) }

    BackHandler(enabled = selectedCategoryIds.isNotEmpty()) {
        selectedCategoryIds = emptySet()
    }

    var editOrder by remember { mutableStateOf(false) }
    var displayCategories by remember { mutableStateOf(categories.filter { it.type.lowercase() == selectedCatType }) }

    LaunchedEffect(categories, selectedCatType, editOrder) {
        if (!editOrder) {
            displayCategories = categories.filter { it.type.lowercase() == selectedCatType }
        }
    }

    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {}
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(top = 32.dp)
                .fillMaxSize()
        ) {
            AnimatedContent(
                targetState = selectedCategoryIds.isNotEmpty(),
                label = "top_bar_animation"
            ) { isSelectionMode ->
                if (isSelectionMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .padding(vertical = 4.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), CircleShape)
                                .clickable { selectedCategoryIds = emptySet() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close Selection", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                        }
                        
                        Text(
                            text = "${selectedCategoryIds.size} selected",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp)
                        )
                        
                        if (selectedCategoryIds.size == 1) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), CircleShape)
                                    .clickable {
                                        val catId = selectedCategoryIds.first()
                                        val cat = categories.find { it.id == catId }
                                        if (cat != null) {
                                            onEditCategory(cat)
                                        }
                                        selectedCategoryIds = emptySet()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                            }
                        }
                        
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), CircleShape)
                                .clickable {
                                    selectedCategoryIds.forEach { id ->
                                        val cat = categories.find { it.id == id }
                                        if (cat != null) {
                                            viewModel.deleteCategory(
                                                category = cat,
                                                onSuccess = {},
                                                onFailure = {}
                                            )
                                        }
                                    }
                                    selectedCategoryIds = emptySet()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        }
                    }
                } else {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        com.expenso.app.ui.components.ExpensoTopBar(
                            title = "Categories",
                            navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                            onNavigationClick = onDismiss,
                            navigationContentDescription = "Back"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Pill Selector (Expense vs Income capsules)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        shape = CircleShape
                    )
                    .padding(4.dp)
            ) {
                // EXPENSE BUTTON
                val isExpenseSelected = selectedCatType == "expense"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(
                            if (isExpenseSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else Color.Transparent
                        )
                        .clickable {
                            selectedCatType = "expense"
                            editOrder = false
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowDownward,
                            contentDescription = null,
                            tint = if (isExpenseSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "EXPENSE",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isExpenseSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // INCOME BUTTON
                val isIncomeSelected = selectedCatType == "income"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(
                            if (isIncomeSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else Color.Transparent
                        )
                        .clickable {
                            selectedCatType = "income"
                            editOrder = false
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowUpward,
                            contentDescription = null,
                            tint = if (isIncomeSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "INCOME",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isIncomeSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .height(36.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(percent = 50))
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(percent = 50)
                        )
                        .clickable {
                            onNavigateToAddCategory()
                        }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Add",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "New Category",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    modifier = Modifier
                        .height(36.dp)
                        .background(
                            if (editOrder) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(percent = 50)
                        )
                        .border(
                            BorderStroke(1.dp, if (editOrder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(percent = 50)
                        )
                        .clickable { editOrder = !editOrder }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (editOrder) Icons.Rounded.Check else Icons.Rounded.Edit,
                        contentDescription = "Edit Order",
                        modifier = Modifier.size(16.dp),
                        tint = if (editOrder) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (editOrder) "Done" else "Edit Order",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (editOrder) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            var draggingItemIndex by remember { mutableStateOf<Int?>(null) }
            var draggingItemKey by remember { mutableStateOf<Int?>(null) }
            var draggingItemOffset by remember { mutableStateOf(0f) }

            if (displayCategories.isEmpty()) {
                com.expenso.app.ui.components.EmptyState(
                    icon = androidx.compose.material.icons.Icons.Rounded.Menu,
                    title = "No Categories",
                    message = "You haven't created any categories of this type yet.",
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .lazyListScrollbar(listState, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                itemsIndexed(displayCategories, key = { _, cat -> cat.id }) { index, category ->
                    val color = try { Color(android.graphics.Color.parseColor(category.color)) } catch (e: Exception) { Color.Gray }

                    val isDragging = index == draggingItemIndex
                    val transY = if (isDragging) draggingItemOffset else 0f

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .graphicsLayer { this.translationY = transY }
                            .zIndex(if (isDragging) 1f else 0f)
                    ) {
                        val isSelected = selectedCategoryIds.contains(category.id)
                        AnimatedEntranceItem(index = index) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        enabled = !editOrder,
                                        onClick = {
                                            if (selectedCategoryIds.isNotEmpty()) {
                                                selectedCategoryIds = if (isSelected) {
                                                    selectedCategoryIds - category.id
                                                } else {
                                                    selectedCategoryIds + category.id
                                                }
                                            } else if (!editOrder) {
                                                onEditCategory(category)
                                            }
                                        },
                                        onLongClick = {
                                            if (!editOrder) {
                                                selectedCategoryIds = if (isSelected) {
                                                    selectedCategoryIds - category.id
                                                } else {
                                                    selectedCategoryIds + category.id
                                                }
                                            }
                                        }
                                    ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .background(color, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        IconMapper.CategoryIcon(
                                            icon = category.icon,
                                            categoryName = category.name,
                                            tint = Color.Black,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = category.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )

                                    if (editOrder) {
                                        Icon(
                                            imageVector = Icons.Rounded.DragHandle,
                                            contentDescription = "Drag to reorder",
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.pointerInput(Unit) {
                                                detectVerticalDragGestures(
                                                    onDragStart = {
                                                        draggingItemIndex = index
                                                        draggingItemKey = category.id
                                                        draggingItemOffset = 0f
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    },
                                                    onVerticalDrag = { change, dragAmount ->
                                                        change.consume()
                                                        draggingItemOffset += dragAmount
                                                        
                                                        val currentIdx = draggingItemIndex ?: return@detectVerticalDragGestures
                                                        val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentIdx } ?: return@detectVerticalDragGestures
                                                        if (itemInfo.key != draggingItemKey) return@detectVerticalDragGestures

                                                        val center = itemInfo.offset + (itemInfo.size / 2) + draggingItemOffset

                                                        val targetItem = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                                            it.index != currentIdx && center > it.offset && center < (it.offset + it.size)
                                                        }

                                                        if (targetItem != null) {
                                                            val targetIdx = targetItem.index
                                                            if (targetIdx in displayCategories.indices) {
                                                                val newList = displayCategories.toMutableList()
                                                                val item = newList.removeAt(currentIdx)
                                                                newList.add(targetIdx, item)
                                                                displayCategories = newList
                                                                draggingItemIndex = targetIdx
                                                                draggingItemOffset -= (targetItem.offset - itemInfo.offset)
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            }
                                                        }
                                                    },
                                                    onDragEnd = {
                                                        draggingItemIndex = null
                                                        draggingItemOffset = 0f
                                                        viewModel.updateCategoryOrders(displayCategories)
                                                    },
                                                    onDragCancel = {
                                                        draggingItemIndex = null
                                                        draggingItemOffset = 0f
                                                    }
                                                )
                                            }
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.ChevronRight,
                                            contentDescription = "Details",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
            }
        }
    }
}

fun Modifier.lazyListScrollbar(
    state: androidx.compose.foundation.lazy.LazyListState,
    color: Color,
    width: Dp = 4.dp
): Modifier = this.drawWithContent {
    drawContent()
    
    val layoutInfo = state.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo
    
    if (visibleItems.isNotEmpty() && totalItems > visibleItems.size) {
        val firstVisibleItem = visibleItems.first()
        val averageItemHeight = visibleItems.sumOf { it.size } / visibleItems.size
        
        val totalHeightEst = totalItems * averageItemHeight.toFloat()
        val viewportHeight = layoutInfo.viewportSize.height
        val currentOffset = (state.firstVisibleItemIndex * averageItemHeight) + state.firstVisibleItemScrollOffset
        
        val scrollbarHeight = (viewportHeight.toFloat() / totalHeightEst) * size.height
        val scrollbarOffsetY = (currentOffset / totalHeightEst) * size.height
        
        val finalScrollbarHeight = scrollbarHeight.coerceIn(24.dp.toPx(), size.height)
        val finalScrollbarOffsetY = scrollbarOffsetY.coerceIn(0f, size.height - finalScrollbarHeight)
        
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width - width.toPx() - 4.dp.toPx(), finalScrollbarOffsetY),
            size = Size(width.toPx(), finalScrollbarHeight),
            cornerRadius = CornerRadius(width.toPx() / 2, width.toPx() / 2)
        )
    }
}
