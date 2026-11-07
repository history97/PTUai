package kr.landscapebid.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kr.landscapebid.app.CalcViewModel
import kr.landscapebid.engine.CalcInput
import kr.landscapebid.engine.CalcOutcome
import kr.landscapebid.engine.CalcResult
import kr.landscapebid.engine.Calculator
import kr.landscapebid.engine.Dataset
import kr.landscapebid.engine.Dist
import kr.landscapebid.engine.Format
import kr.landscapebid.engine.JsMath
import kr.landscapebid.engine.PickChart
import kr.landscapebid.engine.PickModel
import kr.landscapebid.engine.PreDistribution
import kr.landscapebid.engine.PreDistributionCalc
import kr.landscapebid.engine.Rates
import kotlin.math.abs
import kotlin.math.max

/* 하한가 분석 화면의 조각들: 입력 패널, 결과 패널(카드·차트·분포·시나리오), 하단 안내. 화면 구성은 AnalysisScreen.kt */

private fun nn(s: String): Double? = JsMath.num(s).let { if (it.isNaN()) null else it }

/* ------------------------------ 왼쪽: 공고 정보 ------------------------------ */

@Composable
internal fun InputPanel(vm: CalcViewModel, ds: Dataset, dist: Dist, r: CalcResult?, onPickNotice: () -> Unit) {
    Panel {
        T("공고 정보", 19f, weight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        NoticeSection(vm, ds, onPickNotice)
        Field("공고명 (선택)") { TextBox(vm.inName, { vm.inName = it }, alignEnd = false, keyboard = KeyboardType.Text, placeholder = "예측 기록에 저장할 이름") }
        Field("기초금액 (원)") { TextBox(vm.inB, { vm.inB = CalcViewModel.money(it); vm.clearCurrentNotice() }) }
        Field("A값 (원)", "국민연금·건강·노인장기요양·퇴직공제 등 합계. 모르면 0 (결과가 약간 달라짐)") { TextBox(vm.inA, { vm.inA = CalcViewModel.money(it); vm.clearCurrentNotice() }) }
        Field("추정가격 (원)", "하한율 구간은 기초금액이 아니라 추정가격 기준") { TextBox(vm.inE, { vm.inE = CalcViewModel.money(it); vm.clearCurrentNotice() }, placeholder = "비우면 기초금액 ÷ 1.1") }
        val groupOptions = ds.groupCounts().map { (g, n) -> g to "$g (${n}건)" }
        val gHint = if (dist.sub != null) "이 기관 ${dist.n}건 + 전체 데이터를 섞어 사용 (기관 비중 ${JsMath.toFixed(dist.wS * 100, 0)}%). 기관별 차이는 통계적으로 뚜렷하지 않음" else "전체 데이터 기준"
        Field("발주기관", gHint) { SelectBox(groupOptions, vm.group, { vm.group = it }) }
        Field("순공사원가 (원, 선택)") { TextBox(vm.inC, { vm.inC = CalcViewModel.money(it); vm.clearCurrentNotice() }, placeholder = "공고에 있으면 입력") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Field("낙찰하한율") {
                    val rateBase = listOf("89.745", "88.745", "87.495", "87.745", "86.745", "85.495")
                    val rateOpts = if (vm.rateSel.isNotEmpty() && vm.rateSel !in rateBase) rateBase + vm.rateSel else rateBase   // 공고의 하한율이 목록에 없으면 추가
                    SelectBox(listOf("" to "자동 (추정가격 기준)") + rateOpts.map { it to it }, vm.rateSel, { vm.rateSel = it })
                }
            }
            Column(Modifier.weight(1f)) {
                Field("예가 범위") { SelectBox(listOf("" to "자동 (기관 기준)", "3" to "±3%", "2" to "±2%"), vm.rangeSel, { vm.rangeSel = it }) }
            }
        }
        if (r != null) KvList(r.summaryItems())
        Spacer(Modifier.height(20.dp))
        T("금액으로 확인", 15f, weight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        T("투찰금액을 넣으면 위 예정가격(예상 또는 직접 입력) 기준 투찰율을 계산합니다. 인포21C처럼 소수 4자리 버림으로 표시.", 13f, P.muted)
        Spacer(Modifier.height(10.dp))
        Field("투찰금액 (원)") { TextBox(vm.inX, { vm.inX = CalcViewModel.money(it) }, placeholder = "예: 191,980,000") }
        if (r != null) KvList(r.reviewItems())
    }
}

/* ------------------------------ 오른쪽: 결과 ------------------------------ */

@Composable
internal fun ResultPanel(
    vm: CalcViewModel, ds: Dataset, dist: Dist, pick: PickModel, r: CalcResult?, rejected: String?, wide: Boolean,
    onDeckBottom: (Float) -> Unit,
) {
    Panel(padding = if (wide) 24.dp else 14.dp) {
        Deck(vm, ds, dist, pick, r, rejected, wide, onDeckBottom)
        if (r != null) {
            r.netCostLimit?.let { lim ->
                if (r.belowNetCostLimit == true) WarnBox("투찰금액이 순공사원가의 98%(${Format.won(lim)}원)보다 낮습니다. 낙찰 제외 대상이 될 수 있으니 공고문 기준을 확인하세요.", bad = true)
                else WarnBox("순공사원가 98%(${Format.won(lim)}원)보다 높습니다. 단, 정확한 판정 방식(항목별 비교 여부)은 확인이 필요합니다.")
            }
            if (r.aValueMissing) WarnBox("A값이 0이면 하한가가 실제와 달라집니다. 공고문의 A값을 넣는 것을 권장합니다.")
            Facts(r)
            PreSection(vm, ds, dist, r, wide)
            Spacer(Modifier.height(24.dp))
            T("실제 사정율에 따라 내 금액이 어떻게 되나", 15f, weight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            ScenarioTable(r)
        }
    }
}

@Composable
private fun Deck(
    vm: CalcViewModel, ds: Dataset, dist: Dist, pick: PickModel, r: CalcResult?, rejected: String?, wide: Boolean,
    onDeckBottom: (Float) -> Unit,
) {
    val ps = vm.previewS
    val preview = r != null && ps != null
    val shape = RoundedCornerShape(12.dp)
    val accent = if (preview) P.ochre else P.ink
    val bigColor = if (preview) P.ochre else P.ink
    val probColor = if (preview) P.ochre else P.moss
    var outer = Modifier.fillMaxWidth().onGloballyPositioned { onDeckBottom(it.positionInRoot().y + it.size.height) }
    outer = if (preview) outer.border(2.dp, P.ochreSoft, shape).padding(2.dp).border(1.dp, P.ochre, shape) else outer.border(1.dp, P.line, shape)
    Column(outer.clip(shape).background(P.paper).padding(horizontal = if (wide) 18.dp else 10.dp, vertical = if (wide) 16.dp else 12.dp)) {
        // ① 금액 + 확률
        var bidText = "-"
        var meta: AnnotatedString = AnnotatedString(rejected ?: "")
        var probText = "-"
        if (r != null) {
            if (preview) {
                val s = ps!!
                bidText = Format.won(Rates.floorAt(r.b, r.a, r.rate, s))
                meta = AnnotatedString("미리보기 · 사정율 ${Format.f3(s)}% · 예정가격 ${Format.won(r.b * (1 + s / 100))}원 · 손 떼면 선택")
                probText = JsMath.toFixed((dist.cdf(s + vm.probW) - dist.cdf(s - vm.probW)) * 100, 1) + "%"
            } else {
                bidText = Format.won(r.bid)
                meta = buildAnnotatedString {
                    append("사정율 ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(Format.f3(r.sExp) + "%") }
                    append(" · ${if (r.input.plannedPrice != null && r.input.plannedPrice!! > 0) "예정가격" else "예상 예정가격"} ${Format.won(r.plannedPrice)}원 · 투찰/기초 ${Format.trunc3(r.bidRatioToBase)}%")
                }
                probText = JsMath.toFixed(r.nearProb * 100, 1) + "%"
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                T(if (wide) "예정 투찰금액 (그 사정율일 때 낙찰하한가)" else "예정 투찰금액", 13f, P.muted)
                Row(verticalAlignment = Alignment.Bottom) {
                    T(bidText, if (wide) 44f else 27f, bigColor, bold = true, maxLines = 1)
                    if (r != null) { Spacer(Modifier.width(4.dp)); T("원", if (wide) 20f else 15f, bigColor, weight = FontWeight.Medium) }
                }
                TA(meta, if (wide) 14f else 12.5f, P.muted)
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(0.01 to "±0.01%", 0.05 to "±0.05%", 0.1 to "±0.1%").forEach { (w, label) ->
                        MiniChip(label, abs(vm.probW - w) < 1e-9) { vm.probW = w }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Column(horizontalAlignment = Alignment.End) {
                        T("빈도 확률", 12f, P.muted, maxLines = 1)
                        T(probText, if (wide) 26f else 24f, probColor, bold = true, maxLines = 1)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        T("업체 몰림 확률", 12f, P.muted, maxLines = 1)
                        T("데이터 필요", if (wide) 15f else 13f, P.muted, weight = FontWeight.Medium, italic = true, maxLines = 1)
                    }
                }
            }
        }
        if (r == null) return@Column

        // ② 사정율 입력 + 슬라이더
        val shownS = if (r.input.plannedPrice != null && r.input.plannedPrice!! > 0) CalcViewModel.fmtS(r.sExp) else vm.inS
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("사정율", 15f, bold = true)
            Spacer(Modifier.width(12.dp))
            TextBox(shownS, { vm.inS = it; vm.inP = ""; vm.previewS = null }, Modifier.width(120.dp), keyboard = KeyboardType.Decimal, size = 17f, bold = true)
            Spacer(Modifier.width(4.dp))
            T("%", 15f)
        }
        val sliderV = JsMath.num(shownS).let { if (it.isFinite()) it.toFloat().coerceIn(-3f, 3f) else 0f }
        Slider(
            value = sliderV, onValueChange = { vm.setS(it.toDouble()) }, valueRange = -3f..3f,
            colors = SliderDefaults.colors(thumbColor = P.moss, activeTrackColor = P.moss, inactiveTrackColor = P.line),
        )

        // ③ 사정율 선택 차트
        PickChartCanvas(
            model = pick, b = r.b, a = r.a, rate = r.rate, sExp = r.sExp, probW = vm.probW, previewS = vm.previewS,
            heightDp = if (wide) 330 else 300, onPreview = { vm.previewS = it }, onCommit = { vm.setS(it) },
        )
        Spacer(Modifier.height(4.dp))
        T("차트를 좌우로 끌면 위 금액이 미리보기로 바뀌고, 손 떼면 선택됨 · 막대 = 과거에 나온 횟수 · 빈도 확률 = 실제 사정율이 내 사정율 ±오차범위 안에 떨어질 확률 · 업체 몰림 확률 = 그 범위 안에서 과거 참가업체들이 써낸 비율(참가업체 데이터 필요)", 12.5f, P.muted)
        Spacer(Modifier.height(8.dp))
        fun r4(v: Double) = JsMath.round(v * 10000) / 10000
        ChipRow {
            Chip("최빈", false, { vm.setS(r4(pick.modeS)) }, borderColor = MarkMode.copy(alpha = .33f), size = 13f, hPad = 12.dp, vPad = 6.dp, boldPart = Format.won(Rates.floorAt(r.b, r.a, r.rate, r4(pick.modeS))))
            Chip("중앙값", false, { vm.setS(r4(pick.median)) }, borderColor = P.moss, size = 13f, hPad = 12.dp, vPad = 6.dp, boldPart = Format.won(Rates.floorAt(r.b, r.a, r.rate, r4(pick.median))))
            Chip("평균", false, { vm.setS(r4(pick.mean)) }, borderColor = MarkMean.copy(alpha = .33f), size = 13f, hPad = 12.dp, vPad = 6.dp, boldPart = Format.won(Rates.floorAt(r.b, r.a, r.rate, r4(pick.mean))))
        }

        // ④ 빠른 선택 칩
        Spacer(Modifier.height(6.dp))
        val curS = JsMath.num(vm.inS)
        ChipRow {
            ds.sChips(dist, vm.group).forEach { c ->
                val label = c.label + (if (c.label.contains("0%")) "" else " " + Format.f3(c.value) + "%")
                Chip(label, abs(c.value - curS) < 0.00005, { vm.setS(JsMath.toFixed(c.value, 4).toDouble()) })
            }
        }

        // ⑤ 개찰 후 검증
        var open by remember { mutableStateOf(false) }
        Spacer(Modifier.height(12.dp))
        T((if (open) "▾ " else "▸ ") + "개찰 후 검증: 예정가격 직접 입력", 13.5f, P.muted, modifier = Modifier.clickable { open = !open }.padding(vertical = 6.dp))
        if (open) {
            T("예정가격을 넣으면 사정율이 자동 계산됨", 13f)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextBox(vm.inP, { vm.inP = CalcViewModel.money(it) }, Modifier.width(190.dp), placeholder = "예정가격", size = 15f)
                Spacer(Modifier.width(4.dp))
                T("원", 15f)
            }
        }
    }
}

@Composable
private fun Facts(r: CalcResult) {
    val bt = r.backtestAll
    val g = r.backtestGroup
    val cells = listOf(
        Triple("${bt.win}건", "과거 ${bt.n}건에 같은 예상 사정율 적용 시 1순위보다 낮고 하한가 이상", false),
        Triple("${bt.low}건", "하한가 미달로 탈락 (실제 사정율이 예상보다 높았음)", true),
        Triple("${bt.high}건", "하한가는 넘었지만 1순위보다 높아서 밀림", false),
        Triple(if (g != null) "${g.win}/${g.n}" else "-", if (g != null) "선택 기관에서 1순위보다 유리했던 건" else "기관 선택 시 표시", false),
    )
    val line = P.line
    Spacer(Modifier.height(16.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).border(1.dp, line, RoundedCornerShape(8.dp))) {
        cells.chunked(2).forEachIndexed { ri, row ->
            Row(Modifier.fillMaxWidth().drawBehind { if (ri == 0) drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx()) }) {
                row.forEachIndexed { ci, (big, sub, bad) ->
                    Column(
                        Modifier.weight(1f).drawBehind { if (ci == 0) drawLine(line, Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth = 1.dp.toPx()) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        T(big, 19f, if (bad) P.brick else P.ink, bold = true)
                        T(sub, 12f, P.muted)
                    }
                }
            }
        }
    }
}

/* ------------------------------ 예정 투찰금액 분포 ------------------------------ */

@Composable
private fun PreSection(vm: CalcViewModel, ds: Dataset, dist: Dist, r: CalcResult, wide: Boolean) {
    val pre = remember(ds, r.b, r.a, r.rate, vm.group, vm.rangeSel, vm.unit) {
        PreDistributionCalc.compute(ds, dist, r.b, r.a, r.rate, vm.group, nn(vm.rangeSel), vm.unit)
    }
    var selected by remember(pre) { mutableStateOf<Int?>(null) }
    Spacer(Modifier.height(24.dp))
    T("예정 투찰금액 분포", 15f, weight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    T("예정가격은 기초금액 ±3% 안에서 만든 예비가격 15개 중 4개를 뽑아 평균낸 값입니다. 예정가격이 정해지면 낙찰하한가(= 써야 하는 최소 투찰금액)가 정해지므로, 예정가격 분포를 투찰금액으로 바꿔서 보여줍니다.", 13.5f, P.muted)
    Spacer(Modifier.height(6.dp))
    T("A 실제 = 과거 공고의 실제 사정율을 이번 기초금액에 적용 · B 단순 이론 = 범위 안에서 15개를 완전 무작위로 만들고 4개 추첨 · C 보정 이론 = 아래 8개·위 7개 구간에서 하나씩 만들고 4개 추첨 (과거 서울시 기관 데이터와 가장 잘 맞은 방식)", 13.5f, P.muted)
    Spacer(Modifier.height(12.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).border(1.dp, P.line, RoundedCornerShape(8.dp)).padding(horizontal = if (wide) 14.dp else 8.dp, vertical = 12.dp)) {
        ChipRow {
            Chip("10만원 단위", vm.unit == 100000, { vm.unit = 100000 })
            Chip("1만원 단위", vm.unit == 10000, { vm.unit = 10000 })
        }
        Spacer(Modifier.height(6.dp))
        T(pre.note, 13f, P.muted)
        Spacer(Modifier.height(8.dp))

        val statRows = (pre.rows + pre.cPlannedRow).mapIndexed { i, row ->
            val muted = i == 3
            val col = if (muted) P.muted else null
            TRow(listOf(Cell(row.name, col)) + row.quantiles.map { Cell(Format.won(it), col) } + Cell(Format.won(row.mean), col))
        }
        DataTable(
            listOf("방법", "10%", "25%", "50%", "75%", "90%", "평균"), statRows, widths = List(7) { if (it == 0) 120.dp else 104.dp }, leftCols = setOf(0),
        )
        Spacer(Modifier.height(12.dp))
        PreChartCanvas(pre, selected, { selected = it }, heightDp = if (wide) 320 else 300)
        selected?.let { i ->
            T(
                "투찰금액 ${pre.binRange(i)}원 · A 실제: ${JsMath.toFixed(pre.hA[i] * 100, 2)}% · B 단순 이론: ${JsMath.toFixed(pre.hB[i] * 100, 2)}% · C 보정 이론: ${JsMath.toFixed(pre.hC[i] * 100, 2)}%",
                12.5f, P.muted,
            )
        }
        Spacer(Modifier.height(10.dp))
        val rowH = 32.dp
        val vs = rememberScrollState()
        val rowPx = with(LocalDensity.current) { rowH.toPx() }
        val halfViewPx = with(LocalDensity.current) { 160.dp.toPx() }
        LaunchedEffect(pre) { vs.scrollTo(max(0f, (pre.medianBin + 1) * rowPx - halfViewPx).toInt()) }
        val tblRows = (0 until pre.bins).map { i ->
            TRow(
                listOf(
                    Cell(pre.binRange(i)), Cell(JsMath.toFixed(pre.hA[i] * 100, 1) + "%"),
                    Cell(JsMath.toFixed(pre.hB[i] * 100, 1) + "%"), Cell(JsMath.toFixed(pre.hC[i] * 100, 1) + "%"),
                ),
                highlight = i == pre.medianBin,
            )
        }
        Column(Modifier.fillMaxWidth().height(320.dp).verticalScroll(vs)) {
            DataTable(listOf("투찰금액 구간(원)", "A 실제", "B 단순", "C 보정"), tblRows, widths = listOf(210.dp, 76.dp, 76.dp, 76.dp), leftCols = setOf(0), rowHeight = rowH)
        }
    }
}

@Composable
private fun ScenarioTable(r: CalcResult) {
    val rows = r.scenarios.map { row ->
        TRow(
            listOf(
                Cell(Format.f3(row.s) + "%"), Cell(Format.won(row.plannedPrice)), Cell(Format.won(row.floorPrice)),
                Cell((if (row.diff >= 0) "+" else "") + Format.won(row.diff)),
                Cell(if (row.passes) "하한가 이상" else "미달 탈락", if (row.passes) P.moss else P.brick, bold = true),
            ),
            highlight = row.isExpected,
        )
    }
    DataTable(listOf("실제 사정율", "예정가격", "낙찰하한가", "내 금액 − 하한가", "결과"), rows, widths = listOf(96.dp, 120.dp, 120.dp, 130.dp, 100.dp), leftCols = setOf(0, 4))
}

/* ------------------------------ 맨 아래: 계산 기준과 한계 ------------------------------ */

@Composable
internal fun Footer() {
    val line = P.line
    Column(Modifier.fillMaxWidth().drawBehind { drawLine(line, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }.padding(top = 16.dp)) {
        T("계산 기준과 한계", 13f, P.muted, bold = true)
        Spacer(Modifier.height(6.dp))
        listOf(
            "투찰율은 인포21C 표시와 맞추기 위해 소수 4자리 버림으로 표시(예: 89.74998 → 89.7499). 계산 자체는 버림 전 값으로 함.",
            "낙찰하한가 = (예정가격 − A값) × 낙찰하한율 + A값, 원 미만 절상. 기본 데이터 395건 모두 이 식으로 공고 하한가와 1원 이내 일치.",
            "낙찰하한율은 2026.1.30 개정 기준(추정가격 10억 미만 89.745%, 10~50억 88.745%, 50~100억 87.495%). 추가한 엑셀은 공고의 실제 하한가에서 하한율을 역산해 사용.",
            "예정가격(사정율)은 개찰 때 복수예비가격 추첨으로 정해지는 값이라 미리 알 수 없음. 이 도구는 \"맞히기\"가 아니라 과거 분포로 확률과 위험을 보여주는 용도.",
            "투찰율 = (투찰금액 − A값) ÷ (예정가격 − A값). 개찰 전에는 예정가격을 모르므로 예상 사정율로 추정한 예정가격에 목표 투찰율을 곱해 금액을 정함.",
            "예정가격 분포 C(보정 이론)는 기초금액 −3~0%를 8구간, 0~+3%를 7구간으로 나눠 구간마다 1개씩 예비가격을 만들고 4개를 무작위 추첨하는 방식. 과거 서울시 기관 2640건과 비교해 가장 잘 맞았음(KS 검정 p≈0.76). 실제 나라장터 내부 생성 방식과 같은지는 확인되지 않음.",
            "공고 목록·예측 기록은 이 브라우저에만 저장됨. 다른 기기로 옮기려면 \"내 예측 기록\"의 기록 내보내기/가져오기를 사용.",
            "공고 목록의 50/70/90% 금액 = 하한가 이상일 확률이 그만큼 되는 예정 투찰금액(과거 사정율 분포 기준).",
            "1순위는 하한가 이상 중 최저가. 다른 업체들이 어디에 몰리는지(경쟁 분포)는 데이터에 없어서 1순위 확률은 계산하지 않음.",
            "적격심사(이행능력 점수), 순공사원가 98% 조건의 정확한 판정식은 반영하지 않음. 순공사원가 경고는 \"투찰금액 ≥ 순공사원가 × 98%\" 단순 비교이며 추가 확인 필요.",
            "데이터: 인포21C 낙찰정보(서울, 조경식재·시설물 업종), 2026.03~09 개찰분. 실제 투찰 전 공고문 원본을 반드시 확인하세요.",
        ).forEach { Row(Modifier.padding(bottom = 4.dp)) { T("• ", 13f, P.muted); T(it, 13f, P.muted) } }
    }
}
