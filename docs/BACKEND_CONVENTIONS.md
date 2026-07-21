# Backend Conventions

백엔드 개발 시 적용하는 기본 규칙입니다. `frontend`가 `docs/FRONTEND_CONVENTIONS.md`를 기준으로 움직이듯, 백엔드는 이 문서를 기준으로 맞춥니다.

## 기술 스택

- Java 21
- Spring Boot 4.1 (Web MVC)
- Spring Data JPA
- Spring Security
- springdoc-openapi (Swagger UI)
- Lombok
- H2(local) / MySQL(prod)
- Gradle

## 아키텍처: 도메인 기반(DDD 스타일) 패키지 구조

기능을 계층(전체 controller, 전체 service ...)으로 먼저 나누지 않고, `auth`, `child`처럼 **도메인 단위로 먼저 나누고** 그 안에서 계층을 나눕니다. 도메인 하나를 통째로 이해하거나 수정할 때 폴더 하나만 보면 되도록 하는 것이 목적입니다.

```text
com.A501.backend
├── BackendApplication.java
├── common/                 도메인에 속하지 않는 공용 코드
│   ├── exception/          GlobalExceptionHandler, ErrorResponse, ErrorCode
│   ├── response/           공통 응답 래퍼(ApiResponse 등)
│   └── util/                범용 유틸리티
├── config/                 전역 설정 (SecurityConfig, SwaggerConfig, JpaConfig 등)
└── domain/
    ├── auth/
    │   ├── controller/
    │   ├── service/
    │   ├── repository/
    │   ├── entity/
    │   ├── dto/
    │   │   ├── request/
    │   │   └── response/
    │   └── exception/       (도메인 전용 예외가 필요할 때만)
    ├── child/
    ├── game/
    ├── conversation/
    ├── report/
    └── notification/
```

- 도메인 이름은 `docs/FRONTEND_CONVENTIONS.md`의 라우트, `docs/UI_UX_REQUIREMENTS.md`의 UI Epic과 최대한 맞춥니다 (`auth`, `child`, `game`, `conversation`, `report` ...).
- 하위 패키지는 해당 도메인에 필요한 것만 만듭니다. 예를 들어 엔티티가 없는 도메인이면 `entity/`를 만들지 않습니다.
- 다른 도메인의 `entity`, `repository`를 직접 참조하지 않습니다. 다른 도메인 데이터가 필요하면 그 도메인의 `service`를 주입받아 사용합니다.
- 여러 도메인에서 함께 쓰는 코드만 `common/`으로 올립니다. 도메인 하나에서만 쓰는 코드를 미리 `common`으로 빼지 않습니다.

## 네이밍

| 대상 | 규칙 | 예시 |
| --- | --- | --- |
| 클래스 | `PascalCase` | `ChildProfileService` |
| 변수/메서드 | `camelCase` | `findByChildId` |
| 상수 | `UPPER_SNAKE_CASE` | `MAX_PROFILE_COUNT` |
| 패키지 | 소문자 단수형 | `domain.child`, 도메인 자체는 단수 |
| DB 테이블/컬럼 | `snake_case` | `child_profile`, `created_at` |
| API 경로 | `kebab-case`, 복수형 리소스, `/api/{domain}` 접두사 | `/api/child-profiles` |

계층별 클래스는 역할이 이름에 드러나야 합니다.

```text
AuthController      /api/auth
AuthService
AuthRepository
Auth                엔티티는 접미사 없이 도메인 명사 그대로
LoginRequest         DTO는 Request/Response 접미사 필수
LoginResponse
```

## 계층별 책임

- **Controller**: 요청을 받아 DTO로 검증하고 서비스를 호출한 뒤 응답을 반환합니다. 비즈니스 로직을 두지 않습니다.
- **Service**: 실제 로직과 트랜잭션 경계를 담당합니다. 클래스 레벨에 `@Transactional(readOnly = true)`를 걸고, 쓰기 메서드에만 `@Transactional`을 다시 붙입니다.
- **Repository**: `JpaRepository<Entity, Id>`를 상속하는 인터페이스만 둡니다. 복잡한 조회는 쿼리 메서드나 `@Query`로 해결하고, 그래도 부족하면 QueryDSL 도입 여부를 먼저 논의합니다.
- **Entity**: JPA 매핑 전용입니다. 상태를 바꾸는 메서드는 의도가 드러나는 이름으로 명시적으로 만들고, 무분별한 `setter`는 두지 않습니다.
- **DTO**: 외부(요청/응답)와 내부(엔티티)를 분리하는 경계입니다. 엔티티를 컨트롤러 밖으로 그대로 반환하지 않습니다.

기본적으로 서비스는 인터페이스 없이 구현 클래스를 바로 `@Service`로 둡니다. 구현체가 여러 개 필요해질 때만 인터페이스를 분리합니다.

## DTO 규칙

