# Jenkins CI/CD 운영 설정

Jenkins는 EC2의 Docker에서 실행하며 호스트의 `127.0.0.1:8080`에만 바인딩한다.
UFW에 8080 포트를 추가하지 않고 SSH 터널로만 관리 화면에 접근한다.

## 서버 준비

```sh
sudo mkdir -p /srv/jenkins/home /srv/jenkins-backup \
  /srv/a501-backups/mysql /srv/a501-deploy-state
sudo chown -R 1000:1000 /srv/jenkins
sudo chown -R ubuntu:ubuntu /srv/jenkins-backup \
  /srv/a501-backups /srv/a501-deploy-state
sudo chmod 750 /srv/jenkins/home
sudo chmod 700 /srv/jenkins-backup /srv/a501-backups /srv/a501-deploy-state

cd /srv/a501
export DOCKER_GID=$(stat -c '%g' /var/run/docker.sock)
docker compose -f deploy/jenkins/compose.yml build
docker compose -f deploy/jenkins/compose.yml up -d
docker logs a501-jenkins
```

Windows에서 다음 터널을 연 뒤 `http://127.0.0.1:8080`으로 접속한다.

```powershell
ssh -i .\I15A501T.pem -L 8080:127.0.0.1:8080 ubuntu@i15a501.p.ssafy.io
```

## 필수 보안 설정

- Security Realm: Jenkins' own user database
- 사용자 가입 허용: 해제
- Authorization: Logged-in users can do anything
- Allow anonymous read access: 해제
- UFW 8080 규칙: 추가 금지
- GitLab token과 webhook secret: Jenkins Credentials에만 저장

## GitLab 연동

Multibranch Pipeline을 만들고 저장소 credential과 `Jenkinsfile`을 연결한다.
GitLab webhook은 Push events를 선택하고 master push만 파이프라인에서 배포한다.
Secret token은 GitLab webhook과 Jenkins GitLab 설정에 동일하게 등록한다.

## Jenkins 데이터 백업

호스트에서 `backup-jenkins.sh`를 실행한다. 일관된 백업을 위해 Jenkins 컨테이너가
잠시 중지됐다가 자동으로 다시 시작된다. 기본 보관 기간은 14일이다.

```sh
sudo sh /srv/a501/deploy/jenkins/backup-jenkins.sh
```
