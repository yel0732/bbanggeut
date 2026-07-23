import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'

import bgmUrl from '@/assets/audio/start/green-adventure-start.mp3'
import backgroundUrl from '@/assets/images/profile/background.webp'
import childAvatarUrl from '@/assets/images/profile/child-avatar.webp'
import parentAvatarUrl from '@/assets/images/profile/parent-avatar.webp'
import pinDotEmptyUrl from '@/assets/images/profile/pin-dot-empty.webp'
import pinDotFillUrl from '@/assets/images/profile/pin-dot-fill.webp'
import pinKeyUrl from '@/assets/images/profile/pin-key.webp'
import pinPanelUrl from '@/assets/images/profile/pin-panel.webp'
import musicOnUrl from '@/assets/images/start/music-btn.webp'
import musicOffUrl from '@/assets/images/start/mute-btn.webp'
import { useFitScale } from '@/hooks/useFitScale'

const jua = { fontFamily: "'Jua', sans-serif" } as const
const PIN_LENGTH = 4
const KEYPAD_DIGITS = [
  '1',
  '2',
  '3',
  '4',
  '5',
  '6',
  '7',
  '8',
  '9',
  '',
  '0',
  '⌫',
]

const childProfiles = [{ name: '하린' }, { name: '도윤' }]

