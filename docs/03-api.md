# 03. API 명세

> 기준 문서: [01-requirements.md](01-requirements.md), [02-erd.md](02-erd.md) · 작성일: 2026-10-15 · 상태: **확정** (2026-10-15 검토 완료. 이후 변경은 decisions.md에 기록)
> 변경: 2026-10-16 정기 산정 자동 실행을 매월 1일에서 매년 6월 1일로 (01-requirements.md GRD-05).
> 변경: 2026-10-16 가족 구성원 MVP 반영 — 구성원 API 4개, 판매 라인 `memberId`(착용자), 콜리스트 `memberRelation`, `SALE_COMPLETED` 라인 `memberId`, `MEMBER_NOT_FOUND` (ADR-009).
> 변경: 2026-10-18 상품 등록은 선택, 판매는 실제 결제 금액 기록 (01-requirements.md PRD-01·02, BR-10, ADR-014·015) — 상품 등록·수정·엑셀 폼·엑셀 올리기 API, `GET /products/{id}`로 변경, 판매 라인은 `skuId` 또는 직접 입력 + `lineAmount`, `discountRate`·할인 관련 응답 필드와 422 에러 2개 삭제, `PRODUCT_IMPORT_FAILED`·`PRODUCT_CODE_DUPLICATED` 추가.
> 변경: 2026-10-18 후속 연락 API(오늘 할 일·완료·건너뛰기·설정), 판매 요청 `soldAt`은 같은 날 안의 이른 시각을 받음, `FOLLOW_UP_NOT_FOUND`·`FOLLOW_UP_ALREADY_CLOSED` 추가 (01-requirements.md CAL-06·07, BR-14, ADR-016).
> 변경: 2026-10-23 등급 실적을 시즌 단위로 (01-requirements.md BR-03, ADR-020) — 고객 상세 `grade.ticketCount12m`·`netAmount12m`, `stats.qty12m`을 `seasonTicketCount`·`seasonNetAmount`·`seasonQty`로.
> 변경: 2026-10-23 요구사항 점검 반영 (01-requirements.md 8장, ADR-021~027·029) — 역할(본사·매장·직원·KIOSK, 2장), `PATCH /me/password`·`POST /auth/logout-all`, 판매 `sellerStaffId`, 교환·정정·기록 취소·하루 마감 API(4.8~4.10), 본사 매장 목록·직원 관리 API(`/stores/{storeId}/...`, 4.11), 고객 검색은 이름만, 콜리스트 `scope`·`promoOnly`, 연락 `channel`, 알림함, 등급 `confirmedStatus`·`confirmedBasis`, 할인율 `PUT /grade-discounts`, 시즌 중 재계산 삭제, 이벤트 `kind`·`originSaleId`·`exchange`, 에러 코드 7개.
> 구현 후에는 springdoc이 만드는 `/swagger-ui`가 최신 명세다. 이 문서는 설계 의도와 규칙을 남기는 용도다.

## 1. 공통 규칙

| 항목 | 규칙 |
| --- | --- |
| 기본 경로 | `/api/v1` |
| 형식 | JSON, UTF-8. 필드 이름은 camelCase |
| 인증 | `Authorization: Bearer <accessToken>` (JWT). 공개 API(`/public/**`)와 `/auth/login`, `/auth/refresh`만 예외 |
| 시각 | ISO-8601, 한국 시간 오프셋 포함 (`2026-10-15T14:03:00+09:00`). 날짜만이면 `2026-10-15` |
| 금액 | 원 단위 정수 (`798000`). 소수 없음 |
| enum | 대문자 문자열 (`OUTER`, `GRADE`, `MANUAL`). 등급도 코드로 주고받는다 (`A`~`D`, `NORMAL`). 한글 표시명은 화면이 붙인다 |
| 성공 응답 | 감싸지 않고 리소스를 그대로 반환 |
| 목록 응답 | `{ "content": [...], "page": 0, "size": 20, "totalElements": 132, "totalPages": 7 }` |
| 페이지 요청 | `?page=0&size=20` (기본 20, 최대 100) |
| 전화번호 | 요청은 하이픈 있어도 됨. 서버가 E.164로 정규화. 응답 목록은 뒷자리만(`****-4821`, 복호화 없음), 상세만 전체. 상세의 전체 번호는 국내 번호면 `010-2943-4821`, 외국 번호면 E.164(`+819012345678`) |

**에러 응답** (모든 실패 공통)

```json
{
  "code": "CUSTOMER_NOT_FOUND",
  "message": "고객을 찾을 수 없습니다.",
  "fieldErrors": [
    { "field": "phone", "reason": "전화번호 형식이 올바르지 않습니다." }
  ],
  "traceId": "8f2c1a..."
}
```

- `fieldErrors`는 입력값 검증 실패(400)일 때만 들어간다.
- `details`는 에러별 추가 정보가 필요할 때만 들어가는 선택 필드다 (예: 3.2의 중복 고객).

| HTTP | 언제 |
| --- | --- |
| 400 | 입력값 검증 실패 (`fieldErrors` 포함) |
| 401 | 토큰 없음·만료·위조 |
| 403 | 역할 권한 없음 (예: 직원이 등급 기준 수정), 매장 계정이 아닌 계정의 판매 기록 |
| 404 | 리소스 없음 |
| 409 | 상태 충돌 (중복 등록, 이미 반품된 수량 초과 등) |
| 429 | 요청 수 제한 초과 (QR 등록) |

## 2. 역할과 접근

| 역할 그룹 | 계정 (`role`) | 접근 가능 |
| --- | --- | --- |
| 공개 | (없음) | `/public/**`, `/auth/login`, `/auth/refresh` |
| KIOSK | `KIOSK` | `/kiosk/registrations`, `GET /me`, `/public/**` 만 |
| 매장 | `STORE` (매장 계정, 매장 노트북·매장폰) | 판매·반품·교환·그날 정정·기록 취소, 하루 마감, 고객 등록·조회·수정(가족 포함), 메모·연락 결과(직원 지정), 콜리스트·오늘 할 일(매장 전체), 상품 등록·조회, 등급 기준·후속 연락 설정 **조회** |
| 직원 | `MANAGER`·`SENIOR`·`JUNIOR`·`STAFF` (개인 계정, 폰 웹앱) | 고객 조회, 내 할 일, 콜리스트(기본 내 담당), 메모(상담·기타), 알림함, 비밀번호 바꾸기. 판매·연락 결과 기록은 못 한다 |
| 본사 | `HQ` (개인 계정) | 전 매장 조회(매장 지정), 매장 목록·상세, 직원·키오스크·매장 계정 관리(등록·비활성화·이관·승격·비밀번호), 마감된 판매 정정·기록 취소, 하루 마감 다시 열기, 등급 기준·할인율·후속 연락 설정, 정기 산정 실행, 상품 등록·조회. 판매는 기록하지 못한다 |

