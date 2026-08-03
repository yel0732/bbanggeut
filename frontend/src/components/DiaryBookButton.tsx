import { Link, useLocation } from 'react-router-dom'

import diaryBookButtonUrl from '@/assets/images/conversation/diary-book-btn.webp'

/**
 * 일기장으로 가는 책 버튼.
 *
 * 자리는 쓰는 화면이 정한다. `className`에 `absolute`와 좌표를 넘겨야 한다 —
 * 안쪽이 `inset-0`으로 겹쳐 놓여서 바깥 상자가 기준이 되어야 한다.
 *
 * 크기는 가로 70px 고정이고 세로는 그림 비율(1.1314)을 따라온다. 그림을 투명
 * 여백 없이 잘라 두었으므로, 넘긴 좌표가 곧 화면 가장자리에서 그림까지의 실제
 * 거리다.
 */

/*
  실제로 눌리는 영역.

  버튼 그림은 네모 상자에 담겨 있지만 눈에 보이는 것은 책 모양뿐이다. 상자를
  그대로 두면 그림이 없는 구석을 눌러도 일기장으로 넘어간다. 알파 채널의 바깥
  윤곽을 따서 그 안에서만 눌리게 한다.

  값은 에셋에서 각도별로 가장 먼 불투명 픽셀을 찾아 뽑았고, 그림이 잘리지 않도록
  윤곽보다 4% 넉넉하게 잡았다. 에셋을 바꾸면 이 값도 다시 뽑아야 한다.
*/
const DIARY_BUTTON_CLIP =
  'polygon(4.0% 47.1%,6.2% 42.8%,8.4% 38.8%,10.3% 35.1%,12.2% 31.5%,14.0% 27.9%,13.7% 22.9%,13.5% 17.3%,12.9% 10.4%,15.3% 6.0%,21.2% 6.4%,24.7% 3.7%,28.7% 2.1%,32.2% 0%,36.1% 0%,40.3% 0%,44.5% 0%,48.5% 0%,52.4% 0.2%,56.1% 1.2%,59.7% 2.3%,63.3% 3.3%,67.0% 4.2%,70.9% 5.0%,75.0% 5.9%,79.2% 7.1%,83.9% 8.2%,88.8% 9.7%,93.4% 12.2%,96.4% 16.3%,98.1% 21.3%,98.3% 27.0%,100% 31.7%,100% 36.6%,100% 41.7%,100% 46.9%,99.0% 51.8%,97.8% 56.4%,96.5% 60.9%,95.1% 65.3%,93.3% 69.4%,91.2% 73.4%,88.9% 77.1%,85.7% 80.0%,83.9% 84.0%,81.7% 87.9%,79.5% 92.1%,76.9% 96.3%,73.4% 99.1%,69.5% 100%,65.3% 100%,60.8% 100%,56.4% 100%,52.4% 98.8%,48.6% 97.0%,45.1% 95.5%,41.7% 94.1%,38.3% 93.0%,34.9% 91.8%,31.5% 90.5%,27.6% 89.9%,19.8% 94.5%,15.7% 92.3%,8.8% 92.5%,5.8% 88.1%,4.3% 82.6%,0.5% 78.8%,0% 73.7%,0% 68.2%,0% 62.6%,0.5% 57.0%,1.6% 51.8%)'

export function DiaryBookButton({ className = '' }: { className?: string }) {
  // 일기장에서 뒤로 가면 이 화면으로 돌아와야 한다. 일기장은 여러 화면에서
  // 열리므로 돌아갈 곳을 여기서 알려 준다.
  const { pathname } = useLocation()

  return (
    <div className={`h-[61.87px] w-[70px] ${className}`}>
      <Link
        aria-label="내 일기장"
        className="peer absolute inset-0 z-10 block transition hover:scale-105"
        state={{ from: pathname }}
        style={{ clipPath: DIARY_BUTTON_CLIP }}
        to="/diaries"
      >
        <img
          alt=""
          className="size-full object-contain"
          decoding="async"
          draggable={false}
          src={diaryBookButtonUrl}
        />
      </Link>
      {/*
        테두리 빛.

        링크 안이 아니라 밖에 겹쳐 둔다. 링크에는 눌리는 영역을 그림 모양으로
        자르는 clip-path가 걸려 있어서, 안에 두면 그림 밖으로 번지는 빛이 그
        선에서 잘려 나간다. 형제로 두면 빛은 잘리지 않고 누르는 영역은 그림
        모양 그대로 남는다.
      */}
      <img
        alt=""
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 size-full [animation:diary-button-glow_2.6s_ease-in-out_infinite] object-contain [filter:drop-shadow(0_0_2px_rgba(255,245,205,.95))_drop-shadow(0_0_7px_rgba(255,206,90,.9))_drop-shadow(0_0_15px_rgba(255,170,40,.55))] transition peer-hover:scale-105"
        decoding="async"
        draggable={false}
        src={diaryBookButtonUrl}
      />
    </div>
  )
}
