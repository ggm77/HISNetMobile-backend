package com.seohamin.hisnetmobile.domain.facility.dto;

/**
 * 내 예약 내역 한 건 (원본 PSTU420L.php 표의 한 행).
 * <p>
 * 진행중(active) 탭만 예약취소가능기간·바로가기(참석자등록/변경/취소) 칸이 있어 {@code bookingCode} 를 얻는다.
 * 사용완료·취소·미사용취소 탭은 {@code No | 제목 | 장소 | 일시} 4칸뿐이라 {@code bookingCode} 와
 * {@code cancelableUntil} 이 null 이다.
 *
 * @param bookingCode     예약 코드 (원본 {@code bcode} — 취소·변경·참석자등록의 키. 이력 탭은 null)
 * @param title           예약 제목 (없으면 null)
 * @param facilityName    장소(시설명) 원문
 * @param date            이용일 "yyyy-MM-dd"
 * @param startTime       시작 시각 "HH:mm"
 * @param endTime         종료 시각 "HH:mm"
 * @param cancelableUntil 예약취소 가능 마감 "yyyy-MM-dd HH:mm" (진행중 탭만, 없으면 null)
 */
public record ReservationDto(
        Long bookingCode,
        String title,
        String facilityName,
        String date,
        String startTime,
        String endTime,
        String cancelableUntil
) { }
