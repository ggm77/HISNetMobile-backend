package com.seohamin.hisnetmobile.domain.facility.service;

import com.seohamin.hisnetmobile.domain.facility.dto.ReservationDto;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 내 예약 내역(PSTU420L.php) 파싱 회귀 테스트.
 * <p>
 * 진행중 탭은 6칸(+바로가기 링크에서 예약 코드), 이력 탭(사용완료/취소/미사용취소)은 4칸이라 코드가 없다.
 * 라벨엔 자간용 공백("제 목"), 바로가기 칸의 버튼은 중첩 표, 데이터 표는 바깥 표에 중첩돼 있다.
 */
class ReservationListParserTest {

    private final ReservationListParser parser = new ReservationListParser();

    /** 진행중 탭 행: No | 제목 | 장소 | 일시 | 예약취소가능기간 | 바로가기(중첩 표) */
    private static String activeRow(final String no, final String title, final String place,
                                   final String dateTime, final String cancelable, final long bcode) {
        return "<tr>"
                + "<td>" + no + "</td>"
                + "<td>" + title + "</td>"
                + "<td>" + place + "</td>"
                + "<td>" + dateTime + "</td>"
                + "<td>" + cancelable + "</td>"
                + "<td><table><tr>"
                + "<td><a href=\"javascript:OpenWin('PSTU420P.php?bcode=" + bcode + "','780','560')\">참석자등록</a></td>"
                + "<td><a href=\"PSTU420M.php?cname=%ED%9A%8C&fcode=98&bcode=" + bcode + "\">예약변경</a></td>"
                + "<td><a href=\"javascript:BookinCancel(" + bcode + ");\">예약취소</a></td>"
                + "</tr></table></td></tr>";
    }

    /** 이력 탭 행: No | 제목 | 장소 | 일시 (링크 없음) */
    private static String historyRow(final String no, final String title, final String place,
                                     final String dateTime) {
        return "<tr><td>" + no + "</td><td>" + title + "</td><td>" + place + "</td><td>" + dateTime + "</td></tr>";
    }

    private static Document wrap(final String headerAndRows) {
        return Jsoup.parse("<html><body><table><tr><td><table>" + headerAndRows + "</table></td></tr></table></body></html>",
                "https://hisnet.handong.edu");
    }

    private static final String ACTIVE_HTML =
            "<tr><td>No</td><td>제 목</td><td>장 소</td><td>일 시</td><td>예약취소가능기간</td><td>바로가기</td></tr>"
            + activeRow("10", "", "Coding Talk", "2026.05.30(토)<br>15:00 ~ 18:00", "~ 2026.05.30 14:00", 755844L)
            + activeRow("9", "", "농구장4(도서관쪽)", "2026.05.06(수)<br>16:00 ~ 18:00", "~ 2026.05.06 15:00", 751153L)
            + activeRow("8", "정기 모임", "상상랩06", "2026.04.01(수)<br>9:00 ~ 11:00", "~ 2026.04.01 08:00", 700001L)
            + "<tr><td>1</td></tr>";

    private static final String HISTORY_HTML =
            "<tr><td>No</td><td>제 목</td><td>장 소</td><td>일 시</td></tr>"
            + historyRow("2", "", "상상랩01", "2023.12.05(화)<br>21:00 ~ 24:00")
            + historyRow("1", "", "상상랩06", "2023.09.11(월)<br>23:00 ~ 24:00");

    @Test
    void 진행중_탭_6칸_행에서_예약코드_장소_일시_취소가능시각을_뽑는다() {
        final List<ReservationDto> result = parser.parse(wrap(ACTIVE_HTML));

        assertThat(result)
                .extracting(ReservationDto::bookingCode, ReservationDto::title, ReservationDto::facilityName,
                        ReservationDto::date, ReservationDto::startTime, ReservationDto::endTime,
                        ReservationDto::cancelableUntil)
                .containsExactly(
                        tuple(755844L, null, "Coding Talk", "2026-05-30", "15:00", "18:00", "2026-05-30 14:00"),
                        tuple(751153L, null, "농구장4(도서관쪽)", "2026-05-06", "16:00", "18:00", "2026-05-06 15:00"),
                        tuple(700001L, "정기 모임", "상상랩06", "2026-04-01", "09:00", "11:00", "2026-04-01 08:00"));
    }

    @Test
    void 이력_탭_4칸_행은_코드와_취소가능시각이_null() {
        final List<ReservationDto> result = parser.parse(wrap(HISTORY_HTML));

        assertThat(result)
                .extracting(ReservationDto::bookingCode, ReservationDto::facilityName,
                        ReservationDto::date, ReservationDto::startTime, ReservationDto::endTime,
                        ReservationDto::cancelableUntil)
                .containsExactly(
                        tuple(null, "상상랩01", "2023-12-05", "21:00", "24:00", null),
                        tuple(null, "상상랩06", "2023-09-11", "23:00", "24:00", null));
    }

    @Test
    void 번호없는_페이저_행은_건너뛴다() {
        assertThat(parser.parse(wrap(ACTIVE_HTML))).hasSize(3);
    }

    @Test
    void 예약내역_표를_찾지_못하면_빈_리스트() {
        final Document document = Jsoup.parse(
                "<html><body><table><tr><td>딴 표</td></tr></table></body></html>",
                "https://hisnet.handong.edu");

        assertThat(parser.parse(document)).isEmpty();
    }
}
