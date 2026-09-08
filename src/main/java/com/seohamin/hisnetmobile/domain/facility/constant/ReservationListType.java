package com.seohamin.hisnetmobile.domain.facility.constant;

import com.seohamin.hisnetmobile.global.exception.CustomException;
import com.seohamin.hisnetmobile.global.exception.constants.ExceptionCode;

import java.util.Locale;

/**
 * 예약/초대내역조회(PSTU420L.php)의 예약내역 하위 탭.
 * <p>
 * 원본은 {@code PSTU420L.php?gubun=<n>} 으로 탭을 구분한다(2026-09-08 실측):
 * {@code 0}=예약신청내역(진행중), {@code 1}=사용완료내역, {@code 2}=예약취소내역,
 * {@code 3}=미사용취소내역. 표 구조는 네 탭이 동일하다.
 */
public enum ReservationListType {

    ACTIVE(0, "active"),
    COMPLETED(1, "completed"),
    CANCELLED(2, "cancelled"),
    NO_SHOW(3, "noshow");

    private final int gubun;
    private final String slug;

    ReservationListType(final int gubun, final String slug) {
        this.gubun = gubun;
        this.slug = slug;
    }

    public int gubun() {
        return gubun;
    }

    public String slug() {
        return slug;
    }

    /**
     * slug(대소문자 무시)로 탭을 찾는다. null·빈 값이면 {@link #ACTIVE}.
     * @throws CustomException 알 수 없는 slug 면 {@link ExceptionCode#INVALID_RESERVATION_STATUS}
     */
    public static ReservationListType from(final String slug) {
        if (slug == null || slug.isBlank()) {
            return ACTIVE;
        }
        final String normalized = slug.trim().toLowerCase(Locale.ROOT);
        for (final ReservationListType type : values()) {
            if (type.slug.equals(normalized)) {
                return type;
            }
        }
        throw new CustomException(ExceptionCode.INVALID_RESERVATION_STATUS);
    }
}