- 아래 표의 "로그인"은 KIOSK를 뺀 모든 계정이다.
- 요청마다 계정이 활성인지와 `tokens_valid_after`를 확인한다. 비활성 계정이면 `401 AUTH_ACCOUNT_DISABLED`, 기준 시각보다 먼저 발급된 토큰이면 `401 AUTH_TOKEN_INVALID` (ADR-025).
- `/auth/refresh`는 새 Access와 새 Refresh를 함께 준다(14일 연장). 매장 계정·키오스크처럼 매일 쓰는 기기는 계속 로그인 상태다. 비활성 계정이면 `401 AUTH_ACCOUNT_DISABLED`.
- 판매 매장은 요청으로 받지 않고 매장 계정의 소속 매장(`staff.store_id`)으로 정한다. 매장 계정이 아닌 계정이 `POST /sales`를 호출하면 `403 STORE_REQUIRED`.
- 매장 계정으로 판매·연락 결과·메모를 남길 때는 직원 ID(`sellerStaffId`, `staffId`)를 함께 보낸다. 그 매장의 활성 직원(MANAGER~STAFF)이어야 한다. 직원 개인 계정이 메모를 쓰면 본인이 작성 직원이다.
- 본사 전용 매장 단위 API는 `/stores/{storeId}/...`다. 매장 계정이 같은 경로를 쓰면 자기 매장만 허용한다.

## 3. 엔드포인트 목록

### 3.1 인증 (AUTH)

| Method | Path | 설명 | 역할 | 요구사항 |
| --- | --- | --- | --- | --- |
| POST | `/auth/login` | 로그인. Access(30분) + Refresh(14일) 발급 | 공개 | AUTH-01 |
| POST | `/auth/refresh` | Access와 Refresh 재발급 (Refresh도 새로, 14일 연장) | 공개 | AUTH-01 |
| GET | `/me` | 내 정보 (이름, 역할·직급, 매니저 메인/임시, 소속 매장 id·코드·이름) | 로그인 (KIOSK 포함) | AUTH-01 |
| PATCH | `/me/password` | 비밀번호 바꾸기 (`currentPassword`, `newPassword`). 성공하면 이 계정의 다른 로그인이 끊긴다 | 직원·본사 | AUTH-04 |
| POST | `/auth/logout-all` | 모든 기기에서 로그아웃 (`tokens_valid_after` = 지금) | 직원·본사 | AUTH-03 |

- 로그아웃은 화면이 저장한 토큰을 지우는 것으로 끝난다 (서버 호출 없음). 키오스크 화면에는 로그아웃 버튼이 없다.

### 3.2 고객 등록 (REG)

| Method | Path | 설명 | 역할 | 요구사항 |
| --- | --- | --- | --- | --- |
| GET | `/public/join-info?store=GN01&staff=S017` | QR 코드 확인. 매장명, 동의 문구, 사이즈 선택지 반환 | 공개 | REG-03 |
| POST | `/public/registrations` | QR 등록. **IP당 분당 5회 제한** (프록시 뒤에서는 `X-Forwarded-For`로 IP 판별, NFR-04a) | 공개 | REG-01·03·05·06 |
| POST | `/kiosk/registrations` | 매장 iPad 등록. 매장은 토큰에서 결정 | KIOSK | REG-01·02·05·06 |
| POST | `/customers` | 직원 대리 등록 (매장 계정에서) | 매장 | REG-01·04·05 |

- 공개·키오스크 등록은 신규든 기존 번호든 **항상 같은 응답**(`200 { "message": "등록이 완료되었습니다." }`)을 준다. 기존 번호면 아무것도 바꾸지 않는다 (REG-05).
- QR의 매장 코드가 없는 코드면 `GET /public/join-info`와 `POST /public/registrations`가 `404 STORE_NOT_FOUND`.
- 키오스크는 로그인 후 `GET /me`로 역할과 소속 매장을 확인해 키오스크 모드로 들어간다. 동의 문구와 사이즈 선택지는 `GET /public/join-info?store={소속 매장 코드}`로 받는다.
- 직원 등록에서 같은 번호가 있으면 `409 CUSTOMER_PHONE_DUPLICATED`의 `details`에 기존 고객 `id`, 이름, 뒷자리 번호를 돌려준다. 화면은 "이미 등록된 고객입니다"와 기존 고객으로 이동 버튼을 보여준다.

```json
{
  "code": "CUSTOMER_PHONE_DUPLICATED",
  "message": "이미 등록된 고객입니다.",
  "details": { "customerId": 1024, "name": "김하늘", "phoneMasked": "****-4821" },
  "traceId": "8f2c1a..."
}
```

### 3.3 고객 조회·관리 (CUS · GRD · MEM · CAL)

| Method | Path | 설명 | 역할 | 요구사항 |
| --- | --- | --- | --- | --- |
| GET | `/customers?name=김하늘` | 고객명 검색(부분 일치). 목록에 뒷자리 4개 | 로그인 | CUS-01 |
| GET | `/customers/{id}` | 상세 요약 + 프로필 + 등급(현재·실적 기준·임시/확정·근거) + 등급 할인율 | 로그인 | CUS-02·04, GRD-03 |
| PATCH | `/customers/{id}` | 프로필, 담당 직원, 마케팅 수신 변경 | 매장·본사 | CUS-04 |
| GET | `/customers/{id}/purchases` | 구매이력 (영수증 단위, 최신순, 기록 취소 제외). 경로는 고객 아래지만 **sales 모듈이 제공**한다 (판매 테이블 조회) | 로그인 | CUS-03 |
| GET | `/customers/{id}/grade-history` | 등급 변경 이력 (사유, 시즌, 그때의 점수) | 로그인 | GRD-06 |
| GET | `/customers/{id}/members` | 가족 구성원 목록 | 로그인 | CUS-05 |
| POST | `/customers/{id}/members` | 구성원 추가 (호칭, 관계 필수. 성별·출생연도·사이즈 선택) | 매장 | CUS-05 |
| PATCH | `/members/{memberId}` | 구성원 수정 | 매장 | CUS-05 |
| DELETE | `/members/{memberId}` | 구성원 삭제. 과거 판매의 착용자 호칭은 남는다 | 매장 | CUS-05 |
| GET | `/customers/{id}/notes` | 메모 목록 (최신순) | 로그인 | MEM-02 |
| POST | `/customers/{id}/notes` | 메모 작성. 매장 계정은 `staffId`(작성 직원) 필수. 연락 결과(`type=CALL` + `callResult` + `channel`)는 매장 계정만 | 매장·직원 | MEM-01, CAL-02 |
| PATCH | `/notes/{noteId}` | 메모 수정 (작성 직원 본인, 그 매장의 매장 계정, 본사는 전체) | 매장·직원·본사 | MEM-03 |
| DELETE | `/notes/{noteId}` | 메모 삭제 (수정과 같은 규칙) | 매장·직원·본사 | MEM-03 |
| GET | `/call-list` | 콜리스트. 아래 파라미터 참고 | 로그인 | CAL-01 |
| GET | `/follow-ups/today` | 오늘 할 일: 만기일이 오늘이거나 지난 `OPEN` 할 일, 만기일 오래된 순. 직원은 내 할 일, 매장 계정은 매장 전체(매장 계정이 맡은 고객 포함), 본사는 `storeId` 지정 (4.7) | 로그인 | CAL-06 |
| GET | `/customers/{id}/follow-ups` | 고객의 후속 연락 할 일 (상세 화면) | 로그인 | CAL-06 |
| POST | `/follow-ups/{id}/complete` | 연락 결과를 기록하고 완료. 본문은 연락 메모와 같다(`staffId`, `callResult`, `channel`, `content`). `consult_note`(type=`CALL`)를 만들고 할 일에 연결한다 | 매장 | CAL-06, CAL-02 |
| POST | `/follow-ups/{id}/skip` | 건너뛰기 | 매장 | CAL-06 |
| GET | `/notifications` | 내 알림함 (안 읽은 것 먼저) | 직원 | CAL-09 |
| POST | `/notifications/{id}/read` | 읽음 표시 | 직원 | CAL-09 |

