package com.seohamin.hisnetmobile.domain.student.service;

import com.seohamin.hisnetmobile.domain.student.dto.StudentInfoResponseDto;
import com.seohamin.hisnetmobile.global.exception.CustomException;
import com.seohamin.hisnetmobile.global.exception.constants.ExceptionCode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 학적 기본정보 페이지(HHAK110M.php)를 파싱하는 컴포넌트.
 * <p>
 * 실제 구조(2024 확인): {@code form[name=form1]} 안에 표 하나. 라벨 셀은 {@code td.tblcationTitlecls},
 * 값은 대부분 바로 옆 셀의 평문이고, 성명(영문)/휴대폰/전화번호/이메일 네 곳만 {@code <input value>} 다.
 * 주소는 {@code zip1 / address1 / address2} 입력값 세 개로 쪼개져 있어 따로 합쳐준다.
 * 라벨명은 표기 흔들림(공백/콜론)을 감안해 정규화 후 동의어로 매칭한다.
 */
@Component
@Slf4j
public class StudentInfoParser {

    // 라벨 셀 클래스 (원본 표기 그대로 — 오타처럼 보이지만 실제 클래스명이다)
    private static final String LABEL_CELL_SELECTOR = "td.tblcationTitlecls, th.tblcationTitlecls";

