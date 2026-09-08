package com.seohamin.hisnetmobile.domain.facility.service;

import com.seohamin.hisnetmobile.domain.facility.constant.Facility;
import com.seohamin.hisnetmobile.domain.facility.constant.SlotStatus;
import com.seohamin.hisnetmobile.domain.facility.dto.FacilityAvailabilityResponseDto;
import com.seohamin.hisnetmobile.domain.facility.dto.FacilityDayAvailabilityDto;
import com.seohamin.hisnetmobile.domain.facility.dto.FacilitySlotDto;
import com.seohamin.hisnetmobile.global.exception.CustomException;
import com.seohamin.hisnetmobile.global.exception.constants.ExceptionCode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 시설 현황(PSTU420C.php) 파싱 회귀 테스트.
 * <p>
 * 15분 셀 두 개를 30분으로 병합하는 규칙(둘 다 가능이어야 가능, 내 예약 &gt; 타인 예약 &gt; 불가)과
 * 쿼터 문구·운영시간 추출을 확인한다.
 */
class FacilityAvailabilityParserTest {

    private final FacilityAvailabilityParser parser = new FacilityAvailabilityParser();

    private static final LocalDate FROM = LocalDate.of(2026, 9, 15);

    private static String yes(final String date, final String start, final String end) {
        return "<td class=\"bookingactYes\" onclick=\"selectDate(this,'" + date + "','" + start + "','" + end + "')\"></td>";
    }

    private static String other(final String date, final String start, final String end) {
        return "<td class=\"bookingactNo\" onclick=\"ViewInfo('" + date + "','" + start + "','" + end + "','12345')\"></td>";
    }

    private static String mine(final String date, final String start, final String end) {
        return "<td class=\"bookingactNoMy\" onclick=\"ViewInfo('" + date + "','" + start + "','" + end + "','98765')\"></td>";
    }

    // 2026-09-15 하루:
    //  09:00~09:15 가능 / 09:15~09:30 가능              -> 09:00 버킷 AVAILABLE
    //  09:30~09:45 가능 / 09:45~10:00 (셀 없음)          -> 09:30 버킷 UNAVAILABLE
    //  10:00~10:15 타인 / 10:15~10:30 가능              -> 10:00 버킷 RESERVED_OTHER
    //  10:30~10:45 내예약 / 10:45~11:00 가능            -> 10:30 버킷 RESERVED_MINE
    private static final String GRID_HTML = """
            <html><body>
            <div>2026년 09월 15일 (Tue) [테니스장 A]
              총 예약가능시간 일일 예약가능시간 총예약가능시간 초기화 예정일
              잔여 8시간 / 8시간 잔여 2시간 / 2시간 2026-09-20 (Sun)</div>
            <table>
              <tr><td>Time</td><td>09-15 화요일</td></tr>
              <tr><td>09:00~10:00</td>
            """
            + yes("2026-09-15", "0900", "0915")
            + yes("2026-09-15", "0915", "0930")
            + yes("2026-09-15", "0930", "0945")
            + other("2026-09-15", "1000", "1015")
            + yes("2026-09-15", "1015", "1030")
            + mine("2026-09-15", "1030", "1045")
            + yes("2026-09-15", "1045", "1100")
            + """
              </tr>
            </table>
            </body></html>
            """;

    @Test
    void 인접한_15분_두칸을_30분_슬롯으로_병합한다() {
        final Document document = Jsoup.parse(GRID_HTML, "https://hisnet.handong.edu");

        final FacilityAvailabilityResponseDto result = parser.parse(document, FROM, Facility.TENNIS_A);

        assertThat(result.slotMinutes()).isEqualTo(30);
        assertThat(result.facilityId()).isEqualTo(Facility.TENNIS_A.code());
        assertThat(result.facilityName()).isEqualTo("테니스장 A");
        assertThat(result.operatingStart()).isEqualTo("09:00");
        assertThat(result.operatingEnd()).isEqualTo("11:00");
        assertThat(result.rangeFrom()).isEqualTo("2026-09-15");
        assertThat(result.rangeTo()).isEqualTo("2026-09-24");
        assertThat(result.days()).hasSize(10);

        final FacilityDayAvailabilityDto day = result.days().get(0);
        assertThat(day.date()).isEqualTo("2026-09-15");
        assertThat(day.weekday()).isEqualTo("TUE");
        assertThat(day.slots())
                .extracting(FacilitySlotDto::start, FacilitySlotDto::end, FacilitySlotDto::status)
                .containsExactly(
                        tuple("09:00", "09:30", SlotStatus.AVAILABLE),
                        tuple("09:30", "10:00", SlotStatus.UNAVAILABLE),
                        tuple("10:00", "10:30", SlotStatus.RESERVED_OTHER),
                        tuple("10:30", "11:00", SlotStatus.RESERVED_MINE));
    }

