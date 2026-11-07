package kr.landscapebid.engine

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * JavaScript 내장 동작(Math.round, toFixed, parseFloat)을 그대로 흉내 내는 유틸.
 * 원본 웹 앱과 숫자·문자열 결과를 한 글자까지 일치시키기 위해 쓴다.
 */
object JsMath {
    /** Math.round: .5 는 +∞ 쪽으로 (2.5 → 3, -2.5 → -2). */
    fun round(x: Double): Double {
        if (x.isNaN() || x.isInfinite()) return x
        val r = Math.floor(x)
        val res = if (x - r >= 0.5) r + 1.0 else r
        // JS 는 -0.5 ≤ x < 0 에서 -0 을 돌려준다 (화면에 "-0" 으로 찍히는 원인)
        return if (res == 0.0 && (x < 0 || 1.0 / x < 0)) -0.0 else res
    }

    /**
     * Number.prototype.toFixed (|x| < 1e21).
     * 정확한 이진 값을 기준으로 반올림하고, 음수는 부호를 따로 처리해서 "-0.0000" 도 JS 와 같게 나온다.
     */
    fun toFixed(x: Double, digits: Int): String {
        if (x.isNaN()) return "NaN"
        if (x.isInfinite()) return if (x > 0) "Infinity" else "-Infinity"
        val body = BigDecimal(Math.abs(x)).setScale(digits, RoundingMode.HALF_UP).toPlainString()
        return if (x < 0) "-$body" else body
    }

    private val junk = Regex("[^0-9.\\-]")
    private val floatPrefix = Regex("^-?(\\d+\\.?\\d*|\\.\\d+)")

    /** 원본 num(): 숫자·점·마이너스만 남기고 parseFloat. 숫자가 아니면 NaN. */
    fun num(s: String?): Double {
        val cleaned = (s ?: "").replace(junk, "")
        val m = floatPrefix.find(cleaned) ?: return Double.NaN
        val v = m.value.toDouble()
        return if (v.isFinite()) v else Double.NaN
    }
}
