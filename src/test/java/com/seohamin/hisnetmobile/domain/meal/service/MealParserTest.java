package com.seohamin.hisnetmobile.domain.meal.service;

import com.seohamin.hisnetmobile.domain.meal.dto.CafeteriaMealDto;
import com.seohamin.hisnetmobile.domain.meal.dto.MealCornerDto;
import com.seohamin.hisnetmobile.domain.meal.dto.MealResponseDto;
import com.seohamin.hisnetmobile.domain.meal.dto.MealSlotDto;
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
 * 로그인 페이지 식단 위젯 파싱 회귀 테스트.
 * <p>
 * 실제 {@code /login/login.php} 구조를 축약: 위젯 루트 {@code #tr_box_11}, 식당 패널 {@code #tr_box11_1~4},
 * 학생식당은 코너 탭({@code #tKOREA} 등) + 본문 행({@code #tr_box11_001} 등)으로 나뉜다.
 * 메뉴 전체는 셀 또는 행의 {@code title} 속성(개행 구분)에 있고 화면 텍스트는 "..." 로 잘린다.
 */
class MealParserTest {

    private final MealParser parser = new MealParser();

    private static final String LOGIN_HTML = """
            <html><head><title>한동대학교</title></head><body>
            <a href="javascript:printDungNotice();"><img src="/2012_images/intro/dung_print_new8.png?time='2026-09-07'" /></a>
            <table><tr id="tr_box_11"><td><table>

              <tr align="center" id="tr_box11_1"><td>
                <table><tr>
                  <td onClick="setFood(1)"><div id="tKOREA" class="cls_td_food_title">든든한동</div></td>
                  <td onClick="setFood(2)"><div id="tFLY" class="cls_td_food_title">H:plate</div></td>
                  <td onClick="setFood(3)"><div id="tNOODLE" class="cls_td_food_title">Asian Market</div></td>
                  <td onClick="setFood(5)"><div id="tGRACE" class="cls_td_food_title">Han's Deli</div></td>
                  <td onClick="setFood(6)"><div id="tMIX" class="cls_td_food_title">따스한동</div></td>
                </tr></table>
                <table>
                  <tr id="tr_box11_001"><td class="cls_td_food_text" colspan="3"><div><table>
                    <tr height="18">
                      <td width="100" class="cls_td_food_title">아침</td>
                      <td width="100" class="cls_td_food_title">점심</td>
                      <td width="100" class="cls_td_food_title">저녁</td>
                    </tr>
                    <tr><td colspan="3" style="height:3px;"></td></tr>
                    <tr align="center">
                      <td valign="top" width="100" align="left" title="-원산지:메뉴게시판 참조-
            소고기미역죽
            야채커틀렛">-원산지:메뉴게..<br>소고기미역죽<br>야채커틀렛<br><br></td>
                      <td valign="top" width="100" align="left" title="-원산지:메뉴게시판 참조-
            청양마요미트조림
            쌀밥">-원산지:메뉴게..<br>청양마요미트조..<br>쌀밥<br><br></td>
                      <td valign="top" width="100" align="left" title="-원산지:메뉴게시판 참조-
            오징어순대두루치기
            쌀밥">-원산지:메뉴게..<br>오징어순대두루..<br>쌀밥<br><br></td>
                    </tr>
                  </table></div></td></tr>

                  <tr id="tr_box11_002" bgcolor="#FFFFFF"><td class="cls_td_food_text" colspan="3"><div><table>
                    <tr align="center" title="-원산지:메뉴게시판 참조- -  -
            등심돈까스 -  -
            파파치킨까스(대파/양파채 파닭소스) -  -
            달고마떡볶이&모듬튀김 -  -
            ">
                      <td valign="top" width="180" align="left">-원산지:메뉴게..<br>등심돈까스<br>파파치킨까스(대파/양파채..<br>달고마떡볶이&모듬튀김<br><br></td>
                      <td valign="top" width="60" align="right"><br><br></td>
                      <td valign="top" width="60" align="right"><br><br></td>
                    </tr>
                  </table></div></td></tr>
                </table>
              </td></tr>

              <tr align="center" id="tr_box11_2"><td>
                <table><tr><td class="cls_td_food_text"><div><table>
                  <tr>
                    <td width="100" class="cls_td_food_title">아침<br>(7:30am~9:30am)</td>
                    <td width="100" class="cls_td_food_title">점심<br>(11:00am~2:30pm)</td>
                    <td width="100" class="cls_td_food_title">저녁<br>(5:00pm~7:30pm)</td>
                  </tr>
                  <tr><td colspan="3"></td></tr>
                  <tr align="center">
                    <td valign="top" width="33%" align="left" title=""><br></td>
                    <td valign="top" width="33%" align="left" title=""><br></td>
                    <td valign="top" width="33%" align="left" title=""><br></td>
                  </tr>
                </table></div></td></tr></table>
              </td></tr>

              <tr align="center" id="tr_box11_3"><td>
                <table><tr><td class="cls_td_food_text"><div><table>
                  <tr>
                    <td width="100" class="cls_td_food_title">교직원식당<br>점심</td>
                    <td width="100" class="cls_td_food_title">일반식당<br>점심</td>
                    <td width="100" class="cls_td_food_title">일반식당<br>저녁</td>
                  </tr>
                  <tr><td colspan="3"></td></tr>
                  <tr align="center">
                    <td valign="top" width="33%" align="left" title="<원산지:게시판참조>
            쌀밥
            호박수제비국">&lt;원산지:게시판..<br>쌀밥<br>호박수제비국<br><br></td>
                    <td valign="top" width="33%" align="left" title="<원산지:게시판참조>
            짜장면/짜장면곱배기
            홍짬뽕">&lt;원산지:게시판..<br>짜장면/짜장면..<br>홍짬뽕<br><br></td>
                    <td valign="top" width="33%" align="left" title="<원산지:게시판참조>
            짜장면/짜장면곱배기
            홍짬뽕">&lt;원산지:게시판..<br>짜장면/짜장면..<br>홍짬뽕<br><br></td>
                  </tr>
                </table></div></td></tr></table>
              </td></tr>

              <tr align="center" id="tr_box11_4"><td>
                <table><tr><td class="cls_td_food_text"><div><table>
                  <tr>
                    <td width="100" class="cls_td_food_title">아침</td>
                    <td width="100" class="cls_td_food_title">점심</td>
                    <td width="100" class="cls_td_food_title">저녁</td>
                  </tr>
                  <tr><td colspan="3"></td></tr>
                  <tr align="center">
                    <td valign="top" width="33%" align="left" title=""><br><br></td>
                    <td valign="top" width="33%" align="left" title="에그베네틱트

            닭가슴살 샐러드

            꼬막 비빔밥">에그베네틱트<br><br>닭가슴살 샐러드<br><br>꼬막 비빔밥<br><br></td>
                    <td valign="top" width="33%" align="left" title="에그베네틱트

            닭가슴살 샐러드

            꼬막 비빔밥">에그베네틱트<br><br>닭가슴살 샐러드<br><br>꼬막 비빔밥<br><br></td>
                  </tr>
                </table></div></td></tr></table>
              </td></tr>

            </table></td></tr></table>
            </body></html>
            """;

    @Test
    void 식단_기준일을_인쇄아이콘_URL에서_읽는다() {
        final Document document = Jsoup.parse(LOGIN_HTML, "https://hisnet.handong.edu");

        final MealResponseDto result = parser.parse(document);

        assertThat(result.date()).isEqualTo(LocalDate.of(2026, 9, 7));
    }

    @Test
    void 메뉴가_있는_식당만_원본_순서대로_내려준다() {
        final Document document = Jsoup.parse(LOGIN_HTML, "https://hisnet.handong.edu");

        final MealResponseDto result = parser.parse(document);

        // 말스키친(tr_box11_2)은 메뉴가 비어 있어 제외된다
        assertThat(result.cafeterias())
                .extracting(CafeteriaMealDto::id, CafeteriaMealDto::name)
                .containsExactly(
                        tuple("STUDENT", "학생식당"),
                        tuple("HANDONG_LOUNGE", "한동라운지"),
                        tuple("GRACE_TABLE", "더그레이스테이블")
                );
    }

    @Test
    void 학생식당은_코너별로_나뉘고_든든한동은_끼니별_title에서_전체메뉴를_읽는다() {
        final Document document = Jsoup.parse(LOGIN_HTML, "https://hisnet.handong.edu");

        final CafeteriaMealDto student = parser.parse(document).cafeterias().get(0);

        assertThat(student.corners()).extracting(MealCornerDto::name)
                .containsExactly("든든한동", "H:plate");

        final MealCornerDto dundun = student.corners().get(0);
        assertThat(dundun.meals()).extracting(MealSlotDto::slot)
                .containsExactly("아침", "점심", "저녁");
        assertThat(dundun.meals().get(0).items())
                .containsExactly("-원산지:메뉴게시판 참조-", "소고기미역죽", "야채커틀렛");
    }

    @Test
    void 헤더없는_점심전용_코너는_행_title에서_읽고_꼬리_대시를_떼어_점심으로_묶는다() {
        final Document document = Jsoup.parse(LOGIN_HTML, "https://hisnet.handong.edu");

        final MealCornerDto hplate = parser.parse(document).cafeterias().get(0).corners().get(1);

        assertThat(hplate.meals()).hasSize(1);
        assertThat(hplate.meals().get(0).slot()).isEqualTo("점심");
        assertThat(hplate.meals().get(0).items())
                .containsExactly(
                        "-원산지:메뉴게시판 참조-",
                        "등심돈까스",
                        "파파치킨까스(대파/양파채 파닭소스)",
                        "달고마떡볶이&모듬튀김"
                );
    }

    @Test
    void 한동라운지는_원본_헤더표기를_끼니라벨로_쓴다() {
        final Document document = Jsoup.parse(LOGIN_HTML, "https://hisnet.handong.edu");

        final CafeteriaMealDto lounge = parser.parse(document).cafeterias().get(1);

        assertThat(lounge.corners()).hasSize(1);
        assertThat(lounge.corners().get(0).name()).isEqualTo("한동라운지");
        assertThat(lounge.corners().get(0).meals()).extracting(MealSlotDto::slot)
                .containsExactly("교직원식당 점심", "일반식당 점심", "일반식당 저녁");
        assertThat(lounge.corners().get(0).meals().get(0).items())
                .containsExactly("<원산지:게시판참조>", "쌀밥", "호박수제비국");
    }

    @Test
    void 더그레이스테이블은_빈_끼니를_건너뛰고_이중개행_title을_항목으로_쪼갠다() {
        final Document document = Jsoup.parse(LOGIN_HTML, "https://hisnet.handong.edu");

        final CafeteriaMealDto grace = parser.parse(document).cafeterias().get(2);

        assertThat(grace.corners().get(0).meals()).extracting(MealSlotDto::slot)
                .containsExactly("점심", "저녁");
        assertThat(grace.corners().get(0).meals().get(0).items())
                .containsExactly("에그베네틱트", "닭가슴살 샐러드", "꼬막 비빔밥");
    }

    @Test
    void 위젯을_찾지_못하면_MEAL_PARSING_FAILED() {
        final Document document = Jsoup.parse("<html><body><p>로그인하여주십시오</p></body></html>",
                "https://hisnet.handong.edu");

        assertThatThrownBy(() -> parser.parse(document))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getExceptionCode())
                .isEqualTo(ExceptionCode.MEAL_PARSING_FAILED);
    }
}
