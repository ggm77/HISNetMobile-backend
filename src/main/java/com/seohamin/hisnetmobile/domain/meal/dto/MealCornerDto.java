package com.seohamin.hisnetmobile.domain.meal.dto;

import java.util.List;

/**
 * 한 코너의 끼니별 메뉴.
 *
 * @param name  코너명 (든든한동 등). 코너가 하나뿐인 식당은 식당 표시명과 같다.
 * @param meals 끼니별 메뉴. 원본에서 메뉴가 비어 있는 끼니는 제외한다.
 */
public record MealCornerDto(
        String name,
        List<MealSlotDto> meals
) { }
