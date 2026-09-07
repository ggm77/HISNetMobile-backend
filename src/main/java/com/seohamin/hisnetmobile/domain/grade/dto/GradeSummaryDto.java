package com.seohamin.hisnetmobile.domain.grade.dto;

import java.util.Map;

/**
 * 총 누적 성적. 원본 "총 누적 성적" 표(라벨-값 24쌍)에서 뽑는다.
 * <p>
 * 자주 쓰는 값은 타입을 지정해 노출하고, 이수구분별 학점(교양필수/전공선택/…)은 {@code creditsByType} 에,
 * 파싱된 라벨-값 전체(문자열)는 {@code raw} 에 담는다. 원본 표기가 없거나 비면 null.
 *
 * @param requestedCredits  신청학점
 * @param earnedCredits      취득학점
 * @param gpa                평점평균
 * @param majorGpa           전공평점평균
 * @param conversionScore    환산점수 (100점 환산)
 * @param totalGradePoints   평점계
 * @param pfCredits          PF이수학점
 * @param creditsByType      이수구분별 취득학점 (원문 라벨 그대로: "교양필수", "전공선택", …)
 * @param raw                원본 라벨 → 값(문자열) 전체
 */
public record GradeSummaryDto(
        Double requestedCredits,
        Double earnedCredits,
        Double gpa,
        Double majorGpa,
        Double conversionScore,
        Double totalGradePoints,
        Double pfCredits,
        Map<String, Double> creditsByType,
        Map<String, String> raw
) { }
