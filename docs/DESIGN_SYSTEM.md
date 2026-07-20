# Design System

초기 화면 개발에서 공통으로 사용할 디자인 기준입니다. Tailwind CSS v4의 `@theme` 토큰을 `frontend/src/index.css`에 정의합니다.

## 방향

- 아이 화면은 밝고 명확하게, 조작 대상은 크게 만듭니다.
- 부모 화면은 차분하고 정보 탐색이 쉽도록 구성합니다.
- 유아 대상 서비스이므로 부정적 피드백을 강조하는 색과 문구는 피합니다.
- 카드와 버튼 radius는 기본 8px 이하를 사용합니다.

## Color Tokens

| Token | 용도 |
| --- | --- |
| `leaf` | 주 색상, 긍정 피드백, 주요 버튼 |
| `sun` | 보조 강조, 부모 영역 포인트 |
| `berry` | 제한적 강조, 알림 |
| `ink` | 본문과 제목 |
| `ground` | 화면 배경 |
| `paper` | 패널, 카드 배경 |

예시:

```tsx
<button className="rounded-control bg-leaf-600 text-white">시작하기</button>
```

## Radius

- `rounded-control`: 버튼, 입력, 작은 패널
- 큰 장식용 radius는 사용하지 않습니다.

## Shadow

- `shadow-panel`: 화면 패널과 카드의 기본 그림자
- 반복 카드가 많아지는 화면에서는 그림자를 줄이고 border 중심으로 구성합니다.

## Typography

- 기본 폰트는 시스템 sans-serif입니다.
- hero나 첫 화면 외에는 과하게 큰 제목을 쓰지 않습니다.
- 버튼 안 텍스트는 줄바꿈이 생기지 않도록 짧게 작성합니다.

## Component Guidelines

- 버튼: 명령형 텍스트를 짧게 사용합니다.
- 카드: 반복 정보 항목에만 사용합니다.
- 아이용 액션: 터치 영역을 넉넉하게 둡니다.
- 부모용 리스트: 날짜, 아이 이름, 상태를 한눈에 볼 수 있게 둡니다.
