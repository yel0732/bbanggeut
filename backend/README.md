# Backend

놀이 기반 아이 성장 기록 서비스의 백엔드 API 서버입니다.
**Java 21 + Spring Boot 4.1** 기반으로 구축되었으며, **Gradle**로 빌드 및 의존성 관리를 수행합니다.

- 프론트엔드: [`../frontend`](../frontend) (React 19 + TypeScript + Vite)
- AI Realtime 서버: [`../ai-server`](../ai-server) (FastAPI, WebRTC/STT/LLM/TTS)

## 🧾 프로젝트 개요

아이는 게임과 캐릭터 대화로 하루를 일기로 남기고, 부모는 게임 결과와 일기 기반 리포트를 확인하는
유아 통합 교육 플랫폼입니다. 이 저장소는 그 백엔드로 **회원/인증, 자녀 프로필, 게임 세션 기록,
실시간 대화(Realtime) 세션 관리, 일간 리포트·알림, 아이 일기** 등 핵심 도메인 기능을 제공합니다.

DB 소유권은 이 Spring 서버에 있으며, FastAPI(AI 서버)는 MySQL에 직접 접근하지 않고
REST · Redis Stream · Realtime Ticket 계약으로만 통신합니다.

## 📁 주요 폴더 구조

도메인 우선(DDD 스타일)으로 패키지를 나눕니다. 각 도메인 안에
`controller / service / repository / entity / dto` 를 둡니다.

```
backend/
├── src/
│   ├── main/
│   │   ├── java/com/A501/backend/
│   │   │   ├── BackendApplication.java
│   │   │   ├── config/                 # 스프링 설정 (Security, CORS, WebClient 등)
│   │   │   ├── common/                 # 공통 모듈
│   │   │   │   ├── entity/             # BaseTimeEntity 등 공통 엔티티
│   │   │   │   ├── exception/          # ErrorCode + BusinessException + GlobalExceptionHandler
│   │   │   │   ├── storage/            # 프로필 이미지 저장소 (local / S3)
│   │   │   │   └── util/
│   │   │   └── domain/                 # 도메인별 비즈니스 로직
│   │   │       ├── auth/               # 회원/인증, JWT, Google OAuth, PIN
│   │   │       ├── child/              # 자녀 프로필
│   │   │       ├── childdiary/         # 아이 일기
│   │   │       ├── conversation/       # 실시간 대화 세션·턴·이벤트
│   │   │       ├── game/               # 게임 세션 및 결과 (Dobble / Fruit Ninja / 분리수거 / 낱말 맞추기)
│   │   │       └── report/             # 일간 리포트·알림
│   │   └── resources/
│   │       ├── application.yml         # 프로필별 설정 (local / prod)
│   │       └── db/migration/           # Flyway 마이그레이션 (V1 ~)
│   └── test/                           # 테스트 코드 (JUnit)
├── build.gradle                        # Gradle 빌드 설정
├── settings.gradle                     # Gradle 프로젝트 설정
├── SCHEMA                              # 사람이 읽는 스키마 참고 문서
└── README.md
```

## ⚙️ 기술 스택

**Language / Framework**

