# SimpleTunes 개발 현황

기준일: 2026-10-19
브랜치: `hyomusic`  
HEAD: 최근 기록이 없을 때 홈 여백 접기

## 버전·빌드

- 표시 버전: `0.29.1-hyo.30`
- versionCode: `1044`
- applicationId: `kr.co.hyomusic.debug` (debug build; 기본 ID는 `kr.co.hyomusic`)
- 저장소에 보관된 직전 배포 APK: `SimpleTunes-debug-v0.29.1-hyo.26.apk`
- 이번 배포 APK 이름: `SimpleTunes-debug-v0.29.1-hyo.30.apk`
- 직전 배포 APK SHA-256: `4526297CB03C95DD4507217C08B26CE28F554B017F74156F339BBE6FF4B75CF8`
- 이번 빌드 산출물: `app/build/outputs/apk/debug/app-debug.apk`
- 이번 빌드 APK SHA-256: `976F91975E6A82C626783571AC49B7B833A6A4DFF34658719B5846A7B874B721`
- 검증 명령 성공(2026-10-19): `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest`
- 최근 기록이 없을 때 재생목록 앞 가변 여백을 접는 수정 후 `assembleDebug`와 `testDebugUnitTest`가 모두 통과했다.

## 완료된 항목

- 표시 이름을 SimpleTunes로 적용하고 기존 패키지/namespace 호환성을 유지
- NewPipe 기반 YouTube 검색·추출·재생 경로 유지
- 검색, 재생목록/북마크 진입, 중립 탐색·지역·모드 선택 기반 최대 20곡 추천 큐
- 최근 재생 최대 30곡을 내부 스크롤 박스에 표시(썸네일·제목·업로더·재생 버튼); 실제 재생 기록이 없으면 섹션 숨김. Android 16 inflate 충돌 수정 완료, 실기기 확인 필요
- 로컬 재생목록 빠른 접근 1개와 최근 통계 진입 1개
- 짙은 남색 테마, 4개 색상 테마 대화상자, 툴바·시스템 바 색상 연동
- 기존 foreground `PlayerService`, MediaSession/MediaBrowser, 미디어 알림 및 미니 플레이어
- 오디오 포커스·AUDIO_BECOMING_NOISY·Bluetooth A2DP 재연결 시도 코드
- GitHub `update.json` 기반 버전 확인과 설정의 수동 업데이트 확인
- 홈에서 직접 실행 가능한 업데이트 확인 버튼: 최신/새 버전/오류 결과 표시, 알림 권한 없이도 다운로드 브라우저 열기 가능
- 홈 화면의 세로 남는 공간을 최근 음악/재생목록 사이에 가변 배분; 내용이 넘치면 스크롤 유지
- Gemini AI 재생목록 요청을 Cloud Run `/v1/playlist` 프록시로 전환하고 앱의 API 키 입력·저장·헤더 전송을 제거
- Cloud Run 프록시는 Vertex AI Gemini를 ADC/서비스 계정으로 호출하며 `https://simpletunes-gemini-5df6beotgq-du.a.run.app`에 배포됨
- Cloud Run `/health`와 `/v1/playlist` 실제 호출 성공(잔잔한 팝 음악 요청에서 10개 검색어 반환); APK에는 API 키를 넣지 않음
- 큰 글자 설정에서 재생목록 버튼이 줄바꿈되지 않도록 폭·한 줄 표시를 보완함; 실기기 재검증 필요
- Debug APK에서 GitHub 업데이트 확인을 기본 활성화하고 알림 권한 승인 직후에도 업데이트 검사를 예약하도록 보완함; 자동 설치는 하지 않음
- 최근 기록이 없을 때 fallback 검색을 제거하고 최근 섹션을 숨김
- 고정 Pop/Rock/Ballad/Jazz/Classical/Oldies 추천을 제거하고 중립적인 음악 탐색·맞춤·즐겨찾기·최근 재생 진입으로 변경
- `MusicRecommendationEngine` 추가: 음악 후보 필터링, 장르 근거·공식 채널 신호·조회수·업로드 날짜 점수화, 중복 제거, 최대 20곡, 12시간 캐시 및 디버그 근거 로그
- Android Auto/SystemUI의 최근 재생 복원 요청에 실제 재생 기록 루트를 반환하도록 변경

## 부분 완료 / 검증 필요

