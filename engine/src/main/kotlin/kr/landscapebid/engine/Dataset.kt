package kr.landscapebid.engine

/** 하한율·사정율 분포 (기관 데이터 + 전체 데이터를 가중 혼합). 원본: dist(g) */
class Dist(val sub: DoubleArray?, private val sAll: DoubleArray) {
    val n: Int = sub?.size ?: 0
    /** 기관 데이터 비중 = n / (n + 30). 기관 표본이 적으면 전체 분포 쪽으로 당긴다. */
    val wS: Double = if (sub != null) n.toDouble() / (n + Dataset.K_SHRINK) else 0.0

    fun cdf(x: Double): Double =
        if (sub != null) wS * Stats.ecdf(sub, x) + (1 - wS) * Stats.ecdf(sAll, x) else Stats.ecdf(sAll, x)

    /** 누적확률 q 에 해당하는 사정율 (이분법 50회) */
    fun inv(q: Double): Double {
        var lo = -4.0
        var hi = 4.0
        repeat(50) {
            val m = (lo + hi) / 2
            if (cdf(m) < q) lo = m else hi = m
        }
        return hi
    }
}

class BacktestResult(val n: Int, val q: Int, val win: Int, val low: Int, val high: Int)
class SimpleBacktest(val n: Int, val q: Int, val win: Int)

class GapStats(val sortedGap: DoubleArray, val counts: IntArray, val modeIdx: Int, val mode: Double) {
    fun q(p: Double): Double = Stats.quant(sortedGap, p)
}

/**
 * 낙찰 데이터 전체. 원본의 DATA / SALL 에 해당한다.
 * 같은 공고번호는 뒤에 온 것이 덮어쓰고, 금액이 비정상인 건은 제외하고, 개찰일 내림차순으로 정렬한다.
 */
class Dataset private constructor(val rows: List<Enriched>) {
    /** 모든 건의 사정율(%) 오름차순 */
    val sAll: DoubleArray = DoubleArray(rows.size) { rows[it].s }.also { it.sort() }

    fun dist(group: String?): Dist {
        val sub = if (!group.isNullOrEmpty() && group != "전체")
            rows.filter { it.grp == group }.map { it.s }.toDoubleArray().also { it.sort() } else null
        return Dist(sub, sAll)
    }

    /** 기관 그룹별 건수 (화면 선택 목록 순서 그대로, '전체' 포함, 0건 그룹 제외) */
    fun groupCounts(): LinkedHashMap<String, Int> {
        val cnt = HashMap<String, Int>()
        rows.forEach { cnt[it.grp] = (cnt[it.grp] ?: 0) + 1 }
        val out = LinkedHashMap<String, Int>()
        out["전체"] = rows.size
        for (g in Grouping.GU + Grouping.OTHER_GROUPS) cnt[g]?.let { if (it > 0) out[g] = it }
        return out
    }

    /** 하한율 대비 1순위 투찰율 차이(gap)의 분포 */
    fun gapStats(): GapStats {
        val g = DoubleArray(rows.size) { rows[it].gap }.also { it.sort() }
        val edges = GAP_EDGES
        val cnt = IntArray(edges.size - 1)
        for (v in g) for (i in 0 until edges.size - 1) if (v >= edges[i] && v < edges[i + 1]) { cnt[i]++; break }
        var mi = 0
        for (i in cnt.indices) if (cnt[i] > cnt[mi]) mi = i       // 최댓값 중 첫 번째 (JS indexOf(Math.max))
        return GapStats(g, cnt, mi, (edges[mi] + edges[mi + 1]) / 2)
    }

