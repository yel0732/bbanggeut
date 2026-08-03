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

/**
 * 알맞은 통에 넣었을 때.
 *
 * 이 게임에서 가장 크게 칭찬하는 순간이다. 올라가는 네 음(도-미-솔-도)에 위쪽
 * 옥타브를 겹쳐 종이 울리는 느낌을 주고, 마지막 음을 길게 남겨 여운을 둔다.
 * 잡기·오답 소리보다 확실히 커서 아이가 무엇이 잘된 것인지 소리만으로 안다.
 */
export function playCorrectSfx(): void {
  playTones([
    { from: 523, delay: 0, duration: 0.14, gain: 0.2, type: 'triangle' },
    { from: 659, delay: 0.08, duration: 0.14, gain: 0.2, type: 'triangle' },
    { from: 784, delay: 0.16, duration: 0.16, gain: 0.22, type: 'triangle' },
    { from: 1047, delay: 0.24, duration: 0.42, gain: 0.24, type: 'triangle' },
    // 반짝이는 배음. 같은 화음을 한 옥타브 위에서 얇게 덧댄다.
    { from: 1568, delay: 0.24, duration: 0.34, gain: 0.09, type: 'sine' },
    { from: 2093, delay: 0.32, duration: 0.3, gain: 0.06, type: 'sine' },
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
