package com.zzzcy.shiwu.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.zzzcy.shiwu.data.Item
import com.zzzcy.shiwu.data.formatPrice
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 统计报表原生渲染器：不依赖屏幕截图，直接把报表绘制到 Bitmap。
 * 固定浅色品牌版式（宽 1080），高度随内容自适应；最后附 Slogan 条带。
 */
object StatsReportRenderer {
    private const val W = 1080
    private const val PAD = 46
    private const val CW = W - PAD * 2

    // 固定浅色品牌色板（与 LightPalette 一致）
    private val PageBg = Color.parseColor("#F4F1EB")
    private val CardBg = Color.parseColor("#FFFFFF")
    private val Fg = Color.parseColor("#1B1B19")
    private val Fg2 = Color.parseColor("#3A3A35")
    private val Muted = Color.parseColor("#7A766B")
    private val Bg2 = Color.parseColor("#EFEBE2")
    private val Border = Color.parseColor("#E2DCCF")
    private val Accent = Color.parseColor("#1E5F4F")
    private val AccentSoft = Color.parseColor("#E8F0EC")
    private val Accent2 = Color.parseColor("#2D7A6E")
    private val Warn = Color.parseColor("#B85C1E")
    private val Danger = Color.parseColor("#A8362C")
    private val ChipFg = Color.parseColor("#5C5232")
    private val Border2 = Color.parseColor("#D5CDB8")
    private val OnAccent = Color.parseColor("#F7F3E7")

    private val donutColors = intArrayOf(Accent, Warn, Accent2, ChipFg, Border2)

    private const val CARD_RADIUS = 22f
    private const val GAP = 22

    fun render(
        listName: String,
        rangeLabel: String,
        exportedAt: Long,
        trend: List<Pair<String, Double>>,
        totalValue: Double,
        shares: List<Triple<String, Int, Double>>,
        itemCount: Int,
        topCosts: List<Item>,
        expiryRanked: List<Item>
    ): Bitmap {
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(exportedAt))

        // ---- 版式高度计算 ----
        val headerH = 150
        val trendH = 34 + 44 + 18 + 240 + 42 + 30
        val legendRows = shares.size
        val categoryH = 34 + 44 + 18 + maxOf(240, legendRows * 58) + 30
        val costRows = topCosts.size.coerceAtMost(3)
        val costH = 34 + 44 + 10 + (if (topCosts.isEmpty()) 70 else costRows * 96) + 30
        val expRows = expiryRanked.size
        val expH = 34 + 44 + 10 + (if (expiryRanked.isEmpty()) 70 else expRows * 86) + 30
        val sloganH = 140

        val totalH = 48 + headerH + GAP + trendH + GAP + categoryH + GAP + costH + GAP + expH + 26 + sloganH
        val bitmap = Bitmap.createBitmap(W, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(PageBg)

        var y = 48
        // 页头
        drawText(canvas, "时物 · 统计报表", PAD.toFloat(), y + 48f, 42f, Fg, Typeface.SERIF, Typeface.BOLD)
        drawText(canvas, "「$listName」 · $dateStr · $rangeLabel", PAD.toFloat(), y + 100f, 24f, Muted)
        y += headerH + GAP

        y = drawTrendCard(canvas, y, trend, totalValue)
        y += GAP
        y = drawCategoryCard(canvas, y, shares, itemCount)
        y += GAP
        y = drawTopCostCard(canvas, y, topCosts)
        y += GAP
        y = drawExpiryCard(canvas, y, expiryRanked)
        y += 26
        drawSlogan(canvas, y)
        return bitmap
    }

    // ---------- 卡片基础 ----------

    private fun cardRect(top: Int, height: Int): RectF = RectF(
        PAD.toFloat(), top.toFloat(), (W - PAD).toFloat(), (top + height).toFloat()
    )