- **인기/최신 탐색**: 현재 YouTube 검색 질의와 실제 후보 메타데이터 기반이며 공식 인기순위·최신 발매 데이터가 아니다. UI에서 순위로 오해되지 않게 유지해야 한다.
- **공식 API**: YouTube Data API 키/연동이 없어 공식 차트 후보 수집은 구현하지 않았다. 조회수 변화율도 제공되지 않아 추정하지 않는다.
- **실제 장르 결과 예시**: 이번 작업에서는 네트워크/실기기 호출을 수행하지 않아 발라드·힙합·팝·라틴의 실제 20곡 결과를 검증하지 않았다.
- **즐겨찾기 의미**: 별도 favorites 모델이 아니라 NewPipe 북마크/로컬 재생목록을 사용한다. UI 명칭과 실제 이동 경로를 정리해야 한다.
- **연속재생·이어듣기**: 기존 플레이어/재생 큐에는 지원 기반이 있으나 현재 SimpleTunes 홈의 전용 버튼 구현 여부는 확정하지 않았다.
- **업데이트**: GitHub manifest 수동 확인 버튼은 홈과 설정에 있다. 홈 버튼은 새 버전 확인 결과와 다운로드 링크를 직접 표시한다. 자동 설치 및 실제 기기 업데이트 설치는 검증하지 않았다.
- **차량·백그라운드**: Android Auto 최근 재생 복원 경로와 기존 Bluetooth 오디오 콜백이 코드에 있으나 화면 OFF, 앱 백그라운드, 전화 수신, 차량 시동 후 Android Auto 앱 선택, 연결/해제, 네트워크·모바일 데이터 전환, 핸들 버튼은 실제 기기 검증 전이다.
- **화면 대응**: 홈의 가변 세로 간격 변경은 빌드됐으나, 에뮬레이터 APK 설치가 ADB 연결 종료로 실패해 화면을 확인하지 못했다. 작은 화면·큰 글꼴·미니 플레이어 겹침은 실기기 확인이 필요하다.
- **Gemini**: 개인용 Cloud Run 공개 엔드포인트에 의존하며 사용량 제한·비용 관리가 필요하다. Vertex AI 자연어 요청은 서버에서 확인했지만 실기기 호출은 아직 검증하지 않았다.

## 알려진 문제·제약

- YouTube 비공식 추출 방식은 YouTube 변경이나 정책에 따라 검색/재생이 중단될 수 있다.
- 추천 후보 검색과 Gemini 검색어 확장은 네트워크에 의존하며, 재생 실패·네트워크 끊김의 재시도/오류 표시 정책을 별도 검증해야 한다.
- 추천 필터는 검색 메타데이터 기반 휴리스틱이며, 외부 장르 데이터베이스나 공식 아티스트 검증을 대체하지 않는다.
- `HYOMUSIC.md`는 이전 개발 로그라 현재 SimpleTunes 상태와 일부 표현이 다르다. 현재 문서 네 개를 기준 문서로 사용한다.
- 저장소의 `.codex-remote-attachments/`, `.gradle-user/`는 현재 추적하지 않는 생성/작업 파일이며 이번 작업에서 보존했다.
- 원본 NewPipe의 넓은 기능 트리가 남아 있어 “사용자에게 노출되는 SimpleTunes 기능”과 “소스에 존재하는 upstream 기능”을 구분해야 한다.

## 다음 우선순위

1. 실제 차량 Android Auto에서 시동 후 SimpleTunes 복원과 핸들 미디어 버튼을 확인한다.
2. Bluetooth 해제/재연결, 화면 OFF, 전화 수신, 모바일 데이터 전환을 실기기에서 검증한다.
3. 발라드·힙합·팝·라틴 추천 결과와 Gemini 자연어 요청을 실기기에서 확인한다.

## 마지막 작업

2026-10-19: hyo.28 ACRA 보고서에서 Android 16 `NestedScrollView.setNestedScrollingEnabled()` 초기화 순서 NPE를 확인해 hyo.29에서 XML 속성을 제거했다. 후속 화면에서 최근 기록이 없을 때 재생목록 위에 큰 공백이 남는 문제를 확인해 해당 조건에서 두 가변 spacer를 숨기도록 수정했다. hyo.30 빌드·단위 테스트 통과; Samsung 실기기 확인은 남아 있다.

## 이번 작업 기록

- 실제 기기 검증: 미수행. 사용자가 전달한 Samsung Android 16 ACRA 스택과 일치하는 XML 원인을 수정했으며, 재설치 후 확인 필요
- 다음 우선순위: Samsung 실기기에 hyo.30 설치 후 기록 없음/있음 상태의 홈 간격과 최근 음악 내부 스크롤 확인
