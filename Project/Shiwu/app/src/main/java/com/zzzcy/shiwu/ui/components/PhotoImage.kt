package com.zzzcy.shiwu.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.zzzcy.shiwu.util.PhotoCache
import com.zzzcy.shiwu.util.PhotoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 照片展示组件：采样解码全部在后台线程完成，结果按尺寸写入内存缓存。
 * 主线程零解码，首页网格 / 详情页 / 表单预览统一走这里。
 * 未加载完成时不做任何绘制（外层通常有占位背景）。
 */
@Composable
fun PhotoImage(
    path: String,
    targetWidth: Int,
    targetHeight: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    var bitmap by remember(path, targetWidth, targetHeight) {
        mutableStateOf<ImageBitmap?>(null)
    }
    LaunchedEffect(path, targetWidth, targetHeight) {
        bitmap = withContext(Dispatchers.IO) {
            val cached = PhotoCache.get(path, targetWidth, targetHeight)
            if (cached != null) {
                cached.asImageBitmap()
            } else {
                PhotoUtils.decodeSampledBitmap(path, targetWidth, targetHeight)
                    ?.also { PhotoCache.put(path, targetWidth, targetHeight, it) }
                    ?.asImageBitmap()
            }
        }
    }
    bitmap?.let { bmp ->
        Image(
            bitmap = bmp,
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale
        )
    }
}
