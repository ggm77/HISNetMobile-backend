package com.seohamin.hisnetmobile.domain.timetable.dto;

/**
 * 한 과목이 만나는 시간 한 블록.
 * <p>
 * 원본은 교시 단위 격자라 여기서도 교시 번호로 표현한다(원본에 시각 표기가 없어 시:분으로 환산하지 않는다).
 * 연속된 교시는 하나로 합쳐 {@code startPeriod}~{@code endPeriod} 로 준다(단일 교시면 두 값이 같다).
 *
 * @param day         요일 ("월"/"화"/"수"/"목"/"금"/"토")
 * @param startPeriod 시작 교시 (1~14)
 * @param endPeriod   종료 교시 (1~14, 포함)
 * @param room        강의실 (예: "NTH 414", "HCA 효암본관"). 원본에 없으면 null.
 */
public record TimetableSlotDto(
        String day,
        int startPeriod,
        int endPeriod,
        String room
) { }
