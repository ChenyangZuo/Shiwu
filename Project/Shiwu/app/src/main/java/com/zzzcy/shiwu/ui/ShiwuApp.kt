package com.zzzcy.shiwu.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zzzcy.shiwu.ui.components.Dock
import com.zzzcy.shiwu.ui.components.DockTab
import com.zzzcy.shiwu.ui.components.Fab
import com.zzzcy.shiwu.ui.screens.HomeScreen
import com.zzzcy.shiwu.ui.screens.ItemFormScreen
import com.zzzcy.shiwu.ui.screens.ProfileScreen
import com.zzzcy.shiwu.ui.screens.StatsScreen
import com.zzzcy.shiwu.ui.screens.WishlistScreen
import com.zzzcy.shiwu.ui.theme.Bg

/** 应用根：底部 Dock 切换四个主屏，FAB 打开新增/编辑表单 */
@Composable
fun ShiwuApp() {
    var currentTab by rememberSaveable { mutableStateOf(DockTab.HOME) }
    var showForm by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        // 表单打开时，系统返回手势/返回键只关闭表单，不退出应用
        BackHandler(enabled = showForm) {
            showForm = false
        }

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
                    DockTab.HOME -> HomeScreen(onAddClick = { showForm = true })
                    DockTab.WISHLIST -> WishlistScreen(onAddClick = { showForm = true })
                    DockTab.STATS -> StatsScreen()
                    DockTab.PROFILE -> ProfileScreen()
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
                    onClick = { showForm = true }
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

        if (showForm) {
            ItemFormScreen(onClose = { showForm = false })
        }
    }
}
