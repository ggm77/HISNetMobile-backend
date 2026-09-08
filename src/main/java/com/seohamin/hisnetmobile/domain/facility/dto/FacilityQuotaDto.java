package com.seohamin.hisnetmobile.domain.facility.dto;

/**
 * 현황 페이지 상단의 예약 가능시간 쿼터 패널.
 * <p>
 * 원본 문구: "총 예약가능시간 잔여 8시간 / 8시간", "일일 예약가능시간 잔여 2시간 / 2시간",
 * "총예약가능시간 초기화 예정일 2026-09-13 (Sun)". 분 단위로 환산해 담는다.
 * 원본 문구가 바뀌어 파싱하지 못하면 각 필드는 null 이며, 이 경우에도 현황 조회 자체는 실패시키지 않는다.
 *
 * @param totalRemainingMinutes 총 잔여 (분)
 * @param totalLimitMinutes     총 한도 (분)
 * @param dailyRemainingMinutes 일일 잔여 (분)
 * @param dailyLimitMinutes     일일 한도 (분)
 * @param resetDate             총 쿼터 초기화 예정일 "yyyy-MM-dd" (주간 롤링)
 */
public record FacilityQuotaDto(
        Integer totalRemainingMinutes,
        Integer totalLimitMinutes,
        Integer dailyRemainingMinutes,
        Integer dailyLimitMinutes,
        String resetDate
) { }
