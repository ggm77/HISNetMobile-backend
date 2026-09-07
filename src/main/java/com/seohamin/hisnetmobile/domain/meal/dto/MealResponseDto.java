package com.seohamin.hisnetmobile.domain.meal.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 식단표 조회 응답.
 * <p>
 * 원본(HISNet)은 별도 식단 API 가 없고, 로그인 페이지({@code /login/login.php})에 <b>당일</b> 식단표가
 * 서버사이드로 렌더링돼 들어있다. 이 응답은 그 위젯을 파싱해 식당 → 코너 → 끼니(아침/점심/저녁) 순의
 * 트리로 재조립한 것이다.
 *
 * @param date        식단 기준 날짜 (원본 위젯의 인쇄 아이콘 URL 에 박힌 당일 날짜, 못 찾으면 서버 기준 오늘)
 * @param cafeterias  식당별 식단 (메뉴가 하나도 없는 식당은 제외)
 */
public record MealResponseDto(
        LocalDate date,
        List<CafeteriaMealDto> cafeterias
) { }
