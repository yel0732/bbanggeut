import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import { isCompletedTerminationReason } from '@/api/conversations'
import conversationBgmUrl from '@/assets/audio/conversation/todak-room.mp3'
import dialogBoxUrl from '@/assets/images/conversation/dialog-box.webp'
import diaryButtonUrl from '@/assets/images/conversation/diary-btn.webp'
import diaryListButtonUrl from '@/assets/images/conversation/diary-list-btn.webp'
import talkListenDinosaurUrl from '@/assets/images/conversation/talk-listen-dinosaur.webp'
import micOffButtonUrl from '@/assets/images/conversation/mic-off-btn.webp'
import micOnButtonUrl from '@/assets/images/conversation/mic-on-btn.webp'
import readyMicUrl from '@/assets/images/conversation/talk_ready_pic_1.webp'
import readyEarUrl from '@/assets/images/conversation/talk_ready_pic_2.webp'
import readyTalkUrl from '@/assets/images/conversation/talk_ready_pic_3.webp'
import readyPopupUrl from '@/assets/images/conversation/talk_ready_popup.webp'
import talkBackgroundUrl from '@/assets/images/conversation/talk-bg.webp'
import diaryNoSaveButtonUrl from '@/assets/images/diary/diary-nosave-btn.webp'
import diaryNoteUrl from '@/assets/images/diary/diary-note.webp'
import diarySaveButtonUrl from '@/assets/images/diary/diary-save-btn.webp'
import backButtonUrl from '@/assets/images/game-entry/left-btn.webp'
import completeButtonUrl from '@/assets/images/parent-profile/btn-complete.webp'
import photoButtonUrl from '@/assets/images/parent-profile/btn-photo.webp'
import closeButtonUrl from '@/assets/images/start/close-btn.webp'
import musicOnUrl from '@/assets/images/start/music-btn.webp'
import musicOffUrl from '@/assets/images/start/mute-btn.webp'
import { ParentNoticeDialog } from '@/components/ParentNoticeDialog'
import { ChildProfileSwitchButton } from '@/components/ChildProfileSwitchButton'
import { useBgmPlayer } from '@/hooks/useBgmPlayer'
import { useFitScale } from '@/hooks/useFitScale'
import {
  type VoiceStatus,
  useRealtimeConversation,
} from '@/hooks/useRealtimeConversation'

const jua = { fontFamily: "'Jua', sans-serif" } as const

const DIARY_LINES = [
  '오늘은 과일 자르기에서 과일을 힘차게 잘랐다.',
  '빵긋이에게 오늘 있었던 재미있는 일도 들려주었다.',
  '가장 기억에 남는 순간을 이야기해서 기분이 좋았다.',
  '내일도 신나게 놀고 빵긋이와 이야기하고 싶다.',
]
const DINO_FRAME_SEQUENCE = [0, 1, 2, 1, 0] as const
const DINO_FRAME_POSITIONS = [
  '0% 0%',
  '50% 0%',
  '100% 0%',
  '0% 100%',
  '50% 100%',
  '100% 100%',
] as const
const DINO_FRAME_OFFSETS = [0, 12.7, 49.3, 0, 12.7, 37.5] as const

const STATUS_COPY: Record<VoiceStatus, string> = {
  listening: '이야기해 줘, 듣고 있어',
  speaking: '빵긋이가 이야기하고 있어요',
  thinking: '빵긋이가 생각하고 있어요',
  waiting: '조금만 기다려 줘',
}

const TOPIC_LABEL = {
  DAILY: '오늘 이야기',
  GAME: '게임 이야기',
} as const

