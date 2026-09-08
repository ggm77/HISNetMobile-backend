# HISNet 모바일 백엔드 — API 명세

버전 `0.0.1` · 베이스 경로 `/api/v1`

원본 HISNet(`hisnet.handong.edu`)을 중계하는 프록시 API. 모든 데이터는 요청 시점에 원본 페이지를 조회·파싱해 만든다(캐시 없음).

---

## 공통 규약

### 인증

- **세션 쿠키(`JSESSIONID`) 기반.** `POST /auth/login` 성공 시 발급되며, 이후 모든 요청에 자동 전송된다(브라우저) 또는 직접 실어 보낸다(네이티브).
- 로그인/로그아웃/**식단표(`GET /meals`)**/Swagger 를 제외한 **`/api/**` 는 전부 인증 필요.** 미인증 요청은 `401`.
- 서버 세션은 내부적으로 원본 세션 쿠키(PHPSESSID)에 매핑된다. 자격증명은 저장하지 않는다.
- 세션 TTL 기본 30분. 원본 세션이 먼저 만료되면 조회 API 가 `401 SESSION_EXPIRED` 를 반환하므로, 클라이언트는 재로그인을 유도해야 한다.

### CORS

- 크로스오리진 SPA 를 위해 `Access-Control-Allow-Credentials: true` + 허용 오리진(설정값)만 응답한다.
- 브라우저 클라이언트는 `fetch(..., { credentials: 'include' })` 필요.

### 요청/응답 형식

- 요청 바디가 있는 곳은 로그인뿐이며 `application/x-www-form-urlencoded`.
- 응답은 `application/json; charset=UTF-8` (첨부 다운로드는 바이너리).
- 날짜(`time`, `date`)는 `yyyy-MM-dd` 문자열. 파싱 실패 시 `null`.
- 수치 필드는 원본에 값이 없거나 파싱 실패 시 `null`. 리스트 필드는 없으면 빈 배열.

### 에러 응답

`CustomException` 경로는 아래 공통 포맷:

```json
{
  "timestamp": "2026-09-08T21:50:03.05",
  "httpStatus": "400 BAD_REQUEST",
  "code": "INVALID_NOTICE_BOARD",
  "message": "올바르지 않은 공지 게시판입니다."
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `timestamp` | string | 서버 시각 (`LocalDateTime`, 오프셋 없음) |
| `httpStatus` | string | `"<코드> <이름>"` 형식 |
| `code` | string | 아래 [에러 코드](#에러-코드) 표의 enum 이름 |
| `message` | string | 사용자 표시용 한글 메시지 |

> Spring Security 의 `401`, 필수 쿼리 파라미터 누락·타입 미스매치(`400`) 등 프레임워크 기본 처리 경로는 이 포맷이 아니라 Spring 표준 에러 응답으로 나간다.

### 엔드포인트 요약

| Method | Path | 인증 | 설명 |
|---|---|:---:|---|
| `POST` | `/auth/login` | — | 세션 로그인 (form-urlencoded) |
| `POST` | `/auth/logout` | — | 세션 폐기 |
| `GET` | `/auth/me` | ✔ | 로그인 사용자 확인 |
| `GET` | `/students/me` | ✔ | 학적 기본정보 |
| `GET` | `/timetable` | ✔ | 내 시간표 (현재 학기) |
| `GET` | `/grades` | ✔ | 내 성적 (누적 + 학기별) |
| `GET` | `/graduation` | ✔ | 내 졸업심사 결과 |
| `GET` | `/meals` | — | 당일 식단표 |
| `GET` | `/notices/{board}` | ✔ | 고정 게시판 공지 목록 (`general`/`scholarship`/`dormitory`) |
| `GET` | `/notices/{board}/{id}` | ✔ | 고정 게시판 공지 상세 |
| `GET` | `/notices/{board}/{id}/attachments/{index}` | ✔ | 고정 게시판 공지 첨부 다운로드 |
| `GET` | `/notices/department` | ✔ | 학부공지 목록 (`dept` 필요) |
| `GET` | `/notices/department/{id}` | ✔ | 학부공지 상세 (`dept` 필요) |
| `GET` | `/notices/department/{id}/attachments/{index}` | ✔ | 학부공지 첨부 다운로드 (`dept` 필요) |

---

## 엔드포인트

### 인증

#### `POST /api/v1/auth/login`

세션 로그인. 실제 처리는 Security 필터가 하며 원본 로그인을 중계한다.

**Request** — `application/x-www-form-urlencoded`

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `username` | string | ✔ | HISNet 아이디(학번) |
| `password` | string | ✔ | HISNet 비밀번호 (저장·로깅 안 함) |

**Response**

| 상태 | 바디 | 설명 |
|---|---|---|
| `204 No Content` | — | 성공. `Set-Cookie: JSESSIONID=...` 발급 |
| `401 Unauthorized` | — | 아이디/비밀번호 불일치 또는 원본 로그인 실패 |
| `502 Bad Gateway` | 에러 객체 | 원본 서버 통신 실패 |

```bash
curl -i -c cookies.txt -X POST https://<host>/api/v1/auth/login \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'username=22000000' --data-urlencode 'password=***'
```

---

#### `POST /api/v1/auth/logout`

서버 세션(`JSESSIONID`)을 폐기한다. 원본 세션은 만료를 기다린다.

**Response**

| 상태 | 바디 |
|---|---|
| `204 No Content` | — |

---

#### `GET /api/v1/auth/me`

현재 로그인 사용자 확인.

**Response**

| 상태 | 바디 |
|---|---|
| `200 OK` | `AuthMe` |
| `401 Unauthorized` | 에러 객체 (`SESSION_EXPIRED`) |

```json
{ "username": "22000000" }
```

---

### 학적

#### `GET /api/v1/students/me`

로그인 사용자의 학적 기본정보. 원본 `HHAK110M.php` 파싱.

**Response** — `200 OK`, `StudentInfo`

```json
{
  "studentNo": "22000000",
  "name": "홍길동",
  "nameEnglish": "HONG,GILDONG",
  "academicStatus": "재학",
  "grade": "3",
  "nationality": "대한민국",
  "birthDate": "04-08-06",
  "curriculumType": "신 교육과정(130학점 졸업)",
  "admissionDate": "20230227",
  "highSchool": "OO고등학교",
  "graduationDate": null,
  "degreeNumber": null,
  "department": "AI컴퓨터전자공학부",
  "major": "AI·컴퓨터공학심화(60).",
  "doubleMajor": null,
  "minor": null,
  "engineeringCertification": "신청",
  "combinedDegree": null,
  "practicalComputing": "컴퓨터공학전공",
  "rcInfo": "손양원RC",
  "mobile": "010-0000-0000",
  "phone": "051-000-0000",
  "email": "user@example.com",
  "address": "12345 부산 ...",
  "raw": { "성명": "홍길동", "학번": "22000000", "학적상태": "재학", "…": "…" }
}
```

| 상태 | 설명 |
|---|---|
| `200 OK` | 정상 |
| `401 Unauthorized` | 미인증 / 원본 세션 만료 |
| `500 Internal Server Error` | 원본 페이지 구조 변경 등 파싱 실패 (`STUDENT_INFO_PARSING_FAILED`) |
| `502 Bad Gateway` | 원본 통신 실패 |

---

### 시간표

#### `GET /api/v1/timetable`

로그인 사용자의 **현재 학기** 시간표. 원본 `HLES110M.php`(내시간표조회) 파싱. 연도/학기 선택 파라미터 없음.

원본은 14교시 × 요일(월~토) 격자다. 한 과목이 연속 교시에 걸치면 원본 격자엔 교시마다 같은 칸이 반복되는데, 이 응답에서는 **과목 단위로 묶고 연속 교시를 하나의 슬롯**으로 합친다. 요일이 다르거나 교시가 끊기거나 강의실이 바뀌면 슬롯을 나눈다.

**Response** — `200 OK`, `Timetable`

```json
{
  "courses": [
    {
      "name": "오픈소스 스튜디오",
      "section": "01",
      "courseCode": "OSS0000000000001",
      "professor": "장소연",
      "slots": [
        { "day": "월", "startPeriod": 1, "endPeriod": 1, "room": "NTH 414" },
        { "day": "목", "startPeriod": 1, "endPeriod": 2, "room": "NTH 414" }
      ]
    },
    {
      "name": "채플(한국어) 4",
      "section": "01",
      "courseCode": "CHA0000000000001",
      "professor": "노규석외 3명",
      "slots": [
        { "day": "수", "startPeriod": 4, "endPeriod": 6, "room": "HCA 효암본관" }
      ]
    }
  ]
}
```

- 과목명 끝의 분반 표기 `(01)` 은 떼어 `section` 으로 분리한다. 과목명 안의 괄호(`채플(한국어)`)는 보존.
- 원본에 시각 표기가 없어 교시를 시:분으로 환산하지 않는다.
- 수강 과목이 없으면 `courses: []`.

| 상태 | 설명 |
|---|---|
| `200 OK` | 정상 |
| `401 Unauthorized` | 미인증 / 세션 만료 |
| `500` | 요일 헤더 표를 찾지 못함 등 파싱 실패 (`TIMETABLE_PARSING_FAILED`) |
| `502` | 원본 통신 실패 |

---

### 성적

#### `GET /api/v1/grades`

로그인 사용자의 전체 성적. 원본 `HREC110M.php`(전체성적조회) 한 페이지에서 **총 누적 성적 + 학기별 요약 + 학기별 상세 과목**을 한 번에 파싱한다. 현학기 성적은 원본 성적 입력·정정이 끝나야 반영된다.

**Response** — `200 OK`, `GradeResponse`

```json
{
  "summary": {
    "requestedCredits": 57.5,
    "earnedCredits": 57.5,
    "gpa": 4.04,
    "majorGpa": 4.33,
    "conversionScore": 95.4,
    "totalGradePoints": 182.0,
    "pfCredits": 12.5,
    "creditsByType": { "교양필수": 2.5, "교양선택": 5.0, "전공필수": 6.0, "전공선택": 12.0, "교양선택필수": 32.0 },
    "raw": { "신청학점": "57.5", "취득학점": "57.5", "평점평균": "4.04", "…": "…" }
  },
  "semesters": [
    {
      "year": 2026,
      "term": 1,
      "requestedCredits": 19.5,
      "earnedCredits": 19.5,
      "gpa": 4.21,
      "note": null,
      "courses": [
        {
          "code": "ITP20001",
          "name": "Data Structures",
          "type": "전공필수",
          "credits": 3.0,
          "grade": "A+",
          "gradePoints": 4.5,
          "retake": false,
          "note": null
        },
        {
          "code": "GEK20001",
          "name": "채플(한국어) 3",
          "type": "교양필수",
          "credits": 0.0,
          "grade": "P",
          "gradePoints": 0.0,
          "retake": false,
          "note": null
        }
      ]
    }
  ]
}
```

- `semesters` 는 원본 표기 순서(최근 학기부터).
- `summary.creditsByType` 는 이수구분별 취득학점(원문 라벨 그대로). `summary.raw` 는 누적 표의 라벨→값 전체(문자열).
- `grade` 는 원문 등급 문자열(`A+`, `A0`, `B+`, `P`, `PD` 등). P/PF 과목의 `gradePoints` 는 0.
- 누적/요약 표를 모두 찾지 못하면 파싱 실패. 표는 있으나 수강 과목이 없으면 `semesters: []`, `summary` 는 값이 대부분 `null`.

| 상태 | 설명 |
|---|---|
| `200 OK` | 정상 |
| `401 Unauthorized` | 미인증 / 세션 만료 |
| `500` | 파싱 실패 (`GRADE_PARSING_FAILED`) |
| `502` | 원본 통신 실패 |

---

### 졸업심사

#### `GET /api/v1/graduation`

로그인 사용자의 졸업심사 결과. 진입 페이지 `HGRA120M.php` 의 "결과보기" 버튼에서 학생 유형별 결과 페이지(`/prof/graduate/PGRA123S*.php`)를 알아내 **두 번** 조회·파싱한다. 졸업이 이미 확정돼 결과를 더 이상 조회할 수 없는 학생은 `available: false` 로 온다.

**Response** — `200 OK`, `GraduationResponse`

```json
{
  "available": true,
  "certificationType": "공학인증",
  "requiredCredits": 130,
  "notices": [
    "2026년도 8월 졸업심사대상자가 아닙니다.",
    "2026년 8월 졸업예정자를 위한 졸업심사 기준을 적용하여 심사한 결과 입니다."
  ],
  "student": {
    "department": "AI컴퓨터전자공학부",
    "name": "홍길동",
    "studentNo": "22300378",
    "academicStatus": "복학",
    "registeredTerms": 4,
    "major": "AI·컴퓨터공학심화(60)",
    "minor": null,
    "subMajor": "전산전공",
    "raw": { "학부": "AI컴퓨터전자공학부", "학번": "22300378", "…": "…" }
  },
  "criteria": [
    { "category": "신앙및세계관", "standard": "9", "earned": "6", "verdict": "불합격", "note": null },
    { "category": "전공주제(AI컴퓨터심화)", "standard": "60(12)", "earned": "18(3)", "verdict": "불합격", "note": null },
    { "category": "총 취득학점", "standard": "※ 비고 참고", "earned": "57.5", "verdict": "불합격", "note": "취득학점 >= 130 + ..." },
    { "category": "평점 평균", "standard": "2.0 이상", "earned": "4.04", "verdict": "합격", "note": null }
  ],
  "finalVerdict": "졸업불가능"
}
```

- `available: false` 면 `certificationType` / `requiredCredits` / `student` / `finalVerdict` 는 `null`, `criteria` 는 `[]`, `notices` 는 진입 페이지 안내문.
- `criteria[].standard` / `earned` 는 값이 제각각(단순 숫자, `60(12)` 설계학점 병기, `2.0 이상`, `※ 비고 참고`, 여러 줄)이라 **원문 문자열** 그대로 준다.
- 원본 표의 "최종 졸업판정" 행은 `criteria` 에 넣지 않고 `finalVerdict` 로 뺀다.

| 상태 | 설명 |
|---|---|
| `200 OK` | 정상 (`available` 로 조회 가능 여부 구분) |
| `401 Unauthorized` | 미인증 / 세션 만료 |
| `500` | 학생/판정 표를 모두 찾지 못함 등 파싱 실패 (`GRADUATION_PARSING_FAILED`) |
| `502` | 원본 통신 실패 |

---

### 식단표

#### `GET /api/v1/meals`

**당일** 식단표. **인증 불필요.** 원본은 식단 전용 페이지가 없고, 로그인 페이지(`/login/login.php`)에 당일 식단표가 서버사이드로 렌더링돼 미인증 상태에서도 내려온다. 그 위젯을 파싱해 식당 → 코너 → 끼니 트리로 재조립한다.

**Response** — `200 OK`, `MealResponse`

```json
{
  "date": "2026-09-07",
  "cafeterias": [
    {
      "id": "STUDENT",
      "name": "학생식당",
      "corners": [
        {
          "name": "든든한동",
          "meals": [
            { "slot": "아침", "items": ["-원산지:메뉴게시판 참조-", "소고기미역죽", "야채커틀렛"] },
            { "slot": "점심", "items": ["...", "청양마요미트조림", "쌀밥"] },
            { "slot": "저녁", "items": ["...", "오징어순대두루치기", "쌀밥"] }
          ]
        },
        { "name": "H:plate", "meals": [{ "slot": "점심", "items": ["등심돈까스", "..."] }] }
      ]
    },
    {
      "id": "HANDONG_LOUNGE",
      "name": "한동라운지",
      "corners": [
        {
          "name": "한동라운지",
          "meals": [
            { "slot": "교직원식당 점심", "items": ["...", "쌀밥"] },
            { "slot": "일반식당 점심", "items": ["...", "짜장면/짜장면곱배기"] },
            { "slot": "일반식당 저녁", "items": ["...", "짜장면/짜장면곱배기"] }
          ]
        }
      ]
    }
  ]
}
```

- `date` 는 원본 위젯 인쇄 아이콘 URL 에 박힌 당일 날짜(못 찾으면 서버 KST 기준 오늘).
- `id` 는 `STUDENT` / `MARS_KITCHEN` / `HANDONG_LOUNGE` / `GRACE_TABLE`. **메뉴가 하나도 없는 식당은 응답에서 제외**한다(예: 방학 중 말스키친).
- 학생식당만 코너가 여럿(든든한동 / H:plate / Asian Market / Han's Deli / 따스한동)이고, 나머지 식당은 식당명과 같은 코너 하나만 온다.
- `slot` 은 보통 `아침` / `점심` / `저녁`. 원본 헤더가 다르면 그 표기를 그대로 쓴다(한동라운지). 학생식당의 점심 전용 코너는 `점심`.
- `items` 는 원본 표기 순서. 맨 앞의 `-원산지:메뉴게시판 참조-` 같은 안내 문구도 원본에 있으면 포함.

| 상태 | 설명 |
|---|---|
| `200 OK` | 정상 |
| `500` | 식당 패널에서 메뉴를 하나도 찾지 못함 등 파싱 실패 (`MEAL_PARSING_FAILED`) |
| `502` | 원본 통신 실패 |

---

### 공지 — 고정 게시판

`board` 로 게시판을 지정한다(대소문자 무시).

| slug | 원본 Board | 설명 |
|---|---|---|
| `general` | `NB0001` | 일반공지 |
| `scholarship` | `JANG_NOTICE` | 장학공지 |
| `dormitory` | `RCNOTICE` | 생활관(RC)공지 |

목록·상세·첨부 구조는 학부공지와 동일하며 파싱 로직을 공유한다.

#### `GET /api/v1/notices/{board}`

공지 목록.

**Path / Query**

| 이름 | 위치 | 타입 | 필수 | 기본 | 설명 |
|---|---|---|---|---|---|
| `board` | path | string | ✔ | | `general` / `scholarship` / `dormitory` |
| `page` | query | int | | `1` | 페이지(1부터). 1 미만은 1로 보정, 마지막 페이지 초과는 원본이 마지막으로 클램프 |

**Response** — `200 OK`, `NoticeList`

```json
{
  "notices": [
    {
      "id": "175708",
      "subject": "[교무팀] 2026학년도 2학기 폐강과목 안내",
      "files": 0,
      "writer": "kylelee00",
      "time": "2026-09-03",
      "read": 1169,
      "pinned": true
    }
  ],
  "page": 1,
  "totalPages": 3532,
  "hasNext": true,
  "hasPrevious": false
}
```

- 한 페이지 일반 글 최대 15개.
- **고정공지는 원본이 모든 페이지에 반복 노출**하므로 2페이지부터는 제외한다. 1페이지에는 포함되며 `pinned: true` 로 구분.

| 상태 | 설명 |
|---|---|
| `200 OK` | 정상 |
| `400 Bad Request` | 알 수 없는 `board` slug (`INVALID_NOTICE_BOARD`) |
| `401 Unauthorized` | 미인증 / 세션 만료 |
| `500` / `502` | 파싱 실패 (`NOTICE_PARSING_FAILED`) / 원본 통신 실패 |

---

#### `GET /api/v1/notices/{board}/{id}`

공지 상세.

**Path**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `board` | string | ✔ | `general` / `scholarship` / `dormitory` |
| `id` | string(숫자) | ✔ | 목록 응답의 `id` (원본 `read.php?id=`). 화면의 게시판 순번이 아님 |

**Response** — `200 OK`, `Notice`

```json
{
  "id": "175753",
  "subject": "제목",
  "files": [
    { "index": 1, "name": "첨부_안내문.pdf" },
    { "index": 2, "name": "신청서.hwp" }
  ],
  "writer": "csy14(최순윤)",
  "time": "2026-09-06",
  "read": 59,
  "category": "General Info(전체 공지)",
  "body": "본문 텍스트",
  "images": ["https://hisnet.handong.edu/upload/report/03e39d42...image001.jpg"]
}
```

- `category` 는 학부/특수 게시판엔 없어 `null` 인 경우가 많다.
- `body` 는 **본문 텍스트만**. 첨부는 `files`, 본문에 삽입된 이미지는 `images`.
- **이미지로만 이뤄진 공지**(생활관 안내문 등)는 `body` 가 빈 문자열이고 `images` 에 이미지 URL 이 담긴다.
- `images` 는 본문 삽입 이미지의 **절대 URL**(에디터 업로드 이미지만; 레이아웃 아이콘 제외). 원본 이미지는 대부분 세션 없이 접근 가능한 정적 파일이라 URL 을 그대로 쓰면 된다. 없으면 `[]`.

| 상태 | 설명 |
|---|---|
| `200 OK` | 정상 |
| `400 Bad Request` | 알 수 없는 `board` (`INVALID_NOTICE_BOARD`) / `id` 형식 오류 (`INVALID_NOTICE_ID`) |
| `401 Unauthorized` | 미인증 / 세션 만료 |
| `500` / `502` | 파싱 실패 (`NOTICE_PARSING_FAILED`) / 원본 통신 실패 |

---

#### `GET /api/v1/notices/{board}/{id}/attachments/{index}`

공지 첨부파일 다운로드. 원본 `down.php` 를 **스트리밍**으로 릴레이한다(응답을 메모리에 담지 않음).

**Path / Query**

| 이름 | 위치 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| `board` | path | string | ✔ | `general` / `scholarship` / `dormitory` |
| `id` | path | string(숫자) | ✔ | 공지 `id` |
| `index` | path | int (≥1) | ✔ | 상세 응답 `files[].index` |
| `name` | query | string | | 파일명. 상세 응답의 `files[].name` 을 넘기면 그대로 `Content-Disposition` 에 사용. 생략 시 원본 헤더(EUC-KR)를 재디코딩해 복원 |

**Response**

| 상태 | 설명 |
|---|---|
| `200 OK` | 바이너리 스트림. `Content-Disposition: attachment; filename="<ascii>"; filename*=UTF-8''<pct>`, `Content-Type` 은 확장자 매핑(pdf/hwp/hwpx/doc(x)/xls(x)/ppt(x)/zip/png/jpg/gif/txt/csv), 그 외 `application/octet-stream` |
| `400 Bad Request` | `index < 1` (`INVALID_ATTACHMENT_INDEX`) / `id` 형식 오류 (`INVALID_NOTICE_ID`) / 알 수 없는 `board` (`INVALID_NOTICE_BOARD`) |
| `401 Unauthorized` | 미인증 / 세션 만료 |
| `404 Not Found` | 해당 `index` 첨부 없음 (`ATTACHMENT_NOT_FOUND`) |
| `502 Bad Gateway` | 원본 통신 실패 |

```bash
curl -OJ -b cookies.txt \
  "https://<host>/api/v1/notices/general/175753/attachments/1?name=%EC%B2%A8%EB%B6%80_%EC%95%88%EB%82%B4%EB%AC%B8.pdf"
