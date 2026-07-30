import { createContext, useContext } from 'react'

export type AdventureBgmContextValue = {
  isBgmPlaying: boolean
  playBgm: () => Promise<void>
  toggleBgm: () => Promise<void>
}

export const AdventureBgmContext =
  createContext<AdventureBgmContextValue | null>(null)

export function useAdventureBgm() {
  const context = useContext(AdventureBgmContext)
  if (!context) {
    throw new Error(
      'useAdventureBgm은 AdventureBgmLayout 안에서만 사용할 수 있어요.',
    )
  }
  return context
}
