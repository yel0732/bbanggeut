import './App.css'

function App() {
  const childFlow = ['운동 게임', '인지 게임', '캐릭터 대화', '일기 저장']
  const parentFlow = ['게임 결과', '일기 원문', '하루 코멘트', '성장 리포트']

  return (
    <main className="app-shell">
      <section className="intro">
        <p className="eyebrow">Play based growth diary</p>
        <h1>놀이로 남기는 아이 성장 기록</h1>
        <p className="summary">
          아이는 게임으로 놀고 캐릭터와 대화하며 하루를 남기고, 부모는 게임
          결과와 일기를 바탕으로 성장 리포트를 확인합니다.
        </p>
      </section>

      <section className="flow-grid" aria-label="서비스 흐름">
        <article className="flow-panel child">
          <div>
            <p className="panel-kicker">Child</p>
            <h2>아이 화면</h2>
          </div>
          <ol>
            {childFlow.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ol>
        </article>

        <article className="flow-panel parent">
          <div>
            <p className="panel-kicker">Parent</p>
            <h2>부모 화면</h2>
          </div>
          <ol>
            {parentFlow.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ol>
        </article>
      </section>

      <section className="stack" aria-label="프론트 개발 환경">
        <div>
          <p className="panel-kicker">Frontend stack</p>
          <h2>React + TypeScript + Vite</h2>
        </div>
        <p>초기 개발 서버, 타입 체크, 빌드, 린트 실행 환경을 준비했습니다.</p>
      </section>
    </main>
  )
}

export default App
