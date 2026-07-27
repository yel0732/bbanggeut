/**
 * 도블 게임 효과음.
 *
 * 유아는 소리 피드백에 가장 빠르게 반응하므로 잡기/정답/다시 찾기에 각각 다른
 * 소리를 준다. 음원 파일 없이 Web Audio로 합성하므로 추가 에셋과 로딩이 없다.
 * 실제 음원이 준비되면 이 파일의 함수만 교체하면 된다.
 */

type ToneSpec = {
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

function play(specs: ToneSpec[]): void {
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

/** 동물을 잡는 순간 — 짧고 가벼운 "톡" */
export function playGrabSfx(): void {
  play([{ from: 620, to: 980, delay: 0, duration: 0.11, gain: 0.16 }])
}

/** 정답 — 올라가는 세 음 */
export function playMatchSfx(): void {
  play([
    { from: 523, delay: 0, duration: 0.16, gain: 0.16, type: 'triangle' },
    { from: 659, delay: 0.1, duration: 0.16, gain: 0.16, type: 'triangle' },
    { from: 784, delay: 0.2, duration: 0.26, gain: 0.18, type: 'triangle' },
  ])
}

/** 다시 찾기 — 부드럽게 내려가는 두 음. 실패로 들리지 않게 낮고 짧게. */
export function playRetrySfx(): void {
  play([
    { from: 392, delay: 0, duration: 0.16, gain: 0.11 },
    { from: 330, delay: 0.12, duration: 0.2, gain: 0.11 },
  ])
}
