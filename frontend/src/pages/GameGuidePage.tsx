import { useCallback, useEffect, useState, type RefObject } from 'react'
import { Navigate, useNavigate, useParams } from 'react-router-dom'

import bgmUrl from '@/assets/audio/start/green-adventure-start.mp3'
import fruitBgmUrl from '@/assets/audio/fruit-game/fruit-forest-rhythm.mp3'
import guideBackButtonUrl from '@/assets/images/game-entry/guide-back-btn.webp'
import guideBoardUrl from '@/assets/images/game-entry/guide-board.webp'
import guideStartButtonUrl from '@/assets/images/game-entry/guide-start-btn.webp'
import backgroundUrl from '@/assets/images/game-entry/game-select-bg.webp'
import fruitPracticeBackgroundUrl from '@/assets/images/tutorial/fruit/fruit-ninja-bg-ver2.png'
import leftButtonUrl from '@/assets/images/game-entry/left-btn.webp'
import closeButtonUrl from '@/assets/images/start/close-btn.webp'
import musicOnUrl from '@/assets/images/start/music-btn.webp'
import musicOffUrl from '@/assets/images/start/mute-btn.webp'
import { FRUIT_GAME_CONFIG } from '@/features/games/fruit/fruitGameConfig'
import { getGameById } from '@/features/games/games'
import {
  SpeechBubble,
  TutorialBoard,
  TutorialPanel,
} from '@/features/games/tutorial/TutorialBoard'
import {
  CelebrationVideo,
  DobbleGrabVideo,
  DobbleHands,
  FruitPose,
  FruitSliceVideo,
  GreetingVideo,
} from '@/features/games/tutorial/TutorialMedia'
import { TutorialCamera } from '@/features/games/tutorial/TutorialCamera'
import {
  isTutorialGame,
  TUTORIAL_STEPS,
  type TutorialGameId,
  type TutorialStepKind,
} from '@/features/games/tutorial/tutorialSteps'
import { DobblePracticeStage } from '@/features/games/tutorial/DobblePracticeStage'
import { useAttachStream } from '@/features/games/tutorial/useAttachStream'
import { useDetectionHold } from '@/features/games/tutorial/useDetectionHold'
import {
  useDobblePractice,
  type DobblePracticeState,
} from '@/features/games/tutorial/useDobblePractice'
import {
  useFruitPractice,
  type FruitPracticeState,
} from '@/features/games/tutorial/useFruitPractice'
import { useBgmPlayer } from '@/hooks/useBgmPlayer'
import { useCameraStream, type CameraStatus } from '@/hooks/useCameraStream'
import { useFitScale } from '@/hooks/useFitScale'
import { useHandGestures } from '@/hooks/useHandGestures'
import { useHandTracking } from '@/hooks/useHandTracking'

const jua = { fontFamily: "'Jua', sans-serif" } as const

/** 이 시간만큼 계속 잡혀야 인식 성공으로 본다(ms). */
const DETECT_HOLD_MS = 1200
/** 연습에서 잘라야 하는 과일 수. 성공을 맛보게 하는 게 목적이라 짧게 끝낸다. */
const PRACTICE_TARGET_FRUITS = 3

