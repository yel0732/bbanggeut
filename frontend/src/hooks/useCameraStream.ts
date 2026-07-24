import { useCallback, useEffect, useRef, useState } from 'react'

/**
 * 카메라 권한/스트림 상태.
 * - idle: 아직 요청 전
 * - requesting: 권한 요청 및 스트림 연결 대기
 * - granted: 카메라 사용 가능
 * - denied: 사용자가 권한을 거부
 * - unavailable: 카메라 없음 또는 브라우저 미지원
 * - error: 사용 중 연결이 끊김 (MGAME-30)
 */
export type CameraStatus =
  'idle' | 'requesting' | 'granted' | 'denied' | 'unavailable' | 'error'

/**
 * 게임 플레이 화면용 카메라 훅.
 *
 * MGAME-01/02 대응:
 * - requestCamera()로 권한을 요청하고 상태를 노출한다.
 * - 언마운트(화면 이탈) 시 stopCamera로 스트림을 즉시 종료한다.
 *
 * 모션 인식(MediaPipe 연동)은 다음 태스크에서 이 스트림 위에 붙인다.
 */
export function useCameraStream() {
  const videoRef = useRef<HTMLVideoElement>(null)
  const streamRef = useRef<MediaStream | null>(null)
  // 의도적 종료(언마운트/재요청)와 예기치 못한 끊김을 구분한다.
  const intentionalStopRef = useRef(false)
  const [status, setStatus] = useState<CameraStatus>('idle')

  const stopCamera = useCallback(() => {
    intentionalStopRef.current = true
    streamRef.current?.getTracks().forEach((track) => track.stop())
    streamRef.current = null
    if (videoRef.current) {
      videoRef.current.srcObject = null
    }
  }, [])

  const requestCamera = useCallback(async () => {
    if (!navigator.mediaDevices?.getUserMedia) {
      setStatus('unavailable')
      return
    }

    setStatus('requesting')
    intentionalStopRef.current = false

    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: false,
        video: { facingMode: 'user' },
      })
      streamRef.current = stream
      if (videoRef.current) {
        videoRef.current.srcObject = stream
      }
      // 사용 중 트랙이 끊기면(장치 분리 등) 오류로 표시 (MGAME-30).
      stream.getVideoTracks().forEach((track) => {
        track.addEventListener('ended', () => {
          if (!intentionalStopRef.current) {
            setStatus('error')
          }
        })
      })
      setStatus('granted')
    } catch (error) {
      if (error instanceof DOMException && error.name === 'NotFoundError') {
        setStatus('unavailable')
      } else {
        setStatus('denied')
      }
    }
  }, [])

  // status가 granted로 바뀐 뒤 video 엘리먼트가 준비되면 스트림을 다시 연결한다.
  useEffect(() => {
    if (status === 'granted' && videoRef.current && streamRef.current) {
      videoRef.current.srcObject = streamRef.current
    }
  }, [status])

  // MGAME-02: 화면 이탈/언마운트 시 카메라 즉시 종료.
  useEffect(() => stopCamera, [stopCamera])

  return { videoRef, status, requestCamera, stopCamera }
}
