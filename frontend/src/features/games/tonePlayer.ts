/**
 * 게임 효과음 합성기.
 *
 * 음원 파일 없이 Web Audio로 만들어 추가 에셋과 로딩이 없다. 유아는 소리
 * 피드백에 가장 빠르게 반응하므로 동작마다 다른 소리를 준다.
 *
 * 도블(`dobble/dobbleSfx.ts`)에 같은 코드가 먼저 들어가 있다. 도블 쪽을 건드려
 * 잘 돌아가는 게임을 흔들지 않으려고 여기로 옮기지 않았다. 도블을 손볼 일이
 * 생기면 그때 이 모듈로 합치면 된다.
 */

export type ToneSpec = {
  /** 시작 주파수(Hz) */
  from: number
  /** 끝 주파수(Hz). 생략하면 일정하게 유지 */
  to?: number
  /** 컨텍스트 현재 시각 기준 시작 지연(초) */
  delay: number
  /** 길이(초) */
  duration: number
  /** 최대 음량 (0~1) */
  gain: number
  type?: OscillatorType
}

let context: AudioContext | null = null

function ensureContext(): AudioContext | null {
  if (typeof window === 'undefined') {
    return null
  }
  if (!context) {
    const Ctor =
      window.AudioContext ??
      (window as { webkitAudioContext?: typeof AudioContext })
        .webkitAudioContext
    if (!Ctor) {
      return null
    }
    context = new Ctor()
  }
  // 자동재생 정책으로 정지된 상태면 깨운다. 게임 진입 전 버튼 조작이 있어 허용된다.
  if (context.state === 'suspended') {
    void context.resume().catch(() => {})
  }
  return context
}

export function playTones(specs: ToneSpec[]): void {
  const ctx = ensureContext()
  if (!ctx) {
    return
  }
  const now = ctx.currentTime

  for (const spec of specs) {
    const start = now + spec.delay
    const end = start + spec.duration

    const oscillator = ctx.createOscillator()
    oscillator.type = spec.type ?? 'sine'
    oscillator.frequency.setValueAtTime(spec.from, start)
    if (spec.to !== undefined) {
      oscillator.frequency.exponentialRampToValueAtTime(spec.to, end)
    }

    // 짧은 어택 + 부드러운 감쇠. 딸깍거리는 잡음을 막는다.
    const envelope = ctx.createGain()
    envelope.gain.setValueAtTime(0.0001, start)
    envelope.gain.exponentialRampToValueAtTime(spec.gain, start + 0.012)
    envelope.gain.exponentialRampToValueAtTime(0.0001, end)

    oscillator.connect(envelope)
    envelope.connect(ctx.destination)
    oscillator.start(start)
    oscillator.stop(end + 0.02)
  }
}