export function GameGuidePage() {
  const navigate = useNavigate()
  const { gameId } = useParams()
  const canvasRef = useFitScale<HTMLDivElement>()
  const { audioRef, isBgmPlaying, toggleBgm } = useBgmPlayer()
  const [stepIndex, setStepIndex] = useState(0)

  // 훅은 조건 없이 먼저 부른다. 잘못된 gameId로 들어와도 훅 호출 순서가
  // 흔들리면 안 되므로, 화면 이탈 판단은 훅을 다 부른 뒤에 한다.
  const game = getGameById(gameId)
  const tutorialGameId: TutorialGameId | null =
    game && isTutorialGame(game.id) ? game.id : null

  const steps = TUTORIAL_STEPS[tutorialGameId ?? 'fruit']
  const step = steps[Math.min(stepIndex, steps.length - 1)]
  const needsCamera = step.kind === 'detect' || step.kind === 'practice'

  const { requestCamera, status, streamRef, videoRef } = useCameraStream()
  // 게임마다 필요한 모델이 다르다. 둘을 동시에 올리면 낭비라 한쪽만 켠다.
  const cameraReady = status === 'granted'
  const hand = useHandTracking(
    videoRef,
    cameraReady && tutorialGameId === 'fruit',
  )
  const gestures = useHandGestures(
    videoRef,
    cameraReady && tutorialGameId === 'dobble',
  )

  // 카메라는 필요한 단계에 들어갈 때 요청한다. 안내를 보기만 할 아이에게
  // 처음부터 권한 창을 띄우지 않는다.
  useEffect(() => {
    if (needsCamera && status === 'idle') {
      void requestCamera()
    }
  }, [needsCamera, requestCamera, status])

  const goNext = useCallback(
    () => setStepIndex((current) => Math.min(current + 1, steps.length - 1)),
    [steps.length],
  )

  const detectCheck = useCallback(() => {
    if (tutorialGameId === 'fruit') {
      return hand.playHandRef.current !== null
    }
    // 도블은 두 손을 다 써야 하는 게임이라 둘 다 잡혀야 통과시킨다.
    const frame = gestures.gesturesRef.current
    return frame.a !== null && frame.b !== null
  }, [gestures.gesturesRef, hand.playHandRef, tutorialGameId])

  const detection = useDetectionHold({
    active: step.kind === 'detect' && cameraReady,
    check: detectCheck,
    holdMs: DETECT_HOLD_MS,
    onComplete: goNext,
  })

  const dobblePractice = useDobblePractice({
    active:
      step.kind === 'practice' && cameraReady && tutorialGameId === 'dobble',
    gesturesRef: gestures.gesturesRef,
    onComplete: goNext,
  })

  const practice = useFruitPractice({
    active:
      step.kind === 'practice' && cameraReady && tutorialGameId === 'fruit',
    handRef: hand.playHandRef,
    onComplete: goNext,
    targetCount: PRACTICE_TARGET_FRUITS,
  })

  if (!game || !tutorialGameId) {
    return <Navigate replace to="/games" />
  }

  const modelReady =
    tutorialGameId === 'fruit' ? hand.modelReady : gestures.modelReady
  // 권한 거부·장치 없음·연결 끊김이면 인식으로 넘어갈 방법이 없다.
  const cameraUsable =
    status !== 'denied' && status !== 'unavailable' && status !== 'error'

  const backToSelect = () => navigate('/games')
  const startGame = () => navigate(`/games/${game.id}/play`)
  const goPrev = () => setStepIndex((current) => Math.max(current - 1, 0))
  const relearn = () => setStepIndex(0)

  return (
    <div className="flex h-screen w-screen items-center justify-center overflow-hidden bg-[radial-gradient(120%_120%_at_50%_20%,_#123219_0%,_#08160d_100%)]">
      <div
        ref={canvasRef}
        className="relative h-[720px] w-[1280px] overflow-hidden text-white"
      >
        <audio
          aria-label="초록 모험 시작 배경 음악"
          loop
          preload="auto"
          ref={audioRef}
          src={game.id === 'fruit' ? fruitBgmUrl : bgmUrl}
        />

        <img
          alt=""
          className="absolute inset-0 h-full w-full object-cover"
          decoding="async"
          fetchPriority="high"
          src={backgroundUrl}
        />
        <div className="pointer-events-none absolute inset-0 bg-[linear-gradient(180deg,rgba(4,18,9,0.14)_0%,rgba(4,18,9,0.05)_42%,rgba(4,18,9,0.22)_100%)]" />

        <button
          aria-label="게임 선택으로 돌아가기"
          className="absolute top-[25px] left-[25px] z-30 size-[62px] border-0 bg-transparent p-0 drop-shadow-[0_5px_8px_rgba(20,55,20,0.32)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white active:scale-95"
          onClick={backToSelect}
          type="button"
        >
          <img
            alt=""
            className="block h-full w-full object-contain"
            decoding="async"
            draggable={false}
            src={leftButtonUrl}
          />
        </button>

        <button
          aria-label="게임 방법 닫기"
          className="absolute top-[25px] right-[25px] z-30 size-[62px] border-0 bg-transparent p-0 drop-shadow-[0_5px_8px_rgba(20,55,20,0.32)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white active:scale-95"
          onClick={backToSelect}
          type="button"
        >
          <img
            alt=""
            className="block h-full w-full object-contain"
            decoding="async"
            draggable={false}
            src={closeButtonUrl}
          />
        </button>

        {/*
          나무 보드 패널. 세로는 이미 화면(720px) 한계에 가까워 더 못 키운다.
          테두리 장식이 늘어나 보이지 않도록 원본 비율(836:700)을 지킨 채
          세로를 꽉 채우는 크기로 잡았다.
        */}
        <div className="absolute top-1/2 left-1/2 h-[712px] w-[850px] -translate-x-1/2 -translate-y-1/2">
          <img
            alt=""
            className="absolute inset-0 h-full w-full object-fill drop-shadow-[0_20px_40px_rgba(15,40,12,0.42)]"
            decoding="async"
            draggable={false}
            src={guideBoardUrl}
          />

          <TutorialBoard
            body={
              <StepBody
                camera={{
                  detected: detection.detected,
                  modelReady,
                  progress: detection.progress,
                  status,
                  streamRef,
                  videoRef,
                }}
                dobblePractice={dobblePractice}
                gameId={tutorialGameId}
                kind={step.kind}
                practice={practice}
                speech={step.speech}
              />
            }
            bodyOverflowVisible={
              step.kind === 'intro' || step.kind === 'demo' || step.kind === 'ready'
            }
            footer={
              <StepFooter
                cameraUsable={cameraUsable}
                kind={step.kind}
                onNext={goNext}
                onPrev={goPrev}
                onQuit={backToSelect}
                onRelearn={relearn}
                onStart={startGame}
                showPrev={stepIndex > 0}
              />
            }
            showTitle={
              step.kind === 'intro' ||
              step.kind === 'demo' ||
              step.kind === 'detect' ||
              step.kind === 'ready'
            }
            stepCount={steps.length}
            stepIndex={stepIndex}
          />
        </div>

        <button
          aria-label={isBgmPlaying ? '배경 음악 끄기' : '배경 음악 켜기'}
          className="absolute bottom-[25px] left-[25px] z-40 size-[62px] border-none bg-none p-0 drop-shadow-[0_4px_8px_rgba(0,0,0,0.3)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white"
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
      </div>
    </div>
  )
}

