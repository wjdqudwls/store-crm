# 03. API 명세

> 기준 문서: [01-requirements.md](01-requirements.md), [02-erd.md](02-erd.md) · 작성일: 2026-10-15 · 상태: **확정** (2026-10-15 검토 완료. 이후 변경은 decisions.md에 기록)
> 변경: 2026-10-16 정기 산정 자동 실행을 매월 1일에서 매년 6월 1일로 (01-requirements.md GRD-05).
> 변경: 2026-10-16 가족 구성원 MVP 반영 — 구성원 API 4개, 판매 라인 `memberId`(착용자), 콜리스트 `memberRelation`, `SALE_COMPLETED` 라인 `memberId`, `MEMBER_NOT_FOUND` (ADR-009).
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
| 403 | 역할 권한 없음 (예: STAFF가 등급 기준 수정), 소속 매장이 없는 계정의 판매 등록 |
| 404 | 리소스 없음 |
| 409 | 상태 충돌 (중복 등록, 이미 반품된 수량 초과 등) |
| 422 | 업무 규칙 위반 (할인 상한 초과, 비회원 할인 시도 등) |
| 429 | 요청 수 제한 초과 (QR 등록) |

## 2. 역할과 접근

| 역할 | 접근 가능 |
| --- | --- |
| (없음) | `/public/**`, `/auth/login`, `/auth/refresh` |
| KIOSK | `/kiosk/registrations`, `GET /me`, `/public/**` 만 |
| STAFF (매장 직원·매장 매니저) | 판매·반품, 고객 등록·조회·수정(가족 구성원 포함), 메모, 콜리스트, 상품 조회, 등급 기준 **조회** |
| MANAGER (본사) | STAFF 전부 + 등급 기준 수정·재산정, 직원 관리 |

- `/auth/refresh`는 토큰이 유효해도 `staff.active = false`면 `401 AUTH_ACCOUNT_DISABLED`로 거절한다. 비활성화한 직원·키오스크가 Refresh 유효기간(14일) 동안 계속 쓰는 것을 막는다.
- 판매 매장은 요청으로 받지 않고 로그인한 계정의 소속 매장(`staff.store_id`)으로 정한다. 소속 매장이 없는 계정(본사 MANAGER)이 `POST /sales`를 호출하면 `403 STORE_REQUIRED`.

## 3. 엔드포인트 목록

### 3.1 인증 (AUTH)

| Method | Path | 설명 | 역할 | 요구사항 |
| --- | --- | --- | --- | --- |
| POST | `/auth/login` | 로그인. Access(30분) + Refresh(14일) 발급 | 공개 | AUTH-01 |
| POST | `/auth/refresh` | Access 재발급 | 공개 | AUTH-01 |
| GET | `/me` | 내 정보 (이름, 역할, 소속 매장 id·코드·이름) | 로그인 (KIOSK 포함) | AUTH-01 |

### 3.2 고객 등록 (REG)