export function ProfileSelectPage() {
  const navigate = useNavigate()
  const canvasRef = useFitScale<HTMLDivElement>()
  const audioRef = useRef<HTMLAudioElement>(null)
  const [isBgmPlaying, setIsBgmPlaying] = useState(false)
  const [showPin, setShowPin] = useState(false)
  const [pin, setPin] = useState('')
  const [pinMessage, setPinMessage] = useState('')

  useEffect(() => {
    const audio = audioRef.current

    if (!audio) {
      return
    }

    audio.volume = 0.4

    void audio
      .play()
      .then(() => {
        setIsBgmPlaying(true)
      })
      .catch(() => {
        setIsBgmPlaying(false)
      })
  }, [])

  const toggleBgm = async () => {
    const audio = audioRef.current

    if (!audio) {
      return
    }

    if (audio.paused) {
      try {
        await audio.play()
        setIsBgmPlaying(true)
      } catch {
        setIsBgmPlaying(false)
      }
      return
    }

    audio.pause()
    setIsBgmPlaying(false)
  }

  const openPin = () => {
    setPin('')
    setPinMessage('')
    setShowPin(true)
  }

  const closePin = () => {
    setShowPin(false)
    setPin('')
    setPinMessage('')
  }

  const pressDigit = (digit: string) => {
    if (pin.length >= PIN_LENGTH) return
    const next = pin + digit
    setPin(next)
    setPinMessage('')

    if (next.length === PIN_LENGTH) {
      // 데모: 4자리 숫자는 모두 통과 처리 (실제 인증 연동 전)
      window.setTimeout(() => {
        setPinMessage('확인되었습니다 ✓')
        window.setTimeout(() => {
          navigate('/parent')
        }, 500)
      }, 150)
    }
  }

  const pressBackspace = () => {
    setPin((value) => value.slice(0, -1))
    setPinMessage('')
  }

  return (
    <div className="flex h-screen w-screen items-start justify-center overflow-hidden bg-[radial-gradient(120%_120%_at_50%_20%,_#12241a_0%,_#0b140e_100%)]">
      <div
        ref={canvasRef}
        className="relative h-[720px] w-[1280px] origin-top overflow-hidden text-white"
      >
        <audio
          aria-label="초록 모험 시작 배경 음악"
          autoPlay
          loop
          preload="auto"
          ref={audioRef}
          src={bgmUrl}
        />

        <img
          alt=""
          className="absolute inset-0 h-full w-full object-cover"
          decoding="async"
          fetchPriority="high"
          src={backgroundUrl}
        />
        <div className="absolute inset-0 bg-[linear-gradient(180deg,rgba(0,0,0,0)_55%,rgba(20,45,20,0.35)_100%)]" />

        <div
          className="absolute top-[118px] right-0 left-0 z-10 text-center text-[44px] text-white [text-shadow:0_6px_14px_rgba(0,0,0,0.28)]"
          style={{
            ...jua,
            paintOrder: 'stroke fill',
            WebkitTextStroke: '7px #3c7d38',
          }}
        >
          누가 놀러 왔어요?
        </div>

        <div className="absolute top-[262px] right-0 left-0 z-10 flex items-start justify-center gap-8">
          {childProfiles.map((profile) => (
            <button
              className="flex flex-col items-center gap-4 border-none bg-none p-0 transition hover:-translate-y-1.5"
              key={profile.name}
              onClick={() => navigate('/child')}
              type="button"
            >
              <div className="size-[184px] overflow-hidden rounded-full border-[7px] border-white bg-[#eaf4d8] shadow-[0_12px_26px_rgba(0,0,0,0.3)] transition hover:scale-105">
                <img
                  alt={profile.name}
                  className="h-full w-full object-cover"
                  decoding="async"
                  src={childAvatarUrl}
                />
              </div>
              <span
                className="text-[28px] text-white"
                style={{
                  ...jua,
                  paintOrder: 'stroke fill',
                  WebkitTextStroke: '5px #3c7d38',
                }}
              >
                {profile.name}
              </span>
            </button>
          ))}

          <div className="mx-1 h-[196px] w-0.5 bg-[linear-gradient(180deg,rgba(255,255,255,0),rgba(255,255,255,0.5),rgba(255,255,255,0))]" />

          <button
            className="flex flex-col items-center gap-4 border-none bg-none p-0 transition hover:-translate-y-1.5"
            onClick={openPin}
            type="button"
          >
            <div className="size-[184px] overflow-hidden rounded-full border-[7px] border-[#ffe6a6] bg-[#eaf4d8] shadow-[0_12px_26px_rgba(0,0,0,0.3)] transition hover:scale-105">
              <img
                alt="부모"
                className="h-full w-full object-cover"
                decoding="async"
                src={parentAvatarUrl}
              />
            </div>
            <span
              className="flex items-center gap-1.5 text-[28px] text-white"
              style={{
                ...jua,
                paintOrder: 'stroke fill',
                WebkitTextStroke: '5px #8a5a1e',
              }}
            >
              부모 🔒
            </span>
          </button>
        </div>

        <button
          aria-label={isBgmPlaying ? '배경 음악 끄기' : '배경 음악 켜기'}
          className="absolute bottom-[12px] left-[78px] z-40 h-[62px] w-[62px] border-none bg-none p-0 drop-shadow-[0_4px_8px_rgba(0,0,0,0.3)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white"
          onClick={() => {
            void toggleBgm()
          }}
          type="button"
        >
          <img
            alt=""
            className="block h-full w-full object-contain"
            decoding="async"
            src={isBgmPlaying ? musicOnUrl : musicOffUrl}
          />
        </button>

        {showPin ? (
          <div
            className="absolute inset-0 z-50 flex items-center justify-center bg-[rgba(18,30,18,0.6)]"
            onClick={closePin}
          >
            <div
              className="relative h-[560px] w-[440px]"
              onClick={(event) => event.stopPropagation()}
            >
              <img
                alt=""
                className="pointer-events-none absolute inset-0 h-full w-full object-contain"
                decoding="async"
                src={pinPanelUrl}
              />

              <div className="absolute top-[9%] right-[11%] left-[11%] flex flex-col">
                <div
                  className="text-center text-[30px] text-[#3a7d2c]"
                  style={jua}
                >
                  부모 확인
                </div>
                <div
                  className="mt-1.5 text-center text-[17px] text-[#8a7a5a]"
                  style={jua}
                >
                  4자리 비밀번호를 입력하세요
                </div>

                <div className="mt-5 mb-1.5 flex justify-center gap-4">
                  {Array.from({ length: PIN_LENGTH }).map((_, index) => (
                    <img
                      alt=""
                      className="size-[30px]"
                      decoding="async"
                      key={index}
                      src={index < pin.length ? pinDotFillUrl : pinDotEmptyUrl}
                    />
                  ))}
                </div>
                <div
                  className="h-[22px] text-center text-[18px]"
                  style={{
                    ...jua,
                    color: pinMessage ? '#3a7d2c' : 'transparent',
                  }}
                >
                  {pinMessage || '.'}
                </div>

                <div className="mt-6 grid grid-cols-3 gap-2.5">
                  {KEYPAD_DIGITS.map((digit, index) =>
                    digit === '' ? (
                      <div key={`blank-${index}`} />
                    ) : (
                      <button
                        className="relative h-14 bg-cover bg-center text-[28px] text-[#3a6b1e] transition active:scale-95"
                        key={digit}
                        onClick={() =>
                          digit === '⌫' ? pressBackspace() : pressDigit(digit)
                        }
                        style={{ ...jua, backgroundImage: `url(${pinKeyUrl})` }}
                        type="button"
                      >
                        {digit}
                      </button>
                    ),
                  )}
                </div>
              </div>

              <button
                aria-label="닫기"
                className="absolute -top-4 -right-4 grid size-[46px] place-items-center rounded-full border-[3px] border-white bg-[#cf6f4e] text-xl text-white shadow-[0_4px_8px_rgba(0,0,0,0.3)] transition hover:scale-105"
                onClick={closePin}
                type="button"
              >
                ✕
              </button>
            </div>
          </div>
        ) : null}
      </div>
    </div>
  )
}
