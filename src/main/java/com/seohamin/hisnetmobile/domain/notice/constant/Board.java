package com.seohamin.hisnetmobile.domain.notice.constant;

import com.seohamin.hisnetmobile.global.exception.CustomException;
import com.seohamin.hisnetmobile.global.exception.constants.ExceptionCode;

import java.util.regex.Pattern;

/**
 * 원본 게시판(Board) 코드 상수.
 * <p>
 * list.php / read.php 는 일반공지·학부공지가 같은 핸들러를 쓰고 Board 코드만 다르다.
 * 일반공지는 고정 코드(NB0001), 학부공지는 학과별 코드(B00xx 시리즈)라 프론트에서 코드를 넘겨준다.
 * (학과명 → Board 코드 전체 매핑 테이블은 좌측 메뉴 크롤링으로 별도 수집 예정)
 */
public final class Board {

    // 인스턴스화 방지
    private Board() {}

    /** 일반공지 게시판 코드 */
    public static final String GENERAL = "NB0001";

    // 학부공지 게시판 코드 형식 (예: B0029)
    private static final Pattern DEPARTMENT_BOARD_CODE = Pattern.compile("^[A-Z]{1,3}\\d{3,4}$");

    /**
     * 프론트에서 넘어온 학부 게시판 코드를 검증하는 메서드.
     * @param departmentId 학부 게시판 코드
     * @return 검증된 게시판 코드
     */
    public static String department(final String departmentId) {
        if (departmentId == null || !DEPARTMENT_BOARD_CODE.matcher(departmentId).matches()) {
            throw new CustomException(ExceptionCode.INVALID_DEPARTMENT);
        }

        return departmentId;
    }
}