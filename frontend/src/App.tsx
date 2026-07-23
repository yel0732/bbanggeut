import { Suspense } from 'react'
import { RouterProvider } from 'react-router-dom'

import { router } from '@/routes/router'

function App() {
  return (
    <Suspense fallback={<PageLoader />}>
      <RouterProvider router={router} />
    </Suspense>
  )
}

function PageLoader() {
  return (
    <div
      aria-label="화면을 준비하고 있어요"
      className="grid h-screen w-screen place-items-center bg-[#102014] text-white"
      role="status"
    >
      <div className="flex flex-col items-center gap-3">
        <span className="size-10 animate-spin rounded-full border-4 border-[#a9d66f]/35 border-t-[#a9d66f]" />
        <span>잠시만 기다려 주세요</span>
      </div>
    </div>
  )
}

export default App
