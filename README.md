# store-crm

패션 오프라인 매장용 클라이언텔링 CRM. 매장 웹사이트에 없던 상담 기록·콜리스트·고객 등급을, 판매등록과 연결된 웹 서비스로 제공한다.

> 서비스 이름: CRM pos · 상태: 기획 단계 (2026-10-15)

## 문서 (읽는 순서 = 만드는 순서)

| 순서 | 문서 | 내용 | 상태 |
| --- | --- | --- | --- |
| 0 | [docs/00-idea.md](docs/00-idea.md) | 왜 만드는가, 문제, 대상, 범위 | 확정 |
| 1 | [docs/01-requirements.md](docs/01-requirements.md) | 기능·비기능 요구사항, 비즈니스 규칙, 8주 일정 | 확정 |
| 2 | [docs/02-erd.md](docs/02-erd.md) | 데이터 모델 (ERD 그림은 문서 안 mermaid) | 확정 |
| 3 | [docs/03-api.md](docs/03-api.md) | API 명세, 에러 코드, 모듈 간 이벤트 계약 | 확정 |
| 4 | [docs/04-architecture.md](docs/04-architecture.md) | 시스템 구성, 모듈 의존 방향, 패키지 구조, 이벤트 흐름, 보안, 실행 환경 | 초안 |
| - | [docs/project-plan.md](docs/project-plan.md) | 프로젝트 계획 (목표, 우선순위, 일정·마일스톤, 검증, 리스크) | 초안 |
| - | [docs/dev-plan.md](docs/dev-plan.md) | 개발 계획 (백엔드·프론트엔드 PR 단위 작업, 완료 기준, 테스트, 정할 기술 결정) | 초안 |
| - | [docs/decisions.md](docs/decisions.md) | 기술 결정 기록 (ADR) | 계속 추가 |
| - | [docs/research.md](docs/research.md) | 시장·사용자 조사 (역할별 고객관리, 비슷한 서비스 비교, 시장 규모, 확인 못 한 가설) | 참고 |

규칙

- 앞 단계 문서의 핵심(범위, 규칙)이 정해지면 다음 단계를 시작한다. 다음 단계를 쓰다 드러난 문제는 앞 문서에 반영하고, 1주 차 안에 01~03을 모두 확정한다.
- 확정된 문서를 바꾸면 그 문서 상단에 변경을 적고, 기술 결정이면 `docs/decisions.md`에 ADR로 남긴다.
- 결정 기록은 두 곳이다: 업무 규칙 결정은 01-requirements.md 8장, 기술 결정은 decisions.md.

## 기술 스택 (예정)

Java 21 · Spring Boot 4 · Spring Data JPA · MariaDB · Vue 3 · Docker Compose
