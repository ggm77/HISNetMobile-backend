package com.seohamin.hisnetmobile.domain.meal.controller;

import com.seohamin.hisnetmobile.domain.meal.dto.MealResponseDto;
import com.seohamin.hisnetmobile.domain.meal.service.MealService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Meal", description = "식단표 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meals")
public class MealController {

    private final MealService mealService;

    // 당일 식단표 조회 API (인증 불필요 — 원본 로그인 페이지에 공개로 실려온다)
    @Operation(
            summary = "당일 식단표 조회",
            description = "원본 로그인 페이지에 렌더링된 당일 식단표를 파싱해 식당 → 코너 → 끼니 트리로 반환한다. "
                    + "세션이 필요 없다."
    )
    @GetMapping
    public ResponseEntity<MealResponseDto> getTodayMeals() {

        return ResponseEntity.ok(mealService.getTodayMeals());
    }
}
