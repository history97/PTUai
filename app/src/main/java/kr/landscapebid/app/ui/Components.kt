package kr.landscapebid.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 글자 한 덩어리. 숫자 자릿폭을 맞추는 tnum 은 웹과 동일. */
@Composable
fun T(
    text: String, size: Float = 15f, color: Color = P.ink, bold: Boolean = false, weight: FontWeight? = null,
    modifier: Modifier = Modifier, align: TextAlign? = null, italic: Boolean = false, maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text, modifier = modifier, color = color, fontSize = size.sp,
        fontWeight = weight ?: if (bold) FontWeight.Bold else null,
        fontStyle = if (italic) FontStyle.Italic else null, textAlign = align,
        lineHeight = (size * 1.5f).sp, maxLines = maxLines,
        style = TextStyle(fontFeatureSettings = "tnum"),
    )
}

@Composable
fun TA(text: AnnotatedString, size: Float = 15f, color: Color = P.ink, modifier: Modifier = Modifier, align: TextAlign? = null) {
    Text(
        text = text, modifier = modifier, color = color, fontSize = size.sp, textAlign = align,
        lineHeight = (size * 1.5f).sp, style = TextStyle(fontFeatureSettings = "tnum"),
    )
}

@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(P.paper).border(1.dp, P.line, shape).padding(padding),
        content = content,
    )
}

@Composable
fun Chip(
    text: String, pressed: Boolean, onClick: () -> Unit, borderColor: Color? = null,
    size: Float = 13f, hPad: Dp = 11.dp, vPad: Dp = 3.dp, boldPart: String? = null,
) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.clip(shape).background(if (pressed) P.mossSoft else P.bg)
            .border(1.dp, if (pressed) P.moss else (borderColor ?: P.line), shape)
            .clickable(onClick = onClick).padding(horizontal = hPad, vertical = vPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T(text, size, bold = pressed)
        if (boldPart != null) { Spacer(Modifier.width(4.dp)); T(boldPart, size, bold = true) }
    }
}

/** 작은 알약 버튼(±0.05% 같은 것). 웹의 .wchips button */
@Composable
fun MiniChip(text: String, pressed: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.clip(shape).background(if (pressed) P.mossSoft else P.paper)
            .border(1.dp, if (pressed) P.moss else P.line, shape).clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 2.dp),
    ) { T(text, 11.5f, if (pressed) P.ink else P.muted, bold = pressed) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

@Composable
fun TextBox(
    value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "",
    alignEnd: Boolean = true, keyboard: KeyboardType = KeyboardType.Number, size: Float = 15f, bold: Boolean = false,
) {
    val shape = RoundedCornerShape(6.dp)
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = TextStyle(
            color = P.ink, fontSize = size.sp, textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
            fontWeight = if (bold) FontWeight.SemiBold else null, fontFeatureSettings = "tnum",
        ),
        cursorBrush = SolidColor(P.moss),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().clip(shape).background(P.bg)
                    .border(1.dp, if (focused) P.moss else P.line, shape)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                if (value.isEmpty() && placeholder.isNotEmpty()) T(placeholder, size, P.muted)
                inner()
            }
        },
    )
}

/** 라벨 + 입력 + 힌트 */
@Composable
fun Field(label: String, hint: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        T(label, 13f, weight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        content()
        if (hint != null) { Spacer(Modifier.height(3.dp)); T(hint, 12f, P.muted) }
    }
}

@Composable
fun SelectBox(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    val label = options.firstOrNull { it.first == selected }?.second ?: ""
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().clip(shape).background(P.bg).border(1.dp, P.line, shape)
                .clickable { open = true }.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            T(label, 15f, modifier = Modifier.weight(1f))
            T("▾", 13f, P.muted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (v, l) ->
                DropdownMenuItem(text = { Text(l) }, onClick = { onSelect(v); open = false })
            }
        }
    }
}

/** 웹의 .kv (왼쪽 설명 / 오른쪽 값) */
@Composable
fun KvList(items: List<Pair<String, String>>) {
    if (items.isEmpty()) return
    val line = P.line
    Column(
        Modifier.fillMaxWidth().drawBehind {
            drawLine(line, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        }.padding(top = 12.dp, bottom = 4.dp),
    ) {
        items.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                T(k, 14f, P.muted, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                T(v, 14f, weight = FontWeight.Medium, align = TextAlign.End)
            }
        }
    }
}

