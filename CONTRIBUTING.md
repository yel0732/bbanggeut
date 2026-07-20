# Contributing Guide

이 문서는 팀원이 같은 방식으로 작업하고, AI 에이전트가 예측 가능한 결과물을 만들 수 있도록 정한 기본 규칙입니다.

## 기본 원칙

- 작은 단위로 작업하고, 하나의 커밋에는 하나의 목적만 담습니다.
- 기능 구현 전에 사용자 흐름을 먼저 확인합니다.
- 유아 대상 서비스이므로 실패, 오답, 낮은 점수를 강조하는 표현은 피합니다.
- 영상, 음성, 일기 같은 민감 데이터는 저장과 전송 범위를 최소화하는 방향을 기본값으로 둡니다.
- 프론트엔드와 백엔드는 디렉터리 경계를 지킵니다.

## 브랜치 규칙

- 기본 브랜치: `master`
- 기능 작업: `feature/작업-이름`
- 버그 수정: `fix/작업-이름`
- 설정/문서 작업: `chore/작업-이름`

예시:

```bash
feature/child-home
feature/diary-flow
fix/report-list-layout
chore/frontend-conventions
```

## 커밋 규칙

커밋 메시지는 Conventional Commits 형식을 사용합니다.

```text
type: summary
```

주요 타입:

- `feat`: 사용자 기능 추가
- `fix`: 버그 수정
- `chore`: 설정, 패키지, 빌드 등 비기능 변경
- `docs`: 문서 변경
- `style`: 포맷팅, 스타일 정리
- `refactor`: 동작 변경 없는 구조 개선
- `test`: 테스트 추가/수정

예시:

```text
feat: add child home route
fix: handle empty diary list
docs: add frontend conventions
chore: configure prettier
```

## Pull Request 규칙

PR에는 아래 내용을 포함합니다.

- 작업 요약
- 확인한 명령어
- 화면 변경이 있으면 스크린샷
- 남은 이슈나 결정이 필요한 부분

프론트엔드 변경 시 기본 확인 명령어:

```bash
cd frontend
npm run format:check
npm run lint
npm run build
```

## 리뷰 기준

- 사용자 흐름이 서비스 정의와 맞는지
- 유아에게 부정적인 피드백을 주지 않는지
- 부모 화면이 일기/리포트 정보를 명확히 전달하는지
- 타입, 라우팅, 상태 관리가 과하게 복잡하지 않은지
- 개인정보를 불필요하게 저장하거나 노출하지 않는지

## 문서 업데이트 기준

아래 변경이 있으면 관련 문서를 같이 수정합니다.

- 라우트 추가 또는 삭제
- 환경변수 추가
- 실행 명령어 변경
- 공통 컴포넌트 규칙 변경
- API 연동 방식 변경
