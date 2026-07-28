import type { CSSProperties } from 'react'

import armLeftUrl from '@/assets/images/characters/dino-idle/arm-left.webp'
import armRightUrl from '@/assets/images/characters/dino-idle/arm-right.webp'
import bodyCompleteUrl from '@/assets/images/characters/dino-idle/body-complete.webp'

import './DinoCharacter.css'

type DinoCharacterProps = {
  className?: string
  /** 캐릭터 기준 너비. 숫자는 px로 처리한다. */
  size?: number | string
  motion?: 'idle' | 'still'
}

/**
 * 통합 몸통과 양팔 파츠를 transform으로 움직이는 공용 공룡 캐릭터.
 * 파츠 좌표와 모션 수치는 CSS 변수로 분리해 말하기/듣기 모션에도 재사용할 수 있다.
 */
export function DinoCharacter({
  className = '',
  motion = 'idle',
  size = 320,
}: DinoCharacterProps) {
  const style = {
    width: typeof size === 'number' ? `${size}px` : size,
  } as CSSProperties

  return (
    <span
      aria-label="공룡"
      className={`dino-character dino-character--${motion} ${className}`}
      role="img"
      style={style}
    >
      <span aria-hidden="true" className="dino-character__shadow" />
      <span aria-hidden="true" className="dino-character__rig">
        {/* 팔은 몸 뒤에 두어 어깨 연결부를 통합 몸통으로 가린다. */}
        <span className="dino-character__arm dino-character__arm--left">
          <img alt="" draggable={false} src={armLeftUrl} />
        </span>
        <span className="dino-character__arm dino-character__arm--right">
          <img alt="" draggable={false} src={armRightUrl} />
        </span>

        <img
          alt=""
          className="dino-character__body"
          draggable={false}
          src={bodyCompleteUrl}
        />
      </span>
    </span>
  )
}
