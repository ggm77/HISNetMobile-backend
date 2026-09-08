package com.seohamin.hisnetmobile.domain.facility.service;

import com.seohamin.hisnetmobile.domain.facility.constant.Facility;
import com.seohamin.hisnetmobile.global.exception.CustomException;
import com.seohamin.hisnetmobile.global.exception.constants.ExceptionCode;
import org.junit.jupiter.api.Test;
import org.springframework.util.MultiValueMap;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 원본 예약 폼(PSTU420II.php) 바디 변환 테스트 — 30분 입력을 15분 슬롯 필드로 펼치는 규칙.
 */
class ReservationFormBuilderTest {

    private final ReservationFormBuilder builder = new ReservationFormBuilder();

    private static final LocalDate DATE = LocalDate.of(2026, 9, 15);

    @Test
    void 신규신청_폼은_구간을_15분_Y_슬롯으로_펼친다() {
        final MultiValueMap<String, String> form = builder.build(
                Facility.BASKETBALL_1, DATE, LocalTime.of(14, 0), LocalTime.of(16, 0), null);

        assertThat(form.getFirst("gbn")).isEqualTo("83");
        assertThat(form.getFirst("selDate")).isEqualTo("2026-09-15");
        assertThat(form.getFirst("starttime")).isEqualTo("1400");
        assertThat(form.getFirst("endtime")).isEqualTo("1600");
        assertThat(form.getFirst("bcode")).isEmpty();
        assertThat(form.getFirst("chkcnt")).isEqualTo("8");

        assertThat(form.keySet().stream().filter(key -> key.startsWith("f2026")).toList())
                .containsExactlyInAnyOrder(
                        "f202609151400", "f202609151415", "f202609151430", "f202609151445",
                        "f202609151500", "f202609151515", "f202609151530", "f202609151545");
        assertThat(form.get("f202609151400")).containsExactly("Y");
    }

    @Test
    void 예약변경이면_bcode를_채운다() {
        final MultiValueMap<String, String> form = builder.build(
                Facility.BASKETBALL_1, DATE, LocalTime.of(9, 0), LocalTime.of(9, 30), 12345L);

        assertThat(form.getFirst("bcode")).isEqualTo("12345");
        assertThat(form.getFirst("chkcnt")).isEqualTo("2");
    }

    @Test
    void 시작이_30분_단위가_아니면_INVALID_RESERVATION_TIME() {
        assertThatThrownBy(() -> builder.build(
                Facility.BASKETBALL_1, DATE, LocalTime.of(14, 15), LocalTime.of(16, 0), null))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getExceptionCode())
                .isEqualTo(ExceptionCode.INVALID_RESERVATION_TIME);
    }

    @Test
    void 종료가_시작보다_빠르거나_같으면_INVALID_RESERVATION_TIME() {
        assertThatThrownBy(() -> builder.build(
                Facility.BASKETBALL_1, DATE, LocalTime.of(16, 0), LocalTime.of(14, 0), null))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getExceptionCode())
                .isEqualTo(ExceptionCode.INVALID_RESERVATION_TIME);

        assertThatThrownBy(() -> builder.build(
                Facility.BASKETBALL_1, DATE, LocalTime.of(14, 0), LocalTime.of(14, 0), null))
                .isInstanceOf(CustomException.class);
    }
}
