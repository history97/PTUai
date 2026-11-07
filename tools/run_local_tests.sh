#!/bin/sh
# Gradle 없이 kotlinc 만으로 엔진 비교 테스트를 돌린다. (kotlinc 가 PATH 에 있어야 함)
set -e
cd "$(dirname "$0")/.."
kotlinc engine/src/main/kotlin/kr/landscapebid/engine/*.kt \
        engine/src/test/kotlin/kr/landscapebid/engine/GoldenChecker.kt \
        engine/src/test/kotlin/kr/landscapebid/engine/GoldenMain.kt \
        -include-runtime -d /tmp/engine-test.jar
java -Dstdout.encoding=UTF-8 -jar /tmp/engine-test.jar engine/src/test/resources
