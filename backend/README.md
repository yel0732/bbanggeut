# Backend

Spring Boot 4.1, Java 21 기반 API 서버입니다.

## 검증

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

## Conversation Redis Stream

FastAPI가 `conversation-events` Stream에 발행한 확정 대화 이벤트를
`spring-conversation-consumers` Consumer Group으로 처리합니다. 기본값은 비활성화이며,
Redis가 준비된 환경에서 다음 환경 변수로 활성화합니다.

```text
CONVERSATION_STREAM_ENABLED=true
CONVERSATION_STREAM_RECOVERY_ENABLED=true
```

세부 poll·pending 복구 설정은 `src/main/resources/application.yml`의
`conversation.stream` 항목을 기준으로 합니다.
