package com.seohamin.hisnetmobile.domain.notice.service;

import com.seohamin.hisnetmobile.domain.notice.dto.NoticeResponseDto;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

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
            <table><tr><td class="readText BoardContent">본문 내용입니다. 여러 줄.</td></tr></table>
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
    void 첨부는_기사영역만_수집하고_하단_목록의_다운로드_링크와_크기꼬리는_제외한다() {
        final Document document = Jsoup.parse(READ_HTML, "https://hisnet.handong.edu");

        final NoticeResponseDto result = parser.parseDetail(document, "175753");

        assertThat(result.files()).containsExactly("첨부_안내문.pdf");
    }
}
