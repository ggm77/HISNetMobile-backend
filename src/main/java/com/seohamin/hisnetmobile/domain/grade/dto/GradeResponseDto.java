package com.seohamin.hisnetmobile.domain.grade.dto;

import java.util.List;

/**
 * 전체성적조회 응답 (HREC110M.php, "성적 &gt; 전체성적조회").
 * <p>
 * 원본 한 페이지에 (1) 총 누적 성적, (2) 학기별 요약 표, (3) 학기별 상세 과목 표(숨은 div)가 모두 들어있어
 * 한 번의 조회로 전부 재조립한다. 현학기 성적은 성적 입력·정정이 끝나야 원본에 뜬다.
 *
 * @param summary   총 누적 성적 (평점평균·전공평점평균·이수구분별 학점 등)
 * @param semesters 학기별 성적 (원본 표기 순서: 최근 학기부터)
 */
public record GradeResponseDto(
        GradeSummaryDto summary,
        List<SemesterGradeDto> semesters
) { }
