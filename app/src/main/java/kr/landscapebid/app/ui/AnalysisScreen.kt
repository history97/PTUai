package kr.landscapebid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.landscapebid.app.CalcViewModel
import kr.landscapebid.app.Route
import kr.landscapebid.app.Tab
import kr.landscapebid.engine.CalcInput
import kr.landscapebid.engine.CalcOutcome
import kr.landscapebid.engine.CalcResult
import kr.landscapebid.engine.Calculator
import kr.landscapebid.engine.Dataset
import kr.landscapebid.engine.Format
import kr.landscapebid.engine.JsMath
import kr.landscapebid.engine.PickChart

private fun num(s: String): Double? = JsMath.num(s).let { if (it.isNaN()) null else it }

/**
 * 하한가 분석: 두 단계.
 *  1단계 = 엑셀 또는 직접 입력으로 공고 정보를 채우기
 *  2단계 = 예정 투찰금액, 사정율 차트, 금액 분포 그래프 확인하고 내 금액 저장
 */
@Composable
fun AnalysisScreen(vm: CalcViewModel, ds: Dataset, step: Int, onPickResults: () -> Unit, onPickNotice: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 980.dp
        val narrowChart = maxWidth < 600.dp
        val scroll = rememberScrollState()
        var deckBottom by remember { mutableFloatStateOf(1f) }
        LaunchedEffect(step) { scroll.scrollTo(0) }

        // ---- 입력칸 → 계산 (화면이 바뀌어도 같은 값을 쓴다) ----
        val aVal = JsMath.num(vm.inA).let { if (it.isNaN()) 0.0 else it }
        val pin = num(vm.inP)?.takeIf { it > 0 }
        val input = CalcInput(
            base = JsMath.num(vm.inB), aValue = aVal, estimate = num(vm.inE), netCost = num(vm.inC), group = vm.group,
            rateOverride = num(vm.rateSel), expectedS = JsMath.num(vm.inS), plannedPrice = pin, myAmount = num(vm.inX), probWidth = vm.probW,
        )
        val outcome = remember(ds, input) { Calculator.calculate(ds, input) }
        val dist = remember(ds, vm.group) { ds.dist(vm.group) }
        val pick = remember(ds, vm.group, vm.probW, narrowChart) { PickChart.compute(ds, dist, vm.probW, narrowChart) }
        val result: CalcResult? = (outcome as? CalcOutcome.Ok)?.result
        val rejected: String? = (outcome as? CalcOutcome.Rejected)?.message

        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = if (wide) 32.dp else 16.dp).padding(top = 4.dp, bottom = 72.dp),
        ) {
            Stepper(step)
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = if (step == 1) 720.dp else 980.dp).fillMaxWidth()) {
                    if (step == 1) {
                        DataBar(vm, ds, onPickResults)
                        vm.uploadMsg?.let { WarnBox(it.text, it.bad) }
                        Spacer(Modifier.height(16.dp))
                        InputPanel(vm, ds, dist, result, onPickNotice)
                        Spacer(Modifier.height(16.dp))
                        if (result == null && rejected != null) { T(rejected, 13f, P.brick); Spacer(Modifier.height(8.dp)) }
                        PrimaryButton(
                            "다음: 금액 분포 보기  →", { vm.recordHistory(); vm.navigate(Route(Tab.ANALYSIS, 2)) },
                            Modifier.fillMaxWidth(), enabled = result != null,
                        )
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AppButton("← 입력 수정", { vm.navigate(Route(Tab.ANALYSIS, 1)) }, ghost = true)
                            T(vm.inName.ifBlank { "이름 없는 계산" }, 16f, bold = true, modifier = Modifier.weight(1f), maxLines = 2)
                        }
                        Spacer(Modifier.height(12.dp))
                        if (result == null) {
                            Panel {
                                T(rejected ?: "입력값을 확인해 주세요", 14f, P.brick)
                                Spacer(Modifier.height(10.dp))
                                AppButton("← 입력으로 돌아가기", { vm.navigate(Route(Tab.ANALYSIS, 1)) })
                            }
                        } else {
                            ResultPanel(vm, ds, dist, pick, result, rejected, wide) { deckBottom = it }
                            Spacer(Modifier.height(16.dp))
                            SaveMyAmount(vm, result)
                            Spacer(Modifier.height(24.dp))
                            Footer()
                        }
                    }
                }
            }
        }

        // 위쪽 결과 카드가 화면 밖으로 나가면 아래에 고정 금액 바 (2단계에서만)
        if (step == 2 && result != null && deckBottom < 0f) {
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(P.ink).padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom,
            ) {
                T("예정 투찰금액", 13f, P.paper)
                Spacer(Modifier.width(10.dp))
                T(Format.won(result.bid) + "원", 18f, P.paper, bold = true)
                Spacer(Modifier.width(10.dp))
                T("근접 " + JsMath.toFixed(result.nearProb * 100, 1) + "%", 13f, P.paper)
            }
        }
    }
}

/** "1 입력 › 2 금액 분포" 단계 표시 */
@Composable
private fun Stepper(step: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StepPill(1, "입력", step == 1)
        T("›", 16f, P.muted)
        StepPill(2, "금액 분포", step == 2)
    }
}

@Composable
private fun StepPill(n: Int, label: String, on: Boolean) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.clip(shape).background(if (on) P.moss else P.paper).border(1.dp, if (on) P.moss else P.line, shape)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) { T("$n  $label", 13f, if (on) P.onMoss else P.muted, bold = on) }
}

/** 낙찰 데이터 건수와 엑셀 추가 / 기본 데이터로 버튼 */
@Composable
private fun DataBar(vm: CalcViewModel, ds: Dataset, onPickResults: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val d0 = ds.rows.last().rec.date
    val d1 = ds.rows.first().rec.date
    ChipRow(Modifier.padding(bottom = 10.dp)) {
        Box(Modifier.clip(shape).background(P.paper).border(1.dp, P.line, shape).padding(horizontal = 12.dp, vertical = 5.dp)) {
            T("낙찰 ${ds.rows.size}건 · $d0~$d1" + (if (vm.extra.isNotEmpty()) " (추가 ${vm.extra.size})" else ""), 13f)
        }
        AppButton("인포21C 엑셀 추가", onPickResults, ghost = true)
        AppButton("기본 데이터로", { vm.resetExtra() }, ghost = true)
    }
}

/** 2단계 맨 아래: 지금 고른 사정율·금액을 my페이지에 저장 */
@Composable
private fun SaveMyAmount(vm: CalcViewModel, r: CalcResult) {
    val key = vm.snapshot()?.key
    val saved = vm.favorites.firstOrNull { it.form.key == key && it.bid != null && it.sExp != null }
    Panel {
        T("내 금액 저장", 15f, weight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        T("지금 고른 사정율 ${Format.f3(r.sExp)}% · 투찰금액 ${Format.won(r.bid)}원을 my페이지에 즐겨찾기로 저장합니다.", 13f, P.muted)
        Spacer(Modifier.height(10.dp))
        PrimaryButton(
            if (saved == null) "★ 이 금액으로 즐겨찾기 저장" else "★ 저장된 금액 업데이트",
            { vm.saveMyAmount(r.sExp, r.bid) }, Modifier.fillMaxWidth(),
        )
        if (saved != null) {
            Spacer(Modifier.height(8.dp))
            T("저장됨 (${saved.savedAt}): 사정율 ${Format.f3(saved.sExp!!)}% · 투찰금액 ${Format.won(saved.bid!!)}원", 12.5f, P.moss)
        }
    }
}
