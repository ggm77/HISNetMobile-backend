package com.seohamin.hisnetmobile.domain.graduation.dto;

import java.util.List;

/**
 * 졸업심사 결과 조회 응답.
 * <p>
 * 진입 페이지({@code HGRA120M.php})의 "졸업심사 결과보기" 버튼이 학생 유형별 결과 페이지
 * ({@code /prof/graduate/PGRA123S*.php})로 이동시킨다. 이 응답은 그 결과 페이지를 파싱한 것이다.
 * 졸업이 이미 확정돼 결과를 더 이상 조회할 수 없는 학생은 {@code available=false} 로 온다.
 *
 * @param available          원본에서 결과를 조회할 수 있는 상태인지 (false 면 나머지 필드는 대부분 비어있음)
 * @param certificationType  "공학인증" 또는 "일반" (공학교육인증 대상 여부)
 * @param requiredCredits    졸업 기준 학점 (예: 130). 원본 제목에서 추출, 못 찾으면 null
 * @param notices            원본 상단 ▣ 안내문
 * @param student            심사 대상 학생 정보
 * @param criteria           항목별 판정 결과 (최종 졸업판정 행은 제외하고 {@link #finalVerdict} 로 뺀다)
 * @param finalVerdict       최종 졸업판정 ("졸업가능" / "졸업불가능" 등)
 */
public record GraduationResponseDto(
        boolean available,
        String certificationType,
        Integer requiredCredits,
        List<String> notices,
        GraduationStudentDto student,
        List<GraduationCriterionDto> criteria,
        String finalVerdict
) { }
