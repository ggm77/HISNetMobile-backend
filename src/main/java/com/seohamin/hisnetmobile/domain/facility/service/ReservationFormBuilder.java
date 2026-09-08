package com.seohamin.hisnetmobile.domain.facility.service;

import com.seohamin.hisnetmobile.domain.facility.constant.Facility;
import com.seohamin.hisnetmobile.global.exception.CustomException;
import com.seohamin.hisnetmobile.global.exception.constants.ExceptionCode;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 원본 예약 신청 폼(PSTU420II.php {@code <form name="bookingform">})의 요청 바디를 만드는 컴포넌트.
 * <p>
 * HTTP 를 하지 않는 순수 변환기다. 우리 API 는 <b>30분 단위</b>로만 받고, 여기서 원본이 쓰는
 * 15분 슬롯 필드로 펼친다:
 * <ul>
 *   <li>{@code gbn} = 시설 코드</li>
 *   <li>{@code selDate} = "yyyy-MM-dd"</li>
 *   <li>{@code starttime} / {@code endtime} = "HHmm"</li>
 *   <li>{@code bcode} = 예약변경 시 대상 예약 코드, 신규는 빈 값</li>
 *   <li>{@code chkcnt} = 선택한 15분 칸 수 (= 총 분 / 15)</li>
 *   <li>{@code f<yyyyMMddHHmm>} = "Y" — 선택 구간에 속한 15분 칸마다 하나</li>
 * </ul>
 * 원본 화면은 선택 안 된 칸까지 {@code f...=N} 으로 모두 싣지만(가시 창의 457개), 서버가 실제로 읽는 값은
 * 연속 구간(selDate·starttime·endtime)이라 선택 칸만 {@code Y} 로 싣는다. 원본이 전 칸을 요구하는 것으로
 * 밝혀지면 여기서 {@code N} 칸을 채우도록 보완한다.
 * <p>
 * 원본은 자정을 넘기는 구간(예: 23:30~24:00)을 {@link LocalTime} 로 표현할 수 없어 지금은 받지 않는다.
 */
@Component
public class ReservationFormBuilder {

    private static final int SLOT_MINUTES = 30;
    private static final int SUB_SLOT_MINUTES = 15;

    private static final DateTimeFormatter SEL_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter F_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HHmm");

    /**
     * 예약 신청·변경 폼 바디를 만든다.
     *
     * @param facility             대상 시설
     * @param date                 이용일
     * @param start                시작 시각 (30분 정렬)
     * @param end                  종료 시각 (30분 정렬, start 보다 뒤)
     * @param bookingCodeForChange 예약변경이면 대상 예약 코드, 신규 신청이면 null
     * @return {@code application/x-www-form-urlencoded} 로 보낼 필드 맵
     * @throws CustomException 시간이 30분 단위가 아니거나 start ≥ end 이면 {@link ExceptionCode#INVALID_RESERVATION_TIME}
     */
    public MultiValueMap<String, String> build(
            final Facility facility,
            final LocalDate date,
            final LocalTime start,
            final LocalTime end,
            final Long bookingCodeForChange
    ) {
        validateTimeRange(start, end);

        final int subSlotCount = (int) (Duration.between(start, end).toMinutes() / SUB_SLOT_MINUTES);

        final MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("gbn", String.valueOf(facility.code()));
        form.add("selDate", date.format(SEL_DATE));
        form.add("starttime", start.format(HHMM));
        form.add("endtime", end.format(HHMM));
        form.add("bcode", bookingCodeForChange != null ? String.valueOf(bookingCodeForChange) : "");
        form.add("chkcnt", String.valueOf(subSlotCount));

        final String dayPrefix = date.format(F_DATE);
        for (LocalTime slot = start; slot.isBefore(end); slot = slot.plusMinutes(SUB_SLOT_MINUTES)) {
            form.add("f" + dayPrefix + slot.format(HHMM), "Y");
        }

        return form;
    }

    private void validateTimeRange(final LocalTime start, final LocalTime end) {
        if (start == null || end == null || !start.isBefore(end)
                || !isAligned(start) || !isAligned(end)) {
            throw new CustomException(ExceptionCode.INVALID_RESERVATION_TIME);
        }
    }

    private boolean isAligned(final LocalTime time) {
        return time.getMinute() % SLOT_MINUTES == 0
                && time.getSecond() == 0
                && time.getNano() == 0;
    }
}
