package com.zzzcy.shiwu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zzzcy.shiwu.ui.theme.Accent
import com.zzzcy.shiwu.ui.theme.Bg2
import com.zzzcy.shiwu.ui.theme.Border
import com.zzzcy.shiwu.ui.theme.Fg
import com.zzzcy.shiwu.ui.theme.Muted
import com.zzzcy.shiwu.ui.theme.OnAccent
import com.zzzcy.shiwu.ui.theme.SerifFamily
import com.zzzcy.shiwu.ui.theme.Surface
import com.zzzcy.shiwu.ui.components.consumeOverlayTaps

/** 首次启动昵称引导：全屏覆盖，输入昵称后才能进入 App */
@Composable
fun NicknameSetupScreen(onDone: (String) -> Unit) {
    var name by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .consumeOverlayTaps()
            .background(Surface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "时物",
                fontFamily = SerifFamily,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                color = Fg
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "你好，怎么称呼你？",
                fontSize = 13.sp,
                color = Muted
            )
            Spacer(Modifier.height(28.dp))
            Box(Modifier.fillMaxWidth()) {
                BasicTextField(
                    value = name,
                    onValueChange = { if (it.length <= 16) name = it },
                    textStyle = TextStyle(fontSize = 15.sp, color = Fg, lineHeight = 19.sp),
                    cursorBrush = SolidColor(Accent),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Bg2, RoundedCornerShape(12.dp))
                        .border(1.dp, if (name.isBlank()) Border else Accent, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 13.dp)
                )
                if (name.isBlank()) {
                    Text(
                        text = "输入昵称",
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                        color = Muted,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (name.isBlank()) Border.copy(alpha = 0.5f) else Accent)
                    .clickable(enabled = name.isNotBlank()) { onDone(name.trim()) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "开始使用",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnAccent
                )
            }
        }
    }
}