`GET /call-list` 파라미터 (모두 선택, 기본값은 BR-09)

| 파라미터 | 기본값 | 예 |
| --- | --- | --- |
| `scope` | 직원 `MINE`, 매장 계정 `STORE` | `MINE`(내 담당), `STORE`(이 매장 고객 전체: 이 매장에서 가입했거나 산 고객 + 매장 계정이 맡은 고객) |
| `staffId` | 없음 | `17` (특정 직원 담당만) |
| `storeId` | 매장 계정은 자기 매장 | 본사는 필수 |
| `promoOnly` | 매장 계정 `true`, 직원 `false` | 홍보 연락 가능(문자·카톡 중 하나 동의)한 고객만 |
| `minGrade` | 직원 `D`, 매장 계정 없음 | `B` (적용 중 등급, 등급 컷 점수 순서로 비교) |
| `inactiveDays` | 직원 `90`, 매장 계정 없음 | `120` |
| `target` | 없음 | `WOMEN` |
| `itemType` | 없음 | `OUTER` |
| `memberRelation` | 없음 | `CHILD` (그 관계의 가족 구성원이 있는 고객만) |
| `page`, `size` | `0`, `20` | |

`memberRelation` 값은 `SPOUSE`(배우자), `CHILD`(자녀), `PARENT`(부모), `OTHER`(기타). 구성원 API의 `relation`도 같은 값이다.

정렬은 마지막 방문일이 오래된 순. 각 항목에 `marketingAllowed`(문자·카톡 중 하나라도 동의)를 포함한다. `callResult` 값은 `COMPLETED`, `ABSENT`, `RECALL`이고, `channel` 값은 `CALL`, `SMS`, `KAKAO`다 (나중에 `APP` 추가, ADR-029).

### 3.4 상품 (PRD)

| Method | Path | 설명 | 역할 | 요구사항 |
| --- | --- | --- | --- | --- |
| GET | `/products?keyword=코트` | 상품명·품번 검색 (판매 기록 때 고르기). SKU(색상·사이즈) 포함 | 로그인 | PRD-01 |
| GET | `/products/{id}` | 상품 상세 (SKU 포함) | 로그인 | PRD-01 |
| POST | `/products` | 상품 등록. 상품명·품목·사이즈·정가 필수, 품번·대상·색상 선택. 같은 품번이 있으면 `409 PRODUCT_CODE_DUPLICATED` | 매장·본사 | PRD-01 |
| PATCH | `/products/{id}` | 상품 수정. 사이즈·색상을 빼도 과거 판매 기록은 스냅샷이라 그대로다 | 매장·본사 | PRD-01 |
| GET | `/products/import-template` | 엑셀 폼 내려받기 (.xlsx, 품목·대상 드롭다운, 예시 1행, 작성 방법 시트) | 로그인 | PRD-02 |
| POST | `/products/import` | 엑셀 올리기 (`multipart/form-data`, 최대 1,000행). 오류가 하나라도 있으면 아무것도 저장하지 않는다 (4.6) | 매장·본사 | PRD-02 |
| GET | `/public/sizes` | 품목·대상별 허용 사이즈 목록 (민감 정보가 없어 공개) | 공개 | REG-01 |

- 품번 조회(`GET /products/{productCode}`)는 품번이 선택 항목이 되면서 `GET /products/{id}`로 바꿨다. 품번으로 찾을 때는 검색(`keyword`)을 쓴다.
- 상품 목록은 브랜드 공통이다. 매장 계정과 본사 모두 등록·수정할 수 있다.

### 3.5 판매 (SAL)

| Method | Path | 설명 | 역할 | 요구사항 |
| --- | --- | --- | --- | --- |
| POST | `/sales` | 판매 기록. **`Idempotency-Key` 헤더 필수**, `sellerStaffId`(판매 사원) 필수. 매장은 매장 계정의 소속 매장. 마감된 날이면 `409 DAY_CLOSED` | 매장 | SAL-01·02·04 |
| GET | `/sales?from=2026-10-01&to=2026-10-15` | 판매 내역. 매장 계정은 자기 매장, 본사는 `storeId` 필수 | 매장·본사 | SAL-05 |
| GET | `/sales/{id}` | 영수증 상세 (라인, 판매 사원, 정가·결제 금액, 반품·교환 내역, 원 판매·정정 판매 연결) | 매장·본사 | SAL-05 |
| POST | `/sales/{id}/returns` | 반품 (라인별 수량). **`Idempotency-Key` 필수**. 마감된 날이면 `409 DAY_CLOSED` | 매장 | SAL-03 |
| POST | `/sales/{id}/exchanges` | 교환: 반품할 라인 + 새 상품 (4.8). **`Idempotency-Key` 필수** | 매장 | SAL-06 |
| PUT | `/sales/{id}` | 정정: 기록 취소 + 원래 시각으로 다시 기록 (4.9). **`Idempotency-Key` 필수**. 매장 계정은 판매한 날·마감 전만, 그 뒤는 본사 | 매장·본사 | SAL-07 |
| POST | `/sales/{id}/void` | 기록 취소 (4.9). **`Idempotency-Key` 필수**. 권한은 정정과 같다 | 매장·본사 | SAL-07 |
| POST | `/day-closes` | 오늘 판매 마감 (4.10) | 매장 | SAL-08 |
| GET | `/stores/{storeId}/day-closes?from=&to=` | 마감 현황 | 매장·본사 | SAL-08 |
| DELETE | `/stores/{storeId}/day-closes/{date}` | 마감 다시 열기 (오늘만) | 본사 | SAL-08 |

### 3.6 등급 기준 · 후속 연락 설정 (GRD · CAL)

| Method | Path | 설명 | 역할 | 요구사항 |
| --- | --- | --- | --- | --- |
| GET | `/grade-policy` | 지금 적용 중인 기준(구간, 점수, 등급 컷)과 등급 할인율(안내용). 예약된 다음 기준이 있으면 그 적용 시작일 | 로그인 | GRD-04 |
| GET | `/grade-policy/versions` | 기준 버전 목록 (적용 시작일 포함) | 본사 | GRD-04 |
| POST | `/grade-policy` | 새 기준 버전 등록. 7월 1일 ~ 5월 31일이면 `appliesFrom` = 다음 6월 1일, 6월이면 오늘이고 정기 산정을 다시 실행한다 (ADR-027) | 본사 | GRD-04, BR-05 |
| PUT | `/grade-discounts` | 등급별 할인율 저장. 바로 적용 | 본사 | GRD-04, BR-10 |
| GET | `/follow-up-rules` | 후속 연락 단계 설정 (이름, 기간·단위, 홍보 여부, 켜기) | 로그인 | CAL-07 |
| PUT | `/follow-up-rules` | 단계 설정 전체 저장. 이후 구매부터 적용되고, 이미 만든 할 일은 그대로다 | 본사 | CAL-07 |
| POST | `/grade-policy/periodic-runs` | 정기 산정 수동 실행 (6월 1일 자동 실행과 같은 처리. 몇 번 돌려도 결과가 같다. 데모용) | 본사 | GRD-05 |

