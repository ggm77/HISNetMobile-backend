package com.seohamin.hisnetmobile.domain.student.service;

import com.seohamin.hisnetmobile.domain.student.dto.StudentInfoResponseDto;
import com.seohamin.hisnetmobile.global.exception.CustomException;
import com.seohamin.hisnetmobile.global.exception.constants.ExceptionCode;
import com.seohamin.hisnetmobile.global.infra.hisnet.HisnetClient;
import com.seohamin.hisnetmobile.global.infra.hisnet.HisnetSession;
import com.seohamin.hisnetmobile.global.security.HisnetUserDetails;
import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Document;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

/**
 * 학적 기본사항 조회를 원본(HISNet)에 중계하는 서비스.
 * <p>
 * 흐름은 공지 조회와 같다: 인증 주체에서 원본 세션 추출 → HHAK110M.php GET 릴레이(EUC-KR) →
 * 파서로 모바일용 DTO 재조립. 로그인한 사용자 본인의 학적만 조회하므로 별도 파라미터가 없다.
 */
@Service
@RequiredArgsConstructor
public class StudentService {

    // 학적 기본사항 조회 페이지 (로그인 세션 기준 본인 정보)
    private static final String STUDENT_INFO_PATH = "/haksa/hakjuk/HHAK110M.php";

    private final HisnetClient hisnetClient;
    private final StudentInfoParser studentInfoParser;

    /**
     * 현재 로그인한 사용자의 학적 기본사항을 조회하는 메서드.
     * @param userDetails 인증 주체 (원본 세션 보유)
     * @return 학적 기본사항
     */
    public StudentInfoResponseDto getMyInfo(final UserDetails userDetails) {

        // 1) 인증 주체에서 원본 세션(PHPSESSID) 추출
        final HisnetSession session = resolveSession(userDetails);

        // 2) 학적 페이지 GET 릴레이 (EUC-KR → Document)
        final Document document = hisnetClient.get(STUDENT_INFO_PATH, session);

        // 3) 파싱해서 응답
        return studentInfoParser.parse(document);
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
}