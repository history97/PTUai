package kr.landscapebid.app.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kr.landscapebid.engine.Format
import kr.landscapebid.engine.JsMath
import kr.landscapebid.engine.PickModel
import kr.landscapebid.engine.PreDistribution
import kr.landscapebid.engine.Rates
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private fun DrawScope.text(
    paint: Paint, s: String, x: Float, y: Float, sizeDp: Float, color: Color, align: Paint.Align, bold: Boolean = false,
) {
    paint.textSize = sizeDp * density
    paint.color = color.toArgb()
    paint.textAlign = align
    paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    drawContext.canvas.nativeCanvas.drawText(s, x, y, paint)
}

/**
 * 사정율 선택 차트 (웹의 pickSvg). 막대 = 과거에 나온 횟수, 곡선 = 부드럽게 만든 분포,
 * 점선 = 최빈/중앙값/평균, 노란 줄 = 지금 고른 사정율. 좌우로 끌면 미리보기, 손을 떼면 선택.
 */
@Composable
fun PickChartCanvas(
    model: PickModel, b: Double, a: Double, rate: Double, sExp: Double, probW: Double, previewS: Double?,
    heightDp: Int, onPreview: (Double?) -> Unit, onCommit: (Double) -> Unit, modifier: Modifier = Modifier,
) {
    val pal = P
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val preview = rememberUpdatedState(onPreview)
    val commit = rememberUpdatedState(onCommit)
    val left = 14.0
    val right = 12.0
    val top = 62.0

    fun sAt(px: Float, widthPx: Int, density: Float): Double {
        val wDp = widthPx / density.toDouble()
        val s = model.s(px / density.toDouble(), left, wDp, right)
        return JsMath.round(s.coerceIn(model.lo, model.hi) * 10000) / 10000
    }

    Canvas(
        modifier.fillMaxWidth().height(heightDp.dp)
            .pointerInput(model) {
                detectTapGestures(onTap = { off -> commit.value(sAt(off.x, size.width, density)) })
            }
            .pointerInput(model) {
                var last: Double? = null
                detectHorizontalDragGestures(
                    onDragStart = { off -> last = sAt(off.x, size.width, density); preview.value(last) },
                    onDragEnd = { last?.let { commit.value(it) }; preview.value(null) },
                    onDragCancel = { preview.value(null) },
                    onHorizontalDrag = { change, _ -> last = sAt(change.position.x, size.width, density); preview.value(last) },
                )
            },
    ) {
        val d = density
        val wd = size.width / d
        val hd = size.height / d
        val volH = hd - top.toFloat() - 54f
        val xAxY = top.toFloat() + volH
        fun x(s: Double): Float = (model.x(s, left, wd.toDouble(), right) * d).toFloat()
        fun yv(v: Double): Float = ((xAxY - volH * (v / model.vmax)) * d).toFloat()
        val tPx = (top * d).toFloat()
        val axPx = xAxY * d
        val volPx = volH * d

        // 가운데 50% 띠
        drawRect(pal.mossSoft.copy(alpha = .55f), Offset(x(model.q25), tPx), Size(x(model.q75) - x(model.q25), volPx))
        text(paint, "많이 나온 50%", x(model.q25) + 4 * d, (top + 13).toFloat() * d, 11f, pal.moss, Paint.Align.LEFT, bold = true)

        // 막대
        val nb = model.vol.size
        for (i in 0 until nb) {
            val s0 = model.lo + i * model.binWidth
            val xx = x(s0) + 1 * d
            val ww = max(1 * d, x(s0 + model.binWidth) - x(s0) - 2 * d)
            val inBand = s0 + model.binWidth > model.q25 && s0 < model.q75
            val y0 = yv(model.vol[i])
            drawRect(if (inBand) pal.moss.copy(alpha = .9f) else pal.muted.copy(alpha = .45f), Offset(xx, y0), Size(ww, axPx - y0))
        }

        // 곡선
        val path = Path()
        for (i in model.kdeX.indices) {
            val px = x(model.kdeX[i]); val py = yv(model.kdeY[i])
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, pal.ink.copy(alpha = .8f), style = Stroke(width = 1.8f * d))

        // 가로축과 눈금
        drawLine(pal.muted, Offset(left.toFloat() * d, axPx), Offset((wd - right).toFloat() * d, axPx), strokeWidth = 1f * d)
        val step = if (wd < 500) 1.0 else 0.5
        var s = -2.0
        while (s <= 2 + 1e-9) {
            val xx = x(s)
            val align = if (xx > (wd - right - 30) * d) Paint.Align.RIGHT else if (xx < (left + 20) * d) Paint.Align.LEFT else Paint.Align.CENTER
            drawLine(pal.muted, Offset(xx, axPx), Offset(xx, axPx + 4 * d), strokeWidth = 1f * d)
            text(paint, (if (s > 0) "+" else "") + Format.jsNumber(s) + "%", xx, axPx + 17 * d, 11f, pal.ink, align)
            if (wd >= 500 || Math.abs(s) == 1.0 || s == 0.0) {
                text(paint, Format.manAxis(Rates.floorAt(b, a, rate, s)), xx, axPx + 32 * d, 10.5f, pal.muted, align)
            }
            s += step
        }

        // 최빈 / 중앙값 / 평균 표시
        val marks = listOf(Triple(model.modeS, MarkMode, "최빈"), Triple(model.median, pal.moss, "중앙값"), Triple(model.mean, MarkMean, "평균"))
        marks.forEachIndexed { k, (sv, col, lab) ->
            val xx = x(sv)
            val yy = (2 + k * 19) * d
            val wL = (if (lab.length > 2) 48 else 38) * d
            val cx = min(max(xx, left.toFloat() * d + wL / 2), (wd - right).toFloat() * d - wL / 2)
            drawLine(col, Offset(xx, yy + 16 * d), Offset(xx, axPx), strokeWidth = 1.6f * d, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4 * d, 3 * d)))
            drawRoundRect(col, Offset(cx - wL / 2, yy), Size(wL, 16 * d), CornerRadius(8 * d))
            text(paint, lab, cx, yy + 12 * d, 11f, Color.White, Paint.Align.CENTER, bold = true)
        }

        // 지금 고른 사정율
        val sel = sExp.coerceIn(model.lo, model.hi)
        drawRect(pal.ochre.copy(alpha = .28f), Offset(x(sel - probW), tPx), Size(x(sel + probW) - x(sel - probW), volPx))
        drawLine(pal.ochre, Offset(x(sel), tPx), Offset(x(sel), axPx), strokeWidth = 3f * d)

        // 끌고 있는 위치(미리보기)
        if (previewS != null) {
            drawRect(pal.ink.copy(alpha = .12f), Offset(x(previewS - probW), tPx), Size(x(previewS + probW) - x(previewS - probW), axPx - tPx))
            drawLine(pal.ink, Offset(x(previewS), tPx), Offset(x(previewS), axPx), strokeWidth = 1.5f * d)
        }
    }
}

