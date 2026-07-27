# 게임 방법 최종 에셋 안내

기존 7단계에서 일시정지 안내와 일시정지 화면을 제외한 **게임별 5단계** 기준이다.

## 저장소 배치

```text
docs/
└─ TUTORIAL.md

frontend/src/assets/images/tutorial/
├─ common/
│  ├─ common-greeting.webm
│  └─ common-celebration.webm
├─ fruit/
│  ├─ fruit-slice-demo.webm
│  └─ dino-fruit-left-arm-2poses-low.png
└─ dobble/
   ├─ dobble-open-fist-demo.webm
   ├─ dino-hands-open.png
   └─ dino-hands-fist.png
```

`reused/common`, `reused/fruit`, `reused/dobble`로 표시한 파일은 이미 프로젝트의 기존 이미지 폴더에 있으므로 중복 복사하지 않고 현재 파일을 import한다.

## 1. 최종 애니메이션

| 파일 | 사용 장면 | 용도 | 재생 방식 |
| --- | --- | --- | --- |
| `common-greeting.webm` | 두 게임 1단계 | 캐릭터 인사 | 반복 가능 |
| `fruit-slice-demo.webm` | 후르츠류 2단계 | 왼팔을 들었다가 아래로 내리치는 2포즈 시범 | 반복 가능 |
| `dobble-open-fist-demo.webm` | 도블류 2단계 | 같은 위치에서 손 펴기 → 주먹 쥐기 → 손 펴기 | 반복 가능 |
| `common-celebration.webm` | 두 게임 5단계 | 학습 완료 축하 | 반복 가능 |

네 영상은 모두 투명 배경 WebM이다. 영상 안에는 과일, 동물 카드, 말풍선, 버튼을 넣지 않았으므로 실제 게임 UI 위에 겹쳐서 사용한다.

## 2. 편집용 최종 PNG

| 파일 | 용도 |
| --- | --- |
| `dino-fruit-left-arm-2poses-low.png` | 후르츠 자르기 시범의 두 포즈 원본 |
| `dino-hands-open.png` | 도블류 손을 편 정지 화면 또는 영상 대체 이미지 |
| `dino-hands-fist.png` | 도블류 주먹을 쥔 정지 화면 또는 영상 대체 이미지 |

이 PNG들은 WebM을 수정하거나, 저사양 환경에서 애니메이션을 두 장의 이미지 전환으로 대체할 때 사용한다. 기본 화면에서는 WebM과 PNG를 동시에 표시하지 않는다.

## 3. 공통 재사용 에셋

`reused/common`

| 파일 | 용도 |
| --- | --- |
| `game-select-bg.webp` | 1단계 소개 및 5단계 시작 화면 배경 |
| `bubble-big.webp` | 캐릭터 안내 말풍선 |
| `guide-board.webp` | 설명 패널 |
| `guide-start-btn.webp` | 시작하기 버튼 |
| `guide-back-btn.webp` | 다시 배우기 또는 나가기 버튼 |
| `dino-3.webp` | 팔·양손 인식 안내용 정지 캐릭터 |
| `dino-2.webp` | 실습 성공 피드백용 정지 캐릭터 |

## 4. 후르츠류 5단계 배치

