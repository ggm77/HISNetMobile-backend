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

베이스 `/api/v1`. 세션 쿠키(`JSESSIONID`) 기반이며 로그인/로그아웃/Swagger 를 제외한 `/api/**` 는 모두 인증 필요.

| Method | Path | 설명 |
|---|---|---|
| `POST` | `/auth/login` | 세션 로그인 (form-urlencoded). 성공 204 + `JSESSIONID` |
| `POST` | `/auth/logout` | 세션 폐기. 204 |
| `GET`  | `/auth/me` | 로그인 사용자 확인 |
| `GET`  | `/students/me` | 학적 기본정보 |
| `GET`  | `/notices/general?page={n}` | 일반공지 목록 (페이지네이션) |
| `GET`  | `/notices/general/{id}` | 일반공지 상세 |
| `GET`  | `/notices/general/{id}/attachments/{index}` | 일반공지 첨부 다운로드 (스트리밍) |
| `GET`  | `/notices/department?dept={code}&page={n}` | 학부공지 목록 |
| `GET`  | `/notices/department/{id}?dept={code}` | 학부공지 상세 |
| `GET`  | `/notices/department/{id}/attachments/{index}?dept={code}` | 학부공지 첨부 다운로드 |

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
