import { RECYCLE_GAME_CONFIG as CFG } from './recycleGameConfig'
import { RECYCLE_BINS, type RecycleCategory } from './recycleItems'

/**
 * 분리수거 통 네 개.
 *
 * 글자·색·대표 그림은 통 그림 안에 이미 그려져 있어 따로 얹지 않는다(RGAME-03).
 *
 * 손이 올라간 통은 통 모양 그대로 빛난다. 강조를 네모난 판으로 주면 통 뒤에
 * 사각형이 떠올라 그림과 따로 놀아서, `drop-shadow`를 여러 겹 겹쳐 알파 윤곽을
 * 따라 번지게 한다.
 *
 * 강조 여부는 놓기 판정과 똑같은 영역(`binAt`)에서 나온다. 빛나는 통에 손을
 * 펴면 반드시 그 통으로 들어간다.
 *
 * 통 그림은 높이만 맞추고 폭은 비율대로 둔다. 폭까지 고정하면 가로로 넓은 종이
 * 통만 눌려 낮아진다.
 */

/** 손만 올렸을 때 — 흰 빛으로 "여기 있다"만 알린다. */
const HOVER_GLOW = [
  'drop-shadow(0 0 6px rgba(255,255,255,0.95))',
  'drop-shadow(0 0 18px rgba(255,255,255,0.7))',
  'drop-shadow(0 12px 20px rgba(20,50,16,0.4))',
].join(' ')

/** 쓰레기를 들고 올렸을 때 — 금빛으로 "지금 놓으면 들어간다"를 알린다. */
const HOLDING_GLOW = [
  'drop-shadow(0 0 8px rgba(255,255,255,0.95))',
  'drop-shadow(0 0 22px rgba(255,206,42,0.95))',
  'drop-shadow(0 0 38px rgba(255,170,20,0.7))',
  'drop-shadow(0 12px 20px rgba(20,50,16,0.4))',
].join(' ')

/** 알맞게 넣은 직후 — 초록빛으로 크게 터뜨린다. */
const CELEBRATE_GLOW = [
  'drop-shadow(0 0 10px rgba(255,255,255,1))',
  'drop-shadow(0 0 30px rgba(140,240,90,1))',
  'drop-shadow(0 0 56px rgba(90,220,60,0.85))',
  'drop-shadow(0 12px 20px rgba(20,50,16,0.4))',
].join(' ')

const IDLE_GLOW = 'drop-shadow(0 10px 18px rgba(20,50,16,0.35))'

export function BinRow({
  /** 알맞게 넣어서 축하 중인 통 */
  celebratingBin = null,
  /** 손이 올라가 있는 통 (없으면 null) */
  hoveredBin,
  /** 그 손이 쓰레기를 들고 있는지. 들고 있으면 더 강하게 강조한다. */
  holding = false,
}: {
  celebratingBin?: RecycleCategory | null
  hoveredBin: RecycleCategory | null
  holding?: boolean
}) {
  return (
    <>
      {RECYCLE_BINS.map((bin) => {
        const celebrating = celebratingBin === bin.category
        const active = hoveredBin === bin.category

        return (
          <div
            className="pointer-events-none absolute z-[4] flex justify-center"
            key={bin.category}
            style={{
              // 폭은 그림이 비율대로 정하므로, 자리는 가운데를 기준으로 잡는다.
              left: bin.centerX - CFG.bin.width / 2,
              top: CFG.bin.y,
              width: CFG.bin.width,
              height: CFG.bin.height,
            }}
          >
            <img
              alt={`${bin.label} 통`}
              className="block h-full w-auto max-w-none object-contain"
              decoding="async"
              draggable={false}
              src={bin.imageUrl}
              style={{
                filter: celebrating
                  ? CELEBRATE_GLOW
                  : active
                    ? holding
                      ? HOLDING_GLOW
                      : HOVER_GLOW
                    : IDLE_GLOW,
                transform: celebrating
                  ? 'translateY(-16px) scale(1.12)'
                  : active
                    ? 'translateY(-10px) scale(1.06)'
                    : 'none',
                transition:
                  'filter 140ms ease-out, transform 160ms cubic-bezier(0.34, 1.56, 0.64, 1)',
              }}
            />
          </div>
        )
      })}
    </>
  )
}
