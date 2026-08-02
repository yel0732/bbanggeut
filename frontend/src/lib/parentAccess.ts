/**
 * 부모 모드 PIN 인증 통과 여부를 기억한다.
 *
 * sessionStorage에 두는 이유는 "새로고침은 통과, 주소창 입력은 차단"을 동시에
 * 만족시키기 위해서다. 메모리 변수로 두면 부모 모드에서 F5만 눌러도 인증이
 * 날아가 PIN을 다시 묻고, localStorage에 두면 탭을 닫았다 열어도 남아 있어
 * PIN 없이 부모 모드로 들어가진다. sessionStorage는 새로고침에는 살아남고
 * 탭을 닫으면 사라지며 탭끼리 격리된다.
 *
 * 아이 모드·프로필 선택·시작 화면에서 주소창으로 `/parent`를 치는 경로가
 * 막히는 것은 `ProfileSelectPage`가 마운트될 때마다 `revokeParentAccess()`를
 * 부르기 때문이다. 부모 모드를 벗어나는 모든 길은 프로필 선택 화면을 지나므로,
 * 그 화면을 한 번이라도 거친 뒤에는 인증이 남아 있지 않다.
 *
 * 이건 어디까지나 화면 가드다. 실제 권한 검사는 서버가 해야 한다.
 */
const PARENT_ACCESS_KEY = 'bbanggut-parent-access'

/** sessionStorage를 쓸 수 없는 환경(사생활 보호 모드 등)을 위한 대비책. */
let fallbackGranted = false

export function grantParentAccess() {
  fallbackGranted = true
  try {
    window.sessionStorage.setItem(PARENT_ACCESS_KEY, 'true')
  } catch {
    // 저장에 실패하면 이번 탭에서는 메모리 값으로만 버틴다(새로고침 시 재인증).
  }
}

export function revokeParentAccess() {
  fallbackGranted = false
  try {
    window.sessionStorage.removeItem(PARENT_ACCESS_KEY)
  } catch {
    // 지우지 못해도 메모리 값이 이미 꺼졌으므로 아래 has 검사에서 막힌다.
  }
}

export function hasParentAccess() {
  if (fallbackGranted) return true
  try {
    return window.sessionStorage.getItem(PARENT_ACCESS_KEY) === 'true'
  } catch {
    return false
  }
}
