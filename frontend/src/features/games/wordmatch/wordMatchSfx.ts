import { playTones } from '@/features/games/tonePlayer'

/**
 * 낱말 맞추기 효과음.
 *
 * 잡기·놓기·정답·오답에 각각 다른 소리를 준다. 오답 소리는 실패로 들리지 않게
 * 낮고 짧게 둔다 — 유아 화면에서 실패를 강조하지 않는다.
 */

/** 글자를 잡는 순간 — 짧고 가벼운 "톡" */
export function playGrabSfx(): void {
  playTones([{ from: 620, to: 980, delay: 0, duration: 0.1, gain: 0.14 }])
}

/** 글자를 칸에 놓을 때 — 딸깍 끼워지는 소리 */
export function playPlaceSfx(): void {
  playTones([{ from: 880, to: 660, delay: 0, duration: 0.09, gain: 0.13 }])
}

/**
 * 낱말을 완성했을 때.
 *
 * 이 게임에서 가장 크게 칭찬하는 순간이다. 올라가는 네 음에 위쪽 옥타브를 얇게
 * 겹쳐 종이 울리는 느낌을 준다.
 */
export function playCorrectSfx(): void {
  playTones([
    { from: 523, delay: 0, duration: 0.14, gain: 0.2, type: 'triangle' },
    { from: 659, delay: 0.08, duration: 0.14, gain: 0.2, type: 'triangle' },
    { from: 784, delay: 0.16, duration: 0.16, gain: 0.22, type: 'triangle' },
    { from: 1047, delay: 0.24, duration: 0.42, gain: 0.24, type: 'triangle' },
    { from: 1568, delay: 0.24, duration: 0.34, gain: 0.09, type: 'sine' },
  ])
}

/** 틀렸을 때 — 부드럽게 내려가는 두 음 */
export function playWrongSfx(): void {
  playTones([
    { from: 392, delay: 0, duration: 0.16, gain: 0.11 },
    { from: 330, delay: 0.12, duration: 0.2, gain: 0.11 },
  ])
}
