package com.seohamin.hisnetmobile.domain.facility.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 시설 예약 신청·변경 요청 바디 (application/json).
 * <p>
 * 시간은 <b>30분 단위</b>로만 받는다(예: 14:00, 14:30). 종료 시각은 마지막 이용 30분의 끝을 가리킨다
 * (14:00~16:00 = 2시간). 유효성 검사와 원본 폼 변환은 {@code ReservationFormBuilder} 가 한다.
 *
 * @param date      이용일 "yyyy-MM-dd"
 * @param startTime 시작 시각 "HH:mm" (분은 00 또는 30)
 * @param endTime   종료 시각 "HH:mm" (분은 00 또는 30, startTime 보다 뒤)
 */
public record ReservationRequestDto(

        @Schema(description = "이용일 (yyyy-MM-dd)", example = "2026-09-15")
        String date,

        @Schema(description = "시작 시각 HH:mm, 30분 단위", example = "14:00")
        String startTime,

        @Schema(description = "종료 시각 HH:mm, 30분 단위", example = "16:00")
        String endTime
) { }
