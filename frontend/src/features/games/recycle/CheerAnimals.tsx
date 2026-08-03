import deerUrl from '@/assets/images/dobble-game/deer.webp'
import rabbitUrl from '@/assets/images/dobble-game/rabbit.webp'
import squirrelUrl from '@/assets/images/dobble-game/squirrel.webp'

/**
 * 물길 건너편에서 응원하는 동물.
 *
 * 아이가 혼자 놀고 있지 않다는 느낌을 주는 장식이다. 판정과는 아무 상관이 없어
 * 클릭도 히트 판정도 받지 않는다.
 *
 * 그림은 그림 맞추기 게임의 동물 에셋을 그대로 쓴다. 같은 그림책에서 나온 것처럼
 * 보이고 새 에셋을 받을 것도 없다.
 *
 * ⭐ 위치·크기는 아래 CHEER_ANIMALS 배열에서 조절한다.
 */

/** 평소 응원에서 폴짝 뛰는 한 주기(ms) */
const HOP_PERIOD_MS = 2100
/** 정답을 맞혔을 때 크게 뛰는 시간(ms) */
const JUMP_MS = 760

type CheerAnimal = {
  /** 애니메이션 구분용 키 */
  id: string
  imageUrl: string
  /** 가운데 x (캔버스 px) */
  x: number
  /**
   * 발이 닿는 y (캔버스 px).
   *
   * 위가 아니라 아래를 기준으로 잡는다. 크기를 바꿔도 발이 그 자리에 그대로
   * 붙어 있어야 땅에 선 것처럼 보인다.
   */
  groundY: number
  /** 키(px). 폭은 비율대로 따라온다. */
  height: number
  /** 좌우 반전. 안쪽(가운데)을 보게 만들 때 쓴다. */
  flip?: boolean
  /** 뛰는 시작 시점을 어긋나게 하는 지연(ms). 세 마리가 같이 뛰면 어색하다. */
  hopDelayMs: number
}

/**
 * ⭐ 응원 동물 배치.
 *
 * 물이 시작하는 y가 274라, 그 위쪽 잔디·돌 둑에 세운다. 발이 닿는 자리를 물보다
 * 위에 둬야 강가에 선 것처럼 보인다. 위로는 상단 점수 칸(35~85)과 안내 문구
 * (112)를 가리지 않아야 한다.
 *
 * 세 마리를 화면 가운데에 나란히 모아 둔다. 멀리 흩어 놓으면 응원하는 무리가
 * 아니라 배경 장식처럼 보인다.
 *
 * 동물을 더 넣거나 빼려면 이 배열만 고치면 된다. 쓸 수 있는 그림은
 * `assets/images/dobble-game/`에 20종 있다(rabbit, squirrel, deer, frog, bear,
 * fox, koala, panda, penguin, pig, sheep, tiger, otter, monkey, hedgehog,
 * dog, chick, dinosaur, elephant, pangolin).
 */
const CHEER_ANIMALS: CheerAnimal[] = [
  {
    id: 'rabbit',
    imageUrl: rabbitUrl,
    x: 556,
    groundY: 224,
    height: 84,
    hopDelayMs: 0,
  },
  {
    id: 'squirrel',
    imageUrl: squirrelUrl,
    x: 640,
    groundY: 218,
    height: 78,
    hopDelayMs: -700,
  },
  {
    id: 'deer',
    imageUrl: deerUrl,
    x: 726,
    groundY: 224,
    height: 92,
    flip: true,
    hopDelayMs: -1400,
  },
]

export function CheerAnimals({
  /**
   * 정답을 맞힌 순간이 바뀔 때마다 값이 달라지는 키.
   *
   * 이 값이 바뀌면 애니메이션 요소를 새로 마운트해 크게 뛰는 동작을 처음부터
   * 재생한다. 같은 값이 이어지면 평소 응원을 계속한다.
   */
  celebrateKey = null,
}: {
  celebrateKey?: number | null
}) {
  return (
    <>
      {CHEER_ANIMALS.map((animal) => (
        // 자리잡기·뛰기·좌우반전이 모두 transform을 쓴다. 한 요소에 겹쳐 두면
        // 서로 덮어써서 반전이 사라지므로 층을 나눈다.
        <div
          className="pointer-events-none absolute z-[3] -translate-x-1/2"
          key={animal.id}
          style={{
            left: animal.x,
            // 발끝을 groundY에 맞추려고 아래를 기준으로 놓는다.
            top: animal.groundY - animal.height,
            height: animal.height,
          }}
        >
          <div
            className="h-full"
            // 정답이 바뀔 때마다 다시 마운트해 크게 뛰는 동작을 처음부터 재생한다.
            key={celebrateKey ?? 'idle'}
            style={{
              animation:
                celebrateKey === null
                  ? `recycle-cheer-hop ${HOP_PERIOD_MS}ms ease-in-out ${animal.hopDelayMs}ms infinite`
                  : `recycle-cheer-jump ${JUMP_MS}ms cubic-bezier(0.34, 1.4, 0.64, 1) ${-animal.hopDelayMs * 0.06}ms both`,
            }}
          >
            <img
              alt=""
              className="block h-full w-auto object-contain drop-shadow-[0_6px_10px_rgba(20,50,16,0.35)]"
              decoding="async"
              draggable={false}
              src={animal.imageUrl}
              style={{ transform: animal.flip ? 'scaleX(-1)' : undefined }}
            />
          </div>
        </div>
      ))}
    </>
  )
}