export function ConversationPage() {
  const navigate = useNavigate()
  const canvasRef = useFitScale<HTMLDivElement>()
  const remoteAudioRef = useRef<HTMLAudioElement>(null)
  const { finish, start, state, toggleMic } = useRealtimeConversation()

  const isConversing = state.phase === 'active' || state.phase === 'closing'
  const isEnded = state.phase === 'ended'
  const isPreparing =
    state.phase === 'preparing' || state.phase === 'connecting'
  const showGuide = !isConversing && !isEnded
  // 완주한 대화만 원문이 남아 일기를 만들 수 있다. 중간에 끝난 대화는 서버가
  // 내용을 정리하므로 일기 팝업을 띄우지 않는다.
  const canMakeDiary = isCompletedTerminationReason(state.endedReason)

  const conversationBgmVolume =
    state.voice === 'listening' || state.voice === 'speaking' ? 0.055 : 0.22
  const { audioRef, isBgmPlaying, toggleBgm } = useBgmPlayer({
    volume: conversationBgmVolume,
  })

  const [micLevel, setMicLevel] = useState(0)
  const [dinoFrameStep, setDinoFrameStep] = useState(0)
  const [isDiaryOpen, setIsDiaryOpen] = useState(false)
  const [isDiaryAttached, setIsDiaryAttached] = useState(false)
  const [isLeaveConfirmOpen, setIsLeaveConfirmOpen] = useState(false)

  const isListening = state.voice === 'listening' && state.micEnabled

  useEffect(() => {
    const audio = remoteAudioRef.current
    if (!audio || !state.remoteStream) return

    audio.srcObject = state.remoteStream
    // srcObject를 코드로 넣으면 autoPlay 속성만으로는 재생이 시작되지 않는
    // 브라우저가 있다. 대화 시작 버튼이 사용자 제스처라 대개 통과한다.
    void audio.play().catch(() => undefined)

    return () => {
      audio.srcObject = null
    }
  }, [state.remoteStream])

  useEffect(() => {
    if (state.voice !== 'speaking' && state.voice !== 'listening') {
      setDinoFrameStep(0)
      return
    }

    setDinoFrameStep(0)
    const frameTimer = window.setInterval(() => {
      setDinoFrameStep((current) => (current + 1) % DINO_FRAME_SEQUENCE.length)
    }, 380)

    return () => window.clearInterval(frameTimer)
  }, [state.voice])

  useEffect(() => {
    if (!isListening || !state.localStream) {
      setMicLevel(0)
      return
    }

    const audioContext = new AudioContext()
    const analyser = audioContext.createAnalyser()
    analyser.fftSize = 64
    const source = audioContext.createMediaStreamSource(state.localStream)
    source.connect(analyser)
    const samples = new Uint8Array(analyser.frequencyBinCount)
    let animationFrame = 0

    const updateLevel = () => {
      analyser.getByteFrequencyData(samples)
      const average =
        samples.reduce((sum, sample) => sum + sample, 0) / samples.length
      setMicLevel(Math.min(1, average / 75))
      animationFrame = window.requestAnimationFrame(updateLevel)
    }
    updateLevel()

    return () => {
      window.cancelAnimationFrame(animationFrame)
      source.disconnect()
      void audioContext.close()
    }
  }, [isListening, state.localStream])

  const openDiary = useCallback(() => {
    setIsDiaryOpen(true)
  }, [])

  const attachDiary = useCallback(() => {
    if (isDiaryAttached) {
      navigate('/diaries')
      return
    }
    setIsDiaryAttached(true)
  }, [isDiaryAttached, navigate])

  const leaveConversation = () => {
    if (isConversing) {
      setIsLeaveConfirmOpen(true)
      return
    }
    navigate('/child')
  }

  const confirmLeaveConversation = () => {
    setIsLeaveConfirmOpen(false)
    finish('USER_FINISHED')
    navigate('/child')
  }

  const guideMessage =
    state.failure?.message ??
    (isPreparing
      ? '빵긋이를 부르고 있어요…'
      : '빵긋이가 말을 마치면 이야기해 줘.')

  const progressTotal = state.progress?.maxQuestionCount ?? 0
  const progressCount = state.progress?.questionCount ?? 0

  return (
    <div className="flex h-screen w-screen items-center justify-center overflow-hidden bg-[radial-gradient(120%_120%_at_50%_20%,_#12241a_0%,_#0b140e_100%)]">
      <div
        className="relative h-[720px] w-[1280px] overflow-hidden"
        ref={canvasRef}
      >
        <audio
          aria-label="대화방 배경 음악"
          loop
          preload="auto"
          ref={audioRef}
          src={conversationBgmUrl}
        />
        <audio aria-label="빵긋이 목소리" autoPlay ref={remoteAudioRef} />
        <img
          alt=""
          className="absolute inset-0 size-full object-cover"
          fetchPriority="high"
          src={talkBackgroundUrl}
        />

        <button
          aria-label="집으로 돌아가기"
          className="absolute top-[25px] left-[25px] z-60 size-[62px] bg-transparent p-0 drop-shadow-[0_4px_8px_rgba(0,0,0,.28)] transition hover:scale-105"
          onClick={leaveConversation}
          type="button"
        >
          <img
            alt=""
            className="size-full object-contain"
            src={backButtonUrl}
          />
        </button>
        <div className="absolute top-4 right-[25px] z-30 flex flex-col gap-2">
          <button
            aria-label="오늘의 일기"
            className="size-[58px] bg-transparent p-0 transition hover:scale-105"
            onClick={() => void openDiary()}
            type="button"
          >
            <img
              alt=""
              className="size-full object-contain"
              src={diaryButtonUrl}
            />
          </button>
          <Link
            aria-label="내 일기장"
            className="size-[58px] transition hover:scale-105"
            to="/diaries"
          >
            <img
              alt=""
              className="size-full object-contain"
              src={diaryListButtonUrl}
            />
          </Link>
        </div>

        <ChildProfileSwitchButton className="right-[100px]" />

        <button
          aria-label={isBgmPlaying ? '배경 음악 끄기' : '배경 음악 켜기'}
          className="absolute bottom-[25px] left-[25px] z-40 size-[62px] border-0 bg-transparent p-0 drop-shadow-[0_4px_8px_rgba(0,0,0,.3)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white active:scale-95"
          onClick={() => {
            void toggleBgm()
          }}
          type="button"
        >
          <img
            alt=""
            className="block size-full object-contain"
            decoding="async"
            src={isBgmPlaying ? musicOnUrl : musicOffUrl}
          />
        </button>

        {isConversing && progressTotal > 0 ? (
          <div
            aria-label={`대화 진행 ${progressCount}/${progressTotal}`}
            className="absolute top-[24px] left-1/2 z-30 flex -translate-x-1/2 items-center gap-3 rounded-full border-2 border-white/75 bg-[#5c7c45]/85 px-5 py-2.5 shadow-lg backdrop-blur-sm"
            style={jua}
          >
            <span className="text-[17px] text-white">
              {state.progress
                ? TOPIC_LABEL[state.progress.topicType]
                : '이야기'}{' '}
              {progressCount}/{progressTotal}
            </span>
            <div className="flex gap-1.5">
              {Array.from({ length: progressTotal }, (_, index) => (
                <span
                  className={`size-3 rounded-full border-2 border-white/80 ${
                    index < progressCount ? 'bg-[#ffdb63]' : 'bg-white/25'
                  }`}
                  key={index}
                />
              ))}
            </div>
          </div>
        ) : null}

        <div
          aria-label="공룡 친구 빵긋"
          className="absolute bottom-[260px] left-12/19 size-[400px] -translate-x-1/2 [animation:conversation-dino-breathe_3.4s_ease-in-out_infinite] drop-shadow-[0_22px_22px_rgba(0,0,0,.3)]"
          role="img"
        >
          {DINO_FRAME_POSITIONS.map((backgroundPosition, frameIndex) => {
            const sequenceFrame = DINO_FRAME_SEQUENCE[dinoFrameStep]
            const activeFrame =
              state.voice === 'listening' ? sequenceFrame + 3 : sequenceFrame

            return (
              <span
                aria-hidden="true"
                className={`absolute inset-0 bg-no-repeat [will-change:transform] [backface-visibility:hidden] ${
                  frameIndex === activeFrame ? 'opacity-100' : 'opacity-0'
                }`}
                key={backgroundPosition}
                style={{
                  backgroundImage: `url(${talkListenDinosaurUrl})`,
                  backgroundPosition,
                  backgroundSize: '300% 200%',
                  transform: `translate3d(${DINO_FRAME_OFFSETS[frameIndex]}px, 0, 0)`,
                }}
              />
            )
          })}
        </div>

        {!showGuide ? (
          <>
            <div className="absolute bottom-[90px] left-1/2 z-20 w-[710px] -translate-x-1/2">
              <img
                alt=""
                className="block w-full drop-shadow-[0_10px_20px_rgba(0,0,0,.28)]"
                src={dialogBoxUrl}
              />
              <span
                className="absolute top-[11.9%] left-[9.3%] -translate-1/2 text-[29px] whitespace-nowrap text-white drop-shadow"
                style={jua}
              >
                빵긋
              </span>
              <p
                className="absolute top-[48%] right-[8%] left-[9%] -translate-y-1/2 text-[30px] leading-[1.35] text-[#6a4a1e]"
                style={jua}
              >
                {state.assistantText}
              </p>
              {state.childTranscript ? (
                <p
                  className="absolute right-[8%] bottom-[10%] left-[9%] truncate text-[17px] text-[#8b744f]"
                  style={jua}
                >
                  “{state.childTranscript}”
                </p>
              ) : null}
            </div>

            {isConversing ? (
              <div className="absolute right-[95px] bottom-[94px] z-30 flex w-[170px] flex-col items-center">
                <div
                  className={`-mb-0 rounded-full px-3 py-1.5 text-center text-[15px] text-white shadow-md ${
                    state.notice ? 'bg-[#c85f50]' : 'bg-[#527542]/90'
                  }`}
                  role="status"
                  style={jua}
                >
                  {state.notice ??
                    (state.micEnabled
                      ? STATUS_COPY[state.voice]
                      : '마이크가 꺼져 있어')}
                </div>
                <button
                  aria-label={state.micEnabled ? '마이크 끄기' : '마이크 켜기'}
                  className="relative flex size-[96px] items-center justify-center border-0 bg-transparent p-0 drop-shadow-[0_8px_12px_rgba(0,0,0,.3)] transition hover:scale-105 disabled:opacity-70"
                  disabled={state.phase === 'closing'}
                  onClick={toggleMic}
                  type="button"
                >
                  <img
                    alt=""
                    className="block size-full object-contain"
                    decoding="async"
                    draggable={false}
                    src={state.micEnabled ? micOnButtonUrl : micOffButtonUrl}
                  />
                  {state.voice === 'thinking' ? (
                    <span className="absolute size-8 animate-spin rounded-full border-4 border-white/45 border-t-white" />
                  ) : null}
                  {isListening ? (
                    <span
                      className="absolute inset-[-10px] -z-10 rounded-full border-4 border-[#ff9d88]/70"
                      style={{ transform: `scale(${1 + micLevel * 0.24})` }}
                    />
                  ) : null}
                </button>
              </div>
            ) : null}
          </>
        ) : null}

        {showGuide ? (
          <div className="absolute inset-0 z-50 flex items-center justify-center bg-[rgba(16,35,23,.58)] backdrop-blur-[2px]">
            <section className="relative h-[534px] w-[700px] text-center drop-shadow-[0_24px_55px_rgba(0,0,0,.38)]">
              <img
                alt=""
                className="pointer-events-none absolute inset-0 size-full object-fill"
                src={readyPopupUrl}
              />
              <h2
                className="absolute top-[10.5%] right-0 left-0 text-[39px] text-[#52733b]"
                style={jua}
              >
                도란도란 얘기해요
              </h2>
              <p
                className="absolute top-[20%] right-0 left-0 text-[20px] text-[#8b7650]"
                style={jua}
              >
                빵긋이와 이야기할 준비를 해볼까요?
              </p>

              {[
                [readyMicUrl, '마이크를 허용해요'],
                [readyEarUrl, '질문을 잘 들어요'],
                [readyTalkUrl, '천천히 말해요'],
              ].map(([imageUrl, label], index) => (
                <div
                  className="absolute top-[30.5%] flex h-[32%] w-[24%] flex-col items-center justify-center"
                  key={label}
                  style={{ left: `${11.5 + index * 26}%` }}
                >
                  <img
                    alt=""
                    className={`object-contain ${
                      index === 0
                        ? 'h-[118px] w-[118px]'
                        : 'h-[110px] w-[110px]'
                    }`}
                    src={imageUrl}
                  />
                  <p className="mt-2 text-[18px] text-[#725b33]" style={jua}>
                    {index + 1}. {label}
                  </p>
                </div>
              ))}

              {guideMessage ? (
                <p
                  className="absolute top-[70%] right-[12%] left-[12%] min-h-6 text-[15px] text-[#bc5b4c]"
                  role="alert"
                  style={jua}
                >
                  {guideMessage}
                </p>
              ) : null}
              <button
                className="absolute top-[76%] left-1/2 flex h-[11%] w-[45%] -translate-x-1/2 items-center justify-center bg-transparent pb-1 text-[26px] text-white transition hover:scale-[1.02] disabled:opacity-70"
                disabled={isPreparing}
                onClick={() => void start()}
                style={{
                  ...jua,
                  paintOrder: 'stroke fill',
                  WebkitTextStroke: '3px #587d2d',
                }}
                type="button"
              >
                {state.phase === 'failed'
                  ? '다시 해볼까?'
                  : isPreparing
                    ? '준비하고 있어요'
                    : '대화 시작하기'}
              </button>
            </section>
          </div>
        ) : null}

        {isEnded && !isDiaryOpen ? (
          <div className="absolute inset-0 z-50 flex items-center justify-center bg-[rgba(16,35,23,.62)] backdrop-blur-[3px]">
            <section className="relative w-[560px] rounded-[46px] border-[7px] border-[#d6b672] bg-[#fffaf0] px-12 py-10 text-center shadow-[0_24px_55px_rgba(0,0,0,.42)]">
              <div className="absolute inset-[8px] rounded-[32px] border-2 border-[#ead8ab]" />
              <div className="relative">
                <div className="text-[58px]">{canMakeDiary ? '🌟' : '👋'}</div>
                <p className="mt-1 text-[20px] text-[#86a85e]" style={jua}>
                  {canMakeDiary ? '이야기 완성!' : '다음에 또 만나'}
                </p>
                <h2 className="mt-1 text-[34px] text-[#4f6f38]" style={jua}>
                  오늘 이야기도 정말 즐거웠어
                </h2>
                <p className="mt-3 text-[18px] text-[#806b45]" style={jua}>
                  {canMakeDiary
                    ? '빵긋이가 네 이야기를 소중히 간직할게.'
                    : '다음에 더 많이 이야기해 줘!'}
                </p>
                <div className="mt-7 flex justify-center gap-1">
                  {canMakeDiary ? (
                    <button
                      className="h-[70px] w-[240px] bg-[length:100%_100%] bg-center bg-no-repeat text-[19px] text-white"
                      onClick={() => void openDiary()}
                      style={{
                        ...jua,
                        backgroundImage: `url(${completeButtonUrl})`,
                        paintOrder: 'stroke fill',
                        WebkitTextStroke: '3px #4a7d1e',
                      }}
                      type="button"
                    >
                      오늘의 일기 보기
                    </button>
                  ) : null}
                  <button
                    className={`h-[70px] w-[240px] bg-[length:100%_100%] bg-center bg-no-repeat text-[19px] text-[#6a5431] ${
                      canMakeDiary ? '-ml-4' : ''
                    }`}
                    onClick={() => navigate('/child')}
                    style={{
                      ...jua,
                      backgroundImage: `url(${photoButtonUrl})`,
                    }}
                    type="button"
                  >
                    집으로 가기
                  </button>
                </div>
              </div>
            </section>
          </div>
        ) : null}

        {isDiaryOpen ? (
          <div className="absolute inset-0 z-60 bg-[rgba(30,24,10,.48)]">
            <section className="absolute top-[-10px] left-6/7 w-[860px] -translate-x-1/2 [animation:diary-pop-in_.38s_cubic-bezier(.2,1.2,.5,1)_both]">
              <img
                alt=""
                className="block w-full drop-shadow-[0_16px_30px_rgba(0,0,0,.35)]"
                src={diaryNoteUrl}
              />
              <button
                aria-label="오늘의 일기 닫기"
                className="absolute top-[9%] right-[17%] z-20 size-[52px] bg-transparent p-0 drop-shadow-[0_4px_8px_rgba(0,0,0,.28)] transition hover:scale-105"
                onClick={() => setIsDiaryOpen(false)}
                type="button"
              >
                <img
                  alt=""
                  className="size-full object-contain"
                  src={closeButtonUrl}
                />
              </button>
              <div className="absolute top-[15%] right-[15%] bottom-[12%] left-[15%] flex translate-y-[14px] flex-col items-center text-center">
                <p className="text-[25px] text-[#b08a4e]" style={jua}>
                  {new Intl.DateTimeFormat('ko-KR', {
                    month: 'long',
                    day: 'numeric',
                  }).format(new Date())}
                </p>
                <h2 className="mt-0.5 text-[39px] text-[#7a5220]" style={jua}>
                  오늘의 일기
                </h2>
                <div className="mt-3.5 flex max-w-[650px] flex-col gap-2">
                  {DIARY_LINES.map((line) => (
                    <p
                      className="text-[23px] leading-[1.38] text-[#5f4626]"
                      key={line}
                      style={jua}
                    >
                      {line}
                    </p>
                  ))}
                </div>
                <p
                  aria-live="polite"
                  className="mt-auto min-h-6 text-[16px] text-[#6f8e3f]"
                  style={jua}
                >
                  {isDiaryAttached
                    ? '아래 버튼을 누르면 일기장으로 이동해요'
                    : ''}
                </p>
                <div className="-mt-1 flex flex-col items-center">
                  <button
                    className="translate-x-2.4 relative block w-[320px] transition hover:scale-105 active:scale-[.98]"
                    onClick={attachDiary}
                    type="button"
                  >
                    <img
                      alt=""
                      className="block w-full drop-shadow-[0_5px_8px_rgba(0,0,0,.22)]"
                      src={diarySaveButtonUrl}
                    />
                    <span
                      className="absolute top-[50%] right-0 left-0 -translate-y-1/2 text-center text-[24px] whitespace-nowrap text-[#4a6d1e]"
                      style={jua}
                    >
                      {isDiaryAttached
                        ? '일기장에 붙였어요!'
                        : '내 일기장에 붙이기'}
                    </span>
                  </button>
                  <button
                    className="translate-x-2.4 relative mt-0 block w-[345px] -translate-y-[5px] transition hover:scale-105 active:scale-[.98]"
                    onClick={() => navigate('/child')}
                    type="button"
                  >
                    <img
                      alt=""
                      className="block w-full drop-shadow-[0_5px_8px_rgba(0,0,0,.2)]"
                      src={diaryNoSaveButtonUrl}
                    />
                    <span
                      className="absolute top-[50%] right-0 left-0 -translate-y-1/2 text-center text-[23px] whitespace-nowrap text-[#8a5a2a]"
                      style={jua}
                    >
                      {isDiaryAttached
                        ? '첫 화면으로 가기'
                        : '안 붙이고 첫 화면으로 가기'}
                    </span>
                  </button>
                </div>
              </div>
            </section>
          </div>
        ) : null}

        {isLeaveConfirmOpen ? (
          <ParentNoticeDialog
            cancelLabel="취소"
            compact
            confirmLabel="확인"
            message="대화를 그만하고 집으로 돌아갈까요?"
            onCancel={() => setIsLeaveConfirmOpen(false)}
            onConfirm={confirmLeaveConversation}
            title="대화를 마칠까요?"
          />
        ) : null}
      </div>
    </div>
  )
}
