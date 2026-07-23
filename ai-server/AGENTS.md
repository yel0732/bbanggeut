# AI Server Agent Instructions

`ai-server/`에서 작업하는 Codex 에이전트는 구현 전에 다음 문서를 읽습니다.

1. `../docs/AI_CONVENTIONS.md`
2. `../docs/diary/README.md`
3. 현재 Jira Epic과 Task
4. Task와 관련된 DIARY 계약 문서

핵심 규칙:

- 구현은 현재 Jira Task 범위와 `ai-server/` 안으로 제한합니다.
- FastAPI에 MySQL 접근이나 사용자 Access Token 처리를 추가하지 않습니다.
- Partial STT와 원본 Audio를 저장하지 않습니다.
- 외부 JSON은 계약의 camelCase, Python 코드는 snake_case를 사용합니다.
- 세션별 상태·Queue·Task를 격리하고 취소·Cleanup을 테스트합니다.
- 실제 아동 데이터, 전체 STT·Prompt·LLM 응답, Secret을 코드나 로그에 남기지 않습니다.
- 계약을 변경하면 관련 DIARY 문서를 같은 변경에서 갱신합니다.

기본 검증:

```powershell
.\.venv\Scripts\python.exe -m pytest
.\.venv\Scripts\python.exe -m compileall -q app tests
```