| Method | Path | 설명 | 역할 | 요구사항 |
| --- | --- | --- | --- | --- |
| GET | `/public/join-info?store=GN01&staff=S017` | QR 코드 확인. 매장명, 동의 문구, 사이즈 선택지 반환 | 공개 | REG-03 |
| POST | `/public/registrations` | QR 등록. **IP당 분당 5회 제한** (프록시 뒤에서는 `X-Forwarded-For`로 IP 판별, NFR-04a) | 공개 | REG-01·03·05·06 |
| POST | `/kiosk/registrations` | 매장 iPad 등록. 매장은 토큰에서 결정 | KIOSK | REG-01·02·05·06 |
| POST | `/customers` | 직원 대리 등록 | STAFF | REG-01·04·05 |

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
| GET | `/customers?name=김하늘&last4=4821` | 고객명 검색(부분 일치), 뒷자리로 좁히기 | STAFF | CUS-01 |
| GET | `/customers/{id}` | 상세 요약 + 프로필 + 등급(현재·실적 기준·근거) + 등급 할인율 | STAFF | CUS-02·04, GRD-03 |
| PATCH | `/customers/{id}` | 프로필, 담당 직원, 마케팅 수신 변경 | STAFF | CUS-04 |
| GET | `/customers/{id}/purchases` | 구매이력 (영수증 단위, 최신순). 경로는 고객 아래지만 **sales 모듈이 제공**한다 (판매 테이블 조회) | STAFF | CUS-03 |
| GET | `/customers/{id}/grade-history` | 등급 변경 이력 | STAFF | GRD-06 |
| GET | `/customers/{id}/members` | 가족 구성원 목록 | STAFF | CUS-05 |
| POST | `/customers/{id}/members` | 구성원 추가 (호칭, 관계 필수. 성별·출생연도·사이즈 선택) | STAFF | CUS-05 |
| PATCH | `/members/{memberId}` | 구성원 수정 | STAFF | CUS-05 |
| DELETE | `/members/{memberId}` | 구성원 삭제. 과거 판매의 착용자 호칭은 남는다 | STAFF | CUS-05 |
| GET | `/customers/{id}/notes` | 메모 목록 (최신순) | STAFF | MEM-02 |
| POST | `/customers/{id}/notes` | 메모 작성. 연락 결과는 `type=CALL` + `callResult` | STAFF | MEM-01, CAL-02 |
| PATCH | `/notes/{noteId}` | 메모 수정 (본인, MANAGER는 전체) | STAFF | MEM-03 |
| DELETE | `/notes/{noteId}` | 메모 삭제 (본인, MANAGER는 전체) | STAFF | MEM-03 |
| GET | `/call-list` | 콜리스트. 아래 파라미터 참고 | STAFF | CAL-01 |

`GET /call-list` 파라미터 (모두 선택, 기본값은 BR-09)

| 파라미터 | 기본값 | 예 |
| --- | --- | --- |
| `staffId` | 로그인한 본인 | `17` |
| `minGrade` | `D` | `B` (등급 컷 점수 순서로 비교) |
| `inactiveDays` | `90` | `120` |
| `target` | 없음 | `WOMEN` |
| `itemType` | 없음 | `OUTER` |
| `memberRelation` | 없음 | `CHILD` (그 관계의 가족 구성원이 있는 고객만) |
| `page`, `size` | `0`, `20` | |

`memberRelation` 값은 `SPOUSE`(배우자), `CHILD`(자녀), `PARENT`(부모), `OTHER`(기타). 구성원 API의 `relation`도 같은 값이다.

정렬은 마지막 방문일이 오래된 순. 각 항목에 `marketingAllowed`(문자·카톡 중 하나라도 동의) 를 포함한다.

### 3.4 상품 (PRD)

| Method | Path | 설명 | 역할 | 요구사항 |
| --- | --- | --- | --- | --- |
| GET | `/products/{productCode}` | 품번 조회. SKU(색상·사이즈) 목록 포함 | STAFF | PRD-01 |
| GET | `/products?keyword=코트` | 품번·품명 검색 (판매등록 자동완성) | STAFF | PRD-01 |
| GET | `/public/sizes` | 품목·대상별 허용 사이즈 목록 (민감 정보가 없어 공개) | 공개 | REG-01 |

### 3.5 판매 (SAL)

| Method | Path | 설명 | 역할 | 요구사항 |
| --- | --- | --- | --- | --- |
| POST | `/sales` | 판매등록. **`Idempotency-Key` 헤더 필수**. 매장은 로그인한 계정의 소속 매장 | STAFF | SAL-01·02·04 |
| GET | `/sales?storeId=1&from=2026-10-01&to=2026-10-15` | 판매 내역. `storeId`를 빼면 내 매장 | STAFF | SAL-05 |
| GET | `/sales/{id}` | 영수증 상세 (라인, 할인, 반품 내역) | STAFF | SAL-05 |
| POST | `/sales/{id}/returns` | 반품 (라인별 수량). **`Idempotency-Key` 헤더 필수** | STAFF | SAL-03 |

### 3.6 등급 기준 (GRD)

