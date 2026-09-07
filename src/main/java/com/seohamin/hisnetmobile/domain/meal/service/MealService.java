package com.seohamin.hisnetmobile.domain.meal.service;

import com.seohamin.hisnetmobile.domain.meal.dto.MealResponseDto;
import com.seohamin.hisnetmobile.global.infra.hisnet.HisnetClient;
import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

/**
 * 당일 식단표 조회를 원본(HISNet)에 중계하는 서비스.
 * <p>
 * 다른 도메인과 달리 <b>인증이 필요 없다</b>. 원본은 식단 전용 페이지·API 가 없고, 로그인 페이지
 * ({@code /login/login.php})에 당일 식단표가 서버사이드로 렌더링돼 미인증 상태에서도 그대로 내려온다.
 * 그래서 세션 없이 공개 GET({@link HisnetClient#getPublic})으로 받아 파서에 넘긴다.
 */
@Service
@RequiredArgsConstructor
public class MealService {

    private static final String LOGIN_PAGE_PATH = "/login/login.php";

    private final HisnetClient hisnetClient;
    private final MealParser mealParser;

    /**
     * 원본 로그인 페이지에서 당일 식단표를 파싱해 반환하는 메서드.
     * @return 식당 → 코너 → 끼니 트리로 재조립한 식단표
     */
    public MealResponseDto getTodayMeals() {

        final Document document = hisnetClient.getPublic(LOGIN_PAGE_PATH);

        return mealParser.parse(document);
    }
}
