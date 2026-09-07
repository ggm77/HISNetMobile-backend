package com.seohamin.hisnetmobile.domain.meal.dto;

import java.util.List;

/**
 * 한 끼니의 메뉴 항목들.
 *
 * @param slot  끼니 구분. 보통 {@code 아침 / 점심 / 저녁} 이고, 한동라운지처럼 원본 헤더가 다르면
 *              그 표기를 그대로 쓴다(예: {@code 교직원식당 점심}). 학생식당의 점심 전용 코너는 {@code 점심}.
 * @param items 메뉴 항목. 원본 표기 순서를 유지하며, 맨 앞의 "-원산지:메뉴게시판 참조-" 같은 안내 문구도
 *              원본에 있으면 그대로 포함한다.
 */
public record MealSlotDto(
        String slot,
        List<String> items
) { }