| Method | Path | 설명 | 역할 | 요구사항 |
| --- | --- | --- | --- | --- |
| GET | `/grade-policy` | 현재 기준 (구간, 점수, 등급 컷, 등급 할인율, 수동 할인 상한) | STAFF | GRD-04 |
| GET | `/grade-policy/versions` | 기준 버전 목록 | MANAGER | GRD-04 |
| POST | `/grade-policy` | 새 기준 버전 등록 → 전체 재계산 (승급만 즉시) | MANAGER | GRD-04, BR-05 |
| POST | `/grade-policy/periodic-runs` | 정기 산정 수동 실행 (매년 6월 1일 자동 실행과 같은 처리. 데모용) | MANAGER | GRD-05 |

MVP는 재계산을 **동기**로 처리한다(고객 5천 명 기준, 처리 시간은 측정 후 판단). 오래 걸리면 비동기 + 상태 조회로 바꾼다.

### 3.7 관리 (ADM)

| Method | Path | 설명 | 역할 | 요구사항 |
| --- | --- | --- | --- | --- |
| GET | `/staff` | 직원 목록 (매장 필터) | MANAGER | ADM-01 |
| POST | `/staff` | 직원·키오스크 계정 등록 | MANAGER | ADM-01 |
| PATCH | `/staff/{id}` | 역할, 소속, 비활성화 | MANAGER | ADM-01 |
| GET | `/staff/{id}/qr` | 직원별 QR 이미지 (PNG) | MANAGER | REG-03 |
| GET | `/stores` | 매장 목록 (선택 상자용) | STAFF | - |
| GET | `/stores/{id}/qr` | 매장 QR 이미지 (PNG) | MANAGER | REG-03 |

## 4. 주요 요청·응답 예시

### 4.1 판매등록 `POST /sales`

요청

```http
POST /api/v1/sales
Authorization: Bearer eyJ...
Idempotency-Key: 6b0f6c1e-2f7a-4f3c-9a51-1d2c3e4f5a6b
```

```json
{
  "customerId": 1024,
  "lines": [
    { "skuId": 5501, "qty": 1, "discountRate": 3 },
    { "skuId": 7302, "qty": 1, "discountRate": 10, "memberId": 31 }
  ],
  "memo": "요청했던 롱코트 구매. 다음 시즌 스키 바지 관심."
}
```

- `customerId`가 없으면 비회원 판매. 이때 `discountRate`가 0이 아니면 `422 NON_MEMBER_DISCOUNT_NOT_ALLOWED`.
- `memo`는 응대 메모다 (최대 1,000자). 비회원 판매에 `memo`가 있으면 `400 VALIDATION_FAILED`. 메모는 `SALE_COMPLETED` 이벤트로 고객 모듈에 전달되고, 고객 모듈이 `consult_note`(type=`PURCHASE`, `sale_id` 연결)로 저장한다 (SAL-04).
- `discountRate`를 생략하면 고객의 현재 등급 할인율이 들어간다(`GRADE`). 값을 보내고 그 값이 등급 할인율과 다르면 `MANUAL`로 기록된다.
- 할인율이 상한(`manualDiscountMax`)을 넘으면 `422 DISCOUNT_RATE_EXCEEDED`.
- `memberId`는 그 라인의 착용자(가족 구성원)이고 생략할 수 있다. 판매 고객의 구성원이 아니면 `404 MEMBER_NOT_FOUND`, 비회원 판매에 있으면 `400 VALIDATION_FAILED`. 서버가 구성원 호칭을 판매 라인에 복사해 둔다 (CUS-05, ADR-009).
- 금액 계산은 **서버가 한다**. 클라이언트가 보낸 합계는 믿지 않는다.
- 실판매가(`unitPrice`)는 요청으로 받지 않는다. MVP는 상품 정가로 고정한다 (BR-10).
- 판매 시각(`soldAt`)은 서버 시각이다. 더미 판매 생성기(ING-01)용으로 `seed` 프로필에서만 요청의 `soldAt`을 받는다. 운영 프로필에서 보내면 무시한다.

응답 `201 Created`