시즌 중 전체 재계산 API는 없다 (ADR-027). 정기 산정은 **동기**로 처리하고, NFR-02a(고객 5천 명 30초)를 넘으면 비동기 + 상태 조회로 바꾼다.

### 3.7 관리 (ADM)

| Method | Path | 설명 | 역할 | 요구사항 |
| --- | --- | --- | --- | --- |
| GET | `/stores` | 매장 목록 (오늘 판매 수, 마감 여부, 활성 직원 수 요약) | 본사 | ADM-03 |
| GET | `/stores/{storeId}` | 매장 상세 요약 | 본사 | ADM-03 |
| GET | `/stores/{storeId}/staff` | 매장의 계정 목록 (매장 계정·직원·키오스크). 매장 계정은 판매 사원 고르기용으로 활성 직원만 받는다 | 본사·매장 | ADM-01 |
| POST | `/stores/{storeId}/staff` | 직원·키오스크 계정 등록 (이름, 직급, 매니저면 `managerSlot`, 로그인 ID, 처음 비밀번호). 매니저 자리가 차 있으면 `409 MANAGER_SLOT_TAKEN` | 본사 | ADM-01 |
| PATCH | `/staff/{id}` | 이름·직급·매니저 자리 변경 | 본사 | ADM-01 |
| POST | `/staff/{id}/deactivate` | 비활성화 + 담당 고객·열린 할 일 이관 + 매장 계정 새 비밀번호 (4.11) | 본사 | ADM-01, BR-15 |
| POST | `/staff/{id}/promote` | 임시 매니저를 메인 매니저로 | 본사 | ADM-01, BR-15 |
| POST | `/staff/{id}/handover` | 담당 고객·열린 할 일만 넘기기 (`toStaffId`). 매장 계정이 맡은 고객을 새 매니저에게 줄 때 | 본사 | ADM-01, BR-15 |
| POST | `/staff/{id}/password` | 비밀번호 다시 정하기 (매장 계정·직원·키오스크, 본사 계정은 다른 본사가). 그 계정의 기존 로그인이 끊긴다 | 본사 | AUTH-04 |
| GET | `/staff/{id}/qr` | 직원별 QR 이미지 (PNG) | 본사 | REG-03 |
| GET | `/stores/{storeId}/qr` | 매장 QR 이미지 (PNG) | 본사 | REG-03 |

- 매장 추가·정보 수정 API는 다음 단계다 (ADM-02). MVP는 시드 데이터로 매장과 매장 계정을 넣는다.

### 4.1 판매등록 `POST /sales`

요청

```http
POST /api/v1/sales
Authorization: Bearer eyJ...
Idempotency-Key: 6b0f6c1e-2f7a-4f3c-9a51-1d2c3e4f5a6b
```

```json
{
  "sellerStaffId": 17,
  "customerId": 1024,
  "lines": [
    { "skuId": 5501, "qty": 1, "lineAmount": 774060 },
    { "productName": "캐시미어 머플러", "itemType": "ACC", "size": "FREE", "color": "오트밀",
      "qty": 1, "lineAmount": 217800, "memberId": 31 }
  ],
  "memo": "요청했던 롱코트 구매. 다음 시즌 스키 바지 관심."
}
```

- `sellerStaffId`는 판매 사원이고 필수다. 그 매장의 활성 직원(MANAGER~STAFF)이 아니면 `400 VALIDATION_FAILED`, 없는 ID면 `404 STAFF_NOT_FOUND`. 매장 계정 자신은 판매 사원이 될 수 없다. 화면은 직전에 고른 직원을 기본값으로 둔다.
- 마감된 날(`store_day_close`가 있는 날)이면 `409 DAY_CLOSED` (BR-16).
- `customerId`가 없으면 비회원 판매.
- 각 라인은 **등록 상품**(`skuId`) 또는 **직접 입력**(`productName`, `itemType`, `size` 필수, `color`·`target` 선택) 중 하나다. 둘 다 있거나 둘 다 없으면 `400 VALIDATION_FAILED`. 없는 `skuId`면 `404 PRODUCT_NOT_FOUND`, 품목에 맞지 않는 사이즈면 `400 INVALID_SIZE`.
- `lineAmount`는 필수다. 직원이 확인한 **실제 결제 금액**(0원 이상)이고, 서버는 계산하지 않고 그대로 기록한다 (BR-10, ADR-014). 등록 상품이면 화면이 `정가 × 수량 × (1 − 등급 할인율)`(원 미만 버림)로 기본값을 채운다. 등급 할인율은 `GET /customers/{id}`의 `grade.discountRate`다.
- 등록 상품 라인은 서버가 상품 정보와 정가(`listPrice`)를 판매 라인에 복사해 둔다.
- 합계(`totalAmount`, `totalQty`)는 서버가 라인에서 더한다. 클라이언트가 보낸 합계는 받지 않는다.
- `memo`는 응대 메모다 (최대 1,000자). 비회원 판매에 `memo`가 있으면 `400 VALIDATION_FAILED`. 메모는 `SALE_COMPLETED` 이벤트로 고객 모듈에 전달되고, 고객 모듈이 `consult_note`(type=`PURCHASE`, `sale_id` 연결)로 저장한다 (SAL-04).
- `memberId`는 그 라인의 착용자(가족 구성원)이고 생략할 수 있다. 판매 고객의 구성원이 아니면 `404 MEMBER_NOT_FOUND`, 비회원 판매에 있으면 `400 VALIDATION_FAILED`. 서버가 구성원 호칭을 판매 라인에 복사해 둔다 (CUS-05, ADR-009).
- 판매 시각(`soldAt`)은 생략하면 서버 시각(지금)이다. 바쁠 때 몰아서 입력할 수 있게 **오늘(한국 시간) 0시 이후, 지금보다 이른 시각**은 받는다. 다른 날짜나 미래면 `400 VALIDATION_FAILED`. 영수증 번호의 판매일자는 `soldAt`의 날짜다. 더미 판매 생성기(ING-01)용으로 `seed` 프로필에서만 과거 날짜도 받는다. 정정(4.9)은 원 판매 시각을 그대로 쓴다.
- 회원 판매를 저장하면 고객 모듈이 판매 이벤트를 처리할 때 후속 연락 할 일을 만든다 (BR-14, 6장).

응답 `201 Created`

