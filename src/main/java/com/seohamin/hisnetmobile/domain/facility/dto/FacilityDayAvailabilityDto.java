package com.seohamin.hisnetmobile.domain.facility.dto;

import java.util.List;

/**
 * 하루치 슬롯 현황.
 *
 * @param date    날짜 "yyyy-MM-dd"
 * @param weekday 요일 (MON~SUN)
 * @param slots   30분 슬롯 목록 (운영시간 내 시간 오름차순)
 */
public record FacilityDayAvailabilityDto(
        String date,
        String weekday,
        List<FacilitySlotDto> slots
) { }
