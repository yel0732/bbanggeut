/**
 * 게임 방법 튜토리얼 단계 정의.
 *
 * 두 게임 모두 같은 흐름을 탄다: 소개 → 시범 → 인식 확인 → 실습 → 시작.
 * 아이가 "보고" 끝내지 않고 실제로 한 번 해 보게 하는 것이 목적이라, 시범과
 * 실습을 반드시 한 쌍으로 둔다.
 *
 * 문구는 여기 한곳에서만 관리한다. 화면 컴포넌트에는 문장을 두지 않는다.
 */

export type TutorialStepKind =
  /** 빵긋이 인사하고 무엇을 할지 알려 준다 */
  | 'intro'
  /** 조작을 자동 재생으로 보여 준다 */
  | 'demo'
  /** 카메라에 몸/손이 잡히는지 확인한다 */
  | 'detect'
  /** 직접 해 본다. 성공하면 다음으로 넘어간다 */
  | 'practice'
  /** 시작하기 / 다시 배우기 / 나가기 */
  | 'ready'

export type TutorialStep = {
  kind: TutorialStepKind
  /** 빵긋이 하는 말. 줄바꿈은 \n */
  speech: string
}

export type TutorialGameId = 'fruit' | 'dobble'

const FRUIT_STEPS: TutorialStep[] = [
  {
    kind: 'intro',
    speech: '안녕! 나는 빵긋이야!\n우리 같이 과일을 잘라 보자!',
  },
  {
    kind: 'demo',
    speech: '팔을 칼처럼 휘둘러서\n과일을 잘라 봐!',
  },
  {
    kind: 'detect',
    speech: '일어서서 한 팔을 번쩍 들어\n카메라에 보여 줘!',
  },
  {
    kind: 'practice',
    speech: '좋아! 올라오는 과일을\n잘라 볼까?',
  },
  {
    kind: 'ready',
    speech: '정말 잘했어!\n이제 시작해 볼까?',
  },
]

const DOBBLE_STEPS: TutorialStep[] = [
  {
    kind: 'intro',
    speech: '안녕! 나는 빵긋이야!\n이번에는 같은 동물을 찾아보자!',
  },
  {
    kind: 'demo',
    speech: '양쪽에 똑같이 있는 동물은 하나야.\n손을 올리고 주먹을 쥐면 잡혀!',
  },
  {
    kind: 'detect',
    speech: '두 손을 들어서\n카메라에 보여 줘!',
  },
  {
    kind: 'practice',
    speech: '같은 동물을 찾아서\n양쪽 다 잡아 볼까?',
  },
  {
    kind: 'ready',
    speech: '정말 잘했어!\n이제 시작해 볼까?',
  },
]

export const TUTORIAL_STEPS: Record<TutorialGameId, TutorialStep[]> = {
  fruit: FRUIT_STEPS,
  dobble: DOBBLE_STEPS,
}

export function isTutorialGame(id: string | undefined): id is TutorialGameId {
  return id === 'fruit' || id === 'dobble'
}
