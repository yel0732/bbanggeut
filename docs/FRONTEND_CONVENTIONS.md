# Frontend Conventions

프론트엔드 개발 시 적용하는 기본 규칙입니다.

## 기술 스택

- React
- TypeScript
- Vite
- Tailwind CSS
- React Router
- Oxlint
- Prettier

## 디렉터리 규칙

```text
frontend/src/
  pages/      화면 단위 컴포넌트
  routes/     라우터 설정
  assets/     정적 에셋
```

추후 기능이 커지면 아래 디렉터리를 추가합니다.

```text
components/  공통 UI 컴포넌트
features/    기능 단위 모듈
hooks/       공통 React hooks
lib/         외부 연동, 유틸리티
types/       공통 타입
```

## 네이밍

- 컴포넌트: `PascalCase`
- 파일명: 컴포넌트 파일은 `PascalCase.tsx`
- 변수/함수: `camelCase`
- 상수: `UPPER_SNAKE_CASE`
- 라우트 경로: `kebab-case`

예시:

```text
ChildHomePage.tsx
DiaryArchivePage.tsx
report-summary.ts
```

## 라우팅

- 라우터 정의는 `src/routes/router.tsx`에 둡니다.
- 페이지 컴포넌트는 `src/pages/`에서 export합니다.
- 화면이 추가될 때는 URL, 페이지 컴포넌트, 접근 사용자 타입을 함께 고려합니다.

초기 주요 라우트:

- `/`: 서비스 개요
- `/child`: 아이 홈
- `/parent`: 부모 홈
- `/diaries`: 일기 모아보기
- `/reports`: 성장 리포트

## 스타일

- Tailwind CSS 유틸리티 클래스를 기본으로 사용합니다.
- 별도 CSS 파일은 전역 스타일, Tailwind import, 진짜 공통 토큰이 필요할 때만 사용합니다.
- 버튼, 카드, 입력 요소는 8px 이하 radius를 기본으로 합니다.
- 유아용 화면은 조작 대상을 크게 만들고 문장을 짧게 유지합니다.
- 부모용 화면은 정보 스캔이 쉽도록 차분하고 밀도 있게 구성합니다.

## TypeScript

- `any` 사용은 피합니다.
- API 응답 타입은 실제 연동 시 별도 타입으로 분리합니다.
- nullable 값은 화면에서 명시적으로 처리합니다.
- 컴포넌트 props는 가까운 파일에 두고, 여러 곳에서 쓰이면 `types/`로 옮깁니다.

## 환경변수

- 클라이언트에서 쓰는 환경변수는 `VITE_` prefix를 사용합니다.
- 실제 값은 `.env.local`에 둡니다.
- 공유 가능한 예시는 `.env.example`에 둡니다.
- 비밀키는 프론트엔드 환경변수에 두지 않습니다.

현재 예시:

```text
VITE_API_BASE_URL=http://localhost:8080
```

## 검증

작업 전후로 아래 명령을 기준으로 확인합니다.

```bash
npm run format:check
npm run lint
npm run build
```