```json
{
  "id": 88231,
  "receiptNo": "GN01-20261015-0012",
  "soldAt": "2026-10-15T14:03:00+09:00",
  "customerId": 1024,
  "appliedGrade": "B",
  "lines": [
    { "lineId": 1, "productCode": "D2110", "productName": "울 블렌드 롱코트", "color": "차콜", "size": "M",
      "unitPrice": 798000, "qty": 1, "discountRate": 3, "discountSource": "GRADE",
      "discountAmount": 23940, "lineAmount": 774060 },
    { "lineId": 2, "productCode": "K3402", "productName": "캐시미어 머플러", "color": "오트밀", "size": "FREE",
      "unitPrice": 242000, "qty": 1, "discountRate": 10, "discountSource": "MANUAL",
      "discountAmount": 24200, "lineAmount": 217800, "wearerMemberId": 31, "wearerLabel": "남편" }
  ],
  "totalDiscount": 48140,
  "totalAmount": 991860,
  "totalQty": 2
}
```

착용자를 고르지 않은 라인은 `wearerMemberId`와 `wearerLabel`이 `null`이다 (위 예시의 첫 라인은 생략해서 보였다).

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
- 남은 수량보다 많이 반품하면 `409 RETURN_QTY_EXCEEDED`.
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
    "current": "B", "computed": "B", "confirmed": "C",
    "ticketCount12m": 3, "ticketScore": 30,
    "netAmount12m": 3420000, "amountScore": 40,
    "totalScore": 70, "discountRate": 3, "policyVersion": 2
  },
  "stats": { "firstVisitAt": "2025-12-06T13:20:00+09:00", "lastVisitAt": "2026-08-28T15:10:00+09:00",
             "qty12m": 5, "lifetimeAmount": 9200000 },
  "profile": { "gender": "F", "birthYear": 1991, "regionSido": "서울", "regionGu": "강남구",
               "preferredItemTypes": ["OUTER", "TOP"],
               "sizes": { "top": "M", "outer": "90", "bottom": "26", "shoes": "240" },
               "contactChannel": "KAKAO" },
  "marketing": { "sms": true, "kakao": true }
}
```

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

## 5. 에러 코드 목록

| 코드 | HTTP | 뜻 |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | 입력값 검증 실패 |
| `AUTH_INVALID_CREDENTIALS` | 401 | 아이디·비밀번호 불일치 |
| `AUTH_TOKEN_INVALID` | 401 | 토큰 없음·형식 오류·서명 불일치 |
| `AUTH_TOKEN_EXPIRED` | 401 | 토큰 만료 |
| `AUTH_ACCOUNT_DISABLED` | 401 | 비활성화된 계정 (로그인·토큰 재발급 거절) |
| `ACCESS_DENIED` | 403 | 역할 권한 없음 |
| `STORE_REQUIRED` | 403 | 소속 매장이 없는 계정으로 판매 등록 |
| `CUSTOMER_NOT_FOUND` | 404 | 고객 없음 |
| `PRODUCT_NOT_FOUND` | 404 | 품번·SKU 없음 |
| `SALE_NOT_FOUND` | 404 | 판매 없음 |
| `STORE_NOT_FOUND` | 404 | 매장 없음 (QR의 매장 코드 오류 포함) |
| `STAFF_NOT_FOUND` | 404 | 직원 없음 |
| `NOTE_NOT_FOUND` | 404 | 메모 없음 |
| `MEMBER_NOT_FOUND` | 404 | 가족 구성원 없음 (판매 요청의 `memberId`가 그 고객의 구성원이 아닐 때 포함) |
| `CUSTOMER_PHONE_DUPLICATED` | 409 | 같은 전화번호 고객 존재 (직원 등록). `details`에 기존 고객 |
| `STAFF_LOGIN_ID_DUPLICATED` | 409 | 같은 로그인 ID 존재 (직원 등록) |
| `RETURN_QTY_EXCEEDED` | 409 | 반품 가능 수량 초과 |
| `IDEMPOTENCY_KEY_REUSED` | 409 | 같은 키로 **다른 내용**의 요청 (판매·반품) |
| `NON_MEMBER_DISCOUNT_NOT_ALLOWED` | 422 | 비회원 판매에 할인 |
| `DISCOUNT_RATE_EXCEEDED` | 422 | 수동 할인 상한 초과 |
| `INVALID_SIZE` | 400 | 품목·대상에 맞지 않는 사이즈 |
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
  "customerId": 1024,
  "lines": [ { "saleLineId": 2, "qty": 1, "amount": 217800 } ]
}
```

