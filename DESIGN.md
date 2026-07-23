# Design

## Source of truth

- Status: Active
- Last refreshed: 2026-07-23
- Primary product surfaces: 시작, 이메일 인증, 프로필 선택, 아이 홈, 게임·대화 진입
- Evidence reviewed: `docs/UI_UX_REQUIREMENTS.md`, `docs/DESIGN_SYSTEM.md`, `frontend/src/index.css`, `frontend/src/pages/*`

## Brand

- Personality: 따뜻하고 밝은 숲속 모험, 아이에게 친근하고 부모에게 안심을 주는 분위기
- Trust signals: 명확한 입력 상태, 쉬운 닫기·뒤로가기, 예측 가능한 선택 결과
- Avoid: 브라우저 기본 UI가 갑자기 드러나는 화면, 실패를 강조하는 문구, 과도한 장식과 움직임

## Product goals

- Goals: 아이의 놀이 기록과 부모의 관찰 흐름을 쉽고 즐겁게 연결한다.
- Non-goals: 의료·발달 진단처럼 보이는 평가, 장식만을 위한 복잡한 상호작용
- Success signals: 첫 화면이 빠르게 보이고, 주요 조작이 한 번의 클릭 또는 탭으로 이해된다.

## Personas and jobs

- Primary personas: 놀이를 진행하는 아이, 가입·프로필·리포트를 관리하는 부모
- User jobs: 놀이 시작, 보호자 가입, 아이 선택, 기록 확인
- Key contexts of use: 모바일과 데스크톱 브라우저, 터치 및 마우스 입력

## Information architecture

- Primary navigation: 시작 → 로그인/가입 → 프로필 선택 → 아이/부모 모드
- Core routes/screens: `/`, `/auth/login`, `/auth/signup`, `/profiles`, `/child`, `/games`
- Content hierarchy: 한 화면의 대표 행동을 가장 크게, 보조 행동은 가장자리 또는 모달에 배치한다.

## Design principles

- Principle 1: 입력의 보이는 영역 전체가 동일한 행동을 수행한다.
- Principle 2: 첫 화면의 핵심 이미지와 상호작용을 장식 이미지보다 먼저 준비한다.
- Tradeoffs: 작은 장식은 지연 표시할 수 있지만, 배경·로고·현재 캐릭터는 즉시 표시한다.

## Visual language

- Color: 잎사귀 초록, 햇빛 노랑, 나무·종이의 따뜻한 갈색을 중심으로 한다.
- Typography: 기본 sans-serif와 놀이 화면의 `Jua`를 사용한다.
- Spacing/layout rhythm: 큰 터치 영역과 여유 있는 간격을 우선한다.
- Shape/radius/elevation: 둥근 나무판과 잎사귀 형태, 부드러운 그림자와 얇은 내부 테두리
- Motion: 짧은 확대·부유 효과만 사용하고 `prefers-reduced-motion`을 존중한다.
- Imagery/iconography: WebP 장식 이미지와 단순한 선형 SVG 아이콘을 사용한다.

## Components

- Existing components to reuse: 공통 버튼·카드 토큰, 시작/인증 화면의 나무판 시각 언어
- New/changed components: 이메일 가입용 `BirthDatePicker` 커스텀 캘린더
- Variants and states: 달력, 연도 선택, 월 선택, 선택됨, 비활성 날짜
- Token/component ownership: 전역 색상·커서는 `frontend/src/index.css`, 날짜 선택 동작은 `frontend/src/components/ui/BirthDatePicker.tsx`

## Accessibility

- Target standard: WCAG 2.1 AA를 지향한다.
- Keyboard/focus behavior: 날짜 입력 전체를 버튼으로 제공하고, Escape로 모달을 닫으며, 선택 화면에 명확한 포커스 스타일을 둔다.
- Contrast/readability: 텍스트는 밝은 종이색 배경 위 짙은 갈색 또는 초록으로 표시한다.
- Screen-reader semantics: 모달은 `dialog`, 날짜 그리드는 의미 있는 버튼 레이블을 사용한다.
- Reduced motion and sensory considerations: 시스템의 모션 감소 설정에서 반복 애니메이션을 중지한다.

## Responsive behavior

- Supported breakpoints/devices: 320px 이상 모바일과 일반 데스크톱
- Layout adaptations: 캘린더는 작은 화면에서 좌우 여백 16px을 유지하고 내부 크기를 축소한다.
- Touch/hover differences: 날짜와 헤더 선택 영역은 최소 40px 내외의 터치 영역을 확보한다.

## Interaction states

- Loading: 라우트 전환 중 짧고 차분한 로더를 표시한다.
- Empty: 생년월일 미선택 시 `연도 / 월 / 일` 안내를 표시한다.
- Error: 가입 조건을 충족하지 못한 경우 입력 아래에 짧게 안내한다.
- Success: 선택한 날짜와 가입 가능 상태를 초록색으로 표시한다.
- Disabled: 가입 불가 날짜와 제출 버튼은 시각·동작 모두 비활성화한다.
- Offline/slow network, if applicable: 핵심 화면 이미지를 먼저, 숨겨진 장식과 애니메이션 프레임을 이후에 준비한다.

## Content voice

- Tone: 짧고 긍정적이며 안심되는 문장
- Terminology: 보호자 생년월일, 연도, 월, 일
- Microcopy rules: 행동과 결과를 직접 설명하고 기술 용어를 노출하지 않는다.

## Implementation constraints

- Framework/styling system: React, TypeScript, Vite, Tailwind CSS v4
- Design-token constraints: 기존 `leaf`, `sun`, `ink`, `ground`, `paper` 토큰을 우선한다.
- Performance constraints: 화면별 코드 분할, 핵심 이미지 우선순위 지정, 비가시 이미지 지연 준비
- Compatibility constraints: 브라우저 기본 날짜 선택기에 의존하지 않는다.
- Test/screenshot expectations: format, lint, build와 모바일·데스크톱 수동 확인

## Open questions

- [ ] 실제 가입 API가 허용하는 생년월일 최소·최대 범위를 백엔드 계약 확정 시 동기화한다.