/** 소개·시작 단계에서 빵긋이 서는 크기(px) */
const INTRO_HERO_SIZE = 750
/** 첫 번째 안내 단계에서 빵긋이를 왼쪽으로 옮기는 거리(px) */
const INTRO_HERO_X = -220
const INTRO_HERO_Y = -230
const INTRO_SPEECH_X = 340
const INTRO_SPEECH_Y = 110
/** 마지막 시작 단계에서 공룡 에셋 크기(px) */
const READY_HERO_SIZE = 400
/** 마지막 시작 단계 영상 내부의 투명 여백까지 감안해 실제 공룡을 키우는 배율 */
const READY_HERO_SCALE = 1.35
/** 마지막 시작 단계 공룡 좌우 이동(px) */
const READY_HERO_X = -20
/** 마지막 시작 단계 공룡 상하 이동(px) */
const READY_HERO_Y = -20
/** 마지막 시작 단계 말풍선 너비(px) */
const READY_SPEECH_WIDTH = 350
/** 마지막 시작 단계 말풍선 글자 배율 */
const READY_SPEECH_TEXT_SCALE = 1.2
/** 마지막 시작 단계 말풍선 좌우 이동(px) */
const READY_SPEECH_X = -10
/** 마지막 시작 단계 말풍선 상하 이동(px) */
const READY_SPEECH_Y = -20
/** 두 번째 시범 단계에서 패널 안 공룡 에셋 크기(px) */
const DEMO_STAGE_SIZE = 500
/** demo 영상 내부의 투명 여백까지 감안해 실제 공룡을 키우는 배율 */
const DEMO_STAGE_SCALE = 1.55
/** 두 번째 시범 단계 공룡 에셋 좌우 이동(px) */
const DEMO_STAGE_X = 15
/** 두 번째 시범 단계 공룡 에셋 상하 이동(px) */
const DEMO_STAGE_Y = -15
/** 두 번째·세 번째 단계 점선 안내 패널 너비(px 또는 %) */
const COMPACT_GUIDE_PANEL_WIDTH = '100%'
/** 두 번째·세 번째 단계 점선 안내 패널 높이(px) */
const COMPACT_GUIDE_PANEL_HEIGHT = 360
/** 두 번째·세 번째 단계 점선 안내 패널 좌우 이동(px) */
const COMPACT_GUIDE_PANEL_X = 0
/** 두 번째·세 번째 단계 점선 안내 패널 상하 이동(px) */
const COMPACT_GUIDE_PANEL_Y = -10
/** 연습 단계 안내 띠 높이(px) */
const PRACTICE_BANNER_HEIGHT = 52
/**
 * 연습 판 높이(px).
 *
 * 본문 438 - 안내 띠 52 - 간격 6. 안쪽 캔버스가 기댈 확정 높이가 필요해
 * px로 못박는다.
 */
