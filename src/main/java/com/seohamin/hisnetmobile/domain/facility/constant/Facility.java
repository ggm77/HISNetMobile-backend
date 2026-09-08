package com.seohamin.hisnetmobile.domain.facility.constant;

import com.seohamin.hisnetmobile.global.exception.CustomException;
import com.seohamin.hisnetmobile.global.exception.constants.ExceptionCode;

import java.util.Arrays;
import java.util.List;

/**
 * 예약 가능한 개별 시설.
 * <p>
 * 원본 시설예약신청 페이지(PSTU420M.php?gubun=1~3)에서 2026-09-08 로그인 세션으로 수집했다.
 * 각 시설 버튼은 {@code javascript:viewCaleder('<code>','<groupId>')} 이고, 첫 번째 인자
 * {@code code} 가 곧 현황/신청 요청의 {@code gubun}/{@code gbn}/{@code fcode} 값이다.
 * <p>
 * 원본 카탈로그가 학기마다 바뀔 수 있으므로(동아리방 신설·명칭 변경 등) 여기 하드코딩한 목록은
 * 학기 시작마다 원본과 대조해 갱신한다. {@code NoticeBoard} 매핑 테이블과 같은 운영 방침이다.
 *
 * @see FacilityCategory
 */
public enum Facility {

    // 운동시설 (gubun=1)
    BASKETBALL_1(83, "농구장1(채플쪽)", FacilityCategory.SPORTS),
    BASKETBALL_2(84, "농구장2(학생식당쪽)", FacilityCategory.SPORTS),
    BASKETBALL_3(85, "농구장3(맘스쪽)", FacilityCategory.SPORTS),
    BASKETBALL_4(86, "농구장4(도서관쪽)", FacilityCategory.SPORTS),
    TURF_FIELD(82, "인조잔디구장", FacilityCategory.SPORTS),
    TENNIS_A(87, "테니스장 A", FacilityCategory.SPORTS),
    TENNIS_B(102, "테니스장 B", FacilityCategory.SPORTS),
    FUTSAL_HIDDINK(81, "히딩크 드림필드Ⅱ", FacilityCategory.SPORTS),

    // 회의실 (gubun=2)
    CODING_TALK(98, "Coding Talk", FacilityCategory.MEETING),
    SANGSANG_LAB_01(88, "상상랩01", FacilityCategory.MEETING),
    SANGSANG_LAB_02(89, "상상랩02", FacilityCategory.MEETING),
    SANGSANG_LAB_03(90, "상상랩03", FacilityCategory.MEETING),
    SANGSANG_LAB_04(91, "상상랩04", FacilityCategory.MEETING),
    SANGSANG_LAB_05(92, "상상랩05", FacilityCategory.MEETING),
    SANGSANG_LAB_06(93, "상상랩06", FacilityCategory.MEETING),
    SANGSANG_LAB_07(94, "상상랩07", FacilityCategory.MEETING),
    SANGSANG_LAB_08(95, "상상랩08", FacilityCategory.MEETING),
    SANGSANG_LAB_09(96, "상상랩09", FacilityCategory.MEETING),
    SANGSANG_LAB_10(97, "상상랩10", FacilityCategory.MEETING),

    // 편의시설 (gubun=3)
    GLOBAL_LOUNGE_A(211, "글로벌라운지(A)", FacilityCategory.CONVENIENCE),
    GLOBAL_LOUNGE_B(212, "글로벌라운지(B)", FacilityCategory.CONVENIENCE),
    GLOBAL_LOUNGE_C(213, "글로벌라운지(C)", FacilityCategory.CONVENIENCE);

    private final int code;
    private final String displayName;
    private final FacilityCategory category;

    Facility(final int code, final String displayName, final FacilityCategory category) {
        this.code = code;
        this.displayName = displayName;
        this.category = category;
    }

    /** 원본 현황(PSTU420C.php?gubun=)·신청(PSTU420II.php gbn=) 요청에 쓰는 시설 코드 */
    public int code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    public FacilityCategory category() {
        return category;
    }

    /**
     * 시설 코드로 시설을 찾는다.
     * @throws CustomException 매칭되는 시설이 없으면 {@link ExceptionCode#INVALID_FACILITY}
     */
    public static Facility from(final int code) {
        for (final Facility facility : values()) {
            if (facility.code == code) {
                return facility;
            }
        }
        throw new CustomException(ExceptionCode.INVALID_FACILITY);
    }

    /** 해당 분류의 시설을 열거 순서(원본 화면 순서)대로 반환한다. */
    public static List<Facility> byCategory(final FacilityCategory category) {
        return Arrays.stream(values())
                .filter(facility -> facility.category == category)
                .toList();
    }
}