    /** 표준 키 → 원본 라벨 표기들 (normalize 후 정확히 일치 비교) */
    private static final Map<String, List<String>> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put("studentNo", List.of("학번"));
        LABELS.put("name", List.of("성명", "이름"));
        LABELS.put("academicStatus", List.of("학적상태"));
        LABELS.put("grade", List.of("학년"));
        LABELS.put("nationality", List.of("국적"));
        LABELS.put("birthDate", List.of("생년월일"));
        LABELS.put("curriculumType", List.of("교육과정구분"));
        LABELS.put("admissionDate", List.of("입학일자", "입학일"));
        LABELS.put("highSchool", List.of("출신학교"));
        LABELS.put("graduationDate", List.of("졸업일자", "졸업예정일자"));
        LABELS.put("degreeNumber", List.of("학위번호"));
        LABELS.put("department", List.of("학부", "학과", "학부(과)", "학부/과"));
        LABELS.put("major", List.of("전공", "주전공"));
        LABELS.put("doubleMajor", List.of("복수전공"));
        LABELS.put("minor", List.of("부전공"));
        LABELS.put("engineeringCertification", List.of("공학인증"));
        LABELS.put("combinedDegree", List.of("학석사연계"));
        LABELS.put("practicalComputing", List.of("실무전산여부"));
        LABELS.put("rcInfo", List.of("rc정보"));
        LABELS.put("mobile", List.of("휴대폰", "휴대전화", "핸드폰"));
        LABELS.put("phone", List.of("전화번호", "자택전화"));
        LABELS.put("email", List.of("e-mail", "이메일", "전자우편", "email"));
        LABELS.put("address", List.of("주소"));
    }

    /**
     * 학적 기본정보 페이지를 파싱해 응답 DTO 로 변환하는 메서드.
     * @param document EUC-KR 로 디코딩된 HHAK110M.php 페이지
     * @return 학적 기본정보
     */
    public StudentInfoResponseDto parse(final Document document) {

        // 1) 데이터 표는 form[name=form1] 안에 있다. 못 찾으면 문서 전체로 폴백.
        final Element root = document.selectFirst("form[name=form1]") != null
                ? document.selectFirst("form[name=form1]")
                : document;

        // 2) 라벨 셀을 훑어 "표준 키 → 값" + "라벨 원문 → 값" 을 수집
        final Map<String, String> byKey = new LinkedHashMap<>();
        final Map<String, String> raw = new LinkedHashMap<>();

        for (final Element labelCell : root.select(LABEL_CELL_SELECTOR)) {
            final String labelText = labelCell.text().trim();
            final String normalized = normalize(labelText);
            if (normalized.isEmpty()) {
                continue;
            }

            final String key = matchKey(normalized);
            if (key == null) {
                // 인식하지 못한 라벨(보호자/차량 등)은 값이 라벨·안내문과 섞여 신뢰하기 어려워 건너뛴다
                continue;
            }

            final String value = extractValue(root, normalized, labelCell);
            if (value == null || value.isEmpty()) {
                continue;
            }

            byKey.putIfAbsent(key, value);
            raw.putIfAbsent(labelText, value);
        }

        // 3) 성명(영문)은 별도 입력값. 학번은 라벨 셀이 비어 있으면 hidden 입력으로 보강.
        final String nameEnglish = inputValue(root, "hakj_irum_eng");
        byKey.computeIfAbsent("studentNo", k -> inputValue(root, "hakbun"));

        // 4) 최소한 학번이나 성명은 나와야 정상 페이지로 본다
        if (isBlank(byKey.get("studentNo")) && isBlank(byKey.get("name"))) {
            log.warn("[학적 정보 파싱 실패] 학번/성명을 찾지 못함 (구조 변경 의심)");
            throw new CustomException(ExceptionCode.STUDENT_INFO_PARSING_FAILED);
        }

        return new StudentInfoResponseDto(
                byKey.get("studentNo"),
                byKey.get("name"),
                nameEnglish,
                byKey.get("academicStatus"),
                byKey.get("grade"),
                byKey.get("nationality"),
                byKey.get("birthDate"),
                byKey.get("curriculumType"),
                byKey.get("admissionDate"),
                byKey.get("highSchool"),
                byKey.get("graduationDate"),
                byKey.get("degreeNumber"),
                byKey.get("department"),
                byKey.get("major"),
                byKey.get("doubleMajor"),
                byKey.get("minor"),
                byKey.get("engineeringCertification"),
                byKey.get("combinedDegree"),
                byKey.get("practicalComputing"),
                byKey.get("rcInfo"),
                byKey.get("mobile"),
                byKey.get("phone"),
                byKey.get("email"),
                byKey.get("address"),
                raw
        );
    }

    /**
     * 라벨 셀에 대응하는 값을 뽑는 메서드.
     * <ul>
     *   <li>주소: zip1/address1/address2 입력값을 합쳐서 돌려준다</li>
     *   <li>성명: 값 셀에 한글 이름(평문)과 영문 이름(입력)이 같이 있어 <b>평문</b>을 쓴다</li>
     *   <li>그 외 입력이 있는 셀(휴대폰/전화번호/이메일): 옆에 안내문이 붙어 있어 <b>입력값</b>을 쓴다</li>
     *   <li>입력이 없는 셀: 셀의 평문을 쓴다</li>
     * </ul>
     */
    private String extractValue(
            final Element root,
            final String normalizedLabel,
            final Element labelCell
    ) {
        if ("주소".equals(normalizedLabel)) {
            return joinNonBlank(
                    inputValue(root, "zip1"),
                    inputValue(root, "address1"),
                    inputValue(root, "address2")
            );
        }

        final Element valueCell = nextValueCell(labelCell);
        if (valueCell == null) {
            return null;
        }

        final boolean preferText = "성명".equals(normalizedLabel) || "이름".equals(normalizedLabel);
        final Element field = valueCell.selectFirst("input, select, textarea");

        if (field != null && !preferText) {
            return fieldValue(field);
        }

        final String text = valueCell.ownText().trim().isEmpty()
                ? valueCell.text().trim()
                : valueCell.ownText().trim();
        if (!text.isEmpty()) {
            return text;
        }

        return field != null ? fieldValue(field) : null;
    }

    /**
     * 입력 요소의 현재 값을 읽는 메서드. select 는 선택된 option 텍스트를 쓴다.
     */
    private String fieldValue(final Element field) {
        if ("select".equalsIgnoreCase(field.tagName())) {
            final Element selected = field.selectFirst("option[selected]");
            final String text = selected != null ? selected.text().trim() : "";
            return text.isEmpty() ? null : text;
        }

        if (!field.hasAttr("value")) {
            return null;
        }
        final String value = field.attr("value").trim();

        return value.isEmpty() ? null : value;
    }

    /**
     * 라벨 셀의 값 셀을 찾는 메서드. 바로 옆 형제를 우선하고, 없으면 같은 행에서 라벨이 아닌 첫 셀을 쓴다.
     */
    private Element nextValueCell(final Element labelCell) {
        final Element sibling = labelCell.nextElementSibling();
        if (sibling != null) {
            return sibling;
        }

        final Element row = labelCell.closest("tr");
        if (row == null) {
            return null;
        }

        boolean seenLabel = false;
        for (final Element cell : row.select("td, th")) {
            if (cell == labelCell) {
                seenLabel = true;
                continue;
            }
            if (seenLabel && !cell.hasClass("tblcationTitlecls")) {
                return cell;
            }
        }

        return null;
    }

    /**
     * 정규화된 라벨을 표준 키로 매핑하는 메서드.
     * @return 매칭되는 표준 키, 없으면 null
     */
    private String matchKey(final String normalizedLabel) {
        for (final Map.Entry<String, List<String>> entry : LABELS.entrySet()) {
            for (final String synonym : entry.getValue()) {
                if (normalizedLabel.equalsIgnoreCase(normalize(synonym))) {
                    return entry.getKey();
                }
            }
        }

        return null;
    }

    /**
     * form 안에서 name 으로 입력 요소의 value 를 읽는 메서드.
     * @return 값이 없으면 null
     */
    private String inputValue(
            final Element root,
            final String name
    ) {
        final Element input = root.selectFirst("input[name=" + name + "]");
        if (input == null || !input.hasAttr("value")) {
            return null;
        }
        final String value = input.attr("value").trim();

        return value.isEmpty() ? null : value;
    }

    /**
     * 공백이 아닌 값들만 한 칸 띄워 잇는 메서드.
     * @return 이을 값이 없으면 null
     */
    private String joinNonBlank(final String... parts) {
        final StringBuilder builder = new StringBuilder();
        for (final String part : parts) {
            if (part != null && !part.trim().isEmpty()) {
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(part.trim());
            }
        }

        return builder.length() == 0 ? null : builder.toString();
    }

    /**
     * 라벨 비교용 정규화: 앞뒤 공백/전각공백/일반공백/콜론 제거.
     */
    private String normalize(final String text) {
        if (text == null) {
            return "";
        }

        return text.replace("　", "")
                .replaceAll("\\s", "")
                .replace(":", "")
                .replace("：", "")
                .trim();
    }

    private boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }
}