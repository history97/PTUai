package kr.landscapebid.engine

/** 마감 전 공고마다 "하한가 이상이 될 확률이 50/70/90% 인 투찰금액" */
class QuickAmounts(val p50: Double, val p70: Double, val p90: Double)

class NoticeItem(val index: Int, val notice: Notice, val past: Boolean, val quick: QuickAmounts?) {
    /** "강남구 · 기초 191,980,000 · A값 있음 · 마감 11-10 14:00" */
    fun meta(): String =
        notice.org.replaceFirst("서울특별시 ", "") + " · 기초 " + Format.won(notice.base) + " · " +
            (if (notice.aValue.isFinite()) "A값 있음" else "A값 없음") + " · 마감 " +
            (notice.deadline?.replace('T', ' ')?.substring(5) ?: "-")
}

/** 공고를 눌렀을 때 계산기 입력칸에 채울 값. 값이 없으면 null / 빈 문자열. */
class NoticeForm(
    val name: String, val base: Double, val aValue: Double?, val estimate: Double?, val netCost: Double?,
    val rate: String, val range: String, val group: String,
)

object NoticeList {
    /** 원본 quickAmt: A값이 있고 하한율을 알 수 있는 공고만 */
    fun quick(ds: Dataset, x: Notice, groups: Set<String>, cache: MutableMap<String, Dist> = HashMap()): QuickAmounts? {
        if (!x.aValue.isFinite()) return null
        val e = if (x.estimate.isFinite() && x.estimate > 0) x.estimate else x.base / 1.1
        val rate = if (x.rate.isFinite() && x.rate > 0) x.rate else (Rates.rateByEst(e) ?: return null)
        val g = Grouping.groupOf(x.org)
        val key = if (g in groups) g else "전체"
        val d = cache.getOrPut(key) { ds.dist(key) }
        fun at(p: Int) = Rates.floorAt(x.base, x.aValue, rate, d.inv(p / 100.0))
        return QuickAmounts(at(50), at(70), at(90))
    }

    /**
     * 화면에 보여줄 목록. now 는 "YYYY-MM-DDTHH:MM" (한국 시간). 마감 빠른 순.
     * 원본은 UTC 시각과 한국 시간 마감을 비교해서 마감 후 9시간 동안 "마감 전"으로 보였는데, 여기서는 한국 시간끼리 비교한다.
     */
    fun items(ds: Dataset, notices: List<Notice>, showAll: Boolean, now: String): List<NoticeItem> {
        val cache = HashMap<String, Dist>()
        val groups = ds.groupCounts().keys
        val pool = notices.withIndex().filter { showAll || it.value.deadline == null || it.value.deadline!! >= now }
        return pool.sortedBy { it.value.deadline ?: "" }.map { (i, x) ->
            NoticeItem(i, x, x.deadline != null && x.deadline < now, quick(ds, x, groups, cache))
        }
    }

    fun openCount(notices: List<Notice>, now: String): Int = notices.count { it.deadline == null || it.deadline >= now }

    /** 원본 applyNotice 의 값 채우기 */
    fun toForm(x: Notice, groups: Set<String>): NoticeForm {
        val g = Grouping.groupOf(x.org)
        return NoticeForm(
            name = x.title, base = x.base,
            aValue = x.aValue.takeIf { it.isFinite() },
            estimate = x.estimate.takeIf { it.isFinite() && it > 0 },
            netCost = x.netCost.takeIf { it.isFinite() && it > 0 },
            rate = if (x.rate.isFinite() && x.rate > 0) Format.jsNumber(JsMath.toFixed(x.rate, 3).toDouble()) else "",
            range = if (x.variation == 2.0 || x.variation == 3.0) Format.jsNumber(x.variation) else "",
            group = if (g in groups) g else "전체",
        )
    }
}
