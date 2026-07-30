import { createContext, useContext } from 'react'

export type ConversationBgmContextValue = {
  isBgmPlaying: boolean
  setVolume: (volume: number) => void
  toggleBgm: () => Promise<void>
}

export const ConversationBgmContext =
  createContext<ConversationBgmContextValue | null>(null)

export function useConversationBgm() {
  const context = useContext(ConversationBgmContext)
  if (!context) {
    throw new Error(
      'useConversationBgm은 ConversationBgmLayout 안에서만 사용할 수 있어요.',
    )
  }
  return context
}
