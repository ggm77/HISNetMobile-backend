package com.seohamin.hisnetmobile.domain.facility.constant;

import com.seohamin.hisnetmobile.global.exception.CustomException;
import com.seohamin.hisnetmobile.global.exception.constants.ExceptionCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 시설 카탈로그 상수(운동시설/회의실/편의시설 3분류)의 조회 헬퍼 확인.
 */
class FacilityCatalogTest {

    @Test
    void 전용강의실은_제외하고_세_분류만_존재한다() {
        assertThat(FacilityCategory.values())
                .extracting(FacilityCategory::slug)
                .containsExactly("sports", "meeting", "convenience");
    }

    @Test
    void 시설코드로_시설을_찾는다() {
        assertThat(Facility.from(83)).isEqualTo(Facility.BASKETBALL_1);
        assertThat(Facility.from(98)).isEqualTo(Facility.CODING_TALK);
        assertThat(Facility.from(213)).isEqualTo(Facility.GLOBAL_LOUNGE_C);
    }

    @Test
    void 알수없는_시설코드는_INVALID_FACILITY() {
        assertThatThrownBy(() -> Facility.from(9999))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getExceptionCode())
                .isEqualTo(ExceptionCode.INVALID_FACILITY);
    }

    @Test
    void 분류별_시설은_열거_순서를_지킨다() {
        assertThat(Facility.byCategory(FacilityCategory.CONVENIENCE))
                .containsExactly(
                        Facility.GLOBAL_LOUNGE_A, Facility.GLOBAL_LOUNGE_B, Facility.GLOBAL_LOUNGE_C);
        assertThat(Facility.byCategory(FacilityCategory.SPORTS)).hasSize(8);
        assertThat(Facility.byCategory(FacilityCategory.MEETING)).hasSize(11);
    }

    @Test
    void 예약내역_구분은_slug로_찾고_기본값은_active() {
        assertThat(ReservationListType.from(null)).isEqualTo(ReservationListType.ACTIVE);
        assertThat(ReservationListType.from("cancelled")).isEqualTo(ReservationListType.CANCELLED);
        assertThat(ReservationListType.from("noshow").gubun()).isEqualTo(3);

        assertThatThrownBy(() -> ReservationListType.from("bogus"))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getExceptionCode())
                .isEqualTo(ExceptionCode.INVALID_RESERVATION_STATUS);
    }
}
