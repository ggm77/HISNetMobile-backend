package com.seohamin.hisnetmobile.domain.facility.dto;

import com.seohamin.hisnetmobile.domain.facility.constant.Facility;
import com.seohamin.hisnetmobile.domain.facility.constant.FacilityCategory;

import java.util.Arrays;
import java.util.List;

/**
 * 예약 가능한 시설 카탈로그 (분류 → 시설 목록).
 * <p>
 * 원본 {@code PSTU420M.php?gubun=1~3} 화면을 코드 상수({@link Facility})로 고정해 둔 것이라
 * 원본 요청 없이 즉시 응답한다. 학기마다 원본과 대조해 상수를 갱신한다.
 *
 * @param categories 분류별 시설 묶음 (원본 탭 순서: 운동시설·회의실·편의시설)
 */
public record FacilityCatalogResponseDto(
        List<CategoryGroup> categories
) {

    /**
     * @param category     분류 slug (sports/meeting/convenience)
     * @param categoryName 분류 표시명
     * @param facilities   해당 분류의 시설 목록 (원본 화면 순서)
     */
    public record CategoryGroup(
            String category,
            String categoryName,
            List<FacilityDto> facilities
    ) { }

    public static FacilityCatalogResponseDto ofAll() {
        final List<CategoryGroup> groups = Arrays.stream(FacilityCategory.values())
                .map(category -> new CategoryGroup(
                        category.slug(),
                        category.displayName(),
                        Facility.byCategory(category).stream()
                                .map(FacilityDto::from)
                                .toList()))
                .toList();

        return new FacilityCatalogResponseDto(groups);
    }
}
