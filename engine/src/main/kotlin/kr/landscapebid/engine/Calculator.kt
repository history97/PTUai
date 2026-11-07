package kr.landscapebid.engine

import kotlin.math.ceil

/** 계산기 입력. 원본 화면의 입력칸과 1:1 대응. */
data class CalcInput(
    val base: Double,                       // inB 기초금액
    val aValue: Double = 0.0,               // inA A값
    val estimate: Double? = null,           // inE 추정가격 (null/0 이하면 기초금액/1.1)
    val netCost: Double? = null,            // inC 순공사원가
    val group: String = "전체",             // inG 기관 그룹
    val rateOverride: Double? = null,       // inR 하한율(%) — 공고문 값. null/0 이하면 추정가격 기준 기본값
    val expectedS: Double? = null,          // inS 예상 사정율(%) — null 이면 전체 중앙값(소수 4자리)
    val plannedPrice: Double? = null,       // inP 예정가격을 이미 알면 사정율을 역산
    val myAmount: Double? = null,           // inX 이미 정한 투찰금액을 검토하고 싶을 때
    val probWidth: Double = 0.05,           // 근접 확률의 폭(±%p)
)

class ScenarioRow(
    val s: Double,              // 실제 사정율(%)
    val plannedPrice: Double,   // 그때의 예정가격
    val floorPrice: Double,     // 그때의 낙찰하한가
    val diff: Double,           // 내 금액 - 하한가
    val isExpected: Boolean,    // 내가 예상한 사정율 행
) { val passes: Boolean get() = diff >= 0 }

class MyAmountReview(
    val bidRate: Double,        // 투찰율(%) = (X-a)/(예정가격-a)*100
    val gapToFloorRate: Double, // 투찰율 - 하한율 (%p)
    val diffFromFloor: Double,  // X - 예상 하한가
    val ratioToBase: Double,    // X / 기초금액 * 100
    val sAtFloor: Double,       // X 가 하한가가 되는 사정율(%)
    val pctBelow: Double,       // 과거에 그 사정율보다 낮게 나온 비율(0~1)
)

class CalcResult(
    val input: CalcInput,
    val b: Double, val a: Double, val e: Double, val c: Double,
    val group: String, val rate: Double,
    val sExp: Double,           // 예상 사정율(%)
    val plannedPrice: Double,   // 예상(또는 입력한) 예정가격
    val floorExp: Double,       // 그 예정가격에서의 낙찰하한가
    val bid: Double,            // 추천 투찰금액 (= 하한가, 원 미만 절상)
    val sAtBid: Double,         // 투찰금액이 하한가가 되는 사정율(%)
    val pBelow: Double,         // 과거 사정율이 sAtBid 이하였던 확률(0~1)
    val nearProb: Double,       // 과거 사정율이 sAtBid ± probWidth 안에 있던 확률(0~1)
    val pExpected: Double,      // 과거 사정율이 sExp 이하였던 확률(0~1)
    val competitorRate: Double?,// 참가업체 몰림 비율 (데이터가 없으면 null)
    val backtestAll: BacktestResult,
    val backtestGroup: BacktestResult?,
    val groupN: Int, val groupWeight: Double,
    val belowNetCostLimit: Boolean?,   // 순공사원가 98% 미만 여부 (순공사원가 입력 없으면 null)
    val netCostLimit: Double?,
    val aValueMissing: Boolean,
    val scenarios: List<ScenarioRow>,
    val review: MyAmountReview?,
) {
    /** 화면의 "사정율 / 예상 예정가격 / 투찰·기초" 한 줄 */
    val bidRatioToBase: Double get() = bid / b * 100

    /** 원본 화면의 보조 정보 목록 (label, value) */
    fun summaryItems(): List<Pair<String, String>> = listOf(
        "적용 하한율" to Format.jsNumber(rate) + "%",
        "추정가격" to Format.won(e),
        "규모 구간" to Grouping.sizeOf(e),
        "A값 비중" to JsMath.toFixed(a / b * 100, 2) + "%",
        "예상 하한가/기초" to Format.trunc3(floorExp / b * 100) + "%",
        "사정율 0.1%p당 금액" to "약 " + Format.won(b * 0.001 * rate / 100) + "원",
        "예정가격 가능 범위" to Format.won(b * 0.97) + "~" + Format.won(b * 1.03),
    )

    fun reviewItems(): List<Pair<String, String>> = review?.let { r ->
        listOf(
            "투찰율" to Format.trunc4(r.bidRate) + "%",
            "하한율 대비" to (if (r.gapToFloorRate >= 0) "+" else "") + JsMath.toFixed(r.gapToFloorRate, 4) + "%p",
            "하한가와 차이" to (if (r.diffFromFloor >= 0) "+" else "") + Format.won(r.diffFromFloor) + "원",
            "투찰/기초" to Format.trunc3(r.ratioToBase) + "%",
            "이 금액이 하한가 되는 사정율" to Format.f3(r.sAtFloor) + "%",
            "과거 이보다 낮게 나온 확률" to JsMath.toFixed(r.pctBelow * 100, 0) + "%",
        )
    } ?: emptyList()
}