```json
{
  "id": 88231,
  "receiptNo": "GN01-20261015-0012",
  "soldAt": "2026-10-15T14:03:00+09:00",
  "kind": "NORMAL",
  "sellerStaffId": 17,
  "customerId": 1024,
  "appliedGrade": "B",
  "lines": [
    { "lineId": 1, "skuId": 5501, "productCode": "D2110", "productName": "울 블렌드 롱코트", "itemType": "OUTER",
      "color": "차콜", "size": "M", "listPrice": 798000, "qty": 1, "lineAmount": 774060 },
    { "lineId": 2, "skuId": null, "productCode": null, "productName": "캐시미어 머플러", "itemType": "ACC",
      "color": "오트밀", "size": "FREE", "listPrice": null, "qty": 1, "lineAmount": 217800,
      "wearerMemberId": 31, "wearerLabel": "남편" }
  ],
  "totalAmount": 991860,
  "totalQty": 2
}
```

- 직접 입력한 라인은 `skuId`, `productCode`, `listPrice`가 `null`이다.
- 착용자를 고르지 않은 라인은 `wearerMemberId`와 `wearerLabel`이 `null`이다 (위 예시의 첫 라인은 생략해서 보였다).

**중복 요청 방지**: 직원이 "판매 등록"을 두 번 누르거나 네트워크가 재전송해도 판매가 두 번 생기면 안 된다. 같은 `Idempotency-Key`로 다시 오면 새로 저장하지 않고 저장된 판매로 **처음과 같은 응답**을 다시 만들어 `200`으로 돌려준다. 키는 화면이 판매 화면을 열 때 만든 UUID다.

- 키는 `sale.idempotency_key`(UNIQUE)에, 요청 본문 해시는 `sale.request_hash`에 저장한다 (02-erd.md, ADR-006).
- 헤더가 없거나 UUID 형식이 아니면 `400 VALIDATION_FAILED`.
- 같은 키인데 해시가 다르면 `409 IDEMPOTENCY_KEY_REUSED`.
- 같은 키 두 요청이 동시에 오면 UNIQUE 제약으로 하나만 저장되고, 나머지는 저장된 판매를 읽어 같은 응답을 준다.
- 판매가 저장되면(`201`, 또는 재전송에 대한 `200`) 화면은 다음 판매를 위해 **새 키를 만든다**. 같은 키로 다음 판매를 보내면 `409 IDEMPOTENCY_KEY_REUSED`가 된다.

### 4.2 반품 `POST /sales/{id}/returns`

```http
POST /api/v1/sales/88231/returns
Authorization: Bearer eyJ...
Idempotency-Key: 3d8a2f4b-6c1e-4b7a-8e2d-5f9c0a1b2c3d
```

```json
{ "lines": [ { "lineId": 2, "qty": 1 } ] }
```

- **중복 요청 방지**는 판매(4.1)와 같다. 키는 화면이 반품 창을 열 때 만든 UUID이고, `sale_return.idempotency_key`(UNIQUE)와 `sale_return.request_hash`에 저장한다. 같은 키로 다시 오면 저장된 반품으로 같은 응답을 `200`으로 주고, 해시가 다르면 `409 IDEMPOTENCY_KEY_REUSED` (ADR-006). 이것이 없으면 "1개 반품"을 두 번 눌렀을 때 2개가 반품된다.
- 환불 금액 = 그 라인의 결제 금액 ÷ 판매 수량 × 반품 수량 (원 미만 버림). 그 라인의 마지막 남은 수량을 반품하면 남은 결제 금액 전부 (BR-11).
- 남은 수량보다 많이 반품하면 `409 RETURN_QTY_EXCEEDED`. 마감된 날이면 `409 DAY_CLOSED`, 기록 취소된 판매면 `409 SALE_ALREADY_VOIDED`.
- 본문에 처리한 직원 `staffId`를 함께 보낸다 (매장 계정, 2장).
- 응답 `201`: 반품 ID, 반품 금액, 판매의 새 상태(`PARTIAL_RETURNED` / `RETURNED`).

### 4.3 고객 검색 `GET /customers?name=김하늘`

```json
{
  "content": [
    { "id": 1024, "name": "김하늘", "phoneMasked": "****-4821", "grade": "B",
      "lastVisitAt": "2026-08-28T15:10:00+09:00", "staffName": "이지은" },
    { "id": 2310, "name": "김하늘", "phoneMasked": "****-0937", "grade": "NORMAL",
      "lastVisitAt": "2025-10-02T11:40:00+09:00", "staffName": "박도윤" }
  ],
  "page": 0, "size": 20, "totalElements": 2, "totalPages": 1
}
```

### 4.4 고객 상세 `GET /customers/{id}` (요약)

```json
{
  "id": 1024,
  "name": "김하늘",
  "phone": "010-2943-4821",
  "createdAt": "2025-12-06T13:05:00+09:00",
  "homeStore": { "id": 1, "name": "강남점" },
  "staff": { "id": 17, "name": "이지은" },
  "grade": {
    "current": "B", "computed": "B", "confirmed": "C", "confirmedStatus": "CONFIRMED",
    "season": "26/27",
    "seasonTicketCount": 3, "ticketScore": 30,
    "seasonNetAmount": 3420000, "amountScore": 40,
    "totalScore": 70, "discountRate": 3, "policyVersion": 2,
    "confirmedBasis": { "season": "25/26", "ticketCount": 2, "netAmount": 2500000, "totalScore": 40 }
  },
  "stats": { "firstVisitAt": "2025-12-06T13:20:00+09:00", "lastVisitAt": "2026-08-28T15:10:00+09:00",
             "seasonQty": 5, "lifetimeAmount": 9200000 },
  "profile": { "gender": "F", "birthYear": 1991, "regionSido": "서울", "regionGu": "강남구",
               "preferredItemTypes": ["OUTER", "TOP"],
               "sizes": { "top": "M", "outer": "90", "bottom": "26", "shoes": "240" },
               "contactChannel": "KAKAO" },
  "marketing": { "sms": true, "kakao": true }
}
```

`confirmedStatus`는 6월 한 달 `PROVISIONAL`(임시), 7월 1일부터 `CONFIRMED`(확정)다. `confirmedBasis`는 임시·확정 등급의 근거로, 마지막 정기 산정 이력에 남긴 점수다 (GRD-03, ADR-021).

`createdAt`은 가입일(`customer.created_at`)이다. 상세주소는 이 응답에 넣지 않는다. 필요한 화면에서만 `GET /customers/{id}?include=address`로 요청한다 (불필요한 개인정보 노출 줄이기).

### 4.5 QR 등록 `POST /public/registrations`

```json
{
  "storeCode": "GN01",
  "staffCode": "S017",
  "name": "김하늘",
  "phone": "010-2943-4821",
  "nationality": "DOMESTIC",
  "agreements": { "privacy": true, "age14": true, "marketingSms": true, "marketingKakao": false },
  "birthYear": 1991, "birthMd": "0314",
  "address": { "sido": "서울", "gu": "강남구", "detail": "[상세주소]" },
  "gender": "F",
  "preferredItemTypes": ["OUTER"],
  "sizes": { "top": "M", "outer": "90", "bottom": "26", "shoes": "240" },
  "contactChannel": "KAKAO"
}
```