    private fun drawCard(canvas: Canvas, top: Int, height: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = CardBg
            setShadowLayer(10f, 0f, 6f, 0x22000000)
        }
        canvas.drawRoundRect(cardRect(top, height), CARD_RADIUS, CARD_RADIUS, paint)
    }

    private fun textPaint(
        size: Float,
        color: Int,
        family: Typeface = Typeface.DEFAULT,
        style: Int = Typeface.NORMAL
    ): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(family, style)
    }

    private fun drawText(
        canvas: Canvas,
        text: String,
        x: Float,
        baseline: Float,
        size: Float,
        color: Int,
        family: Typeface = Typeface.DEFAULT,
        style: Int = Typeface.NORMAL,
        align: Paint.Align = Paint.Align.LEFT
    ) {
        val p = textPaint(size, color, family, style).apply { textAlign = align }
        canvas.drawText(text, x, baseline, p)
    }

    private fun truncate(paint: Paint, text: String, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var s = text
        while (s.isNotEmpty() && paint.measureText(s + "…") > maxWidth) s = s.dropLast(1)
        return s + "…"
    }

    private fun drawPill(
        canvas: Canvas,
        text: String,
        rightEdge: Float,
        centerY: Float,
        bg: Int,
        fg: Int,
        textSize: Float
    ): Float {
        val p = textPaint(textSize, fg, Typeface.DEFAULT, Typeface.BOLD)
        val tw = p.measureText(text)
        val w = tw + 30f
        val h = 46f
        val top = centerY - h / 2f
        val left = rightEdge - w
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg }
        canvas.drawRoundRect(RectF(left, top, rightEdge, top + h), 23f, 23f, paint)
        val centered = centerY - (p.ascent() + p.descent()) / 2f
        canvas.drawText(text, rightEdge - tw - 15f, centered, p)
        return w
    }

    private fun drawCardTitle(canvas: Canvas, top: Int, title: String, meta: String) {
        drawText(canvas, title, (PAD + 30).toFloat(), top + 44f, 28f, Fg, Typeface.DEFAULT, Typeface.BOLD)
        drawText(
            canvas, meta, (W - PAD - 30).toFloat(), top + 44f, 22f, Muted,
            align = Paint.Align.RIGHT
        )
    }

    private fun drawEmpty(canvas: Canvas, top: Int, cardHeight: Int) {
        val contentTop = top + 34 + 44 + 18
        val contentBottom = top + cardHeight - 30
        val centerY = (contentTop + contentBottom) / 2f
        drawText(
            canvas, "暂无数据", W / 2f, centerY + 10f, 24f, Muted,
            align = Paint.Align.CENTER
        )
    }

    // ---------- 趋势 ----------

    private fun drawTrendCard(canvas: Canvas, top: Int, trend: List<Pair<String, Double>>, totalValue: Double): Int {
        val height = 34 + 44 + 18 + 240 + 42 + 30
        drawCard(canvas, top, height)
        drawText(canvas, "总价值趋势", (PAD + 30).toFloat(), top + 44f, 28f, Fg, Typeface.DEFAULT, Typeface.BOLD)

        // 右侧：当前金额 + 当前 pill
        val valuePaint = textPaint(26f, Fg2, Typeface.DEFAULT, Typeface.BOLD)
        val valueStr = "¥${totalValue.formatPrice()}"
        val rightEdge = (W - PAD - 30).toFloat()
        val valueCenterY = top + 44f + (valuePaint.ascent() + valuePaint.descent()) / 2f
        val pillW = drawPill(canvas, "当前", rightEdge, valueCenterY, AccentSoft, Accent, 20f)
        canvas.drawText(
            valueStr, rightEdge - pillW - 16f, top + 44f,
            valuePaint.apply { textAlign = Paint.Align.RIGHT }
        )

        val left = (PAD + 30).toFloat()
        val right = (W - PAD - 30).toFloat()
        val chartTop = top + 34 + 44 + 18
        val chartH = 240f
        val plotBottom = chartTop + chartH - 12f
        val plotTop = chartTop + 12f

        if (trend.isEmpty()) {
            drawText(canvas, "暂无数据", (PAD + 30).toFloat(), plotBottom - 40f, 24f, Muted, align = Paint.Align.CENTER)
        } else {
            val maxV = (trend.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(1.0)
            // 网格
            val grid = textPaint(18f, Muted)
            listOf(0.25f, 0.5f, 0.75f).forEach { f ->
                val gy = plotTop + (plotBottom - plotTop) * f
                val gp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Border
                    strokeWidth = 2f
                }
                canvas.drawLine(left, gy, right, gy, gp)
            }
            // 数据点
            val step = if (trend.size > 1) (right - left) / (trend.size - 1) else 0f
            val points = trend.mapIndexed { i, (_, v) ->
                val px = left + step * i
                val py = plotBottom - (v / maxV).toFloat() * (plotBottom - plotTop)
                px to py
            }
            // 面积渐变
            val areaPath = Path().apply {
                moveTo(points.first().first, plotBottom)
                points.forEach { lineTo(it.first, it.second) }
                lineTo(points.last().first, plotBottom)
                close()
            }
            val areaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, plotTop, 0f, plotBottom,
                    Color.argb(46, 30, 95, 79),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawPath(areaPath, areaPaint)

            if (points.size >= 2) {
                val line = Path().apply {
                    moveTo(points.first().first, points.first().second)
                    points.drop(1).forEach { lineTo(it.first, it.second) }
                }
                val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Accent
                    style = Paint.Style.STROKE
                    strokeWidth = 6f
                    strokeCap = Paint.Cap.ROUND
                    isAntiAlias = true
                }
                canvas.drawPath(line, linePaint)
            }
            // 末点
            val last = points.last()
            canvas.drawCircle(last.first, last.second, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CardBg })
            canvas.drawCircle(last.first, last.second, 7f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Accent })

            // 月份标签
            trend.forEachIndexed { i, (label, _) ->
                val labelX = if (trend.size > 1) left + step * i else left + (right - left) / 2f
                canvas.drawText(label, labelX, plotBottom + 38f, grid.apply { textAlign = Paint.Align.CENTER })
            }
        }
        return top + height
    }

    // ---------- 分类占比 ----------

    private fun drawCategoryCard(
        canvas: Canvas,
        top: Int,
        shares: List<Triple<String, Int, Double>>,
        totalCount: Int
    ): Int {
        val legendRows = shares.size
        val height = 34 + 44 + 18 + maxOf(240, legendRows * 58) + 30
        drawCard(canvas, top, height)
        drawCardTitle(canvas, top, "分类占比", "${shares.size} 个分类")

        if (shares.isEmpty()) {
            drawEmpty(canvas, top, height)
            return top + height
        }

        val contentTop = top + 34 + 44 + 18
        // 环形图
        val donutSize = 240f
        val stroke = 30f
        val cx = PAD + 30 + donutSize / 2f
        val cy = contentTop + donutSize / 2f
        val rect = RectF(cx - donutSize / 2f + stroke / 2f, cy - donutSize / 2f + stroke / 2f,
            cx + donutSize / 2f - stroke / 2f, cy + donutSize / 2f - stroke / 2f)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Bg2
            style = Paint.Style.STROKE
            strokeWidth = stroke
        }
        canvas.drawArc(rect, -90f, 360f, false, bgPaint)
        var start = -90f
        shares.forEachIndexed { index, share ->
            val sweep = (share.third * 360f).toFloat()
            val segPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = donutColors[index % donutColors.size]
                style = Paint.Style.STROKE
                strokeWidth = stroke
            }
            canvas.drawArc(rect, start, sweep - 0.5f, false, segPaint)
            start += sweep
        }
        drawText(canvas, "$totalCount", cx, cy - 2f, 38f, Fg, Typeface.DEFAULT, Typeface.BOLD, Paint.Align.CENTER)
        drawText(canvas, "件物品", cx, cy + 34f, 18f, Muted, align = Paint.Align.CENTER)

        // 图例
        val legendX = PAD + 30 + donutSize + 48f
        val legendMaxW = (W - PAD - 30) - legendX
        shares.forEachIndexed { index, share ->
            val rowY = contentTop + 12f + index * 58f
            canvas.drawRoundRect(
                RectF(legendX, rowY + 4f, legendX + 18f, rowY + 22f),
                5f, 5f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = donutColors[index % donutColors.size] }
            )
            val namePaint = textPaint(22f, Fg2)
            canvas.drawText(
                truncate(namePaint, share.first, legendMaxW - 150f),
                legendX + 32f, rowY + 22f, namePaint
            )
            drawText(
                canvas, "${share.second} 件", legendX + 32f, rowY + 50f, 18f, Muted
            )
            drawText(
                canvas, "${(share.third * 100).toInt()}%",
                (W - PAD - 30).toFloat(), rowY + 22f, 22f, Fg, Typeface.DEFAULT, Typeface.BOLD,
                Paint.Align.RIGHT
            )
        }
        return top + height
    }

    // ---------- 日均成本排名 ----------

    private fun drawTopCostCard(canvas: Canvas, top: Int, topCosts: List<Item>): Int {
        val count = topCosts.size.coerceAtMost(3)
        val height = 34 + 44 + 10 + (if (topCosts.isEmpty()) 70 else count * 96) + 30
        drawCard(canvas, top, height)
        drawCardTitle(canvas, top, "日均成本排名", "TOP ${count.coerceAtLeast(1)} · 按当前持有")
        if (topCosts.isEmpty()) {
            drawEmpty(canvas, top, height)
            return top + height
        }
        val maxCost = topCosts.maxOfOrNull { it.dayCost } ?: 1.0
        val rowLeft = (PAD + 30).toFloat()
        val rowRight = (W - PAD - 30).toFloat()
        topCosts.take(3).forEachIndexed { index, item ->
            val rowTop = top + 34 + 44 + 10 + index * 96
            val rank = index + 1
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (rank == 1) Accent else Bg2
            }
            canvas.drawRoundRect(RectF(rowLeft, rowTop.toFloat(), rowLeft + 42f, rowTop + 42f), 14f, 14f, badgePaint)
            drawText(
                canvas, "$rank", rowLeft + 21f, rowTop + 29f, 22f,
                if (rank == 1) OnAccent else Muted, Typeface.DEFAULT, Typeface.BOLD, Paint.Align.CENTER
            )
            val namePaint = textPaint(24f, Fg, Typeface.DEFAULT, Typeface.BOLD)
            val costPaint = textPaint(26f, Fg, Typeface.DEFAULT, Typeface.BOLD)
            val costStr = "¥${"%.1f".format(item.dayCost)}"
            val nameMax = rowRight - (rowLeft + 42f + 18f) - costPaint.measureText(costStr) - 90f
            canvas.drawText(
                truncate(namePaint, item.name, nameMax),
                rowLeft + 42f + 18f, rowTop + 28f, namePaint
            )
            // 成本文字
            canvas.drawText(costStr, rowRight, rowTop + 28f, costPaint.apply { textAlign = Paint.Align.RIGHT })
            drawText(canvas, "/天", rowRight, rowTop + 48f, 18f, Muted, align = Paint.Align.RIGHT)
            // 进度条
            val barTop = rowTop + 56f
            val barLeft = rowLeft + 42f + 18f
            val barRight = rowRight
            val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Bg2 }
            canvas.drawRoundRect(RectF(barLeft, barTop, barRight, barTop + 10f), 5f, 5f, trackPaint)
            val fillW = (barRight - barLeft) * (item.dayCost / maxCost).toFloat().coerceIn(0f, 1f)
            if (fillW > 0f) {
                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Accent }
                fillPaint.alpha = 204
                canvas.drawRoundRect(RectF(barLeft, barTop, barLeft + fillW, barTop + 10f), 5f, 5f, fillPaint)
            }
        }
        return top + height
    }

    // ---------- 临期排行 ----------

    private fun drawExpiryCard(canvas: Canvas, top: Int, expiryRanked: List<Item>): Int {
        val height = 34 + 44 + 10 + (if (expiryRanked.isEmpty()) 70 else expiryRanked.size * 86) + 30
        drawCard(canvas, top, height)
        drawCardTitle(canvas, top, "临期物品 TOP 10", "按剩余比例")
        if (expiryRanked.isEmpty()) {
            drawEmpty(canvas, top, height)
            return top + height
        }
        val rowLeft = (PAD + 30).toFloat()
        val rowRight = (W - PAD - 30).toFloat()
        expiryRanked.forEachIndexed { index, item ->
            val rowTop = top + 34 + 44 + 10 + index * 86
            val rank = index + 1
            val status = item.expiryStatus
            val color = when (status) {
                com.zzzcy.shiwu.data.ExpiryStatus.EXPIRED -> Danger
                com.zzzcy.shiwu.data.ExpiryStatus.EXPIRING -> Warn
                else -> Muted
            }
            val statusText = when (status) {
                com.zzzcy.shiwu.data.ExpiryStatus.EXPIRED ->
                    "已过期 ${(-(item.daysToExpiry ?: 0L)).coerceAtLeast(0)} 天"
                else -> {
                    val pct = ((item.remainingRatio ?: 0.0) * 100).toInt()
                    "剩 ${item.daysToExpiry ?: 0} 天 · $pct%"
                }
            }
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = if (rank == 1) Accent else Bg2
            }
            canvas.drawRoundRect(RectF(rowLeft, rowTop.toFloat(), rowLeft + 42f, rowTop + 42f), 14f, 14f, badgePaint)
            drawText(
                canvas, "$rank", rowLeft + 21f, rowTop + 29f, 22f,
                if (rank == 1) OnAccent else Muted, Typeface.DEFAULT, Typeface.BOLD, Paint.Align.CENTER
            )
            val statusPaint = textPaint(22f, color, Typeface.DEFAULT, Typeface.BOLD)
            val statusW = statusPaint.measureText(statusText)
            val namePaint = textPaint(24f, Fg, Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(
                truncate(namePaint, item.name, rowRight - (rowLeft + 42f + 18f) - statusW - 24f),
                rowLeft + 42f + 18f, rowTop + 28f, namePaint
            )
            canvas.drawText(statusText, rowRight, rowTop + 28f, statusPaint.apply { textAlign = Paint.Align.RIGHT })
            // 剩余比例条
            val barTop = rowTop + 52f
            val barLeft = rowLeft + 42f + 18f
            val barRight = rowRight
            val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Bg2 }
            canvas.drawRoundRect(RectF(barLeft, barTop, barRight, barTop + 10f), 5f, 5f, trackPaint)
            val fillW = (barRight - barLeft) * (item.remainingRatio ?: 0.0).toFloat().coerceIn(0f, 1f)
            if (fillW > 0f) {
                canvas.drawRoundRect(
                    RectF(barLeft, barTop, barLeft + fillW, barTop + 10f), 5f, 5f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
                )
            }
        }
        return top + height
    }

    // ---------- Slogan 条带 ----------

    private fun drawSlogan(canvas: Canvas, top: Int) {
        val h = 140f
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AccentSoft }
        canvas.drawRect(0f, top.toFloat(), W.toFloat(), top + h, bg)
        val p = textPaint(30f, Accent, Typeface.SERIF, Typeface.BOLD).apply { textAlign = Paint.Align.CENTER }
        val baseline = top + h / 2f - (p.ascent() + p.descent()) / 2f
        canvas.drawText("「物有来处，时有所记。」 — 时物", W / 2f, baseline, p)
    }
}
