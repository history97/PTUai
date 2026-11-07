package kr.landscapebid.engine

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/** "사정율 선택 차트"(막대 + 곡선 + 최빈/중앙값/평균) 계산 결과. 원본 drawPick 의 계산 부분. */
class PickModel(
    val lo: Double, val hi: Double,
    val binWidth: Double,            // 막대 폭 = 근접 확률 폭(probW)
    val vol: DoubleArray,            // 막대 높이(구간별 과거 비율)
    val kdeX: DoubleArray, val kdeY: DoubleArray,   // 부드러운 곡선
    val modeS: Double, val median: Double, val mean: Double,
    val q25: Double, val q75: Double,
    val vmax: Double,
) {
    fun x(s: Double, left: Double, width: Double, right: Double) = left + (s - lo) / (hi - lo) * (width - left - right)
    fun s(px: Double, left: Double, width: Double, right: Double) = lo + (px - left) / (width - left - right) * (hi - lo)
}

object PickChart {
    /** @param narrow 화면 폭이 600 미만이면 ±2%, 아니면 ±2.5% 범위로 그린다 */
    fun compute(ds: Dataset, dist: Dist, probW: Double, narrow: Boolean): PickModel {
        val lo = if (narrow) -2.0 else -2.5
        val hi = if (narrow) 2.0 else 2.5
        val med = dist.inv(0.5)
        val sAll = ds.sAll
        val sub = dist.sub
        val total = (sub?.size ?: 0) + sAll.size
        val pv = DoubleArray(total)
        val pw = DoubleArray(total)
        var k = 0
        if (sub != null) { for (v in sub) { pv[k] = v; pw[k] = dist.wS / dist.n; k++ } }
        for (v in sAll) { pv[k] = v; pw[k] = if (sub != null) (1 - dist.wS) / sAll.size else 1.0 / sAll.size; k++ }

        var meanS = 0.0
        for (i in 0 until total) meanS += pv[i] * pw[i]

        val bw = probW
        val nb = JsMath.round((hi - lo) / bw).toInt()
        val vol = DoubleArray(nb)
        for (i in 0 until total) {
            val idx = Math.floor((pv[i] - lo) / bw).toInt()
            if (idx in 0 until nb) vol[idx] += pw[i]
        }

        // 부드러운 곡선(KDE): 폭 0.12, 막대와 같은 척도. s 는 원본처럼 0.02 씩 더해 가며 만든다.
        val xs = ArrayList<Double>()
        val ys = ArrayList<Double>()
        var modeS = 0.0
        var best = -1.0
        var s = lo
        val norm = 0.12 * sqrt(2 * Math.PI)
        while (s <= hi + 1e-9) {
            var y = 0.0
            for (i in 0 until total) { val z = (s - pv[i]) / 0.12; y += pw[i] * exp(-0.5 * (z * z)) }
            y = y / norm * bw
            xs.add(s); ys.add(y)
            if (s >= -2 && s <= 2 && y > best) { best = y; modeS = s }
            s += 0.02
        }
        var mx = best
        for (v in vol) mx = max(mx, v)
        return PickModel(
            lo, hi, bw, vol, xs.toDoubleArray(), ys.toDoubleArray(), modeS, med, meanS,
            dist.inv(0.25), dist.inv(0.75), mx * 1.08,
        )
    }
}
