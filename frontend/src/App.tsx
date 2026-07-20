function App() {
  const childFlow = ['운동 게임', '인지 게임', '캐릭터 대화', '일기 저장']
  const parentFlow = ['게임 결과', '일기 원문', '하루 코멘트', '성장 리포트']

  return (
    <main className="mx-auto min-h-screen max-w-[1120px] px-5 py-14 max-[760px]:px-3.5 max-[760px]:py-8">
      <section className="grid max-w-3xl gap-5">
        <p className="text-sm font-bold uppercase tracking-normal text-emerald-700">
          Play based growth diary
        </p>
        <h1 className="max-w-4xl text-6xl font-bold leading-tight tracking-normal text-slate-950 max-[760px]:text-4xl">
          놀이로 남기는 아이 성장 기록
        </h1>
        <p className="max-w-2xl text-lg text-slate-600">
          아이는 게임으로 놀고 캐릭터와 대화하며 하루를 남기고, 부모는 게임
          결과와 일기를 바탕으로 성장 리포트를 확인합니다.
        </p>
      </section>

      <section
        className="mt-12 grid grid-cols-2 gap-4 max-[760px]:grid-cols-1"
        aria-label="서비스 흐름"
      >
        <article className="grid gap-7 rounded-lg border border-emerald-100 border-t-[6px] border-t-emerald-500 bg-white p-7 shadow-xl shadow-emerald-950/10">
          <div>
            <p className="text-sm font-bold uppercase tracking-normal text-emerald-700">
              Child
            </p>
            <h2 className="text-2xl font-bold leading-snug text-slate-950">
              아이 화면
            </h2>
          </div>
          <ol className="grid gap-3">
            {childFlow.map((item) => (
              <li
                className="flex min-h-12 items-center rounded-lg bg-emerald-50 px-4 font-bold text-slate-800"
                key={item}
              >
                {item}
              </li>
            ))}
          </ol>
        </article>

        <article className="grid gap-7 rounded-lg border border-orange-100 border-t-[6px] border-t-orange-400 bg-white p-7 shadow-xl shadow-emerald-950/10">
          <div>
            <p className="text-sm font-bold uppercase tracking-normal text-emerald-700">
              Parent
            </p>
            <h2 className="text-2xl font-bold leading-snug text-slate-950">
              부모 화면
            </h2>
          </div>
          <ol className="grid gap-3">
            {parentFlow.map((item) => (
              <li
                className="flex min-h-12 items-center rounded-lg bg-emerald-50 px-4 font-bold text-slate-800"
                key={item}
              >
                {item}
              </li>
            ))}
          </ol>
        </article>
      </section>

      <section
        className="mt-4 flex items-center justify-between gap-6 rounded-lg border border-emerald-100 bg-white p-7 shadow-xl shadow-emerald-950/10 max-[760px]:grid"
        aria-label="프론트 개발 환경"
      >
        <div>
          <p className="text-sm font-bold uppercase tracking-normal text-emerald-700">
            Frontend stack
          </p>
          <h2 className="text-2xl font-bold leading-snug text-slate-950">
            React + TypeScript + Vite + Tailwind CSS
          </h2>
        </div>
        <p className="max-w-md text-right text-slate-600 max-[760px]:text-left">
          초기 개발 서버, 타입 체크, 빌드, 린트 실행 환경을 준비했습니다.
        </p>
      </section>
    </main>
  )
}

export default App