    /** 예상 사정율 sExp 로 같은 조건에 투찰했다면: 하한가 이상이었던 건(q), 1순위보다 낮아 낙찰됐을 건(win), 탈락(low), 1순위보다 높아 밀린 건(high) */
    fun backtestTS(sExp: Double, off: Double, arr: List<Enriched> = rows): BacktestResult {
        var q = 0; var win = 0; var low = 0; var high = 0
        for (r in arr) {
            val bid = Rates.bidFor(r.base, r.aValue, sExp, r.rate + off)
            if (bid >= r.floorPrice) { q++; if (bid < r.winBid) win++ else high++ } else low++
        }
        return BacktestResult(arr.size, q, win, low, high)
    }

    /** 사정율 기준 단순 백테스트 (구 버전) */
    fun backtest(sB: Double, arr: List<Enriched> = rows): SimpleBacktest {
        var q = 0; var win = 0
        for (r in arr) if (r.s <= sB) { q++; if (sB < r.sw) win++ }
        return SimpleBacktest(arr.size, q, win)
    }

    class SChip(val label: String, val value: Double)

    /** 사정율 입력 아래 빠른 선택 칩: 최근 30일 평균, 0%, (기관 선택 시) 기관 평균 */
    fun sChips(dist: Dist, group: String): List<SChip> {
        val cut = java.time.LocalDate.parse(rows[0].rec.date).minusDays(30).toString()
        val last30 = rows.filter { it.rec.date >= cut }.map { it.s }.toDoubleArray()
        val out = arrayListOf(SChip("최근 30일 평균", Stats.mean(last30)), SChip("기초금액 그대로(0%)", 0.0))
        val sub = dist.sub
        if (sub != null && dist.n > 0) out.add(SChip("$group 평균(${dist.n}건)", Stats.mean(sub)))
        return out
    }

    /** 화면에 처음 채워 주던 예상 사정율: 전체 중앙값을 소수 4자리로 (원본 fmtS(median(...)) 후 num()) */
    fun defaultExpectedS(): Double {
        val v = JsMath.toFixed(Stats.median(sAll), 4).toDouble()
        return if (v == 0.0) 0.0 else v
    }

    companion object {
        const val K_SHRINK = 30
        val GAP_EDGES = doubleArrayOf(0.0, 0.001, 0.002, 0.003, 0.005, 0.0075, 0.01, 0.015, 0.02, 0.03, 0.05, 0.1, 0.25, 99.0)

        /** 원본 enrich(): 하한율은 낙찰하한가가 있으면 거기서 역산(snap), 없으면 추정가격 기준 기본값 */
        fun enrich(r: BidRecord): Enriched {
            val e = if (r.estimate != 0.0 && !r.estimate.isNaN()) r.estimate else r.base / 1.1
            val rate = if (r.floorPrice != 0.0 && r.planned != 0.0 && r.planned > r.aValue)
                Rates.snapRate((r.floorPrice - r.aValue) / (r.planned - r.aValue) * 100)
            else Rates.rateByEst(e) ?: Double.NaN
            val s = (r.planned / r.base - 1) * 100
            val sw = Rates.sOfBid(r.winBid, r.base, r.aValue, rate)
            val rA = (r.winBid - r.aValue) / (r.planned - r.aValue) * 100
            return Enriched(
                rec = r, e = e, rate = rate, s = s, sw = sw, rA = rA, gap = rA - rate, diff = r.winBid - r.floorPrice,
                grp = Grouping.groupOf(r.org), size = Grouping.sizeOf(e), mon = r.date.take(7),
            )
        }

        /** 원본 rebuild(). base 는 내장 데이터, extra 는 사용자가 추가한 건. */
        fun build(base: List<BidRecord>, extra: List<BidRecord> = emptyList()): Dataset {
            val seen = LinkedHashMap<String, BidRecord>()
            for (r in base + extra) seen[if (r.no.isNotEmpty()) r.no else r.name + r.date] = r
            val rows = seen.values
                .filter { it.base > 0 && it.planned > 0 && it.winBid > 0 && it.planned > it.aValue }
                .map { enrich(it) }
                .sortedWith(compareByDescending<Enriched> { it.rec.date })
            return Dataset(rows)
        }
    }
}
