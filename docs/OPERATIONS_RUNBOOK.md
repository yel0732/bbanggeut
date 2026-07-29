# A501 운영·백업·복구 가이드

이 문서는 EC2의 `/srv/a501` 배포를 기준으로 한다. 명령은 EC2의 `ubuntu` 계정에서
실행하며 실제 비밀값은 `/srv/a501-secrets/.env`에만 둔다.

## 1. 기본 위치와 운영 원칙

- 애플리케이션 소스: `/srv/a501`
- Jenkins가 사용하는 안정적인 배포 파일: `/srv/a501-deploy-state/runtime`
- 배포 상태와 이전 이미지: `/srv/a501-deploy-state`
- MySQL 백업: `/srv/a501-backups/mysql`
- Jenkins 데이터: `/srv/jenkins/home`
- 인증서: `/etc/letsencrypt`
- 외부 공개 포트: `80`, `443`만 사용한다. MySQL, Redis, Backend, AI Server는
  Docker 내부망에서만 접근한다.
- 운영 DB 볼륨과 Redis 볼륨을 삭제하는 `docker compose down -v`는 실행하지 않는다.

## 2. 일상 점검

```bash
cd /srv/a501
sudo deploy/scripts/check-operations.sh
curl -fsS https://i15a501.p.ssafy.io/nginx-health
curl -fsS https://i15a501.p.ssafy.io/actuator/health
```

정상 기준은 모든 Compose 서비스가 `healthy`, 인증서 만료일이 유효하고 두 HTTPS
요청이 성공하는 것이다. 디스크 사용률이 80%를 넘거나 인증서 만료가 30일 이내이면
정리 또는 갱신 상태를 즉시 점검한다.

## 3. MySQL 백업과 보관

Jenkins 배포는 애플리케이션 교체 전에 자동으로 백업한다. 수동 백업은 다음과 같다.

```bash
sudo /srv/a501-deploy-state/runtime/deploy/scripts/backup-db.sh
```

- 기본 보관 기간은 14일이며 `DB_BACKUP_RETENTION_DAYS`로 변경할 수 있다.
- 파일은 `gzip -t 백업파일.sql.gz`로 무결성을 확인한다.
- EC2 장애에도 복구하려면 최신 백업을 별도 서버 또는 별도 마운트로 복사한다.

```bash
sudo deploy/scripts/export-db-backup.sh \
  /srv/a501-backups/mysql/a501-YYYYMMDDTHHMMSSZ.sql.gz \
  backup-user@backup-host:/secure/a501/
```

외부 백업 서버가 없다면 팀의 승인된 암호화 저장소를 먼저 정한다. 백업에는 사용자
정보가 포함되므로 Git, 메신저, 공개 공유 폴더에는 올리지 않는다.

## 4. 운영 DB를 건드리지 않는 복구 시험

아래 스크립트는 외부 포트를 열지 않은 임시 MySQL 8.4 컨테이너와 임시 볼륨에 최신
백업을 복원한다. 테이블 수와 Flyway 버전을 확인한 뒤 임시 컨테이너와 볼륨을 자동
삭제한다.

```bash
sudo deploy/scripts/test-db-restore.sh
# 특정 백업 검증
sudo deploy/scripts/test-db-restore.sh /srv/a501-backups/mysql/a501-....sql.gz
```

`Temporary restore verified`가 출력되어야 성공이다.

## 5. 실제 DB 복구

실제 복구는 Backend 쓰기를 중단하고 현재 DB를 교체한다. 장애 복구 상황에서만
실행한다. 스크립트는 복구 직전에 안전 백업을 하나 더 만들며 확인 문구가 정확하지
않으면 실행되지 않는다.

```bash
sudo deploy/scripts/restore-db.sh \
  /srv/a501-backups/mysql/a501-....sql.gz \
  RESTORE_PRODUCTION_DB
```

복구 후 AI Server, Backend, Nginx를 다시 시작하고 health와 내부 smoke test까지
자동 확인한다. 실패하면 새로 생성된 안전 백업을 보존하고 원인을 확인한다.

## 6. 애플리케이션 이미지 롤백 시험

Jenkins는 배포 직전 이미지 태그를 `/srv/a501-deploy-state/previous.env`에 기록한다.
롤백 시험은 먼저 DB를 백업하고 이전 AI Server·Backend·Nginx 이미지로 교체해 상태를
검증한 뒤, 시험 시작 당시 최신 이미지로 자동 복귀한다. DB 볼륨과 Redis 볼륨은
교체하거나 삭제하지 않는다.

```bash
sudo deploy/scripts/test-rollback.sh
```

다음 두 문구가 모두 출력되어야 한다.

```text
Previous images became healthy; rollback path verified.
Current images restored and smoke-tested.
```

중간에 실패해도 종료 처리에서 최신 이미지를 복원한다. 복원 메시지까지 확인하고
`check-operations.sh`를 다시 실행한다.

## 7. 수동 이미지 롤백

