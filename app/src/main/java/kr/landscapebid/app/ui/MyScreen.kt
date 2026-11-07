package kr.landscapebid.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.landscapebid.app.CalcViewModel
import kr.landscapebid.app.data.SavedItem
import kr.landscapebid.engine.CalcInput
import kr.landscapebid.engine.CalcOutcome
import kr.landscapebid.engine.Calculator
import kr.landscapebid.engine.Dataset
import kr.landscapebid.engine.Format
import kr.landscapebid.engine.JsMath

/** my페이지: 즐겨찾기한 공고와, 그 공고에 내가 정해 둔 사정율·투찰금액 */
@Composable
fun MyScreen(vm: CalcViewModel, ds: Dataset) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 32.dp)) {
        if (vm.favorites.isEmpty()) {
            Panel {
                T("즐겨찾기한 공고가 없어요", 16f, bold = true)
                Spacer(Modifier.height(6.dp))
                T("최근 공고에서 ☆ 를 누르거나, 하한가 분석 2단계에서 \"이 금액으로 즐겨찾기 저장\"을 누르면 여기에 모여요.", 13.5f, P.muted)
                Spacer(Modifier.height(12.dp))
                AppButton("최근 공고 보러가기", { vm.home() })
            }
            return@Column
        }
        T("즐겨찾기 ${vm.favorites.size}건 · 눌러서 다시 분석", 13f, P.muted)
        Spacer(Modifier.height(10.dp))
        vm.favorites.forEach { item ->
            FavoriteCard(vm, ds, item)
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun FavoriteCard(vm: CalcViewModel, ds: Dataset, item: SavedItem) {
    val f = item.form
    // 저장해 둔 사정율로 다시 계산해서 "지금 데이터 기준" 확률을 보여준다
    val res = remember(ds, item) {
        val s = item.sExp
        if (s == null) null else (Calculator.calculate(
            ds,
            CalcInput(
                base = f.base, aValue = f.aValue ?: 0.0, estimate = f.estimate, netCost = f.netCost, group = f.group,
                rateOverride = JsMath.num(f.rate).let { if (it.isNaN()) null else it }, expectedS = s,
            ),
        ) as? CalcOutcome.Ok)?.result
    }
    Panel(Modifier.clickable { vm.openFavorite(item) }, padding = 14.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                T(f.name, 15f, weight = FontWeight.SemiBold)
                T(f.meta(), 12.5f, P.muted)
            }
            T("삭제", 13f, P.brick, modifier = Modifier.clickable { vm.removeFavorite(item.id) }.padding(4.dp))
        }
        Spacer(Modifier.height(10.dp))
        if (item.bid != null && item.sExp != null) {
            T("내가 정한 금액", 12.5f, P.muted)
            T(Format.won(item.bid) + "원", 22f, P.moss, bold = true)
            T("사정율 ${Format.f3(item.sExp)}% · 저장 ${item.savedAt}", 12.5f, P.muted)
            if (res != null) {
                Spacer(Modifier.height(6.dp))
                T("지금 데이터 기준 하한가 이상일 확률 ${JsMath.toFixed(res.pBelow * 100, 0)}% · 근접 ${JsMath.toFixed(res.nearProb * 100, 1)}%", 13f)
            }
        } else {
            T("아직 정한 금액이 없어요. 눌러서 분석하고 금액을 저장하세요.", 13f, P.muted)
        }
    }
}
