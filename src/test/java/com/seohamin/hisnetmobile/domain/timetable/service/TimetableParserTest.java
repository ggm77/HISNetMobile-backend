package com.seohamin.hisnetmobile.domain.timetable.service;

import com.seohamin.hisnetmobile.domain.timetable.dto.TimetableCourseDto;
import com.seohamin.hisnetmobile.domain.timetable.dto.TimetableResponseDto;
import com.seohamin.hisnetmobile.domain.timetable.dto.TimetableSlotDto;
import com.seohamin.hisnetmobile.global.exception.CustomException;
import com.seohamin.hisnetmobile.global.exception.constants.ExceptionCode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 내시간표조회(HLES110M.php) 파싱 회귀 테스트.
 * <p>
 * 실제 구조 축약: id/class 없는 표, 헤더 {@code 시간|월~토}, {@code N교시} 행. 과목 칸은
 * {@code <a href="...&CIS_GWAMOK=코드"><font>과목명(분반)</font><br><font>교원</font><br><font>강의실</font></a>}.
 * 여러 교시에 걸치는 과목은 rowspan 없이 교시 행마다 반복된다.
 */
class TimetableParserTest {

    private final TimetableParser parser = new TimetableParser();

    private static String cell(final String code, final String nameSection,
                               final String professor, final String room) {
        return "<td align=\"center\"><a href=\"/cis/list.php?Board=CIS0001&amp;CIS_GWAMOK=" + code + "\">"
                + "<font color=\"#666666\">" + nameSection + "</font><br>"
                + "<font color=\"#9966FF\">" + professor + "</font><br>"
                + "<font color=\"#666666\">" + room + "</font></a></td>";
    }

    private static final String OPEN = cell("OSS0000000000001", "오픈소스 스튜디오(01)", "장소연", "NTH 414");
    private static final String SYS = cell("SYS0000000000001", "시스템프로그래밍(01)", "이강", "NTH 417");
    private static final String DISCRETE = cell("DIS0000000000001", "Discrete Mathematics(01)", "최희열", "NTH 313");
    private static final String CHAPEL = cell("CHA0000000000001", "채플(한국어) 4(01)", "노규석외 3명", "HCA 효암본관");
    private static final String EMPTY = "<td align=\"center\">&nbsp;</td>";

    private static String row(final String period, final String mon, final String tue, final String wed,
                              final String thu, final String fri, final String sat) {
        return "<tr><td>" + period + "</td>" + mon + tue + wed + thu + fri + sat + "</tr>";
    }

    /** 바깥 레이아웃 표로 감싸서, 헤더 기반 표 탐지가 엉뚱한 표를 잡지 않는지도 함께 본다. */
    private static final String TIMETABLE_HTML = """
            <html><body>
            <table><tr><td>
              <table><tr><td>메뉴 시간 안내 월요일 등</td></tr></table>
              <table>
                <tr><td>시간</td><td>월</td><td>화</td><td>수</td><td>목</td><td>금</td><td>토</td></tr>
            """
            + row("1교시", OPEN, EMPTY, EMPTY, OPEN, EMPTY, EMPTY)
            + row("2교시", EMPTY, EMPTY, EMPTY, OPEN, EMPTY, EMPTY)
            + row("3교시", SYS, EMPTY, EMPTY, SYS, EMPTY, EMPTY)
            + row("4교시", DISCRETE, EMPTY, CHAPEL, EMPTY, EMPTY, EMPTY)
            + row("5교시", DISCRETE, EMPTY, CHAPEL, EMPTY, EMPTY, EMPTY)
            + row("6교시", EMPTY, EMPTY, CHAPEL, EMPTY, EMPTY, EMPTY)
            + row("7교시", EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY)
            + """
              </table>
            </td></tr></table>
            </body></html>
            """;

    @Test
    void 과목_단위로_격자_등장순서대로_모은다() {
        final Document document = Jsoup.parse(TIMETABLE_HTML, "https://hisnet.handong.edu");

        final TimetableResponseDto result = parser.parse(document);

        assertThat(result.courses())
                .extracting(TimetableCourseDto::name, TimetableCourseDto::section,
                        TimetableCourseDto::courseCode, TimetableCourseDto::professor)
                .containsExactly(
                        tuple("오픈소스 스튜디오", "01", "OSS0000000000001", "장소연"),
                        tuple("시스템프로그래밍", "01", "SYS0000000000001", "이강"),
                        tuple("Discrete Mathematics", "01", "DIS0000000000001", "최희열"),
                        tuple("채플(한국어) 4", "01", "CHA0000000000001", "노규석외 3명")
                );
    }

    @Test
    void 같은_요일의_연속_교시는_한_슬롯으로_합친다() {
        final Document document = Jsoup.parse(TIMETABLE_HTML, "https://hisnet.handong.edu");

        final TimetableResponseDto result = parser.parse(document);

        final TimetableSlotDto discrete = result.courses().get(2).slots().get(0);
        assertThat(result.courses().get(2).slots()).hasSize(1);
        assertThat(discrete).isEqualTo(new TimetableSlotDto("월", 4, 5, "NTH 313"));

        final TimetableSlotDto chapel = result.courses().get(3).slots().get(0);
        assertThat(chapel).isEqualTo(new TimetableSlotDto("수", 4, 6, "HCA 효암본관"));
    }

    @Test
    void 요일이_다르면_슬롯을_나누고_요일순서대로_준다() {
        final Document document = Jsoup.parse(TIMETABLE_HTML, "https://hisnet.handong.edu");

        final TimetableCourseDto open = parser.parse(document).courses().get(0);

        assertThat(open.slots())
                .extracting(TimetableSlotDto::day, TimetableSlotDto::startPeriod,
                        TimetableSlotDto::endPeriod, TimetableSlotDto::room)
                .containsExactly(
                        tuple("월", 1, 1, "NTH 414"),
                        tuple("목", 1, 2, "NTH 414")
                );
    }

    @Test
    void 수강과목이_없으면_빈_리스트를_준다() {
        final String emptyHtml = "<html><body><table>"
                + "<tr><td>시간</td><td>월</td><td>화</td><td>수</td><td>목</td><td>금</td><td>토</td></tr>"
                + row("1교시", EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY)
                + row("2교시", EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY)
                + "</table></body></html>";
        final Document document = Jsoup.parse(emptyHtml, "https://hisnet.handong.edu");

        assertThat(parser.parse(document).courses()).isEmpty();
    }

    @Test
    void 시간표_표를_찾지_못하면_TIMETABLE_PARSING_FAILED() {
        final Document document = Jsoup.parse(
                "<html><body><table><tr><td>로그인 후 이용</td></tr></table></body></html>",
                "https://hisnet.handong.edu");

        assertThatThrownBy(() -> parser.parse(document))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getExceptionCode())
                .isEqualTo(ExceptionCode.TIMETABLE_PARSING_FAILED);
    }
}
