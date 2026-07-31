/**
 * 분리수거 게임 규칙 설정 (RGAME).
 *
 * 제한 시간, 물길·통 배치, 조준 시간, 판정 후 잠금 시간처럼 사용자 테스트로
 * 조정될 값은 모두 여기 모아 둔다. 로직 파일에는 상수를 두지 않는다.
 *
 * 좌표는 모두 1280x720 캔버스 기준 좌상단 px다. 배경 이미지에서 물길이
 * 차지하는 띠(세로 35~66%)에 맞춰 물길 값을 잡았고, 통은 그 아래 잔디에 둔다.
 */
export const RECYCLE_GAME_CONFIG = {
  /** 규칙 버전 (결과 저장 시 기록) */
  rulesVersion: 'RECYCLING_V1',
  canvasWidth: 1280,
  canvasHeight: 720,

  /** 총 진행 시간(초). 일시정지·손 놓침 시간은 제외한 실제 플레이 시간이다. */
  durationSec: 60,
  /** 손 인식 후 시작 카운트다운(초) */
  countdownSec: 3,
  /** 남은 시간이 이 값 이하면 화면에서 강조한다(초) */
  timeWarningSec: 10,

  /** 정답 1개당 점수. 서버가 successCount × 이 값으로 검증한다. */
  scorePerCorrect: 100,

  /**
   * 쓰레기가 떠내려오는 물길.
   *
   * `y`는 쓰레기 중심이 지나는 높이다. 물길 띠(252~474px) 가운데보다 조금
   * 위에 둬서, 아래쪽 통을 향해 손을 내릴 여유를 남긴다.
   */
  river: {
    y: 344,
    /**
     * 쓰레기가 놓이는 높이 갈래. `y`에서 위아래로 이만큼씩 벌린다.
     *
     * 모두 같은 높이로 흐르면 한 줄로 늘어서 보인다. 갈래를 나눠 놓으면
     * 빠른 쓰레기가 느린 쓰레기를 다른 높이로 지나쳐 강처럼 보인다.
     */
    lanesY: [-52, -18, 18, 52],
    /** 갈래 안에서 더 흩어 놓는 폭(px) */
    laneJitterPx: 10,
    /** 중심 높이에서 위아래로 흔들리는 폭(px) */
    bobAmplitude: 9,
    /** 흔들림 한 주기(ms) */
    bobPeriodMs: 2600,
  },

  /**
   * 쓰레기 이동 속도(px/초)와 쓰레기별 편차.
   *
   * RGAME-04가 막는 것은 게임이 진행될수록 빨라지는 난이도 상승이다. 여기
   * 편차는 쓰레기가 생길 때 한 번 정해지고 그 뒤로 바뀌지 않으며, 뽑는 범위도
   * 게임 내내 같다.
   */
  itemSpeedPx: 84,
  /** 기준 속도 대비 쓰레기별 편차 비율(±). 0.22면 66~102px/초. */
  speedJitterRatio: 0.22,
  /**
   * 쓰레기 생성 간격(ms). 게임 중 바뀌지 않는다(RGAME-04).
   *
   * 기준 속도 84px/초와 곱하면 쓰레기 사이가 약 270px 벌어진다. 쓰레기 자체가
   * 128px이라 사이에 140px쯤 빈다 — 손을 뻗어도 옆 것이 같이 잡히지 않는다.
   */
  spawnIntervalMs: 3200,
  /** 첫 쓰레기가 나오기까지 기다리는 시간(ms) */
  firstSpawnDelayMs: 400,
  /** 쓰레기 표시 크기(가장 긴 변, px) */
  itemSize: 128,
  /** 표시 크기 대비 히트 반경 비율 */
  hitRadiusScale: 0.46,
  /** 같은 쓰레기가 이 횟수를 넘겨 연속으로 나오지 않게 한다(RGAME-12). */
  maxSameItemStreak: 2,

  /**
   * 분리수거 통 배치.
   *
   * 네 통을 가로로 늘어놓되 오른쪽 아래 자기 모습 미리보기와 겹치지 않게
   * 오른쪽 여백을 남긴다. `y`는 통 위쪽, `dropPadTop`은 통 입구 위로 조금 더
   * 넉넉하게 잡는 판정 영역이다 — 아이 손은 정확히 통 안까지 내려오지 않는다.
   */
  bin: {
    y: 474,
    width: 196,
    height: 200,
    centersX: [250, 505, 760, 1015],
    dropPadTop: 46,
  },

  /** 손바닥을 쓰레기 위에 올린 뒤 링이 다 차는 시간(ms). 다 차면 잡을 수 있다. */
  aimMs: 300,

  /** 정답 연출 시간(ms). 이 동안 그 쓰레기는 추가 입력을 받지 않는다. */
  correctFeedbackMs: 900,
  /** 오답 안내 시간(ms). 이 동안 생성·이동·입력을 모두 멈춘다(RGAME-09). */
  wrongGuideMs: 2600,

  /** 손이 사라진 뒤 자동 중단까지 유예(ms) */
  handLostGraceMs: 700,
} as const

/** 물길 왼쪽 바깥에서 생성되는 x 좌표. 화면 밖에서 떠내려온다. */
export const SPAWN_X = -RECYCLE_GAME_CONFIG.itemSize
/** 이 x를 넘어가면 놓친 것으로 보고 제거한다(RGAME-10). */
export const DESPAWN_X =
  RECYCLE_GAME_CONFIG.canvasWidth + RECYCLE_GAME_CONFIG.itemSize
