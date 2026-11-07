package kr.landscapebid.engine

import java.util.Locale

/** 원본 화면에 찍히던 숫자 표기(won / f3 / trunc3 / trunc4) 그대로. */
object Format {
    /** Math.round(v).toLocaleString('ko-KR') */
    fun won(v: Double): String {
        val r = JsMath.round(v)
        if (r.isNaN()) return "NaN"
        if (r.isInfinite()) return if (r > 0) "∞" else "-∞"
        if (r == 0.0 && 1.0 / r < 0) return "-0"                // JS 의 (-0).toLocaleString()
        return String.format(Locale.US, "%,d", r.toLong())
    }

    /** 부호를 붙인 소수 4자리: +0.1234 / -0.1234 */
    fun f3(v: Double): String = (if (v >= 0) "+" else "") + JsMath.toFixed(v, 4)

    /** 반올림하지 않고 소수 4자리에서 버림 */
    fun trunc4(v: Double): String = JsMath.toFixed(Math.floor(v * 10000 + 1e-9) / 10000, 4)

    /** 반올림하지 않고 소수 3자리에서 버림 */
    fun trunc3(v: Double): String = JsMath.toFixed(Math.floor(v * 1000 + 1e-9) / 1000, 3)

    /** JS 의 String(number) 흉내: 정수는 소수점 없이, 아니면 가장 짧은 표기 (일반 범위만) */
    fun jsNumber(v: Double): String {
        if (v.isNaN()) return "NaN"
        if (v.isInfinite()) return if (v > 0) "Infinity" else "-Infinity"
        if (v == Math.floor(v) && Math.abs(v) < 1e15) return (if (v == 0.0) 0L else v.toLong()).toString()
        return java.math.BigDecimal(v.toString()).toPlainString()
    }

    /** (v/10000).toLocaleString('ko-KR', {maximumFractionDigits: 1}) — 만원 단위 표기 */
    fun man(v: Double): String {
        if (v.isNaN()) return "NaN"
        if (v.isInfinite()) return if (v > 0) "∞" else "-∞"
        val df = java.text.DecimalFormat("#,##0.#", java.text.DecimalFormatSymbols(Locale.US))
        df.roundingMode = java.math.RoundingMode.HALF_UP
        return df.format(java.math.BigDecimal((v / 10000).toString()))
    }

    /** 사정율 차트 x축 아래 "14,123만" 표기: man() 결과에서 소수 부분을 버린다 */
    fun manAxis(v: Double): String = man(v).substringBefore('.') + "만"
}
