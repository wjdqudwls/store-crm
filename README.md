# store-crm

패션 오프라인 매장용 클라이언텔링 CRM. 판매를 기록하는 순간 고객관리가 시작되고, 언제 누구에게 연락할지 알려 준다. POS·ERP 옆에 붙어 상담 기록·콜리스트·고객 등급·후속 연락을 제공한다.

> 서비스 이름: CRM pos · 1인 포트폴리오 프로젝트, 마감 2026-12-09 · 상태는 아래 "진행 상황" (2026-11-19 기준)

## 진행 상황

| 영역 | 된 것 | 다음 |
| --- | --- | --- |
| 화면 (`frontend/`) | 화면 초안 F2~F13을 가짜 데이터로 모두 만듦, 공통 에러 띠(F12) | 실제 API 연결 |
| 백엔드 (`backend/`) | 뼈대·CI, 물리 스키마(B0-5), 공통 응답·에러(B0-2), 현재 계정 자리·테스트 기반·기본 데이터(B0-6) | B1 상품·고객 |
| 배포 | Dockerfile(K1), k3s 매니페스트 초안(K3), 화면 데모 Pages 워크플로(공개 전) | Oracle VM·k3s 설치(K2), 배포 확인 |

## 실행 방법

```bash
cp .env.example .env              # 처음 한 번, 값은 직접 바꾼다
docker compose up -d              # 로컬 DB (MariaDB 11, 포트 3316)
cd backend && ./gradlew bootRun   # 서버 → http://localhost:8080/actuator/health
cd backend && ./gradlew test      # 테스트 (Docker 필요: Testcontainers MariaDB)
cd frontend && npm run dev        # 화면 개발 서버 (가짜 데이터 모드)
cd frontend && npm run build:demo # 화면 데모 빌드 (GitHub Pages용)
```

## 폴더 구성

| 경로 | 내용 |
| --- | --- |
| `backend/` | Spring Boot 4, Java 21, Gradle |
| `frontend/` | Vue 3 + Vite 화면 |
| `deploy/k8s/` | k3s 배포 매니페스트 (kustomize) |
| `Dockerfile` | 화면과 서버를 한 이미지로 빌드 |
| `docker-compose.yml` | 로컬 개발용 MariaDB |
| `.github/workflows/` | `backend-ci`, `frontend-ci`(PR 검사), `pages`(화면 데모 수동 배포) |

## 배포 구조

Oracle Cloud 무료 VM 1대에 k3s(가벼운 쿠버네티스) 단일 노드를 두고, Traefik Ingress로 HTTPS를 받는다. 앱은 Deployment, MariaDB는 PVC가 붙은 StatefulSet이다. 합친 뒤 배포는 호스트 Docker 컨테이너의 Jenkins가 이미지를 빌드해 Docker Hub에 올리고 롤아웃한다. PR 검사는 GitHub Actions가 한다. Oracle에서는 VM만 빌리고 DB는 그 안의 MariaDB다 (ADR-037). 단일 노드라 고가용성은 없고, 학습·포트폴리오 목적이다.

## 시스템 아키텍처

(시스템 아키텍처 그림은 로컬 문서에 있다: `docs/images/architecture.png`. 레포에는 올리지 않는다.) 자세한 설명은 `docs/04-architecture.md`.

## 문서 (읽는 순서 = 만드는 순서)

> **기획 문서(`docs/`)는 레포에 올리지 않는다** (2026-11-19, `.gitignore`). 아래 표의 문서는 작성자의 로컬 폴더에만 있다. 이 README와 코드만 레포에 있다.

| 순서 | 문서 | 내용 | 상태 |
| --- | --- | --- | --- |
| 0 | `docs/00-idea.md` | 왜 만드는가, 문제, 대상, 범위 | 확정 |
| 1 | `docs/01-requirements.md` | 기능·비기능 요구사항, 비즈니스 규칙, 8주 일정 | 확정 |
| 2 | `docs/02-erd.md` | 데이터 모델 개념·논리 (ERD 그림은 문서 안 mermaid). 구현 뒤 물리 명세는 `schema.sql` | 확정 |
| 3 | `docs/03-api.md` | API 명세, 에러 코드, 모듈 간 이벤트 계약 | 확정 |
| 4 | `docs/04-architecture.md` | 시스템 구성, 모듈 의존 방향, 패키지 구조, 이벤트 흐름, 보안, 실행 환경 | 초안 |
| 5 | `docs/05-screens.md` | 화면설계: 계정별 화면·메뉴, 화면 흐름, 화면별 칸·검증·버튼·쓰는 API (그림은 피그마) | 정함 |
| - | `docs/project-plan.md` | 프로젝트 계획 (목표, 우선순위, 일정·마일스톤, 검증, 리스크) | 초안 |
| - | `docs/wbs.md` | WBS: 12단계 진행 순서와 작업 상태 (매일 갱신) | 계속 갱신 |
| - | `docs/dev-plan.md` | 개발 계획 (백엔드·프론트엔드 PR 단위 작업, 완료 기준, 테스트, 정할 기술 결정) | 초안 |
| - | `docs/decisions.md` | 기술 결정 기록 (ADR) | 계속 추가 |
| - | `docs/architecture-review.md` | 시스템 아키텍처 점검 (통과한 것, 개발하면서 정할 것 A1~A12, 요구사항 대조) | 초안 |
| - | `docs/erd-review.md` | ERD 개념·논리 점검 (추적표, 시나리오, 찾은 것과 결정, 화면설계서와 대조) | 완료 (2026-11-05) |
| - | `docs/screen-interview.md` | 화면설계 인터뷰 기록 (질문과 답, 추천안대로 정한 것 W1~W14) | 완료 (2026-11-04) |
| - | `docs/worklog.md` | 작업 시간 기록 (날짜, 한 일, 걸린 시간). 일정 점검의 근거 | 계속 추가 |
| - | `docs/sessions.md` | 세션 운영 규칙(총괄·문서·백엔드·화면·배포 세션, 검토·승인 흐름)과 마무리 3주 계획 | 확정 (2026-11-18) |
| - | `docs/ui-test-report.md` | UI 테스트 결과서 (화면별 확인 항목과 결과, 가짜 데이터 기준. 12단계에서 실제 API로 다시) | 진행 (2026-11-18) |
| - | `docs/research.md` | 시장·사용자 조사 (역할별 고객관리, 비슷한 서비스 비교, 시장 규모, 확인 못 한 가설) | 참고 |

규칙

- 앞 단계 문서의 핵심(범위, 규칙)이 정해지면 다음 단계를 시작한다. 다음 단계를 쓰다 드러난 문제는 앞 문서에 반영하고, 1주 차 안에 01~03을 모두 확정한다.
- 확정된 문서를 바꾸면 그 문서 상단에 변경을 적고, 기술 결정이면 `docs/decisions.md`에 ADR로 남긴다.
- 결정 기록은 두 곳이다: 업무 규칙 결정은 01-requirements.md 8장, 기술 결정은 decisions.md.
- 구현 뒤 최신 명세는 API는 `/swagger-ui`, DB 물리 명세는 `schema.sql`이다. 03과 02는 설계 의도와 개념·논리를 남긴다 (ADR-031).

## 기술 스택

Java 21 · Spring Boot 4 · Spring Data JPA · MyBatis · MariaDB · Vue 3 · Element Plus · Docker · k3s · Jenkins
