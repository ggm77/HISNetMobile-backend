package com.seohamin.hisnetmobile.domain.grade.service;

import com.seohamin.hisnetmobile.domain.grade.dto.CourseGradeDto;
import com.seohamin.hisnetmobile.domain.grade.dto.GradeResponseDto;
import com.seohamin.hisnetmobile.domain.grade.dto.GradeSummaryDto;
import com.seohamin.hisnetmobile.domain.grade.dto.SemesterGradeDto;
import com.seohamin.hisnetmobile.global.exception.CustomException;
import com.seohamin.hisnetmobile.global.exception.constants.ExceptionCode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 전체성적조회 페이지(HREC110M.php)를 파싱하는 컴포넌트.
 * <p>
 * 원본 한 페이지에 세 덩어리가 있고 셋 다 초기 HTML 에 들어있다(과거 학기 상세는 {@code display:none} 일 뿐).
 * <ul>
 *   <li><b>총 누적 성적</b> 표 — "누적 성적" 캡션 행 뒤로 라벨/값 셀이 짝지어 이어진다.</li>
 *   <li><b>학기별 요약</b> 표 — 헤더 {@code 학사년도 | 신청학점 | 취득학점 | 평점평균 | 비고 | 학기별성적}.</li>
 *   <li><b>학기별 상세</b> — {@code div#div_YYYYT} 안의 표. 캡션 "YYYY-T 학기 성적" + 헤더
 *       {@code 과목코드 | 과목명 | 이수구분 | 학점 | 성적 | 평점 | 재이수 | 비고} + 과목 행들.</li>
 * </ul>
 * id/class 앵커가 거의 없어, 표는 "특정 텍스트를 가진 가장 안쪽 표"로, 상세는 div id 로 지목한다.
 */
@Component
@Slf4j
public class GradeParser {

    // "2026-1" → 년도 2026, 학기 1
    private static final Pattern YEAR_TERM = Pattern.compile("(\\d{4})\\s*-\\s*(\\d+)");
    // 상세 div id: div_20261
    private static final Pattern DETAIL_DIV_ID = Pattern.compile("^div_(\\d{4})(\\d+)$");

    // 총 누적 성적에서 타입 필드로 끌어올릴 라벨(공백 제거 비교)
    private static final String K_REQUESTED = "신청학점";
    private static final String K_EARNED = "취득학점";
    private static final String K_GPA = "평점평균";
    private static final String K_MAJOR_GPA = "전공평점평균";
    private static final String K_CONVERSION = "환산점수";
    private static final String K_TOTAL_POINTS = "평점계";
    private static final String K_PF = "PF이수학점";
    private static final Set<String> SUMMARY_LABELS = Set.of(
            K_REQUESTED, K_EARNED, K_GPA, K_MAJOR_GPA, K_CONVERSION, K_TOTAL_POINTS, K_PF);

    /**
     * 성적 페이지를 파싱해 응답 DTO 로 변환하는 메서드.
     * @param document EUC-KR 로 디코딩된 HREC110M.php 페이지
     * @return 성적 (누적 요약 + 학기별)
     */
    public GradeResponseDto parse(final Document document) {

        final Element cumulativeTable = innermostTableContaining(document, "누적 성적", "평점평균");
        final Element summaryTable = innermostTableContaining(document, "학사년도", "평점평균");

        if (cumulativeTable == null && summaryTable == null) {
            log.warn("[성적 파싱 실패] 누적/학기별 요약 표를 모두 찾지 못함 (구조 변경 의심)");
            throw new CustomException(ExceptionCode.GRADE_PARSING_FAILED);
        }

        final Map<String, List<CourseGradeDto>> coursesBySemester = parseDetailDivs(document);

        final GradeSummaryDto summary = cumulativeTable != null ? parseCumulative(cumulativeTable) : null;
        final List<SemesterGradeDto> semesters = parseSemesters(summaryTable, coursesBySemester);

        return new GradeResponseDto(summary, semesters);
    }

    // ------------------------------------------------------------------
    // 총 누적 성적
    // ------------------------------------------------------------------

    private GradeSummaryDto parseCumulative(final Element table) {

        final Map<String, String> raw = new LinkedHashMap<>();
        final Map<String, Double> values = new LinkedHashMap<>();

        for (final Element row : rowsAfter(table, "누적")) {
            final Elements cells = row.select("td");
            for (int i = 0; i + 1 < cells.size(); i += 2) {
                final String label = cells.get(i).text().trim();
                final String value = cells.get(i + 1).text().trim();
                if (label.isEmpty()) {
                    continue;
                }
                raw.putIfAbsent(label, value);
                final Double number = parseNumber(value);
                if (number != null) {
                    values.putIfAbsent(label, number);
                }
            }
        }

        final Map<String, Double> creditsByType = new LinkedHashMap<>();
        for (final Map.Entry<String, Double> entry : values.entrySet()) {
            if (!SUMMARY_LABELS.contains(normalize(entry.getKey()))) {
                creditsByType.put(entry.getKey(), entry.getValue());
            }
        }

        return new GradeSummaryDto(
                lookup(values, K_REQUESTED),
                lookup(values, K_EARNED),
                lookup(values, K_GPA),
                lookup(values, K_MAJOR_GPA),
                lookup(values, K_CONVERSION),
                lookup(values, K_TOTAL_POINTS),
                lookup(values, K_PF),
                creditsByType,
                raw
        );
    }

    // ------------------------------------------------------------------
    // 학기별 요약 + 상세 결합
    // ------------------------------------------------------------------

    private List<SemesterGradeDto> parseSemesters(
            final Element summaryTable,
            final Map<String, List<CourseGradeDto>> coursesBySemester
    ) {
        final List<SemesterGradeDto> semesters = new ArrayList<>();
        final Set<String> seen = new LinkedHashSet<>();

        if (summaryTable != null) {
            for (final Element row : rowsAfter(summaryTable, "학사년도")) {
                final Elements cells = row.select("td");
                if (cells.size() < 4) {
                    continue;
                }
                final Matcher yt = YEAR_TERM.matcher(cells.get(0).text().trim());
                if (!yt.find()) {
                    continue;
                }
                final int year = Integer.parseInt(yt.group(1));
                final int term = Integer.parseInt(yt.group(2));
                final String key = year + "-" + term;

                semesters.add(new SemesterGradeDto(
                        year,
                        term,
                        parseNumber(cells.get(1).text()),
                        parseNumber(cells.get(2).text()),
                        parseNumber(cells.get(3).text()),
                        cells.size() > 4 ? blankToNull(cells.get(4).text()) : null,
                        coursesBySemester.getOrDefault(key, List.of())
                ));
                seen.add(key);
            }
        }

        // 요약 행이 없는 상세(희귀)는 뒤에 붙인다
        for (final Map.Entry<String, List<CourseGradeDto>> entry : coursesBySemester.entrySet()) {
            if (seen.contains(entry.getKey())) {
                continue;
            }
            final Matcher yt = YEAR_TERM.matcher(entry.getKey());
            if (!yt.find()) {
                continue;
            }
            semesters.add(new SemesterGradeDto(
                    Integer.parseInt(yt.group(1)),
                    Integer.parseInt(yt.group(2)),
                    null, null, null, null,
                    entry.getValue()
            ));
        }

        return semesters;
    }

    // ------------------------------------------------------------------
    // 학기별 상세 (div#div_YYYYT)
    // ------------------------------------------------------------------

    private Map<String, List<CourseGradeDto>> parseDetailDivs(final Document document) {

        final Map<String, List<CourseGradeDto>> bySemester = new LinkedHashMap<>();

        for (final Element div : document.select("div[id]")) {
            final Matcher idMatcher = DETAIL_DIV_ID.matcher(div.id());
            if (!idMatcher.matches()) {
                continue;
            }
            final Element table = div.selectFirst("table");
            if (table == null) {
                continue;
            }

            final List<CourseGradeDto> courses = new ArrayList<>();
            for (final Element row : table.select("tr")) {
                final String rowText = row.text();
                if (rowText.contains("학기 성적") || rowText.contains("과목코드")) {
                    continue;
                }
                final Elements cells = row.select("td");
                if (cells.size() < 6) {
                    continue;
                }
                final String code = cells.get(0).text().trim();
                final String name = cells.get(1).text().trim();
                if (code.isEmpty() || name.isEmpty()) {
                    continue;
                }
                courses.add(new CourseGradeDto(
                        code,
                        name,
                        blankToNull(cells.get(2).text()),
                        parseNumber(cells.get(3).text()),
                        blankToNull(cells.get(4).text()),
                        parseNumber(cells.get(5).text()),
                        cells.size() > 6 && !cells.get(6).text().isBlank(),
                        cells.size() > 7 ? blankToNull(cells.get(7).text()) : null
                ));
            }

            if (!courses.isEmpty()) {
                bySemester.put(idMatcher.group(1) + "-" + idMatcher.group(2), courses);
            }
        }

        return bySemester;
    }

    // ------------------------------------------------------------------
    // 헬퍼
    // ------------------------------------------------------------------

    /**
     * 주어진 문자열들을 모두 포함하는 표 중 <b>가장 안쪽</b>(문서 순서상 마지막) 것을 찾는 메서드.
     * 원본은 바깥 레이아웃 표가 안쪽 표 내용을 통째로 감싸므로 "마지막 매칭"이 실제 데이터 표다.
     */
    private Element innermostTableContaining(final Document document, final String... needles) {
        Element found = null;
        for (final Element table : document.select("table")) {
            final String text = table.text();
            boolean all = true;
            for (final String needle : needles) {
                if (!text.contains(needle)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                found = table;
            }
        }
        return found;
    }

    /**
     * 표에서 {@code marker} 를 포함한 행 <b>다음</b> 행들만 추리는 메서드 (캡션/헤더 행 스킵용).
     */
    private List<Element> rowsAfter(final Element table, final String marker) {
        final List<Element> rows = new ArrayList<>();
        boolean past = false;
        for (final Element row : table.select("tr")) {
            if (!past) {
                if (row.text().contains(marker)) {
                    past = true;
                }
                continue;
            }
            rows.add(row);
        }
        return rows;
    }

    private Double lookup(final Map<String, Double> values, final String normalizedTarget) {
        for (final Map.Entry<String, Double> entry : values.entrySet()) {
            if (normalize(entry.getKey()).equals(normalizedTarget)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Double parseNumber(final String raw) {
        if (raw == null) {
            return null;
        }
        final String text = raw.replace(",", "").replace(' ', ' ').replace('　', ' ').trim();
        if (text.isEmpty() || text.equals("-")) {
            return null;
        }
        try {
            return Double.valueOf(text);
        } catch (final NumberFormatException ex) {
            return null;
        }
    }

    private String normalize(final String value) {
        return value == null ? "" : value.replace(' ', ' ').replace('　', ' ').replaceAll("\\s", "");
    }

    private String blankToNull(final String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.replace(' ', ' ').replace('　', ' ').trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
