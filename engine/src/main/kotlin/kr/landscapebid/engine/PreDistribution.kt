package kr.landscapebid.engine

import kotlin.math.ceil

/** "예정 투찰금액 분포" 한 줄: 방법 이름, 분위수(10/25/50/75/90%), 평균 */
class PreRow(val name: String, val quantiles: DoubleArray, val mean: Double)

class PreDistribution(
    val rangePct: Double,        // 예가 범위(±%)
    val rows: List<PreRow>,      // A 실제 / B 단순 이론 / C 보정 이론
    val cPlannedRow: PreRow,     // 참고: C 예정가격
    val note: String,
    val step: Int, val lo: Double, val hi: Double,
    val hA: DoubleArray, val hB: DoubleArray, val hC: DoubleArray,   // 구간별 비율(0~1)
    val medianBin: Int,
) {
    val bins: Int get() = hA.size
    fun binStart(i: Int): Double = lo + i * step
    fun binLabel(i: Int): String = Format.man(binStart(i)) + "만"
    fun binRange(i: Int): String = Format.won(binStart(i)) + "~" + Format.won(binStart(i) + step - 1)
}

/** 원본 drawPre 의 계산 부분. 예정가격을 투찰금액(= 그때의 낙찰하한가)으로 바꿔서 A/B/C 세 방식으로 분포를 만든다. */
object PreDistributionCalc {
    val QUANTILES = doubleArrayOf(0.1, 0.25, 0.5, 0.75, 0.9)

    fun compute(ds: Dataset, dist: Dist, b: Double, a: Double, rate: Double, group: String,
                rangeOverride: Double?, unit: Int): PreDistribution {
        val r = rangeOverride?.takeIf { it != 0.0 && !it.isNaN() } ?: (if (group == "국가·기타") 2.0 else 3.0)
        val sAll = ds.sAll
        val sub = dist.sub
        // A: 가중 경험분포 (예정가격 기준으로 정렬한 뒤 투찰금액으로 변환)
        val n0 = (sub?.size ?: 0) + sAll.size
        val av = DoubleArray(n0); val aw = DoubleArray(n0)
        var k = 0
        if (sub != null) { for (s in sub) { av[k] = b * (1 + s / 100); aw[k] = dist.wS / dist.n; k++ } }
        for (s in sAll) { av[k] = b * (1 + s / 100); aw[k] = if (sub != null) (1 - dist.wS) / sAll.size else 1.0 / sAll.size; k++ }
        val order = (0 until n0).sortedWith(compareBy { av[it] })        // 안정 정렬
        fun fl(p: Double) = ceil((p - a) * rate / 100 + a - 1e-7)
        val aV = DoubleArray(n0) { fl(av[order[it]]) }
        val aW = DoubleArray(n0) { aw[order[it]] }

        val bs = Simulation.simS('B', r)
        val cs = Simulation.simS('C', r)
        val pB = DoubleArray(bs.size) { fl(b * (1 + bs[it] / 100)) }
        val pC = DoubleArray(cs.size) { fl(b * (1 + cs[it] / 100)) }
        fun qA(q: Double): Double { var c = 0.0; for (i in 0 until n0) { c += aW[i]; if (c >= q) return aV[i] }; return aV[n0 - 1] }
        fun qArr(arr: DoubleArray, q: Double) = arr[Math.min(arr.size - 1, Math.floor(q * arr.size).toInt())]
        var aMean = 0.0
        for (i in 0 until n0) aMean += aV[i] * aW[i]

        val rows = listOf(
            PreRow("A 실제", DoubleArray(5) { qA(QUANTILES[it]) }, aMean),
            PreRow("B 단순 이론", DoubleArray(5) { qArr(pB, QUANTILES[it]) }, Stats.mean(pB)),
            PreRow("C 보정 이론", DoubleArray(5) { qArr(pC, QUANTILES[it]) }, Stats.mean(pC)),
        )
        val ref = PreRow("참고: C 예정가격", DoubleArray(5) { b * (1 + qArr(cs, QUANTILES[it]) / 100) }, b * (1 + Stats.mean(cs) / 100))

        // 구간(bin)
        val step = unit
        val lo: Double; val hi: Double
        if (step >= 100000) {
            lo = Math.floor(fl(b * (1 - r / 100)) / step) * step
            hi = Math.ceil(fl(b * (1 + r / 100)) / step) * step
        } else {
            val c = Math.floor(qArr(pC, 0.5) / step) * step
            lo = c - 1500000; hi = c + 1500000
        }
        val n = JsMath.round((hi - lo) / step).toInt()
        val hA = DoubleArray(n); val hB = DoubleArray(n); val hC = DoubleArray(n)
        for (i in 0 until n0) { val idx = Math.floor((aV[i] - lo) / step).toInt(); if (idx in 0 until n) hA[idx] += aW[i] }
        for (v in pB) { val idx = Math.floor((v - lo) / step).toInt(); if (idx in 0 until n) hB[idx]++ }
        for (v in pC) { val idx = Math.floor((v - lo) / step).toInt(); if (idx in 0 until n) hC[idx]++ }
        for (i in 0 until n) { hB[i] /= pB.size; hC[i] /= pC.size }
        val note = if (step >= 100000) Format.won(lo) + "~" + Format.won(hi) + "원 전체 (±" + Format.jsNumber(r) + "%) " else "C 중앙값 기준 ±150만원 구간만 표시"
        val mi = Math.floor((qArr(pC, 0.5) - lo) / step).toInt()
        return PreDistribution(r, rows, ref, note, step, lo, hi, hA, hB, hC, mi)
    }
}
