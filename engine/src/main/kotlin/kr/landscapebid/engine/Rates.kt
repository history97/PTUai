package kr.landscapebid.engine

import kotlin.math.abs
import kotlin.math.ceil

/** 낙찰하한가 계산의 핵심 수식. 원본: rateByEst / snapRate / floorAt / sOfBid / bidFor */
object Rates {
    val KNOWN_RATES = doubleArrayOf(89.745, 88.745, 87.495, 87.745, 86.745, 85.495, 81.995, 79.995)

    /**
     * 추정가격으로 정하는 기본 하한율(%). 2026.1.30 개정 기준(10억 미만 89.745, 10~50억 88.745, 50~100억 87.495).
     * 100억 이상은 종합심사낙찰제라 null.
     * 개정일 이후 공고에서는 내장 데이터 570건 중 569건과 일치한다. 개정 전(2023~2025) 공고는 87.745 등
     * 다른 하한율을 썼으므로, 옛 공고를 계산할 때는 CalcInput.rateOverride 에 공고문의 하한율을 넣어야 한다.
     */
    fun rateByEst(e: Double): Double? = when {
        e < 1e9 -> 89.745
        e < 5e9 -> 88.745
        e < 1e10 -> 87.495
        else -> null
    }

    /** 역산한 하한율을 알려진 값에 붙이고, 없으면 소수 3자리로 반올림 */
    fun snapRate(v: Double): Double {
        for (k in KNOWN_RATES) if (abs(v - k) < 0.004) return k
        return JsMath.round(v * 1000) / 1000
    }

    /** 사정율 s(%)일 때의 낙찰하한가 (원 미만 절상). 1e-7 은 부동소수 오차로 1원 올라가는 것을 막는 원본 보정값. */
    fun floorAt(b: Double, a: Double, rate: Double, s: Double): Double {
        val p = b * (1 + s / 100)
        return ceil((p - a) * rate / 100 + a - 1e-7)
    }

    /** 투찰금액 B 가 하한가와 같아지는 사정율(%) */
    fun sOfBid(bid: Double, b: Double, a: Double, rate: Double): Double =
        ((bid - a) / (rate / 100) + a) / b * 100 - 100

    /** floorAt 과 같은 식 (원본에서 하한율 T 를 따로 받는 버전) */
    fun bidFor(b: Double, a: Double, s: Double, t: Double): Double {
        val p = b * (1 + s / 100)
        return ceil((p - a) * t / 100 + a - 1e-7)
    }
}
