# HISNet 모바일 — 백엔드

한동대학교 통합정보시스템(**HISNet**, `hisnet.handong.edu`)을 모바일 앱에서 쓰기 좋게 중계하는 프록시 API 서버.

HISNet은 공개 API가 없고 전 페이지가 EUC-KR 레거시 HTML이다. 이 서버는

1. 사용자의 HISNet 로그인을 **대신 수행**해 원본 세션 쿠키(PHPSESSID)를 확보하고,
2. 그 세션으로 원본 페이지를 대신 조회한 뒤 HTML을 **서버사이드에서 파싱**해
3. 모바일 친화적인 JSON으로 돌려준다.

**자격증명(아이디/비밀번호)은 어디에도 저장하지 않는다.** 로그인 중계 과정에서만 메모리에 존재하고, 이후에는 발급받은 원본 세션 쿠키만 서버 세션에 매핑해 둔다.

---

## 기술 스택

| | |
|---|---|
| 언어/런타임 | Java 25 |
| 프레임워크 | Spring Boot 4.1.1 (Web MVC, Security) |
| HTML 파싱 | jsoup 1.18.3 |
| API 문서 | springdoc-openapi (Swagger UI) |
| 빌드 | Gradle |

---

## 인증 흐름

```
[클라이언트] --username/password (form-urlencoded)--> [POST /api/v1/auth/login]
                                                          │
                       Security formLogin 필터 → HisnetAuthenticationProvider
                                                          │
                       HisnetLoginClient: login.php GET → _login.php POST → 세션 검증
                                                          │
                       성공: PHPSESSID/cookie_id 를 담은 principal 로 인증
                                                          ▼
[클라이언트] <--204 No Content + JSESSIONID 쿠키-- [서버]
```

- 이후 모든 조회는 클라이언트가 보낸 **JSESSIONID** → 서버 세션 → 매핑된 **PHPSESSID** 로 원본에 요청.
- `HisnetUserDetails.getPassword()` 는 항상 `null`. 비밀번호는 로그에도 남기지 않는다.
- 세션 고정 공격 방어를 위해 로그인 성공 시 세션 ID 재발급.
- 미인증/만료 시 리다이렉트 없이 **401**.

---

## API

베이스: `/api/v1`. 로그인/로그아웃/Swagger 를 제외한 `/api/**` 는 모두 인증 필요.

### Auth — `/api/v1/auth`

| Method | Path | 설명 |
|---|---|---|
| `POST` | `/login` | `application/x-www-form-urlencoded` 로 `username`, `password`. 성공 **204** + `JSESSIONID`, 실패 **401** |
| `POST` | `/logout` | 서버 세션 폐기. **204** |
| `GET`  | `/me` | 로그인 사용자 조회. `{ "username": "..." }` / 미로그인 **401** |

### Student — `/api/v1/students`

| Method | Path | 설명 |
|---|---|---|
| `GET` | `/me` | 로그인 사용자의 학적 기본정보 (원본 `HHAK110M.php`) |

```jsonc
// StudentInfoResponseDto
{
  "studentNo": "...", "name": "...", "nameEnglish": "...",
  "academicStatus": "...", "grade": "...", "nationality": "...", "birthDate": "...",
  "curriculumType": "...", "admissionDate": "...", "highSchool": "...",
  "graduationDate": null, "degreeNumber": null,
  "department": "...", "major": "...", "doubleMajor": null, "minor": null,
  "engineeringCertification": "...", "combinedDegree": null, "practicalComputing": "...",
  "rcInfo": "...", "mobile": "...", "phone": "...", "email": "...", "address": "...",
  "raw": { "성명": "...", "학번": "...", "...": "..." }   // 파싱된 라벨-값 전체
}
```

### Notice — `/api/v1/notices`

일반공지와 학부공지는 원본 핸들러가 같고 `Board` 코드만 다르다. 일반공지는 `NB0001` 고정, 학부공지는 `dept` 쿼리로 코드를 넘긴다(형식 `^[A-Z]{1,3}\d{3,4}$`).

