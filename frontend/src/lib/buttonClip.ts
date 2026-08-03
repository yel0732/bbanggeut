/*
  버튼 그림의 투명 여백을 잘라 내는 값.

  나무 버튼 그림들은 넓은 네모 안에 알약 하나가 놓인 모양이고 그 바깥은 전부
  투명하다. 상자를 그대로 두면 그림이 없는 자리를 눌러도 버튼이 눌리고, 버튼을
  위아래로 붙여 놓으면 서로의 여백이 겹쳐 위 버튼이 아래 버튼을 가린다.

  clip-path는 그림뿐 아니라 눌리는 자리도 함께 자른다. 그래서 이 값만 걸면
  보이는 곳과 눌리는 곳이 같아진다.

  값은 각 그림의 알파 채널 경계 상자에서 뽑았다(위·오른쪽·아래·왼쪽 순).
  모서리가 둥근 만큼은 남는다. 그림을 바꾸면 이 값도 다시 뽑아야 한다.
*/

/** 초록 완료 버튼 — parent-profile/btn-complete.webp, auth/btn-go-login.webp */
export const COMPLETE_BUTTON_CLIP = 'inset(23.62% 11.37% 25.69% 11.33%)'

/** 크림색 보조 버튼 — parent-profile/btn-photo.webp, auth/btn-cream.webp */
export const PHOTO_BUTTON_CLIP = 'inset(21.13% 17.99% 23.48% 18.32%)'

/** 게임 중단 화면의 이어하기 — game-common/resume-button.webp */
export const RESUME_BUTTON_CLIP = 'inset(14.97% 7.18% 12.24% 7.23%)'

/** 게임 중단 화면의 다시하기 — game-common/replay-button.webp */
export const REPLAY_BUTTON_CLIP = 'inset(9.64% 6.01% 11.72% 5.47%)'

/** 게임 중단 화면의 나가기 — game-common/go-home-button.webp */
export const GO_HOME_BUTTON_CLIP = 'inset(6.64% 3.22% 10.03% 3.86%)'

/** 일기 저장 — diary/diary-save-btn.webp */
export const DIARY_SAVE_BUTTON_CLIP = 'inset(8.45% 3.23% 9.89% 3.35%)'

/** 일기 저장 안 함 — diary/diary-nosave-btn.webp */
export const DIARY_NOSAVE_BUTTON_CLIP = 'inset(13.97% 6.21% 16.62% 6.76%)'
