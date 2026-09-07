package com.seohamin.hisnetmobile.domain.notice.constant;

import com.seohamin.hisnetmobile.global.exception.CustomException;
import com.seohamin.hisnetmobile.global.exception.constants.ExceptionCode;

import java.util.Locale;

/**
 * 학과별 코드가 아닌 <b>고정 이름</b>을 가진 공지 게시판.
 * <p>
 * list.php / read.php / down.php 는 게시판을 가리지 않으므로, 이 게시판들은 학부공지처럼
 * 프론트에서 코드를 받을 필요 없이 슬러그({@code general} 등)로만 구분한다. 파싱은 전부 동일.
 */
public enum NoticeBoard {

    /** 일반공지 */
    GENERAL("NB0001"),
    /** 장학공지 */
    SCHOLARSHIP("JANG_NOTICE"),
    /** 생활관(RC)공지 */
    DORMITORY("RCNOTICE");

    private final String code;

    NoticeBoard(final String code) {
        this.code = code;
    }

    /** 원본 list.php {@code Board} 파라미터 값. */
    public String code() {
        return code;
    }

    /**
     * URL 슬러그(대소문자 무시: {@code general} / {@code scholarship} / {@code dormitory})를 게시판으로 바꾸는 메서드.
     * @throws CustomException 알 수 없는 슬러그면 {@link ExceptionCode#INVALID_NOTICE_BOARD}
     */
    public static NoticeBoard from(final String slug) {
        if (slug == null || slug.isBlank()) {
            throw new CustomException(ExceptionCode.INVALID_NOTICE_BOARD);
        }
        try {
            return valueOf(slug.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ex) {
            throw new CustomException(ExceptionCode.INVALID_NOTICE_BOARD);
        }
    }
}
