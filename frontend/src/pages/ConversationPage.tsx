import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'

import { isCompletedTerminationReason } from '@/api/conversations'
import { previewChildDiary, saveChildDiary } from '@/api/diaries'
import dialogBoxUrl from '@/assets/images/conversation/dialog-box.webp'
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
import { DiaryBookButton } from '@/components/DiaryBookButton'
import { ParentNoticeDialog } from '@/components/ParentNoticeDialog'
import { useConversationBgm } from '@/hooks/useConversationBgm'
import { COMPLETE_BUTTON_CLIP, PHOTO_BUTTON_CLIP } from '@/lib/buttonClip'
import { useFitScale } from '@/hooks/useFitScale'
import {
  type VoiceStatus,
  useRealtimeConversation,
} from '@/hooks/useRealtimeConversation'

const jua = { fontFamily: "'Jua', sans-serif" } as const

const MIC_LEVEL_NOISE_FLOOR = 0.015
const MIC_LEVEL_FULL_SCALE = 0.12
const MIC_LEVEL_SMOOTHING = 0.25
const MIC_LEVEL_ZERO_CUTOFF = 0.01

type DiaryStatus = 'idle' | 'loading' | 'ready' | 'error'
type DiaryData = { title: string; lines: string[]; previewToken: string }

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

