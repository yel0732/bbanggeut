import { Suspense } from 'react'
import { RouterProvider } from 'react-router-dom'

import { useButtonClickSound } from '@/hooks/useButtonClickSound'
import { router } from '@/routes/router'

function App() {
  useButtonClickSound()

  return (
    <Suspense fallback={<PageLoader />}>
      <RouterProvider router={router} />
    </Suspense>
  )
}

const jua = { fontFamily: "'Jua', sans-serif" } as const

function PageLoader() {
  return (
    <div
      aria-label="화면을 준비하고 있어요"
      className="grid h-screen w-screen place-items-center bg-[radial-gradient(120%_120%_at_50%_20%,_#12241a_0%,_#0b140e_100%)] text-white"
      role="status"
    >
      <div className="flex flex-col items-center gap-4">
        <span className="size-10 animate-spin rounded-full border-4 border-[#a9d66f]/35 border-t-[#a9d66f]" />
        <span className="text-[22px]" style={jua}>
          잠시만 기다려 주세요
        </span>
      </div>
    </div>
  )
}

export default App