/** 웹의 .warn / .warn.bad */
@Composable
fun WarnBox(text: String, bad: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
            .background(if (bad) P.brickSoft else P.ochreSoft).height(IntrinsicSize.Min),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(if (bad) P.brick else P.ochre))
        T(text, 14f, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
    }
}

class Cell(val text: String, val color: Color? = null, val bold: Boolean = false, val italic: Boolean = false)
class TRow(val cells: List<Cell>, val highlight: Boolean = false)

/** 가로로 밀 수 있는 표. 웹의 table 과 같은 모양(머리글 회색 배경, 줄 구분선, 강조 행). */
@Composable
fun DataTable(
    headers: List<String>, rows: List<TRow>, widths: List<Dp>, leftCols: Set<Int> = setOf(0),
    rowHeight: Dp = 32.dp, modifier: Modifier = Modifier,
) {
    val hs = rememberScrollState()
    Column(modifier.horizontalScroll(hs)) {
        Row(Modifier.background(P.bg)) {
            headers.forEachIndexed { i, h ->
                Box(
                    Modifier.width(widths[i]).height(rowHeight).padding(horizontal = 8.dp),
                    contentAlignment = if (i in leftCols) Alignment.CenterStart else Alignment.CenterEnd,
                ) { T(h, 12.5f, P.muted, weight = FontWeight.SemiBold, maxLines = 1) }
            }
        }
        rows.forEach { r ->
            val line = P.line
            Row(
                Modifier.background(if (r.highlight) P.mossSoft else Color.Transparent).drawBehind {
                    drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                },
            ) {
                r.cells.forEachIndexed { i, c ->
                    Box(
                        Modifier.width(widths[i]).height(rowHeight).padding(horizontal = 8.dp),
                        contentAlignment = if (i in leftCols) Alignment.CenterStart else Alignment.CenterEnd,
                    ) { T(c.text, 12.5f, c.color ?: P.ink, bold = r.highlight || c.bold, italic = c.italic, maxLines = 1) }
                }
            }
        }
    }
}

/** 웹의 .btn / .btn.ghost */
@Composable
fun AppButton(text: String, onClick: () -> Unit, ghost: Boolean = false) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier.clip(shape).background(P.paper).border(1.dp, if (ghost) P.line else P.ink, shape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
    ) { T(text, 14f, weight = FontWeight.Medium) }
}

/** 화면 아래쪽에 크게 놓는 주 버튼 (다음 단계, 저장 등) */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier.clip(shape).background(if (enabled) P.moss else P.line)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) { T(text, 16f, if (enabled) P.onMoss else P.muted, bold = true) }
}

/** 화면 위쪽의 "← 뒤로", "홈" 버튼. 갈 곳이 없으면 흐리게 */
@Composable
fun NavButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.alpha(if (enabled) 1f else 0.38f).clip(shape).background(P.paper).border(1.dp, P.line, shape)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp),
    ) { T(text, 13.5f, weight = FontWeight.Medium, maxLines = 1) }
}

/** 설정 톱니바퀴 (글꼴에 상관없이 같은 모양이 나오도록 직접 그림) */
@Composable
fun GearIcon(color: Color, iconSize: Dp = 22.dp) {
    Canvas(Modifier.size(iconSize)) {
        val c = Offset(this.size.width / 2, this.size.height / 2)
        val r = this.size.minDimension / 2
        for (i in 0 until 8) {
            rotate(degrees = i * 45f, pivot = c) {
                drawRoundRect(color, Offset(c.x - r * 0.16f, c.y - r), Size(r * 0.32f, r * 0.42f), CornerRadius(r * 0.06f))
            }
        }
        val ring = Path().apply {
            fillType = PathFillType.EvenOdd
            addOval(Rect(center = c, radius = r * 0.72f))
            addOval(Rect(center = c, radius = r * 0.30f))
        }
        drawPath(ring, color)
    }
}
