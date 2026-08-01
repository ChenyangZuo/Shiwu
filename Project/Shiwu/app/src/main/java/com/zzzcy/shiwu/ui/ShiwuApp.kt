package com.zzzcy.shiwu.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zzzcy.shiwu.data.ShiwuRepository
import com.zzzcy.shiwu.ui.components.Dock
import com.zzzcy.shiwu.ui.components.DockTab
import com.zzzcy.shiwu.ui.components.Fab
import com.zzzcy.shiwu.ui.components.RetiredFilter
import com.zzzcy.shiwu.ui.screens.HomeScreen
import com.zzzcy.shiwu.ui.screens.CategoryManagementScreen
import com.zzzcy.shiwu.ui.screens.ItemDetailScreen
import com.zzzcy.shiwu.ui.screens.ItemFormScreen
import com.zzzcy.shiwu.ui.screens.ProfileScreen
import com.zzzcy.shiwu.ui.screens.StatsScreen
import com.zzzcy.shiwu.ui.screens.TrashScreen
import com.zzzcy.shiwu.ui.screens.ThemeScreen
import com.zzzcy.shiwu.ui.screens.WishFormScreen
import com.zzzcy.shiwu.ui.screens.WishlistScreen
import com.zzzcy.shiwu.ui.theme.Bg

enum class FormKind { ITEM, WISH }

