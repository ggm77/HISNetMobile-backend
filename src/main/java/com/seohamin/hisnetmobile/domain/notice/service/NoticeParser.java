package com.seohamin.hisnetmobile.domain.notice.service;

import com.seohamin.hisnetmobile.domain.notice.dto.NoticeResponseDto;
import com.seohamin.hisnetmobile.domain.notice.dto.SimpleNoticeResponseDto;
import com.seohamin.hisnetmobile.global.exception.CustomException;
import com.seohamin.hisnetmobile.global.exception.constants.ExceptionCode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 원본(HISNet) 공지 목록/본문 페이지(list.php / read.php)를 파싱하는 컴포넌트.
 * <p>
 * 두 게시판(일반공지·학부공지)이 같은 핸들러·같은 구조라 파서는 하나로 공유한다.
 * 원본이 테이블 레이아웃 + 클래스명이 거의 없는 레거시 HTML 이라, 클래스명 대신
 * "read.php 링크 / 라벨 셀 텍스트 / colspan" 같은 구조 앵커를 기준으로 값을 지목한다.
 */
@Component
@Slf4j
public class NoticeParser {

    // 목록 행 → 본문 링크. href 안의 id 파라미터가 글 ID
    private static final String READ_LINK_SELECTOR = "a[href*=read.php]";
    private static final Pattern READ_ID_PATTERN = Pattern.compile("[?&]id=([^&]+)");

    // 목록 하단 페이저 링크(list.php?...&Page=N) → 마지막 페이지 파악
    private static final String PAGER_LINK_SELECTOR = "a[href*=list.php]";
    private static final Pattern PAGE_PARAM_PATTERN = Pattern.compile("[?&]Page=(\\d{1,9})");

