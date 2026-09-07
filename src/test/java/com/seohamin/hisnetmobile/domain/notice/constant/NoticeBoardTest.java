package com.seohamin.hisnetmobile.domain.notice.constant;

import com.seohamin.hisnetmobile.global.exception.CustomException;
import com.seohamin.hisnetmobile.global.exception.constants.ExceptionCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoticeBoardTest {

    @Test
    void 슬러그를_대소문자_무시하고_게시판으로_바꾼다() {
        assertThat(NoticeBoard.from("general")).isEqualTo(NoticeBoard.GENERAL);
        assertThat(NoticeBoard.from("SCHOLARSHIP")).isEqualTo(NoticeBoard.SCHOLARSHIP);
        assertThat(NoticeBoard.from("Dormitory")).isEqualTo(NoticeBoard.DORMITORY);
    }

    @Test
    void 각_게시판은_원본_Board_코드를_안다() {
        assertThat(NoticeBoard.GENERAL.code()).isEqualTo("NB0001");
        assertThat(NoticeBoard.SCHOLARSHIP.code()).isEqualTo("JANG_NOTICE");
        assertThat(NoticeBoard.DORMITORY.code()).isEqualTo("RCNOTICE");
    }

    @Test
    void 알_수_없는_슬러그는_INVALID_NOTICE_BOARD() {
        assertThatThrownBy(() -> NoticeBoard.from("department"))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getExceptionCode())
                .isEqualTo(ExceptionCode.INVALID_NOTICE_BOARD);

        assertThatThrownBy(() -> NoticeBoard.from(null))
                .isInstanceOf(CustomException.class);
    }
}
