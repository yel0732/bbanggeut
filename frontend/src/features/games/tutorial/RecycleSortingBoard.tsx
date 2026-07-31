import {
  RECYCLE_BINS,
  RECYCLE_ITEMS,
} from '@/features/games/recycle/recycleItems'

/**
 * 분리수거 분류 안내.
 *
 * 통 네 개를 늘어놓고 그 아래에 그 통으로 가는 쓰레기를 붙여 보여 준다.
 * 실습에 들어가기 전에 "무엇이 어디로 가는지"를 한 번 훑게 하는 화면이라
 * 글보다 그림이 먼저 눈에 들어오게 배치한다.
 *
 * 통마다 다섯 개씩 스무 개를 모두 늘어놓으면 한 칸이 너무 좁아진다. 통 그림에
 * 그려진 대표 그림과 가까운 것부터 세 개만 보여 준다 — 나머지는 게임에서
 * 만나며 배운다.
 *
 * 네 칸은 크기를 px로 못박는다. 내용에 맞춰 늘리게 두면 이름이 두 줄인 칸만
 * 커져서 네 칸이 들쭉날쭉해진다. 안쪽은 `justify-between`으로 나눠, 칸 높이가
 * 같아도 통·쓰레기·이름이 각자 제자리에 놓인다.
 */

/** 한 통에 보여 줄 쓰레기 수 */
const ITEMS_PER_BIN = 3

/** 한 칸 크기(px). 네 칸이 모두 이 크기다. */
const CELL_WIDTH = 152
const CELL_HEIGHT = 292

/** 칸 안에서 각 줄이 차지하는 높이(px) */
const BIN_HEIGHT = 132
const ITEM_ROW_HEIGHT = 56
const NAME_HEIGHT = 60

const jua = { fontFamily: "'Jua', sans-serif" } as const

export function RecycleSortingBoard() {
  return (
    <div className="flex w-full items-start justify-center gap-3">
      {RECYCLE_BINS.map((bin) => {
        const items = RECYCLE_ITEMS.filter(
          (item) => item.category === bin.category,
        ).slice(0, ITEMS_PER_BIN)

        return (
          <div
            className="flex flex-col items-center justify-between rounded-[20px] border-[3px] border-dashed border-[#c39a5d] bg-[#fffaec]/75 px-2 py-2.5"
            key={bin.category}
            style={{ width: CELL_WIDTH, height: CELL_HEIGHT }}
          >
            <div
              className="flex w-full items-center justify-center"
              style={{ height: BIN_HEIGHT }}
            >
              <img
                alt={`${bin.label} 통`}
                className="block max-h-full w-auto object-contain drop-shadow-[0_6px_10px_rgba(120,80,30,0.22)]"
                decoding="async"
                draggable={false}
                src={bin.imageUrl}
              />
            </div>

            <div
              className="flex w-full items-center justify-center gap-1"
              style={{ height: ITEM_ROW_HEIGHT }}
            >
              {items.map((item) => (
                <img
                  alt={item.name}
                  className="block size-[46px] object-contain drop-shadow-[0_3px_5px_rgba(120,80,30,0.25)]"
                  decoding="async"
                  draggable={false}
                  key={item.code}
                  src={item.imageUrl}
                />
              ))}
            </div>

            <p
              className="flex w-full items-center justify-center text-center text-[17px] leading-tight break-keep text-[#6f4a1f]"
              style={{ ...jua, height: NAME_HEIGHT }}
            >
              {items.map((item) => item.name).join(' · ')}
            </p>
          </div>
        )
      })}
    </div>
  )
}