const PRACTICE_STAGE_HEIGHT = 380
/** 인식 단계에서 말풍선 아래에 곁들이는 자세 그림 크기(px) */
const POSE_HINT_SIZE = 172

/**
 * 단계별 본문.
 *
 * 소개·시작은 빵긋이 주인공이라 크게 세우고, 시범·인식은 보여 줄 동작이
 * 주인공이라 말풍선을 왼쪽으로 물리고 그림에 자리를 몰아준다. 실습은 게임
 * 화면(16:9)이 들어와야 해서 말풍선을 위로 올린다.
 */
type CameraProps = {
  detected: boolean
  modelReady: boolean
  progress: number
  status: CameraStatus
  streamRef: RefObject<MediaStream | null>
  videoRef: RefObject<HTMLVideoElement | null>
}

function StepBody({
  camera,
  dobblePractice,
  gameId,
  kind,
  practice,
  speech,
}: {
  camera: CameraProps
  dobblePractice: DobblePracticeState
  gameId: TutorialGameId
  kind: TutorialStepKind
  practice: FruitPracticeState
  speech: string
}) {
  if (kind === 'intro' || kind === 'ready') {
    if (kind === 'intro') {
      return (
        <div className="relative h-full w-full">
          <div
            className="absolute"
            style={{ left: INTRO_HERO_X, top: INTRO_HERO_Y }}
          >
            <GreetingVideo size={INTRO_HERO_SIZE} />
          </div>
          <div
            className="absolute"
            style={{ left: INTRO_SPEECH_X, top: INTRO_SPEECH_Y }}
          >
            <SpeechBubble speech={speech} />
          </div>
        </div>
      )
    }

    return (
      <div className="flex items-center justify-center gap-4">
        <div
          style={{
            transform: `translate(${READY_HERO_X}px, ${READY_HERO_Y}px)`,
          }}
        >
          <CelebrationVideo
            scale={READY_HERO_SCALE}
            size={READY_HERO_SIZE}
          />
        </div>
        <div
          style={{
            transform: `translate(${READY_SPEECH_X}px, ${READY_SPEECH_Y}px)`,
            width: READY_SPEECH_WIDTH,
          }}
        >
          <SpeechBubble
            className="w-full"
            speech={speech}
            textScale={READY_SPEECH_TEXT_SCALE}
          />
        </div>
      </div>
    )
  }

  if (kind === 'practice') {
    const coaching =
      gameId === 'fruit'
        ? fruitCoaching(practice)
        : dobbleCoaching(dobblePractice.feedback)

    return (
      <div className="flex h-full w-full flex-col items-center gap-1.5">
        <CoachingBanner text={coaching.text} tone={coaching.tone} />
        <div
          className="flex w-full items-center justify-center"
          style={{ height: PRACTICE_STAGE_HEIGHT }}
        >
          {camera.status !== 'granted' ? (
            <TutorialPanel>
              <CameraFallbackNotice status={camera.status} />
            </TutorialPanel>
          ) : gameId === 'fruit' ? (
            <TutorialPanel
              className="relative bg-cover bg-center"
              style={{ backgroundImage: `url(${fruitPracticeBackgroundUrl})` }}
            >
              <FruitPracticeStage camera={camera} practice={practice} />
            </TutorialPanel>
          ) : (
            <DobblePracticeStage
              practice={dobblePractice}
              streamRef={camera.streamRef}
              videoRef={camera.videoRef}
            />
          )}
        </div>
      </div>
    )
  }

  // demo · detect — 왼쪽에 말풍선, 오른쪽에 보여 줄 것.
  // detect에서는 왼쪽 아래에 취해야 할 자세를 작게 곁들인다.
  const isDemoStep = kind === 'demo'
  const usesCompactGuidePanel = kind === 'demo' || kind === 'detect'

  return (
    <div
      className={`flex h-full w-full items-center ${
        isDemoStep ? 'gap-6' : 'gap-4'
      }`}
    >
      <div
        className={`flex shrink-0 flex-col items-center gap-2 ${
          isDemoStep ? 'w-[310px]' : 'w-[240px]'
        }`}
      >
        <SpeechBubble
          side="right"
          size={isDemoStep ? 'large' : 'small'}
          speech={speech}
        />
        {kind === 'detect' ? (
          gameId === 'fruit' ? (
            <FruitPose pose="raise-arm" size={POSE_HINT_SIZE} />
          ) : (
            <DobbleHands pose="open" size={POSE_HINT_SIZE} />
          )
        ) : null}
      </div>
      <div className="flex h-full min-w-0 flex-1 items-center justify-center">
        <div
          className="flex items-center justify-center"
          style={{
            width: usesCompactGuidePanel ? COMPACT_GUIDE_PANEL_WIDTH : '100%',
            height: usesCompactGuidePanel
              ? COMPACT_GUIDE_PANEL_HEIGHT
              : '100%',
            transform: usesCompactGuidePanel
              ? `translate(${COMPACT_GUIDE_PANEL_X}px, ${COMPACT_GUIDE_PANEL_Y}px)`
              : undefined,
          }}
        >
          <TutorialPanel overflowVisible={isDemoStep}>
            {kind === 'demo' ? (
              gameId === 'fruit' ? (
                <div
                  style={{
                    transform: `translate(${DEMO_STAGE_X}px, ${DEMO_STAGE_Y}px)`,
                  }}
                >
                  <FruitSliceVideo
                    scale={DEMO_STAGE_SCALE}
                    size={DEMO_STAGE_SIZE}
                  />
                </div>
              ) : (
                <div
                  style={{
                    transform: `translate(${DEMO_STAGE_X}px, ${DEMO_STAGE_Y}px)`,
                  }}
                >
                  <DobbleGrabVideo
                    scale={DEMO_STAGE_SCALE}
                    size={DEMO_STAGE_SIZE}
                  />
                </div>
              )
            ) : (
              <TutorialCamera
                detected={camera.detected}
                gapClassName="gap-5"
                hintWhenWaiting={
                  gameId === 'fruit'
                    ? '한 팔을 번쩍 들어 주세요'
                    : '두 손을 다 보여 주세요'
                }
                modelReady={camera.modelReady}
                progress={camera.progress}
                status={camera.status}
                streamRef={camera.streamRef}
                videoRef={camera.videoRef}
              />
            )}
          </TutorialPanel>
        </div>
      </div>
    </div>
  )
}

