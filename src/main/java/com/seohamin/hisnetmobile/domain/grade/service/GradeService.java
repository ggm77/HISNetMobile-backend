package com.seohamin.hisnetmobile.domain.grade.service;

import com.seohamin.hisnetmobile.domain.grade.dto.GradeResponseDto;
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
 * 전체성적조회를 원본(HISNet)에 중계하는 서비스.
 * <p>
 * 흐름은 학적/시간표 조회와 같다: 인증 주체에서 원본 세션 추출 → HREC110M.php GET 릴레이(EUC-KR) →
 * 파서로 모바일용 DTO 재조립. 누적 성적·학기별 요약·학기별 상세가 한 페이지에 다 들어있어 한 번만 조회한다.
 */
@Service
@RequiredArgsConstructor
public class GradeService {

    // 전체성적조회 페이지 (로그인 세션 기준 본인 성적)
    private static final String GRADE_PATH = "/haksa/record/HREC110M.php";

    private final HisnetClient hisnetClient;
    private final GradeParser gradeParser;

    /**
     * 현재 로그인한 사용자의 전체 성적을 조회하는 메서드.
     * @param userDetails 인증 주체 (원본 세션 보유)
     * @return 성적 (누적 요약 + 학기별)
     */
    public GradeResponseDto getMyGrades(final UserDetails userDetails) {

        final HisnetSession session = resolveSession(userDetails);

        final Document document = hisnetClient.get(GRADE_PATH, session);

        return gradeParser.parse(document);
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
