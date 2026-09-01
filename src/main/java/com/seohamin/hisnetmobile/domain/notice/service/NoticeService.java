package com.seohamin.hisnetmobile.domain.notice.service;

import com.seohamin.hisnetmobile.domain.notice.constant.Board;
import com.seohamin.hisnetmobile.domain.notice.dto.NoticeListResponseDto;
import com.seohamin.hisnetmobile.domain.notice.dto.NoticeResponseDto;
import com.seohamin.hisnetmobile.global.exception.CustomException;
import com.seohamin.hisnetmobile.global.exception.constants.ExceptionCode;
import com.seohamin.hisnetmobile.global.infra.hisnet.HisnetClient;
import com.seohamin.hisnetmobile.global.infra.hisnet.HisnetSession;
import com.seohamin.hisnetmobile.global.security.HisnetUserDetails;
import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Document;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * 공지사항(일반공지 + 학부공지) 조회를 원본(HISNet)에 중계하는 서비스.
 * <p>
 * 흐름은 모두 동일하다: 인증 주체에서 원본 세션 추출 → list.php/read.php GET 릴레이(EUC-KR) →
 * 파서로 모바일용 DTO 재조립. 일반공지와 학부공지는 Board 코드만 다르고 파싱은 완전히 같다.
 */
@Service
@RequiredArgsConstructor
public class NoticeService {

    // 목록/본문 조회는 첫 페이지 기준 (페이징 파라미터는 컨트롤러 확장 시 노출 예정)
    private static final int DEFAULT_PAGE = 1;

    // 글 ID 는 원본이 숫자 ID 를 쓰므로 숫자만 허용
    private static final Pattern NOTICE_NO_PATTERN = Pattern.compile("^\\d+$");

    private final HisnetClient hisnetClient;
    private final NoticeParser noticeParser;

    /**
     * 일반공지 목록을 조회하는 메서드.
     * @param userDetails 인증 주체 (원본 세션 보유)
     * @return 공지 요약 리스트
     */
    public NoticeListResponseDto getGeneralNoticeList(final UserDetails userDetails) {

        return getNoticeList(userDetails, Board.GENERAL);
    }

    /**
     * 특정 일반공지 본문을 조회하는 메서드.
     * @param userDetails 인증 주체 (원본 세션 보유)
     * @param noticeNo 글 ID
     * @return 공지 상세
     */
    public NoticeResponseDto getGeneralNotice(
            final UserDetails userDetails,
            final String noticeNo
    ) {

        return getNotice(userDetails, Board.GENERAL, noticeNo);
    }

    /**
     * 특정 학부 게시판의 공지 목록을 조회하는 메서드.
     * @param userDetails 인증 주체 (원본 세션 보유)
     * @param departmentId 학부 게시판 코드 (프론트에서 전달, 전 학과 대상)
     * @return 공지 요약 리스트
     */
    public NoticeListResponseDto getDepartmentNoticeList(
            final UserDetails userDetails,
            final String departmentId
    ) {

        return getNoticeList(userDetails, Board.department(departmentId));
    }

    /**
     * 특정 학부 게시판의 공지 본문을 조회하는 메서드.
     * @param userDetails 인증 주체 (원본 세션 보유)
     * @param departmentId 학부 게시판 코드
     * @param noticeNo 글 ID
     * @return 공지 상세
     */
    public NoticeResponseDto getDepartmentNotice(
            final UserDetails userDetails,
            final String departmentId,
            final String noticeNo
    ) {

        return getNotice(userDetails, Board.department(departmentId), noticeNo);
    }

    /**
     * 게시판 코드로 목록 페이지를 릴레이·파싱하는 공통 메서드.
     */
    private NoticeListResponseDto getNoticeList(
            final UserDetails userDetails,
            final String boardCode
    ) {

        // 1) 인증 주체에서 원본 세션(PHPSESSID) 추출
        final HisnetSession session = resolveSession(userDetails);

        // 2) 목록 페이지 GET 릴레이 (EUC-KR → Document)
        final Document document = hisnetClient.get(listPath(boardCode), session);

        // 3) 파싱해서 요약 리스트로 응답
        return new NoticeListResponseDto(noticeParser.parseList(document));
    }

    /**
     * 게시판 코드 + 글 ID 로 본문 페이지를 릴레이·파싱하는 공통 메서드.
     */
    private NoticeResponseDto getNotice(
            final UserDetails userDetails,
            final String boardCode,
            final String noticeNo
    ) {

        // 1) 글 ID 형식 검증
        if (noticeNo == null || !NOTICE_NO_PATTERN.matcher(noticeNo).matches()) {
            throw new CustomException(ExceptionCode.INVALID_NOTICE_NO);
        }

        // 2) 인증 주체에서 원본 세션(PHPSESSID) 추출
        final HisnetSession session = resolveSession(userDetails);

        // 3) 본문 페이지 GET 릴레이 (EUC-KR → Document)
        final Document document = hisnetClient.get(readPath(boardCode, noticeNo), session);

        // 4) 파싱해서 상세로 응답
        return noticeParser.parseDetail(document, noticeNo);
    }

    /**
     * 인증 주체에서 원본 세션 쿠키를 꺼내는 메서드.
     * 로그인 중계로 채워진 HisnetUserDetails 가 아니면 세션 만료로 취급한다.
     */
    private HisnetSession resolveSession(final UserDetails userDetails) {
        if (!(userDetails instanceof HisnetUserDetails hisnetUserDetails)) {
            throw new CustomException(ExceptionCode.SESSION_EXPIRED);
        }

        return new HisnetSession(
                hisnetUserDetails.getPhpSessionId(),
                hisnetUserDetails.getCookieId()
        );
    }

    /**
     * 목록 조회용 원본 경로를 만드는 메서드.
     */
    private String listPath(final String boardCode) {
        return "/myboard/list.php?Board=" + encode(boardCode)
                + "&Page=" + DEFAULT_PAGE
                + "&FindIt=&FindText=";
    }

    /**
     * 본문 조회용 원본 경로를 만드는 메서드.
     */
    private String readPath(
            final String boardCode,
            final String noticeNo
    ) {
        return "/myboard/read.php?id=" + encode(noticeNo)
                + "&Board=" + encode(boardCode)
                + "&Page=" + DEFAULT_PAGE;
    }

    private String encode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}