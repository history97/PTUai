package kr.landscapebid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kr.landscapebid.app.CalcViewModel
import kr.landscapebid.engine.Dataset
import kr.landscapebid.engine.Format
import kr.landscapebid.engine.Grouping
import kr.landscapebid.engine.NoticeItem
import kr.landscapebid.engine.NoticeList

/**
 * 최근 공고: 불러온 공고 목록을 검색어 + 카테고리(발주기관 분류)로 걸러서 보여준다.
 * 공고를 누르면 값을 채워서 하한가 분석 2단계로, ☆ 를 누르면 즐겨찾기.
 * (지금은 엑셀로 불러온 목록이 대상. 나중에 나라장터 API 를 붙이면 이 목록이 자동으로 채워진다.)
 */
@Composable
fun RecentScreen(vm: CalcViewModel, ds: Dataset, onPickNotice: () -> Unit) {
    val now = remember(vm.notices) { nowKstIso() }
    val base = remember(ds, vm.notices, vm.recentOpenOnly, now) { NoticeList.items(ds, vm.notices, !vm.recentOpenOnly, now) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 32.dp)) {
        vm.uploadMsg?.let { WarnBox(it.text, it.bad); Spacer(Modifier.height(10.dp)) }

        if (vm.notices.isEmpty()) {
            Panel {
                T("불러온 공고가 없어요", 16f, bold = true)
                Spacer(Modifier.height(6.dp))
                T("인포21C 입찰리스트 엑셀을 불러오면 마감 전 공고가 여기에 모여요. 공고를 누르면 하한가 분석으로 바로 넘어갑니다.", 13.5f, P.muted)
                Spacer(Modifier.height(12.dp))
                AppButton("공고 목록 불러오기", onPickNotice)
            }
            return@Column
        }

        // 검색
        TextBox(vm.recentQuery, { vm.recentQuery = it }, alignEnd = false, keyboard = KeyboardType.Text, placeholder = "공고명 · 기관 · 공고번호 검색")
        Spacer(Modifier.height(10.dp))

        // 카테고리: 발주기관 분류(구 / 공사·공단 / 교육청·학교 …) 중 공고가 있는 것만
        val counts = HashMap<String, Int>()
        base.forEach { val g = Grouping.groupOf(it.notice.org); counts[g] = (counts[g] ?: 0) + 1 }
        val cats = listOf("전체" to base.size) + (Grouping.GU + Grouping.OTHER_GROUPS).filter { (counts[it] ?: 0) > 0 }.map { it to counts[it]!! }
        val cat = if (cats.any { it.first == vm.recentCategory }) vm.recentCategory else "전체"
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            cats.forEach { (c, n) -> Chip("$c $n", c == cat, { vm.recentCategory = c }) }
        }
        Spacer(Modifier.height(10.dp))

        val q = vm.recentQuery.trim()
        val shown = base.filter { item ->
            val x = item.notice
            (cat == "전체" || Grouping.groupOf(x.org) == cat) &&
                (q.isEmpty() || x.name.contains(q, ignoreCase = true) || x.org.contains(q, ignoreCase = true) || x.no.contains(q, ignoreCase = true))
        }

        // 목록 머리줄: 마감 전만 보기 + 건수 + 새 엑셀
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.clickable { vm.recentOpenOnly = !vm.recentOpenOnly }, verticalAlignment = Alignment.CenterVertically) {
                val on = vm.recentOpenOnly
                val box = RoundedCornerShape(4.dp)
                Box(Modifier.size(18.dp).clip(box).background(if (on) P.moss else P.paper).border(1.dp, if (on) P.moss else P.muted, box), contentAlignment = Alignment.Center) {
                    if (on) T("✓", 12f, P.onMoss, bold = true)
                }
                Spacer(Modifier.width(6.dp))
                T("마감 전 공고만", 13f)
            }
            T("${shown.size}건 · 마감 임박순", 12.5f, P.muted)
        }
        Spacer(Modifier.height(10.dp))

        if (shown.isEmpty()) {
            T("조건에 맞는 공고가 없습니다. 검색어나 카테고리를 바꿔 보세요.", 13.5f, P.muted)
        }
        shown.forEach { item ->
            RecentRow(item, vm.isFavorite(item.notice), { vm.openNoticeInAnalysis(item.notice) }, { vm.toggleFavorite(item.notice) })
        }
        Spacer(Modifier.height(8.dp))
        AppButton("공고 목록 다시 불러오기", onPickNotice, ghost = true)
    }
}

@Composable
private fun RecentRow(item: NoticeItem, favorite: Boolean, onOpen: () -> Unit, onStar: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(shape).background(P.paper).border(1.dp, P.line, shape),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f).alpha(if (item.past) 0.55f else 1f).clickable(onClick = onOpen).padding(start = 12.dp, top = 10.dp, bottom = 10.dp)) {
            T(item.notice.title, 14f, weight = FontWeight.SemiBold)
            T(item.meta(), 12.5f, P.muted)
            item.quick?.let { q ->
                TA(
                    buildAnnotatedString {
                        val b = SpanStyle(fontWeight = FontWeight.SemiBold)
                        append("50% "); withStyle(b) { append(Format.won(q.p50)) }
                        append(" · 70% "); withStyle(b) { append(Format.won(q.p70)) }
                        append(" · 90% "); withStyle(b) { append(Format.won(q.p90)) }
                    },
                    12.5f,
                )
            }
        }
        Box(Modifier.size(48.dp).clickable(onClick = onStar), contentAlignment = Alignment.Center) {
            T(if (favorite) "★" else "☆", 22f, if (favorite) P.ochre else P.muted)
        }
    }
}
