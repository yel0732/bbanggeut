import { useEffect, type RefObject } from 'react'

/**
 * video 엘리먼트가 마운트될 때마다 카메라 스트림을 다시 물린다.
 *
 * `useCameraStream`은 status가 바뀌는 순간에만 연결한다. 단계를 오가며 video가
 * 새로 마운트되면 그때는 이미 status가 granted라 연결이 일어나지 않고 화면이
 * 비어 버린다.
 */
export function useAttachStream(
  videoRef: RefObject<HTMLVideoElement | null>,
  streamRef: RefObject<MediaStream | null>,
): void {
  useEffect(() => {
    const video = videoRef.current
    const stream = streamRef.current
    if (video && stream && video.srcObject !== stream) {
      video.srcObject = stream
    }
  })
}
