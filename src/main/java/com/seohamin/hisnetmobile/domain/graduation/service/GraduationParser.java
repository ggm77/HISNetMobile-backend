package com.seohamin.hisnetmobile.domain.graduation.service;

import com.seohamin.hisnetmobile.domain.graduation.dto.GraduationCriterionDto;
import com.seohamin.hisnetmobile.domain.graduation.dto.GraduationResponseDto;
import com.seohamin.hisnetmobile.domain.graduation.dto.GraduationStudentDto;
import com.seohamin.hisnetmobile.global.exception.CustomException;
import com.seohamin.hisnetmobile.global.exception.constants.ExceptionCode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 졸업심사 결과 페이지를 파싱하는 컴포넌트.
 * <p>
 * 진입 페이지({@code HGRA120M.php})의 "결과보기" 버튼 onclick 은 {@code view('PGRA123S_gong')} 처럼
 * 결과 페이지 파일명 토큰만 넘긴다. 실제 결과는 {@code /prof/graduate/{토큰}.php?gubun=hak} 에 있다.
 * 결과 페이지는 id/class 앵커가 거의 없어, 표는 "특정 텍스트를 가진 가장 안쪽 표"로 지목한다.
 * <ul>
 *   <li>학생 정보 표 — "학부"·"학번"·"등록학기" 포함</li>
 *   <li>판정 표 — 헤더 {@code 구분 | 졸업기준(설계) | 취득학점(설계) | 판정 | 비고}</li>
 *   <li>안내문 — 요소의 직접 텍스트가 {@code ▣} 로 시작하는 것들</li>
 * </ul>
 */
@Component
@Slf4j
public class GraduationParser {

    // HGRA120M.php 결과보기 버튼: view('PGRA123S_gong')
    private static final Pattern RESULT_TOKEN = Pattern.compile("view\\((['\"])([A-Za-z0-9_]+)\\1\\)");
    // 안전장치: 결과 페이지 파일명은 PGRA 로 시작
    private static final Pattern VALID_TOKEN = Pattern.compile("^PGRA[A-Za-z0-9_]{1,40}$");

    private static final Pattern REQUIRED_CREDITS = Pattern.compile("(\\d{2,3})\\s*졸업학점");
    private static final Pattern REGISTERED_TERMS = Pattern.compile("(\\d{1,2})");
    private static final String FINAL_VERDICT_MARK = "최종 졸업판정";

    /** 학생 정보 표: 표준 키 → 원본 라벨(정규화 후 정확 일치) */
    private static final Map<String, List<String>> STUDENT_LABELS = new LinkedHashMap<>();

    static {
        STUDENT_LABELS.put("department", List.of("학부", "학부(과)", "학과"));
        STUDENT_LABELS.put("name", List.of("이름", "성명"));
        STUDENT_LABELS.put("studentNo", List.of("학번"));
        STUDENT_LABELS.put("academicStatus", List.of("학적", "학적상태"));
        STUDENT_LABELS.put("registeredTerms", List.of("등록학기수", "등록학기 수", "등록학기"));
        STUDENT_LABELS.put("major", List.of("전공", "주전공"));
        STUDENT_LABELS.put("minor", List.of("부전공"));
        STUDENT_LABELS.put("subMajor", List.of("실무전산/컴퓨터공학부전공", "실무전산부전공", "컴퓨터공학부전공"));
    }

    /**
     * 진입 페이지({@code HGRA120M.php})에서 결과 페이지 파일명 토큰을 뽑는 메서드.
     * @return 토큰 (예: {@code PGRA123S_gong}). 버튼이 없으면(조회 불가) null.
     */
    public String extractResultPageToken(final Document entryPage) {
        final Matcher matcher = RESULT_TOKEN.matcher(entryPage.html());
        while (matcher.find()) {
            final String token = matcher.group(2);
            if (VALID_TOKEN.matcher(token).matches()) {
                return token;
            }
        }
        return null;
    }

    /**
     * 결과를 조회할 수 없는(버튼 없는) 경우의 응답 — 진입 페이지의 안내문만 실어준다.
     */
    public GraduationResponseDto unavailable(final Document entryPage) {
        return new GraduationResponseDto(
                false, null, null, extractNotices(entryPage), null, List.of(), null);
    }

