import type { RefObject } from 'react'

import type { CameraStatus } from '@/hooks/useCameraStream'

import { useAttachStream } from './useAttachStream'

/**
 * 튜토리얼 카메라 미리보기.
 *
 * 아이가 "카메라에 내가 어떻게 보이는지"를 눈으로 확인하는 화면이다. 좌우를
 * 뒤집어 거울처럼 보여 준다. 뒤집지 않으면 아이가 오른팔을 들었는데 화면에서는
 * 왼쪽에 나와 방향 감각이 어긋난다.
 *
 * 스트림 연결을 여기서 한 번 더 확인한다. 권한을 이미 받은 상태에서 이 단계로
 * 되돌아오면 video 엘리먼트가 새로 마운트되는데, `useCameraStream`은 status가
 * 바뀔 때만 연결하므로 그때는 화면이 비어 버린다.
 */

const jua = { fontFamily: "'Jua', sans-serif" } as const

export function TutorialCamera({
  detected,
  gapClassName = 'gap-2',
  hintWhenWaiting,
  modelReady,
  progress,
  status,
  streamRef,
  videoRef,
}: {
  detected: boolean
  gapClassName?: string
  /** 아직 못 찾았을 때 보여 줄 안내 */
  hintWhenWaiting: string
  modelReady: boolean
  /** 인식 유지 진행도 0..1 */
  progress: number
  status: CameraStatus
  streamRef: RefObject<MediaStream | null>
  videoRef: RefObject<HTMLVideoElement | null>
}) {
  useAttachStream(videoRef, streamRef)

  if (status === 'denied' || status === 'unavailable' || status === 'error') {
    return (
      <p
        className="max-w-[300px] text-center text-[22px] leading-snug text-[#9a6a34]"
        style={jua}
      >
        카메라를 켤 수 없어요.
        <br />
        그냥 넘어가도 괜찮아요!
      </p>
    )
  }

  const ready = status === 'granted' && modelReady
  const message = !ready ? '카메라를 준비하고 있어요' : hintWhenWaiting

  return (
    <div className={`flex w-full flex-col items-center ${gapClassName}`}>
      <div
        className="relative w-full overflow-hidden rounded-[18px] bg-[#e7dcc4]"
        style={{
          aspectRatio: '4 / 3',
          // 잡히면 테두리가 초록으로 차오른다. 말 대신 색으로 "됐다"를 알린다.
          boxShadow: detected
            ? '0 0 0 5px #6cbc3f, 0 6px 14px rgba(60,110,30,0.35)'
            : '0 0 0 4px #d8c39d',
        }}
      >
        <video
          autoPlay
          className="h-full w-full scale-x-[-1] object-cover"
          muted
          playsInline
          ref={videoRef}
        />

        {/* 유지 진행도 — 아래에서 위로 차오르는 띠 */}
        {detected ? (
          <div
            className="pointer-events-none absolute inset-x-0 bottom-0 bg-[rgba(108,188,63,0.35)] transition-[height] duration-100"
            style={{ height: `${progress * 100}%` }}
          />
        ) : null}
      </div>

      <p
        className="text-center text-[20px] leading-snug text-[#9a6a34]"
        style={jua}
      >
        {detected ? '좋아요! 그대로 있어 봐요' : message}
      </p>
    </div>
  )
}
