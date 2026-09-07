package com.seohamin.hisnetmobile.domain.timetable.service;

import com.seohamin.hisnetmobile.domain.timetable.dto.TimetableResponseDto;
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
 * 내 시간표 조회를 원본(HISNet)에 중계하는 서비스.
 * <p>
 * 흐름은 학적 조회와 같다: 인증 주체에서 원본 세션 추출 → HLES110M.php GET 릴레이(EUC-KR) →
 * 파서로 모바일용 DTO 재조립. 원본이 연도/학기 선택 없이 현재 학기 시간표를 주므로 별도 파라미터가 없다.
 */
@Service
@RequiredArgsConstructor
public class TimetableService {

    // 내시간표조회 페이지 (로그인 세션 기준 현재 학기)
    private static final String TIMETABLE_PATH = "/for_student/course/HLES110M.php";

    private final HisnetClient hisnetClient;
    private final TimetableParser timetableParser;

    /**
     * 현재 로그인한 사용자의 이번 학기 시간표를 조회하는 메서드.
     * @param userDetails 인증 주체 (원본 세션 보유)
     * @return 시간표
     */
    public TimetableResponseDto getMyTimetable(final UserDetails userDetails) {

        // 1) 인증 주체에서 원본 세션(PHPSESSID) 추출
        final HisnetSession session = resolveSession(userDetails);

        // 2) 시간표 페이지 GET 릴레이 (EUC-KR → Document)
        final Document document = hisnetClient.get(TIMETABLE_PATH, session);

        // 3) 파싱해서 응답
        return timetableParser.parse(document);
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
