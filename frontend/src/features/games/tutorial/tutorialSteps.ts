/**
 * 게임 방법 튜토리얼 단계 정의.
 *
 * 모든 게임이 같은 흐름을 탄다: 소개 → 시범 → 인식 확인 → 실습 → 시작.
 * 아이가 "보고" 끝내지 않고 실제로 한 번 해 보게 하는 것이 목적이라, 시범과
 * 실습을 반드시 한 쌍으로 둔다.
 *
 * 문구는 여기 한곳에서만 관리한다. 화면 컴포넌트에는 문장을 두지 않는다.
 */

import type { GameId } from '@/features/games/games'

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

/**
 * 카메라 판정과 연습 판이 실제로 구현된 게임.
 *
 * 나머지 게임은 같은 5단계를 보여 주되 인식·실습을 실제로 하지 않고 안내만
 * 보고 넘어간다. 판정이 없는데 통과를 요구하면 아이가 그 단계에 갇힌다.
 */
export type InteractiveTutorialGameId = 'fruit' | 'dobble'

export function isInteractiveTutorialGame(
  id: GameId,
): id is InteractiveTutorialGameId {
  return id === 'fruit' || id === 'dobble'
}

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

/*
  아직 만들지 않은 게임의 시범·인식·실습 문구.

  단계 수와 흐름은 기존 게임과 같게 두고, 무엇을 하라고 시키지는 않는다. 아직
  할 수 있는 것이 없는데 "해 봐"라고 하면 아이는 게임이 고장난 줄 안다. 대신
  곧 준비된다고만 알려 준다. 게임을 실제로 만들 때 이 문구부터 바꾼다.
*/
const UPCOMING_MIDDLE_STEPS: TutorialStep[] = [
  {
    kind: 'demo',
    speech: '어떻게 하는지\n곧 보여 줄게!',
  },
  {
    kind: 'detect',
    speech: '조금만 기다려 줘!\n금방 준비할게!',
  },
  {
    kind: 'practice',
    speech: '여기서 같이\n연습해 볼 거야!',
  },
]

const READY_STEP: TutorialStep = {
  kind: 'ready',
  speech: '정말 잘했어!\n이제 시작해 볼까?',
}

const WORDMATCH_STEPS: TutorialStep[] = [
  {
    kind: 'intro',
    speech: '안녕! 나는 빵긋이야!\n이번에는 낱말을 맞춰 보자!',
  },
  ...UPCOMING_MIDDLE_STEPS,
  READY_STEP,
]

const RECYCLE_STEPS: TutorialStep[] = [
  {
    kind: 'intro',
    speech: '안녕! 나는 빵긋이야!\n이번에는 숲을 깨끗하게 치워 보자!',
  },
  ...UPCOMING_MIDDLE_STEPS,
  READY_STEP,
]

export const TUTORIAL_STEPS: Record<GameId, TutorialStep[]> = {
  fruit: FRUIT_STEPS,
  dobble: DOBBLE_STEPS,
  wordmatch: WORDMATCH_STEPS,
  recycle: RECYCLE_STEPS,
}
