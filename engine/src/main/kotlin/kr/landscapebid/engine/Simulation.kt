package kr.landscapebid.engine

/** 예정가격 분포 시뮬레이션. 원본 simS 의 시드 고정 PRNG(mulberry32 계열)를 정수 연산까지 똑같이 옮겼다. */
object Simulation {
    private class Rng(seed: Int) {
        private var sd = seed
        fun next(): Double {
            sd += 0x6D2B79F5
            var x = (sd xor (sd ushr 15)) * (1 or sd)
            x = (x + (x xor (x ushr 7)) * (61 or x)) xor x
            return ((x xor (x ushr 14)).toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
        }
    }

    /**
     * 15개 복수예비가격 중 4개를 뽑아 평균낸 사정율 편차(%)를 n 번 시뮬레이션해서 정렬해 돌려준다.
     * model 'B': 모두 [-R, +R] 균등. 그 외('A'): 8개는 -R~0, 7개는 0~+R 구간에서 층화 추출.
     */
    private val cache = java.util.concurrent.ConcurrentHashMap<String, DoubleArray>()

    /** 같은 (model, r) 는 한 번만 계산한다 (원본 SIMCACHE). 돌려받은 배열은 수정하지 말 것. */
    fun simS(model: Char, r: Double, n: Int = 100000): DoubleArray =
        cache.getOrPut("$model$r/$n") { simSUncached(model, r, n) }

    private fun simSUncached(model: Char, r: Double, n: Int): DoubleArray {
        val rng = Rng(if (model == 'B') 12345 else 67890)
        val out = DoubleArray(n)
        val p = DoubleArray(15)
        for (k in 0 until n) {
            if (model == 'B') {
                for (i in 0 until 15) p[i] = (rng.next() * 2 - 1) * r
            } else {
                val wl = r / 8
                val wh = r / 7
                for (i in 0 until 8) p[i] = -r + i * wl + rng.next() * wl
                for (i in 0 until 7) p[8 + i] = i * wh + rng.next() * wh
            }
            for (i in 0 until 4) {
                val j = i + Math.floor(rng.next() * (15 - i)).toInt()
                val t = p[i]; p[i] = p[j]; p[j] = t
            }
            out[k] = (p[0] + p[1] + p[2] + p[3]) / 4
        }
        out.sort()
        return out
    }
}