```

---

### 공지 — 학부

학부공지는 게시판이 학과별 코드다. `dept` 쿼리로 코드를 넘긴다(형식 `^[A-Z]{1,3}\d{3,4}$`, 값은 [학부 게시판 코드](#학부-게시판-코드) 표). 목록·상세·첨부 응답 스키마는 고정 게시판과 같다.

#### `GET /api/v1/notices/department`
#### `GET /api/v1/notices/department/{id}`
#### `GET /api/v1/notices/department/{id}/attachments/{index}`

**Query 추가**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `dept` | string | ✔ | 학부 게시판 코드 |

| 상태 추가 | 설명 |
|---|---|
| `400 Bad Request` | `dept` 형식 오류 (`INVALID_DEPARTMENT`) / `dept` 누락(프레임워크 기본 응답) |

---

## 스키마

### AuthMe

| 필드 | 타입 | Null | 설명 |
|---|---|---|---|
| `username` | string | | 로그인 아이디(학번) |

### StudentInfo

읽기 전용 학적 기본정보. 미보유 항목은 `null`.

| 필드 | 타입 | 설명 |
|---|---|---|
| `studentNo` | string | 학번 |
| `name` | string | 성명(한글) |
| `nameEnglish` | string | 영문 성명 |
| `academicStatus` | string | 학적상태(재학/휴학/복학/졸업 등) |
| `grade` | string | 학년 |
| `nationality` | string | 국적 |
| `birthDate` | string | 생년월일 (원본 표기 그대로) |
| `curriculumType` | string | 교육과정 구분 |
| `admissionDate` | string | 입학일자 (원본 표기 그대로) |
| `highSchool` | string | 출신학교 |
| `graduationDate` | string | 졸업(예정)일자 |
| `degreeNumber` | string | 학위번호 |
| `department` | string | 학부(과) |
| `major` | string | 전공 |
| `doubleMajor` | string | 복수전공 |
| `minor` | string | 부전공 |
| `engineeringCertification` | string | 공학인증 |
| `combinedDegree` | string | 학석사연계 |
| `practicalComputing` | string | 실무전산여부 |
| `rcInfo` | string | RC 정보 |
| `mobile` | string | 휴대폰 |
| `phone` | string | 전화번호 |
| `email` | string | 이메일 |
| `address` | string | 주소(우편번호 + 도로명) |
| `raw` | object&lt;string,string&gt; | 파싱된 라벨→값 전체. 타입 필드에 없는 항목까지 포함 |

### Timetable

| 필드 | 타입 | 설명 |
|---|---|---|
| `courses` | `TimetableCourse[]` | 수강 과목 (원본 격자 등장 순서) |

### TimetableCourse

| 필드 | 타입 | Null | 설명 |
|---|---|---|---|
| `name` | string | | 과목명 (분반 표기 제거) |
| `section` | string | ✔ | 분반 (예: `01`) |
| `courseCode` | string | ✔ | 원본 과목 코드(`CIS_GWAMOK`) |
| `professor` | string | ✔ | 담당 교원 (예: `장소연`, `노규석외 3명`) |
| `slots` | `TimetableSlot[]` | | 요일·교시·강의실 |

### TimetableSlot

| 필드 | 타입 | Null | 설명 |
|---|---|---|---|
| `day` | string | | 요일 (`월`/`화`/`수`/`목`/`금`/`토`) |
| `startPeriod` | int | | 시작 교시 (1~14) |
| `endPeriod` | int | | 종료 교시 (포함). 단일 교시면 `startPeriod` 와 같음 |
| `room` | string | ✔ | 강의실 |

### GradeResponse

| 필드 | 타입 | Null | 설명 |
|---|---|---|---|
| `summary` | `GradeSummary` | ✔ | 총 누적 성적 |
| `semesters` | `SemesterGrade[]` | | 학기별 성적 (최근 학기부터) |

### GradeSummary

| 필드 | 타입 | Null | 설명 |
|---|---|---|---|
| `requestedCredits` | number | ✔ | 신청학점 |
| `earnedCredits` | number | ✔ | 취득학점 |
| `gpa` | number | ✔ | 평점평균 |
| `majorGpa` | number | ✔ | 전공평점평균 |
| `conversionScore` | number | ✔ | 환산점수(100점 환산) |
| `totalGradePoints` | number | ✔ | 평점계 |
| `pfCredits` | number | ✔ | PF이수학점 |
| `creditsByType` | object&lt;string,number&gt; | | 이수구분별 취득학점 (원문 라벨) |
| `raw` | object&lt;string,string&gt; | | 누적 표 라벨→값 전체(문자열) |

### SemesterGrade

| 필드 | 타입 | Null | 설명 |
|---|---|---|---|
| `year` | int | | 학년도 |
| `term` | int | | 학기 (1/2 등) |
| `requestedCredits` | number | ✔ | 신청학점 |
| `earnedCredits` | number | ✔ | 취득학점 |
| `gpa` | number | ✔ | 평점평균 |
| `note` | string | ✔ | 비고 |
| `courses` | `CourseGrade[]` | | 수강 과목 (상세 표 없으면 `[]`) |

### CourseGrade

| 필드 | 타입 | Null | 설명 |
|---|---|---|---|
| `code` | string | | 과목코드 |
| `name` | string | | 과목명 |
| `type` | string | ✔ | 이수구분 |
| `credits` | number | ✔ | 학점 (0.5 단위 존재) |
| `grade` | string | ✔ | 성적 등급 원문 (`A+`, `P`, `PD` 등) |
| `gradePoints` | number | ✔ | 평점 (P/PF 는 0) |
| `retake` | boolean | | 재이수 여부 |
| `note` | string | ✔ | 비고 |

### GraduationResponse

| 필드 | 타입 | Null | 설명 |
|---|---|---|---|
| `available` | boolean | | 원본에서 결과를 조회할 수 있는 상태인지 |
| `certificationType` | string | ✔ | `공학인증` 또는 `일반` |
| `requiredCredits` | int | ✔ | 졸업 기준 학점 (예: 130) |
| `notices` | string[] | | 원본 상단 ▣ 안내문 |
| `student` | `GraduationStudent` | ✔ | 심사 대상 학생 정보 |
| `criteria` | `GraduationCriterion[]` | | 항목별 판정 (최종 졸업판정 행 제외) |
| `finalVerdict` | string | ✔ | 최종 졸업판정 (`졸업가능` / `졸업불가능` 등) |

`available: false` 면 `certificationType`·`requiredCredits`·`student`·`finalVerdict` 는 `null`, `criteria` 는 `[]`.

### GraduationStudent

| 필드 | 타입 | Null | 설명 |
|---|---|---|---|
| `department` | string | ✔ | 학부 |
| `name` | string | ✔ | 이름 |
| `studentNo` | string | ✔ | 학번 |
| `academicStatus` | string | ✔ | 학적 |
| `registeredTerms` | int | ✔ | 등록학기 수 |
| `major` | string | ✔ | 전공 |
| `minor` | string | ✔ | 부전공 |
| `subMajor` | string | ✔ | 실무전산/컴퓨터공학 부전공 등 부가 전공 |
| `raw` | object&lt;string,string&gt; | | 학생 정보 표 라벨→값 전체 |

### GraduationCriterion

| 필드 | 타입 | Null | 설명 |
|---|---|---|---|
| `category` | string | | 구분 (예: `신앙및세계관`, `총 취득학점`) |
| `standard` | string | ✔ | 졸업기준(설계) 원문 |
| `earned` | string | ✔ | 취득학점(설계) 원문 |
| `verdict` | string | ✔ | 판정 (`합격` / `불합격` 등) |
| `note` | string | ✔ | 비고 |

### MealResponse

| 필드 | 타입 | Null | 설명 |
|---|---|---|---|
| `date` | string(date) | ✔ | 식단 기준 날짜 |
| `cafeterias` | `CafeteriaMeal[]` | | 식당별 식단 (메뉴 없는 식당 제외) |

### CafeteriaMeal

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | string | `STUDENT` / `MARS_KITCHEN` / `HANDONG_LOUNGE` / `GRACE_TABLE` |
| `name` | string | 식당 표시명 |
| `corners` | `MealCorner[]` | 코너별 메뉴 |

### MealCorner

| 필드 | 타입 | 설명 |
|---|---|---|
| `name` | string | 코너명 (코너가 하나뿐인 식당은 식당명과 동일) |
| `meals` | `MealSlot[]` | 끼니별 메뉴 (빈 끼니 제외) |

### MealSlot

| 필드 | 타입 | 설명 |
|---|---|---|
| `slot` | string | 끼니 구분 (`아침`/`점심`/`저녁` 또는 원본 헤더 표기) |
| `items` | string[] | 메뉴 항목 (원본 순서, 원산지 안내 문구 포함 가능) |

### NoticeList

| 필드 | 타입 | 설명 |
|---|---|---|
| `notices` | `SimpleNotice[]` | 현재 페이지 공지 요약 |
| `page` | int | 현재 페이지(1부터) |
| `totalPages` | int | 원본 페이저 기준 마지막 페이지 |
| `hasNext` | boolean | `page < totalPages` |
| `hasPrevious` | boolean | `page > 1` |

### SimpleNotice

| 필드 | 타입 | Null | 설명 |
|---|---|---|---|
| `id` | string | | 공지 ID (`read.php?id=`, 상세·첨부 조회에 사용) |
| `subject` | string | | 제목 |
| `files` | int | | 첨부 개수 |
| `writer` | string | ✔ | 작성자(아이디) |
| `time` | string(date) | ✔ | 등록일 `yyyy-MM-dd` |
| `read` | int | ✔ | 조회수 |
| `pinned` | boolean | | 고정공지 여부 |

### Notice

| 필드 | 타입 | Null | 설명 |
|---|---|---|---|
| `id` | string | | 공지 ID |
| `subject` | string | | 제목 |
| `files` | `Attachment[]` | | 첨부 목록 |
| `writer` | string | ✔ | 작성자 |
| `time` | string(date) | ✔ | 등록일 |
| `read` | int | ✔ | 조회수 |
| `category` | string | ✔ | 분류 |
| `body` | string | | 본문 텍스트 (이미지 전용 공지는 빈 문자열) |
| `images` | string[] | | 본문 삽입 이미지 절대 URL (없으면 `[]`) |

### Attachment

| 필드 | 타입 | 설명 |
|---|---|---|
| `index` | int | 다운로드 API 의 `{index}` (원본 `fidx`, 1부터) |
| `name` | string | 파일명 |

---

## 에러 코드

| code | HTTP | message |
|---|---|---|
| `INVALID_REQUEST` | 400 | 필요한 값이 비어있습니다. |
| `INVALID_NOTICE_ID` | 400 | 올바르지 않은 공지 ID입니다. |
| `INVALID_NOTICE_BOARD` | 400 | 올바르지 않은 공지 게시판입니다. |
| `INVALID_DEPARTMENT` | 400 | 올바르지 않은 학부 게시판 코드입니다. |
| `INVALID_ATTACHMENT_INDEX` | 400 | 올바르지 않은 첨부파일 번호입니다. |
| `LOGIN_FAILED` | 401 | 로그인에 실패했습니다. 아이디와 비밀번호를 확인해주세요. |
| `SESSION_EXPIRED` | 401 | 히즈넷 세션이 만료되었습니다. 다시 로그인해주세요. |
| `NOTICE_NOT_FOUND` | 404 | 공지를 찾을 수 없습니다. |
| `ATTACHMENT_NOT_FOUND` | 404 | 첨부파일을 찾을 수 없습니다. |
| `HISNET_REQUEST_FAILED` | 502 | 히즈넷 서버 요청에 실패했습니다. |
| `NOTICE_PARSING_FAILED` | 500 | 공지 페이지 파싱에 실패했습니다. |
| `STUDENT_INFO_PARSING_FAILED` | 500 | 학적 정보 페이지 파싱에 실패했습니다. |
| `MEAL_PARSING_FAILED` | 500 | 식단 페이지 파싱에 실패했습니다. |
| `TIMETABLE_PARSING_FAILED` | 500 | 시간표 페이지 파싱에 실패했습니다. |
| `GRADE_PARSING_FAILED` | 500 | 성적 페이지 파싱에 실패했습니다. |
| `GRADUATION_PARSING_FAILED` | 500 | 졸업심사 결과 페이지 파싱에 실패했습니다. |
| `INTERNAL_SERVER_ERROR` | 500 | 서버에서 에러가 발생했습니다. |

---

## 학부 게시판 코드

`department` 계열 엔드포인트의 `dept` 파라미터 값. (HISNet `list.php` 좌측 메뉴 기준)

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