- `privacy`, `age14`가 `true`가 아니면 `400`.
- 사이즈가 허용값이 아니면 `400` + `fieldErrors`.
- 값 목록: `nationality` = `DOMESTIC`, `FOREIGN` · `gender` = `F`, `M` · `contactChannel` = `CALL`, `SMS`, `KAKAO`. `gender`와 `contactChannel`은 선택 항목이라 비워도 된다.
- 같은 IP에서 1분에 5번을 넘으면 `429 TOO_MANY_REQUESTS`.

### 4.6 상품 엑셀 올리기 `POST /products/import`

`multipart/form-data`로 `file` 하나를 올린다. 파일은 `GET /products/import-template`로 받은 폼이다. 한 행이 한 상품이고, 사이즈·색상은 쉼표로 여러 개 적는다 (예: `S,M,L`).

성공 `200`

```json
{ "importedCount": 128 }
```

잘못된 행이 있으면 `400`, 아무것도 저장하지 않는다. 행 번호는 엑셀에 보이는 번호다 (머리글 다음 첫 행이 2).

```json
{
  "code": "PRODUCT_IMPORT_FAILED",
  "message": "엑셀에 잘못된 행이 있어 아무것도 저장하지 않았습니다.",
  "details": {
    "errors": [
      { "row": 23, "column": "사이즈", "reason": "'95'는 여성 하의 사이즈가 아닙니다." },
      { "row": 41, "column": "품번", "reason": "이미 등록된 품번입니다." }
    ]
  },
  "traceId": "8f2c1a..."
}
```

- 검사하는 것: 필수 칸(상품명·품목·사이즈·정가), 품목·대상 값, 품목별 사이즈 허용값(02-erd.md 3.2), 정가(0 이상 정수), 품번 중복(이미 등록된 품번과 같은 파일 안의 중복 모두).
- .xlsx가 아니거나, 폼의 시트·열 이름이 다르거나, 1,000행을 넘으면 `400 VALIDATION_FAILED`.
- 모든 행이 맞아야 한 트랜잭션으로 저장한다. 고친 뒤 같은 파일을 다시 올리면 된다.

### 4.7 오늘 할 일 `GET /follow-ups/today`

```json
{
  "content": [
    { "id": 501, "customerId": 1024, "customerName": "김하늘", "phoneMasked": "****-4821", "grade": "B",
      "stage": "착용감 확인", "promotional": false, "dueDate": "2026-10-29", "overdueDays": 0,
      "saleId": 88231, "soldAt": "2026-10-15T14:03:00+09:00", "marketingAllowed": true },
    { "id": 377, "customerId": 2310, "customerName": "박서연", "phoneMasked": "****-0937", "grade": "C",
      "stage": "신상·재방문 안내", "promotional": true, "dueDate": "2026-10-26", "overdueDays": 3,
      "saleId": 80112, "soldAt": "2026-08-26T16:40:00+09:00", "marketingAllowed": false }
  ],
  "page": 0, "size": 20, "totalElements": 2, "totalPages": 1
}
```

- 만기일이 오래된 순이라 밀린 할 일(`overdueDays` > 0)이 먼저 나온다.
- 직원 개인은 내 할 일, 매장 계정은 매장 전체 할 일을 받는다. 매장 계정이 맡은 고객의 할 일은 `assignee`가 `STORE`라 화면이 "매장 할 일"로 묶어 보여준다. 본사는 `storeId`를 지정한다.
- `promotional`이 `true`인데 `marketingAllowed`가 `false`면 화면은 "홍보 연락 불가 · 안부 연락만"을 보여준다 (BR-08, BR-14).
- 이미 완료·건너뛰기·취소된 할 일에 `complete`나 `skip`을 보내면 `409 FOLLOW_UP_ALREADY_CLOSED`.

### 4.8 교환 `POST /sales/{id}/exchanges`

```http
POST /api/v1/sales/88231/exchanges
Authorization: Bearer eyJ...
Idempotency-Key: 9c1e2b3a-4d5e-4f60-8a7b-1c2d3e4f5a6b
```

```json
{
  "staffId": 17,
  "returnLines": [ { "lineId": 1, "qty": 1 } ],
  "newLines": [
    { "skuId": 5502, "qty": 1, "lineAmount": 550000 },
    { "skuId": 5503, "qty": 1, "lineAmount": 550000 }
  ]
}
```

- `staffId`는 교환을 처리한 직원이고, 새 상품의 판매 사원도 이 직원이다.
- 한 트랜잭션으로 `sale_return`(kind=`EXCHANGE`)과 새 `sale`(kind=`EXCHANGE`, `origin_sale_id` = 원 판매)을 만든다. 새 판매는 새 영수증 번호를 받는다. 반품 라인의 환불 금액은 BR-11과 같고, 새 상품 라인 규칙은 4.1과 같다.
- 응답 `201`: `{ "returnId": 5030, "returnedAmount": 1000000, "newSale": { 4.1 응답과 같은 모양 }, "extraPaid": 100000 }`. `extraPaid` = 새 상품 합계 − 반품 금액이고, 음수면 차액 환불이다.
- 티켓은 고객 모듈이 이벤트를 받아 정한다. 추가 결제(`extraPaid` > 0)면 새 구매 건, 아니면 원 구매 건에 붙는다. 원 판매에서 이미 교환 티켓이 생겼으면 붙인다 (BR-17).
- 남은 수량 초과면 `409 RETURN_QTY_EXCEEDED`, 마감된 날이면 `409 DAY_CLOSED`. 중복 요청 방지는 4.1과 같다.

### 4.9 정정 `PUT /sales/{id}` · 기록 취소 `POST /sales/{id}/void`

- **기록 취소**: 본문은 처리한 직원 `staffId`(매장 계정일 때)뿐이다. 남은 모든 라인을 `sale_return`(kind=`VOID`)으로 돌리고 `sale.status = VOIDED`로 바꾼다. 응답 `200`: 취소된 판매.
- **정정**: 본문은 `POST /sales`와 같다(`sellerStaffId`, `customerId`, `lines`, `memo`). 원 판매를 기록 취소하고, 고친 내용으로 **원 판매 시각**에 새 판매(kind=`CORRECTION`, `origin_sale_id`)를 만든다. 새 영수증 번호를 받는다. 응답 `201`: 새 판매(4.1 응답 모양 + `originSaleId`).
- 권한: 매장 계정은 판매한 날이고 마감 전일 때만 된다. 그 밖에는 `409 SALE_LOCKED`. 본사는 마감된 판매도 고칠 수 있다 (BR-16).
- 이미 기록 취소된 판매면 `409 SALE_ALREADY_VOIDED`. 반품·교환이 있었던 판매는 남은 라인만 취소·정정한다.
- 중복 요청 방지는 4.1과 같다 (`Idempotency-Key`).

### 4.10 하루 마감 `POST /day-closes`