sealed interface CalcOutcome {
    data class Ok(val result: CalcResult) : CalcOutcome
    data class Rejected(val reason: Reason, val message: String) : CalcOutcome
    enum class Reason { NO_BASE, ESTIMATE_TOO_LARGE, INVALID_S }
}

object Calculator {
    private val SCENARIO_S = doubleArrayOf(-2.0, -1.5, -1.0, -0.5, 0.0, 0.5, 1.0, 1.5, 2.0)

    /** 이 결과가 원본 앱의 calc() 와 같은지는 src/test 의 golden 테스트가 검증한다. */
    fun calculate(ds: Dataset, input: CalcInput): CalcOutcome {
        val b = input.base
        val a = if (input.aValue.isNaN()) 0.0 else input.aValue
        val e = input.estimate?.takeIf { it > 0 } ?: (b / 1.1)
        if (!(b > 0)) return CalcOutcome.Rejected(CalcOutcome.Reason.NO_BASE, "기초금액을 입력하세요")
        val rate = input.rateOverride?.takeIf { it > 0 } ?: Rates.rateByEst(e)
            ?: return CalcOutcome.Rejected(CalcOutcome.Reason.ESTIMATE_TOO_LARGE, "추정가격 100억 이상은 종합심사낙찰제 대상이라 이 계산을 쓰지 않습니다")

        val t = rate
        var sExp = input.expectedS ?: ds.defaultExpectedS()
        val pin = input.plannedPrice?.takeIf { it > 0 }
        if (pin != null) sExp = (pin / b - 1) * 100
        if (!sExp.isFinite()) return CalcOutcome.Rejected(CalcOutcome.Reason.INVALID_S, "예상 사정율이 올바르지 않습니다")

        val off = t - rate                                   // 항상 0 (원본과 동일)
        val dist = ds.dist(input.group)
        val pExp = pin ?: (b * (1 + sExp / 100))
        val bid = ceil((pExp - a) * t / 100 + a - 1e-7)
        val floorExp = ceil((pExp - a) * rate / 100 + a - 1e-7)
        val sAtBid = Rates.sOfBid(bid, b, a, rate)
        val pBelow = dist.cdf(sAtBid)
        val w = input.probWidth
        val nearProb = dist.cdf(sAtBid + w) - dist.cdf(sAtBid - w)

        val btAll = ds.backtestTS(sExp, off)
        val btGrp = dist.sub?.let { ds.backtestTS(sExp, off, ds.rows.filter { r -> r.grp == input.group }) }

        val c = input.netCost ?: Double.NaN
        val limit = if (c > 0) ceil(c * 0.98) else null

        val sR = JsMath.round(sExp * 10000) / 10000
        val sRn = if (sR == 0.0) 0.0 else sR                  // JS 의 Set 은 -0 과 0 을 같은 값으로 본다
        val svals = java.util.TreeSet<Double>()
        for (v in SCENARIO_S) svals.add(v)
        svals.add(sRn)
        val rows = svals.map { s ->
            val p = b * (1 + s / 100)
            val fl = Rates.floorAt(b, a, rate, s)
            ScenarioRow(s, p, fl, bid - fl, s == sRn)
        }

        val x = input.myAmount?.takeIf { it > 0 }
        val review = x?.let {
            val bidRate = (it - a) / (pExp - a) * 100
            val sx = Rates.sOfBid(it, b, a, rate)
            MyAmountReview(bidRate, bidRate - rate, it - floorExp, it / b * 100, sx, dist.cdf(sx))
        }

        return CalcOutcome.Ok(
            CalcResult(
                input = input, b = b, a = a, e = e, c = c, group = input.group, rate = rate, sExp = sExp,
                plannedPrice = pExp, floorExp = floorExp, bid = bid, sAtBid = sAtBid, pBelow = pBelow,
                nearProb = nearProb, pExpected = dist.cdf(sExp), competitorRate = competitorRate(sExp, w),
                backtestAll = btAll, backtestGroup = btGrp, groupN = dist.n, groupWeight = dist.wS,
                belowNetCostLimit = limit?.let { bid < it }, netCostLimit = limit,
                aValueMissing = !(a > 0), scenarios = rows, review = review,
            ),
        )
    }

    /** 참가업체 전체 투찰 데이터가 들어오면 sExp±w 에 몰린 비율을 계산할 자리. 지금은 원본처럼 항상 null. */
    @Suppress("UNUSED_PARAMETER")
    fun competitorRate(sExp: Double, w: Double): Double? = null
}
