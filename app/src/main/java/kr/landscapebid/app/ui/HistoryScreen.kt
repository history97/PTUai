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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.landscapebid.app.CalcViewModel

/** 검색 이력: 분석했거나 열어 본 공고. 누르면 그 값으로 다시 분석 2단계를 연다. */
@Composable
fun HistoryScreen(vm: CalcViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 32.dp)) {
        if (vm.history.isEmpty()) {
            Panel {
                T("검색 이력이 없어요", 16f, bold = true)
                Spacer(Modifier.height(6.dp))
                T("최근 공고에서 공고를 열거나 하한가 분석에서 \"다음\"을 누르면 여기에 남아요.", 13.5f, P.muted)
            }
            return@Column
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            T("${vm.history.size}건 · 최근 순", 13f, P.muted)
            AppButton("전체 삭제", { vm.clearHistory() }, ghost = true)
        }
        Spacer(Modifier.height(10.dp))
        vm.history.forEach { item ->
            Panel(Modifier.clickable { vm.openHistory(item) }, padding = 14.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        T(item.at, 12f, P.muted)
                        T(item.form.name, 15f, weight = FontWeight.SemiBold)
                        T(item.form.meta(), 12.5f, P.muted)
                    }
                    T("삭제", 13f, P.brick, modifier = Modifier.clickable { vm.removeHistory(item.id) }.padding(4.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