/**
 * 카메라가 아직 없을 때 연습 판 대신 띄우는 안내.
 *
 * 준비 중과 못 쓰는 상태를 구분해야 한다. 준비 중인데 "없어도 괜찮아요"가 뜨면
 * 기다리면 되는 상황인지 포기해야 하는 상황인지 알 수 없다.
 */
function CameraFallbackNotice({ status }: { status: CameraStatus }) {
  const preparing = status === 'idle' || status === 'requesting'

  return (
    <p
      className="text-center text-[24px] break-keep text-[#9a6a34]"
      style={jua}
    >
      {preparing ? (
        '카메라를 준비하고 있어요...'
      ) : (
        <>
          카메라가 없어도 괜찮아요.
          <br />
          건너뛰기를 눌러 주세요!
        </>
      )}
    </p>
  )
}

type CoachingTone = 'guide' | 'praise' | 'retry'

/** 과일 연습 안내 문구. */
function fruitCoaching(practice: FruitPracticeState): {
  text: string
  tone: CoachingTone
} {
  if (practice.praising) {
    const last = practice.slicedCount >= practice.targetCount
    return {
      text: last ? '완벽해요! 다 잘랐어요!' : '잘했어요! 하나 더!',
      tone: 'praise',
    }
  }
  return { text: '올라오는 과일을 슝 잘라 봐!', tone: 'guide' }
}