    @Test
    void 쿼터_문구를_분으로_환산하고_초기화일을_뽑는다() {
        final Document document = Jsoup.parse(GRID_HTML, "https://hisnet.handong.edu");

        final FacilityAvailabilityResponseDto result = parser.parse(document, FROM, Facility.TENNIS_A);

        assertThat(result.quota()).isNotNull();
        assertThat(result.quota().totalRemainingMinutes()).isEqualTo(480);
        assertThat(result.quota().totalLimitMinutes()).isEqualTo(480);
        assertThat(result.quota().dailyRemainingMinutes()).isEqualTo(120);
        assertThat(result.quota().dailyLimitMinutes()).isEqualTo(120);
        assertThat(result.quota().resetDate()).isEqualTo("2026-09-20");
    }

    @Test
    void 가변길이_구간을_15분으로_펼쳐_30분_슬롯을_채운다() {
        // 09:00~11:00 가능(2시간 한 칸), 11:00~13:00 타인 예약(2시간 한 칸) — 글로벌라운지·상상랩류 구조
        final String html = """
                <html><body>
                <table><tr><td>Time</td><td>09-15</td></tr><tr><td>x</td>
                """
                + yes("2026-09-15", "0900", "1100")
                + other("2026-09-15", "1100", "1300")
                + """
                </tr></table></body></html>
                """;
        final Document document = Jsoup.parse(html, "https://hisnet.handong.edu");

        final FacilityAvailabilityResponseDto result = parser.parse(document, FROM, Facility.GLOBAL_LOUNGE_A);

        assertThat(result.operatingStart()).isEqualTo("09:00");
        assertThat(result.operatingEnd()).isEqualTo("13:00");
        assertThat(result.days().get(0).slots())
                .extracting(FacilitySlotDto::start, FacilitySlotDto::status)
                .containsExactly(
                        tuple("09:00", SlotStatus.AVAILABLE),
                        tuple("09:30", SlotStatus.AVAILABLE),
                        tuple("10:00", SlotStatus.AVAILABLE),
                        tuple("10:30", SlotStatus.AVAILABLE),
                        tuple("11:00", SlotStatus.RESERVED_OTHER),
                        tuple("11:30", SlotStatus.RESERVED_OTHER),
                        tuple("12:00", SlotStatus.RESERVED_OTHER),
                        tuple("12:30", SlotStatus.RESERVED_OTHER));
    }

    @Test
    void 자정_2400_종료_구간도_처리한다() {
        final String html = """
                <html><body><table><tr><td>Time</td></tr><tr><td>x</td>
                """
                + yes("2026-09-15", "2300", "2400")
                + """
                </tr></table></body></html>
                """;
        final Document document = Jsoup.parse(html, "https://hisnet.handong.edu");

        final FacilityAvailabilityResponseDto result = parser.parse(document, FROM, Facility.CODING_TALK);

        assertThat(result.operatingStart()).isEqualTo("23:00");
        assertThat(result.operatingEnd()).isEqualTo("24:00");
        assertThat(result.days().get(0).slots())
                .extracting(FacilitySlotDto::start, FacilitySlotDto::end, FacilitySlotDto::status)
                .containsExactly(
                        tuple("23:00", "23:30", SlotStatus.AVAILABLE),
                        tuple("23:30", "24:00", SlotStatus.AVAILABLE));
    }

    @Test
    void 슬롯없는_날은_운영시간만큼_UNAVAILABLE_버킷을_채운다() {
        final Document document = Jsoup.parse(GRID_HTML, "https://hisnet.handong.edu");

        final FacilityAvailabilityResponseDto result = parser.parse(document, FROM, Facility.TENNIS_A);

        assertThat(result.days().get(1).slots())
                .isNotEmpty()
                .allMatch(slot -> slot.status() == SlotStatus.UNAVAILABLE);
    }

    @Test
    void 쿼터_문구가_없어도_현황조회는_성공한다() {
        final String noQuota = """
                <html><body>
                <table><tr><td>Time</td><td>09-15</td></tr>
                <tr><td>09:00</td>
                """
                + yes("2026-09-15", "0900", "0915")
                + yes("2026-09-15", "0915", "0930")
                + """
                </tr></table></body></html>
                """;
        final Document document = Jsoup.parse(noQuota, "https://hisnet.handong.edu");

        final FacilityAvailabilityResponseDto result = parser.parse(document, FROM, Facility.TENNIS_A);

        assertThat(result.quota()).isNull();
        assertThat(result.days().get(0).slots())
                .extracting(FacilitySlotDto::status)
                .containsExactly(SlotStatus.AVAILABLE);
    }

    @Test
    void 슬롯도_쿼터문구도_없으면_FACILITY_PARSING_FAILED() {
        final Document document = Jsoup.parse(
                "<html><body><p>로그인 후 이용해 주십시오</p></body></html>",
                "https://hisnet.handong.edu");

        assertThatThrownBy(() -> parser.parse(document, FROM, Facility.TENNIS_A))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getExceptionCode())
                .isEqualTo(ExceptionCode.FACILITY_PARSING_FAILED);
    }
}
