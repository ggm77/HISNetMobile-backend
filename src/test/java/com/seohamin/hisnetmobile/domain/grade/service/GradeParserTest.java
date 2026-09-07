package com.seohamin.hisnetmobile.domain.grade.service;

import com.seohamin.hisnetmobile.domain.grade.dto.CourseGradeDto;
import com.seohamin.hisnetmobile.domain.grade.dto.GradeResponseDto;
import com.seohamin.hisnetmobile.domain.grade.dto.SemesterGradeDto;
import com.seohamin.hisnetmobile.global.exception.CustomException;
import com.seohamin.hisnetmobile.global.exception.constants.ExceptionCode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

/**
 * 전체성적조회(HREC110M.php) 파싱 회귀 테스트.
 * <p>
 * 실제 구조 축약: 바깥 레이아웃 표가 데이터 표들을 통째로 감싼다(→ "가장 안쪽 표"를 골라야 함).
 * 누적 성적 표(라벨/값 짝), 학기별 요약 표, 그리고 학기마다 숨은 {@code div#div_YYYYT} 안의 상세 표.
 */
class GradeParserTest {

    private final GradeParser parser = new GradeParser();

    private static String courseRow(final String code, final String name, final String type,
                                    final String credits, final String grade, final String points,
                                    final String retake) {
        return "<tr>"
                + "<td><font>" + code + "</font></td>"
                + "<td><font>" + name + "</font></td>"
                + "<td><font>" + type + "</font></td>"
                + "<td><font>" + credits + "</font></td>"
                + "<td><font>" + grade + "</font></td>"
                + "<td><font>" + points + "</font></td>"
                + "<td>" + retake + "</td>"
                + "<td></td>"
                + "</tr>";
    }

    private static final String GRADE_HTML = """
            <html><body>
            <table><tr><td>
              <table><tr><td>메뉴 성적 전체성적조회 학사년도 평점평균 누적 성적 등</td></tr></table>

              <table>
                <tr><td>학사년도</td><td>신청학점</td><td>취득학점</td><td>평점평균</td><td>비고</td><td>학기별성적</td></tr>
                <tr><td>2026-1</td><td>19.5</td><td>19.5</td><td>4.21</td><td></td><td><a href="javascript:div_view('20261')">선택</a></td></tr>
                <tr><td>2023-1</td><td>20.5</td><td>20.5</td><td>4.07</td><td>재수강</td><td><a href="javascript:div_view('20231')">선택</a></td></tr>
              </table>

              <table>
                <tr><td colspan="8">총 누적 성적</td></tr>
                <tr><td>신청학점</td><td>57.5</td><td>취득학점</td><td>57.5</td><td>실무필수</td><td>0</td><td>실무선택</td><td>0</td></tr>
                <tr><td>교양필수</td><td>2.5</td><td>교양선택</td><td>5</td><td>전공필수</td><td>6</td><td>전공선택</td><td>12</td></tr>
                <tr><td>전공선택필수</td><td>0</td><td>교양선택필수</td><td>32</td><td>공통선택</td><td>0</td><td>평점계</td><td>182</td></tr>
                <tr><td>평점평균</td><td>4.04</td><td>환산점수</td><td>95.4</td><td>전공평점평균</td><td>4.33</td><td>PF이수학점</td><td>12.5</td></tr>
              </table>

              <div id="div_20261" style="display:none"><table>
                <tr><td colspan="8">2026-1 학기 성적</td></tr>
                <tr><td>과목코드</td><td>과목명</td><td>이수구분</td><td>학점</td><td>성적</td><td>평점</td><td>재이수</td><td>비고</td></tr>
            """
            + courseRow("ECE20025", "프로그래밍 스튜디오", "전공선택", "2", "A+", "4.5", "")
            + courseRow("GEK20008", "공동체리더십훈련3", "교양필수", ".5", "PD", "0", "")
            + courseRow("ITP20001", "Data Structures", "전공필수", "3", "A+", "4.5", "")
            + """
              </table></div>

              <div id="div_20231" style="display:none"><table>
                <tr><td colspan="8">2023-1 학기 성적</td></tr>
                <tr><td>과목코드</td><td>과목명</td><td>이수구분</td><td>학점</td><td>성적</td><td>평점</td><td>재이수</td><td>비고</td></tr>
            """
            + courseRow("GEK10001", "채플(한국어) 1", "교양필수", "0", "P", "0", "")
            + courseRow("MTH10001", "미적분학", "교양선택필수", "3", "C+", "2.5", "재이수")
            + """
              </table></div>

            </td></tr></table>
            </body></html>
            """;

