# 효도뮤직 개발 및 검증 기록

효도뮤직은 부모님 개인 사용을 위한 YouTube 음악 스트리밍 전용 Android 앱입니다.
NewPipe의 플레이어와 추출기 호환성을 유지하기 위해 작은 UI·동작 변경만 적용했습니다.

## 기반과 라이선스

- 기준 소스: [TeamNewPipe/NewPipe v0.29.1](https://github.com/TeamNewPipe/NewPipe/releases/tag/v0.29.1)
- 포함 추출기: [TeamNewPipe/NewPipeExtractor v0.26.5](https://github.com/TeamNewPipe/NewPipeExtractor/releases/tag/v0.26.5)
- 빌드 환경: JDK 21, Gradle wrapper 9.6.1, Android SDK 37
- 라이선스: GPL-3.0-or-later. 루트 `LICENSE`, 원본 파일의 저작권 고지와 SPDX 표기를 보존합니다.

## 사용자에게 제공하는 기능

- 한글 대형 버튼 홈: 음악 검색, 즐겨찾기(로컬/원격 재생목록), 전체 연속재생, 마지막 음악 이어듣기
- 검색 결과의 노래를 누르면 NewPipe 오디오 플레이어로 바로 재생
- YouTube 검색 필터 중 전체, 노래, 채널, 재생목록만 노출
- 포그라운드 미디어 서비스에 의한 화면 잠금·백그라운드 재생
- MediaSession 기반 블루투스/차량 재생·일시정지·이전·다음 버튼
- 오디오 출력 해제 시 일시정지, 실행 중인 플레이어에서 Bluetooth A2DP가 다시 연결되면 재생 재시도
- 기존 NewPipe의 재생 오류 표시, 네트워크 오류 처리, 재생 위치 복구

`전체 연속재생`은 재생 기록의 YouTube 항목을 최근 순서대로 큐에 넣습니다.
`마지막 음악 이어듣기`는 가장 최근 YouTube 항목 하나를 저장 위치에서 재개합니다.
기록이 없으면 먼저 한 곡을 재생하라는 안내를 표시합니다.

## 의도적으로 제외한 기능

- 다운로드 화면, 다운로드 저장 권한, MP3/파일 다운로드 진입점
- 라디오, SoundCloud, PeerTube, Bandcamp, media.ccc.de 등 다른 콘텐츠 서비스 선택
- 로그인, 유료 콘텐츠, 연령 제한, DRM 또는 접근 통제 우회
- 팝업 영상 재생과 영상 중심 홈/추천 UI

다운로드 구현 파일 자체는 upstream 변경을 작게 유지하기 위해 소스 트리에 남아 있지만,
manifest 등록·저장 권한·메뉴·상세 화면 진입점은 제거했습니다.

## 정책 및 기술적 제한

이 앱은 YouTube 공식 앱이나 공식 YouTube API 클라이언트가 아닙니다. NewPipeExtractor를
이용한 비공식 추출 및 백그라운드 스트리밍은 YouTube 이용약관 또는 정책과 충돌할 수
있고, YouTube 측 변경으로 예고 없이 작동하지 않을 수 있습니다. 배포 전 사용자가 현재
정책과 적용 법률을 직접 확인해야 합니다.

Android가 앱 프로세스나 포그라운드 서비스를 종료한 뒤에는 Bluetooth 재연결만으로 앱을
자동 시작하지 않습니다. 이 경우 알림 또는 차량 미디어 버튼으로 재생을 다시 시작해야
합니다. 실제 차량의 AVRCP 구현 차이도 있어 차량별 검증이 필요합니다.

## 빌드

PowerShell에서 Android Studio 내장 JDK 21을 사용합니다.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug testDebugUnitTest --stacktrace -DskipFormatKtlint
```

APK 출력 위치는 `app/build/outputs/apk/debug/app-debug.apk`입니다.

## 검증 기록

- 변경 전 원본 v0.29.1: `assembleDebug testDebugUnitTest` 성공
- 변경 후: `assembleDebug testDebugUnitTest` 성공(156개 테스트, 실패/오류/건너뜀 0)
- 변경 후: `lintDebug` 태스크 완료. 변경하지 않은 upstream 코드의 기존 Error 진단 2건
  (`AboutActivity` lint 해석 실패, `KeyboardUtil` 플래그)과 Warning 347건은 보고서에 남아 있음
- APK 확인: `kr.co.hyomusic.debug`, 표시 이름 `효도뮤직`, debug v1/v2 서명 검증 성공
- 현재 APK: `HyoMusic-debug-v0.29.1-hyo.2.apk`, versionCode 1016
- 최신 홈: `SimpleTunes`, 장르별 YouTube 음악 최대 10곡 큐(Pop/Rock/Ballad/Jazz/Classical/Oldies)
- 최신 APK: `SimpleTunes-debug-v0.29.1-hyo.3.apk`, versionCode 1017
- 홈 화면 MaterialComponents 테마 오류 수정: AppCompatButton 사용을 APK XML에서 확인
- 현재 APK SHA-256: `1CA75411DC80EC86DC8C10C6D82433EDA24CED3CF7A56CAB5FF48094C606DB02`
- hyo.2: `assembleDebug` 성공. 에뮬레이터 설치는 `connect error for write: closed`로 실패
- 실제 Android 기기/차량: 에뮬레이터가 offline 상태라 설치 및 실기기 시나리오 미실행

실기기에서는 화면 꺼짐, 앱 백그라운드, 전화 수신/종료, 차량 Bluetooth 연결/해제,
Wi-Fi↔모바일 데이터 전환, 네트워크 단절 후 복구를 각각 확인해야 합니다.
