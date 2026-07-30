import { createContext, useContext } from 'react'

export type ParentBgmContextValue = {
  isBgmPlaying: boolean
  toggleBgm: () => Promise<void>
}

export const ParentBgmContext = createContext<ParentBgmContextValue | null>(
  null,
)

export function useParentBgm() {
  const context = useContext(ParentBgmContext)
  if (!context) {
    throw new Error('useParentBgm은 ParentBgmLayout 안에서만 사용할 수 있어요.')
  }
  return context
}