    @Test
    void 총_누적_성적의_주요값과_이수구분별_학점을_뽑는다() {
        final Document document = Jsoup.parse(GRADE_HTML, "https://hisnet.handong.edu");

        final GradeResponseDto result = parser.parse(document);

        assertThat(result.summary().gpa()).isEqualTo(4.04);
        assertThat(result.summary().majorGpa()).isEqualTo(4.33);
        assertThat(result.summary().requestedCredits()).isEqualTo(57.5);
        assertThat(result.summary().earnedCredits()).isEqualTo(57.5);
        assertThat(result.summary().conversionScore()).isEqualTo(95.4);
        assertThat(result.summary().totalGradePoints()).isEqualTo(182.0);
        assertThat(result.summary().pfCredits()).isEqualTo(12.5);

        // 이수구분별 학점: 요약용 라벨(신청학점/평점평균/…)은 빠지고 카테고리만
        assertThat(result.summary().creditsByType())
                .containsEntry("교양필수", 2.5)
                .containsEntry("전공선택", 12.0)
                .containsEntry("교양선택필수", 32.0)
                .doesNotContainKey("평점평균")
                .doesNotContainKey("신청학점");

        assertThat(result.summary().raw()).containsEntry("신청학점", "57.5");
    }

    @Test
    void 학기별_요약과_상세_과목을_결합하고_요약_표기순서를_지킨다() {
        final Document document = Jsoup.parse(GRADE_HTML, "https://hisnet.handong.edu");

        final GradeResponseDto result = parser.parse(document);

        assertThat(result.semesters())
                .extracting(SemesterGradeDto::year, SemesterGradeDto::term,
                        SemesterGradeDto::requestedCredits, SemesterGradeDto::gpa, SemesterGradeDto::note)
                .containsExactly(
                        tuple(2026, 1, 19.5, 4.21, null),
                        tuple(2023, 1, 20.5, 4.07, "재수강")
                );

        final SemesterGradeDto fall2026 = result.semesters().get(0);
        assertThat(fall2026.courses())
                .extracting(CourseGradeDto::code, CourseGradeDto::name, CourseGradeDto::type,
                        CourseGradeDto::credits, CourseGradeDto::grade, CourseGradeDto::gradePoints,
                        CourseGradeDto::retake)
                .containsExactly(
                        tuple("ECE20025", "프로그래밍 스튜디오", "전공선택", 2.0, "A+", 4.5, false),
                        tuple("GEK20008", "공동체리더십훈련3", "교양필수", 0.5, "PD", 0.0, false),
                        tuple("ITP20001", "Data Structures", "전공필수", 3.0, "A+", 4.5, false)
                );
    }

    @Test
    void 재이수_칸이_비어있지_않으면_retake_true() {
        final Document document = Jsoup.parse(GRADE_HTML, "https://hisnet.handong.edu");

        final CourseGradeDto retaken = parser.parse(document).semesters().get(1).courses().get(1);

        assertThat(retaken.code()).isEqualTo("MTH10001");
        assertThat(retaken.retake()).isTrue();
        assertThat(retaken.gradePoints()).isCloseTo(2.5, within(1e-9));
    }

    @Test
    void 누적과_요약_표를_모두_찾지_못하면_GRADE_PARSING_FAILED() {
        final Document document = Jsoup.parse(
                "<html><body><p>로그인 후 이용해 주십시오</p></body></html>",
                "https://hisnet.handong.edu");

        assertThatThrownBy(() -> parser.parse(document))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getExceptionCode())
                .isEqualTo(ExceptionCode.GRADE_PARSING_FAILED);
    }
}
