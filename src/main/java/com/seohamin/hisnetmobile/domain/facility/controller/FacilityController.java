package com.seohamin.hisnetmobile.domain.facility.controller;

import com.seohamin.hisnetmobile.domain.facility.constant.ReservationListType;
import com.seohamin.hisnetmobile.domain.facility.dto.FacilityAvailabilityResponseDto;
import com.seohamin.hisnetmobile.domain.facility.dto.FacilityCatalogResponseDto;
import com.seohamin.hisnetmobile.domain.facility.dto.ReservationDto;
import com.seohamin.hisnetmobile.domain.facility.dto.ReservationListResponseDto;
import com.seohamin.hisnetmobile.domain.facility.dto.ReservationRequestDto;
import com.seohamin.hisnetmobile.domain.facility.service.FacilityReservationService;
import com.seohamin.hisnetmobile.domain.facility.service.FacilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Facility", description = "시설/공간 예약 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/facilities")
public class FacilityController {

    private final FacilityService facilityService;
    private final FacilityReservationService facilityReservationService;

    // 예약 가능한 시설 카탈로그 (운동시설/회의실/편의시설). 원본 요청 없이 상수로 응답.
    @Operation(
            summary = "시설 카탈로그 조회",
            description = "원본 시설예약신청(PSTU420M.php?gubun=1~3) 화면을 코드 상수로 고정한 목록. "
                    + "분류(sports/meeting/convenience) → 시설(id, name) 트리로 반환한다."
    )
    @GetMapping
    public ResponseEntity<FacilityCatalogResponseDto> getCatalog() {

        return ResponseEntity.ok(facilityService.getCatalog());
    }

    // 한 시설의 예약 현황 (기준일부터 10일, 30분 단위 슬롯).
    @Operation(
            summary = "시설 예약 현황 조회",
            description = "원본 PSTU420C.php 를 파싱해 30분 단위 슬롯 현황과 예약 가능시간 쿼터를 반환한다. "
                    + "date 를 생략하면 오늘부터, 지정하면 그 날짜부터 10일치."
    )
    @GetMapping("/{facilityId}/availability")
    public ResponseEntity<FacilityAvailabilityResponseDto> getAvailability(
            @AuthenticationPrincipal final UserDetails userDetails,
            @PathVariable final int facilityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate date
    ) {

        return ResponseEntity.ok(facilityService.getAvailability(userDetails, facilityId, date));
    }

    // 현재 로그인 사용자의 예약 내역.
    @Operation(
            summary = "내 예약 내역 조회",
            description = "원본 PSTU420L.php 를 파싱해 반환한다. status: active(기본)/completed/cancelled/noshow."
    )
    @GetMapping("/reservations")
    public ResponseEntity<ReservationListResponseDto> getMyReservations(
            @AuthenticationPrincipal final UserDetails userDetails,
            @RequestParam(required = false, defaultValue = "active") final String status
    ) {

        return ResponseEntity.ok(
                facilityService.getMyReservations(userDetails, ReservationListType.from(status)));
    }

    // 예약 신청. 30분 단위. 원본(HISNet)에 실제 예약이 생성된다.
    @Operation(
            summary = "시설 예약 신청",
            description = "시작·종료 시각은 30분 단위(예: 14:00~16:00). 원본 현황으로 선검증한 뒤 원본에 신청한다."
    )
    @PostMapping("/{facilityId}/reservations")
    public ResponseEntity<ReservationDto> createReservation(
            @AuthenticationPrincipal final UserDetails userDetails,
            @PathVariable final int facilityId,
            @RequestBody final ReservationRequestDto request
    ) {

        return ResponseEntity.ok(
                facilityReservationService.create(userDetails, facilityId, request));
    }

    // 예약 취소. 원본(HISNet)에서 실제로 취소된다.
    @Operation(
            summary = "시설 예약 취소",
            description = "예약 코드(bookingCode)로 취소한다. 이용시작 1시간 전 이후 취소는 원본에서 벌점이 부여된다."
    )
    @DeleteMapping("/reservations/{bookingCode}")
    public ResponseEntity<Void> cancelReservation(
            @AuthenticationPrincipal final UserDetails userDetails,
            @PathVariable final long bookingCode
    ) {

        facilityReservationService.cancel(userDetails, bookingCode);
        return ResponseEntity.noContent().build();
    }
}
