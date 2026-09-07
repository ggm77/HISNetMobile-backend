package com.seohamin.hisnetmobile.domain.graduation.service;

import com.seohamin.hisnetmobile.domain.graduation.dto.GraduationCriterionDto;
import com.seohamin.hisnetmobile.domain.graduation.dto.GraduationResponseDto;
import com.seohamin.hisnetmobile.global.exception.CustomException;
import com.seohamin.hisnetmobile.global.exception.constants.ExceptionCode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 졸업심사 결과 파싱 회귀 테스트.
 * <p>
 * 실제 구조 축약: 바깥 레이아웃 표가 데이터 표를 감싼다(→ "가장 안쪽 표"). 상단 ▣ 안내문,
 * 학생 정보 표(라벨/값), 판정 표(헤더 {@code 구분|졸업기준(설계)|취득학점(설계)|판정|비고} + 최종 졸업판정 행).
 */
class GraduationParserTest {

    private final GraduationParser parser = new GraduationParser();

    private static final String RESULT_HTML = """
            <html><body>
            <table><tr><td>

              <b>공학인증 졸업심사결과조회(130 졸업학점 기준)</b>
              <font>▣ 2026년도 8월 졸업심사대상자가 아닙니다.</font>
              <div>▣ 2026년 8월 졸업예정자를 위한 졸업심사 기준을 적용하여 심사한 결과 입니다.</div>
              <font>▣ 심사결과에 오류 또는 이의가 있는 경우, 공학교육혁신센터로 연락주시기 바랍니다.</font>

              <table>
                <tr><td>학부</td><td>AI컴퓨터전자공학부</td><td>이름</td><td>서하민</td></tr>
                <tr><td>학번</td><td>22300378</td><td>학적</td><td>복학</td></tr>
                <tr><td>등록학기 수</td><td>4 학기</td><td></td><td></td></tr>
                <tr><td>전공</td><td colspan="3">AI·컴퓨터공학심화(60)</td></tr>
                <tr><td>부전공</td><td colspan="3"></td></tr>
                <tr><td>실무전산/컴퓨터공학 부전공</td><td colspan="3">전산전공</td></tr>
              </table>

              <table>
                <tr><td>구분</td><td>졸업기준(설계)</td><td>취득학점(설계)</td><td>판정</td><td>비고</td></tr>
                <tr><td>신앙및세계관</td><td>9</td><td>6</td><td>불합격</td><td></td></tr>
                <tr><td>전공주제(AI컴퓨터심화)</td><td>60(12)</td><td>18(3)</td><td>불합격</td><td></td></tr>
                <tr><td colspan="5"></td></tr>
                <tr><td>총 취득학점</td><td>※ 비고 참고</td><td>57.5</td><td>불합격</td><td>취득학점 &gt;= 130 + 초과학점</td></tr>
                <tr><td>평점 평균</td><td>2.0 이상</td><td>4.04</td><td>합격</td><td></td></tr>
                <tr><td>공학인증 최종 졸업판정</td><td></td><td></td><td>졸업불가능</td><td></td></tr>
              </table>

            </td></tr></table>
            </body></html>
            """;

    @Test
    void 제목에서_인증구분과_졸업기준학점을_안내문을_뽑는다() {
        final Document document = Jsoup.parse(RESULT_HTML, "https://hisnet.handong.edu");

        final GraduationResponseDto result = parser.parse(document);

        assertThat(result.available()).isTrue();
        assertThat(result.certificationType()).isEqualTo("공학인증");
        assertThat(result.requiredCredits()).isEqualTo(130);
        assertThat(result.notices()).containsExactly(
                "2026년도 8월 졸업심사대상자가 아닙니다.",
                "2026년 8월 졸업예정자를 위한 졸업심사 기준을 적용하여 심사한 결과 입니다.",
                "심사결과에 오류 또는 이의가 있는 경우, 공학교육혁신센터로 연락주시기 바랍니다."
        );
    }

    @Test
    void 학생_정보표의_라벨을_표준키로_매핑한다() {
        final Document document = Jsoup.parse(RESULT_HTML, "https://hisnet.handong.edu");

        final var student = parser.parse(document).student();

        assertThat(student.department()).isEqualTo("AI컴퓨터전자공학부");
        assertThat(student.name()).isEqualTo("서하민");
        assertThat(student.studentNo()).isEqualTo("22300378");
        assertThat(student.academicStatus()).isEqualTo("복학");
        assertThat(student.registeredTerms()).isEqualTo(4);
        assertThat(student.major()).isEqualTo("AI·컴퓨터공학심화(60)");
        assertThat(student.minor()).isNull();
        assertThat(student.subMajor()).isEqualTo("전산전공");
    }

    @Test
    void 판정표를_행별로_읽고_최종_졸업판정은_따로_뺀다() {
        final Document document = Jsoup.parse(RESULT_HTML, "https://hisnet.handong.edu");

        final GraduationResponseDto result = parser.parse(document);

        assertThat(result.criteria())
                .extracting(GraduationCriterionDto::category, GraduationCriterionDto::standard,
                        GraduationCriterionDto::earned, GraduationCriterionDto::verdict,
                        GraduationCriterionDto::note)
                .containsExactly(
                        tuple("신앙및세계관", "9", "6", "불합격", null),
                        tuple("전공주제(AI컴퓨터심화)", "60(12)", "18(3)", "불합격", null),
                        tuple("총 취득학점", "※ 비고 참고", "57.5", "불합격", "취득학점 >= 130 + 초과학점"),
                        tuple("평점 평균", "2.0 이상", "4.04", "합격", null)
                );
        assertThat(result.finalVerdict()).isEqualTo("졸업불가능");
    }

    @Test
    void 진입페이지_결과보기_버튼에서_결과페이지_토큰을_뽑는다() {
        final Document entry = Jsoup.parse(
                "<html><body><input type='button' value='졸업심사 결과보기' "
                        + "onclick=\"view('PGRA123S_gong')\"></body></html>",
                "https://hisnet.handong.edu");

        assertThat(parser.extractResultPageToken(entry)).isEqualTo("PGRA123S_gong");
    }

    @Test
    void 결과보기_버튼이_없으면_토큰은_null이고_unavailable_응답을_만든다() {
        final Document entry = Jsoup.parse(
                "<html><body><div>이미 졸업이 확정되어 조회할 수 없습니다.</div>"
                        + "<font>▣ 문의: 학사팀</font></body></html>",
                "https://hisnet.handong.edu");

        assertThat(parser.extractResultPageToken(entry)).isNull();

        final GraduationResponseDto result = parser.unavailable(entry);
        assertThat(result.available()).isFalse();
        assertThat(result.criteria()).isEmpty();
        assertThat(result.finalVerdict()).isNull();
        assertThat(result.notices()).containsExactly("문의: 학사팀");
    }

    @Test
    void 학생_판정표를_모두_찾지_못하면_GRADUATION_PARSING_FAILED() {
        final Document document = Jsoup.parse(
                "<html><body><p>로그인 후 이용해 주십시오</p></body></html>",
                "https://hisnet.handong.edu");

        assertThatThrownBy(() -> parser.parse(document))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getExceptionCode())
                .isEqualTo(ExceptionCode.GRADUATION_PARSING_FAILED);
    }
}