/** 도블 연습 안내 문구. */
function dobbleCoaching(feedback: DobblePracticeState['feedback']): {
  text: string
  tone: CoachingTone
} {
  switch (feedback) {
    case 'matched':
      return { text: '잘했어요! 똑같은 동물이에요!', tone: 'praise' }
    case 'wrong':
      return {
        text: '앗, 서로 다른 동물이에요. 다시 찾아볼까요?',
        tone: 'retry',
      }
    case 'one-side':
      return { text: '좋아요! 반대쪽에서도 같은 동물을 잡아요', tone: 'guide' }
    default:
      return { text: '양쪽에 똑같이 있는 동물을 잡아 봐!', tone: 'guide' }
  }
}

const COACHING_STYLE: Record<
  CoachingTone,
  { background: string; color: string }
> = {
  guide: { background: 'rgba(255,255,255,0.92)', color: '#6f4a1f' },
  praise: { background: 'rgba(108,188,63,0.95)', color: '#ffffff' },
  retry: { background: 'rgba(255,153,0,0.95)', color: '#ffffff' },
}

/**
 * 연습 중 안내 띠.
 *
 * 잘했는지 다시 해야 하는지를 글자보다 색으로 먼저 알린다. 아직 글을 못 읽는
 * 아이도 초록이면 잘된 것, 주황이면 다시 하는 것으로 읽는다.
 */
function CoachingBanner({ text, tone }: { text: string; tone: CoachingTone }) {
  const style = COACHING_STYLE[tone]

  return (
    <div
      className="flex items-center justify-center rounded-full px-7 break-keep shadow-[0_4px_10px_rgba(120,80,30,0.22)] transition-colors"
      // 문구가 바뀔 때마다 다시 마운트해 살짝 튀어오르게 한다.
      key={text}
      style={{
        ...jua,
        ...style,
        height: PRACTICE_BANNER_HEIGHT,
        fontSize: 26,
        animation: 'dobble-grab-pop 300ms ease-out',
      }}
    >
      {text}
    </div>
  )
}

/**
 * 과일 자르기 연습 판.
 *
 * 본 게임과 같은 판정으로 과일 몇 개만 올려 보낸다. 남은 개수는 점으로 보여 줘
 * 글자를 못 읽는 아이도 진행도를 안다.
 */
