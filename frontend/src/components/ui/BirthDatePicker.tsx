import { useEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']
const MONTHS = Array.from({ length: 12 }, (_, index) => index + 1)
const YEAR_PAGE_SIZE = 12

type PickerView = 'calendar' | 'month' | 'year'

type BirthDatePickerProps = {
  dialogLabel?: string
  headerLabel?: string
  maxDate: Date
  onChange: (value: string) => void
  placeholder?: string
  value: string
}

function toDateValue(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function fromDateValue(value: string) {
  const [year, month, day] = value.split('-').map(Number)
  return year && month && day ? new Date(year, month - 1, day) : null
}

function sameDay(left: Date, right: Date) {
  return toDateValue(left) === toDateValue(right)
}

function getYearPageStart(year: number, maxYear: number, minYear: number) {
  return Math.max(
    minYear,
    Math.min(
      Math.floor(year / YEAR_PAGE_SIZE) * YEAR_PAGE_SIZE,
      maxYear - YEAR_PAGE_SIZE + 1,
    ),
  )
}

export function BirthDatePicker({
  dialogLabel = '보호자 생년월일 선택',
  headerLabel = '보호자 생년월일',
  maxDate,
  onChange,
  placeholder = '연도 / 월 / 일',
  value,
}: BirthDatePickerProps) {
  const selectedDate = useMemo(() => fromDateValue(value), [value])
  const [isOpen, setIsOpen] = useState(false)
  const [view, setView] = useState<PickerView>('calendar')
  const [visibleDate, setVisibleDate] = useState(selectedDate ?? maxDate)
  const maxYear = maxDate.getFullYear()
  const minYear = maxYear - 100
  const [yearPageStart, setYearPageStart] = useState(
    getYearPageStart((selectedDate ?? maxDate).getFullYear(), maxYear, minYear),
  )
  const closeButtonRef = useRef<HTMLButtonElement>(null)

  const visibleYear = visibleDate.getFullYear()
  const visibleMonth = visibleDate.getMonth()
  const daysInMonth = new Date(visibleYear, visibleMonth + 1, 0).getDate()
  const firstWeekday = new Date(visibleYear, visibleMonth, 1).getDay()
  const calendarCells = Array.from(
    { length: Math.ceil((firstWeekday + daysInMonth) / 7) * 7 },
    (_, index) => {
      const day = index - firstWeekday + 1
      return day > 0 && day <= daysInMonth ? day : null
    },
  )

  useEffect(() => {
    if (!isOpen) return

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setIsOpen(false)
    }

    document.addEventListener('keydown', handleKeyDown)
    closeButtonRef.current?.focus()
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [isOpen])

  const openPicker = () => {
    const initialDate = selectedDate ?? maxDate
    setVisibleDate(initialDate)
    setYearPageStart(
      getYearPageStart(initialDate.getFullYear(), maxYear, minYear),
    )
    setView('calendar')
    setIsOpen(true)
  }

  const changeMonth = (offset: number) => {
    const nextDate = new Date(visibleYear, visibleMonth + offset, 1)
    const latestMonth = new Date(maxDate.getFullYear(), maxDate.getMonth(), 1)
    if (nextDate > latestMonth || nextDate.getFullYear() < minYear) return
    setVisibleDate(nextDate)
  }

  const chooseYear = (year: number) => {
    const month =
      year === maxYear
        ? Math.min(visibleMonth, maxDate.getMonth())
        : visibleMonth
    setVisibleDate(new Date(year, month, 1))
    setView('month')
  }

  const chooseMonth = (month: number) => {
    if (visibleYear === maxYear && month > maxDate.getMonth()) return
    setVisibleDate(new Date(visibleYear, month, 1))
    setView('calendar')
  }

  const chooseDay = (day: number) => {
    const nextDate = new Date(visibleYear, visibleMonth, day)
    if (nextDate > maxDate) return
    onChange(toDateValue(nextDate))
    setIsOpen(false)
  }

  return (
    <>
      <button
        aria-haspopup="dialog"
        className="flex h-full w-full items-center justify-between bg-transparent px-[3%] text-left text-[clamp(13px,3.8vw,18px)] text-[#5a4a2a] outline-none focus-visible:ring-2 focus-visible:ring-[#6e9d45]"
        onClick={openPicker}
        type="button"
      >
        <span className={selectedDate ? '' : 'text-[#9db089]'}>
          {selectedDate
            ? `${selectedDate.getFullYear()}년 ${selectedDate.getMonth() + 1}월 ${selectedDate.getDate()}일`
            : placeholder}
        </span>
        <CalendarIcon />
      </button>

      {isOpen
        ? createPortal(
            <div
              className="fixed inset-0 z-[80] flex items-center justify-center bg-[#102014]/65 p-4 backdrop-blur-[3px]"
              onMouseDown={(event) => {
                if (event.target === event.currentTarget) setIsOpen(false)
              }}
            >
              <section
                aria-label={dialogLabel}
                aria-modal="true"
                className="w-full max-w-[390px] overflow-hidden rounded-[30px] border-[5px] border-[#79552d] bg-[#fff8dc] shadow-[0_24px_70px_rgba(7,24,12,0.45),inset_0_0_0_3px_#e8c879]"
                role="dialog"
                style={{ fontFamily: "'Jua', sans-serif" }}
              >
                <header className="relative bg-[linear-gradient(135deg,#6fa844,#4f8132)] px-6 pt-5 pb-4 text-white">
                  <div className="pointer-events-none absolute -top-5 -right-3 size-20 rounded-full bg-[#a9d66f]/35" />
                  <p className="text-sm text-[#eaffd7]">{headerLabel}</p>
                  <div className="mt-1 flex items-center gap-2 text-2xl">
                    <button
                      className="rounded-xl px-2 py-1 hover:bg-white/15 focus-visible:outline-2 focus-visible:outline-white"
                      onClick={() => setView('year')}
                      type="button"
                    >
                      {visibleYear}년
                    </button>
                    <button
                      className="rounded-xl px-2 py-1 hover:bg-white/15 focus-visible:outline-2 focus-visible:outline-white"
                      onClick={() => setView('month')}
                      type="button"
                    >
                      {visibleMonth + 1}월
                    </button>
                  </div>
                  <button
                    aria-label="달력 닫기"
                    className="absolute top-4 right-4 grid size-10 place-items-center rounded-full bg-[#355d26]/60 text-2xl hover:bg-[#2b4e1f] focus-visible:outline-2 focus-visible:outline-white"
                    onClick={() => setIsOpen(false)}
                    ref={closeButtonRef}
                    type="button"
                  >
                    ×
                  </button>
                </header>

                <div className="min-h-[350px] px-5 pt-4 pb-5 text-[#5a452b]">
                  {view === 'calendar' ? (
                    <>
                      <div className="mb-3 flex items-center justify-between">
                        <button
                          aria-label="이전 달"
                          className="grid size-10 place-items-center rounded-full text-2xl hover:bg-[#e8efcf] focus-visible:outline-2 focus-visible:outline-[#5b8c38]"
                          onClick={() => changeMonth(-1)}
                          type="button"
                        >
                          ‹
                        </button>
                        <p className="text-lg">날짜를 골라주세요</p>
                        <button
                          aria-label="다음 달"
                          className="grid size-10 place-items-center rounded-full text-2xl hover:bg-[#e8efcf] focus-visible:outline-2 focus-visible:outline-[#5b8c38] disabled:opacity-25"
                          disabled={
                            visibleYear === maxYear &&
                            visibleMonth === maxDate.getMonth()
                          }
                          onClick={() => changeMonth(1)}
                          type="button"
                        >
                          ›
                        </button>
                      </div>
                      <div className="grid grid-cols-7 text-center text-sm text-[#7d8b69]">
                        {WEEKDAYS.map((weekday) => (
                          <span
                            className="py-2 first:text-[#cc735d]"
                            key={weekday}
                          >
                            {weekday}
                          </span>
                        ))}
                        {calendarCells.map((day, index) => {
                          if (!day) return <span key={`empty-${index}`} />
                          const date = new Date(visibleYear, visibleMonth, day)
                          const isSelected = selectedDate
                            ? sameDay(date, selectedDate)
                            : false
                          const isDisabled = date > maxDate
                          return (
                            <button
                              aria-label={`${visibleYear}년 ${visibleMonth + 1}월 ${day}일`}
                              aria-pressed={isSelected}
                              className="mx-auto my-0.5 grid size-10 place-items-center rounded-full hover:bg-[#dfecc0] focus-visible:outline-2 focus-visible:outline-[#5b8c38] disabled:text-[#c7c9bd] aria-pressed:bg-[#63983e] aria-pressed:text-white"
                              disabled={isDisabled}
                              key={day}
                              onClick={() => chooseDay(day)}
                              type="button"
                            >
                              {day}
                            </button>
                          )
                        })}
                      </div>
                    </>
                  ) : null}

                  {view === 'year' ? (
                    <>
                      <div className="mb-4 flex items-center justify-between">
                        <button
                          aria-label="이전 연도 목록"
                          className="grid size-10 place-items-center rounded-full text-2xl hover:bg-[#e8efcf] disabled:opacity-25"
                          disabled={yearPageStart <= minYear}
                          onClick={() =>
                            setYearPageStart((year) => year - YEAR_PAGE_SIZE)
                          }
                          type="button"
                        >
                          ‹
                        </button>
                        <p className="text-lg">
                          {Math.max(yearPageStart, minYear)}–
                          {Math.min(
                            yearPageStart + YEAR_PAGE_SIZE - 1,
                            maxYear,
                          )}
                          년
                        </p>
                        <button
                          aria-label="다음 연도 목록"
                          className="grid size-10 place-items-center rounded-full text-2xl hover:bg-[#e8efcf] disabled:opacity-25"
                          disabled={yearPageStart + YEAR_PAGE_SIZE > maxYear}
                          onClick={() =>
                            setYearPageStart((year) => year + YEAR_PAGE_SIZE)
                          }
                          type="button"
                        >
                          ›
                        </button>
                      </div>
                      <div className="grid grid-cols-3 gap-2">
                        {Array.from(
                          { length: YEAR_PAGE_SIZE },
                          (_, index) => yearPageStart + index,
                        )
                          .filter((year) => year >= minYear && year <= maxYear)
                          .map((year) => (
                            <button
                              aria-pressed={year === visibleYear}
                              className="rounded-2xl py-3 hover:bg-[#dfecc0] focus-visible:outline-2 focus-visible:outline-[#5b8c38] aria-pressed:bg-[#63983e] aria-pressed:text-white"
                              key={year}
                              onClick={() => chooseYear(year)}
                              type="button"
                            >
                              {year}년
                            </button>
                          ))}
                      </div>
                    </>
                  ) : null}

                  {view === 'month' ? (
                    <div className="grid grid-cols-3 gap-2 pt-5">
                      {MONTHS.map((month) => {
                        const isDisabled =
                          visibleYear === maxYear &&
                          month - 1 > maxDate.getMonth()
                        return (
                          <button
                            aria-pressed={month - 1 === visibleMonth}
                            className="rounded-2xl py-4 hover:bg-[#dfecc0] focus-visible:outline-2 focus-visible:outline-[#5b8c38] disabled:text-[#c7c9bd] aria-pressed:bg-[#63983e] aria-pressed:text-white"
                            disabled={isDisabled}
                            key={month}
                            onClick={() => chooseMonth(month - 1)}
                            type="button"
                          >
                            {month}월
                          </button>
                        )
                      })}
                    </div>
                  ) : null}
                </div>
              </section>
            </div>,
            document.body,
          )
        : null}
    </>
  )
}

function CalendarIcon() {
  return (
    <svg
      aria-hidden="true"
      className="size-5 shrink-0 text-[#6f8a5e]"
      fill="none"
      stroke="currentColor"
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth="2"
      viewBox="0 0 24 24"
    >
      <path d="M7 3v3M17 3v3M4 9h16M5 5h14a1 1 0 0 1 1 1v14H4V6a1 1 0 0 1 1-1Z" />
      <path d="M8 13h2M14 13h2M8 17h2M14 17h2" />
    </svg>
  )
}
