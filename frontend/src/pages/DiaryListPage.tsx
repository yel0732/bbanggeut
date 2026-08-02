import { useEffect, useMemo, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'

import dateCircleUrl from '@/assets/images/diary-list/date-circle.webp'
import diaryBookUrl from '@/assets/images/diary-list/diary-open-book.webp'
import diaryNextButtonUrl from '@/assets/images/diary-list/diary-next-btn.webp'
import diaryPrevButtonUrl from '@/assets/images/diary-list/diary-prev-btn.webp'
import monthArrowLeftUrl from '@/assets/images/diary-list/month-arrow-left.webp'
import monthArrowRightUrl from '@/assets/images/diary-list/month-arrow-right.webp'
import monthBarUrl from '@/assets/images/diary-list/month-bar.webp'
import diaryNoteUrl from '@/assets/images/diary/diary-note.webp'
import backButtonUrl from '@/assets/images/game-entry/left-btn.webp'
import { ChildProfileSwitchButton } from '@/components/ChildProfileSwitchButton'
import { getChildDiaries } from '@/api/diaries'
import { useConversationBgm } from '@/hooks/useConversationBgm'
import { useFitScale } from '@/hooks/useFitScale'
import { loadActiveChildProfileId } from '@/lib/childProfiles'

type DiaryEntry = {
  id: number
  date: string
  title: string
  lines: string[]
}

const jua = { fontFamily: "'Jua', sans-serif" } as const

const WEEKDAY_LABELS = ['일', '월', '화', '수', '목', '금', '토'] as const

/** 그 달의 날 수. month는 1~12다. */
function daysInMonth(year: number, month: number) {
  return new Date(year, month, 0).getDate()
}

/** 그 달 1일이 무슨 요일인지(0=일). 달력 앞의 빈 칸 수이기도 하다. */
function firstWeekdayOfMonth(year: number, month: number) {
  return new Date(year, month - 1, 1).getDay()
}

function formatYearMonth(year: number, month: number) {
  return `${year}년 ${month}월`
}

function formatDiaryDate(date: string) {
  const [, month, day] = date.split('-').map(Number)
  return `${month}월 ${day}일`
}

/**
 * 뒤로 가기로 돌아갈 화면.
 *
 * 일기장은 아이 홈과 대화 화면 두 곳에서 열린다. 들어온 곳을 함께 받아 그리로
 * 돌려보낸다. 값이 없으면(주소를 직접 열었거나 새로고침) 아이 홈으로 보낸다 —
 * 대화 화면으로 보내면 아이가 하지도 않은 대화 준비 화면이 뜬다.
 */
function useBackTo(): string {
  const { state } = useLocation()
  const from = (state as { from?: unknown } | null)?.from
  return typeof from === 'string' && from.startsWith('/') ? from : '/child'
}

export function DiaryListPage() {
  const canvasRef = useFitScale<HTMLDivElement>()
  const backTo = useBackTo()
  const { setVolume } = useConversationBgm()
  /*
    처음 열면 이번 달을 보여 준다.

    고정값으로 두면 달이 넘어가는 순간 방금 붙인 일기가 사라진 것처럼 보인다.
    목록은 달과 무관하게 다 받아 오고 화면에서만 걸러 내므로, 보고 있는 달이
    어긋나면 있는 일기도 안 보인다.
  */
  const [year, setYear] = useState(() => new Date().getFullYear())
  const [month, setMonth] = useState(() => new Date().getMonth() + 1)
  const [diaries, setDiaries] = useState<DiaryEntry[]>([])
  /** 달력에서 고른 날. 그 달에 일기가 하나도 없으면 null이다. */
  const [selectedDay, setSelectedDay] = useState<number | null>(null)
  /** 고른 날의 몇 번째 일기를 보고 있는지 */
  const [diaryIndex, setDiaryIndex] = useState(0)

  useEffect(() => {
    // 대화 화면에서 목소리에 맞춰 낮춰 둔 볼륨을 이 화면의 기본값으로 되돌린다.
    setVolume(0.22)
  }, [setVolume])

  useEffect(() => {
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
      })
      .catch(() => {
        if (!active) return
        setDiaries([])
      })
    return () => {
      active = false
    }
  }, [])

  /*
    날짜별로 묶어 둔다.

    아이는 대화할 때마다 일기를 붙일 수 있어 하루에 여러 개가 쌓인다. 달력 한 칸이
    하루를 맡고, 그 칸을 누르면 그날 것들을 차례로 넘겨 본다.

    만든 순서대로 보여 주려고 id 오름차순으로 세운다. 서버가 주는 순서에 기대면
    같은 날 일기의 앞뒤가 요청마다 달라질 수 있다.
  */
  const diariesByDay = useMemo(() => {
    const grouped = new Map<number, DiaryEntry[]>()
    for (const diary of diaries) {
      const [diaryYear, diaryMonth, diaryDay] = diary.date
        .split('-')
        .map(Number)
      if (diaryYear !== year || diaryMonth !== month) continue
      const bucket = grouped.get(diaryDay)
      if (bucket) bucket.push(diary)
      else grouped.set(diaryDay, [diary])
    }
    for (const bucket of grouped.values()) {
      bucket.sort((a, b) => a.id - b.id)
    }
    return grouped
  }, [diaries, month, year])

  /*
    처음 보여 줄 날을 고른다.

    오늘 쓴 일기가 있으면 오늘, 없으면 그 달에서 가장 최근에 쓴 날이다. 빈 화면으로
    맞이하면 아이는 무엇을 눌러야 할지 모른다.
  */
  useEffect(() => {
    if (selectedDay !== null && diariesByDay.has(selectedDay)) return

    const today = new Date()
    const isThisMonth =
      today.getFullYear() === year && today.getMonth() + 1 === month
    const days = [...diariesByDay.keys()].sort((a, b) => a - b)
    const nextDay =
      isThisMonth && diariesByDay.has(today.getDate())
        ? today.getDate()
        : (days[days.length - 1] ?? null)

    setSelectedDay(nextDay)
    setDiaryIndex(0)
  }, [diariesByDay, month, selectedDay, year])

  const selectedDayDiaries =
    selectedDay === null ? [] : (diariesByDay.get(selectedDay) ?? [])
  const selectedDiary = selectedDayDiaries[diaryIndex] ?? null
  const hasPrevDiary = diaryIndex > 0
  const hasNextDiary = diaryIndex < selectedDayDiaries.length - 1

  const totalDays = daysInMonth(year, month)
  const leadingBlanks = firstWeekdayOfMonth(year, month)

  const moveMonth = (amount: number) => {
    const next = new Date(year, month - 1 + amount, 1)
    setYear(next.getFullYear())
    setMonth(next.getMonth() + 1)
    setSelectedDay(null)
    setDiaryIndex(0)
  }

  const openDay = (day: number) => {
    setSelectedDay(day)
    setDiaryIndex(0)
  }

  return (
    <main className="flex h-screen w-screen items-center justify-center overflow-hidden bg-[radial-gradient(120%_120%_at_50%_20%,_#2a3320_0%,_#131a0e_100%)]">
      <div
        className="relative h-[720px] w-[1280px] shrink-0 overflow-hidden"
        ref={canvasRef}
      >
        <img
          alt=""
          className="absolute -top-[40px] left-0 h-[760px] w-full object-cover"
          fetchPriority="high"
          src={diaryBookUrl}
        />

        <Link
          aria-label="이전 화면으로 돌아가기"
          className="absolute top-[25px] left-[25px] z-30 size-[62px] drop-shadow-[0_4px_8px_rgba(0,0,0,.28)] transition hover:scale-108"
          to={backTo}
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

          {/*
            달력.

            한 칸이 하루다. 일기가 있는 날에만 동그라미를 씌우고 오른쪽 위에 개수를
            달아, 아이가 훑기만 해도 어느 날에 이야기가 쌓였는지 보이게 한다.

            줄 수는 달마다 다르다(4~6줄). 칸 높이를 고정하고 남는 줄은 비워 두면
            달을 넘길 때 아래 요소가 들썩이지 않는다.
          */}
          <div className="-mt-5 h-[333px] w-full">
            <div className="grid grid-cols-7 gap-x-[6px]">
              {WEEKDAY_LABELS.map((label) => (
                <span
                  className="text-center text-[24px] text-[#a98a58]"
                  key={label}
                  style={jua}
                >
                  {label}
                </span>
              ))}
            </div>

            <div className="mt-1 grid grid-cols-7 gap-x-[6px] gap-y-[3px]">
              {Array.from({ length: leadingBlanks }, (_, index) => (
                <span key={`blank-${index}`} />
              ))}
              {Array.from({ length: totalDays }, (_, index) => {
                const day = index + 1
                const count = diariesByDay.get(day)?.length ?? 0
                const selected = day === selectedDay

                if (count === 0) {
                  return (
                    <span
                      className="flex h-[47px] items-center justify-center text-[24px] text-[#c3ab84]"
                      key={day}
                      style={jua}
                    >
                      {day}
                    </span>
                  )
                }

                return (
                  <button
                    aria-label={`${month}월 ${day}일 일기 ${count}개`}
                    aria-pressed={selected}
                    className={`relative h-[47px] border-0 bg-transparent p-0 transition hover:scale-108 ${
                      selected
                        ? 'scale-108 drop-shadow-[0_4px_7px_rgba(120,90,40,.4)]'
                        : ''
                    }`}
                    key={day}
                    onClick={() => openDay(day)}
                    type="button"
                  >
                    <img
                      alt=""
                      className="absolute top-1/2 left-1/2 size-[46px] -translate-x-1/2 -translate-y-1/2 object-contain"
                      src={dateCircleUrl}
                      style={{ opacity: selected ? 1 : 0.82 }}
                    />
                    <span
                      className="absolute inset-0 flex items-center justify-center text-[24px] text-[#6a4a1e]"
                      style={jua}
                    >
                      {day}
                    </span>
                    {/* 개수 뱃지. 하루에 여러 개가 쌓였다는 것을 한눈에 알린다. */}
                    <span
                      className="absolute top-0 right-0 flex size-[21px] items-center justify-center rounded-full border-2 border-[#fdf6e3] bg-[#7cb342] text-[12px] leading-none text-white"
                      style={jua}
                    >
                      {count}
                    </span>
                  </button>
                )
              })}
            </div>
          </div>
        </section>

        <section className="absolute top-[90px] right-[145px] w-[600px] rotate-[-1.2deg] [animation:diary-note-in_.4s_ease_both]">
          <img
            alt=""
            className="block w-full drop-shadow-[0_12px_22px_rgba(0,0,0,.25)]"
            src={diaryNoteUrl}
          />
          {/*
            그날의 일기 넘기기.

            종이 안쪽 여백(좌우 13%)에 세워 글을 가리지 않는다. 넘길 것이 있을
            때만 나타난다 — 눌러도 아무 일이 없는 버튼은 아이를 헷갈리게 한다.
          */}
          {hasPrevDiary ? (
            <button
              aria-label="이전 일기"
              className="absolute top-[55%] left-[8%] z-10 size-[50px] -translate-y-1/2 border-0 bg-transparent p-0 drop-shadow-[0_4px_7px_rgba(60,90,20,.32)] transition hover:scale-110 active:scale-95"
              onClick={() => setDiaryIndex((current) => current - 1)}
              type="button"
            >
              <img
                alt=""
                className="size-full object-contain"
                src={diaryPrevButtonUrl}
              />
            </button>
          ) : null}
          {hasNextDiary ? (
            <button
              aria-label="다음 일기"
              className="absolute top-[55%] right-[9%] z-10 size-[50px] -translate-y-1/2 border-0 bg-transparent p-0 drop-shadow-[0_4px_7px_rgba(60,90,20,.32)] transition hover:scale-110 active:scale-95"
              onClick={() => setDiaryIndex((current) => current + 1)}
              type="button"
            >
              <img
                alt=""
                className="size-full object-contain"
                src={diaryNextButtonUrl}
              />
            </button>
          ) : null}

          <div className="absolute top-[5%] right-[13%] bottom-[14%] left-[13%] flex flex-col items-center justify-center gap-1 text-center">
            {selectedDiary ? (
              <>
                <p className="text-[25px] text-[#b08a4e]" style={jua}>
                  {formatDiaryDate(selectedDiary.date)}
                  {selectedDayDiaries.length > 1
                    ? ` (${diaryIndex + 1}/${selectedDayDiaries.length})`
                    : ''}
                </p>
                <h2 className="text-[29px] text-[#7a5220]" style={jua}>
                  {selectedDiary.title}
                </h2>
                <div className="mt-0.5 flex flex-col gap-[7px]">
                  {selectedDiary.lines.map((line) => (
                    <p
                      className="text-[18.5px] leading-[1.35] text-[#5f4626]"
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
                달력에서 일기가 있는 날을
                <br />
                눌러 보세요
              </p>
            )}
          </div>
        </section>
      </div>
    </main>
  )
}
