package com.seohamin.hisnetmobile.domain.timetable.dto;

import java.util.List;

/**
 * 내 시간표 조회 응답 (HLES110M.php, "수강정보 &gt; 내시간표조회").
 * <p>
 * 원본은 연도/학기 선택 없이 <b>현재 학기</b> 시간표를 14교시 × 요일(월~토) 격자로 렌더링한다.
 * 한 과목이 연속된 여러 교시에 걸치면 원본 격자에는 교시마다 같은 칸이 반복되는데, 이 응답에서는
 * 과목 단위로 묶고 연속 교시는 하나의 {@link TimetableSlotDto} 로 합쳐서 내려준다.
 *
 * @param courses 수강 과목 목록 (원본 격자에 처음 등장한 순서)
 */
public record TimetableResponseDto(
        List<TimetableCourseDto> courses
) { }
