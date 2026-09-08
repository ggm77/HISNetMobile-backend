package com.seohamin.hisnetmobile.domain.facility.dto;

import java.util.List;

/**
 * 시설 예약 현황 조회 응답 (원본 PSTU420C.php 파싱 결과).
 * <p>
 * 원본은 기준일부터 10일치를 15분 단위 그리드로 준다. 이 응답은 시간 단위를 <b>30분</b>으로 통일해
 * 병합한 슬롯을 날짜별로 담는다. 예약 신청도 30분 단위로만 받는다.
 *
 * @param facilityId     시설 코드
 * @param facilityName   시설명 (원본 페이지 헤더 기준)
 * @param slotMinutes    슬롯 단위(분) — 항상 30
 * @param operatingStart 운영 시작 시각 "HH:mm" (그리드에서 관측된 최소 시작, 없으면 null)
 * @param operatingEnd   운영 종료 시각 "HH:mm" (그리드에서 관측된 최대 종료, 없으면 null)
 * @param rangeFrom      조회 시작일 "yyyy-MM-dd"
 * @param rangeTo        조회 종료일 "yyyy-MM-dd" (rangeFrom + 9일)
 * @param quota          예약 가능시간 쿼터 (파싱 실패 시 필드가 null 일 수 있음)
 * @param days           날짜별 슬롯 현황 (날짜 오름차순, 최대 10일)
 */
public record FacilityAvailabilityResponseDto(
        int facilityId,
        String facilityName,
        int slotMinutes,
        String operatingStart,
        String operatingEnd,
        String rangeFrom,
        String rangeTo,
        FacilityQuotaDto quota,
        List<FacilityDayAvailabilityDto> days
) { }
