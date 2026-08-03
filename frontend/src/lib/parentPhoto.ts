/*
  아직 서버에 올리지 못한 부모 프로필 사진을 잠시 맡아 둔다.

  온보딩에서 찍은 사진은 핀을 저장한 직후 한 번만 업로드를 시도한다. 그 요청이
  실패해도 화면은 프로필 선택으로 넘어가므로, 여기 남겨 두지 않으면 사진이
  통째로 사라진다. 부모 모드가 이 값을 이어받아 다시 저장할 수 있다.

  서버에 사진이 올라간 것이 확인되면 지운다 — 서버가 진짜이고 이건 임시본이다.
*/
const PARENT_PHOTO_KEY = 'bbanggut-parent-photo'

export function loadParentPhoto() {
  return window.localStorage.getItem(PARENT_PHOTO_KEY)
}

export function saveParentPhoto(dataImageUrl: string) {
  window.localStorage.setItem(PARENT_PHOTO_KEY, dataImageUrl)
}

export function clearParentPhoto() {
  window.localStorage.removeItem(PARENT_PHOTO_KEY)
}
