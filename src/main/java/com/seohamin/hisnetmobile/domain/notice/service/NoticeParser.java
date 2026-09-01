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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    // 날짜 셀 형식: 2026-09-01 / 2026.09.01 / 26.09.01 등
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{2,4})[.\\-/](\\d{1,2})[.\\-/](\\d{1,2})");
    private static final Pattern DIGITS_ONLY = Pattern.compile("^\\d+$");

    // 본문 상세 페이지의 라벨 셀 텍스트
    private static final List<String> SUBJECT_LABELS = List.of("제목");
    private static final List<String> WRITER_LABELS = List.of("작성자", "이름", "글쓴이");
    private static final List<String> READ_COUNT_LABELS = List.of("조회", "조회수");
    private static final List<String> DATE_LABELS = List.of("date", "작성일", "등록일", "날짜");
    private static final List<String> CATEGORY_LABELS = List.of("분류", "구분");

    // 첨부파일 다운로드로 볼 수 있는 링크 패턴
    private static final Pattern ATTACHMENT_HREF_PATTERN =
            Pattern.compile("(download|filedown|file_down|down\\.php|getfile|attach)", Pattern.CASE_INSENSITIVE);

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
            final String noticeNo = extractNoticeNo(link.attr("href"));
            if (noticeNo == null) {
                continue;
            }
            final Element row = link.closest("tr");
            if (row == null) {
                continue;
            }
            rowById.putIfAbsent(noticeNo, row);
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
     * 본문 페이지(read.php)를 파싱해 공지 상세로 변환하는 메서드.
     * @param document EUC-KR 로 디코딩된 본문 페이지
     * @param noticeNo 요청한 글 ID (응답에 그대로 실어준다)
     * @return 공지 상세
     */
    public NoticeResponseDto parseDetail(
            final Document document,
            final String noticeNo
    ) {

        // 1) 라벨 셀("제목/작성자/조회/Date")을 앵커로 값 셀을 지목
        final String subject = findValueByLabel(document, SUBJECT_LABELS);
        if (subject == null || subject.isBlank()) {
            log.warn("[공지 본문 파싱 실패] 제목 셀을 찾지 못함 noticeNo={}", noticeNo);
            throw new CustomException(ExceptionCode.NOTICE_PARSING_FAILED);
        }

        final String writer = findValueByLabel(document, WRITER_LABELS);
        final Integer read = parseIntOrNull(findValueByLabel(document, READ_COUNT_LABELS));
        final LocalDate time = parseDate(findValueByLabel(document, DATE_LABELS));
        final String category = findValueByLabel(document, CATEGORY_LABELS);

        // 2) 본문 컨테이너는 표 레이아웃에서 colspan 이 걸린 가장 텍스트가 긴 셀
        final String body = extractBody(document);

        // 3) 첨부파일 링크 이름 수집 (바이너리 릴레이는 2차 범위, 지금은 이름만)
        final List<String> files = extractAttachmentNames(document);

        return new NoticeResponseDto(
                noticeNo,
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
     * 목록 행 하나를 요약 DTO 로 변환하는 메서드.
     * 셀 순서(번호/제목/첨부/작성자/날짜/조회)가 게시판마다 미묘하게 다를 수 있어 값의 형태로 역추론한다.
     */
    private SimpleNoticeResponseDto toSimpleNotice(
            final String noticeNo,
            final Element row
    ) {
        final Elements cells = row.select("td");

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
                noticeNo,
                subject,
                countAttachments(row),
                writer,
                time,
                read
        );
    }

    /**
     * href 쿼리스트링에서 글 ID(id 파라미터)를 뽑아내는 메서드.
     */
    private String extractNoticeNo(final String href) {
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
     * 본문 컨테이너 셀을 지목하는 메서드.
     * 표 레이아웃(테이블 20여 개)에서 본문은 colspan 이 걸린 셀 중 텍스트가 가장 긴 셀이다.
     */
    private String extractBody(final Document document) {
        return document.select("td[colspan]").stream()
                .max((a, b) -> Integer.compare(a.text().length(), b.text().length()))
                .map(Element::wholeText)
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .orElse("");
    }

    /**
     * 첨부파일 링크의 표시 이름을 수집하는 메서드.
     */
    private List<String> extractAttachmentNames(final Document document) {
        final List<String> names = new ArrayList<>();
        for (final Element link : document.select("a[href]")) {
            if (!ATTACHMENT_HREF_PATTERN.matcher(link.attr("href")).find()) {
                continue;
            }
            final String name = link.text().trim();
            if (!name.isEmpty() && !names.contains(name)) {
                names.add(name);
            }
        }

        return names;
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