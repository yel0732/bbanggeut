import airplaneUrl from '@/assets/images/wordmatch-game/words/airplane.webp'
import ambulanceUrl from '@/assets/images/wordmatch-game/words/ambulance.webp'
import bagUrl from '@/assets/images/wordmatch-game/words/bag.webp'
import balloonUrl from '@/assets/images/wordmatch-game/words/balloon.webp'
import bedUrl from '@/assets/images/wordmatch-game/words/bed.webp'
import bicycleUrl from '@/assets/images/wordmatch-game/words/bicycle.webp'
import blocksUrl from '@/assets/images/wordmatch-game/words/blocks.webp'
import breadUrl from '@/assets/images/wordmatch-game/words/bread.webp'
import busUrl from '@/assets/images/wordmatch-game/words/bus.webp'
import cakeUrl from '@/assets/images/wordmatch-game/words/cake.webp'
import cameraUrl from '@/assets/images/wordmatch-game/words/camera.webp'
import candyUrl from '@/assets/images/wordmatch-game/words/candy.webp'
import carUrl from '@/assets/images/wordmatch-game/words/car.webp'
import catUrl from '@/assets/images/wordmatch-game/words/cat.webp'
import chairUrl from '@/assets/images/wordmatch-game/words/chair.webp'
import cheeseUrl from '@/assets/images/wordmatch-game/words/cheese.webp'
import chopsticksUrl from '@/assets/images/wordmatch-game/words/chopsticks.webp'
import clockUrl from '@/assets/images/wordmatch-game/words/clock.webp'
import computerUrl from '@/assets/images/wordmatch-game/words/computer.webp'
import deskUrl from '@/assets/images/wordmatch-game/words/desk.webp'
import dollUrl from '@/assets/images/wordmatch-game/words/doll.webp'
import eggUrl from '@/assets/images/wordmatch-game/words/egg.webp'
import firetruckUrl from '@/assets/images/wordmatch-game/words/firetruck.webp'
import gimbapUrl from '@/assets/images/wordmatch-game/words/gimbap.webp'
import glassesUrl from '@/assets/images/wordmatch-game/words/glasses.webp'
import hamburgerUrl from '@/assets/images/wordmatch-game/words/hamburger.webp'
import hammerUrl from '@/assets/images/wordmatch-game/words/hammer.webp'
import hatUrl from '@/assets/images/wordmatch-game/words/hat.webp'
import helicopterUrl from '@/assets/images/wordmatch-game/words/helicopter.webp'
import kettleUrl from '@/assets/images/wordmatch-game/words/kettle.webp'
import keyUrl from '@/assets/images/wordmatch-game/words/key.webp'
import milkUrl from '@/assets/images/wordmatch-game/words/milk.webp'
import mirrorUrl from '@/assets/images/wordmatch-game/words/mirror.webp'
import padlockUrl from '@/assets/images/wordmatch-game/words/padlock.webp'
import pencilUrl from '@/assets/images/wordmatch-game/words/pencil.webp'
import pizzaUrl from '@/assets/images/wordmatch-game/words/pizza.webp'
import plateUrl from '@/assets/images/wordmatch-game/words/plate.webp'
import policecarUrl from '@/assets/images/wordmatch-game/words/policecar.webp'
import popcornUrl from '@/assets/images/wordmatch-game/words/popcorn.webp'
import potUrl from '@/assets/images/wordmatch-game/words/pot.webp'
import puppyUrl from '@/assets/images/wordmatch-game/words/puppy.webp'
import rabbitUrl from '@/assets/images/wordmatch-game/words/rabbit.webp'
import rainbowUrl from '@/assets/images/wordmatch-game/words/rainbow.webp'
import robotUrl from '@/assets/images/wordmatch-game/words/robot.webp'
import sailboatUrl from '@/assets/images/wordmatch-game/words/sailboat.webp'
import scissorsUrl from '@/assets/images/wordmatch-game/words/scissors.webp'
import shoesUrl from '@/assets/images/wordmatch-game/words/shoes.webp'
import snowmanUrl from '@/assets/images/wordmatch-game/words/snowman.webp'
import soapUrl from '@/assets/images/wordmatch-game/words/soap.webp'
import soccerballUrl from '@/assets/images/wordmatch-game/words/soccerball.webp'
import spaceshipUrl from '@/assets/images/wordmatch-game/words/spaceship.webp'
import spoonUrl from '@/assets/images/wordmatch-game/words/spoon.webp'
import storybookUrl from '@/assets/images/wordmatch-game/words/storybook.webp'
import telephoneUrl from '@/assets/images/wordmatch-game/words/telephone.webp'
import toothbrushUrl from '@/assets/images/wordmatch-game/words/toothbrush.webp'
import towelUrl from '@/assets/images/wordmatch-game/words/towel.webp'
import trainUrl from '@/assets/images/wordmatch-game/words/train.webp'
import umbrellaUrl from '@/assets/images/wordmatch-game/words/umbrella.webp'