| Method | Path | 설명 |
|---|---|---|
| `GET` | `/general?page={n}` | 일반공지 목록 (`page` 1부터, 기본 1) |
| `GET` | `/general/{id}` | 일반공지 상세 |
| `GET` | `/general/{id}/attachments/{index}?name={파일명}` | 일반공지 첨부 다운로드 (스트리밍) |
| `GET` | `/department?dept={code}&page={n}` | 학부공지 목록 |
| `GET` | `/department/{id}?dept={code}` | 학부공지 상세 |
| `GET` | `/department/{id}/attachments/{index}?dept={code}&name={파일명}` | 학부공지 첨부 다운로드 |

#### 목록 응답

```jsonc
// NoticeListResponseDto
{
  "notices": [
    {
      "id": "175708",          // read.php 가 요구하는 글 ID (화면의 게시판 순번과 다름)
      "subject": "제목",
      "files": 2,               // 첨부 개수
      "writer": "kylelee00",
      "time": "2026-09-03",     // LocalDate, 파싱 실패 시 null
      "read": 1169,             // 조회수, null 가능
      "pinned": true            // 고정공지 여부
    }
  ],
  "page": 1,
  "totalPages": 3532,           // 원본 페이저의 마지막 페이지
  "hasNext": true,
  "hasPrevious": false
}
```

- 한 페이지 일반 글 최대 15개.
- **고정공지는 원본이 모든 페이지에 반복 노출**하므로 2페이지부터는 제외한다. 1페이지에는 포함되고 `pinned: true` 로 구분.
- `page` 가 마지막 페이지를 넘으면 원본이 마지막 페이지로 클램프한다.

#### 상세 응답

```jsonc
// NoticeResponseDto
{
  "id": "175753",
  "subject": "제목",
  "files": [
    { "index": 1, "name": "첨부_안내문.pdf" }   // index = 다운로드 API 의 {index}
  ],
  "writer": "csy14(최순윤)",
  "time": "2026-09-06",
  "read": 59,
  "category": "General Info(전체 공지)",   // 학부보드엔 없어서 null 인 경우 많음
  "body": "본문 텍스트"
}
```

#### 첨부 다운로드

- 원본 `down.php` 를 스트리밍으로 릴레이한다. 응답을 메모리에 담지 않고 그대로 흘려보낸다.
- `index` 는 상세 응답의 `files[].index`.
- `name` (선택): 상세 응답의 `files[].name` 을 넘기면 그 값으로 `Content-Disposition` 을 만든다. 없으면 원본 응답 헤더(EUC-KR)를 재디코딩해 복원한다.
- 응답: `200` + 파일 스트림 + `Content-Disposition: attachment; filename*=UTF-8''…` + 확장자 기반 `Content-Type`(pdf/hwp/hwpx/doc(x)/xls(x)/ppt(x)/zip/이미지/txt, 그 외 `application/octet-stream`).
- 없는 `index` → **404** `ATTACHMENT_NOT_FOUND`, `index < 1` → **400** `INVALID_ATTACHMENT_INDEX`.

### 에러 응답

`CustomException` 경로는 아래 포맷:

```jsonc
{
  "timestamp": "2026-09-06T21:50:03.05",
  "httpStatus": "400 BAD_REQUEST",
  "code": "INVALID_DEPARTMENT",
  "message": "올바르지 않은 학부 게시판 코드입니다."
}
```

주요 코드: `LOGIN_FAILED`(401), `SESSION_EXPIRED`(401), `INVALID_DEPARTMENT`(400), `INVALID_NOTICE_ID`(400), `INVALID_ATTACHMENT_INDEX`(400), `NOTICE_NOT_FOUND`(404), `ATTACHMENT_NOT_FOUND`(404), `HISNET_REQUEST_FAILED`(502), `NOTICE_PARSING_FAILED`(500), `STUDENT_INFO_PARSING_FAILED`(500).

