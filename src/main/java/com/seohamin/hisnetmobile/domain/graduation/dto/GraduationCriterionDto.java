package com.seohamin.hisnetmobile.domain.graduation.dto;

/**
 * 졸업심사 항목별 판정 한 줄.
 * <p>
 * 원본 표 컬럼은 {@code 구분 | 졸업기준(설계) | 취득학점(설계) | 판정 | 비고}. 기준/취득 칸은
 * 값이 제각각(단순 숫자, "60(12)" 설계학점 병기, "2.0 이상", "※ 비고 참고", 여러 줄 등)이라
 * 숫자로 환산하지 않고 원문 문자열을 그대로 준다.
 *
 * @param category 구분 (예: "신앙및세계관", "총 취득학점", "평점 평균")
 * @param standard 졸업기준(설계) 원문
 * @param earned   취득학점(설계) 원문
 * @param verdict  판정 ("합격" / "불합격" 등, 판정이 없는 항목은 null)
 * @param note     비고 (없으면 null)
 */
public record GraduationCriterionDto(
        String category,
        String standard,
        String earned,
        String verdict,
        String note
) { }
