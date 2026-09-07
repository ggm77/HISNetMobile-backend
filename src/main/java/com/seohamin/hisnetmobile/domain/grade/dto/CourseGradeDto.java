package com.seohamin.hisnetmobile.domain.grade.dto;

/**
 * 한 과목의 성적 (학기별 상세 표의 한 행).
 *
 * @param code        과목코드 (예: "ITP20001")
 * @param name        과목명
 * @param type        이수구분 (예: "전공필수", "교양선택필수")
 * @param credits     학점 (0.5 학점 과목 존재)
 * @param grade       성적 등급 원문 ("A+", "A0", "B+", "P", "PD", …)
 * @param gradePoints 평점 (4.5, 4.0, … / P·PF 과목은 0)
 * @param retake      재이수 여부 (원본 재이수 칸이 비어있지 않으면 true)
 * @param note        비고 (원본에 있으면, 보통 빈값 → null)
 */
public record CourseGradeDto(
        String code,
        String name,
        String type,
        Double credits,
        String grade,
        Double gradePoints,
        boolean retake,
        String note
) { }
