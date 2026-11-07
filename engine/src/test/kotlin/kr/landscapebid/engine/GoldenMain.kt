package kr.landscapebid.engine

import java.io.File
import kotlin.system.exitProcess

/** Gradle 없이 확인하는 용도: kotlinc 로 컴파일해서 실행. 인자: golden/bids/extra 가 있는 폴더 */
fun main(args: Array<String>) {
    val dir = File(args.getOrElse(0) { "engine/src/test/resources" })
    val rep = GoldenChecker.run(Golden(File(dir, "golden.tsv").readText()), File(dir, "bids.tsv").readText(), File(dir, "extra.tsv").readText())
    println("== 원본 JS 대비 Kotlin 결과 비교 ==")
    val sections = (rep.passed.keys + rep.failedCount.keys).toSortedSet()
    for (s in sections) println("%-10s 통과 %6d   실패 %d".format(s, rep.passed[s] ?: 0, rep.failedCount[s] ?: 0))
    println("합계: 통과 ${rep.totalPassed}, 실패 ${rep.totalFailed}")
    rep.failures.forEach { println("  FAIL $it") }
    exitProcess(if (rep.totalFailed == 0) 0 else 1)
}
