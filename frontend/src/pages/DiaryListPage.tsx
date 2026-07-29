import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'

import conversationBgmUrl from '@/assets/audio/conversation/todak-room.mp3'
import dateCircleUrl from '@/assets/images/diary-list/date-circle.webp'
import dateRectUrl from '@/assets/images/diary-list/date-rect.webp'
import diaryBookUrl from '@/assets/images/diary-list/diary-open-book.webp'
import monthArrowLeftUrl from '@/assets/images/diary-list/month-arrow-left.webp'
import monthArrowRightUrl from '@/assets/images/diary-list/month-arrow-right.webp'
import monthBarUrl from '@/assets/images/diary-list/month-bar.webp'
import diaryNoteUrl from '@/assets/images/diary/diary-note.webp'
import backButtonUrl from '@/assets/images/game-entry/left-btn.webp'
import pagerLeftUrl from '@/assets/images/parent-profile/pager-left.webp'
import pagerPageUrl from '@/assets/images/parent-profile/pager-page.webp'
import { getVisiblePageIndices } from '@/utils/pagination'
import pagerRightUrl from '@/assets/images/parent-profile/pager-right.webp'
import { ChildProfileSwitchButton } from '@/components/ChildProfileSwitchButton'
import { getChildDiaries } from '@/api/diaries'
import { useBgmPlayer } from '@/hooks/useBgmPlayer'
import { useFitScale } from '@/hooks/useFitScale'
import { loadActiveChildProfileId } from '@/lib/childProfiles'

type DiaryEntry = {
  id: number
  date: string
  title: string
  lines: string[]
}

const DIARIES: DiaryEntry[] = [
  {
    id: 1,
    date: '2026-07-22',
    title: '그림 맞추기 놀이',
    lines: [
      '오늘은 그림 맞추기 놀이로 이야기꽃을 피웠다.',
      '빙글이와 동물 친구들의 그림을 함께 맞혔다.',
      '가장 기억에 남는 순간은 토끼를 찾았을 때였다.',
      '내일은 빙글이와 다른 놀이도 해 보고 싶다.',
    ],
  },
  {
    id: 2,
    date: '2026-07-21',
    title: '과일 자르기 대회',
    lines: [
      '오늘 나는 과일 자르기 게임을 했다.',
      '빨간 과일을 한 번에 잘라서 신이 났다.',
      '끝까지 포기하지 않은 내가 자랑스러웠다.',
    ],
  },
  {
    id: 3,
    date: '2026-07-19',
    title: '빙글이와 나',
    lines: [
      '빙글이와 오늘 있었던 일을 이야기했다.',
      '유치원에서 그린 그림을 자랑했다.',
      '빙글이가 칭찬해 줘서 기분이 좋았다.',
    ],
  },
  {
    id: 4,
    date: '2026-07-18',
    title: '같은 그림 찾기',
    lines: [
      '같은 그림 찾기 게임을 했다.',
      '공룡 그림을 빠르게 찾아서 뿌듯했다.',
      '다음에는 더 빨리 찾고 싶다.',
    ],
  },
  {
    id: 5,
    date: '2026-07-17',
    title: '숫자 세기 대회',
    lines: [
      '빙글이와 숫자를 세어 보았다.',
      '열까지 안 틀리고 세었다.',
      '참 재미있었다.',
    ],
  },
  {
    id: 6,
    date: '2026-07-15',
    title: '색깔 맞히기',
    lines: [
      '여러 색깔을 맞히는 놀이를 했다.',
      '노란색을 제일 좋아한다고 말했다.',
      '다음에는 초록색도 골라 보고 싶다.',
    ],
  },
  {
    id: 7,
    date: '2026-07-14',
    title: '동물 소리 흉내',
    lines: [
      '동물 소리를 흉내 냈다.',
      '멍멍, 야옹 소리를 냈다.',
      '빙글이가 깔깔 웃었다.',
    ],
  },
  {
    id: 8,
    date: '2026-08-02',
    title: '여름 물놀이',
    lines: [
      '빙글이와 물놀이 이야기를 했다.',
      '시원한 물이 정말 좋았다.',
      '또 가고 싶다.',
    ],
  },
  {
    id: 9,
    date: '2026-08-01',
    title: '숫자 카드 놀이',
    lines: [
      '숫자 카드 게임을 했다.',
      '스무 개까지 세어 보았다.',
      '정말 뿌듯했다.',
    ],
  },
  {
    id: 10,
    date: '2026-06-28',
    title: '첫 그림 맞추기',
    lines: [
      '처음 그림 맞추기를 했다.',
      '조금 어려웠지만 재미있었다.',
      '다음에는 더 잘할 거다.',
    ],
  },
]

const jua = { fontFamily: "'Jua', sans-serif" } as const
const ITEMS_PER_PAGE = 4

function formatYearMonth(year: number, month: number) {
  return `${year}년 ${month}월`
}

