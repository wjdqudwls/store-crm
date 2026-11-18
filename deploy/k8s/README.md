# k3s 매니페스트 (ADR-037)

Oracle 무료 VM의 k3s(단일 노드)에 올리는 파일이다. 비밀 값은 없다.

| 파일 | 내용 |
| --- | --- |
| `kustomization.yaml` | 묶음, 이미지 이름·태그 (젠킨스가 태그를 바꾼다) |
| `mariadb.yaml` | MariaDB 11 StatefulSet + PVC 5Gi, 클러스터 안에서만 접속 |
| `app.yaml` | 앱 Deployment(롤링, 프로브) + Service |
| `middlewares.yaml` | basicAuth(로그인 전), no-cache, immutable-cache |
| `ingress.yaml` | PWA 파일(인증 없음) / `/assets`(1년 캐시) / 나머지(basicAuth) |
| `traefik-config.yaml` | 인증서 자동 발급·https 이동 (VM에서 한 번) |
| `secret.example.yaml` | 필요한 Secret의 키 이름만 (값 없음) |

## VM에서 처음 적용 (K3)

1. `traefik-config.yaml`의 `ACME_EMAIL_PLACEHOLDER`를 실제 값으로 바꿔 적용한다.
2. Secret 두 개를 만든다 (`secret.example.yaml` 위쪽 명령). 값은 VM에서 직접 적는다.
3. 도메인을 넣어 적용한다:
   `kubectl kustomize deploy/k8s | sed 's/APP_HOST_PLACEHOLDER/<DuckDNS 도메인>/g' | kubectl apply -f -`
4. `kubectl -n store-crm rollout status deploy/app` 와 `kubectl -n store-crm get pods`로 확인한다.

## 알아 둘 것

- 앱 프로브는 `/actuator/health/liveness`·`readiness`다. 백엔드 `prod` 프로필에서 `health`가 노출돼야 하고, Spring Boot가 쿠버네티스를 감지하면 두 그룹이 자동으로 켜진다 (백엔드 세션에 확인 요청).
- basicAuth는 로그인(B0-3)이 들어가면 `ingress.yaml`의 `store-crm-basic-auth@kubernetescrd`를 떼고, 앱의 `Authorization: Bearer`와 겹치지 않게 한다.
- 이 매니페스트는 아직 클러스터에 적용해 본 적이 없다. `kubectl kustomize`로 렌더링만 확인했다.
