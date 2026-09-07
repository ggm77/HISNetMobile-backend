package com.seohamin.hisnetmobile.domain.graduation.service;

import com.seohamin.hisnetmobile.domain.graduation.dto.GraduationResponseDto;
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
 * 졸업심사 결과 조회를 원본(HISNet)에 중계하는 서비스.
 * <p>
 * 두 번 조회한다: (1) 진입 페이지 {@code HGRA120M.php} 에서 학생 유형별 결과 페이지 파일명 토큰을 읽고,
 * (2) {@code /prof/graduate/{토큰}.php?gubun=hak} 결과 페이지를 파싱한다.
 * 진입 페이지에 결과보기 버튼이 없으면(졸업 확정 후 등) {@code available=false} 로 돌려준다.
 */
@Service
@RequiredArgsConstructor
public class GraduationService {

    private static final String ENTRY_PATH = "/haksa/graduate/HGRA120M.php";
    private static final String RESULT_PATH_FORMAT = "/prof/graduate/%s.php?gubun=hak";

    private final HisnetClient hisnetClient;
    private final GraduationParser graduationParser;

    /**
     * 현재 로그인한 사용자의 졸업심사 결과를 조회하는 메서드.
     * @param userDetails 인증 주체 (원본 세션 보유)
     * @return 졸업심사 결과 (조회 불가 상태면 available=false)
     */
    public GraduationResponseDto getMyResult(final UserDetails userDetails) {

        final HisnetSession session = resolveSession(userDetails);

        final Document entryPage = hisnetClient.get(ENTRY_PATH, session);
        final String token = graduationParser.extractResultPageToken(entryPage);
        if (token == null) {
            return graduationParser.unavailable(entryPage);
        }

        final Document resultPage = hisnetClient.get(String.format(RESULT_PATH_FORMAT, token), session);

        return graduationParser.parse(resultPage);
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
