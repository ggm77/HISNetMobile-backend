package com.seohamin.hisnetmobile.domain.notice.service;

import com.seohamin.hisnetmobile.domain.notice.constant.Board;
import com.seohamin.hisnetmobile.domain.notice.dto.NoticeListResponseDto;
import com.seohamin.hisnetmobile.domain.notice.dto.NoticeResponseDto;
import com.seohamin.hisnetmobile.domain.notice.dto.SimpleNoticeResponseDto;
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
import java.util.List;
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

    // 페이지 번호는 1부터. 본문(read.php) 조회는 페이지 맥락이 의미 없어 이 값을 그대로 쓴다.
    private static final int FIRST_PAGE = 1;

    // 글 ID 는 원본이 숫자 ID 를 쓰므로 숫자만 허용
    private static final Pattern NOTICE_ID_PATTERN = Pattern.compile("^\\d+$");

    private final HisnetClient hisnetClient;
    private final NoticeParser noticeParser;

    /**
     * 일반공지 목록을 조회하는 메서드.
     * @param userDetails 인증 주체 (원본 세션 보유)
     * @param page 조회할 페이지 (1부터, 1 미만은 1로 보정)
     * @return 공지 요약 리스트 (+ 페이지 정보)
     */
    public NoticeListResponseDto getGeneralNoticeList(
            final UserDetails userDetails,
            final int page
    ) {

        return getNoticeList(userDetails, Board.GENERAL, page);
    }

    /**
     * 특정 일반공지 본문을 조회하는 메서드.
     * @param userDetails 인증 주체 (원본 세션 보유)
     * @param noticeId 글 ID
     * @return 공지 상세
     */
    public NoticeResponseDto getGeneralNotice(
            final UserDetails userDetails,
            final String noticeId
    ) {

        return getNotice(userDetails, Board.GENERAL, noticeId);
    }

    /**
     * 특정 학부 게시판의 공지 목록을 조회하는 메서드.
     * @param userDetails 인증 주체 (원본 세션 보유)
     * @param departmentId 학부 게시판 코드 (프론트에서 전달, 전 학과 대상)
     * @param page 조회할 페이지 (1부터, 1 미만은 1로 보정)
     * @return 공지 요약 리스트 (+ 페이지 정보)
     */
    public NoticeListResponseDto getDepartmentNoticeList(
            final UserDetails userDetails,
            final String departmentId,
            final int page
    ) {

        return getNoticeList(userDetails, Board.department(departmentId), page);
    }

    /**
     * 특정 학부 게시판의 공지 본문을 조회하는 메서드.
     * @param userDetails 인증 주체 (원본 세션 보유)
     * @param departmentId 학부 게시판 코드
     * @param noticeId 글 ID
     * @return 공지 상세
     */
    public NoticeResponseDto getDepartmentNotice(
            final UserDetails userDetails,
            final String departmentId,
            final String noticeId
    ) {

        return getNotice(userDetails, Board.department(departmentId), noticeId);
    }

    /**
     * 게시판 코드 + 페이지로 목록을 릴레이·파싱하는 공통 메서드.
     * 고정공지는 모든 페이지에 반복되므로 2페이지부터는 걸러낸다.
     */
    private NoticeListResponseDto getNoticeList(
            final UserDetails userDetails,
            final String boardCode,
            final int page
    ) {

        // 1) 페이지 보정 (1 미만은 1) — 원본은 최대 페이지 초과 시 마지막 페이지로 클램프한다
        final int safePage = Math.max(page, FIRST_PAGE);

        // 2) 인증 주체에서 원본 세션(PHPSESSID) 추출
        final HisnetSession session = resolveSession(userDetails);

        // 3) 목록 페이지 GET 릴레이 (EUC-KR → Document)
        final Document document = hisnetClient.get(listPath(boardCode, safePage), session);

        // 4) 파싱 + 2페이지부터 고정공지 제외
        List<SimpleNoticeResponseDto> notices = noticeParser.parseList(document);
        if (safePage > FIRST_PAGE) {
            notices = notices.stream()
                    .filter(notice -> !notice.pinned())
                    .toList();
        }

        // 5) 페이저에서 마지막 페이지 파악 후 페이지 메타 구성
        final int totalPages = Math.max(safePage, noticeParser.parseTotalPages(document));

        return new NoticeListResponseDto(
                notices,
                safePage,
                totalPages,
                safePage < totalPages,
                safePage > FIRST_PAGE
        );
    }

    /**
     * 게시판 코드 + 글 ID 로 본문 페이지를 릴레이·파싱하는 공통 메서드.
     */
    private NoticeResponseDto getNotice(
            final UserDetails userDetails,
            final String boardCode,
            final String noticeId
    ) {

        // 1) 글 ID 형식 검증
        if (noticeId == null || !NOTICE_ID_PATTERN.matcher(noticeId).matches()) {
            throw new CustomException(ExceptionCode.INVALID_NOTICE_ID);
        }

        // 2) 인증 주체에서 원본 세션(PHPSESSID) 추출
        final HisnetSession session = resolveSession(userDetails);

        // 3) 본문 페이지 GET 릴레이 (EUC-KR → Document)
        final Document document = hisnetClient.get(readPath(boardCode, noticeId), session);

        // 4) 파싱해서 상세로 응답
        return noticeParser.parseDetail(document, noticeId);
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
    private String listPath(
            final String boardCode,
            final int page
    ) {
        return "/myboard/list.php?Board=" + encode(boardCode)
                + "&Page=" + page
                + "&FindIt=&FindText=";
    }

    /**
     * 본문 조회용 원본 경로를 만드는 메서드.
     */
    private String readPath(
            final String boardCode,
            final String noticeId
    ) {
        return "/myboard/read.php?id=" + encode(noticeId)
                + "&Board=" + encode(boardCode)
                + "&Page=" + FIRST_PAGE;
    }

    private String encode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}