/** 应用根：Tab 切换 + 详情页 / 表单 / 回收站 覆盖层 */
@Composable
fun ShiwuApp() {
    val items by ShiwuRepository.items.collectAsState()
    val wishes by ShiwuRepository.wishes.collectAsState()

    var currentTab by rememberSaveable { mutableStateOf(DockTab.HOME) }
    var query by rememberSaveable { mutableStateOf("") }
    var retiredFilter by rememberSaveable { mutableStateOf(RetiredFilter.ALL) }
    var detailItemId by rememberSaveable { mutableStateOf<Long?>(null) }
    var formKind by rememberSaveable { mutableStateOf<FormKind?>(null) }
    var editingItemId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingWishId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showTrash by rememberSaveable { mutableStateOf(false) }
    var showCategories by rememberSaveable { mutableStateOf(false) }
    var showTheme by rememberSaveable { mutableStateOf(false) }
    var formDirect by rememberSaveable { mutableStateOf(false) }

    // 覆盖层内容在退出动画期间保留：visible 状态被置空时，动画仍能带着旧内容滑出
    val detailContentId = remember { mutableStateOf<Long?>(null) }
    val formContentKind = remember { mutableStateOf<FormKind?>(null) }
    val formContentItemId = remember { mutableStateOf<Long?>(null) }
    val formContentWishId = remember { mutableStateOf<Long?>(null) }
    if (detailItemId != null) detailContentId.value = detailItemId
    if (formKind != null) formContentKind.value = formKind
    if (editingItemId != null) formContentItemId.value = editingItemId
    if (editingWishId != null) formContentWishId.value = editingWishId

    fun closeForm() {
        formKind = null
        editingItemId = null
        editingWishId = null
    }

    // 返回手势优先级：表单 > 详情 > 回收站 > 默认
    BackHandler(enabled = formKind != null || detailItemId != null || showTrash || showCategories || showTheme) {
        when {
            formKind != null -> closeForm()
            detailItemId != null -> detailItemId = null
            showTrash -> showTrash = false
            showCategories -> showCategories = false
            showTheme -> showTheme = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Crossfade(
                targetState = currentTab,
                animationSpec = tween(durationMillis = 220),
                label = "tabContent"
            ) { tab ->
                when (tab) {
                    DockTab.HOME -> HomeScreen(
                        items = items,
                        query = query,
                        onQueryChange = { query = it },
                        retiredFilter = retiredFilter,
                        onFilterChange = { retiredFilter = it },
                        onItemClick = { detailItemId = it },
                        onAddClick = {
                            formKind = FormKind.ITEM
                            editingItemId = null
                            formDirect = false
                        }
                    )
                    DockTab.WISHLIST -> WishlistScreen(
                        wishes = wishes,
                        onWishClick = {
                            formKind = FormKind.WISH
                            editingWishId = it
                            formDirect = false
                        },
                        onAddClick = {
                            formKind = FormKind.WISH
                            editingWishId = null
                            formDirect = false
                        },
                        onPurchased = { newItemId ->
                            formKind = null
                            detailItemId = newItemId
                        }
                    )
                    DockTab.STATS -> StatsScreen(items = items)
                    DockTab.PROFILE -> ProfileScreen(
                        items = items,
                        wishes = wishes,
                        onOpenTrash = { showTrash = true },
                        onOpenCategories = { showCategories = true },
                        onOpenTheme = { showTheme = true }
                    )
                }
            }

            val showFab = currentTab == DockTab.HOME || currentTab == DockTab.WISHLIST
            AnimatedVisibility(
                visible = showFab,
                enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.7f, animationSpec = tween(220)),
                exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.7f, animationSpec = tween(160)),
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                Fab(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(end = 24.dp, bottom = 96.dp),
                    onClick = {
                        when (currentTab) {
                            DockTab.HOME -> {
                                formKind = FormKind.ITEM
                                editingItemId = null
                                formDirect = false
                            }
                            DockTab.WISHLIST -> {
                                formKind = FormKind.WISH
                                editingWishId = null
                                formDirect = false
                            }
                            else -> Unit
                        }
                    }
                )
            }

            Dock(
                selected = currentTab,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
                onSelect = { currentTab = it }
            )
        }

        // 物品详情
        AnimatedVisibility(
            visible = detailItemId != null,
            enter = slideInVertically(
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                initialOffsetY = { it }
            ) + fadeIn(tween(durationMillis = 200)),
            exit = slideOutVertically(
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                targetOffsetY = { it }
            ) + fadeOut(tween(durationMillis = 180))
        ) {
            detailContentId.value?.let { id ->
                ItemDetailScreen(
                    itemId = id,
                    onBack = { detailItemId = null },
                    onEdit = {
                        // 保持详情页在表单下层打开，返回时直接回到详情
                        formKind = FormKind.ITEM
                        editingItemId = id
                        formDirect = true
                    }
                )
            }
        }

        // 回收站
        AnimatedVisibility(
            visible = showTrash,
            enter = slideInVertically(
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                initialOffsetY = { it }
            ) + fadeIn(tween(durationMillis = 200)),
            exit = slideOutVertically(
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                targetOffsetY = { it }
            ) + fadeOut(tween(durationMillis = 180))
        ) {
            TrashScreen(onClose = { showTrash = false })
        }

        // 分类管理
        AnimatedVisibility(
            visible = showCategories,
            enter = slideInVertically(
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                initialOffsetY = { it }
            ) + fadeIn(tween(durationMillis = 200)),
            exit = slideOutVertically(
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                targetOffsetY = { it }
            ) + fadeOut(tween(durationMillis = 180))
        ) {
            CategoryManagementScreen(onClose = { showCategories = false })
        }

        // 外观主题
        AnimatedVisibility(
            visible = showTheme,
            enter = slideInVertically(
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                initialOffsetY = { it }
            ) + fadeIn(tween(durationMillis = 200)),
            exit = slideOutVertically(
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                targetOffsetY = { it }
            ) + fadeOut(tween(durationMillis = 180))
        ) {
            ThemeScreen(onClose = { showTheme = false })
        }

        // 新增 / 编辑表单（底部滑入）
        AnimatedVisibility(
            visible = formKind != null,
            enter = if (formDirect) {
                EnterTransition.None
            } else {
                slideInVertically(
                    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                    initialOffsetY = { it }
                ) + fadeIn(tween(durationMillis = 200))
            },
            exit = if (formDirect) {
                ExitTransition.None
            } else {
                slideOutVertically(
                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                    targetOffsetY = { it }
                ) + fadeOut(tween(durationMillis = 180))
            }
        ) {
            when (formContentKind.value) {
                FormKind.ITEM -> ItemFormScreen(
                    editingItem = formContentItemId.value?.let { ShiwuRepository.itemById(it) },
                    onClose = ::closeForm,
                    onDeleted = { detailItemId = null },
                    onSaved = { id ->
                        closeForm()
                        detailItemId = id
                    }
                )
                FormKind.WISH -> WishFormScreen(
                    editingWish = formContentWishId.value?.let { ShiwuRepository.wishById(it) },
                    onClose = ::closeForm
                )
                null -> Unit
            }
        }
    }
}
