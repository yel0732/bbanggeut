/**
 * 낱말 맞추기 게임 규칙 설정 (WGAME).
 *
 * 제한 시간, 판 배치, 조준 시간, 점수처럼 사용자 테스트로 조정될 값은 모두
 * 여기 모아 둔다. 로직 파일에는 상수를 두지 않는다.
 *
 * 좌표는 모두 1280x720 캔버스 기준 좌상단 px다. 배경 그림의 두 판(위쪽 양피지,
 * 아래쪽 나무 띠) 안쪽 영역을 픽셀로 재서 넣었다.
 */
export const WORD_MATCH_CONFIG = {
  /** 규칙 버전 (결과 저장 시 기록) */
  rulesVersion: 'WORD_MATCH_V1',
  canvasWidth: 1280,
  canvasHeight: 720,

  /** 총 진행 시간(초). 일시정지·손 놓침 시간은 제외한 실제 플레이 시간이다. */
  durationSec: 90,
  /** 손 인식 후 시작 카운트다운(초) */
  countdownSec: 3,
  /** 남은 시간이 이 값 이하면 화면에서 강조한다(초) */
  timeWarningSec: 15,

  /** 힌트 없이 완성한 낱말 점수 */
  scoreWithoutHint: 1000,
  /** 힌트를 쓴 뒤 완성한 낱말 점수 */
  scoreWithHint: 700,
  /** 한 문제에서 이 횟수만큼 틀리면 힌트를 켠다(WGAME-13). */
  hintWrongThreshold: 2,

  /** 화면에 늘어놓는 글자 카드 수(WGAME-03) */
  letterCardCount: 7,

  /**
   * 낱말 길이 단계(WGAME-11).
   *
   * 위에서부터 순서대로 진행한다. `requiredCount`만큼 완성하면 다음 단계로
   * 넘어가고, 마지막 단계는 계속 유지한다.
   */
  difficultySteps: [
    { wordLength: 2, requiredCount: 2 },
    { wordLength: 3, requiredCount: 3 },
    { wordLength: 4, requiredCount: null },
  ],

  /**
   * 위쪽 양피지 판 — 그림과 정답 칸이 놓이는 자리.
   *
   * 배경 그림의 나무 테두리 안쪽 크림색 영역이다. 캔버스 좌표 격자를 배경에
   * 얹어 재서 넣었다.
   */
  board: {
    x: 300,
    y: 30,
    width: 680,
    height: 315,
  },

  /** 문제 그림 */
  picture: {
    /** 그림 높이(px). 폭은 비율대로 따라온다. */
    height: 152,
    /** 판 안에서 그림 가운데의 y */
    centerY: 100,
  },

  /** 정답 칸 — 낱말 길이만큼 가로로 늘어놓는다. */
  slot: {
    /** 칸 높이(px). 폭은 비율대로 따라온다. */
    height: 92,
    /** 칸 사이 간격(px) */
    gap: 16,
    /** 판 안에서 칸 가운데의 y */
    centerY: 246,
  },

  /**
   * 아래쪽 나무 띠 — 글자 카드가 놓이는 자리.
   *
   * 카드를 한 줄로 세운다(WGAME-12). 띠가 화면 아래로 이어져 있어 높이는 보이는
   * 만큼만 잡고, 카드는 그 가운데에 놓는다.
   */
  tray: {
    x: 112,
    y: 495,
    width: 1066,
    height: 190,
    /** 카드 한 변(px) */
    cardSize: 124,
    /** 카드 사이 간격(px) */
    gap: 26,
  },

  /** 손바닥을 글자 위에 올린 뒤 링이 다 차는 시간(ms)(WGAME-05) */
  aimMs: 300,
  /** 표시 크기 대비 히트 반경 비율 */
  hitRadiusScale: 0.46,

  /** 정답 연출 시간(ms). 이 동안 입력을 받지 않는다. */
  correctFeedbackMs: 1600,
  /** 오답 안내 시간(ms). 이 동안 입력을 받지 않는다. */
  wrongFeedbackMs: 1100,

  /** 손이 사라진 뒤 자동 중단까지 유예(ms) */
  handLostGraceMs: 700,

  /**
   * 손 좌표 평활 계수. 1에 가까울수록 손이 즉각 따라온다.
   *
   * 분리수거와 같은 값을 쓴다. 두 게임 다 화면을 위아래로 크게 오간다.
   */
  handSmoothing: 0.6,
  /**
   * 손이 닿는 범위를 넓히는 배율.
   *
   * 아이는 카메라 프레임 가장자리까지 팔을 뻗지 않는다. 가운데 영역을 화면
   * 전체로 늘려, 팔을 조금만 움직여도 양쪽 끝 카드까지 닿게 한다.
   */
  reachGain: 1.3,
} as const

/** 정답 칸 원본 비율(폭/높이). 에셋 284x240에서 나온 값. */
export const SLOT_ASPECT = 284 / 240