/*
  마이크 위 안내.

  말풍선은 어절 단위로만 줄을 바꾼다(break-keep). 한국어는 기본값으로 두면 글자
  아무 데서나 잘려 "빵긋이가 생각하 / 고 있어요"처럼 읽힌다.

  꼭 여기서 끊고 싶은 자리는 \n으로 직접 적는다. 폭에 맡기면 그날 문구 길이에 따라
  끊기는 자리가 달라진다.
*/
const STATUS_COPY: Record<VoiceStatus, string> = {
  listening: '이야기해 줘\n듣고 있어',
  speaking: '지금 빵긋이가 \n말하고 있어요',
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
    state.voice === 'listening' ? 0 : state.voice === 'speaking' ? 0.055 : 0.22
  const { isBgmPlaying, setVolume, toggleBgm } = useConversationBgm()

  useEffect(() => {
    setVolume(conversationBgmVolume)
  }, [conversationBgmVolume, setVolume])

  const [micLevel, setMicLevel] = useState(0)
  const [dinoFrameStep, setDinoFrameStep] = useState(0)
  const [isDiaryOpen, setIsDiaryOpen] = useState(false)
  const [isDiaryAttached, setIsDiaryAttached] = useState(false)
  const [isLeaveConfirmOpen, setIsLeaveConfirmOpen] = useState(false)
  const [diaryStatus, setDiaryStatus] = useState<DiaryStatus>('idle')
  const [diaryData, setDiaryData] = useState<DiaryData | null>(null)
  const [isSavingDiary, setIsSavingDiary] = useState(false)
  const diaryRequestConversationRef = useRef<string | null>(null)

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
    analyser.fftSize = 1024
    const source = audioContext.createMediaStreamSource(state.localStream)
    source.connect(analyser)
    const samples = new Float32Array(analyser.fftSize)
    let animationFrame = 0
    let smoothedLevel = 0

    const updateLevel = () => {
      analyser.getFloatTimeDomainData(samples)
      const meanSquare =
        samples.reduce((sum, sample) => sum + sample * sample, 0) /
        samples.length
      const rms = Math.sqrt(meanSquare)

      // 브라우저 AGC가 키운 팬·마찰음 같은 무음 바닥은 시각화하지 않는다.
      // 일반적인 말소리 대역은 0~1로 정규화하고 EMA로 링 떨림을 줄인다.
      const normalized =
        rms <= MIC_LEVEL_NOISE_FLOOR
          ? 0
          : Math.min(1, (rms - MIC_LEVEL_NOISE_FLOOR) / MIC_LEVEL_FULL_SCALE)
      smoothedLevel =
        smoothedLevel * (1 - MIC_LEVEL_SMOOTHING) +
        normalized * MIC_LEVEL_SMOOTHING
      if (smoothedLevel < MIC_LEVEL_ZERO_CUTOFF) smoothedLevel = 0
      setMicLevel(smoothedLevel)
      animationFrame = window.requestAnimationFrame(updateLevel)
    }
    updateLevel()

    return () => {
      window.cancelAnimationFrame(animationFrame)
      source.disconnect()
      void audioContext.close()
    }
  }, [isListening, state.localStream])

  // 대화 종료가 확정되면 일기 미리보기를 즉시 한 번만 요청한다. 아이가 모달을
  // 열었을 때 아직 생성 중이면 안내 문구를 유지하고, 완료됐다면 바로 교체한다.
  const { conversationId, childId } = state
  useEffect(() => {
    if (!isEnded || !canMakeDiary) return
    if (conversationId === null || childId === null) return
    if (diaryRequestConversationRef.current === conversationId) return
    diaryRequestConversationRef.current = conversationId
    setDiaryStatus('loading')

    void previewChildDiary(childId, conversationId)
      .then((preview) => {
        setDiaryData({
          lines: preview.lines,
          previewToken: preview.previewToken,
          title: preview.title,
        })
        setDiaryStatus('ready')
      })
      .catch(() => {
        setDiaryStatus('error')
      })
  }, [isEnded, canMakeDiary, conversationId, childId])

  const openDiary = useCallback(() => {
    setIsDiaryOpen(true)
  }, [])

  const attachDiary = useCallback(async () => {
    if (isDiaryAttached) {
      navigate('/diaries', { state: { from: '/conversation' } })
      return
    }
    if (diaryStatus !== 'ready' || diaryData === null || isSavingDiary) return
    if (childId === null) return

    setIsSavingDiary(true)
    try {
      await saveChildDiary(childId, diaryData.previewToken)
    } catch {
      // 토큰 만료(10분)나 일시 오류면 미리보기를 한 번 다시 받아 저장을 시도한다.
      // 이미 저장된 대화(중복)면 그대로 일기장으로 보내면 방금 일기가 보인다.
      if (conversationId !== null) {
        try {
          const fresh = await previewChildDiary(childId, conversationId)
          await saveChildDiary(childId, fresh.previewToken)
        } catch {
          // 유아 화면이라 실패를 강조하지 않고 일기장으로 넘어간다.
        }
      }
    } finally {
      setIsSavingDiary(false)
      setIsDiaryAttached(true)
      navigate('/diaries', { state: { from: '/conversation' } })
    }
  }, [
    childId,
    conversationId,
    diaryData,
    diaryStatus,
    isDiaryAttached,
    isSavingDiary,
    navigate,
  ])

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
          aria-label="빵긋이 목소리"
          autoPlay
          muted={state.remoteAudioMuted}
          ref={remoteAudioRef}
        />
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
        <DiaryBookButton className="absolute top-[25px] right-[25px] z-30" />

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
          className="absolute bottom-[215px] left-12/19 size-[400px] -translate-x-1/2 [animation:conversation-dino-breathe_3.4s_ease-in-out_infinite] drop-shadow-[0_22px_22px_rgba(0,0,0,.3)]"
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
            <div className="absolute bottom-[45px] left-1/2 z-20 w-[710px] -translate-x-1/2">
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
                className="absolute top-[48%] right-[8%] left-[9%] -translate-y-1/2 text-[30px] leading-[1.35] break-keep text-[#6a4a1e]"
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
              <div className="absolute right-[95px] bottom-[45px] z-30 flex w-[170px] flex-col items-center">
                <div
                  className={`-mb-0 max-w-[280px] rounded-[26px] px-4 py-2 text-center text-[24px] leading-[1.25] break-keep whitespace-pre-line text-white shadow-md ${
                    state.notice ? 'bg-[#c85f50]' : 'bg-[#527542]/90'
                  }`}
                  role="status"
                  style={jua}
                >
                  {state.notice ??
                    (state.micEnabled
                      ? STATUS_COPY[state.voice]
                      : '마이크가\n꺼져 있어')}
                </div>
                <button
                  aria-label={state.micEnabled ? '마이크 끄기' : '마이크 켜기'}
                  className="relative flex size-[120px] items-center justify-center border-0 bg-transparent p-0 drop-shadow-[0_8px_12px_rgba(0,0,0,.3)] transition hover:scale-105 disabled:opacity-70"
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
                      className="absolute inset-[-10px] -z-10 rounded-full border-4 border-[#ff9d88]/70 transition-[opacity,transform] duration-100"
                      style={{
                        opacity: micLevel === 0 ? 0 : 0.35 + micLevel * 0.65,
                        transform: `scale(${1 + micLevel * 0.24})`,
                      }}
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
                  className="absolute top-[68%] right-[12%] left-[12%] min-h-6 text-[25px] text-[#bc5b4c]"
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
                <p className="mt-1 text-[35px] text-[#86a85e]" style={jua}>
                  {canMakeDiary ? '이야기 완성!' : '다음에 또 만나'}
                </p>
                <h2 className="mt-1 text-[40px] text-[#4f6f38]" style={jua}>
                  오늘 이야기도 정말 즐거웠어
                </h2>
                <p className="mt-3 text-[25px] text-[#806b45]" style={jua}>
                  {canMakeDiary
                    ? '빵긋이가 네 이야기를 소중히 간직할게.'
                    : '다음에 더 많이 이야기해 줘!'}
                </p>
                <div className="mt-1 -mb-2 flex justify-center gap-0">
                  {canMakeDiary ? (
                    <button
                      className="h-[100px] w-[270px] bg-[length:100%_100%] bg-center bg-no-repeat text-[22px] text-white"
                      onClick={() => void openDiary()}
                      style={{
                        ...jua,
                        backgroundImage: `url(${completeButtonUrl})`,
                        clipPath: COMPLETE_BUTTON_CLIP,
                        paintOrder: 'stroke fill',
                        WebkitTextStroke: '3px #4a7d1e',
                      }}
                      type="button"
                    >
                      오늘의 일기 보기
                    </button>
                  ) : null}
                  <button
                    className={`h-[100px] w-[320px] bg-[length:100%_100%] bg-center bg-no-repeat text-[27px] text-[#6a5431] ${
                      canMakeDiary ? '-ml-9' : ''
                    }`}
                    onClick={() => navigate('/child')}
                    style={{
                      ...jua,
                      backgroundImage: `url(${photoButtonUrl})`,
                      clipPath: PHOTO_BUTTON_CLIP,
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
                <div
                  aria-live="polite"
                  className="mt-3.5 flex max-h-[240px] min-h-[140px] max-w-[650px] flex-col justify-center gap-2 overflow-y-auto"
                >
                  {diaryStatus === 'ready' && diaryData ? (
                    diaryData.lines.map((line) => (
                      <p
                        className="text-[23px] leading-[1.38] text-[#5f4626]"
                        key={line}
                        style={jua}
                      >
                        {line}
                      </p>
                    ))
                  ) : diaryStatus === 'error' ? (
                    <p
                      className="text-[22px] leading-[1.5] text-[#8a6a34]"
                      style={jua}
                    >
                      일기는 잠시 뒤에 일기장에서 볼 수 있어.
                    </p>
                  ) : (
                    <p
                      className="text-[22px] leading-[1.5] text-[#6f8e3f]"
                      style={jua}
                    >
                      빵긋이가 일기를 만들고 있어요…
                    </p>
                  )}
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
                    className="translate-x-2.4 relative block w-[320px] transition hover:scale-105 active:scale-[.98] disabled:pointer-events-none disabled:opacity-50"
                    disabled={
                      !isDiaryAttached &&
                      (diaryStatus !== 'ready' || isSavingDiary)
                    }
                    onClick={() => void attachDiary()}
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
                        : isSavingDiary
                          ? '붙이는 중…'
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