function FruitPracticeStage({
  camera,
  practice,
}: {
  camera: CameraProps
  practice: FruitPracticeState
}) {
  if (camera.status !== 'granted') {
    return (
      <TutorialPanel>
        <CameraFallbackNotice status={camera.status} />
      </TutorialPanel>
    )
  }

  return (
    <div className="relative flex h-full w-full items-center justify-center overflow-hidden rounded-[18px]">
      <canvas
        // 배경 이미지는 바깥 점선 패널에 깔고, 캔버스는 과일과 칼날만 투명하게 얹는다.
        className="absolute inset-0 z-10 block h-full w-full"
        height={FRUIT_GAME_CONFIG.canvasHeight}
        ref={practice.canvasRef}
        width={FRUIT_GAME_CONFIG.canvasWidth}
      />

      {/* 남은 개수는 판 위에 겹쳐 둔다. 아래에 두면 그만큼 판이 작아진다. */}
      <div className="pointer-events-none absolute top-2 left-1/2 z-30 flex -translate-x-1/2 items-center gap-2 rounded-full bg-[rgba(255,250,236,0.85)] px-3 py-1.5">
        {Array.from({ length: practice.targetCount }, (_, index) => (
          <span
            className="block size-[16px] rounded-full transition-colors"
            key={index}
            style={{
              background: index < practice.slicedCount ? '#6cbc3f' : '#d8c39d',
            }}
          />
        ))}
      </div>

      {/*
          자기 모습 확인용이자, 동작 인식이 프레임을 읽을 video를 붙잡아 두는
          자리다. 이 엘리먼트가 없으면 MediaPipe가 볼 영상이 사라진다.
        */}
      <PracticeCameraPreview
        streamRef={camera.streamRef}
        videoRef={camera.videoRef}
      />
    </div>
  )
}

/** 연습 판 구석에 붙는 작은 자기 모습. 플레이 화면과 같은 자리·모양이다. */
function PracticeCameraPreview({
  streamRef,
  videoRef,
}: {
  streamRef: RefObject<MediaStream | null>
  videoRef: RefObject<HTMLVideoElement | null>
}) {
  useAttachStream(videoRef, streamRef)

  return (
    <video
      autoPlay
      className="pointer-events-none absolute right-1 bottom-2 z-30 h-[74px] w-[98px] scale-x-[-1] rounded-[10px] border-2 border-white/70 object-cover shadow-[0_3px_8px_rgba(0,0,0,0.3)]"
      muted
      playsInline
      ref={videoRef}
    />
  )
}

