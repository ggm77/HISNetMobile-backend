package com.seohamin.hisnetmobile.domain.facility.constant;

import com.seohamin.hisnetmobile.global.exception.CustomException;
import com.seohamin.hisnetmobile.global.exception.constants.ExceptionCode;

import java.util.Locale;

/**
 * 시설예약신청(PSTU420M.php)의 상단 탭 분류.
 * <p>
 * 원본은 {@code PSTU420M.php?gubun=<n>} 으로 탭을 전환한다. 전용강의실(gubun=4)은
 * 이 서비스 범위에서 제외하고 세 분류만 노출한다.
 *
 * @see Facility
 */
public enum FacilityCategory {

    SPORTS(1, "sports", "운동시설"),
    MEETING(2, "meeting", "회의실"),
    CONVENIENCE(3, "convenience", "편의시설");

    private final int gubun;
    private final String slug;
    private final String displayName;

    FacilityCategory(final int gubun, final String slug, final String displayName) {
        this.gubun = gubun;
        this.slug = slug;
        this.displayName = displayName;
    }

    /** 원본 {@code PSTU420M.php?gubun=} 값 */
    public int gubun() {
        return gubun;
    }

    /** API 경로/파라미터에서 쓰는 소문자 식별자 */
    public String slug() {
        return slug;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * slug(대소문자 무시)로 분류를 찾는다.
     * @throws CustomException 매칭되는 분류가 없으면 {@link ExceptionCode#INVALID_FACILITY_CATEGORY}
     */
    public static FacilityCategory from(final String slug) {
        if (slug != null) {
            final String normalized = slug.trim().toLowerCase(Locale.ROOT);
            for (final FacilityCategory category : values()) {
                if (category.slug.equals(normalized)) {
                    return category;
                }
            }
        }
        throw new CustomException(ExceptionCode.INVALID_FACILITY_CATEGORY);
    }
}
