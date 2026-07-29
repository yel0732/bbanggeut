pipeline {
    agent any

    options {
        disableConcurrentBuilds()
        timestamps()
        timeout(time: 45, unit: 'MINUTES')
        skipDefaultCheckout(true)
    }

    parameters {
        booleanParam(
                name: 'SKIP_TESTS',
                defaultValue: false,
                description: 'Temporary manual deployment only: skip Backend and AI Server tests')
    }

    environment {
        ENV_FILE = '/srv/a501-secrets/.env'
        COMPOSE_PROJECT_NAME = 'a501'
    }

    stages {
        stage('Checkout') {
            steps { checkout scm }
        }

        stage('Verify master') {
            steps {
                script {
                    def branchRefs = [
                            env.BRANCH_NAME,
                            env.gitlabSourceBranch,
                            env.GIT_BRANCH,
                            scm.branches?.first()?.name
                    ].findAll { it }
                    def isMaster = branchRefs.any {
                        it == 'master' || it.endsWith('/master')
                    }
                    echo "Resolved branch refs: ${branchRefs.join(', ')}"
                    if (!isMaster) {
                        currentBuild.result = 'NOT_BUILT'
                        error('Deployment pipeline only runs for master.')
                    }
                }
            }
        }

        stage('Backend test') {
            when {
                expression { !params.SKIP_TESTS }
            }
            steps {
                dir('backend') {
                    sh './gradlew test'
                }
            }
        }

        stage('AI Server test') {
            when {
                expression { !params.SKIP_TESTS }
            }
            steps {
                dir('ai-server') {
                    sh '''python3 -m venv "$WORKSPACE/.venv-ai-ci"
                        . "$WORKSPACE/.venv-ai-ci/bin/activate"
                        pip install --disable-pip-version-check -r requirements.txt
                        timeout 15m pytest -q'''
                }
            }
        }

        stage('Build images') {
            steps {
                sh '''docker build -t a501-ai-server:${GIT_COMMIT} ai-server
                    docker build -t a501-backend:${GIT_COMMIT} backend
                    NGINX_IMAGE=a501-nginx:${GIT_COMMIT} docker compose \
                        --env-file "${ENV_FILE}" build nginx'''
            }
        }

        stage('Deploy') {
            steps {
                sh 'chmod +x deploy/scripts/*.sh && deploy/scripts/deploy.sh "${GIT_COMMIT}"'
            }
        }
    }

    post {
        always {
            junit allowEmptyResults: true,
                    testResults: 'backend/build/test-results/test/*.xml'
            archiveArtifacts allowEmptyArchive: true,
                    artifacts: 'backend/build/reports/tests/test/**'
            deleteDir()
        }
    }
}