![Java](https://img.shields.io/badge/Java-21-007396?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring_Security-JWT_+_OAuth2-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white)
![Spring Batch](https://img.shields.io/badge/Spring_Batch-6.x-6DB33F?style=for-the-badge&logo=spring&logoColor=white)

**Persistence**

![MySQL](https://img.shields.io/badge/MySQL-8.x-4479A1?style=for-the-badge&logo=mysql&logoColor=white)
![H2](https://img.shields.io/badge/H2-local-1E88E5?style=for-the-badge&logo=h2database&logoColor=white)
![JPA](https://img.shields.io/badge/JPA-Hibernate-59666C?style=for-the-badge&logo=hibernate&logoColor=white)
![Flyway](https://img.shields.io/badge/Flyway-migration-CC0200?style=for-the-badge&logo=flyway&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-Stream-FF4438?style=for-the-badge&logo=redis&logoColor=white)

**Infra / Docs**

![AWS S3](https://img.shields.io/badge/AWS_S3-storage-569A31?style=for-the-badge&logo=amazons3&logoColor=white)
![Swagger](https://img.shields.io/badge/Swagger-springdoc_OpenAPI-85EA2D?style=for-the-badge&logo=swagger&logoColor=black)
![Prometheus](https://img.shields.io/badge/Prometheus-Actuator-E6522C?style=for-the-badge&logo=prometheus&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-build-02303A?style=for-the-badge&logo=gradle&logoColor=white)

## 🔐 인증 및 보안

- **JWT 기반 인증**: Access Token + HttpOnly Refresh Token 쿠키. Refresh Token은 SHA-256 해시로만 저장하고, rotation·재사용 감지를 지원합니다.
- **소셜 로그인**: Google OAuth2 (`AuthProvider = LOCAL | GOOGLE`).
- **PIN 보호**: 부모 화면 진입 등 민감 동작에 PIN 검증. 5회 실패 시 5분 잠금.
- **Realtime Ticket**: 사용자 Access Token은 AI 서버로 보내지 않습니다. Realtime 접속은 Spring이 발급한 **일회용 Ticket(JWT)** 을 FastAPI가 JWKS로 검증합니다.
- 원본 오디오·Partial STT·전체 Transcript·프롬프트는 저장·로그에 남기지 않습니다.

## 📦 환경 설정

- `application.yml` 하나에 `local` / `prod` 프로필 문서가 함께 들어 있습니다. 기본 프로필은 **`local`** 입니다.
- 로컬 실행 시 `backend/.env` 파일을 환경변수처럼 읽습니다(`spring.config.import: optional:file:./.env[.properties]`). 파일이 없으면 무시하고, 배포 환경에서는 컨테이너 환경변수가 우선합니다.
- `.env` 는 **`.properties` 형식으로 파싱**되므로 값에 따옴표를 쓰지 않습니다. 시크릿은 커밋하지 않습니다.

### `.env` 예시 (local)

`local` 프로필은 H2 인메모리 DB로 기동하므로 별도 DB 설정 없이도 실행됩니다.
대화 파이프라인·AI 서버 연동을 함께 확인하려면 아래 값을 채웁니다.

```properties
# 실행 프로필 (미지정 시 local)
SPRING_PROFILES_ACTIVE=local

# JWT / OAuth
JWT_SECRET=local-dev-only-change-me
GOOGLE_CLIENT_ID=

# Redis (대화 이벤트 스트림·Realtime 컨텍스트)
REDIS_HOST=127.0.0.1
REDIS_PORT=6379

# AI 서버 (FastAPI)
AI_SERVER_BASE_URL=http://127.0.0.1:8000

# S3 (프로필 업로드 사용 시에만. 기본 provider=local 은 메모리 가짜 저장소)
STORAGE_PROVIDER=local
# S3_UPLOAD_BUCKET=
# AWS_REGION=ap-northeast-2
```

### `prod` 추가 환경변수

`prod` 프로필은 MySQL과 시크릿을 환경변수로 주입해야 합니다.

```properties
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:mysql://...
DB_USERNAME=
DB_PASSWORD=
CORS_ALLOWED_ORIGINS=https://...
# Realtime Ticket 서명 키 (RSA PEM)
REALTIME_TICKET_PRIVATE_KEY_PEM=
REALTIME_TICKET_PUBLIC_KEY_PEM=
```

## 🏁 실행 방법

Windows PowerShell 기준입니다(`gradlew.bat`, macOS/Linux는 `./gradlew`).

```powershell
# 빌드
.\gradlew.bat clean build

# 애플리케이션 실행 (기본 local 프로필, H2 인메모리)
.\gradlew.bat bootRun
```

- IDE에서 `BackendApplication` 을 직접 실행해도 됩니다.
- `bootRun` 은 대화 파이프라인이 정상 동작하도록 `CONVERSATION_STREAM_ENABLED`, `CONVERSATION_STREAM_RECOVERY_ENABLED`, `REALTIME_RECONCILER_ENABLED` 를 켜고 뜹니다(디버깅 시 필요하면 조정).
- 전체 스택은 저장소 루트의 `compose.yml`(mysql · redis · ai-server · backend · nginx)로 함께 띄울 수 있습니다.

## 🧪 검증

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

특정 도메인만 테스트: `.\gradlew.bat test --tests "com.A501.backend.domain.child.*"`

## 📖 API 문서

`local` 프로필에서 Swagger UI가 열립니다(`prod` 는 비활성화).

- Swagger UI: <http://localhost:8080/swagger-ui/index.html>
- H2 Console: <http://localhost:8080/h2-console> (JDBC URL `jdbc:h2:mem:a501;MODE=MySQL`, user `sa`)

## 🔍 주요 기능

- ✅ JWT + Google OAuth2 기반 로그인 / 회원가입, Refresh Token rotation, 회원 탈퇴(soft delete + hard delete 스케줄러)
- ✅ 자녀 프로필 CRUD, 프로필 이미지(프리셋 / 웹캠 업로드 → S3 presigned URL), PIN 보호
- ✅ 실시간 대화(Realtime) 세션·턴 기록, Ticket 발급, 세션 격리/정리(Reconciler)
- ✅ 게임 세션 및 결과 저장(Dobble·Fruit Ninja·분리수거·낱말 맞추기)
- ✅ 일간 리포트 자동 생성(Spring Batch + 스케줄러) 및 재생성, SSE 기반 알림
- ✅ 아이 일기 생성/조회
- ✅ Actuator / Prometheus 메트릭 노출(`/actuator/prometheus`)

## 🔄 Conversation Redis Stream

FastAPI가 `conversation-events` Stream에 발행한 확정 대화 이벤트를
`spring-conversation-consumers` Consumer Group으로 처리합니다. 기본값은 비활성화이며,
Redis가 준비된 환경에서 다음 환경 변수로 활성화합니다.

```properties
CONVERSATION_STREAM_ENABLED=true
CONVERSATION_STREAM_RECOVERY_ENABLED=true
```

세부 poll · pending 복구 설정은 `application.yml` 의 `conversation.stream` 항목을 기준으로 합니다.
발행 성공이 곧 MySQL 저장 완료를 의미하지는 않습니다(멱등 처리 + pending 복구로 보장).

## 🗄️ DB 스키마 / 마이그레이션

- 운영 스키마 변경은 **Flyway 마이그레이션**(`src/main/resources/db/migration/`)으로만 적용합니다.
- `local`(H2)은 `ddl-auto=create-drop` 로 엔티티에서 매번 생성하고, `prod`(MySQL)은 Flyway 적용 후 `ddl-auto=validate` 로 일치 여부를 검증합니다.
- 도메인 간 참조에는 FK를 걸지 않습니다(경계 유지, 애플리케이션이 무결성 책임). 사람이 읽는 전체 스키마 정리는 [`SCHEMA`](SCHEMA) 참고.

## 📚 관련 문서

- [`../docs/BACKEND_CONVENTIONS.md`](../docs/BACKEND_CONVENTIONS.md) — 패키지 구조·DTO·예외·트랜잭션 규칙
- 서비스 간 계약(백엔드가 한쪽 당사자):
  [`CONVERSATION_TOPIC_CONTRACT`](../docs/CONVERSATION_TOPIC_CONTRACT.md),
  [`CHILD_DIARY_BACKEND_CONTRACT`](../docs/CHILD_DIARY_BACKEND_CONTRACT.md),
  [`CHILD_DIARY_AI_CONTRACT`](../docs/CHILD_DIARY_AI_CONTRACT.md),
  [`REPORT_AI_CONTRACT`](../docs/REPORT_AI_CONTRACT.md)
- [`../docs/MONITORING.md`](../docs/MONITORING.md), [`../docs/OPERATIONS_RUNBOOK.md`](../docs/OPERATIONS_RUNBOOK.md) — 모니터링·운영 절차
- 서비스 경계와 전체 규칙은 루트 [`../CLAUDE.md`](../CLAUDE.md) 를 따릅니다.

## 📌 기타

- 유아 대상 서비스입니다. 실제 아이 이름·음성·영상·일기 원문은 샘플/픽스처로 커밋하지 않습니다.
- `prod` 환경에서는 반드시 HTTPS 설정과 함께 JWT 쿠키 보안(`app.cookie.secure=true`)을 유지하세요.
