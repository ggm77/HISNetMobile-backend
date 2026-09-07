package com.seohamin.hisnetmobile.domain.meal.dto;

import java.util.List;

/**
 * 한 식당(학생식당 / 말스키친 / 한동라운지 / 더그레이스테이블)의 식단.
 *
 * @param id      식당 식별자 (STUDENT / MARS_KITCHEN / HANDONG_LOUNGE / GRACE_TABLE)
 * @param name    식당 표시명 (학생식당 등)
 * @param corners 코너별 메뉴. 학생식당만 코너가 여러 개(든든한동 / H:plate / Asian Market / Han's Deli / 따스한동)고,
 *                나머지 식당은 식당명과 같은 코너 하나만 온다.
 */
public record CafeteriaMealDto(
        String id,
        String name,
        List<MealCornerDto> corners
) { }
