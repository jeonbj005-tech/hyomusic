# SimpleTunes 공통 개발 맥락

> 이 문서는 ChatGPT에서 논의한 요구사항과 Codex에서 확인한 실제 코드 상태를 이어 주는 기준 문서다. 코드와 충돌하면 현재 코드 및 검증 결과를 우선하고, 차이를 `DEV_STATUS.md`에 기록한다.

## 1. 프로젝트 목표

- 부모님이 적은 터치로 음악을 검색하고 재생하는 개인용 Android 앱
- YouTube를 유일한 음악 콘텐츠 소스로 사용하는 인터넷 스트리밍
- 화면이 꺼지거나 앱이 백그라운드에 있어도 기존 플레이어 지원 범위에서 재생 유지
- 자동차 Bluetooth 오디오와 핸들/하드웨어 미디어 버튼 활용
- 즐겨찾기(북마크/로컬 재생목록 기반), 재생목록, 연속 재생, 이어듣기 지원
- 개인 설치용 debug APK 제공

공식 표시 이름은 **SimpleTunes**다. 과거 효도뮤직/HyoMusic 명칭이 일부 역사 문서와 패키지에 남아 있지만 표시 문자열은 SimpleTunes로 통일되어 있다. `kr.co.hyomusic` 패키지와 upstream 네임스페이스는 임의로 바꾸지 않는다.

## 2. 확정 요구사항과 제외 범위

### 확정

- 기본 언어는 한국어이며 큰 글자·넓은 터치 영역을 사용한다.
- 짙은 남색 기반의 절제된 다크 UI를 유지한다. 검색, 재생목록, 장르별 추천, 최근 들은 음악, 자주 쓰는 재생목록, 고정 미니 플레이어를 핵심으로 한다.
- 작은 화면에서는 콘텐츠만 스크롤되고 미니 플레이어와 겹치지 않아야 한다.
- 버튼은 짧은 150~200ms 반응을 사용하고, 재생 중 항목에만 작은 상태 애니메이션을 사용한다. 상시 고비용 애니메이션은 금지한다.
- 연결된 Bluetooth가 끊기면 일시정지하고, 재연결 시 마지막 재생 재개를 시도한다. Android 백그라운드 제한으로 실패할 수 있으므로 알림/미디어 버튼을 대안으로 둔다.

### 제외

- MP3/파일 다운로드, 라디오, 다른 음원 플랫폼
- 로그인, 유료 콘텐츠, DRM·연령 제한·접근 통제 우회
- Gemini AI 재생목록 기능은 유지한다. 앱은 자연어 요청을 개인용 Cloud Run 프록시(`https://simpletunes-gemini-5df6beotgq-du.a.run.app/v1/playlist`)에 보내고, 프록시는 Google Cloud 서비스 계정의 ADC로 Vertex AI Gemini를 호출해 검색어를 반환한다. Gemini 자격 증명은 APK·Git·로그에 저장하지 않으며, 프록시가 반환한 검색어를 YouTube 검색 결과와 결합해 최대 10곡의 로컬 재생목록 추가 대화상자를 연다.

## 3. 현재 구조(코드 확인 기준)

- 기반: NewPipe 앱과 NewPipeExtractor를 포크한 멀티모듈 Gradle 프로젝트
- 모듈: `app`, `shared`, `desktopApp`; 설정에 로컬 NewPipeExtractor includeBuild 선택지가 있다.
- UI: Android XML/ViewBinding 중심. 현재 SimpleTunes 홈은 `fragment_main.xml` + `MainFragment.java`이며 Compose 홈이 아니다.
- 언어: Java 282개, Kotlin 149개 파일(현재 `app/src/main/java` 집계). Kotlin JVM toolchain 21.
- 앱 ID: `kr.co.hyomusic`; debug suffix `.debug`; 표시 리소스 `SimpleTunes`; upstream namespace는 유지.
- SDK/빌드: `app/build.gradle.kts`의 프로젝트 상수로 compile/target/min SDK를 지정하며, AGP 9.2.1, Kotlin 2.4.10이 version catalog에 있다.
- 라이선스: 소스 헤더와 `LICENSE`의 GPL-3.0-or-later 고지를 유지한다.

## 4. 실제 구현 상태

### 홈·검색·추천 — 구현됨(기능 검증 필요)

