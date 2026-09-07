package com.seohamin.hisnetmobile.domain.notice.service;

import com.seohamin.hisnetmobile.domain.notice.dto.AttachmentResponseDto;
import com.seohamin.hisnetmobile.domain.notice.dto.NoticeResponseDto;
import com.seohamin.hisnetmobile.domain.notice.dto.SimpleNoticeResponseDto;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * read.php 상세 파싱 회귀 테스트.
 * <p>
 * 실제 HISNet read.php 구조를 축약: 헤더 메타는 {@code div.readText.cls_Padding10} 안의
 * "라벨 span → 값 span" 나열(영문 라벨), 제목은 첫 블록의 "글ID." 뒤, 본문은
 * {@code td.readText.BoardContent}. 페이지 하단엔 게시판 목록({@code tr.tr_basic})이 붙는다.
 */
class NoticeParserTest {

    private final NoticeParser parser = new NoticeParser();

    private static final String READ_HTML = """
            <html><head><title>HISNet</title></head><body>
            Welcome, tester | 일반공지 | 학부공지
            <table><tr><td><form><input></form><script>var u='http://x?a=1&b=2';</script></td>
            <td><meta http-equiv="X-UA-Compatible" content="IE=edge">
            <link rel="stylesheet" href="/a.css?v=1">
            <script>function f(){}</script>
            <table><tr><td><table><tr>
              <td><div align="left" class="readText cls_Padding10" style="vertical-align:middle">
                    <span style="font-weight:bold">\s
                        175753.
                    </span>
                    <span style="font-weight:bold">
                        &#128226;공지!! 26-2학기 아나운서 교육희망자를 모집합니다
                    </span>
                  </div></td>
              <td><div class="readText cls_Padding10"><span>Date</span><span>2026-09-06</span></div></td>
            </tr><tr>
              <td><div class="readText cls_Padding10"><span>Writer</span><span>csy14(홍길동)</span><span>Read</span><span>56</span></div></td>
              <td><div class="readText cls_Padding10"><span><span>Category</span><span>General Info(전체 공지)</span></span><span>No</span><span>175753</span></div></td>
            </tr></table></td></tr></table>
            <table><tr><td class="cls_PaddingL10">첨부 #1</td>
                <td><a href="down.php?Board=NB0001&id=175753&fidx=1&filename=x">첨부_안내문.pdf (1,321,448 bytes)</a></td></tr></table>
            <table><tr><td class="readText BoardContent">본문 내용입니다. 여러 줄.
                <img src="/upload/report/abc123image001.jpg">
                <img src="/myboard/images/icon_file.gif">
                <img src="/upload/report/abc123image001.jpg">
            </td></tr></table>
            <table>
              <tr class="listTitleBorder"><td>No</td><td>제목</td></tr>
              <tr class="tr_basic"><td class="listBody">99</td><td class="listBody"><a href="read.php?id=175700">다른 글</a></td>
                  <td class="listBody"><a href="down.php?id=175700&fidx=1&filename=other">1</a></td></tr>
            </table>
            </td></tr></table>
            </td></tr></table>
            </body></html>
            """;

    @Test
    void 상세_페이지에서_제목_작성자_조회수_날짜_분류_본문을_파싱한다() {
        final Document document = Jsoup.parse(READ_HTML, "https://hisnet.handong.edu");

        final NoticeResponseDto result = parser.parseDetail(document, "175753");

        assertThat(result.id()).isEqualTo("175753");
        assertThat(result.subject()).isEqualTo("📢공지!! 26-2학기 아나운서 교육희망자를 모집합니다");
        assertThat(result.writer()).isEqualTo("csy14(홍길동)");
        assertThat(result.read()).isEqualTo(56);
        assertThat(result.time()).isEqualTo(LocalDate.of(2026, 9, 6));
        assertThat(result.category()).isEqualTo("General Info(전체 공지)");
        assertThat(result.body()).contains("본문 내용입니다");
    }

    @Test
    void 본문_이미지는_절대URL로_수집하고_레이아웃_아이콘과_중복은_제외한다() {
        final Document document = Jsoup.parse(READ_HTML, "https://hisnet.handong.edu");

        final NoticeResponseDto result = parser.parseDetail(document, "175753");

        assertThat(result.images())
                .containsExactly("https://hisnet.handong.edu/upload/report/abc123image001.jpg");
    }

