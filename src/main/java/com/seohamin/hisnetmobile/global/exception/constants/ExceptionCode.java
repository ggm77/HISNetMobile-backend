package com.seohamin.hisnetmobile.global.exception.constants;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ExceptionCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "필요한 값이 비어있습니다."),
    INVALID_NOTICE_ID(HttpStatus.BAD_REQUEST, "올바르지 않은 공지 ID입니다."),
    INVALID_DEPARTMENT(HttpStatus.BAD_REQUEST, "올바르지 않은 학부 게시판 코드입니다."),
    INVALID_ATTACHMENT_INDEX(HttpStatus.BAD_REQUEST, "올바르지 않은 첨부파일 번호입니다."),

    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "로그인에 실패했습니다. 아이디와 비밀번호를 확인해주세요."),
    SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "히즈넷 세션이 만료되었습니다. 다시 로그인해주세요."),

    NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "공지를 찾을 수 없습니다."),
    ATTACHMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "첨부파일을 찾을 수 없습니다."),

    HISNET_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "히즈넷 서버 요청에 실패했습니다."),

    NOTICE_PARSING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "공지 페이지 파싱에 실패했습니다."),
    STUDENT_INFO_PARSING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "학적 정보 페이지 파싱에 실패했습니다."),
    MEAL_PARSING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "식단 페이지 파싱에 실패했습니다."),
    TIMETABLE_PARSING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "시간표 페이지 파싱에 실패했습니다."),
    GRADE_PARSING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "성적 페이지 파싱에 실패했습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버에서 에러가 발생했습니다.")
    ;

    private final HttpStatus httpStatus;
    private final String message;
}