> 시큐리티 401, 필수 쿼리 파라미터 누락·타입 미스매치 등 Spring 기본 처리 경로는 위 포맷이 아닌 Spring 표준 에러 응답으로 나간다.

---

## 학부 게시판 코드

HISNet `list.php` 좌측 메뉴에서 확인한 값. `dept` 파라미터에 넣는다.

| 코드 | 학부/기관 | 코드 | 학부/기관 |
|---|---|---|---|
| `B0020` | 글로벌리더십학부 | `B0031` | 언어교육원 |
| `B0021` | 국제어문학부 | `B0427` | 창의융합교육원 |
| `B0022` | 경영경제학부 | `B0419` | ICT창업학부 |
| `B0023` | 법학부 | `B0431` | AI융합교육원 |
| `B0024` | 커뮤니케이션학부 | `B0434` | AI융합학부(신설) |
| `B0102` | 상담심리사회복지학부 | `B0432` | 신앙교육원 |
| `B0028` | 생명과학부 | `B0113` | 대학원공지 |
| `B0025` | 공간환경시스템공학부 | `B0114` | 대학원양식 |
| `B0029` | AI컴퓨터전자공학부 | `B0430` | 학과공지 |
| `B0027` | 콘텐츠융합디자인학부 | | |
| `B0026` | 기계제어공학부 | | |

---

## 프로젝트 구조

```
domain/
  auth/       세션 로그인 (컨트롤러는 문서화용, 실제 처리는 Security 필터)
  student/    학적 기본정보 조회 + 파서
  notice/     공지 목록/상세/첨부
    service/NoticeService            목록·상세 릴레이
    service/NoticeParser             list.php / read.php HTML 파싱
    service/NoticeAttachmentService  down.php 스트리밍 릴레이
    constant/Board                  게시판 코드 상수·검증
global/
  config/SecurityConfig             세션 로그인, CORS, 401 정책
  config/HisnetRestClientConfig     원본 릴레이 전용 RestClient
  infra/hisnet/HisnetClient         조회 GET 릴레이 + 세션 만료 판별 + 다운로드 스트리밍
  infra/hisnet/HisnetLoginClient    로그인 2단계 중계 (login.php → _login.php)
  security/HisnetAuthenticationProvider   원본 로그인 결과로 인증 판정
  security/HisnetUserDetails        principal (username + PHPSESSID + cookie_id)
  exception/                        CustomException + 전역 핸들러 + 공통 응답
```

---

## 테스트 페이지

`src/main/resources/static/test.html` — 정적 리소스로 서빙되는 단일 파일 테스트 콘솔(`/test.html`). 로그인 → 학적 조회 → 공지(일반/학부 탭, 페이지네이션, 행 클릭 시 상세 + 첨부 다운로드 링크)를 브라우저에서 확인할 수 있다. 동일 오리진으로 서빙되므로 세션 쿠키가 그대로 동작한다.

---

## 개발 노트

- **원본 HTML 구조는 추정하지 말고 실제로 확인한다.** 레거시 EUC-KR 표 레이아웃이라 클래스명/컬럼 순서가 예측 불가능하다. 로그인된 브라우저로 `list.php` / `read.php` / `down.php` 응답을 직접 열어 DOM을 보고 파서를 맞춘다.
- **jsoup `Element.select(query)` 는 컨텍스트 요소 자신도 매칭에 포함**한다 (`querySelectorAll` 과 다름). "자식이 있나" 는 `el.children().select(...)`, "안에 있나" 는 `el.closest(...)` 로 검사한다.
- 파서 로직은 실제 응답 축약본으로 `NoticeParserTest` 처럼 jsoup 단위 테스트를 붙인다.
- `id` vs `No`: 목록/상세에서 쓰는 `id` 는 `read.php?id=` 파라미터(예: `175753`)다. 화면에 보이는 게시판 순번(`344`, `고정공지`)과 다르며, 상세·첨부 조회에는 `id` 가 필요하다.