| 단계 | 화면 | 사용할 에셋 | 구현 메모 |
| ---: | --- | --- | --- |
| 1 | 게임 소개 | `common-greeting.webm`, `reused/common/game-select-bg.webp`, `reused/common/bubble-big.webp`, 과일 이미지 | 캐릭터가 인사하고 과일 자르기 게임을 소개한다. |
| 2 | 자르기 시범 | `fruit-slice-demo.webm`, `reused/fruit/play-frame.webp`, `reused/fruit/watermelon.webp` | 튜토리얼 전용 과일 1개를 정해진 위치에서 올린다. 캐릭터가 내리치는 순간 과일을 두 조각으로 바꾸고, 영상 반복 시 과일도 처음 상태로 되돌린다. |
| 3 | 팔 인식 | `reused/common/dino-3.webp`, `reused/fruit/play-frame.webp`, `reused/fruit/leaf-sword.webp` | 카메라에서 한쪽 팔을 인식하고 플레이 손에 칼날 UI를 붙인다. |
| 4 | 자르기 실습 | `reused/fruit/play-frame.webp`, `reused/fruit/leaf-sword.webp`, 과일 이미지 4종, `reused/common/dino-2.webp` | 실제 게임 로직으로 과일을 생성한다. 성공 시 `dino-2.webp`를 짧게 보여준다. |
| 5 | 게임 시작 | `common-celebration.webm`, `reused/common/game-select-bg.webp`, 공통 버튼·말풍선 | 시작하기, 다시 배우기, 나가기 버튼을 표시한다. |

`reused/fruit`의 과일은 `apple.webp`, `orange.webp`, `strawberry.webp`, `watermelon.webp`를 사용한다.

## 5. 도블류 5단계 배치

| 단계 | 화면 | 사용할 에셋 | 구현 메모 |
| ---: | --- | --- | --- |
| 1 | 게임 소개 | `common-greeting.webm`, `reused/common/game-select-bg.webp`, `reused/common/bubble-big.webp`, 동물 이미지 | 캐릭터가 인사하고 같은 동물 찾기를 소개한다. |
| 2 | 맞추기 시범 | `dobble-open-fist-demo.webm`, `reused/dobble/board-bg.webp`, 동물 이미지 | 튜토리얼 전용 정답 동물 2개를 캐릭터의 양손 위치에 고정한다. 주먹을 쥐는 순간 두 동물을 선택하고, 손을 펴는 순간 선택을 확정한다. 영상 반복 시 선택 상태를 초기화한다. |
| 3 | 양손 인식 | `reused/common/dino-3.webp`, `reused/dobble/board-bg.webp` | 카메라에서 두 손을 인식하고 손 위치 커서를 코드로 표시한다. |
| 4 | 맞추기 실습 | `reused/dobble/board-bg.webp`, 동물 이미지, `reused/common/dino-2.webp` | 손 이동 → 주먹 선택 → 손 펴기를 실제 인식으로 판정한다. |
| 5 | 게임 시작 | `common-celebration.webm`, `reused/common/game-select-bg.webp`, 공통 버튼·말풍선 | 시작하기, 다시 배우기, 나가기 버튼을 표시한다. |

`reused/dobble`의 동물 이미지는 `bear.webp`, `deer.webp`, `frog.webp`, `koala.webp`, `monkey.webp`, `rabbit.webp`를 사용한다.

## 6. 시범 영상과 UI 동기화

두 시범 영상은 `30fps`, 약 3초 길이이며 반복 재생한다. 타이머 시작 시점이 아니라 `<video>`의 `currentTime`을 기준으로 UI를 변경한다.

### 후르츠류 자르기 시범

| 영상 시간 | 캐릭터 동작 | 튜토리얼 UI |
| ---: | --- | --- |
| 0.00~0.93초 | 왼팔을 든 준비 자세 | 과일 1개가 화면 아래에서 절단 위치까지 올라온다. |
| 0.93~1.07초 | 내리치기 전환 효과 | 칼날 궤적과 과일 충돌 강조를 시작한다. |
| **1.07초** | 왼팔을 아래로 내리친 자세 | 과일을 두 조각으로 변경하고 절단 효과음과 효과를 재생한다. |
| 1.07~2.20초 | 내리친 자세 유지 및 복귀 효과 | 두 과일 조각이 양옆 아래로 짧게 떨어진다. |
| 2.20~3.00초 | 준비 자세로 복귀 | 잘린 조각을 숨기고 다음 반복을 준비한다. |
| **반복 시작** | 처음 프레임으로 복귀 | 온전한 과일 1개와 모든 효과를 초기화한다. |

