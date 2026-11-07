package kr.landscapebid.engine

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 원본 index.html 의 JS 를 실제로 돌려서 뽑은 기준값(golden.tsv)과 Kotlin 엔진 결과를 비교한다.
 * 기준값을 다시 만들려면: node tools/export_golden.js tools/index.original.html src/test/resources
 */
class GoldenTest {
    private fun res(name: String): String =
        GoldenTest::class.java.getResource("/$name")!!.readText(Charsets.UTF_8)

    @Test
    fun kotlinEngineMatchesOriginalJs() {
        val rep = GoldenChecker.run(Golden(res("golden.tsv")), res("bids.tsv"), res("extra.tsv"))
        println("통과 ${rep.totalPassed}건, 실패 ${rep.totalFailed}건")
        assertEquals(0, rep.totalFailed, "원본 JS 와 다른 결과:\n" + rep.failures.joinToString("\n"))
    }
}