import { WORD_MATCH_CONFIG as CFG } from './wordMatchConfig'

/**
 * 낱말 문제 목록 (WGAME-03, WGAME-12).
 *
 * 서버가 문제 카탈로그를 내려주지 않으므로 프론트가 유일한 출처다. 저장 요청에는
 * `code`를 그대로 보내고, 서버는 같은 코드가 한 판에 두 번 오면 결과를 거부한다.
 *
 * 낱말 길이는 `answer`에서 세므로 따로 적지 않는다. 길이 필드를 따로 두면 글자를
 * 고칠 때 한쪽만 바뀌어 어긋난다.
 */

export type WordProblem = {
  /** 서버에 보내는 문제 코드. 한 판에서 중복될 수 없다. */
  code: string
  /** 정답 낱말 */
  answer: string
  imageUrl: string
}

export const WORD_PROBLEMS: WordProblem[] = [
  { code: 'BAG', answer: '가방', imageUrl: bagUrl },
  { code: 'SCISSORS', answer: '가위', imageUrl: scissorsUrl },
  { code: 'MIRROR', answer: '거울', imageUrl: mirrorUrl },
  { code: 'TRAIN', answer: '기차', imageUrl: trainUrl },
  { code: 'GIMBAP', answer: '김밥', imageUrl: gimbapUrl },
  { code: 'POT', answer: '냄비', imageUrl: potUrl },
  { code: 'EGG', answer: '달걀', imageUrl: eggUrl },
  { code: 'ROBOT', answer: '로봇', imageUrl: robotUrl },
  { code: 'HAMMER', answer: '망치', imageUrl: hammerUrl },
  { code: 'HAT', answer: '모자', imageUrl: hatUrl },
  { code: 'BUS', answer: '버스', imageUrl: busUrl },
  { code: 'BLOCKS', answer: '블록', imageUrl: blocksUrl },
  { code: 'SOAP', answer: '비누', imageUrl: soapUrl },
  { code: 'CANDY', answer: '사탕', imageUrl: candyUrl },
  { code: 'TOWEL', answer: '수건', imageUrl: towelUrl },
  { code: 'CLOCK', answer: '시계', imageUrl: clockUrl },
  { code: 'BREAD', answer: '식빵', imageUrl: breadUrl },
  { code: 'SHOES', answer: '신발', imageUrl: shoesUrl },
  { code: 'GLASSES', answer: '안경', imageUrl: glassesUrl },
  { code: 'PENCIL', answer: '연필', imageUrl: pencilUrl },
  { code: 'KEY', answer: '열쇠', imageUrl: keyUrl },
  { code: 'UMBRELLA', answer: '우산', imageUrl: umbrellaUrl },
  { code: 'MILK', answer: '우유', imageUrl: milkUrl },
  { code: 'CHAIR', answer: '의자', imageUrl: chairUrl },
  { code: 'DOLL', answer: '인형', imageUrl: dollUrl },
  { code: 'PLATE', answer: '접시', imageUrl: plateUrl },
  { code: 'DESK', answer: '책상', imageUrl: deskUrl },
  { code: 'CHEESE', answer: '치즈', imageUrl: cheeseUrl },
  { code: 'BED', answer: '침대', imageUrl: bedUrl },
  { code: 'TOOTHBRUSH', answer: '칫솔', imageUrl: toothbrushUrl },
  { code: 'RABBIT', answer: '토끼', imageUrl: rabbitUrl },
  { code: 'POPCORN', answer: '팝콘', imageUrl: popcornUrl },
  { code: 'BALLOON', answer: '풍선', imageUrl: balloonUrl },
  { code: 'PIZZA', answer: '피자', imageUrl: pizzaUrl },
  { code: 'HELICOPTER', answer: '헬기', imageUrl: helicopterUrl },
  { code: 'PUPPY', answer: '강아지', imageUrl: puppyUrl },
  { code: 'POLICECAR', answer: '경찰차', imageUrl: policecarUrl },
  { code: 'CAT', answer: '고양이', imageUrl: catUrl },
  { code: 'AMBULANCE', answer: '구급차', imageUrl: ambulanceUrl },
  { code: 'SNOWMAN', answer: '눈사람', imageUrl: snowmanUrl },
  { code: 'STORYBOOK', answer: '동화책', imageUrl: storybookUrl },
  { code: 'SAILBOAT', answer: '돛단배', imageUrl: sailboatUrl },
  { code: 'RAINBOW', answer: '무지개', imageUrl: rainbowUrl },
  { code: 'AIRPLANE', answer: '비행기', imageUrl: airplaneUrl },
  { code: 'FIRETRUCK', answer: '소방차', imageUrl: firetruckUrl },
  { code: 'SPOON', answer: '숟가락', imageUrl: spoonUrl },
  { code: 'SPACESHIP', answer: '우주선', imageUrl: spaceshipUrl },
  { code: 'CAR', answer: '자동차', imageUrl: carUrl },
  { code: 'PADLOCK', answer: '자물쇠', imageUrl: padlockUrl },
  { code: 'BICYCLE', answer: '자전거', imageUrl: bicycleUrl },
  { code: 'TELEPHONE', answer: '전화기', imageUrl: telephoneUrl },
  { code: 'CHOPSTICKS', answer: '젓가락', imageUrl: chopsticksUrl },
  { code: 'KETTLE', answer: '주전자', imageUrl: kettleUrl },
  { code: 'SOCCERBALL', answer: '축구공', imageUrl: soccerballUrl },
  { code: 'CAMERA', answer: '카메라', imageUrl: cameraUrl },
  { code: 'COMPUTER', answer: '컴퓨터', imageUrl: computerUrl },
  { code: 'CAKE', answer: '케이크', imageUrl: cakeUrl },
  { code: 'HAMBURGER', answer: '햄버거', imageUrl: hamburgerUrl },
]

