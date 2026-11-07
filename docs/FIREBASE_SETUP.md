# 구글 로그인 + 계정별 저장 켜는 법 (Firebase)

코드는 이미 들어 있습니다. **`app/google-services.json` 파일을 넣기 전에는** 로그인이 꺼진 채(설정 화면에 "준비 중" 표시)로 앱이 정상 동작하고,
파일을 넣고 Gradle Sync 를 하면 자동으로 Firebase 버전이 켜집니다. (`app/build.gradle.kts` 의 `hasCloud`)

## 1. Firebase 프로젝트 만들기
1. https://console.firebase.google.com → 프로젝트 추가
2. 프로젝트 설정 → 일반 → **내 앱 → Android 앱 추가**
   - 패키지 이름: `kr.landscapebid.app`
   - **SHA-1 인증서 지문**: Android Studio 터미널에서 `./gradlew :app:signingReport` 를 실행하고 `debug` 항목의 SHA1 을 복사해 붙여넣기
     (Windows 는 `.\gradlew.bat :app:signingReport`)

## 2. 로그인 켜기
1. 빌드 → **Authentication** → 시작하기 → 로그인 방법 → **Google** 사용 설정 (프로젝트 지원 이메일 선택)

## 3. 데이터베이스 만들기
1. 빌드 → **Firestore Database** → 데이터베이스 만들기 (위치는 `asia-northeast3` 서울 권장, **프로덕션 모드**)
2. **규칙** 탭에 아래를 붙여넣고 게시 — 본인 계정 데이터만 읽고 쓸 수 있게 하는 규칙입니다.

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{uid}/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == uid;
    }
  }
}
```

## 4. 설정 파일 넣기
1. 프로젝트 설정 → 내 앱 → **google-services.json 다운로드**
   (⚠️ 1·2번을 끝낸 **뒤에** 받으세요. SHA-1 등록과 Google 로그인 설정 전에 받은 파일에는 로그인에 필요한 `oauth_client` 항목이 없습니다.)
2. 파일을 `app/` 폴더(= `app/build.gradle.kts` 옆)에 넣기
3. Android Studio 에서 **Sync Project with Gradle Files** → ▶ 실행
4. 앱의 설정(톱니바퀴) → 계정 → **Google 계정으로 로그인**

## 저장되는 것 / 안 되는 것
| 데이터 | 저장 위치 |
|---|---|
| 즐겨찾기(내가 정한 사정율·금액 포함), 검색 이력 | 계정별 클라우드 (`users/{uid}/favorites`, `users/{uid}/history`) + 기기 |
| 인포21C 엑셀로 추가한 낙찰 데이터, 불러온 공고 목록 | **기기에만** (유료 자료라 클라우드에 올리지 않음) |

## 동작 방식
- 첫 로그인 때 로그인 전(게스트)에 쓰던 즐겨찾기·이력을 계정으로 합칩니다. 같은 공고는 더 최근 것을 남깁니다.
- 이후 변경은 기기에 먼저 저장하고 클라우드로 올립니다. 오프라인이면 Firestore 가 쌓아 두었다가 연결되면 올립니다.
- 다른 기기의 변경은 **앱을 켤 때와 "지금 동기화"를 누를 때** 가져옵니다. (실시간 반영은 아직 아님)
- 한계: 삭제 기록(tombstone)이 없어서, 한 기기에서 지운 항목이 오프라인으로 남아 있던 다른 기기와 합쳐지면 되살아날 수 있습니다.

## 문제 해결
- `No credentials available` / 계정 선택창이 안 뜸: 기기(에뮬레이터)에 Google 계정이 로그인되어 있고 Play 스토어가 있는 이미지인지 확인
- 로그인 오류 코드 10 / 12500: SHA-1 이 Firebase 에 등록되지 않았거나, 등록 전에 받은 `google-services.json` 사용 중 → 등록 후 파일 다시 받기
- `default_web_client_id 가 없습니다`: Google 로그인을 켠 뒤 `google-services.json` 을 다시 받아 교체
- 나중에 릴리스 서명으로 배포할 때는 릴리스 키의 SHA-1 도 Firebase 에 추가해야 합니다.