    @Test
    void 이미지로만_이뤄진_공지는_body가_비고_images로_내용을_노출한다() {
        final String imageOnlyHtml = """
                <html><head><title>HISNet</title></head><body>
                <table><tr><td><table><tr>
                  <td><div class="readText cls_Padding10"><span style="font-weight:bold">11345.</span>
                      <span style="font-weight:bold">[생활관] 2학기 안내문</span></div></td>
                  <td><div class="readText cls_Padding10"><span>Date</span><span>2026-09-05</span>
                      <span>Writer</span><span>rc_office</span></div></td>
                </tr></table></td></tr></table>
                <table><tr><td class="readText BoardContent">
                    <img src="/upload/report/03e39d42image001.jpg" width="1403">
                </td></tr></table>
                </body></html>
                """;
        final Document document = Jsoup.parse(imageOnlyHtml, "https://hisnet.handong.edu");

        final NoticeResponseDto result = parser.parseDetail(document, "11345");

        assertThat(result.subject()).isEqualTo("[생활관] 2학기 안내문");
        assertThat(result.body()).isBlank();
        assertThat(result.images())
                .containsExactly("https://hisnet.handong.edu/upload/report/03e39d42image001.jpg");
    }

    @Test
    void 첨부는_기사영역만_수집하고_fidx와_파일명을_뽑으며_하단_목록_링크와_크기꼬리는_제외한다() {
        final Document document = Jsoup.parse(READ_HTML, "https://hisnet.handong.edu");

        final NoticeResponseDto result = parser.parseDetail(document, "175753");

        assertThat(result.files())
                .extracting(AttachmentResponseDto::index, AttachmentResponseDto::name)
                .containsExactly(tuple(1, "첨부_안내문.pdf"));
    }

    /**
     * list.php 목록 표: 헤더 {@code No | Subject | Files | Writer | Date | Read},
     * 데이터 행은 {@code tr.tr_basic}, No·Subject 칸 모두 read.php 링크가 걸려 있다.
     * 고정공지 행은 No 칸이 "고정공지".
     */
    private static final String LIST_HTML = """
            <html><body>
            <table>
              <tr>
                <td class="listTitleBorder"><div>No</div></td>
                <td class="listTitleBorder"><div>Subject</div></td>
                <td class="listTitleBorder"><div>Files</div></td>
                <td class="listTitleBorder"><div>Writer</div></td>
                <td class="listTitleBorder"><div>Date</div></td>
                <td class="listTitleBorder"><div>Read</div></td>
              </tr>
              <tr class="tr_basic">
                <td class="listBody"><a href="read.php?id=175708&Board=NB0001&Page=1">고정공지</a></td>
                <td class="listBody"><a href="read.php?id=175708&Board=NB0001&Page=1">[교무팀] 폐강과목 안내</a></td>
                <td class="listBody">- -</td>
                <td class="listBody">kylelee00</td>
                <td class="listBody">2026-09-03</td>
                <td class="listBody">1,169.</td>
              </tr>
              <tr class="tr_basic">
                <td class="listBody"><a href="read.php?id=175600&Board=NB0001&Page=1">344</a></td>
                <td class="listBody"><a href="read.php?id=175600&Board=NB0001&Page=1">🌐지속가능한 지역을 위한 수요조사</a></td>
                <td class="listBody"><a href="down.php?id=175600&fidx=1">1</a><a href="down.php?id=175600&fidx=2">2</a></td>
                <td class="listBody">bgd6315</td>
                <td class="listBody">2026-09-04</td>
                <td class="listBody">8.</td>
              </tr>
            </table>
            <table>
              <tr><td class="listTitleBorder">1</td><td>2</td><td>3</td></tr>
              <tr><td><a href="list.php?Board=NB0001&Page=2">2</a><a href="list.php?Board=NB0001&Page=3532">&gt;&gt;</a></td></tr>
            </table>
            </body></html>
            """;

    @Test
    void 목록은_헤더_컬럼_위치대로_제목_작성자_날짜_조회수_첨부수를_읽는다() {
        final Document document = Jsoup.parse(LIST_HTML, "https://hisnet.handong.edu");

        final List<SimpleNoticeResponseDto> notices = parser.parseList(document);

        assertThat(notices)
                .extracting(SimpleNoticeResponseDto::id, SimpleNoticeResponseDto::subject,
                        SimpleNoticeResponseDto::writer, SimpleNoticeResponseDto::time,
                        SimpleNoticeResponseDto::read, SimpleNoticeResponseDto::files,
                        SimpleNoticeResponseDto::pinned)
                .containsExactly(
                        tuple("175708", "[교무팀] 폐강과목 안내", "kylelee00", LocalDate.of(2026, 9, 3), 1169, 0, true),
                        tuple("175600", "🌐지속가능한 지역을 위한 수요조사", "bgd6315", LocalDate.of(2026, 9, 4), 8, 2, false)
                );
    }

    @Test
    void 목록_페이저에서_마지막_페이지를_읽는다() {
        final Document document = Jsoup.parse(LIST_HTML, "https://hisnet.handong.edu");

        assertThat(parser.parseTotalPages(document)).isEqualTo(3532);
    }
}
