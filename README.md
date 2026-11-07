<<<<<<< HEAD
# 서울시 조경공사 낙찰하한가 계산기 (Android / Kotlin)

웹 버전(`tools/index.original.html`)을 안드로이드 앱으로 옮기는 프로젝트입니다. Gradle 멀티모듈입니다.

| 모듈 | 내용 |
|---|---|
| `:engine` | 계산 로직만 옮긴 순수 Kotlin(JVM) 라이브러리. 원본 JS 와 결과가 같은지 테스트로 검증 |
| `:app` | Jetpack Compose 화면 (웹 화면과 같은 색·배치) |

## 화면 구성 (`:app`)
위쪽 탭 4개 + 오른쪽 톱니바퀴. 모든 화면 위에 "← 뒤로 / 홈" 줄이 있고, 안드로이드 기본 뒤로가기도 같은 이동 기록을 따릅니다. **홈 = 최근 공고**.

| 화면 | 내용 |
|---|---|
| 최근 공고 | 불러온 공고 목록을 검색어·카테고리(발주기관 분류)·마감 전 여부로 필터. 공고를 누르면 하한가 분석 2단계로, ☆ 로 즐겨찾기 |
| 하한가 분석 | 1단계 입력(엑셀 또는 직접 입력) → 2단계 예정 투찰금액·사정율 차트·금액 분포 그래프·시나리오 표, 내 금액 저장 |
| my페이지 | 즐겨찾기 공고와 내가 정한 사정율·투찰금액 (저장한 사정율로 지금 데이터 기준 확률을 다시 계산) |
| 검색 이력 | 열어 본/분석한 공고 (최근 순, 100건까지). 누르면 다시 분석 |
| 설정(톱니바퀴) | 화면 틀만 있음. 항목은 추후 |

즐겨찾기·검색 이력·불러온 엑셀은 앱 안(`filesDir`)에 JSON 으로 저장됩니다.

## 로그인 / 계정별 저장
`app/google-services.json` 을 넣으면 구글 로그인과 계정별 클라우드 저장(Firestore)이 켜지고, 없으면 "준비 중" 상태로 기기 안에만 저장합니다.
설정 방법은 `docs/FIREBASE_SETUP.md`. 화면·ViewModel 은 `cloud/CloudService` 인터페이스만 알고, 구현은 `src/cloud`(Firebase) / `src/nocloud`(준비 중) 중 하나가 컴파일됩니다.

## 실행
Android Studio 에서 이 폴더(`settings.gradle.kts` 가 있는 곳)를 열고 Gradle Sync → ▶ 실행. 터미널에서는 `./gradlew :app:installDebug`, 엔진 테스트는 `./gradlew :engine:test`.

## 이식된 것
| 파일 | 원본 함수 |
|---|---|
| `Rates.kt` | `rateByEst`, `snapRate`, `floorAt`, `sOfBid`, `bidFor` |
| `Grouping.kt` | `groupOf`, `sizeOf` |
| `Dataset.kt` | `enrich`, `rebuild`(중복 덮어쓰기·이상값 제외·정렬), `dist`, `gapStats`, `backtest`, `backtestTS`, `fillGroups`의 건수 계산 |
| `Stats.kt` | `ecdf`, `mean`, `sd`, `median`, `quant`, `ranks`, `gammaP`, `lgamma`, `kruskal` |
| `Simulation.kt` | `simS` (시드 고정 PRNG 까지 동일) |
| `PickChart.kt`, `PreDistribution.kt` | `drawPick`(사정율 선택 차트), `drawPre`(예정 투찰금액 분포)의 계산 부분, `chipsS` |
| `NoticeImport.kt`, `NoticeList.kt` | `parseRows`, `parseNotices`, `renderNotices`/`quickAmt`/`applyNotice` (엑셀 표 → 낙찰 데이터 / 공고 목록, 공고별 50·70·90% 금액) |
| `Calculator.kt` | `calc()` 의 계산 부분 (투찰금액, 확률, 백테스트, 시나리오 표, 순공사원가 경고, 내 금액 검토) |
| `Format.kt`, `JsMath.kt` | `won`, `f3`, `trunc3/4`, `toFixed`, `Math.round`, `num` 등 JS 표기 동작 |

## 아직 이식하지 않은 것
- 화면: `renderStats`(통계 탭), `renderLog`(내 예측 기록), 원자료 탭
- `simMC` (`Math.random` 을 쓰는 통계 탭용 몬테카를로 — 시드 고정 버전으로 바꿔 화면 이식 때 같이)
- 예측 기록 저장·동기화, AI 검토, 예전 `.xls` 형식 읽기(지금은 `.xlsx` 와 CSV 만)

## 엑셀 / 공고 목록
- `인포21C 엑셀 추가`: 낙찰정보리스트(공고명·예정가격·A값 등 열 포함)를 읽어 기본 데이터에 더함. 같은 공고는 건너뜀. 앱 안에 저장됨.
- `공고 목록 불러오기`: 입찰리스트(공고명·기초금액 열 포함)를 읽어 마감 전 공고 목록을 보여줌. 공고를 누르면 입력칸이 채워지고, 목록에는 공고별 "하한가 이상일 확률 50/70/90% 투찰금액"이 표시됨.
- 마감 비교는 한국 시간끼리 함. (웹 원본은 UTC 와 비교해서 마감 후 9시간 동안 "마감 전"으로 보임)

## 검증 방법
원본 `index.html` 의 JS 를 Node 에서 가짜 DOM 위에 그대로 실행해 기준값을 뽑고(`tools/export_golden.js`),
Kotlin 결과가 같은지 비교합니다. 숫자(원 단위 금액은 완전 일치) 와 화면에 찍히던 문자열까지 비교합니다.

```
# Gradle (안드로이드 스튜디오에서 이 폴더를 열고 test 실행)
./gradlew :engine:test

# Gradle 없이
tools/run_local_tests.sh      # kotlinc 필요

# 기준값 다시 만들기 (원본 HTML 이 바뀌었을 때)
node tools/export_golden.js tools/index.original.html engine/src/test/resources
```

## 사용 예
```kotlin
val ds = Dataset.build(BidTsv.parse(File("bids.tsv").readText()))
val out = Calculator.calculate(ds, CalcInput(base = 148_870_940.0, aValue = 12_056_532.0, rateOverride = 89.745))
if (out is CalcOutcome.Ok) println(Format.won(out.result.bid))
```

## 알아둘 점
- **하한율 기본값(`Rates.rateByEst`)은 원본 그대로**이고 틀린 것이 아닙니다. 2026.1.30 개정 기준이라 개정일 이후 공고는
  내장 데이터 570건 중 569건과 일치합니다. 개정 전(2023~2025) 공고에는 87.745% 등 옛 하한율이 쓰였으니,
  옛 공고를 계산할 때만 `CalcInput.rateOverride` 에 공고문의 하한율을 넣으세요.
- `floorAt` 의 `-1e-7` 보정은 원본과 똑같이 넣었지만, 내장 데이터 2,904건에서도 600만 건 탐색에서도 결과를 바꾸는 경우를 찾지 못해
  테스트로는 이 줄의 유무를 구분할 수 없습니다.
- Gradle 빌드와 안드로이드 앱은 이 환경에서 빌드해 보지 못했습니다(Maven 저장소 접근 불가). 엔진 테스트만 `kotlinc` 로 컴파일해 통과를 확인했습니다.
=======
# PTUai
평택대 AI포트폴리오 팀프로젝
>>>>>>> 369de550690f30e866a69b7863d9960a8b02a579
