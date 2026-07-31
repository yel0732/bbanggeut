import type { CSSProperties } from 'react'

import wordCardFrameUrl from '@/assets/images/wordmatch-game/word-card-frame.png'
import wordMatchLayoutBgUrl from '@/assets/images/wordmatch-game/wordmatch-layout-bg-v2.jpg'
import { HandShape } from '@/features/games/recycle/HandCursor'

import type { WordMatchInteraction } from './useWordMatchInteraction'
import {
  cardCenter,
  slotCenter,
  WORD_MATCH_CONFIG,
  type WordMatchProblem,
} from './wordMatchConfig'

export function WordMatchBoard({
  interaction,
  problem,
}: {
  interaction: WordMatchInteraction
  problem: WordMatchProblem
}) {
  return (
    <div
      className="relative overflow-hidden bg-cover bg-center shadow-[0_18px_45px_rgba(35,70,90,0.3)]"
      style={{
        width: WORD_MATCH_CONFIG.width,
        height: WORD_MATCH_CONFIG.height,
        backgroundImage: `url(${wordMatchLayoutBgUrl})`,
        backgroundPosition: 'center',
        backgroundSize: 'cover',
        fontFamily: "'Jua', sans-serif",
      }}
    >
      <div className="absolute top-[72px] left-[310px] flex h-[175px] w-[660px] items-center justify-center">
        <img
          alt={problem.label}
          className="h-[155px] w-[155px] object-contain drop-shadow-[0_8px_12px_rgba(75,45,15,0.25)]"
          draggable={false}
          src={problem.imageUrl}
        />
      </div>

      {problem.answer.split('').map((_, index) => {
        const point = slotCenter(index, problem.answer.length)
        return (
          <div
            className="absolute z-10"
            key={index}
            style={{
              left: point.x - WORD_MATCH_CONFIG.cardSize / 2,
              top: point.y - WORD_MATCH_CONFIG.cardSize / 2,
              width: WORD_MATCH_CONFIG.cardSize,
              height: WORD_MATCH_CONFIG.cardSize,
            }}
          >
            <img
              alt=""
              className="h-full w-full object-contain"
              draggable={false}
              src={wordCardFrameUrl}
            />
          </div>
        )
      })}

      {interaction.cards.map((card) => {
        const placedIndex = interaction.placements.indexOf(card.id)
        const point =
          interaction.grabbedId === card.id
            ? interaction.pointer
            : placedIndex >= 0
              ? slotCenter(placedIndex, problem.answer.length)
              : cardCenter(card.trayIndex)
        const active =
          interaction.hoveredId === card.id || interaction.grabbedId === card.id
        return (
          <div
            className="absolute z-20 flex items-center justify-center text-[58px] text-[#4d391f] transition-[left,top,filter] duration-150"
            key={card.id}
            style={{
              left: point.x - WORD_MATCH_CONFIG.cardSize / 2,
              top: point.y - WORD_MATCH_CONFIG.cardSize / 2,
              width: WORD_MATCH_CONFIG.cardSize,
              height: WORD_MATCH_CONFIG.cardSize,
              filter: active
                ? 'drop-shadow(0 0 13px rgba(255,226,61,.95))'
                : 'drop-shadow(0 5px 6px rgba(74,50,20,.3))',
              transitionDuration:
                interaction.grabbedId === card.id ? '0ms' : undefined,
            }}
          >
            {placedIndex < 0 || interaction.grabbedId === card.id ? (
              <img
                alt=""
                className="absolute inset-0 h-full w-full object-contain"
                draggable={false}
                src={wordCardFrameUrl}
              />
            ) : null}
            <span className="relative z-10 pt-1">{card.letter}</span>
          </div>
        )
      })}

      {interaction.pointer.visible ? (
        <div
          className="pointer-events-none absolute z-40 -translate-x-1/2 -translate-y-1/2"
          style={{ left: interaction.pointer.x, top: interaction.pointer.y }}
        >
          <svg
            className="-rotate-90"
            height="82"
            viewBox="0 0 82 82"
            width="82"
          >
            <circle
              cx="41"
              cy="41"
              fill="rgba(255,255,255,.2)"
              r="35"
              stroke="white"
              strokeWidth="5"
            />
            <circle
              cx="41"
              cy="41"
              fill="none"
              r="35"
              stroke="#ffb51b"
              strokeDasharray={220}
              strokeDashoffset={220 * (1 - interaction.aimProgress)}
              strokeLinecap="round"
              strokeWidth="8"
            />
          </svg>
          <div
            className="absolute inset-[8px] grid place-items-center"
            style={
              {
                '--fist': interaction.pointer.fist ? 1 : 0,
                filter: 'drop-shadow(0 3px 5px rgba(60,36,10,.42))',
              } as CSSProperties
            }
          >
            <HandShape color="#ffab26" />
          </div>
        </div>
      ) : null}
    </div>
  )
}
