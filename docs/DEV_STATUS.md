# SimpleTunes 개발 현황

기준일: 2026-10-18  
브랜치: `hyomusic`  
HEAD: 배포 준비 완료 (Gemini Cloud Run 프록시 연결)

## 버전·빌드

- 표시 버전: `0.29.1-hyo.22`
- versionCode: `1036`
- applicationId: `kr.co.hyomusic.debug` (debug build; 기본 ID는 `kr.co.hyomusic`)
- 저장소에 보관된 이전 배포 APK: `SimpleTunes-debug-v0.29.1-hyo.20.apk`
- 이번 배포 APK 이름: `SimpleTunes-debug-v0.29.1-hyo.22.apk`
- 이전 배포 APK SHA-256: `6278962DD332FA2CC2E1F824A4F6F7F24C4DE643B478AA221461680012269F66`
- 이번 빌드 산출물: `app/build/outputs/apk/debug/app-debug.apk`
- 이번 빌드 APK SHA-256: `C4E8DBF168C276682BACBD82C78604AFDABD53406329EDD5D523C4720DBA4D9F`
- 최근 검증 명령 성공(2026-10-18): `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest` (Cloud Run URL 기본값 포함)
- Checkstyle에서 발견된 100자 초과 한 줄을 수정한 뒤 재실행했으며, `assembleDebug`와 `testDebugUnitTest`가 모두 통과했다.

## 완료된 항목

- 표시 이름을 SimpleTunes로 적용하고 기존 패키지/namespace 호환성을 유지
- NewPipe 기반 YouTube 검색·추출·재생 경로 유지
- 검색, 재생목록/북마크 진입, 중립 탐색·지역·모드 선택 기반 최대 20곡 추천 큐
- 최근 기록 3개 카드(썸네일·제목·업로더·재생 버튼)
- 로컬 재생목록 빠른 접근 1개와 최근 통계 진입 1개
- 짙은 남색 테마, 4개 색상 테마 대화상자, 툴바·시스템 바 색상 연동
- 기존 foreground `PlayerService`, MediaSession/MediaBrowser, 미디어 알림 및 미니 플레이어
- 오디오 포커스·AUDIO_BECOMING_NOISY·Bluetooth A2DP 재연결 시도 코드
- GitHub `update.json` 기반 버전 확인과 설정의 수동 업데이트 확인
- Gemini AI 재생목록 요청을 Cloud Run `/v1/playlist` 프록시로 전환하고 앱의 API 키 입력·저장·헤더 전송을 제거
- Cloud Run 프록시는 Vertex AI Gemini를 ADC/서비스 계정으로 호출하며 `https://simpletunes-gemini-5df6beotgq-du.a.run.app`에 배포됨
- Cloud Run `/health`와 `/v1/playlist` 실제 호출 성공(잔잔한 팝 음악 요청에서 10개 검색어 반환); APK에는 API 키를 넣지 않음
- 최근 기록이 없을 때 fallback 검색을 제거하고 최근 섹션을 숨김
- 고정 Pop/Rock/Ballad/Jazz/Classical/Oldies 추천을 제거하고 중립적인 음악 탐색·맞춤·즐겨찾기·최근 재생 진입으로 변경
- `MusicRecommendationEngine` 추가: 음악 후보 필터링, 장르 근거·공식 채널 신호·조회수·업로드 날짜 점수화, 중복 제거, 최대 20곡, 12시간 캐시 및 디버그 근거 로그

## 부분 완료 / 검증 필요

- **인기/최신 탐색**: 현재 YouTube 검색 질의와 실제 후보 메타데이터 기반이며 공식 인기순위·최신 발매 데이터가 아니다. UI에서 순위로 오해되지 않게 유지해야 한다.
- **공식 API**: YouTube Data API 키/연동이 없어 공식 차트 후보 수집은 구현하지 않았다. 조회수 변화율도 제공되지 않아 추정하지 않는다.
- **실제 장르 결과 예시**: 이번 작업에서는 네트워크/실기기 호출을 수행하지 않아 발라드·힙합·팝·라틴의 실제 20곡 결과를 검증하지 않았다.
- **즐겨찾기 의미**: 별도 favorites 모델이 아니라 NewPipe 북마크/로컬 재생목록을 사용한다. UI 명칭과 실제 이동 경로를 정리해야 한다.
- **연속재생·이어듣기**: 기존 플레이어/재생 큐에는 지원 기반이 있으나 현재 SimpleTunes 홈의 전용 버튼 구현 여부는 확정하지 않았다.
- **업데이트**: GitHub manifest 확인과 브라우저 다운로드 링크는 Debug/release 설정 화면에 노출되며, 실기기 알림 확인은 아직 필요하다. APK 자동 설치는 하지 않는다.
- **차량·백그라운드**: 코드상 MediaSession, foreground service, Bluetooth 콜백이 있으나 화면 OFF, 앱 백그라운드, 전화 수신, 차량 해제/재연결, 네트워크·모바일 데이터 전환, 핸들 버튼은 실제 기기 검증 전이다.
- **화면 대응**: 홈은 `NestedScrollView`이지만 다양한 화면 크기·큰 글꼴·미니 플레이어 겹침을 실기기에서 확인하지 않았다.
- **Gemini**: 개인용 Cloud Run 공개 엔드포인트에 의존하며 사용량 제한·비용 관리가 필요하다. Vertex AI 자연어 요청은 서버에서 확인했지만 실기기 호출은 아직 검증하지 않았다.

## 알려진 문제·제약

- YouTube 비공식 추출 방식은 YouTube 변경이나 정책에 따라 검색/재생이 중단될 수 있다.
- 추천 후보 검색과 Gemini 검색어 확장은 네트워크에 의존하며, 재생 실패·네트워크 끊김의 재시도/오류 표시 정책을 별도 검증해야 한다.
- 추천 필터는 검색 메타데이터 기반 휴리스틱이며, 외부 장르 데이터베이스나 공식 아티스트 검증을 대체하지 않는다.
- `HYOMUSIC.md`는 이전 개발 로그라 현재 SimpleTunes 상태와 일부 표현이 다르다. 현재 문서 네 개를 기준 문서로 사용한다.
- 저장소의 `.codex-remote-attachments/`, `.gradle-user/`는 현재 추적하지 않는 생성/작업 파일이며 이번 작업에서 보존했다.
- 원본 NewPipe의 넓은 기능 트리가 남아 있어 “사용자에게 노출되는 SimpleTunes 기능”과 “소스에 존재하는 upstream 기능”을 구분해야 한다.

## 다음 우선순위

1. 발라드·힙합·팝·라틴의 실제 검색 결과를 실기기에서 확인하고 채택/제외 로그 품질을 점검한다.
2. Gemini 자연어 요청을 실기기에서 테스트하고 Cloud Run 사용량·비용을 모니터링한다.
3. 작은 화면·큰 글꼴·미니 플레이어 고정 상태와 Bluetooth 차량·화면 OFF·전화 수신·데이터 전환을 검증한다.

## 마지막 작업

2026-10-18: Gemini API 키를 APK에서 제거하고 Cloud Run/Vertex AI 프록시 호출 구조로 전환했다. Cloud Run 배포와 서버 자연어 요청을 확인하고 URL을 APK 기본 빌드 설정에 연결했다.
