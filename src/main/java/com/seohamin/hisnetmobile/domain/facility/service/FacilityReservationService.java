package com.seohamin.hisnetmobile.domain.facility.service;

import com.seohamin.hisnetmobile.domain.facility.constant.Facility;
import com.seohamin.hisnetmobile.domain.facility.constant.ReservationListType;
import com.seohamin.hisnetmobile.domain.facility.constant.SlotStatus;
import com.seohamin.hisnetmobile.domain.facility.dto.FacilityAvailabilityResponseDto;
import com.seohamin.hisnetmobile.domain.facility.dto.FacilityDayAvailabilityDto;
import com.seohamin.hisnetmobile.domain.facility.dto.ReservationDto;
import com.seohamin.hisnetmobile.domain.facility.dto.ReservationRequestDto;
import com.seohamin.hisnetmobile.global.exception.CustomException;
import com.seohamin.hisnetmobile.global.exception.constants.ExceptionCode;
import com.seohamin.hisnetmobile.global.infra.hisnet.HisnetClient;
import com.seohamin.hisnetmobile.global.infra.hisnet.HisnetSession;
import com.seohamin.hisnetmobile.global.security.HisnetUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 시설 예약 <b>신청·변경·취소</b>(상태변경)를 원본(HISNet)에 중계하는 서비스.
 * <p>
 * 흐름: 인증 주체에서 원본 세션 추출 → 입력 검증(30분 단위) → 원본 현황으로 선검증 →
 * 30분 요청을 원본 15분 폼으로 변환({@link ReservationFormBuilder}) → 원본에 전송.
 * <p>
 * 원본 응답(PSTU420II.php / PSTU420D.php)은 성패를 상태코드로 주지 않고 JS alert 스텁을 준다.
 * {@link #interpretCreateResult} 의 실패 문구 매핑은 실제 응답 샘플로 보정해야 한다(현재는 재조회 기반 추정).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FacilityReservationService {

    private static final String INSERT_PATH = "/prof/booking/PSTU420II.php";
    private static final String CANCEL_PATH = "/prof/booking/PSTU420D.php?bno=%d";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final HisnetClient hisnetClient;
    private final FacilityService facilityService;
    private final ReservationFormBuilder formBuilder;

    /**
     * 예약을 신청하는 메서드.
     *
     * @param userDetails 인증 주체
     * @param facilityId  시설 코드
     * @param request     이용일·시작·종료 (30분 단위)
     * @return 생성된 예약
     * @throws CustomException 30분 단위 위반({@link ExceptionCode#INVALID_RESERVATION_TIME}),
     *                         선택 구간에 예약 불가 슬롯 포함({@link ExceptionCode#RESERVATION_SLOT_UNAVAILABLE})
     */
    public ReservationDto create(
            final UserDetails userDetails,
            final int facilityId,
            final ReservationRequestDto request
    ) {
        final Facility facility = Facility.from(facilityId);
        final LocalDate date = parseDate(request.date());
        final LocalTime start = parseTime(request.startTime());
        final LocalTime end = parseTime(request.endTime());

        // 30분 정렬·구간 검증 + 원본 폼 변환
        final MultiValueMap<String, String> form = formBuilder.build(facility, date, start, end, null);

        // 원본 현황으로 선검증 (읽기 전용 — 예약을 만들지 않는다)
        ensureRangeAvailable(userDetails, facility, date, start, end);

        log.info("[시설 예약 신청] facility={}({}) {} {}~{}",
                facility.displayName(), facility.code(), date, start, end);

        final Document response = hisnetClient.post(INSERT_PATH, form, resolveSession(userDetails));
        return interpretCreateResult(response, userDetails, facility, date, start);
    }

    /**
     * 예약을 취소하는 메서드. 이용시작 1시간 전 이후 취소는 원본에서 벌점이 부여된다.
     *
     * @param userDetails 인증 주체
     * @param bookingCode 예약 코드 (원본 {@code bcode})
     */
    public void cancel(final UserDetails userDetails, final long bookingCode) {
        final HisnetSession session = resolveSession(userDetails);

        log.info("[시설 예약 취소] bookingCode={}", bookingCode);

        hisnetClient.mutateViaGet(CANCEL_PATH.formatted(bookingCode), session);
        // TODO: 실제 취소 응답 본문 샘플을 확보해 성패/벌점 문구를 해석한다.
    }

    // ------------------------------------------------------------------
    // 선검증
    // ------------------------------------------------------------------

    private void ensureRangeAvailable(
            final UserDetails userDetails,
            final Facility facility,
            final LocalDate date,
            final LocalTime start,
            final LocalTime end
    ) {
        final FacilityAvailabilityResponseDto availability =
                facilityService.getAvailability(userDetails, facility.code(), date);

        final FacilityDayAvailabilityDto day = availability.days().stream()
                .filter(d -> d.date().equals(date.format(DateTimeFormatter.ISO_LOCAL_DATE)))
                .findFirst()
                .orElseThrow(() -> new CustomException(ExceptionCode.RESERVATION_SLOT_UNAVAILABLE));

        for (LocalTime slot = start; slot.isBefore(end); slot = slot.plusMinutes(30)) {
            final String slotStart = slot.format(TIME);
            final boolean available = day.slots().stream()
                    .anyMatch(s -> s.start().equals(slotStart) && s.status() == SlotStatus.AVAILABLE);
            if (!available) {
                throw new CustomException(ExceptionCode.RESERVATION_SLOT_UNAVAILABLE);
            }
        }
    }

    // ------------------------------------------------------------------
    // 응답 해석
    // ------------------------------------------------------------------

    /**
     * 신청 응답을 해석한다. 원본은 성공/실패를 상태코드로 주지 않으므로, 신청 직후 진행중 예약 내역을
     * 재조회해 방금 만든 예약(같은 시설·날짜·시작시각)을 찾아 반환한다. 찾지 못하면 실패로 본다.
     * <p>실제 alert 문구(쿼터 초과·중복·시간 마감 등) 매핑은 응답 샘플 확보 후 보완한다.
     */
    private ReservationDto interpretCreateResult(
            final Document response,
            final UserDetails userDetails,
            final Facility facility,
            final LocalDate date,
            final LocalTime start
    ) {
        final String body = response.text();
        log.info("[시설 예약 신청 응답] facility={} 응답 길이={}", facility.code(), body.length());

        return facilityService.getMyReservations(userDetails, ReservationListType.ACTIVE).reservations().stream()
                .filter(r -> date.format(DateTimeFormatter.ISO_LOCAL_DATE).equals(r.date()))
                .filter(r -> start.format(TIME).equals(r.startTime()))
                .findFirst()
                .orElseThrow(() -> new CustomException(ExceptionCode.HISNET_REQUEST_FAILED));
    }

    // ------------------------------------------------------------------
    // 공통
    // ------------------------------------------------------------------

    private LocalDate parseDate(final String raw) {
        try {
            return LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (final DateTimeParseException | NullPointerException ex) {
            throw new CustomException(ExceptionCode.INVALID_REQUEST);
        }
    }

    private LocalTime parseTime(final String raw) {
        try {
            return LocalTime.parse(raw, TIME);
        } catch (final DateTimeParseException | NullPointerException ex) {
            throw new CustomException(ExceptionCode.INVALID_RESERVATION_TIME);
        }
    }

    private HisnetSession resolveSession(final UserDetails userDetails) {
        if (!(userDetails instanceof HisnetUserDetails hisnetUserDetails)) {
            throw new CustomException(ExceptionCode.SESSION_EXPIRED);
        }

        return new HisnetSession(
                hisnetUserDetails.getPhpSessionId(),
                hisnetUserDetails.getCookieId());
    }
}