- 본문 없음. 매장 계정의 매장과 오늘(한국 시간) 날짜로 마감한다. 이미 마감됐으면 `200`으로 그대로 돌려준다.
- 마감된 날, 그리고 지난 날짜의 판매에는 매장 계정이 판매·반품·교환·정정·기록 취소를 할 수 없다 (`409 DAY_CLOSED`, `409 SALE_LOCKED`). 자정이 지나면 날짜로 잠기므로 따로 도는 작업은 없다.
- 본사가 `DELETE /stores/{storeId}/day-closes/{date}`로 다시 열면 그날은 다시 기록할 수 있다. 다시 열 수 있는 날은 오늘뿐이고, 지난 날짜는 본사가 정정(4.9)으로 고친다.

### 4.11 직원 비활성화 `POST /staff/{id}/deactivate`

```json
{ "handoverToStaffId": 23, "newStorePassword": "[새 매장 계정 비밀번호]" }
```

- `handoverToStaffId`는 담당 고객과 열린 할 일을 넘겨받을 계정이다. BR-15의 직급 규칙(같은 직급 이상, 메인 매니저는 매장 계정)에 맞지 않거나, 다른 매장·비활성 계정이면 `409 HANDOVER_NOT_ALLOWED`.
- `newStorePassword`는 그 매장 계정의 새 비밀번호다 (필수, AUTH-04 규칙). 바꾸면 매장 계정의 기존 로그인이 끊긴다.
- 비활성화, 이관, 매장 계정 비밀번호 변경은 한 트랜잭션이다. common 모듈이 같은 트랜잭션에서 `StaffDeactivated` Spring 이벤트를 발행하고 고객 모듈이 받아 이관한다 (ADR-024).
- 응답 `200`: `{ "movedCustomers": 312, "movedFollowUps": 18 }`. 비활성화한 계정은 다음 요청부터 바로 막힌다 (ADR-025).

## 5. 에러 코드 목록

| 코드 | HTTP | 뜻 |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | 입력값 검증 실패 |
| `AUTH_INVALID_CREDENTIALS` | 401 | 아이디·비밀번호 불일치 |
| `AUTH_TOKEN_INVALID` | 401 | 토큰 없음·형식 오류·서명 불일치 |
| `AUTH_TOKEN_EXPIRED` | 401 | 토큰 만료 |
| `AUTH_ACCOUNT_DISABLED` | 401 | 비활성화된 계정 (로그인·토큰 재발급 거절) |
| `ACCESS_DENIED` | 403 | 역할 권한 없음 |
| `STORE_REQUIRED` | 403 | 매장 계정이 아닌 계정으로 판매 기록 |
| `CUSTOMER_NOT_FOUND` | 404 | 고객 없음 |
| `PRODUCT_NOT_FOUND` | 404 | 상품·SKU 없음 |
| `PRODUCT_IMPORT_FAILED` | 400 | 상품 엑셀에 잘못된 행이 있어 아무것도 저장하지 않음. `details.errors`에 행별 오류 (4.6) |
| `PRODUCT_CODE_DUPLICATED` | 409 | 같은 품번의 상품이 이미 있음 (상품 등록·수정) |
| `SALE_NOT_FOUND` | 404 | 판매 없음 |
| `STORE_NOT_FOUND` | 404 | 매장 없음 (QR의 매장 코드 오류 포함) |
| `STAFF_NOT_FOUND` | 404 | 직원 없음 |
| `NOTE_NOT_FOUND` | 404 | 메모 없음 |
| `FOLLOW_UP_NOT_FOUND` | 404 | 후속 연락 할 일 없음 |
| `MEMBER_NOT_FOUND` | 404 | 가족 구성원 없음 (판매 요청의 `memberId`가 그 고객의 구성원이 아닐 때 포함) |
| `CUSTOMER_PHONE_DUPLICATED` | 409 | 같은 전화번호 고객 존재 (직원 등록). `details`에 기존 고객 |
| `STAFF_LOGIN_ID_DUPLICATED` | 409 | 같은 로그인 ID 존재 (직원 등록) |
| `RETURN_QTY_EXCEEDED` | 409 | 반품 가능 수량 초과 |
| `IDEMPOTENCY_KEY_REUSED` | 409 | 같은 키로 **다른 내용**의 요청 (판매·반품) |
| `FOLLOW_UP_ALREADY_CLOSED` | 409 | 이미 완료·건너뛰기·취소·대체된 할 일 |
| `INVALID_SIZE` | 400 | 품목·대상에 맞지 않는 사이즈 |
| `DAY_CLOSED` | 409 | 마감된 날에 판매·반품·교환을 기록 |
| `SALE_LOCKED` | 409 | 매장 계정이 마감된(또는 지난 날의) 판매를 정정·기록 취소 |
| `SALE_ALREADY_VOIDED` | 409 | 이미 기록 취소된 판매 |
| `MANAGER_SLOT_TAKEN` | 409 | 그 매장의 메인(또는 임시) 매니저 자리가 이미 차 있음 |
| `HANDOVER_NOT_ALLOWED` | 409 | 넘겨받을 계정이 직급 규칙·매장·활성 조건에 맞지 않음 (BR-15) |
| `PASSWORD_POLICY_VIOLATION` | 400 | 비밀번호 규칙(8자 이상, 영문과 숫자 포함) 위반 |
| `PASSWORD_MISMATCH` | 400 | 현재 비밀번호가 틀림 (비밀번호 바꾸기) |
| `TOO_MANY_REQUESTS` | 429 | 요청 수 제한 초과 |

## 6. 모듈 간 이벤트 계약 (내부)

HTTP API는 아니지만 판매 모듈과 고객 모듈 사이의 **계약**이라 여기에 고정한다. `outbox_event.payload`에 그대로 저장된다.

**SALE_COMPLETED v1**

```json
{
  "eventId": "0d6f...uuid",
  "eventType": "SALE_COMPLETED",
  "version": 1,
  "occurredAt": "2026-10-15T14:03:00+09:00",
  "saleId": 88231,
  "receiptNo": "GN01-20261015-0012",
  "storeId": 1,
  "customerId": 1024,
  "staffId": 17,
  "kind": "NORMAL",
  "originSaleId": null,
  "exchange": null,
  "memo": "요청했던 롱코트 구매. 다음 시즌 스키 바지 관심.",
  "lines": [
    { "saleLineId": 1, "target": "WOMEN", "itemType": "OUTER", "qty": 1, "lineAmount": 774060 },
    { "saleLineId": 2, "target": "UNISEX", "itemType": "ACC", "qty": 1, "lineAmount": 217800, "memberId": 31 }
  ]
}
```

**SALE_RETURNED v1**

```json
{
  "eventId": "7a1c...uuid",
  "eventType": "SALE_RETURNED",
  "version": 1,
  "occurredAt": "2026-10-16T11:20:00+09:00",
  "saleId": 88231,
  "returnId": 5021,
  "kind": "RETURN",
  "customerId": 1024,
  "lines": [ { "saleLineId": 2, "qty": 1, "amount": 217800 } ]
}
```

