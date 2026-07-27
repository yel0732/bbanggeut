const DEFAULT_VISIBLE_PAGE_COUNT = 3

/**
 * 현재 페이지를 포함해 화면에 표시할 연속된 페이지 인덱스를 반환한다.
 * 처음에는 1·2·3을 유지하고, 그 범위를 넘어가면 한 칸씩 밀린다.
 */
export function getVisiblePageIndices(
  currentPage: number,
  totalPages: number,
  visibleCount = DEFAULT_VISIBLE_PAGE_COUNT,
): number[] {
  const safeTotal = Math.max(0, totalPages)
  const count = Math.min(safeTotal, Math.max(1, visibleCount))
  const safeCurrent = Math.min(Math.max(0, currentPage), safeTotal - 1)
  const start = Math.min(
    Math.max(0, safeCurrent - count + 1),
    Math.max(0, safeTotal - count),
  )

  return Array.from({ length: count }, (_, index) => start + index)
}
