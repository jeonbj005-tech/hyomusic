# SimpleTunes 개발 현황

기준일: 2026-10-18  
브랜치: `hyomusic`  
HEAD: `78080de0b` (`Localize update settings for SimpleTunes`)

## 버전·빌드

- 표시 버전: `0.29.1-hyo.20`
- versionCode: `1034`
- applicationId: `kr.co.hyomusic.debug` (debug build; 기본 ID는 `kr.co.hyomusic`)
- 저장소에 보관된 이전 배포 APK: `SimpleTunes-debug-v0.29.1-hyo.20.apk`
- 이전 배포 APK SHA-256: `6278962DD332FA2CC2E1F824A4F6F7F24C4DE643B478AA221461680012269F66`
- 이번 빌드 산출물: `app/build/outputs/apk/debug/app-debug.apk`
- 이번 빌드 APK SHA-256: `A0D9248C966256256C7C774292C50455286E238CD76439F6952B82C4FC079703`
- 최근 검증 명령 성공(2026-10-18): `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest`
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
- Gemini AI 재생목록 기능 복원: 사용자 API 키 검증·자연어 요청·Gemini 검색어 생성·YouTube 결과 수집·재생목록 추가 대화상자
- 최근 기록이 없을 때 fallback 검색을 제거하고 최근 섹션을 숨김
- 고정 Pop/Rock/Ballad/Jazz/Classical/Oldies 추천을 제거하고 중립적인 음악 탐색·맞춤·즐겨찾기·최근 재생 진입으로 변경
- `MusicRecommendationEngine` 추가: 음악 후보 필터링, 장르 근거·공식 채널 신호·조회수·업로드 날짜 점수화, 중복 제거, 최대 20곡, 12시간 캐시 및 디버그 근거 로그

## 부분 완료 / 검증 필요

- **인기/최신 탐색**: 현재 YouTube 검색 질의와 실제 후보 메타데이터 기반이며 공식 인기순위·최신 발매 데이터가 아니다. UI에서 순위로 오해되지 않게 유지해야 한다.
- **공식 API**: YouTube Data API 키/연동이 없어 공식 차트 후보 수집은 구현하지 않았다. 조회수 변화율도 제공되지 않아 추정하지 않는다.
- **실제 장르 결과 예시**: 이번 작업에서는 네트워크/실기기 호출을 수행하지 않아 발라드·힙합·팝·라틴의 실제 20곡 결과를 검증하지 않았다.
- **즐겨찾기 의미**: 별도 favorites 모델이 아니라 NewPipe 북마크/로컬 재생목록을 사용한다. UI 명칭과 실제 이동 경로를 정리해야 한다.
- **연속재생·이어듣기**: 기존 플레이어/재생 큐에는 지원 기반이 있으나 현재 SimpleTunes 홈의 전용 버튼 구현 여부는 확정하지 않았다.
- **업데이트**: GitHub manifest 확인과 브라우저 다운로드 링크는 구현됐지만 debug 서명에서 자동 동의/알림 흐름은 실기기 확인이 필요하며 APK 자동 설치는 하지 않는다.
- **차량·백그라운드**: 코드상 MediaSession, foreground service, Bluetooth 콜백이 있으나 화면 OFF, 앱 백그라운드, 전화 수신, 차량 해제/재연결, 네트워크·모바일 데이터 전환, 핸들 버튼은 실제 기기 검증 전이다.
- **화면 대응**: 홈은 `NestedScrollView`이지만 다양한 화면 크기·큰 글꼴·미니 플레이어 겹침을 실기기에서 확인하지 않았다.
- **Gemini**: API 키·모델 가용성·쿼터·네트워크에 따라 실패할 수 있으며 실기기에서 새 복원 경로를 아직 검증하지 않았다.

## 알려진 문제·제약

- YouTube 비공식 추출 방식은 YouTube 변경이나 정책에 따라 검색/재생이 중단될 수 있다.
- 추천 후보 검색과 Gemini 검색어 확장은 네트워크에 의존하며, 재생 실패·네트워크 끊김의 재시도/오류 표시 정책을 별도 검증해야 한다.
- 추천 필터는 검색 메타데이터 기반 휴리스틱이며, 외부 장르 데이터베이스나 공식 아티스트 검증을 대체하지 않는다.
- `HYOMUSIC.md`는 이전 개발 로그라 현재 SimpleTunes 상태와 일부 표현이 다르다. 현재 문서 네 개를 기준 문서로 사용한다.
- 저장소의 `.codex-remote-attachments/`, `.gradle-user/`는 현재 추적하지 않는 생성/작업 파일이며 이번 작업에서 보존했다.
- 원본 NewPipe의 넓은 기능 트리가 남아 있어 “사용자에게 노출되는 SimpleTunes 기능”과 “소스에 존재하는 upstream 기능”을 구분해야 한다.

## 다음 우선순위

1. 발라드·힙합·팝·라틴의 실제 검색 결과를 실기기에서 확인하고 채택/제외 로그 품질을 점검한다.
2. Gemini 복원 경로와 추천 캐시 만료·네트워크 장애 fallback을 실제 API 키/네트워크에서 테스트한다.
3. 작은 화면·큰 글꼴·미니 플레이어 고정 상태와 Bluetooth 차량·화면 OFF·전화 수신·데이터 전환을 검증한다.

## 마지막 작업

2026-10-18: 고정 장르 추천을 중립적인 탐색 구조로 바꾸고, 최근 기록이 없을 때 섹션을 숨기며, Gemini 재생목록 기능을 복원했다. `MusicRecommendationEngine`으로 후보 필터·점수화·중복 제거·12시간 캐시를 추가하고 문서를 갱신했다.
