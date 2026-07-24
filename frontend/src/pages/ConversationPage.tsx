import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import dialogBoxUrl from '@/assets/images/conversation/dialog-box.webp'
import diaryButtonUrl from '@/assets/images/conversation/diary-btn.webp'
import diaryListButtonUrl from '@/assets/images/conversation/diary-list-btn.webp'
import dinoUrl from '@/assets/images/conversation/dino.webp'
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
import { useFitScale } from '@/hooks/useFitScale'

type ConversationStage = 'guide' | 'conversation' | 'complete'
type VoiceStatus = 'ready' | 'listening' | 'thinking' | 'speaking' | 'error'

type SpeechRecognitionEventLike = Event & {
  resultIndex: number
  results: {
    [index: number]: {
      isFinal: boolean
      0: { transcript: string }
    }
    length: number
  }
}

type SpeechRecognitionErrorEventLike = Event & {
  error: string
}

type SpeechRecognitionLike = {
  continuous: boolean
  interimResults: boolean
  lang: string
  onresult: ((event: SpeechRecognitionEventLike) => void) | null
  onerror: ((event: SpeechRecognitionErrorEventLike) => void) | null
  onend: (() => void) | null
  start: () => void
  stop: () => void
  abort: () => void
}

type SpeechRecognitionConstructor = new () => SpeechRecognitionLike

declare global {
  interface Window {
    SpeechRecognition?: SpeechRecognitionConstructor
    webkitSpeechRecognition?: SpeechRecognitionConstructor
  }
}

const jua = { fontFamily: "'Jua', sans-serif" } as const
const QUESTIONS = [
  '안녕! 오늘은 어떤 하루를 보냈어?',
  '그중에서 가장 기억에 남는 일은 뭐였어?',
  '그때 누구와 함께 있었어?',
  '그 일이 있었을 때 어떤 기분이 들었어?',
  '내일은 어떤 하루를 보내고 싶어?',
]
const DIARY_LINES = [
  '오늘은 과일 자르기에서 과일을 힘차게 잘랐다.',
  '빵긋이에게 오늘 있었던 재미있는 일도 들려주었다.',
  '가장 기억에 남는 순간을 이야기해서 기분이 좋았다.',
  '내일도 신나게 놀고 빵긋이와 이야기하고 싶다.',
]

const STATUS_COPY: Record<VoiceStatus, string> = {
  ready: '마이크 버튼을 누르고 이야기해 줘',
  listening: '잘 듣고 있어요',
  thinking: '이야기를 생각하고 있어요',
  speaking: '빵긋이가 이야기하고 있어요',
  error: '목소리를 듣지 못했어요. 다시 말해 줄래?',
}

