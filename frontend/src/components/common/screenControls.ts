/**
 * 화면 공통 버튼의 자리.
 *
 * 음악 토글·뒤로가기·닫기는 거의 모든 화면에 있다. 화면마다 몇 px씩 다르면
 * 넘어갈 때 버튼이 미끄러지듯 움직여서 아이가 같은 버튼으로 인식하지 못한다.
 * 자리를 여기 한곳에 적어 두고 모든 화면이 같은 값을 쓴다.
 *
 * 값은 1280x720 캔버스 기준 px다. 화면 배율은 `useFitScale`이 맞추므로 여기서
 * 뷰포트 단위를 쓸 일은 없다.
 */
export const SCREEN_CONTROL_SIZE = 62

/**
 * 세 버튼 모두 모서리에서 25px.
 *
 * 여백을 같게 두면 화면을 넘겨도 버튼이 제자리에 있는 것으로 읽힌다. 값이
 * 조금씩 다르면 미끄러지듯 움직여 같은 버튼으로 인식되지 않는다.
 */
export const SCREEN_CONTROL_POSITION = {
  back: { top: 25, left: 25 },
  close: { top: 25, right: 25 },
  music: { bottom: 25, left: 25 },
} as const