- `SALE_COMPLETED`의 `lines[].memberId`는 착용자이고, 고르지 않았으면 빠진다. 판매 사실을 그대로 담기 위한 필드로, MVP의 고객 모듈은 쓰지 않는다.
- 비회원 판매(`customerId` 없음)도 이벤트는 발행한다. 고객 모듈은 `customerId`가 없으면 처리 기록만 남기고 건너뛴다.
- 같은 `saleId`의 이벤트는 발생 순서대로 전달한다. `SALE_COMPLETED`가 처리되기 전에는 `SALE_RETURNED`를 보내지 않는다 (BR-06, ADR-007).
- 발행기는 1초마다 `PENDING`을 읽고, 실패하면 최대 10회 재시도한 뒤 `FAILED`로 둔다.
- `occurredAt`은 판매·반품 시각이다. 고객 모듈은 `SALE_COMPLETED`의 `occurredAt`을 `customer_purchase.sold_at`으로 쓴다 (`seed` 프로필에서는 요청한 과거 시각).
- 필드를 추가하는 변경은 같은 버전에서 허용한다. 필드를 빼거나 뜻을 바꾸면 `version`을 올린다.

## 7. 화면 ↔ API

| 화면 | 쓰는 API |
| --- | --- |
| 고객 등록 폼 (QR) | `GET /public/join-info` (사이즈 선택지 포함), `POST /public/registrations` |
| 고객 등록 폼 (iPad) | `GET /me`, `GET /public/join-info` (동의 문구, 사이즈 선택지 포함), `POST /kiosk/registrations` |
| 판매등록 | `GET /customers?name=`, `GET /customers/{id}`, `GET /customers/{id}/members`(착용자 선택), `GET /products?keyword=`, `GET /products/{code}`, `GET /grade-policy`, `POST /sales`, `POST /customers`, `GET /public/sizes` |
| 고객등록 (직원) | `GET /public/sizes`, `POST /customers` |
| 판매 내역 · 반품 | `GET /sales`, `GET /sales/{id}`, `POST /sales/{id}/returns` |
| 고객 검색 · 상세 | `GET /customers`, `GET /customers/{id}`, `PATCH /customers/{id}`, `GET /customers/{id}/purchases`, `GET /customers/{id}/grade-history`, 구성원 API, 메모 API, `GET /public/sizes` |
| 콜리스트 | `GET /call-list`, `POST /customers/{id}/notes` |
| 등급 기준 | `GET /grade-policy`, `GET /grade-policy/versions`, `POST /grade-policy`, `POST /grade-policy/periodic-runs` |
| 직원 관리 | `GET/POST/PATCH /staff`, `GET /staff/{id}/qr`, `GET /stores`, `GET /stores/{id}/qr` |
| 로그인 | `POST /auth/login`, `POST /auth/refresh`, `GET /me` |

## 8. 확인 필요

체크하지 않은 두 항목은 구현 후 측정해서 정한다. 확정을 막지 않는다.

- [x] `Idempotency-Key` 저장 위치 → `sale.idempotency_key` UNIQUE + `request_hash` (ADR-006, 02-erd.md 반영)
- [x] 반품 중복 요청 → `sale_return.idempotency_key` UNIQUE + `request_hash` (ADR-006, 02-erd.md 반영)
- [ ] 재계산 동기 처리 시간이 괜찮은지 (구현 후 측정)
- [ ] Access 30분 / Refresh 14일이 매장 사용에 맞는지 (매장 PC는 하루 종일 켜 둠)
