// 배포 파이프라인 (ADR-037): 빌드 → Docker Hub push → 매니페스트 적용(이미지 태그 교체) → 롤아웃 확인 → 실패하면 되돌리기.
// 테스트는 PR에서 GitHub Actions(backend-ci·frontend-ci)가 이미 돌렸으므로 여기서는 하지 않는다.
// 비밀 값은 코드에 없다. Jenkins Credentials의 ID와 Jenkins 전역 환경변수 이름만 적는다 (NFR-06).
//
// Jenkins에 미리 만들어 둘 것 (deploy/jenkins/README.md):
//   Credentials  dockerhub          Username with password (Docker Hub 아이디 + Read & Write 액세스 토큰)
//                k3s-deployer-kubeconfig  Secret file (store-crm 네임스페이스만 다루는 ServiceAccount의 kubeconfig)
//   전역 환경변수 DOCKERHUB_REPO    예: <Docker Hub 아이디>/store-crm
//                APP_HOST          앱 도메인 (DuckDNS)
//   작업 설정    Pipeline from SCM, 브랜치 */main 하나만, 트리거는 GitHub hook(웹훅) 또는 폴링
pipeline {
  agent any

  options {
    timeout(time: 25, unit: 'MINUTES')
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '10'))
  }

  triggers {
    // 웹훅이 안 되면 아래 줄 대신 pollSCM('H/5 * * * *')를 쓴다 (노출 없이 5분마다 확인).
    githubPush()
  }

  environment {
    NAMESPACE = 'store-crm'
  }

  stages {
    stage('Check') {
      steps {
        script {
          if (!env.DOCKERHUB_REPO || !env.APP_HOST) {
            error('Jenkins 전역 환경변수 DOCKERHUB_REPO, APP_HOST가 필요하다')
          }
          env.TAG = sh(returnStdout: true, script: 'git rev-parse --short=7 HEAD').trim()
        }
        sh 'docker version --format "{{.Client.Version}}" && kubectl version --client'
      }
    }

    stage('Build') {
      steps {
        sh 'docker build -t "$DOCKERHUB_REPO:$TAG" -t "$DOCKERHUB_REPO:latest" .'
      }
    }

    stage('Push') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'dockerhub', usernameVariable: 'DH_USER', passwordVariable: 'DH_TOKEN')]) {
          sh '''
            echo "$DH_TOKEN" | docker login -u "$DH_USER" --password-stdin
            docker push "$DOCKERHUB_REPO:$TAG"
            docker push "$DOCKERHUB_REPO:latest"
          '''
        }
      }
    }

    stage('Deploy') {
      steps {
        withCredentials([file(credentialsId: 'k3s-deployer-kubeconfig', variable: 'KUBECONFIG')]) {
          sh '''
            set -eu
            # Secret(app-env)은 VM에서 직접 만든다. 없으면 여기서 멈춘다.
            kubectl -n "$NAMESPACE" get secret app-env >/dev/null

            # 레포의 매니페스트를 복사해 도메인과 이미지 태그를 넣고 적용한다 (Git이 기준).
            rm -rf .deploy-tmp && cp -r deploy/k8s .deploy-tmp
            sed -i "s#DOCKERHUB_USER/store-crm#$DOCKERHUB_REPO#; s#newTag: .*#newTag: \\"$TAG\\"#" .deploy-tmp/kustomization.yaml
            kubectl kustomize .deploy-tmp | sed "s#APP_HOST_PLACEHOLDER#$APP_HOST#g" | kubectl apply -f -
            rm -rf .deploy-tmp
          '''
          // 새 Pod가 준비(readiness)될 때까지 기다린다. 실패하면 직전 버전으로 되돌린다.
          script {
            def status = sh(returnStatus: true, script: 'kubectl -n "$NAMESPACE" rollout status deploy/app --timeout=180s')
            if (status != 0) {
              sh 'kubectl -n "$NAMESPACE" rollout undo deploy/app'
              sh 'kubectl -n "$NAMESPACE" rollout status deploy/app --timeout=180s || true'
              error("롤아웃 실패: 직전 버전으로 되돌렸다 (태그 ${env.TAG})")
            }
          }
        }
      }
    }
  }

  post {
    always {
      sh 'docker logout || true'
      // 쓰지 않는 오래된 이미지 정리 (디스크 아끼기, 7일 이상 된 것만)
      sh 'docker image prune -af --filter "until=168h" || true'
    }
    success {
      echo "배포 완료: ${env.DOCKERHUB_REPO}:${env.TAG}"
    }
  }
}
