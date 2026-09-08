package com.seohamin.hisnetmobile.domain.facility.dto;

import com.seohamin.hisnetmobile.domain.facility.constant.Facility;

/**
 * 카탈로그의 시설 한 개.
 *
 * @param id   현황·신청 요청에 쓰는 시설 코드 (원본 {@code gubun}/{@code gbn})
 * @param name 시설명
 */
public record FacilityDto(
        int id,
        String name
) {

    public static FacilityDto from(final Facility facility) {
        return new FacilityDto(facility.code(), facility.displayName());
    }
}
