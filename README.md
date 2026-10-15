# store-crm

패션 오프라인 매장용 클라이언텔링 CRM. 매장 웹사이트에 없던 상담 기록·콜리스트·고객 등급을, 판매등록과 연결된 웹 서비스로 제공한다.

> 상태: 기획 단계 (2026-10-15)

## 문서 (읽는 순서 = 만드는 순서)

| 순서 | 문서 | 내용 | 상태 |
| --- | --- | --- | --- |
| 0 | [docs/00-idea.md](docs/00-idea.md) | 왜 만드는가, 문제, 대상, 범위 | 확정 |
| 1 | [docs/01-requirements.md](docs/01-requirements.md) | 기능·비기능 요구사항, 비즈니스 규칙, 8주 일정 | 초안 |
| 2 | [docs/02-erd.md](docs/02-erd.md) | 데이터 모델 (ERD 그림은 문서 안 mermaid) | 확정 |
| 3 | [docs/03-api.md](docs/03-api.md) | API 명세, 에러 코드, 모듈 간 이벤트 계약 | 확정 |
| - | [docs/decisions.md](docs/decisions.md) | 기술 결정 기록 (ADR) | 계속 추가 |

규칙

- 앞 단계 문서의 핵심(범위, 규칙)이 정해지면 다음 단계를 시작한다. 다음 단계를 쓰다 드러난 문제는 앞 문서에 반영하고, 1주 차 안에 01~03을 모두 확정한다.
- 확정된 문서를 바꾸면 그 문서 상단에 변경을 적고, 기술 결정이면 `docs/decisions.md`에 ADR로 남긴다.
- 결정 기록은 두 곳이다: 업무 규칙 결정은 01-requirements.md 8장, 기술 결정은 decisions.md.

## 기술 스택 (예정)

Java · Spring Boot 3 · Spring Data JPA · MariaDB · Vue 3 · Docker Compose