/**
 * 예정 투찰금액 분포 그래프 (웹의 cPre). 막대 = A 실제, 점선 = B 단순 이론, 굵은 선 = C 보정 이론.
 * 막대를 누르면 selected 가 바뀌어 아래에 숫자가 나온다.
 */
@Composable
fun PreChartCanvas(pre: PreDistribution, selected: Int?, onSelect: (Int?) -> Unit, heightDp: Int = 300, modifier: Modifier = Modifier) {
    val pal = P
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val select = rememberUpdatedState(onSelect)
    val mL = 46f; val mR = 10f; val mT = 10f; val mB = 108f

    Canvas(
        modifier.fillMaxWidth().height(heightDp.dp).pointerInput(pre) {
            detectTapGestures(onTap = { off ->
                val n = pre.bins
                val plotW = size.width / density - mL - mR
                val idx = floor((off.x / density - mL) / (plotW / n)).toInt()
                select.value(if (idx in 0 until n) idx else null)
            })
        },
    ) {
        val d = density
        val n = pre.bins
        if (n == 0) return@Canvas
        val wd = size.width / d; val hd = size.height / d
        val plotW = wd - mL - mR; val plotH = hd - mT - mB
        val cat = plotW / n
        var maxV = 0.0
        for (i in 0 until n) maxV = max(maxV, max(pre.hA[i], max(pre.hB[i], pre.hC[i]))) 
        maxV *= 100
        // 보기 좋은 최대값
        val niceSteps = doubleArrayOf(0.5, 1.0, 2.0, 2.5, 5.0, 10.0, 20.0, 25.0, 50.0)
        val stepY = niceSteps.firstOrNull { ceil(maxV / it) <= 6 } ?: 50.0
        val yMax = max(stepY, ceil(maxV / stepY) * stepY)
        fun px(v: Number): Float = (v.toDouble() * d).toFloat()
        fun yOf(v: Double): Float = px(mT + plotH - plotH * (v * 100 / yMax))

        // 가로 눈금선
        var yv = 0.0
        while (yv <= yMax + 1e-9) {
            val yy = px(mT + plotH - plotH * (yv / yMax))
            drawLine(pal.line, Offset(px(mL.toDouble()), yy), Offset(px((wd - mR).toDouble()), yy), strokeWidth = 1f * d)
            text(paint, Format.jsNumber(yv), px(mL - 6.0), yy + 4 * d, 11f, pal.muted, Paint.Align.RIGHT)
            yv += stepY
        }
        // 선택한 막대 배경
        if (selected != null && selected in 0 until n) {
            drawRect(pal.ochre.copy(alpha = .22f), Offset(px(mL + selected * cat), px(mT.toDouble())), Size(px(cat), px(plotH)))
        }
        // A 막대
        for (i in 0 until n) {
            val x0 = px(mL + i * cat + cat * 0.1); val y0 = yOf(pre.hA[i])
            drawRect(pal.moss, Offset(x0, y0), Size(px(cat * 0.8), px(mT + plotH) - y0))
        }
        // B 점선, C 굵은 선
        fun line(h: DoubleArray, color: Color, width: Float, dashed: Boolean) {
            val path = Path()
            for (i in 0 until n) {
                val cx = px(mL + i * cat + cat / 2); val cy = yOf(h[i])
                if (i == 0) path.moveTo(cx, cy) else path.lineTo(cx, cy)
            }
            drawPath(path, color, style = Stroke(width = width * d, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4 * d, 4 * d)) else null))
        }
        line(pre.hB, pal.ink, 1.5f, true)
        line(pre.hC, pal.ochre, 2.5f, false)

        // 가로축 글자(비스듬히)
        val every = max(1, ceil(n / 16.0).toInt())
        val axisY = px(mT + plotH + 12.0)
        for (i in 0 until n step every) {
            val cx = px(mL + i * cat + cat / 2)
            val c = drawContext.canvas.nativeCanvas
            c.save(); c.rotate(-45f, cx, axisY)
            text(paint, pre.binLabel(i), cx, axisY, 11f, pal.ink, Paint.Align.RIGHT)
            c.restore()
        }
        text(paint, "투찰금액 구간 시작", px(mL + plotW / 2), px(mT + plotH + 70.0), 12f, pal.muted, Paint.Align.CENTER)
        // 범례
        val ly = px(hd - 12.0)
        val items = listOf("A 실제", "B 단순 이론", "C 보정 이론")
        var lx = px(mL + plotW / 2 - 120.0)
        items.forEachIndexed { k, name ->
            when (k) {
                0 -> drawRect(pal.moss, Offset(lx, ly - 9 * d), Size(18 * d, 9 * d))
                1 -> drawLine(pal.ink, Offset(lx, ly - 4 * d), Offset(lx + 18 * d, ly - 4 * d), strokeWidth = 1.5f * d, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4 * d, 4 * d)))
                else -> drawLine(pal.ochre, Offset(lx, ly - 4 * d), Offset(lx + 18 * d, ly - 4 * d), strokeWidth = 2.5f * d)
            }
            text(paint, name, lx + 22 * d, ly, 11f, pal.ink, Paint.Align.LEFT)
            lx += (22 + name.length * 11 + 14) * d
        }
    }
}
