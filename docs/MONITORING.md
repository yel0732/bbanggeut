# 모니터링 (Prometheus + Grafana)

Prometheus가 backend·ai-server의 메트릭을 15초마다 수집하고, Grafana가 nginx 뒤의
`/grafana/` 경로에서 대시보드를 제공한다. 모니터링 스택은 compose profile로
분리되어 있어 기본 스택 기동에 영향을 주지 않는다.

## 기동

```bash
# 로컬 (Windows/macOS — node-exporter 제외)
docker compose --profile monitoring up -d
```

**배포(EC2)는 deploy.sh가 담당한다** — 모니터링 설정 파일을 런타임 디렉터리로
복사하고 prometheus·grafana·node-exporter를 함께 올린다. 별도 명령이 필요 없다.

첫 배포 전에 한 번만 EC2의 `/srv/a501-secrets/.env`에 추가한다:

```
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=강한-비밀번호
GRAFANA_ROOT_URL=https://i15a501.p.ssafy.io/grafana/
```

접속: `https://i15a501.p.ssafy.io/grafana/` (로컬은 `http://localhost:3000/grafana/`
또는 nginx를 띄웠다면 `http://localhost/grafana/`).
`GRAFANA_ADMIN_PASSWORD`는 **grafana_data 볼륨이 처음 만들어질 때만 적용**된다 —
이후 변경은 Grafana UI 또는
`docker compose exec grafana grafana cli admin reset-admin-password <pw>`.

## 노출 원칙

외부에서 접근 가능한 것은 nginx 뒤의 Grafana 하나뿐이다.

- Prometheus·node-exporter는 포트를 publish하지 않고 nginx도 라우팅하지 않는다.
- backend `/actuator/prometheus`는 Security에서 permitAll이지만 nginx가
  라우팅하지 않아 외부에서 도달할 수 없다(외부 노출은 `/actuator/health`뿐).
- ai-server `/metrics`는 host 네트워크의 8000 포트에 열리지만 UFW가 외부를
  막는다 — 기존 `/health/*`와 같은 노출 수준이다.
- grafana upstream은 nginx에서 변수 + resolver로 요청 시점에 조회한다.
  monitoring profile을 끈 배포에서도 nginx가 정상 기동하고 `/grafana/`만
  502를 반환한다.

## 수집 대상 (deploy/prometheus.yml)

| job | 대상 | 내용 |
|---|---|---|
| backend | `backend:8080/actuator/prometheus` | JVM 힙·GC, HTTP 요청 히스토그램(p95), HikariCP, Lettuce |
| ai-server | `host.docker.internal:8000/metrics` | 프로세스 CPU·RSS, GC (도메인 메트릭은 후속 Task) |
| node | `node-exporter:9100` | 호스트 CPU/RAM/디스크 (host-metrics profile 전용) |
| prometheus | 자기 자신 | 수집 상태 자가 진단 |

보존 기간 15일(`--storage.tsdb.retention.time`), 데이터는 named volume
(`prometheus_data`, `grafana_data`)에 남는다.

## 대시보드

- **A501 Overview**가 provisioning으로 자동 로드된다(서비스 Up, HTTP 처리율·p95,
  JVM 힙, ai-server 프로세스, 호스트 CPU/RAM).
- 더 깊은 패널이 필요하면 Grafana UI에서 커뮤니티 대시보드를 import한다:
  JVM Micrometer `4701`, Node Exporter Full `1860`. datasource는 `Prometheus`를
  선택하면 된다.
- provisioning 디렉터리(`deploy/grafana/provisioning/`)는 읽기 전용 마운트다 —
  대시보드를 코드로 관리하려면 JSON을 이 디렉터리에 추가해 커밋한다.

## 메트릭 추가 규칙

- 도메인 메트릭(barge-in 횟수, STT/TTS 지연 등)은 별도 Task에서
  `prometheus_client`(ai-server) / Micrometer(backend)로 등록한다.
- **레이블에 conversationId·아이 이름·전사 텍스트를 넣지 않는다** — 카디널리티
  폭발과 민감 정보 규칙(원문 미저장) 양쪽의 문제다. 레이블은 유한한 enum
  값(reason, topic type 등)만 쓴다.
