# CLAUDE.md

CRM pos (레포 `store-crm`): 패션 오프라인 매장용 클라이언텔링 CRM. 1인 포트폴리오 프로젝트, 8주 일정.

## 대화와 작업 방식

- 한국어로, 쉬운 말로 설명한다.
- 이번 프로젝트의 목표는 개발 순서를 직접 익히는 것이다 (01-requirements.md 8장). 요청이 없으면 큰 코드를 한 번에 만들지 말고, 설명·검토·막힌 곳 풀기를 중심으로 돕는다. 사용자가 만들어 달라고 하면 만든다.
- 커밋과 push는 사용자에게 확인받고 한다. 브랜치 없이 `main`에 바로 커밋한다.
- 커밋 메시지는 `docs: …`, `feat: …`, `fix: …`, `chore: …`, `test: …` 형식으로 쓴다.

## 문서 읽는 순서

작업 전에 관련 문서를 먼저 읽는다. 코드는 문서를 따른다. 문서와 코드가 다르거나 문서에 없는 결정이 필요하면 혼자 정하지 말고 사용자에게 묻는다.

1. `README.md`: 문서 목록과 규칙
2. `docs/00-idea.md`: 왜 만드는가, 범위
3. `docs/01-requirements.md`: 기능(SAL, REG, CUS, GRD, MEM, CAL, AUTH, ADM), 업무 규칙(BR), 비기능(NFR), 일정, 8장 결정 기록
4. `docs/02-erd.md`: 테이블, 컬럼, 인덱스
5. `docs/03-api.md`: API, 에러 코드, 모듈 간 이벤트 계약
6. `docs/decisions.md`: 기술 결정(ADR)

필요한 부분은 ID로 찾는다 (예: `SAL-01`, `BR-05`, `NFR-04`, `ADR-006`).

## 문서 변경 규칙

- 00~03은 확정된 문서다. 바꾸면 그 문서 상단에 `> 변경: 날짜 내용` 줄을 추가한다.
- 업무 규칙 결정은 01-requirements.md 8장 표에 행을 추가한다. 기술 결정은 `decisions.md`에 ADR로 남긴다 (번호는 마지막 ADR 다음).
- 바뀐 결정은 지우지 않고 '대체됨'으로 표시한다.
- 한 결정이 여러 문서에 걸치면 01·02·03·ADR을 함께 고쳐 용어와 값을 맞춘다.
- 문서는 한국어로 쓴다.
- 구현 후에는 `/swagger-ui`가 최신 API 명세다. 03은 설계 의도를 남기는 문서다.

## 코드 구조

- `backend/`: Spring Boot 4, Java 21, Gradle (Groovy)
- `frontend/`: Vue 3 (아직 없음)
- 패키지 루트는 `com.crmpos`. 모듈은 `com.crmpos.sales`, `com.crmpos.customer`, `com.crmpos.product`, `com.crmpos.common` 4개다 (ADR-001).
- 모듈끼리는 FK 없이 ID만 들고 있는다. 고객 모듈은 판매 테이블을 조인하지 않고, 이벤트로 받은 `customer_purchase`를 쓴다 (ADR-003).
- 판매 → 고객관리는 outbox 이벤트로 연결한다 (ADR-002, ADR-007).

## DTO 규칙

- 요청·응답 DTO는 Java `record`로 만든다.
- 엔티티 → DTO 변환은 record 안의 `static from(...)` 메서드로 직접 한다. ModelMapper, MapStruct는 쓰지 않는다.
- 엔티티를 API 응답으로 그대로 내보내지 않는다.
- JSON 필드는 camelCase, enum은 대문자 문자열로 주고받는다 (03-api.md 1장).

## 롬복 규칙

- 엔티티에는 `@Getter`와 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`만 쓴다.
- 엔티티에 `@Data`, `@Setter`, `@ToString`, `@EqualsAndHashCode`를 쓰지 않는다. 값은 setter 대신 뜻이 있는 메서드로 바꾼다 (예: 승급, 반품 처리).
- 생성자 주입은 `@RequiredArgsConstructor`, 로그는 `@Slf4j`를 쓴다.

## 의존성

- 새 의존성은 필요할 때 추가한다. 예정: Spring Security + jjwt(로그인), springdoc-openapi(NFR-12), Testcontainers MariaDB(통합 테스트).
- 쓰지 않는다: OpenFeign, Kafka, Redis, MyBatis, ModelMapper. 통합 테스트에 H2 대신 Testcontainers MariaDB를 쓴다 (`FOR UPDATE`, JSON 컬럼, UNIQUE 제약을 실제 DB로 확인해야 하므로).

## 보안

- 비밀번호, 암호화 키, JWT 시크릿은 환경변수로 받는다. 코드와 설정 파일에 직접 쓰지 않는다 (NFR-06).
- `.env`는 커밋하지 않는다. 예시는 `.env.example`에만 둔다.
- 실제 고객 데이터를 레포에 올리지 않는다. 문서·코드·테스트 예시는 가상 데이터만 쓴다 (NFR-07).

## 명령어

```bash
cp .env.example .env              # 처음 한 번, 값은 직접 바꾼다
docker compose up -d              # 로컬 DB (MariaDB 11, 포트 3316, NFR-11)
cd backend && ./gradlew test      # 테스트 (DB 필요)
cd backend && ./gradlew bootRun   # 실행 → http://localhost:8080/actuator/health
```

앱은 레포 맨 위의 `.env`를 읽어 DB에 접속한다 (`application.properties`의 `spring.config.import`).
