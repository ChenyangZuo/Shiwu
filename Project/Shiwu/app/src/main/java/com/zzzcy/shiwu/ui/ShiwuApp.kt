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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.zzzcy.shiwu.ui.screens.ItemSort
import com.zzzcy.shiwu.ui.screens.ItemSortField
import com.zzzcy.shiwu.ui.screens.LocationManagementScreen
import com.zzzcy.shiwu.ui.screens.ListSwitcherScreen
import com.zzzcy.shiwu.ui.screens.NicknameSetupScreen
import com.zzzcy.shiwu.ui.screens.ProfileScreen
import com.zzzcy.shiwu.ui.screens.StatsScreen
import com.zzzcy.shiwu.ui.screens.TrashScreen
import com.zzzcy.shiwu.ui.screens.ThemeScreen
import com.zzzcy.shiwu.ui.screens.WebDavSyncScreen
import com.zzzcy.shiwu.ui.screens.WishFormScreen
import com.zzzcy.shiwu.ui.screens.WishlistScreen
import com.zzzcy.shiwu.ui.theme.Bg

enum class FormKind { ITEM, WISH }

/** 应用根：Tab 切换 + 详情页 / 表单 / 回收站 覆盖层 */
@Composable
fun ShiwuApp() {
    val items by ShiwuRepository.items.collectAsState()
    val wishes by ShiwuRepository.wishes.collectAsState()
    val currentListId by ShiwuRepository.currentListId.collectAsState()
    val lists by ShiwuRepository.lists.collectAsState()
    val currentItems = remember(items, currentListId) { items.filter { it.listId == currentListId } }
    val currentWishes = remember(wishes, currentListId) { wishes.filter { it.listId == currentListId } }

    var currentTab by rememberSaveable { mutableStateOf(DockTab.HOME) }
    var query by rememberSaveable { mutableStateOf("") }
    var retiredFilter by rememberSaveable { mutableStateOf(RetiredFilter.ALL) }
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var sortField by rememberSaveable { mutableStateOf(ItemSortField.PURCHASE_DATE) }
    var sortAsc by rememberSaveable { mutableStateOf(false) }
    var detailItemId by rememberSaveable { mutableStateOf<Long?>(null) }
    var formKind by rememberSaveable { mutableStateOf<FormKind?>(null) }
    var editingItemId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingWishId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showTrash by rememberSaveable { mutableStateOf(false) }
    var showCategories by rememberSaveable { mutableStateOf(false) }
    var showLocations by rememberSaveable { mutableStateOf(false) }
    var showSync by rememberSaveable { mutableStateOf(false) }
    var showTheme by rememberSaveable { mutableStateOf(false) }
    var showListSwitcher by rememberSaveable { mutableStateOf(false) }
    var formDirect by rememberSaveable { mutableStateOf(false) }
    var formSession by rememberSaveable { mutableStateOf(0) }
    var showNicknameSetup by remember { mutableStateOf(ShiwuRepository.needsNicknameSetup) }

    // 当前清单无效（如全部清单均被锁定）时，自动打开清单面板选择/解锁
    LaunchedEffect(currentListId, lists.size) {
        if (lists.isNotEmpty() && lists.none { it.id == currentListId }) {
            showListSwitcher = true
        }
    }

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

    /** 打开物品表单：editId 为 null 表示新增，否则为编辑；每次打开递增会话号，强制重置表单状态 */
    fun openItemForm(editId: Long?, direct: Boolean = false) {
        editingWishId = null
        editingItemId = editId
        formContentWishId.value = null
        formContentItemId.value = editId
        formKind = FormKind.ITEM
        formDirect = direct
        formSession++
    }

    /** 打开心愿表单：editId 为 null 表示新增 */
    fun openWishForm(editId: Long?) {
        editingItemId = null
        editingWishId = editId
        formContentItemId.value = null
        formContentWishId.value = editId
        formKind = FormKind.WISH
        formDirect = false
        formSession++
    }

    // 返回手势优先级：表单 > 详情 > 回收站 > 默认
    BackHandler(enabled = showNicknameSetup) {
        // 首次昵称引导不可跳过
    }
    BackHandler(
        enabled = formKind != null || detailItemId != null || showTrash || showCategories || showLocations || showSync || showTheme || showListSwitcher
    ) {
        when {
            showListSwitcher -> showListSwitcher = false
            formKind != null -> closeForm()
            detailItemId != null -> detailItemId = null
            showTrash -> showTrash = false
            showCategories -> showCategories = false
            showLocations -> showLocations = false
            showSync -> showSync = false
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
                        items = currentItems,
                        query = query,
                        onQueryChange = { query = it },
                        retiredFilter = retiredFilter,
                        onFilterChange = { retiredFilter = it },
                        categoryFilter = categoryFilter,
                        onCategoryFilterChange = { categoryFilter = it },
                        sort = ItemSort(sortField, sortAsc),
                        onSortChange = {
                            sortField = it.field
                            sortAsc = it.asc
                        },
                        listName = lists.firstOrNull { it.id == currentListId }?.name ?: "选择清单",
                        onListClick = { showListSwitcher = true },
                        onItemClick = { detailItemId = it },
                        onAddClick = { openItemForm(null) }
                    )
                    DockTab.WISHLIST -> WishlistScreen(
                        wishes = currentWishes,
                        onWishClick = { openWishForm(it) },
                        onAddClick = { openWishForm(null) },
                        onPurchased = { newItemId ->
                            formKind = null
                            detailItemId = newItemId
                        }
                    )
                    DockTab.STATS -> StatsScreen(items = currentItems)
                    DockTab.PROFILE -> ProfileScreen(
                        items = items,
                        wishes = wishes,
                        onOpenTrash = { showTrash = true },
                        onOpenCategories = { showCategories = true },
                        onOpenLocations = { showLocations = true },
                        onOpenSync = { showSync = true },
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
                            DockTab.HOME -> openItemForm(null)
                            DockTab.WISHLIST -> openWishForm(null)
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
                        openItemForm(id, direct = true)
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

        // 存放地点管理
        AnimatedVisibility(
            visible = showLocations,
            enter = slideInVertically(
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                initialOffsetY = { it }
            ) + fadeIn(tween(durationMillis = 200)),
            exit = slideOutVertically(
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                targetOffsetY = { it }
            ) + fadeOut(tween(durationMillis = 180))
        ) {
            LocationManagementScreen(onClose = { showLocations = false })
        }

        // WebDAV 同步
        AnimatedVisibility(
            visible = showSync,
            enter = slideInVertically(
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                initialOffsetY = { it }
            ) + fadeIn(tween(durationMillis = 200)),
            exit = slideOutVertically(
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                targetOffsetY = { it }
            ) + fadeOut(tween(durationMillis = 180))
        ) {
            WebDavSyncScreen(onClose = { showSync = false })
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

        // 清单切换面板
        AnimatedVisibility(
            visible = showListSwitcher,
            enter = slideInVertically(
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                initialOffsetY = { it }
            ) + fadeIn(tween(durationMillis = 200)),
            exit = slideOutVertically(
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                targetOffsetY = { it }
            ) + fadeOut(tween(durationMillis = 180))
        ) {
            ListSwitcherScreen(onClose = { showListSwitcher = false })
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
            key(formSession) {
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

        // 首次启动昵称引导（覆盖在最上层）
        if (showNicknameSetup) {
            NicknameSetupScreen(
                onDone = { name ->
                    ShiwuRepository.setupNickname(name)
                    showNicknameSetup = false
                }
            )
        }
    }
}
