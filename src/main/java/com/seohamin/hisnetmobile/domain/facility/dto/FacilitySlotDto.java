package com.seohamin.hisnetmobile.domain.facility.dto;

import com.seohamin.hisnetmobile.domain.facility.constant.SlotStatus;

/**
 * 현황 그리드의 30분 슬롯 한 칸.
 *
 * @param start  시작 시각 "HH:mm"
 * @param end    종료 시각 "HH:mm"
 * @param status 예약 상태
 */
public record FacilitySlotDto(
        String start,
        String end,
        SlotStatus status
) { }