/**
 * 방해 글자 풀 (WGAME-03).
 *
 * 문제마다 손으로 적지 않고 여기서 뽑는다. 낱말이 쉰 개를 넘어가면 손으로 적은
 * 목록은 반드시 어딘가 빠지거나 정답 글자와 겹친다. 뽑을 때 정답에 든 글자를
 * 빼므로 겹칠 일이 없다.
 *
 * 5~7세가 읽을 수 있는 흔한 글자만 둔다. 정답 글자와 헷갈릴 만큼 닮은 글자를
 * 일부러 넣지는 않는다 — 틀리게 만드는 것이 목적이 아니라 고르는 연습이 목적이다.
 */
const DISTRACTOR_POOL = [
  '가',
  '나',
  '다',
  '라',
  '마',
  '바',
  '사',
  '아',
  '자',
  '차',
  '카',
  '타',
  '파',
  '하',
  '거',
  '너',
  '더',
  '러',
  '머',
  '버',
  '고',
  '노',
  '도',
  '로',
  '모',
  '보',
  '구',
  '누',
  '두',
  '루',
  '무',
  '부',
  '기',
  '니',
  '리',
  '미',
  '비',
  '시',
  '이',
  '지',
]

/**
 * 지금 단계에서 낼 낱말 길이 (WGAME-11).
 *
 * 완성한 개수로 단계를 정한다. 2글자 2개를 완성하면 3글자로, 거기서 3개를 더
 * 완성하면 그 뒤로는 마지막 단계를 유지한다.
 */