- `lines[].target`은 대상을 모르는 라인(직접 입력에서 대상을 비운 경우, 대상이 없는 등록 상품)이면 빠진다. 고객 모듈은 `customer_purchase.target`을 NULL로 저장하고, 콜리스트의 대상 필터에는 걸리지 않는다.
- `SALE_COMPLETED`의 `lines[].memberId`는 착용자이고, 고르지 않았으면 빠진다. 판매 사실을 그대로 담기 위한 필드로, MVP의 고객 모듈은 쓰지 않는다.
- 고객 모듈은 `SALE_COMPLETED`를 처리할 때 후속 연락 할 일도 만든다. 받는 직원은 고객의 담당 직원(없거나 비활성이면 이벤트의 `staffId`), 매장 계정이 맡은 고객이면 매장 계정이다. `kind=EXCHANGE`면 할 일을 만들지 않는다. 같은 고객의 이전 `OPEN` 할 일은 `REPLACED`로 닫는다. `SALE_RETURNED`로 그 판매가 전부 반품되거나 기록 취소(`kind=VOID`)되면 남은 할 일을 `CANCELLED`로 닫는다 (BR-14).
- `SALE_COMPLETED`의 `staffId`는 판매 사원(판매 화면에서 고른 직원)이다. `kind`는 `NORMAL`·`EXCHANGE`·`CORRECTION`이고, 교환·정정이면 `originSaleId`(원 판매)가 있다. 교환이면 `exchange: { "returnId": 5030, "returnedAmount": 1000000 }`이 들어가, 고객 모듈이 추가 결제 여부(새 상품 합계 > 반품 금액)로 구매 건을 정한다 (BR-17, ADR-022). 정정이면 `occurredAt`은 원 판매 시각이다.
- `SALE_RETURNED`의 `kind`는 `RETURN`·`EXCHANGE`·`VOID`다. `VOID`(기록 취소)면 고객 모듈은 그 구매 건을 없던 것으로 처리한다. 6월에 지난 시즌 판매가 반품·교환·기록 취소되면 임시 등급도 다시 계산한다 (BR-05).
- 교환·정정으로 생긴 판매의 이벤트는 원 판매 ID를 `aggregate_id`로 써서, 원 판매의 반품 이벤트 다음에 처리된다 (ADR-022).
- 비회원 판매(`customerId` 없음)도 이벤트는 발행한다. 고객 모듈은 `customerId`가 없으면 처리 기록만 남기고 건너뛴다.
- 같은 `saleId`의 이벤트는 발생 순서대로 전달한다. `SALE_COMPLETED`가 처리되기 전에는 `SALE_RETURNED`를 보내지 않는다 (BR-06, ADR-007).
- 발행기는 1초마다 `PENDING`을 읽고, 실패하면 최대 10회 재시도한 뒤 `FAILED`로 둔다.
- `occurredAt`은 판매·반품 시각이다. 고객 모듈은 `SALE_COMPLETED`의 `occurredAt`을 `customer_purchase.sold_at`으로 쓴다 (`seed` 프로필에서는 요청한 과거 시각).
- 필드를 추가하는 변경은 같은 버전에서 허용한다. 필드를 빼거나 뜻을 바꾸면 `version`을 올린다. 2026-10-23의 `kind`·`originSaleId`·`exchange`는 추가라 v1 그대로다.

**모듈 안 동기 이벤트 (outbox 아님)**: `StaffDeactivated`(staffId, handoverToStaffId, storeId)와 `StaffHandover`(fromStaffId, toStaffId)는 같은 트랜잭션의 Spring 이벤트다. 고객 모듈이 받아 `customer.staff_id`와 `OPEN` 할 일의 `staff_id`를 일괄로 바꾼다 (ADR-024, NFR-02a).

## 7. 화면 ↔ API

| 화면 (계정) | 쓰는 API |
| --- | --- |
| 고객 등록 폼 (QR) | `GET /public/join-info` (사이즈 선택지 포함), `POST /public/registrations` |
| 고객 등록 폼 (iPad, KIOSK) | `GET /me`, `GET /public/join-info` (동의 문구, 사이즈 선택지 포함), `POST /kiosk/registrations` |
| 판매 기록 (매장 계정, 노트북) | `GET /stores/{storeId}/staff`(판매 사원 고르기), `GET /customers?name=`, `GET /customers/{id}`, `GET /customers/{id}/members`(착용자), `GET /products?keyword=`, `POST /sales`, `POST /customers`, `GET /public/sizes` |
| 판매 내역 · 반품 · 교환 · 정정 · 마감 (매장 계정) | `GET /sales`, `GET /sales/{id}`, `POST /sales/{id}/returns`, `POST /sales/{id}/exchanges`, `PUT /sales/{id}`, `POST /sales/{id}/void`, `POST /day-closes`, `GET /stores/{storeId}/day-closes` |
| 고객등록 (매장 계정) | `GET /public/sizes`, `POST /customers` |
| 상품 관리 (매장 계정·본사) | `GET /products?keyword=`, `GET /products/{id}`, `POST /products`, `PATCH /products/{id}`, `GET /products/import-template`, `POST /products/import`, `GET /public/sizes` |
| 고객 검색 · 상세 (모두) | `GET /customers`, `GET /customers/{id}`, `PATCH /customers/{id}`, `GET /customers/{id}/purchases`, `GET /customers/{id}/grade-history`, 구성원 API, 메모 API, `GET /public/sizes` |
| 오늘 할 일 · 콜리스트 (매장폰·직원 폰) | `GET /follow-ups/today`, `GET /call-list`, `POST /follow-ups/{id}/complete`, `POST /follow-ups/{id}/skip`, `POST /customers/{id}/notes`, `GET /notifications`, `POST /notifications/{id}/read` |
| 매장 목록 · 상세 (본사) | `GET /stores`, `GET /stores/{storeId}`, `GET/POST /stores/{storeId}/staff`, `PATCH /staff/{id}`, `POST /staff/{id}/deactivate`, `POST /staff/{id}/promote`, `POST /staff/{id}/handover`, `POST /staff/{id}/password`, QR API, `GET /sales?storeId=`, `PUT /sales/{id}`, `POST /sales/{id}/void`, `GET /stores/{storeId}/day-closes`, `DELETE /stores/{storeId}/day-closes/{date}` |
| 등급 기준 · 후속 연락 설정 (본사) | `GET /grade-policy`, `GET /grade-policy/versions`, `POST /grade-policy`, `PUT /grade-discounts`, `GET /follow-up-rules`, `PUT /follow-up-rules`, `POST /grade-policy/periodic-runs` |
| 로그인 · 내 정보 | `POST /auth/login`, `POST /auth/refresh`, `GET /me`, `PATCH /me/password`, `POST /auth/logout-all` |

## 8. 확인 필요

체크하지 않은 항목은 구현 후 측정해서 정한다. 확정을 막지 않는다.

- [x] `Idempotency-Key` 저장 위치 → `sale.idempotency_key` UNIQUE + `request_hash` (ADR-006, 02-erd.md 반영)
- [x] 반품 중복 요청 → `sale_return.idempotency_key` UNIQUE + `request_hash` (ADR-006, 02-erd.md 반영)
- [ ] 정기 산정 동기 처리 시간이 NFR-02a(고객 5천 명 30초) 안인지 (구현 후 측정)
- [x] Access 30분 / Refresh 14일이 매장 사용에 맞는지 → 재발급 때 Refresh도 새로 줘서, 매일 쓰는 매장 노트북·키오스크는 계속 로그인 상태다 (ADR-025)