export function ConversationPage() {
  const navigate = useNavigate()
  const canvasRef = useFitScale<HTMLDivElement>()
  const recognitionRef = useRef<SpeechRecognitionLike | null>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const answerRef = useRef('')
  const advanceTimerRef = useRef<number | null>(null)
  const [stage, setStage] = useState<ConversationStage>('guide')
  const [status, setStatus] = useState<VoiceStatus>('ready')
  const [questionIndex, setQuestionIndex] = useState(0)
  const [transcript, setTranscript] = useState('')
  const [micLevel, setMicLevel] = useState(0)
  const [guideMessage, setGuideMessage] = useState('')
  const [isDiaryOpen, setIsDiaryOpen] = useState(false)
  const [isDiaryAttached, setIsDiaryAttached] = useState(false)
  const [isSpeechSupported] = useState(
    () =>
      Boolean(window.SpeechRecognition || window.webkitSpeechRecognition) &&
      Boolean(navigator.mediaDevices?.getUserMedia),
  )

  const stopListening = useCallback(() => {
    recognitionRef.current?.abort()
    recognitionRef.current = null
  }, [])

  const stopVoiceResources = useCallback(() => {
    stopListening()
    window.speechSynthesis?.cancel()
    streamRef.current?.getTracks().forEach((track) => track.stop())
    streamRef.current = null
    if (advanceTimerRef.current !== null) {
      window.clearTimeout(advanceTimerRef.current)
      advanceTimerRef.current = null
    }
  }, [stopListening])

  useEffect(() => stopVoiceResources, [stopVoiceResources])

  useEffect(() => {
    if (status !== 'listening' || !streamRef.current) {
      setMicLevel(0)
      return
    }

    const audioContext = new AudioContext()
    const analyser = audioContext.createAnalyser()
    analyser.fftSize = 64
    const source = audioContext.createMediaStreamSource(streamRef.current)
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
  }, [status])

  const speak = useCallback((text: string, onEnd?: () => void) => {
    if (!('speechSynthesis' in window)) {
      setStatus('ready')
      onEnd?.()
      return
    }

    window.speechSynthesis.cancel()
    const utterance = new SpeechSynthesisUtterance(text)
    utterance.lang = 'ko-KR'
    utterance.rate = 0.9
    utterance.pitch = 1.12
    utterance.volume = 1
    utterance.onstart = () => setStatus('speaking')
    utterance.onend = () => {
      setStatus('ready')
      onEnd?.()
    }
    utterance.onerror = () => {
      setStatus('ready')
      onEnd?.()
    }
    window.speechSynthesis.speak(utterance)
  }, [])

  const completeConversation = useCallback(() => {
    stopListening()
    setStage('complete')
    setStatus('speaking')
    speak('오늘 이야기도 정말 즐거웠어! 다음에 또 이야기하자.')
  }, [speak, stopListening])

  const acceptAnswer = useCallback(
    (answer: string) => {
      if (!answer.trim()) {
        setStatus('error')
        return
      }

      setTranscript(answer.trim())
      setStatus('thinking')
      advanceTimerRef.current = window.setTimeout(() => {
        if (questionIndex >= QUESTIONS.length - 1) {
          completeConversation()
          return
        }

        const nextQuestionIndex = questionIndex + 1
        setQuestionIndex(nextQuestionIndex)
        setTranscript('')
        speak(QUESTIONS[nextQuestionIndex])
      }, 900)
    },
    [completeConversation, questionIndex, speak],
  )

  const startListening = useCallback(() => {
    if (stage !== 'conversation' || status === 'thinking') return

    if (status === 'speaking') {
      window.speechSynthesis.cancel()
    }

    const Recognition =
      window.SpeechRecognition ?? window.webkitSpeechRecognition
    if (!Recognition) {
      setStatus('error')
      return
    }

    stopListening()
    answerRef.current = ''
    setTranscript('')
    const recognition = new Recognition()
    recognition.lang = 'ko-KR'
    recognition.continuous = false
    recognition.interimResults = true
    recognition.onresult = (event) => {
      let partial = ''
      let final = ''

      for (
        let index = event.resultIndex;
        index < event.results.length;
        index++
      ) {
        const result = event.results[index]
        if (result.isFinal) final += result[0].transcript
        else partial += result[0].transcript
      }

      if (final) answerRef.current = final
      setTranscript(final || partial)
    }
    recognition.onerror = (event) => {
      recognitionRef.current = null
      if (event.error !== 'aborted') setStatus('error')
    }
    recognition.onend = () => {
      recognitionRef.current = null
      const answer = answerRef.current
      if (answer) acceptAnswer(answer)
      else {
        setStatus((current) => (current === 'listening' ? 'ready' : current))
      }
    }
    recognitionRef.current = recognition

    try {
      recognition.start()
      setStatus('listening')
    } catch {
      recognitionRef.current = null
      setStatus('error')
    }
  }, [acceptAnswer, stage, status, stopListening])

  const finishListening = () => {
    recognitionRef.current?.stop()
  }

  const startConversation = async () => {
    setGuideMessage('')
    if (!isSpeechSupported) {
      setGuideMessage('이 브라우저에서는 음성 대화를 사용할 수 없어요.')
      return
    }

    try {
      streamRef.current = await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
        },
      })
      setStage('conversation')
      setStatus('speaking')
      speak(QUESTIONS[0])
    } catch {
      setGuideMessage(
        '대화를 시작하려면 브라우저에서 마이크 사용을 허용해 주세요.',
      )
    }
  }

  const leaveConversation = () => {
    if (
      stage === 'conversation' &&
      !window.confirm('대화를 그만하고 집으로 돌아갈까요?')
    ) {
      return
    }
    stopVoiceResources()
    navigate('/child')
  }

  return (
    <div className="flex h-screen w-screen items-start justify-center overflow-hidden bg-[radial-gradient(120%_120%_at_50%_20%,_#12241a_0%,_#0b140e_100%)]">
      <div
        className="relative h-[720px] w-[1280px] origin-top overflow-hidden"
        ref={canvasRef}
      >
        <img
          alt=""
          className="absolute inset-0 size-full object-cover"
          fetchPriority="high"
          src={talkBackgroundUrl}
        />

        <button
          aria-label="집으로 돌아가기"
          className="absolute top-4 left-[78px] z-40 size-[58px] bg-transparent p-0 drop-shadow-[0_4px_8px_rgba(0,0,0,.28)] transition hover:scale-105"
          onClick={leaveConversation}
          type="button"
        >
          <img
            alt=""
            className="size-full object-contain"
            src={backButtonUrl}
          />
        </button>
        <h1
          className="absolute top-[19px] left-[150px] z-30 text-[30px] text-white drop-shadow-lg [-webkit-text-stroke:5px_#7a5a2a] [paint-order:stroke_fill]"
          style={jua}
        >
          대화하기
        </h1>

        <div className="absolute top-4 right-[78px] z-30 flex flex-col gap-2">
          <button
            aria-label="오늘의 일기"
            className="size-[58px] bg-transparent p-0 transition hover:scale-105"
            onClick={() => setIsDiaryOpen(true)}
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

        {stage === 'conversation' ? (
          <div
            aria-label={`대화 진행 ${questionIndex + 1}/${QUESTIONS.length}`}
            className="absolute top-[24px] left-1/2 z-30 flex -translate-x-1/2 items-center gap-3 rounded-full border-2 border-white/75 bg-[#5c7c45]/85 px-5 py-2.5 shadow-lg backdrop-blur-sm"
            style={jua}
          >
            <span className="text-[17px] text-white">
              이야기 {questionIndex + 1}/{QUESTIONS.length}
            </span>
            <div className="flex gap-1.5">
              {QUESTIONS.map((_, index) => (
                <span
                  className={`size-3 rounded-full border-2 border-white/80 ${
                    index <= questionIndex ? 'bg-[#ffdb63]' : 'bg-white/25'
                  }`}
                  key={index}
                />
              ))}
            </div>
          </div>
        ) : null}

        <img
          alt="공룡 친구 빵긋"
          className="absolute bottom-[72px] left-3/4 h-[530px] w-auto max-w-none -translate-x-1/2 [animation:conversation-dino-breathe_3.4s_ease-in-out_infinite] drop-shadow-[0_22px_22px_rgba(0,0,0,.3)]"
          src={dinoUrl}
        />

        {stage !== 'guide' ? (
          <>
            <div className="absolute bottom-[90px] left-1/2 z-20 w-[710px] -translate-x-1/2">
              <img
                alt=""
                className="block w-full drop-shadow-[0_10px_20px_rgba(0,0,0,.28)]"
                src={dialogBoxUrl}
              />
              <span
                className="absolute top-[11.9%] left-[9.3%] -translate-1/2 text-[24px] whitespace-nowrap text-white drop-shadow"
                style={jua}
              >
                빵긋
              </span>
              <p
                className="absolute top-[48%] right-[8%] left-[9%] -translate-y-1/2 text-[28px] leading-[1.35] text-[#6a4a1e]"
                style={jua}
              >
                {QUESTIONS[questionIndex]}
              </p>
              {transcript ? (
                <p
                  className="absolute right-[8%] bottom-[10%] left-[9%] truncate text-[17px] text-[#8b744f]"
                  style={jua}
                >
                  “{transcript}”
                </p>
              ) : null}
            </div>

            <div className="absolute right-[95px] bottom-[94px] z-30 flex w-[170px] flex-col items-center">
              <div
                className={`mb-2 rounded-full px-3 py-1.5 text-center text-[15px] text-white shadow-md ${
                  status === 'error' ? 'bg-[#c85f50]' : 'bg-[#527542]/90'
                }`}
                role="status"
                style={jua}
              >
                {STATUS_COPY[status]}
              </div>
              <button
                aria-label={
                  status === 'listening' ? '말하기 마치기' : '마이크 켜기'
                }
                className={`relative flex size-[82px] items-center justify-center rounded-full border-[5px] border-white/90 shadow-[0_8px_18px_rgba(0,0,0,.3)] transition hover:scale-105 ${
                  status === 'listening'
                    ? 'bg-[#ef745f]'
                    : status === 'thinking'
                      ? 'cursor-wait bg-[#d6a950]'
                      : 'bg-[#72a950]'
                }`}
                disabled={status === 'thinking'}
                onClick={
                  status === 'listening' ? finishListening : startListening
                }
                type="button"
              >
                {status === 'thinking' ? (
                  <span className="size-9 animate-spin rounded-full border-4 border-white/40 border-t-white" />
                ) : (
                  <svg
                    aria-hidden="true"
                    className="size-10 text-white"
                    fill="currentColor"
                    viewBox="0 0 24 24"
                  >
                    <path d="M12 15.5a3.5 3.5 0 0 0 3.5-3.5V5a3.5 3.5 0 1 0-7 0v7a3.5 3.5 0 0 0 3.5 3.5Zm-1.5-10.5a1.5 1.5 0 1 1 3 0v7a1.5 1.5 0 1 1-3 0V5Zm-5 6a1 1 0 0 1 1 1 5.5 5.5 0 0 0 11 0 1 1 0 1 1 2 0 7.5 7.5 0 0 1-6.5 7.43V22h2a1 1 0 1 1 0 2H9a1 1 0 1 1 0-2h2v-2.07A7.5 7.5 0 0 1 4.5 12a1 1 0 0 1 1-1Z" />
                  </svg>
                )}
                {status === 'listening' ? (
                  <span
                    className="absolute inset-[-10px] -z-10 rounded-full border-4 border-[#ff9d88]/70"
                    style={{ transform: `scale(${1 + micLevel * 0.24})` }}
                  />
                ) : null}
              </button>
            </div>
          </>
        ) : null}

        {stage === 'guide' ? (
          <div className="absolute inset-0 z-50 flex items-center justify-center bg-[rgba(16,35,23,.58)] backdrop-blur-[2px]">
            <section className="relative h-[534px] w-[700px] text-center drop-shadow-[0_24px_55px_rgba(0,0,0,.38)]">
              <img
                alt=""
                className="pointer-events-none absolute inset-0 size-full object-fill"
                src={readyPopupUrl}
              />
              <h2
                className="absolute top-[10.5%] right-0 left-0 text-[34px] text-[#52733b]"
                style={jua}
              >
                도란도란 얘기해요
              </h2>
              <p
                className="absolute top-[19.5%] right-0 left-0 text-[17px] text-[#8b7650]"
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
                  <p className="mt-1 text-[16px] text-[#725b33]" style={jua}>
                    {index + 1}. {label}
                  </p>
                </div>
              ))}

              <p
                className="absolute top-[70%] right-[12%] left-[12%] min-h-6 text-[15px] text-[#bc5b4c]"
                role="alert"
                style={jua}
              >
                {guideMessage ||
                  '빵긋이가 말하는 중에도 언제든 이야기할 수 있어요.'}
              </p>
              <button
                className="absolute top-[76%] left-1/2 flex h-[11%] w-[45%] -translate-x-1/2 items-center justify-center bg-transparent pb-1 text-[22px] text-white transition hover:scale-[1.02]"
                onClick={() => void startConversation()}
                style={{
                  ...jua,
                  paintOrder: 'stroke fill',
                  WebkitTextStroke: '3px #587d2d',
                }}
                type="button"
              >
                대화 시작하기
              </button>
            </section>
          </div>
        ) : null}

        {stage === 'complete' && !isDiaryOpen ? (
          <div className="absolute inset-0 z-50 flex items-center justify-center bg-[rgba(16,35,23,.62)] backdrop-blur-[3px]">
            <section className="relative w-[560px] rounded-[46px] border-[7px] border-[#d6b672] bg-[#fffaf0] px-12 py-10 text-center shadow-[0_24px_55px_rgba(0,0,0,.42)]">
              <div className="absolute inset-[8px] rounded-[32px] border-2 border-[#ead8ab]" />
              <div className="relative">
                <div className="text-[58px]">🌟</div>
                <p className="mt-1 text-[20px] text-[#86a85e]" style={jua}>
                  이야기 완성!
                </p>
                <h2 className="mt-1 text-[34px] text-[#4f6f38]" style={jua}>
                  오늘 이야기도 정말 즐거웠어
                </h2>
                <p className="mt-3 text-[18px] text-[#806b45]" style={jua}>
                  빵긋이가 네 이야기를 소중히 간직할게.
                </p>
                <div className="mt-7 flex justify-center gap-1">
                  <button
                    className="h-[70px] w-[240px] bg-[length:100%_100%] bg-center bg-no-repeat text-[19px] text-white"
                    onClick={() => {
                      stopVoiceResources()
                      setIsDiaryOpen(true)
                    }}
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
                  <button
                    className="-ml-4 h-[70px] w-[240px] bg-[length:100%_100%] bg-center bg-no-repeat text-[19px] text-[#6a5431]"
                    onClick={() => {
                      stopVoiceResources()
                      navigate('/child')
                    }}
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
            <button
              aria-label="완료 화면으로 돌아가기"
              className="absolute top-4 left-[78px] z-20 size-[58px] bg-transparent p-0 drop-shadow-[0_4px_8px_rgba(0,0,0,.28)] transition hover:scale-105"
              onClick={() => setIsDiaryOpen(false)}
              type="button"
            >
              <img
                alt=""
                className="size-full object-contain"
                src={backButtonUrl}
              />
            </button>

            <section className="absolute top-[-10px] left-6/7 w-[860px] -translate-x-1/2 [animation:diary-pop-in_.38s_cubic-bezier(.2,1.2,.5,1)_both]">
              <img
                alt=""
                className="block w-full drop-shadow-[0_16px_30px_rgba(0,0,0,.35)]"
                src={diaryNoteUrl}
              />
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
                  {isDiaryAttached ? '내 일기장에 예쁘게 붙였어요!' : ''}
                </p>
                <div className="-mt-1 flex flex-col items-center">
                  <button
                    className="relative block w-[320px] translate-x-2.5 transition hover:scale-105 active:scale-[.98]"
                    disabled={isDiaryAttached}
                    onClick={() => setIsDiaryAttached(true)}
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
                    className="relative -mt-3 block w-[345px] translate-x-3 transition hover:scale-105 active:scale-[.98]"
                    onClick={() => {
                      stopVoiceResources()
                      navigate('/child')
                    }}
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
      </div>
    </div>
  )
}
