import { RECYCLE_GAME_CONFIG as CFG } from './recycleGameConfig'
import { RECYCLE_BINS, type RecycleCategory } from './recycleItems'

/**
 * 분리수거 통 네 개.
 *
 * 글자·색·대표 그림은 통 그림 안에 이미 그려져 있어 따로 얹지 않는다(RGAME-03).
 *
 * 손이 올라간 통은 밝아지고 살짝 커진다. 그림 맞추기에서 손을 올린 그림에
 * 테두리가 생기는 것과 같은 역할이다 — 손을 펴기 전에 "지금 여기에 넣는다"를
 * 눈으로 확인해야 아이가 엉뚱한 통에 넣고 왜 틀렸는지 모르는 일이 없다.
 *
 * 강조 여부는 놓기 판정과 똑같은 영역(`binAt`)에서 나온다. 밝아진 통에 놓으면
 * 반드시 그 통으로 들어간다.
 */

/** 잡은 쓰레기 없이 손만 올렸을 때 */
const HOVER_RING = '0 0 0 5px rgba(255,255,255,0.85)'
/** 쓰레기를 들고 올렸을 때 — 지금 놓으면 들어간다 */
const HOLDING_RING = '0 0 0 6px rgba(255,206,42,0.95)'

export function BinRow({
  /** 손이 올라가 있는 통 (없으면 null) */
  hoveredBin,
  /** 그 손이 쓰레기를 들고 있는지. 들고 있으면 더 강하게 강조한다. */
  holding = false,
}: {
  hoveredBin: RecycleCategory | null
  holding?: boolean
}) {
  return (
    <>
      {RECYCLE_BINS.map((bin) => {
        const active = hoveredBin === bin.category

        return (
          <div
            className="pointer-events-none absolute z-[4]"
            key={bin.category}
            style={{
              left: bin.centerX - CFG.bin.width / 2,
              top: CFG.bin.y,
              width: CFG.bin.width,
              height: CFG.bin.height,
              transform: active ? 'translateY(-10px) scale(1.06)' : 'none',
              transition: 'transform 140ms ease-out',
            }}
          >
            {/*
              강조는 통 그림 뒤에 깔린 둥근 판으로 준다. 통 모양이 제각각이라
              그림 자체에 테두리를 두르면 네모난 자국이 보인다.
            */}
            <span
              className="absolute inset-x-[6%] bottom-[4%] block h-[62%] rounded-[28px]"
              style={{
                background: active
                  ? holding
                    ? 'rgba(255,206,42,0.34)'
                    : 'rgba(255,255,255,0.28)'
                  : 'transparent',
                boxShadow: active
                  ? holding
                    ? HOLDING_RING
                    : HOVER_RING
                  : 'none',
                transition:
                  'background 140ms ease-out, box-shadow 140ms ease-out',
              }}
            />
            <img
              alt={`${bin.label} 통`}
              className="relative block h-full w-full object-contain"
              decoding="async"
              draggable={false}
              src={bin.imageUrl}
              style={{
                filter: active
                  ? 'drop-shadow(0 0 16px rgba(255,236,150,0.95)) drop-shadow(0 12px 20px rgba(20,50,16,0.4)) brightness(1.08)'
                  : 'drop-shadow(0 10px 18px rgba(20,50,16,0.35))',
                transition: 'filter 140ms ease-out',
              }}
            />
          </div>
        )
      })}
    </>
  )
}