    /**
     * 결과 페이지를 파싱해 응답 DTO 로 변환하는 메서드.
     * @param resultPage EUC-KR 로 디코딩된 PGRA123S*.php 페이지
     * @return 졸업심사 결과
     */
    public GraduationResponseDto parse(final Document resultPage) {

        final String title = findTitle(resultPage);
        final Element studentTable = innermostTableContaining(resultPage, "학부", "학번", "등록학기");
        final Element criteriaTable = innermostTableContaining(
                resultPage, "구분", "졸업기준", "판정", "비고");

        if (studentTable == null && criteriaTable == null) {
            log.warn("[졸업심사 파싱 실패] 학생 정보/판정 표를 모두 찾지 못함 (구조 변경 의심)");
            throw new CustomException(ExceptionCode.GRADUATION_PARSING_FAILED);
        }

        final String certificationType = title != null && title.contains("공학인증") ? "공학인증" : "일반";
        final Integer requiredCredits = extractRequiredCredits(title);

        final List<GraduationCriterionDto> criteria = new ArrayList<>();
        final String[] finalVerdict = {null};
        if (criteriaTable != null) {
            parseCriteria(criteriaTable, criteria, finalVerdict);
        }

        return new GraduationResponseDto(
                true,
                certificationType,
                requiredCredits,
                extractNotices(resultPage),
                studentTable != null ? parseStudent(studentTable) : null,
                criteria,
                finalVerdict[0]
        );
    }

    // ------------------------------------------------------------------
    // 안내문 / 제목
    // ------------------------------------------------------------------

    private List<String> extractNotices(final Document document) {
        final List<String> notices = new ArrayList<>();
        for (final Element element : document.getAllElements()) {
            final String own = element.ownText().trim();
            if (own.startsWith("▣")) {
                final String text = own.replaceFirst("^▣\\s*", "").trim();
                if (!text.isEmpty() && !notices.contains(text)) {
                    notices.add(text);
                }
            }
        }
        return notices;
    }

    private String findTitle(final Document document) {
        for (final Element element : document.getAllElements()) {
            final String text = element.ownText().trim();
            if (text.contains("졸업심사결과조회") && text.contains("(") && text.length() < 80) {
                return text;
            }
        }
        return null;
    }

    private Integer extractRequiredCredits(final String title) {
        if (title == null) {
            return null;
        }
        final Matcher matcher = REQUIRED_CREDITS.matcher(title);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    // ------------------------------------------------------------------
    // 학생 정보
    // ------------------------------------------------------------------

    private GraduationStudentDto parseStudent(final Element table) {

        final Map<String, String> raw = new LinkedHashMap<>();
        for (final Element row : table.select("tr")) {
            final Elements cells = row.select("td, th");
            for (int i = 0; i + 1 < cells.size(); i += 2) {
                final String label = cells.get(i).text().trim();
                final String value = cells.get(i + 1).text().trim();
                if (!label.isEmpty()) {
                    raw.putIfAbsent(label, value);
                }
            }
        }

        return new GraduationStudentDto(
                byLabel(raw, "department"),
                byLabel(raw, "name"),
                byLabel(raw, "studentNo"),
                byLabel(raw, "academicStatus"),
                parseTerms(byLabel(raw, "registeredTerms")),
                byLabel(raw, "major"),
                blankToNull(byLabel(raw, "minor")),
                blankToNull(byLabel(raw, "subMajor")),
                raw
        );
    }

    private String byLabel(final Map<String, String> raw, final String key) {
        final List<String> synonyms = STUDENT_LABELS.get(key);
        for (final Map.Entry<String, String> entry : raw.entrySet()) {
            final String normalized = normalize(entry.getKey());
            for (final String synonym : synonyms) {
                if (normalized.equals(normalize(synonym))) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private Integer parseTerms(final String value) {
        if (value == null) {
            return null;
        }
        final Matcher matcher = REGISTERED_TERMS.matcher(value);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    // ------------------------------------------------------------------
    // 판정 표
    // ------------------------------------------------------------------

    private void parseCriteria(
            final Element table,
            final List<GraduationCriterionDto> out,
            final String[] finalVerdict
    ) {
        boolean pastHeader = false;
        for (final Element row : table.select("tr")) {
            final Elements cells = row.select("td, th");

            if (!pastHeader) {
                if (row.text().contains("구분") && row.text().contains("졸업기준")) {
                    pastHeader = true;
                }
                continue;
            }
            if (cells.size() < 4) {
                continue;
            }

            final String category = cells.get(0).text().trim();
            if (category.isEmpty()) {
                continue;
            }
            if (category.contains(FINAL_VERDICT_MARK)) {
                finalVerdict[0] = blankToNull(cells.get(3).text());
                continue;
            }

            out.add(new GraduationCriterionDto(
                    category,
                    cells.get(1).text().trim(),
                    cells.get(2).text().trim(),
                    blankToNull(cells.get(3).text()),
                    cells.size() > 4 ? blankToNull(cells.get(4).text()) : null
            ));
        }
    }

    // ------------------------------------------------------------------
    // 공통
    // ------------------------------------------------------------------

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

    private String normalize(final String value) {
        return value == null ? "" : value.replace('\u00A0', ' ').replace('\u3000', ' ').replaceAll("[\\s:：]", "");
    }

    private String blankToNull(final String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.replace('\u00A0', ' ').replace('\u3000', ' ').trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