export function wordLengthForSolvedCount(solvedCount: number): number {
  let remaining = solvedCount

  for (const step of CFG.difficultySteps) {
    if (step.requiredCount === null || remaining < step.requiredCount) {
      return step.wordLength
    }
    remaining -= step.requiredCount
  }

  return CFG.difficultySteps[CFG.difficultySteps.length - 1].wordLength
}

/**
 * 다음에 낼 문제.
 *
 * 이미 낸 문제는 다시 내지 않는다(WGAME-12). 서버도 한 판에 같은 코드가 두 번
 * 오면 결과를 거부하므로 이건 표시 문제가 아니라 저장 조건이다.
 *
 * 목표 길이의 문제가 남지 않으면 길이를 낮춰 가며 찾는다. 지금 문제 목록에는
 * 4글자 낱말이 없어서 마지막 단계에 가면 반드시 이 경로를 탄다. 남은 문제가 아예
 * 없으면 null을 주고, 부르는 쪽이 게임을 끝낸다.
 */
export function pickNextProblem(
  usedCodes: readonly string[],
  targetLength: number,
): WordProblem | null {
  const used = new Set(usedCodes)
  const available = WORD_PROBLEMS.filter((problem) => !used.has(problem.code))

  if (available.length === 0) {
    return null
  }

  for (let length = targetLength; length >= 2; length--) {
    const sameLength = available.filter(
      (problem) => problem.answer.length === length,
    )
    if (sameLength.length > 0) {
      return sameLength[Math.floor(Math.random() * sameLength.length)]
    }
  }

  const longest = Math.max(...available.map((problem) => problem.answer.length))
  const pool = available.filter((problem) => problem.answer.length === longest)
  return pool[Math.floor(Math.random() * pool.length)]
}

/**
 * 문제의 글자 카드 목록.
 *
 * 정답 글자를 필요한 개수만큼 넣고(같은 글자가 두 번 나오면 두 장), 방해 글자로
 * 7장을 채운 뒤 순서를 섞는다. 방해 글자는 정답에 든 글자를 뺀 풀에서 겹치지
 * 않게 뽑는다.
 */
export function buildLetterCards(problem: WordProblem): string[] {
  const letters = [...problem.answer]
  const answerLetters = new Set(letters)
  const candidates = DISTRACTOR_POOL.filter(
    (letter) => !answerLetters.has(letter),
  )

  const distractors: string[] = []
  const needed = CFG.letterCardCount - letters.length
  while (distractors.length < needed && candidates.length > 0) {
    const index = Math.floor(Math.random() * candidates.length)
    distractors.push(candidates.splice(index, 1)[0])
  }

  const cards = [...letters, ...distractors]

  // Fisher-Yates. 정답 글자가 늘 앞에 오면 순서대로 집기만 해도 맞는다.
  for (let i = cards.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[cards[i], cards[j]] = [cards[j], cards[i]]
  }

  return cards
}

/** 문제 그림을 미리 디코딩해 둔다. 문제가 바뀌는 순간 늦게 뜨지 않게 한다. */
export async function preloadWordImages(): Promise<void> {
  await Promise.all(
    WORD_PROBLEMS.map(
      (problem) =>
        new Promise<void>((resolve) => {
          const image = new Image()
          image.onload = () => resolve()
          image.onerror = () => resolve()
          image.src = problem.imageUrl
        }),
    ),
  )
}
