package com.seohamin.hisnetmobile.domain.grade.dto;

import java.util.List;

/**
 * 한 학기 성적 (요약 + 수강 과목).
 *
 * @param year             학년도 (예: 2026)
 * @param term             학기 (1 또는 2, 계절학기 등은 원문 숫자 그대로)
 * @param requestedCredits 신청학점 (학기별 요약 표)
 * @param earnedCredits    취득학점
 * @param gpa              평점평균
 * @param note             비고 (원본에 있으면, 보통 빈값 → null)
 * @param courses          수강 과목별 성적 (상세 표가 없으면 빈 리스트)
 */
public record SemesterGradeDto(
        int year,
        int term,
        Double requestedCredits,
        Double earnedCredits,
        Double gpa,
        String note,
        List<CourseGradeDto> courses
) { }