- 홈 검색 버튼은 `ServiceList.YouTube` 검색 화면을 연다.
- 재생목록 버튼은 NewPipe 북마크 화면을 연다.
- 홈 탐색 버튼은 고정 국가·언어·장르 대신 인기 음악 탐색, 최신 음악 탐색, 자유 장르 검색, 사용자 기록 기반 맞춤 재생, 즐겨찾기, 최근 재생으로 구성한다. 인기/최신 버튼은 공식 순위 데이터가 아니라 YouTube 검색 질의를 사용하므로 순위로 표시하지 않는다.
- 추천 엔진은 장르·지역·탐색 모드(지금 인기/인기 명곡/최신곡)를 선택하고 여러 YouTube 검색 후보를 모은 뒤 라이브·Shorts·커버·노래방·모음집·리액션·리믹스 등을 제외한다. 음악/장르 근거, 인증 채널 여부, 공식/오디오/가사 신호, 실제 조회수, 확인된 업로드 날짜를 조합해 최대 20곡을 중복 제거·정렬한다.
- 테마 선택은 `simple_tunes_theme` preference와 AlertDialog로 저장하며 홈 카드·툴바·상태/내비게이션 바 색을 함께 적용한다.
- 현재 화면에는 최근 재생 최대 30곡을 담는 고정 높이 내부 스크롤 박스와 자주 쓰는 재생목록 2개 영역이 있다. 실제 기록이 없으면 최근 섹션을 숨긴다.

### 기록·재생목록 — 부분 구현

- 최근 음악은 `HistoryRecordManager.getStreamHistory()`에서 최대 30개를 읽어 내부 스크롤 박스에 썸네일·제목·업로더·재생 버튼으로 표시한다. 내부 `NestedScrollView`에는 `nestedScrollingEnabled`를 XML로 지정하지 않는다. Android 16에서 뷰 helper 초기화 전 setter가 호출되어 NPE를 일으킨다.
- 로컬 재생목록은 `LocalPlaylistManager.getPlaylists()`에서 첫 목록만 빠른 접근으로 표시한다.
- 별도의 SimpleTunes 즐겨찾기 저장 모델은 없고 NewPipe 북마크/로컬 재생목록 구조를 사용한다. “최근”, “즐겨찾기”, “내 재생목록”의 의미를 UI에서 더 명확히 분리할 필요가 있다.
- 현재 재생 항목에만 작은 alpha equalizer 텍스트 애니메이션이 있다. 실제 오디오 FFT/스펙트럼 분석은 아니다.

### 맞춤·AI 추천

- 맞춤 추천 버튼은 `HistoryRecordManager`의 최근 기록을 최대 10곡으로 재생한다. 기록이 없으면 안내만 표시한다.
- Gemini 버튼은 자연어 요청을 Cloud Run `/v1/playlist` 프록시에 보내고, 프록시가 Vertex AI `gemini-2.5-flash`로 검색어 10개를 생성한다. 앱은 반환된 검색어로 YouTube에서 첫 결과를 모아 NewPipe의 재생목록 추가 대화상자를 사용한다. 앱에 Gemini API 키 입력 UI를 두지 않으며, Cloud Run 공개 엔드포인트는 개인 설치용으로만 제한한다.

### 추천 데이터 제약

- 저장소에는 YouTube Data API 키나 공식 차트 연동이 없다. `videos.list(chart=mostPopular)` 등 임의 공식 API 엔드포인트를 호출하지 않으며, NewPipeExtractor 검색 결과만 사용한다.
- 조회수는 후보 점수 신호로만 사용하고 공식 순위나 실시간 인기순으로 표시하지 않는다. 조회수 변화율은 데이터가 없으므로 계산하지 않는다.
- 추천 결과는 조건별로 12시간 SharedPreferences 캐시에 저장한다. 캐시 실패는 재생을 막지 않으며, 네트워크 검색 실패 시 만료되지 않은 캐시를 우선 사용할 수 있다.

### 재생·백그라운드·차량 — 코드 지원, 실기기 검증 필요