function StepFooter({
  cameraUsable,
  kind,
  onNext,
  onPrev,
  onQuit,
  onRelearn,
  onStart,
  showPrev,
}: {
  /** 카메라를 쓸 수 있는지. 못 쓰면 인식 단계를 건너뛸 길을 열어 준다. */
  cameraUsable: boolean
  kind: TutorialStepKind
  onNext: () => void
  onPrev: () => void
  onQuit: () => void
  onRelearn: () => void
  onStart: () => void
  showPrev: boolean
}) {
  if (kind === 'ready') {
    return (
      <div className="flex w-full items-center justify-center gap-5">
        <GuideButton
          imageUrl={guideStartButtonUrl}
          label="시작하기"
          labelClassName="text-white [text-shadow:0_2px_0_#3f7d1e,0_5px_8px_rgba(20,50,15,0.3)]"
          onClick={onStart}
          width={196}
        />
        <GuideButton
          imageUrl={guideBackButtonUrl}
          label="다시 배우기"
          labelClassName="text-[#6f4a1f] [text-shadow:0_2px_0_rgba(255,255,255,0.4)]"
          onClick={onRelearn}
          width={196}
        />
        <GuideButton
          imageUrl={guideBackButtonUrl}
          label="나가기"
          labelClassName="text-[#6f4a1f] [text-shadow:0_2px_0_rgba(255,255,255,0.4)]"
          onClick={onQuit}
          width={196}
        />
      </div>
    )
  }

  /*
    인식과 실습은 직접 해내야 넘어간다. "다음"으로 건너뛸 수 있으면 인식도 안 되고
    조작도 모르는 채로 플레이에 들어가서, 아이는 게임이 고장난 줄 안다. 해내면
    알아서 다음으로 넘어가므로 버튼이 필요 없다.

    카메라를 못 쓰는 경우에만 길을 열어 둔다. 그때는 여기서 막아도 방법이 없다.
  */
  const mustFinish = (kind === 'detect' || kind === 'practice') && cameraUsable

  /*
    실습은 버튼을 아예 두지 않는다. 몸으로 하는 단계라 손에 칼이 붙어 있고 손을
    휘두르는 중인데, 밑에 누를 것이 있으면 시선이 거기로 간다. 그만두고 싶으면
    화면 왼쪽 위 뒤로가기·닫기 버튼이 그대로 있으니 갇히지도 않는다.
  */
  const hidePrev = kind === 'practice' && cameraUsable
  const alignWithIntroButtons = kind === 'demo' || kind === 'detect'
  const keepSingleButtonInLeftSlot = kind === 'detect' && mustFinish
  const footerLayoutClass =
    !showPrev || alignWithIntroButtons
      ? 'justify-center gap-12'
      : 'justify-between'

  /*
    버튼이 하나만 남을 때 가운데 오면 "이걸 눌러 진행하라"는 것처럼 보인다.
    좌우로 벌려 두면 지워져도 "이전"이 왼쪽에 그대로 있어 자리가 흔들리지 않고,
    화면 왼쪽 위 뒤로가기 버튼과도 방향이 맞는다.
  */
  return (
    <div className={`flex w-full items-center ${footerLayoutClass}`}>
      {hidePrev ? null : (
        <GuideButton
          imageUrl={guideBackButtonUrl}
          label={showPrev ? '이전' : '나가기'}
          labelClassName="text-[#6f4a1f] [text-shadow:0_2px_0_rgba(255,255,255,0.4)]"
          onClick={showPrev ? onPrev : onQuit}
        />
      )}
      {mustFinish ? null : (
        <GuideButton
          imageUrl={guideStartButtonUrl}
          label={kind === 'detect' || kind === 'practice' ? '건너뛰기' : '다음'}
          labelClassName="text-white [text-shadow:0_2px_0_#3f7d1e,0_5px_8px_rgba(20,50,15,0.3)]"
          onClick={onNext}
        />
      )}
      {keepSingleButtonInLeftSlot ? (
        <div
          aria-hidden="true"
          style={{ width: 236, height: Math.round(236 * (96 / 236)) }}
        />
      ) : null}
    </div>
  )
}

function GuideButton({
  imageUrl,
  label,
  labelClassName,
  onClick,
  width = 236,
}: {
  imageUrl: string
  label: string
  labelClassName: string
  onClick: () => void
  width?: number
}) {
  return (
    <button
      className="relative border-0 bg-transparent p-0 transition hover:scale-[1.04] focus-visible:outline-4 focus-visible:outline-offset-4 focus-visible:outline-white active:scale-[0.98]"
      onClick={onClick}
      style={{ width, height: Math.round(width * (96 / 236)) }}
      type="button"
    >
      <img
        alt=""
        className="absolute inset-0 h-full w-full object-contain drop-shadow-[0_8px_12px_rgba(55,30,10,0.28)]"
        decoding="async"
        draggable={false}
        src={imageUrl}
      />
      <span
        // 버튼 이미지는 위쪽 테두리가 더 두꺼워 글자가 떠 보인다. 아래로 조금 내려
        // 나무판 안쪽 가운데에 맞춘다.
        className={`absolute inset-0 flex items-center justify-center pt-4 text-[30px] leading-none ${labelClassName}`}
        style={jua}
      >
        {label}
      </span>
    </button>
  )
}