    // 날짜 셀 형식: 2026-09-01 / 2026.09.01 / 26.09.01 등
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{2,4})[.\\-/](\\d{1,2})[.\\-/](\\d{1,2})");
    private static final Pattern DIGITS_ONLY = Pattern.compile("^\\d+$");

    // read.php 상세: 헤더 메타는 div.readText 안의 라벨/값 span 묶음, 본문은 td.readText.BoardContent
    // (헤더 블록은 "readText cls_Padding10", 본문 셀은 td 라서 div.readText 로만 잡아도 헤더만 걸린다)
    private static final String DETAIL_HEADER_SELECTOR = "div.readText";
    private static final String DETAIL_BODY_SELECTOR = "td.readText.BoardContent, .BoardContent, td.BoardContent";
    // 제목 블록 맨 앞에 붙는 "175753." 같은 글 ID 토큰
    private static final Pattern LEADING_ID_TOKEN = Pattern.compile("^\\d+\\.?$");
    // 첨부 링크 텍스트 끝의 " (1,321,448 bytes)" 꼬리
    private static final Pattern ATTACHMENT_SIZE_SUFFIX =
            Pattern.compile("\\s*\\([\\d,]+\\s*bytes\\)\\s*$", Pattern.CASE_INSENSITIVE);

    // 본문 상세 페이지의 라벨 텍스트 (원본이 영문 라벨 Date/Writer/Read/Category 를 쓴다. 구 레이아웃용 국문도 함께)
    private static final List<String> SUBJECT_LABELS = List.of("제목", "subject");
    private static final List<String> WRITER_LABELS = List.of("작성자", "이름", "글쓴이", "writer");
    private static final List<String> READ_COUNT_LABELS = List.of("조회", "조회수", "read");
    private static final List<String> DATE_LABELS = List.of("date", "작성일", "등록일", "날짜");
    private static final List<String> CATEGORY_LABELS = List.of("분류", "구분", "category");

    // 첨부파일 다운로드로 볼 수 있는 링크 패턴
    private static final Pattern ATTACHMENT_HREF_PATTERN =
            Pattern.compile("(download|filedown|file_down|down\\.php|getfile|attach)", Pattern.CASE_INSENSITIVE);

    // read.php 하단에는 게시판 목록(tr.tr_basic)이 통째로 붙는다. 그 목록 행의 첨부 링크는 제외한다.
    // (기사 본문의 첨부 셀도 td.listBody 를 쓰므로 tr.tr_basic 으로만 걸러야 한다)
    private static final String DETAIL_LIST_ROW_SELECTOR = "tr.tr_basic";

    /**
     * 목록 페이지(list.php)를 파싱해 공지 요약 리스트로 변환하는 메서드.
     * 고정공지 행도 그대로 포함하며 DOM 순서(고정공지 → 일반)를 유지한다.
     * @param document EUC-KR 로 디코딩된 목록 페이지
     * @return 공지 요약 리스트
     */
    public List<SimpleNoticeResponseDto> parseList(final Document document) {

        // 1) 같은 행에 링크가 여러 개(제목/댓글 등)일 수 있어 글 ID 기준으로 행을 1개로 모은다
        final Map<String, Element> rowById = new LinkedHashMap<>();
        for (final Element link : document.select(READ_LINK_SELECTOR)) {
            final String noticeId = extractNoticeId(link.attr("href"));
            if (noticeId == null) {
                continue;
            }
            final Element row = link.closest("tr");
            if (row == null) {
                continue;
            }
            rowById.putIfAbsent(noticeId, row);
        }

        // 2) 목록 링크가 하나도 없으면 구조가 바뀌었거나 잘못된 페이지 → 파싱 실패로 처리
        if (rowById.isEmpty()) {
            log.warn("[공지 목록 파싱 실패] read.php 링크를 찾지 못함");
            throw new CustomException(ExceptionCode.NOTICE_PARSING_FAILED);
        }

        // 3) 행마다 셀을 훑어 요약 DTO 로 변환
        final List<SimpleNoticeResponseDto> notices = new ArrayList<>();
        for (final Map.Entry<String, Element> entry : rowById.entrySet()) {
            notices.add(toSimpleNotice(entry.getKey(), entry.getValue()));
        }

        return notices;
    }

    /**
     * 목록 페이지 하단 페이저에서 마지막(최대) 페이지 번호를 뽑는 메서드.
     * 페이저는 list.php 링크들의 {@code Page} 파라미터로 구성되고, {@code >>} 링크가 마지막 페이지를 가리킨다.
     * @param document EUC-KR 로 디코딩된 목록 페이지
     * @return 파악된 마지막 페이지. 페이저가 없으면 1.
     */
    public int parseTotalPages(final Document document) {

        int lastPage = 1;
        for (final Element link : document.select(PAGER_LINK_SELECTOR)) {
            final Matcher matcher = PAGE_PARAM_PATTERN.matcher(link.attr("href"));
            while (matcher.find()) {
                lastPage = Math.max(lastPage, Integer.parseInt(matcher.group(1)));
            }
        }

        return lastPage;
    }

    /**
     * 본문 페이지(read.php)를 파싱해 공지 상세로 변환하는 메서드.
     * <p>
     * 현재 read.php 구조: 헤더 메타는 {@code div.readText} 안에 "라벨 span → 값 span" 이 이어지고
     * (라벨은 영문 Date/Writer/Read/Category, 카테고리는 라벨·값이 중첩 span 으로 붙기도 함),
     * 제목은 첫 헤더 블록에서 "글 ID." 토큰을 뺀 나머지, 본문은 {@code td.readText.BoardContent} 다.
     * 구조가 다른 게시판/구버전을 대비해 실패 시 라벨 셀(th/td) 방식으로 폴백한다.
     * @param document EUC-KR 로 디코딩된 본문 페이지
     * @param noticeId 요청한 글 ID (응답에 그대로 실어준다)
     * @return 공지 상세
     */
    public NoticeResponseDto parseDetail(
            final Document document,
            final String noticeId
    ) {

        // 1) 헤더 span 묶음에서 메타 추출
        final Map<String, String> header = parseDetailHeader(document);

        // 2) 제목: 헤더 첫 블록 → 폴백으로 라벨 셀
        String subject = extractDetailSubject(document);
        if (isBlank(subject)) {
            subject = findValueByLabel(document, SUBJECT_LABELS);
        }
        if (isBlank(subject)) {
            log.warn("[공지 본문 파싱 실패] 제목을 찾지 못함 noticeId={} / {}", noticeId, describeDocument(document));
            throw new CustomException(ExceptionCode.NOTICE_PARSING_FAILED);
        }

        final String writer = firstNonBlank(header.get("writer"), findValueByLabel(document, WRITER_LABELS));
        final Integer read = parseIntOrNull(firstNonBlank(header.get("read"), findValueByLabel(document, READ_COUNT_LABELS)));
        final LocalDate time = parseDate(firstNonBlank(header.get("date"), findValueByLabel(document, DATE_LABELS)));
        final String category = firstNonBlank(header.get("category"), findValueByLabel(document, CATEGORY_LABELS));

        // 3) 본문
        final String body = extractBody(document);

        // 4) 첨부파일 이름 (하단 게시판 목록의 첨부 링크는 제외)
        final List<String> files = extractAttachmentNames(document);

        return new NoticeResponseDto(
                noticeId,
                subject,
                files,
                writer,
                time,
                read,
                category,
                body
        );
    }

    /**
     * 파싱 실패 시 원본 응답의 정체를 로그로 남기기 위한 요약 메서드.
     * (세션 만료 페이지인지, 구조가 바뀐 건지, 인코딩이 깨진 건지 구분하기 위함)
     */
    private String describeDocument(final Document document) {
        final String text = document.body() != null ? document.body().text() : document.text();
        final String head = text.length() > 300 ? text.substring(0, 300) : text;

        return String.format(
                "htmlLen=%d, title='%s', div.readText=%d, readText.cls_Padding10=%d, span=%d, BoardContent=%d, td=%d, bodyTextHead='%s'",
                document.outerHtml().length(),
                document.title(),
                document.select("div.readText").size(),
                document.select("div.readText.cls_Padding10").size(),
                document.select("span").size(),
                document.select(DETAIL_BODY_SELECTOR).size(),
                document.select("td").size(),
                head.replaceAll("\\s+", " ")
        );
    }

    /**
     * read.php 헤더의 {@code div.readText} 블록들을 훑어 "라벨 → 값" 맵을 만드는 메서드.
     * 라벨과 값이 이웃한 leaf span 으로 나온다. 중첩 span 이 있으면 부모는 건너뛰고 자식만 본다.
     * readText 블록을 못 찾으면(파서가 구조를 뭉갠 경우) 문서 전체의 span 을 훑는다.
     */
    private Map<String, String> parseDetailHeader(final Document document) {

        List<String> tokens = leafSpanTokens(document.select(DETAIL_HEADER_SELECTOR));
        if (tokens.isEmpty()) {
            tokens = leafSpanTokens(document.select("span"));
        }

        final Map<String, String> header = new LinkedHashMap<>();
        for (int i = 0; i + 1 < tokens.size(); i++) {
            final String key = canonicalDetailLabel(tokens.get(i));
            if (key != null) {
                header.putIfAbsent(key, tokens.get(i + 1).trim());
            }
        }

        return header;
    }

    /**
     * span 목록에서 leaf(자식 span 없는) span 의 텍스트만 순서대로 뽑는 메서드.
     */
    private List<String> leafSpanTokens(final Elements spans) {
        final List<String> tokens = new ArrayList<>();
        for (final Element span : spans) {
            if (!span.select("span").isEmpty()) {
                continue;
            }
            final String text = span.text().trim();
            if (!text.isEmpty()) {
                tokens.add(text);
            }
        }

        return tokens;
    }

    /**
     * 헤더 라벨 텍스트를 표준 키(writer/read/date/category)로 바꾸는 메서드.
     * @return 알려진 라벨이 아니면 null
     */
    private String canonicalDetailLabel(final String raw) {
        final String normalized = raw.replace(":", "").replace(" ", "").trim().toLowerCase(Locale.ROOT);

        if (WRITER_LABELS.contains(normalized)) {
            return "writer";
        }
        if (READ_COUNT_LABELS.contains(normalized)) {
            return "read";
        }
        if (DATE_LABELS.contains(normalized)) {
            return "date";
        }
        if (CATEGORY_LABELS.contains(normalized)) {
            return "category";
        }

        return null;
    }

    /**
     * 제목을 뽑는 메서드. 첫 헤더 블록의 leaf span 중 "글 ID." 토큰을 뺀 나머지를 이어붙인다.
     * 헤더 블록을 못 찾으면, 문서 전체에서 "숫자." 토큰 바로 뒤의 span 을 제목으로 본다.
     */
    private String extractDetailSubject(final Document document) {
        final Element firstBlock = document.selectFirst(DETAIL_HEADER_SELECTOR);
        if (firstBlock != null) {
            final List<String> parts = new ArrayList<>();
            for (final String token : leafSpanTokens(firstBlock.select("span"))) {
                if (!LEADING_ID_TOKEN.matcher(token).matches()) {
                    parts.add(token);
                }
            }
            if (!parts.isEmpty()) {
                return String.join(" ", parts);
            }
        }

        // 폴백: 전체 span 토큰에서 "175753." 다음 토큰
        final List<String> allTokens = leafSpanTokens(document.select("span"));
        for (int i = 0; i + 1 < allTokens.size(); i++) {
            if (LEADING_ID_TOKEN.matcher(allTokens.get(i)).matches()
                    && !LEADING_ID_TOKEN.matcher(allTokens.get(i + 1)).matches()) {
                return allTokens.get(i + 1);
            }
        }

        return null;
    }

    /**
     * 목록 행 하나를 요약 DTO 로 변환하는 메서드.
     * 셀 순서(번호/제목/첨부/작성자/날짜/조회)가 게시판마다 미묘하게 다를 수 있어 값의 형태로 역추론한다.
     */
    private SimpleNoticeResponseDto toSimpleNotice(
            final String noticeId,
            final Element row
    ) {
        final Elements cells = row.select("td");
        final boolean pinned = isPinnedRow(cells);

        final String subject = row.select(READ_LINK_SELECTOR).stream()
                .map(Element::text)
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .findFirst()
                .orElse("");

        LocalDate time = null;
        Integer read = null;
        String writer = null;

        for (final Element cell : cells) {
            final String text = cell.text().trim();
            if (text.isEmpty() || text.equals(subject)) {
                continue;
            }

            final LocalDate parsedDate = parseDate(text);
            if (parsedDate != null) {
                time = parsedDate;
                continue;
            }

            if (DIGITS_ONLY.matcher(text).matches()) {
                // 숫자 셀은 번호/조회수 후보 → 마지막 숫자 셀을 조회수로 본다
                read = Integer.valueOf(text);
                continue;
            }

            if (writer == null) {
                writer = text;
            }
        }

        return new SimpleNoticeResponseDto(
                noticeId,
                subject,
                countAttachments(row),
                writer,
                time,
                read,
                pinned
        );
    }

    /**
     * 고정공지 행 여부를 판별하는 메서드.
     * 일반 행은 첫 셀(No 칸)이 게시글 번호(숫자)지만, 고정공지 행은 "고정공지" 같은 라벨이 들어간다.
     */
    private boolean isPinnedRow(final Elements cells) {
        if (cells.isEmpty()) {
            return false;
        }
        final String noText = cells.first().text().trim();

        return !noText.isEmpty() && !DIGITS_ONLY.matcher(noText).matches();
    }

    /**
     * href 쿼리스트링에서 글 ID(id 파라미터)를 뽑아내는 메서드.
     */
    private String extractNoticeId(final String href) {
        if (href == null) {
            return null;
        }
        final Matcher matcher = READ_ID_PATTERN.matcher(href);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * 행/문서 안의 첨부파일 개수를 세는 메서드.
     * 첨부 아이콘(img) 또는 다운로드성 링크 개수를 합산한다.
     */
    private Integer countAttachments(final Element scope) {
        final long iconCount = scope.select("img[src*=file], img[src*=clip], img[src*=attach], img[alt*=첨부]").size();
        final long linkCount = scope.select("a[href]").stream()
                .filter(link -> ATTACHMENT_HREF_PATTERN.matcher(link.attr("href")).find())
                .count();

        return (int) (iconCount + linkCount);
    }

    /**
     * 상세 페이지에서 라벨 텍스트와 일치하는 셀을 찾아 바로 다음(또는 형제) 셀 값을 반환하는 메서드.
     */
    private String findValueByLabel(
            final Document document,
            final List<String> labels
    ) {
        for (final Element cell : document.select("th, td")) {
            final String ownText = cell.ownText().trim();
            final boolean matched = labels.stream()
                    .anyMatch(label -> ownText.equalsIgnoreCase(label)
                            || ownText.replace(" ", "").equalsIgnoreCase(label));
            if (!matched) {
                continue;
            }

            final Element valueCell = cell.nextElementSibling();
            if (valueCell != null && !valueCell.text().trim().isEmpty()) {
                return valueCell.text().trim();
            }
        }

        return null;
    }

    /**
     * 본문을 뽑는 메서드. {@code td.readText.BoardContent} 를 우선 보고,
     * 없으면 표 레이아웃에서 colspan 이 걸린 가장 긴 셀로 폴백한다.
     */
    private String extractBody(final Document document) {
        final Element boardContent = document.selectFirst(DETAIL_BODY_SELECTOR);
        if (boardContent != null) {
            final String text = boardContent.wholeText().trim();
            if (!text.isEmpty()) {
                return text;
            }
        }

        return document.select("td[colspan]").stream()
                .max((a, b) -> Integer.compare(a.text().length(), b.text().length()))
                .map(Element::wholeText)
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .orElse("");
    }

    /**
     * 첨부파일 링크의 표시 이름을 수집하는 메서드.
     * read.php 하단에 붙는 게시판 목록의 첨부 링크는 제외하기 위해 "기사 영역"으로 범위를 좁힌다.
     * 링크 텍스트가 파일명이며, 끝의 "(12,345 bytes)" 꼬리는 떼고 숫자만인 텍스트는 버린다.
     */
    private List<String> extractAttachmentNames(final Document document) {
        final Element articleScope = detailArticleScope(document);
        final Element searchRoot = articleScope != null ? articleScope : document;

        final List<String> names = new ArrayList<>();
        for (final Element link : searchRoot.select("a[href]")) {
            if (!ATTACHMENT_HREF_PATTERN.matcher(link.attr("href")).find()) {
                continue;
            }
            if (!link.parents().select(DETAIL_LIST_ROW_SELECTOR).isEmpty()) {
                continue;
            }

            final String name = ATTACHMENT_SIZE_SUFFIX.matcher(link.text().trim()).replaceAll("").trim();
            if (name.isEmpty() || DIGITS_ONLY.matcher(name).matches()) {
                continue;
            }
            if (!names.contains(name)) {
                names.add(name);
            }
        }

        return names;
    }

    /**
     * 상세 페이지에서 "기사 영역"(하단 게시판 목록 제외)을 좁히는 메서드.
     * 헤더 span 묶음과 본문 셀의 최소 공통 조상을 쓴다. 구조가 다르면 null.
     */
    private Element detailArticleScope(final Document document) {
        final Element header = document.selectFirst(DETAIL_HEADER_SELECTOR);
        final Element body = document.selectFirst(DETAIL_BODY_SELECTOR);
        if (header == null || body == null) {
            return null;
        }

        final Set<Element> headerAncestors = new HashSet<>(header.parents());
        for (final Element ancestor : body.parents()) {
            if (headerAncestors.contains(ancestor)) {
                return ancestor;
            }
        }

        return null;
    }

    private boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }

    private String firstNonBlank(final String primary, final String fallback) {
        if (!isBlank(primary)) {
            return primary;
        }
        return isBlank(fallback) ? null : fallback;
    }

    /**
     * 다양한 구분자(- . /)와 2자리 연도를 허용해 날짜 문자열을 LocalDate 로 변환하는 메서드.
     * @return 파싱 실패 시 null
     */
    private LocalDate parseDate(final String text) {
        if (text == null) {
            return null;
        }
        final Matcher matcher = DATE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }

        try {
            int year = Integer.parseInt(matcher.group(1));
            if (year < 100) {
                year += 2000;
            }
            final int month = Integer.parseInt(matcher.group(2));
            final int day = Integer.parseInt(matcher.group(3));

            return LocalDate.of(year, month, day);
        } catch (final RuntimeException ex) {
            return null;
        }
    }

    /**
     * 숫자만 남겨 Integer 로 변환하는 메서드.
     * @return 숫자가 없으면 null
     */
    private Integer parseIntOrNull(final String text) {
        if (text == null) {
            return null;
        }
        final String digits = text.replaceAll("[^0-9]", "");

        return digits.isEmpty() ? null : Integer.valueOf(digits);
    }
}