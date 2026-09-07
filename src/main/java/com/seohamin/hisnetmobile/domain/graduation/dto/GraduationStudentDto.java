package com.seohamin.hisnetmobile.domain.graduation.dto;

import java.util.Map;

/**
 * 졸업심사 대상 학생 정보 (결과 페이지 상단 표).
 *
 * @param department      학부
 * @param name            이름
 * @param studentNo       학번
 * @param academicStatus  학적 (재학/복학/휴학 등)
 * @param registeredTerms 등록학기 수 ("4 학기" → 4)
 * @param major           전공 (예: "AI·컴퓨터공학심화(60)")
 * @param minor           부전공 (없으면 null)
 * @param subMajor        실무전산/컴퓨터공학 부전공 등 부가 전공 표기 (없으면 null)
 * @param raw             원본 라벨 → 값 전체
 */
public record GraduationStudentDto(
        String department,
        String name,
        String studentNo,
        String academicStatus,
        Integer registeredTerms,
        String major,
        String minor,
        String subMajor,
        Map<String, String> raw
) { }
