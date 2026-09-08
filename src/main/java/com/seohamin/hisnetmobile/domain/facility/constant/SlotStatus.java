package com.seohamin.hisnetmobile.domain.facility.constant;

/**
 * 현황 그리드 한 칸(우리 기준 30분)의 예약 상태.
 * <p>
 * 원본 PSTU420C.php 는 15분 단위 {@code <td>} 에 CSS class 로 상태를 표시한다:
 * {@code bookingactYes}(가능) / {@code bookingactNo}(타인 예약) / {@code bookingactNoMy}(내 예약) /
 * class 없음(불가 — 운영시간 외·마감·블랙아웃 등). 30분으로 병합할 때의 우선순위는
 * {@link #RESERVED_MINE} &gt; {@link #RESERVED_OTHER} &gt; {@link #UNAVAILABLE} &gt; {@link #AVAILABLE}
 * (한 쪽이라도 막혀 있으면 그 30분은 예약 불가로 본다).
 */
public enum SlotStatus {

    /** 30분 두 칸 모두 예약 가능 */
    AVAILABLE,

    /** 현재 로그인 사용자의 예약이 포함됨 */
    RESERVED_MINE,

    /** 다른 사용자의 예약이 포함됨 */
    RESERVED_OTHER,

    /** 운영시간 외·마감·블랙아웃 등으로 선택 불가 */
    UNAVAILABLE
}
