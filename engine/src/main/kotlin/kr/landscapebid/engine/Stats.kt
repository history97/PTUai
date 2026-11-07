package kr.landscapebid.engine

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** 순수 통계 함수들. 원본: ecdf / mean / sd / median / quant / ranks / gammaP / lgamma / kruskal */
object Stats {
    /** 정렬된 배열의 경험적 누적분포(구간 사이는 선형 보간, 양끝은 0.5/n 만큼 여유) */
    fun ecdf(sorted: DoubleArray, x: Double): Double {
        val n = sorted.size
        if (n == 0) return 0.0
        if (x < sorted[0]) return 0.0
        if (x >= sorted[n - 1]) return 1.0
        var lo = 0
        var hi = n
        while (lo < hi) {
            val m = (lo + hi) shr 1
            if (sorted[m] <= x) lo = m + 1 else hi = m
        }
        if (lo == 0) return Double.NaN            // x 가 NaN 일 때만 도달 (JS 는 undefined → NaN)
        val a = sorted[lo - 1]
        val b = sorted[lo]
        val fr = if (b > a) (x - a) / (b - a) else 1.0
        return (lo - 1 + fr + 0.5) / n
    }

    fun mean(a: DoubleArray): Double {
        var s = 0.0
        for (v in a) s += v
        return s / (if (a.isEmpty()) 1 else a.size)
    }

    /** 표본 표준편차 (n-1) */
    fun sd(a: DoubleArray): Double {
        val m = mean(a)
        var s = 0.0
        for (v in a) { val d = v - m; s += d * d }
        return sqrt(s / max(1, a.size - 1))
    }

    fun median(a: DoubleArray): Double {
        val n = a.size
        if (n == 0) return Double.NaN
        val s = a.copyOf().also { it.sort() }
        return if (n % 2 == 1) s[(n - 1) / 2] else (s[n / 2 - 1] + s[n / 2]) / 2
    }

    /** 정렬된 배열의 분위수(선형 보간) */
    fun quant(sorted: DoubleArray, q: Double): Double {
        if (sorted.isEmpty()) return Double.NaN
        val i = (sorted.size - 1) * q
        val l = floor(i).toInt()
        val h = ceil(i).toInt()
        return sorted[l] + (sorted[h] - sorted[l]) * (i - l)
    }

    class Ranks(val r: DoubleArray, val ties: Double)

    /** 평균 순위(동점은 평균) + 동점 보정항 */
    fun ranks(vals: DoubleArray): Ranks {
        val idx = vals.indices.sortedWith(compareBy { vals[it] })   // 안정 정렬
        val r = DoubleArray(vals.size)
        var i = 0
        var ties = 0.0
        while (i < idx.size) {
            var j = i
            while (j + 1 < idx.size && vals[idx[j + 1]] == vals[idx[i]]) j++
            val rk = (i + j) / 2.0 + 1
            for (k in i..j) r[idx[k]] = rk
            val t = (j - i + 1).toDouble()
            ties += t * t * t - t
            i = j + 1
        }
        return Ranks(r, ties)
    }

    private val LG_C = doubleArrayOf(
        0.99999999999980993, 676.5203681218851, -1259.1392167224028, 771.32342877765313,
        -176.61502916214059, 12.507343278686905, -0.13857109526572012, 9.9843695780195716e-6, 1.5056327351493116e-7,
    )

    fun lgamma(zIn: Double): Double {
        if (zIn < 0.5) return ln(Math.PI / sin(Math.PI * zIn)) - lgamma(1 - zIn)
        val g = 7
        val z = zIn - 1
        var x = LG_C[0]
        for (i in 1 until g + 2) x += LG_C[i] / (z + i)
        val t = z + g + 0.5
        return 0.5 * ln(2 * Math.PI) + (z + 0.5) * ln(t) - t + ln(x)
    }

    /** 하한 정규화 불완전 감마 P(a, x) */
    fun gammaP(a: Double, x: Double): Double {
        if (x <= 0) return 0.0
        if (x < a + 1) {
            var s = 1 / a
            var t = s
            for (n in 1 until 300) {
                t *= x / (a + n)
                s += t
                if (abs(t) < 1e-12) break
            }
            return s * exp(-x + a * ln(x) - lgamma(a))
        }
        var b = x + 1 - a
        var c = 1e300
        var d = 1 / b
        var h = d
        for (i in 1 until 300) {
            val an = -i * (i - a)
            b += 2
            d = an * d + b
            if (abs(d) < 1e-300) d = 1e-300
            c = b + an / c
            if (abs(c) < 1e-300) c = 1e-300
            d = 1 / d
            val del = d * c
            h *= del
            if (abs(del - 1) < 1e-12) break
        }
        return 1 - exp(-x + a * ln(x) - lgamma(a)) * h
    }

    class Kruskal(val h: Double, val df: Int, val p: Double)

    /** Kruskal-Wallis H 검정 (동점 보정 포함) */
    fun kruskal(groups: List<DoubleArray>): Kruskal {
        val all = ArrayList<Double>()
        val lab = ArrayList<Int>()
        groups.forEachIndexed { i, g -> for (v in g) { all.add(v); lab.add(i) } }
        val n = all.size
        val rk = ranks(all.toDoubleArray())
        val rSum = DoubleArray(groups.size)
        rk.r.forEachIndexed { i, x -> rSum[lab[i]] += x }
        var acc = 0.0
        rSum.forEachIndexed { i, x -> acc += x * x / groups[i].size }
        var h = 12.0 / (n.toDouble() * (n + 1)) * acc - 3.0 * (n + 1)
        val n3 = n.toDouble() * n * n
        h /= 1 - rk.ties / (n3 - n)
        val df = groups.size - 1
        return Kruskal(h, df, 1 - gammaP(df / 2.0, h / 2))
    }
}
