import { playTones } from '@/features/games/tonePlayer'

/**
 * 분리수거 게임 효과음.
 *
 * 잡기·정답·오답 안내·물길 반환에 각각 다른 소리를 준다. 오답 소리는 실패로
 * 들리지 않게 낮고 짧게 둔다 — 유아 화면에서 실패를 강조하지 않는다.
 */

/** 쓰레기를 잡는 순간 — 짧고 가벼운 "톡" */
export function playGrabSfx(): void {
  playTones([{ from: 620, to: 980, delay: 0, duration: 0.11, gain: 0.16 }])
}

/** 알맞은 통에 넣었을 때 — 올라가는 세 음 */
export function playCorrectSfx(): void {
  playTones([
    { from: 523, delay: 0, duration: 0.16, gain: 0.16, type: 'triangle' },
    { from: 659, delay: 0.1, duration: 0.16, gain: 0.16, type: 'triangle' },
    { from: 784, delay: 0.2, duration: 0.26, gain: 0.18, type: 'triangle' },
  ])
}

/** 다른 통에 넣었을 때 — 부드럽게 내려가는 두 음 */
export function playWrongSfx(): void {
  playTones([
    { from: 392, delay: 0, duration: 0.16, gain: 0.11 },
    { from: 330, delay: 0.12, duration: 0.2, gain: 0.11 },
  ])
}

/** 통이 아닌 곳에 놓아 물길로 돌아갈 때 — 물에 닿는 짧은 소리 */
export function playReturnSfx(): void {
  playTones([{ from: 300, to: 200, delay: 0, duration: 0.14, gain: 0.09 }])
}
