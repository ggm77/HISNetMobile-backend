package com.seohamin.hisnetmobile.domain.facility.dto;

import java.util.List;

/**
 * 내 예약 내역 조회 응답 (원본 PSTU420L.php).
 *
 * @param status       조회한 내역 구분 (active/completed/cancelled/noshow)
 * @param reservations 예약 내역 (원본 표기 순서: 최근 건부터)
 */
public record ReservationListResponseDto(
        String status,
        List<ReservationDto> reservations
) { }
