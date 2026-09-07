package com.seohamin.hisnetmobile.domain.timetable.dto;

import java.util.List;

/**
 * 시간표의 한 과목.
 *
 * @param name       과목명 (예: "오픈소스 스튜디오", "채플(한국어) 4"). 끝의 분반 표기 "(01)" 는 뗀다.
 * @param section    분반 (예: "01"). 원본에 없으면 null.
 * @param courseCode 원본 과목 코드 (칸 링크의 {@code CIS_GWAMOK} 값). 못 찾으면 null.
 * @param professor  담당 교원 (예: "장소연", "노규석외 3명"). 원본에 없으면 null.
 * @param slots      요일·교시·강의실 (연속 교시는 합쳐짐)
 */
public record TimetableCourseDto(
        String name,
        String section,
        String courseCode,
        String professor,
        List<TimetableSlotDto> slots
) { }
