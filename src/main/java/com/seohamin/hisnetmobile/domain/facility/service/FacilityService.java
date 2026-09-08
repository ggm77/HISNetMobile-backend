package com.seohamin.hisnetmobile.domain.facility.service;

import com.seohamin.hisnetmobile.domain.facility.constant.Facility;
import com.seohamin.hisnetmobile.domain.facility.constant.ReservationListType;
import com.seohamin.hisnetmobile.domain.facility.dto.FacilityAvailabilityResponseDto;
import com.seohamin.hisnetmobile.domain.facility.dto.FacilityCatalogResponseDto;
import com.seohamin.hisnetmobile.domain.facility.dto.ReservationListResponseDto;
import com.seohamin.hisnetmobile.global.exception.CustomException;
import com.seohamin.hisnetmobile.global.exception.constants.ExceptionCode;
import com.seohamin.hisnetmobile.global.infra.hisnet.HisnetClient;
import com.seohamin.hisnetmobile.global.infra.hisnet.HisnetSession;
import com.seohamin.hisnetmobile.global.security.HisnetUserDetails;
import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Document;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 시설 예약 <b>조회</b>를 원본(HISNet)에 중계하는 서비스.
 * <p>
 * 흐름은 다른 조회 도메인과 같다: 인증 주체에서 원본 세션 추출 → 원본 GET 릴레이(EUC-KR) → 파서로 재조립.
 * 신청·변경·취소(상태변경)는 {@link FacilityReservationService} 가 담당한다.
 * <ul>
 *   <li>카탈로그: 원본 요청 없이 {@link Facility} 상수로 응답</li>
 *   <li>현황: {@code PSTU420C.php?gubun=<코드>&ThisDate=<기준일>}</li>
 *   <li>내 예약 내역: {@code PSTU420L.php?gubun=<0~3>}</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class FacilityService {

    // 현황: 기준일부터 10일치 15분 그리드. bcode 는 예약변경 진입 시에만 채워지므로 조회에선 빈 값.
    private static final String AVAILABILITY_PATH =
            "/prof/booking/PSTU420C.php?bcode=&lan=KO&ThisDate=%s&gubun=%d";
    // 내 예약 내역: gubun 0=진행중 / 1=사용완료 / 2=취소 / 3=미사용취소
    private static final String RESERVATION_LIST_PATH = "/prof/booking/PSTU420L.php?gubun=%d";

    private final HisnetClient hisnetClient;
    private final FacilityAvailabilityParser availabilityParser;
    private final ReservationListParser reservationListParser;

    /**
     * 예약 가능한 시설 카탈로그(분류 → 시설)를 반환하는 메서드. 원본 요청 없이 상수로 응답한다.
     */
    public FacilityCatalogResponseDto getCatalog() {
        return FacilityCatalogResponseDto.ofAll();
    }

    /**
     * 한 시설의 예약 현황을 30분 단위로 조회하는 메서드.
     *
     * @param userDetails 인증 주체 (원본 세션 보유)
     * @param facilityId  시설 코드
     * @param from        조회 기준일 (null 이면 오늘). 이 날짜부터 10일치를 반환한다.
     * @return 30분 슬롯 현황 + 쿼터
     */
    public FacilityAvailabilityResponseDto getAvailability(
            final UserDetails userDetails,
            final int facilityId,
            final LocalDate from
    ) {
        final Facility facility = Facility.from(facilityId);
        final LocalDate baseDate = from != null ? from : LocalDate.now();

        final HisnetSession session = resolveSession(userDetails);
        final Document document = hisnetClient.get(
                AVAILABILITY_PATH.formatted(baseDate.format(DateTimeFormatter.ISO_LOCAL_DATE), facility.code()),
                session);

        return availabilityParser.parse(document, baseDate, facility);
    }

    /**
     * 현재 로그인 사용자의 예약 내역을 조회하는 메서드.
     *
     * @param userDetails 인증 주체 (원본 세션 보유)
     * @param type        내역 구분 (진행중/사용완료/취소/미사용취소)
     * @return 예약 내역 목록
     */
    public ReservationListResponseDto getMyReservations(
            final UserDetails userDetails,
            final ReservationListType type
    ) {
        final HisnetSession session = resolveSession(userDetails);
        final Document document = hisnetClient.get(
                RESERVATION_LIST_PATH.formatted(type.gubun()), session);

        return new ReservationListResponseDto(type.slug(), reservationListParser.parse(document));
    }

    /**
     * 인증 주체에서 원본 세션 쿠키를 꺼내는 메서드.
     * 로그인 중계로 채워진 HisnetUserDetails 가 아니면 세션 만료로 취급한다.
     */
    private HisnetSession resolveSession(final UserDetails userDetails) {
        if (!(userDetails instanceof HisnetUserDetails hisnetUserDetails)) {
            throw new CustomException(ExceptionCode.SESSION_EXPIRED);
        }

        return new HisnetSession(
                hisnetUserDetails.getPhpSessionId(),
                hisnetUserDetails.getCookieId());
    }
}