function formatDiaryDate(date: string) {
  const [, month, day] = date.split('-').map(Number)
  return `${month}월 ${day}일`
}

export function DiaryListPage() {
  const canvasRef = useFitScale<HTMLDivElement>()
  const { audioRef } = useBgmPlayer({ volume: 0.22 })
  const [year, setYear] = useState(2026)
  const [month, setMonth] = useState(7)
  const [page, setPage] = useState(0)
  const [diaries, setDiaries] = useState<DiaryEntry[]>([])
  const [selectedId, setSelectedId] = useState<number | null>(null)

  useEffect(() => {
    // Keep the old design fixture out of the rendered flow; diaries always come
    // from the selected child's server-side diary collection.
    void DIARIES
    const childId = Number(loadActiveChildProfileId())
    if (!Number.isInteger(childId) || childId <= 0) {
      setDiaries([])
      return
    }
    let active = true
    void getChildDiaries(childId)
      .then((response) => {
        if (!active) return
        const next = response.items.map((diary) => ({
          id: diary.childDiaryId,
          date: diary.diaryDate,
          title: diary.title,
          lines: diary.lines,
        }))
        setDiaries(next)
        setSelectedId(next[0]?.id ?? null)
      })
      .catch(() => {
        if (!active) return
        setDiaries([])
        setSelectedId(null)
      })
    return () => {
      active = false
    }
  }, [])

  const monthDiaries = useMemo(
    () =>
      diaries.filter((diary) => {
        const [diaryYear, diaryMonth] = diary.date.split('-').map(Number)
        return diaryYear === year && diaryMonth === month
      }),
    [diaries, month, year],
  )
  const pageCount = Math.max(1, Math.ceil(monthDiaries.length / ITEMS_PER_PAGE))
  const visibleDiaries = monthDiaries.slice(
    page * ITEMS_PER_PAGE,
    (page + 1) * ITEMS_PER_PAGE,
  )
  const selectedDiary =
    monthDiaries.find((diary) => diary.id === selectedId) ??
    monthDiaries[0] ??
    null

  const moveMonth = (amount: number) => {
    const next = new Date(year, month - 1 + amount, 1)
    setYear(next.getFullYear())
    setMonth(next.getMonth() + 1)
    setPage(0)
    setSelectedId(null)
  }

  return (
    <main className="flex h-screen w-screen items-center justify-center overflow-hidden bg-[radial-gradient(120%_120%_at_50%_20%,_#2a3320_0%,_#131a0e_100%)]">
      <div
        className="relative h-[720px] w-[1280px] shrink-0 overflow-hidden"
        ref={canvasRef}
      >
        <audio
          aria-label="대화방 배경 음악"
          loop
          preload="auto"
          ref={audioRef}
          src={conversationBgmUrl}
        />

        <img
          alt=""
          className="absolute -top-[40px] left-0 h-[760px] w-full object-cover"
          fetchPriority="high"
          src={diaryBookUrl}
        />

        <Link
          aria-label="대화 화면으로 돌아가기"
          className="absolute top-[25px] left-[25px] z-30 size-[62px] drop-shadow-[0_4px_8px_rgba(0,0,0,.28)] transition hover:scale-108"
          to="/conversation"
        >
          <img
            alt=""
            className="size-full object-contain"
            src={backButtonUrl}
          />
        </Link>

        <ChildProfileSwitchButton />

        <section className="absolute top-[70px] left-[165px] flex w-[375px] flex-col items-center">
          <h1 className="text-[38px] text-[#7a5220]" style={jua}>
            나의 일기장
          </h1>

          <div className="-mt-8 flex items-center justify-center gap-4">
            <button
              aria-label="이전 달"
              className="relative z-20 -mr-[55px] h-[40px] w-[80px] shrink-0 border-0 bg-transparent p-0 drop-shadow-[0_3px_5px_rgba(60,90,20,.3)] transition hover:scale-110"
              onClick={() => moveMonth(-1)}
              type="button"
            >
              <img
                alt=""
                className="pointer-events-none size-full object-contain"
                src={monthArrowLeftUrl}
              />
            </button>
            <div className="relative z-0 h-[120px] w-[280px]">
              <img
                alt=""
                className="absolute inset-0 size-full object-fill"
                src={monthBarUrl}
              />
              <span
                className="absolute inset-0 flex items-center justify-center text-[24px] text-[#7a5220]"
                style={jua}
              >
                {formatYearMonth(year, month)}
              </span>
            </div>
            <button
              aria-label="다음 달"
              className="relative z-20 -ml-[55px] h-[40px] w-[80px] shrink-0 border-0 bg-transparent p-0 drop-shadow-[0_3px_5px_rgba(60,90,20,.3)] transition hover:scale-110"
              onClick={() => moveMonth(1)}
              type="button"
            >
              <img
                alt=""
                className="pointer-events-none size-full object-contain"
                src={monthArrowRightUrl}
              />
            </button>
          </div>

          <div className="-mt-9 flex h-[333px] w-full flex-col">
            {visibleDiaries.length ? (
              visibleDiaries.map((diary, index) => {
                const selected = diary.id === selectedDiary?.id
                const diaryDay = Number(diary.date.split('-')[2])

                return (
                  <button
                    className={`relative h-[96px] w-full shrink-0 border-0 bg-transparent p-0 text-left transition hover:brightness-104 active:scale-99 ${
                      index ? '-mt-[17px]' : ''
                    } ${
                      selected
                        ? 'z-2 scale-103 drop-shadow-[0_4px_8px_rgba(120,90,40,.35)]'
                        : 'z-1 brightness-98'
                    }`}
                    key={diary.id}
                    onClick={() => setSelectedId(diary.id)}
                    type="button"
                  >
                    <img
                      alt=""
                      className="absolute inset-0 size-full object-fill"
                      src={dateRectUrl}
                    />
                    <span className="absolute top-1/2 left-[18px] z-1 size-[58px] -translate-y-1/2">
                      <img
                        alt=""
                        className="absolute inset-0 size-full object-contain"
                        src={dateCircleUrl}
                      />
                      <span
                        className="absolute inset-0 flex items-center justify-center pb-0.5 text-[25px] text-[#8a6a34]"
                        style={jua}
                      >
                        {diaryDay}
                      </span>
                    </span>
                    <span
                      className="absolute top-1/2 right-5 left-[88px] -translate-y-1/2 truncate text-[24px] text-[#6a4a1e]"
                      style={jua}
                    >
                      {diary.title}
                    </span>
                  </button>
                )
              })
            ) : (
              <div
                className="flex h-full items-center justify-center text-center text-[22px] leading-relaxed text-[#9a7a48]"
                style={jua}
              >
                이 달에 붙인 일기가
                <br />
                아직 없어요
              </div>
            )}
          </div>

          <nav
            aria-label="일기 목록 페이지"
            className="mt-2 flex items-center justify-center gap-[7px]"
          >
            <button
              aria-label="이전 페이지"
              className="h-[56px] border-0 bg-transparent p-0 transition hover:scale-110 disabled:opacity-40"
              disabled={page === 0}
              onClick={() => setPage((current) => Math.max(0, current - 1))}
              type="button"
            >
              <img
                alt=""
                className="h-full w-auto object-contain"
                src={pagerLeftUrl}
              />
            </button>
            {getVisiblePageIndices(page, pageCount).map((index) => (
              <button
                aria-label={`${index + 1}페이지`}
                aria-current={index === page ? 'page' : undefined}
                className={`relative size-[56px] border-0 bg-transparent p-0 transition hover:scale-110 ${
                  index === page ? 'scale-112' : 'opacity-78'
                }`}
                key={index}
                onClick={() => setPage(index)}
                type="button"
              >
                <img
                  alt=""
                  className="absolute inset-0 size-full object-contain"
                  src={pagerPageUrl}
                />
                <span
                  className="absolute inset-0 flex items-center justify-center text-[25px] leading-none text-white [text-shadow:0_2px_3px_rgba(60,90,20,.55)]"
                  style={jua}
                >
                  {index + 1}
                </span>
              </button>
            ))}
            <button
              aria-label="다음 페이지"
              className="h-[56px] border-0 bg-transparent p-0 transition hover:scale-110 disabled:opacity-40"
              disabled={page >= pageCount - 1}
              onClick={() =>
                setPage((current) => Math.min(pageCount - 1, current + 1))
              }
              type="button"
            >
              <img
                alt=""
                className="h-full w-auto object-contain"
                src={pagerRightUrl}
              />
            </button>
          </nav>
        </section>

        <section className="absolute top-[90px] right-[145px] w-[600px] rotate-[-1.2deg] [animation:diary-note-in_.4s_ease_both]">
          <img
            alt=""
            className="block w-full drop-shadow-[0_12px_22px_rgba(0,0,0,.25)]"
            src={diaryNoteUrl}
          />
          <div className="absolute top-[5%] right-[13%] bottom-[14%] left-[13%] flex flex-col items-center justify-center gap-1 text-center">
            {selectedDiary ? (
              <>
                <p className="text-[25px] text-[#b08a4e]" style={jua}>
                  {formatDiaryDate(selectedDiary.date)}
                </p>
                <h2 className="text-[29px] text-[#7a5220]" style={jua}>
                  {selectedDiary.title}
                </h2>
                <div className="mt-0.5 flex flex-col gap-[7px]">
                  {selectedDiary.lines.map((line) => (
                    <p
                      className="text-[21px] leading-[1.35] text-[#5f4626]"
                      key={line}
                      style={jua}
                    >
                      {line}
                    </p>
                  ))}
                </div>
              </>
            ) : (
              <p className="text-[23px] text-[#9a7a48]" style={jua}>
                해당 날짜의 일기가 없어요
              </p>
            )}
          </div>
        </section>
      </div>
    </main>
  )
}