- request/response DTO는 각각 `dto/request`, `dto/response`에 둡니다.
- 불변 DTO가 기본입니다. 필드가 단순하면 Java `record`를 사용하고, Bean Validation이 필요하면 컴팩트 생성자나 필드에 `jakarta.validation` 애너테이션을 붙입니다.
- 컨트롤러 메서드 파라미터에서 요청 DTO를 검증할 때는 `@Valid`를 반드시 붙입니다.
- 엔티티 ↔ DTO 변환은 DTO의 정적 팩토리 메서드(`from(Entity entity)`)나 별도 `Mapper`로 처리하고, 서비스 로직 중간에 흩어두지 않습니다.

```java
public record CreateChildProfileRequest(
        @NotBlank String name,
        @NotNull LocalDate birthDate
) {}

public record ChildProfileResponse(Long id, String name, LocalDate birthDate) {
    public static ChildProfileResponse from(ChildProfile entity) {
        return new ChildProfileResponse(entity.getId(), entity.getName(), entity.getBirthDate());
    }
}
```

## 공통 응답 포맷

에러 응답은 `common/exception/ErrorResponse`, `GlobalExceptionHandler`로 이미 통일되어 있습니다. 새 예외를 추가할 때는 `Exception` 클래스를 도메인마다 새로 만들기보다, 아래처럼 `ErrorCode` + 공통 `BusinessException`을 쌓아가는 방식을 기본으로 합니다.

```java
public enum ChildErrorCode implements ErrorCode {
    PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "CHILD_PROFILE_NOT_FOUND", "아이 프로필을 찾을 수 없습니다.");
    // ...
}
```

성공 응답도 동일한 형태로 오갈 수 있도록, 여러 컨트롤러에서 반복되는 성공 응답 포맷이 생기면 `common/response`에 공용 래퍼(`ApiResponse<T>` 등)를 추가하는 것을 우선 검토하고, 도메인 컨트롤러마다 각자 포맷을 만들지 않습니다.

## 예외 처리

- 전역 처리는 `common/exception/GlobalExceptionHandler`에서 담당합니다. 새 예외 타입이 여러 도메인에서 공통으로 발생하면 여기에 핸들러를 추가합니다.
- 도메인 특화 예외가 필요하면 `domain/{name}/exception`에 두고, 마찬가지로 `GlobalExceptionHandler`가 처리하도록 등록합니다.
- 예외 메시지는 사용자에게 그대로 노출될 수 있음을 감안해 내부 구현 세부사항을 담지 않습니다.

## Lombok / 생성자

- 엔티티: `@Getter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`, 필요 시 `@Builder`를 정적 팩토리와 함께 사용합니다. `@Setter`는 두지 않습니다.
- 서비스/컴포넌트: 필드 주입(`@Autowired` on field) 대신 생성자 주입을 사용합니다. `@RequiredArgsConstructor` + `private final` 조합이 기본입니다.
- 로깅은 `@Slf4j`를 사용합니다.

## 트랜잭션 / 조회

- 조회 전용 서비스 메서드는 `readOnly = true`를 유지해 불필요한 flush를 막습니다.
- N+1이 예상되는 연관관계는 fetch join이나 `@EntityGraph`로 명시적으로 해결하고, 로그(`hibernate.SQL: debug`, 로컬 프로필에 이미 설정됨)로 쿼리 수를 확인합니다.

## API 문서화

- springdoc-openapi를 사용 중이므로 컨트롤러에 `@Tag`, 각 메서드에 `@Operation`을 붙여 Swagger UI에서 도메인별로 API를 바로 확인할 수 있게 합니다.

## 환경 변수 / 설정

- 프로필은 `local`(H2), `prod`(MySQL)로 이미 분리되어 있습니다. 새 설정값은 두 프로필 중 어디에 필요한지 구분해서 추가합니다.
- 비밀번호, 토큰, 키 등은 `application.yml`에 직접 쓰지 않고 환경변수로 주입합니다(`${DB_PASSWORD}` 방식). `.env`나 비밀값을 커밋하지 않는 규칙은 `docs/AI_AGENT_WORKFLOW.md`의 민감 정보 규칙을 그대로 따릅니다.

## 검증

작업 전후로 아래 명령을 기준으로 확인합니다.

```bash
./gradlew test
./gradlew build
```

## AI 에이전트와 협업 시

- 요청 템플릿과 리뷰 체크리스트는 `docs/AI_AGENT_WORKFLOW.md`를 그대로 따릅니다.
- 수정 위치를 도메인 단위로 명시합니다. 예: `backend/src/main/java/com/A501/backend/domain/child`.
- 다른 도메인 패키지나 `common/`, `config/`는 요청 범위에 없으면 건드리지 않습니다.
- 새 도메인을 추가할 때는 이 문서의 패키지 구조(`controller/service/repository/entity/dto`)를 그대로 따르고, 구조가 맞지 않으면 코드 작성 전에 먼저 구조 변경을 제안합니다.