과일은 실제 게임의 무작위 생성과 충돌 판정을 사용하지 않는다. 튜토리얼 전용 객체를 사용하여 항상 동일한 경로, 속도, 절단 위치로 움직인다.

### 도블류 맞추기 시범

| 영상 시간 | 캐릭터 동작 | 튜토리얼 UI |
| ---: | --- | --- |
| 0.00~0.87초 | 양손을 편 자세 | 동일한 동물 2개를 캐릭터의 양손 위치에 고정해 표시한다. |
| 0.87~1.00초 | 주먹 전환 효과 | 두 동물의 선택 영역을 미리 강조한다. |
| **1.00초** | 주먹을 쥔 자세 | 두 동물을 동시에 선택하고 선택 테두리와 확대 효과를 표시한다. |
| 1.00~1.87초 | 주먹 자세 유지 | 선택 상태를 유지한다. |
| 1.87~2.00초 | 손 펴기 전환 효과 | 정답 확정 효과를 준비한다. |
| **2.00초** | 손을 다시 편 자세 | 정답을 확정하고 성공 효과를 표시한다. |
| **반복 시작** | 처음 프레임으로 복귀 | 선택·정답·강조 효과를 모두 초기화한다. |

동물 카드는 실제 게임의 카드 생성과 정답 판정을 사용하지 않는다. 튜토리얼 전용 동일 동물 2개를 캐릭터의 왼손과 오른손이 자연스럽게 닿는 고정 좌표에 배치한다.

## 7. 프론트 적용 원칙

- 네 개의 WebM 모두 `loop`로 반복 재생할 수 있다.
- 모든 영상에는 `autoPlay`, `muted`, `playsInline`을 적용한다.
- 시범 영상은 반복 횟수와 관계없이 다음 버튼을 항상 표시하거나 첫 번째 반복이 끝난 뒤 활성화한다.
- 캐릭터의 위치와 크기는 장면이 바뀌어도 동일한 고정 컨테이너 안에서 유지한다.
- 과일, 카드, 선택 테두리, 손 커서, 말풍선 텍스트는 영상에 합치지 않고 실제 프론트 요소로 표시한다.
- 시범용 과일과 동물은 실제 게임 객체와 분리된 고정 데이터로 관리한다.
- 영상이 반복되어 `currentTime`이 이전 프레임보다 작아지면 해당 시범 UI의 상태를 초기화한다.
- 각 효과는 한 번의 반복 구간에서 중복 실행되지 않도록 실행 여부를 별도로 관리한다.

```tsx
import fruitSliceDemo from '@/assets/images/tutorial/fruit/fruit-slice-demo.webm';

<video
  src={fruitSliceDemo}
  autoPlay
  muted
  loop
  playsInline
/>
```

```tsx
const cueState = {
  previousTime: 0,
  actionTriggered: false,
};

function syncFruitDemo(video: HTMLVideoElement) {
  const time = video.currentTime;

  // loop로 영상 시간이 처음으로 돌아간 경우
  if (time < cueState.previousTime) {
    cueState.actionTriggered = false;
    resetTutorialFruit();
  }

  if (!cueState.actionTriggered && time >= 1.07) {
    cueState.actionTriggered = true;
    splitTutorialFruit();
  }

  cueState.previousTime = time;
  requestAnimationFrame(() => syncFruitDemo(video));
}
```

도블류는 한 반복마다 `1.00초`의 선택 처리와 `2.00초`의 정답 확정 여부를 각각 따로 관리한다.

## 8. 남겨 둔 파일 기준

- 실제 화면에서 바로 사용하는 최종 WebM
- 영상 수정 또는 정지 화면 대체에 필요한 최종 PNG
- 현재 5단계 화면에 배치할 기존 프론트 에셋

중간 프레임 시트, 크로마키 원본, 구버전 포즈, 10프레임 실험본은 삭제했다.