- `PlayerService`가 foreground media playback service로 실행되고 `MediaSessionCompat`, MediaBrowser, MediaSessionConnector를 사용한다.
- Android Auto/SystemUI의 `EXTRA_RECENT` 복원 요청은 실제 재생 기록 루트(`ID_HISTORY`)를 반환하며, 기록 항목은 기존 MediaBrowser 큐 생성 경로로 재생할 수 있다.
- `Player`/`AudioReactor`가 오디오 포커스와 `AUDIO_BECOMING_NOISY`를 처리한다.
- Bluetooth A2DP 장치 콜백과 재연결 시 resume 시도 코드가 있으며, `MEDIA_BUTTON` receiver/service와 AVRCP용 media session navigator가 선언되어 있다.
- Manifest에는 INTERNET, WAKE_LOCK, 네트워크 상태, foreground media playback, 알림 권한이 있다.
- 전화 수신, 특정 차량 헤드유닛, Android 최신 백그라운드 정책, 데이터 전환 조합은 실제 기기에서 아직 확인하지 않았다.

### 업데이트 — 부분 구현

- 기존 NewPipe 업데이트 설정/WorkManager 흐름을 유지하면서 `NewVersionWorker.kt`가 GitHub raw `update.json`을 읽는다.
- 현재 manifest와 APK 주소는 `jeonbj005-tech/hyomusic`의 `hyomusic` 브랜치를 가리킨다.
- 버전 코드 비교 후 업데이트 알림에서 브라우저 다운로드 주소를 열며, 자동 설치는 하지 않는다.
- 설정의 수동 업데이트 확인은 Debug와 release APK 모두 노출된다. Debug/custom APK는 서명이 다르므로 자동 설치가 아니라 GitHub APK 다운로드 알림/브라우저 열기만 지원한다.
- Debug APK는 최초 실행 시 GitHub 업데이트 확인을 기본 활성화하고, 사용자가 설정에서 끄면 예약 검사를 중단한다. Android 알림 권한이 승인된 뒤에도 첫 검사를 다시 예약한다.
- 홈 장르 제목 옆의 `업데이트 확인` 버튼은 동일한 GitHub manifest/WorkManager 경로를 수동 실행하고, 최신/새 버전/확인 실패를 앱 화면에 표시한다. 새 버전 다운로드는 HTTPS GitHub URL을 브라우저로 연다. 자동 설치는 하지 않는다.

## 5. 스트리밍 아키텍처와 제약

검색·추출·재생은 NewPipe의 YouTube service와 Extractor/Downloader 경로를 사용한다. 이는 공식 YouTube 플레이어 SDK가 아닌 비공식 추출 방식이므로 YouTube 변경, 지역/네트워크, 서비스 정책에 따라 검색·재생이 깨질 수 있다. 이 앱은 DRM이나 접근 통제를 우회하지 않으며, 이 제약을 사용자 문서와 오류 처리에서 명확히 한다.

NewPipe 원본 트리의 다운로드 관련 코드가 남아 있을 수 있으나 SimpleTunes 사용자 흐름에서 기능을 노출하거나 지원한다고 간주하지 않는다.

## 6. UI/UX 기준

- 배경은 짙은 남색, 카드는 낮은 대비의 블루 계열. 네온, 과도한 글로우, 큰 환영 배너와 장식용 빈 공간은 사용하지 않는다.
- 검색·재생목록·장르·최근 재생은 첫 화면에서 이해 가능해야 한다.
- 실제 데이터가 있을 때만 최근/자주 듣는 섹션을 표시한다. 데이터가 없으면 빈 카드 대신 짧은 안내나 선택 장르 검색 결과를 사용하되, fallback임을 표시한다.
- 인기·최신이라는 문구는 실제 YouTube 순위/최신 데이터가 확인된 경우에만 순위처럼 표현한다. 현재 구현은 검색 결과 탐색용이다.
- 콘텐츠 영역은 화면 크기와 글자 크기에 맞춰 스크롤하고 하단 미니 플레이어는 고정한다.
- 홈의 세로 여유 공간은 실제 데이터 섹션 사이에만 가변 간격으로 나눈다. 콘텐츠가 화면보다 길면 간격 확장 없이 스크롤한다.
- 재생 중 항목에는 작은 이퀄라이저/상태 표시만 적용하며, 애니메이션 감소 설정을 존중한다.

## 7. ChatGPT ↔ Codex 전달 절차

1. ChatGPT에서 확정된 요구사항을 이 문서 또는 `DECISIONS.md`에 요약한다.
2. 기존 결정과 충돌하는지 확인하고, 확정되지 않은 아이디어는 계획으로만 기록한다.
3. Codex는 실제 호출·저장·생명주기 코드를 확인한 뒤 필요한 파일만 수정한다.
4. 결과와 미검증 항목을 `DEV_STATUS.md`에 남긴다. 다른 채팅과 자동 동기화된다고 가정하지 않는다.
