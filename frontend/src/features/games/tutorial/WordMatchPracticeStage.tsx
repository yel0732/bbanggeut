import { type RefObject } from 'react'

import answerSlotUrl from '@/assets/images/wordmatch-game/answer-slot.webp'
import backgroundUrl from '@/assets/images/wordmatch-game/background.webp'
import letterCardUrl from '@/assets/images/wordmatch-game/letter-card.webp'
import { HandCursor } from '@/features/games/recycle/HandCursor'
import { WORD_MATCH_CONFIG as CFG } from '@/features/games/wordmatch/wordMatchConfig'
import {
  hitRadius,
  pictureCenter,
  slotCenter,
  slotSize,
  tileCenter,
  tileSize,
} from '@/features/games/wordmatch/wordMatchGrab'

import { useAttachStream } from './useAttachStream'
import type { WordMatchPracticeState } from './useWordMatchPractice'

/**
 * 낱말 맞추기 연습 판.
 *
 * 좌표계는 본 게임과 같은 1280x720으로 그리고, 통째로 축소해 튜토리얼 보드 안에
 * 넣는다. 판정에 쓰는 좌표와 눈에 보이는 좌표가 어긋나지 않게 하려면 배치를 다시
 * 계산하는 것보다 화면만 줄이는 쪽이 안전하다 — 배경·정답 칸·글자 카드가 본
 * 게임과 같은 자리에 있어, 연습에서 익힌 위치가 본 게임에서 그대로 통한다.
 */

/** 연습 판 자리가 663x380px이라 1280x720을 여기에 맞춰 넣으면 이 값이 나온다. */
const SCALE = 0.52

const jua = { fontFamily: "'Jua', sans-serif" } as const

export function WordMatchPracticeStage({
  practice,
  streamRef,
  videoRef,
}: {
  practice: WordMatchPracticeState
  streamRef: RefObject<MediaStream | null>
  videoRef: RefObject<HTMLVideoElement | null>
}) {
  useAttachStream(videoRef, streamRef)

  const { problem, tiles, feedback, hover, slotHover } = practice
  const wordLength = problem ? problem.answer.length : 0
  const picture = pictureCenter()
  const slot = slotSize()
  const radius = hitRadius()

  return (
    <div
      className="relative overflow-hidden rounded-[18px] border-[3px] border-[#c39a5d]"
      style={{
        width: CFG.canvasWidth * SCALE,
        height: CFG.canvasHeight * SCALE,
      }}
    >
      <div
        className="absolute top-0 left-0"
        style={{
          width: CFG.canvasWidth,
          height: CFG.canvasHeight,
          transform: `scale(${SCALE})`,
          transformOrigin: '0 0',
        }}
      >
        <img
          alt=""
          className="absolute inset-0 h-full w-full object-cover"
          decoding="async"
          draggable={false}
          src={backgroundUrl}
        />

        {problem ? (
          <>
            {/* 문제 그림 — 맞히면 본 게임과 같이 크게 튀어오른다. */}
            <img
              alt=""
              className="pointer-events-none absolute -translate-x-1/2 -translate-y-1/2 object-contain"
              decoding="sync"
              draggable={false}
              src={problem.imageUrl}
              style={{
                left: picture.x,
                top: picture.y,
                height: CFG.picture.height,
                animation:
                  feedback === 'done'
                    ? 'dobble-grab-pop 620ms ease-out'
                    : undefined,
                filter:
                  feedback === 'done'
                    ? 'drop-shadow(0 0 20px rgba(255,236,150,0.95))'
                    : 'drop-shadow(0 8px 12px rgba(90,60,20,0.28))',
              }}
            />

            {/* 정답 칸 */}
            {Array.from({ length: wordLength }, (_, index) => {
              const center = slotCenter(index, wordLength)
              const hovered = slotHover.a === index || slotHover.b === index

              return (
                <div
                  className="pointer-events-none absolute -translate-x-1/2 -translate-y-1/2"
                  key={index}
                  style={{
                    left: center.x,
                    top: center.y,
                    width: slot.width,
                    height: slot.height,
                  }}
                >
                  <img
                    alt=""
                    className="block h-full w-full object-contain"
                    decoding="async"
                    draggable={false}
                    src={answerSlotUrl}
                    style={{
                      filter: hovered
                        ? 'drop-shadow(0 0 12px rgba(255,255,255,0.95))'
                        : 'drop-shadow(0 6px 10px rgba(90,60,20,0.3))',
                      transition: 'filter 140ms ease-out',
                    }}
                  />
                </div>
              )
            })}

            {/* 글자 — 카드 자리·정답 칸·손 어디에 있든 이 목록으로 그린다. */}
            {tiles.map((tile) => {
              const center = tileCenter(tile, wordLength)
              const hovered = hover.a === tile.uid || hover.b === tile.uid
              const held = tile.heldBy !== null
              const size = tileSize(tile)

              return (
                <div
                  className="pointer-events-none absolute z-[8] -translate-x-1/2 -translate-y-1/2"
                  key={tile.uid}
                  style={{
                    left: center.x,
                    top: center.y,
                    // 본 게임과 같은 이유로 크기를 못박는다. 비워 두면 화면
                    // 가장자리에서 상자가 눌려 글자가 쪼그라든다.
                    width: size,
                    height: size,
                    transition: held
                      ? undefined
                      : 'left 160ms ease-out, top 160ms ease-out, width 160ms ease-out, height 160ms ease-out',
                  }}
                >
                  {hovered && !held ? (
                    <span
                      className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 rounded-full"
                      style={{
                        width: radius * 2,
                        height: radius * 2,
                        border: '5px dashed rgba(255,255,255,0.85)',
                        background: 'rgba(255,255,255,0.12)',
                      }}
                    />
                  ) : null}

                  <img
                    alt=""
                    className="absolute inset-0 h-full w-full object-contain"
                    decoding="async"
                    draggable={false}
                    src={letterCardUrl}
                    style={{
                      filter: held
                        ? 'drop-shadow(0 0 16px rgba(255,206,42,0.95))'
                        : 'drop-shadow(0 5px 9px rgba(90,60,20,0.3))',
                    }}
                  />
                  <span
                    className="absolute inset-0 flex items-center justify-center leading-none"
                    style={{
                      ...jua,
                      fontSize: size * 0.48,
                      color: '#5a3c14',
                      transform: held ? 'scale(1.1)' : 'scale(1)',
                      transition: 'transform 140ms ease-out',
                    }}
                  >
                    {tile.letter}
                  </span>
                </div>
              )
            })}
          </>
        ) : null}

        {/*
          손 포인터 — 좌표·각도·주먹은 훅이 이 노드에 직접 쓴다.

          본 게임과 같은 커서를 쓴다. 연습에서 본 손 모양이 본 게임에서 다르면
          아이는 같은 조작인지 알 수 없다.
        */}
        {(['a', 'b'] as const).map((slotKey) => (
          <HandCursor
            key={slotKey}
            nodeRef={practice.pointerRefs[slotKey]}
            slot={slotKey}
          />
        ))}
      </div>

      {/*
        자기 모습 확인용이자, 동작 인식이 프레임을 읽을 video를 붙잡아 두는
        자리다. 이 엘리먼트가 없으면 MediaPipe가 볼 영상이 사라진다.
      */}
      <video
        autoPlay
        className="pointer-events-none absolute right-1 bottom-1 h-[74px] w-[98px] scale-x-[-1] rounded-[10px] border-2 border-white/70 object-cover shadow-[0_3px_8px_rgba(0,0,0,0.3)]"
        muted
        playsInline
        ref={videoRef}
      />
    </div>
  )
}