자동 배포 실패 시 다음 명령으로 기록된 이전 이미지를 복구한다.

```bash
sudo COMPOSE_FILE=/srv/a501-deploy-state/runtime/compose.yml \
  ENV_FILE=/srv/a501-secrets/.env \
  /srv/a501-deploy-state/runtime/deploy/scripts/rollback.sh \
  /srv/a501-deploy-state/previous.env
```

Flyway migration은 자동으로 되돌리지 않는다. 삭제·타입 변경과 같은 파괴적 migration은
최소 한 배포 동안 구버전 이미지와 호환되도록 단계적으로 적용한다.

## 8. Jenkins 자동 배포

`master` 병합 시 GitLab Webhook이 Jenkins의 `a501-deploy` 파이프라인을 실행한다.

1. master 확인
2. Backend 및 AI Server 테스트
3. Git SHA 태그 이미지 빌드
4. DB 백업
5. AI Server, Backend, Nginx 순차 교체와 health 확인
6. 내부 AI readiness, Backend readiness, JWKS smoke test
7. 실패 시 이전 이미지 복원

`SKIP_TESTS=true`는 미완성 테스트를 확인한 임시 수동 배포에만 사용한다. 정상 운영
완료 조건에서는 `false`로 실행해 테스트 실패 시 배포를 차단한다.

## 9. 인증서 갱신

```bash
sudo systemctl status certbot.timer --no-pager
sudo certbot renew --dry-run
```

갱신 후 Nginx가 새 인증서를 읽도록 한다.

```bash
sudo /srv/a501/deploy/certbot/reload-nginx.sh
```

실패하면 80 포트, `/.well-known/acme-challenge/`, DNS 연결과 Certbot 로그를 확인한다.

## 10. 비밀값 교체

1. `/srv/a501-secrets/.env`를 권한 `600`, 소유자 `ubuntu`로 유지한다.
2. 교체 전 현재 파일을 Git 외부의 안전한 장소에 백업한다.
3. 값을 수정한 뒤 `docker compose config` 출력에 비밀값이 노출되지 않도록 공유하지
   않는다.
4. 관련 서비스만 재생성하고 health를 확인한다.
5. GitLab/Jenkins credential은 Jenkins Credentials 화면에서 교체한다.

비밀값, 개인키, `.pem`, 실제 `.env`는 Git과 Jenkins 로그에 출력하지 않는다.

## 11. 디스크와 이미지 정리

먼저 삭제 없이 사용량만 확인한다.

```bash
sudo deploy/scripts/cleanup-docker.sh
```

확인 후 7일이 지난 dangling image와 build cache만 정리한다.

```bash
sudo deploy/scripts/cleanup-docker.sh --apply
```

롤백용 Git SHA 이미지, 실행 중인 이미지, MySQL·Redis 볼륨은 이 스크립트가 삭제하지
않는다.

## 12. 운영 보안 확인

- 운영 Spring profile은 H2 Console과 Swagger/OpenAPI endpoint를 비활성화한다.
- Jenkins UI는 EC2의 `127.0.0.1:8080`에만 바인딩하고 SSH 터널로 접속한다.
- UFW는 활성 상태를 유지하고 `22`, `80`, `443` 외 불필요한 포트를 열지 않는다.
- `22` 포트와 `/home/ubuntu/.ssh` 권한은 임의로 변경하지 않는다.

## 13. 장애 대응 체크리스트

1. `docker compose ps`와 실패한 컨테이너 로그를 확인한다.
2. 디스크·메모리·볼륨·인증서 상태를 확인한다.
3. 현재 Git SHA와 이미지 태그, DB 백업 경로를 기록한다.
4. 데이터 문제가 아니면 이전 이미지 롤백을 우선한다.
5. 데이터 문제이면 임시 복원 시험에 성공한 백업만 실제 복구에 사용한다.
6. HTTPS health, 로그인·refresh cookie, WSS signaling을 확인한다.
7. 원인, 복구 시각, 사용한 이미지 SHA와 백업 파일을 기록한다.

## 14. 배포 완료 체크리스트

- [ ] Jenkins 결과가 `SUCCESS`이고 테스트를 건너뛰지 않았다.
- [ ] Backend, AI Server, Nginx가 healthy이다.
- [ ] HTTPS health와 WSS 연결이 성공한다.
- [ ] 로그인과 secure refresh cookie가 정상이다.
- [ ] 배포 전 DB 백업이 존재하고 임시 복원 시험이 성공한다.
- [ ] 이전 이미지 롤백 후 최신 이미지 복귀가 성공한다.
- [ ] MySQL·Redis 볼륨이 유지된다.
- [ ] 최신 DB 백업이 EC2 외부에 보관된다.
- [ ] Certbot 자동 갱신과 dry-run이 성공한다.
- [ ] Swagger와 H2 Console이 운영에서 노출되지 않는다.
- [ ] Git, Jenkins 로그, 문서에 실제 비밀값이 없다.
