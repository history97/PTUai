package kr.landscapebid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kr.landscapebid.app.CalcViewModel
import kr.landscapebid.engine.Dataset
import kr.landscapebid.engine.Format
import kr.landscapebid.engine.NoticeItem
import kr.landscapebid.engine.NoticeList
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 공고 목록 불러오기 버튼 + 공고 목록 상자 (웹의 .loadrow / .noticebox) */
@Composable
fun NoticeSection(vm: CalcViewModel, ds: Dataset, onPick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        AppButton("공고 목록 불러오기", onPick)
        Spacer(Modifier.width(10.dp))
        T("인포21C 입찰리스트 엑셀을 올리면 공고를 눌러 바로 채울 수 있음", 12.5f, P.muted, modifier = Modifier.weight(1f))
    }
    if (vm.notices.isEmpty()) return

    // 마감 시각은 한국 시간으로 적혀 있으니 한국 시간 현재와 비교한다
    val now = remember(vm.notices) { LocalDateTime.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")) }
    val items = remember(ds, vm.notices, vm.showAllNotices, now) { NoticeList.items(ds, vm.notices, vm.showAllNotices, now) }
    val open = remember(vm.notices, now) { NoticeList.openCount(vm.notices, now) }
    val shape = RoundedCornerShape(8.dp)
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp).clip(shape).border(1.dp, P.line, shape)) {
        Row(
            Modifier.fillMaxWidth().background(P.bg).padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            T("공고 ${vm.notices.size}건 중 마감 전 ${open}건", 13f, bold = true, modifier = Modifier.weight(1f))
            Row(Modifier.clickable { vm.showAllNotices = !vm.showAllNotices }, verticalAlignment = Alignment.CenterVertically) {
                val on = vm.showAllNotices
                val box = RoundedCornerShape(4.dp)
                Box(
                    Modifier.size(18.dp).clip(box).background(if (on) P.moss else P.paper).border(1.dp, if (on) P.moss else P.muted, box),
                    contentAlignment = Alignment.Center,
                ) { if (on) T("✓", 12f, P.onMoss, bold = true) }
                Spacer(Modifier.width(6.dp))
                T("마감 지난 것도 보기", 12.5f)
            }
        }
        Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
            if (items.isEmpty()) T("마감 전 공고가 없습니다. \"마감 지난 것도 보기\"를 켜세요.", 12.5f, P.muted, modifier = Modifier.padding(10.dp))
            items.forEach { item -> NoticeRow(item, item.notice.no.isNotEmpty() && item.notice.no == vm.curNoticeNo) { vm.applyNotice(item.notice) } }
        }
    }
}

@Composable
private fun NoticeRow(item: NoticeItem, selected: Boolean, onClick: () -> Unit) {
    val line = P.line
    Column(
        Modifier.fillMaxWidth().alpha(if (item.past) 0.55f else 1f).background(if (selected) P.mossSoft else P.paper)
            .clickable(onClick = onClick)
            .drawBehind { drawLine(line, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        T(item.notice.title, 13f, weight = FontWeight.SemiBold)
        T(item.meta(), 12f, P.muted)
        item.quick?.let { q ->
            TA(
                buildAnnotatedString {
                    val b = SpanStyle(fontWeight = FontWeight.SemiBold)
                    append("50% "); withStyle(b) { append(Format.won(q.p50)) }
                    append(" · 70% "); withStyle(b) { append(Format.won(q.p70)) }
                    append(" · 90% "); withStyle(b) { append(Format.won(q.p90)) }
                },
                12f,
            )
        }
    }
}
