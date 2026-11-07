package kr.landscapebid.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import kr.landscapebid.app.CalcViewModel
import kr.landscapebid.app.Tab
import kr.landscapebid.engine.Dataset

/**
 * 앱의 뼈대: 맨 위 탭 4개 + 톱니바퀴, 그 아래 "← 뒤로 / 홈" 줄, 나머지가 화면 본문.
 * 안드로이드 기본 뒤로가기 버튼도 같은 이동 기록을 따른다.
 */
@Composable
fun AppScaffold(vm: CalcViewModel) {
    BackHandler(enabled = vm.canGoBack) { vm.back() }
    val ds = vm.dataset
    val err = vm.loadError
    Column(Modifier.fillMaxSize().background(P.bg).systemBarsPadding().imePadding()) {
        TopTabs(vm)
        // [비활성화] "← 뒤로 / 홈" 줄. 다시 쓰려면 아래 한 줄의 주석(//)과 맨 아래 PageBar 함수의 블록 주석(/* */)을 풀면 된다.
        // PageBar(vm)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                err != null -> Box(Modifier.fillMaxSize().padding(24.dp)) { T("데이터를 불러오지 못했습니다: $err", 15f, P.brick) }
                ds == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { T("데이터 불러오는 중", 15f, P.muted) }
                else -> Screens(vm, ds)
            }
        }
    }
}

@Composable
private fun Screens(vm: CalcViewModel, ds: Dataset) {
    // 엑셀 파일 고르기 (낙찰정보 추가 / 공고 목록)
    val mimes = arrayOf(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel",
        "text/csv", "text/comma-separated-values", "application/octet-stream",
    )
    val pickResults = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> vm.importResults(uris) }
    val pickNotice = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importNotices(uri) }
    val onResults: () -> Unit = { pickResults.launch(mimes) }
    val onNotice: () -> Unit = { pickNotice.launch(mimes) }
    val r = vm.route
    when (r.tab) {
        Tab.RECENT -> RecentScreen(vm, ds, onNotice)
        Tab.ANALYSIS -> AnalysisScreen(vm, ds, r.step, onResults, onNotice)
        Tab.MY -> MyScreen(vm, ds)
        Tab.HISTORY -> HistoryScreen(vm)
        Tab.SETTINGS -> SettingsScreen(vm, ds)
    }
}

@Composable
private fun TopTabs(vm: CalcViewModel) {
    val line = P.line
    val moss = P.moss
    Row(
        Modifier.fillMaxWidth().background(P.paper).drawBehind { drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx()) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(Tab.RECENT, Tab.ANALYSIS, Tab.MY, Tab.HISTORY).forEach { tab ->
            val sel = vm.route.tab == tab
            Box(
                Modifier.weight(1f).clickable { vm.selectTab(tab) }
                    .drawBehind { if (sel) drawRect(moss, Offset(0f, size.height - 3.dp.toPx()), Size(size.width, 3.dp.toPx())) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) { T(tab.label, 13f, if (sel) P.ink else P.muted, bold = sel, maxLines = 1) }
        }
        Box(Modifier.size(46.dp).clickable { vm.selectTab(Tab.SETTINGS) }, contentAlignment = Alignment.Center) {
            GearIcon(if (vm.route.tab == Tab.SETTINGS) P.moss else P.muted, 22.dp)
        }
    }
}

/* [비활성화] "← 뒤로 / 홈" 줄 (제목 표시 포함). 위 PageBar(vm) 호출과 함께 풀면 다시 나타난다.
   안드로이드 기본 뒤로가기(BackHandler)는 그대로 동작한다.

@Composable
private fun PageBar(vm: CalcViewModel) {
    val r = vm.route
    val title = when (r.tab) {
        Tab.RECENT -> "최근 공고"
        Tab.ANALYSIS -> if (r.step == 1) "하한가 분석 · 1단계 입력" else "하한가 분석 · 2단계 금액 분포"
        Tab.MY -> "my페이지"
        Tab.HISTORY -> "검색 이력"
        Tab.SETTINGS -> "설정"
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NavButton("← 뒤로", vm.canGoBack) { vm.back() }
        NavButton("홈", !vm.isHome) { vm.home() }
        T(title, 14f, P.muted, modifier = Modifier.weight(1f).padding(start = 4.dp), maxLines = 1)
    }
}
*/
