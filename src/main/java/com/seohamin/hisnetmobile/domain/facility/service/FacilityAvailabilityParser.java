package com.seohamin.hisnetmobile.domain.facility.service;

import com.seohamin.hisnetmobile.domain.facility.constant.Facility;
import com.seohamin.hisnetmobile.domain.facility.constant.SlotStatus;
import com.seohamin.hisnetmobile.domain.facility.dto.FacilityAvailabilityResponseDto;
import com.seohamin.hisnetmobile.domain.facility.dto.FacilityDayAvailabilityDto;
import com.seohamin.hisnetmobile.domain.facility.dto.FacilityQuotaDto;
import com.seohamin.hisnetmobile.domain.facility.dto.FacilitySlotDto;
import com.seohamin.hisnetmobile.global.exception.CustomException;
import com.seohamin.hisnetmobile.global.exception.constants.ExceptionCode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 시설 예약 현황 페이지(PSTU420C.php)를 파싱하는 컴포넌트.
 * <p>
 * 원본 그리드는 기준일부터 10일치이고, 한 칸({@code <td>})은 <b>고정 15분이 아니라 가변 길이 구간</b>이다
 * (한 예약/한 빈 구간이 통째로 한 칸 — 15분부터 여러 시간까지). id/class 앵커가 거의 없어 셀의 onclick 만 읽는다:
 * <ul>
 *   <li>{@code selectDate(this,'YYYY-MM-DD','HHMM','HHMM')} + class {@code bookingactYes} → 예약 가능 구간</li>
 *   <li>{@code ViewInfo('YYYY-MM-DD','HHMM','HHMM','id')} + class {@code bookingactNo} → 타인 예약 구간</li>
 *   <li>같은 {@code ViewInfo} + class {@code bookingactNoMy} → 내 예약 구간</li>
 *   <li>onclick 없는 칸 → 격자 뼈대. 읽지 않는다.</li>
 * </ul>
 * 각 구간을 15분 격자로 펼친 뒤, 인접한 두 칸을 묶어 <b>30분 슬롯</b>으로 병합한다. 30분을 이루는 두 15분이
 * 모두 "가능"이어야 예약 가능으로 보고, 하나라도 내 예약/타인 예약/공백이면 각각 그 상태로 내려간다
 * ({@link SlotStatus} 우선순위 참고). 종료가 {@code 2400}(자정)인 칸도 처리한다.
 * <p>
 * 쿼터 패널("총 예약가능시간 잔여 8시간 / 8시간" …)은 문구가 자주 바뀔 수 있어, 파싱에 실패해도
 * 현황 조회 자체는 실패시키지 않고 {@code quota} 를 비운다.
 */
@Component
@Slf4j
public class FacilityAvailabilityParser {

    /** 우리가 노출하는 슬롯 단위(분). 원본 가변 구간을 이 단위로 병합한다. */
    public static final int SLOT_MINUTES = 30;

    private static final int WINDOW_DAYS = 10;
    private static final int SUB_SLOT_MINUTES = 15;
    private static final int MINUTES_PER_DAY = 24 * 60;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final Pattern SELECT_DATE = Pattern.compile(
            "selectDate\\(this,\\s*'(\\d{4}-\\d{2}-\\d{2})',\\s*'(\\d{3,4})',\\s*'(\\d{3,4})'");
    private static final Pattern VIEW_INFO = Pattern.compile(
            "ViewInfo\\(\\s*'(\\d{4}-\\d{2}-\\d{2})',\\s*'(\\d{3,4})',\\s*'(\\d{3,4})'");

    // "잔여 8시간 / 8시간", "잔여 2시간 30분 / 10시간"
    private static final Pattern QUOTA_PAIR = Pattern.compile(
            "잔여\\s*(\\d+)\\s*시간(?:\\s*(\\d+)\\s*분)?\\s*/\\s*(\\d+)\\s*시간(?:\\s*(\\d+)\\s*분)?");
    // 초기화 예정일 "2026-09-13 (Sun)"
    private static final Pattern RESET_DATE = Pattern.compile(
            "(\\d{4}-\\d{2}-\\d{2})\\s*\\((?:Mon|Tue|Wed|Thu|Fri|Sat|Sun)\\)");
    // 페이지 헤더의 "[테니스장 A]"
    private static final Pattern FACILITY_NAME = Pattern.compile("\\[([^\\[\\]]{1,40})\\]");

    private static final String BOOKING_PAGE_MARKER = "예약가능시간";

    /**
     * 현황 페이지를 파싱해 30분 단위 응답 DTO 로 변환한다.
     *
     * @param document EUC-KR 로 디코딩된 PSTU420C.php 페이지
     * @param from     조회 기준일 (원본 {@code ThisDate}, 그리드 첫 열)
     * @param facility 조회 대상 시설 (id·이름 fallback 용)
     * @return 30분 슬롯 현황
     */
    public FacilityAvailabilityResponseDto parse(
            final Document document,
            final LocalDate from,
            final Facility facility
    ) {
        // 날짜 → (15분 격자 시작분 → 상태)
        final Map<LocalDate, Map<Integer, SlotStatus>> subSlots = new HashMap<>();
        int minStartMinute = Integer.MAX_VALUE;
        int maxEndMinute = Integer.MIN_VALUE;

        for (final Element cell : document.select("td[onclick]")) {
            final Interval interval = readInterval(cell);
            if (interval == null) {
                continue;
            }
            minStartMinute = Math.min(minStartMinute, interval.startMinute());
            maxEndMinute = Math.max(maxEndMinute, interval.endMinute());

            final Map<Integer, SlotStatus> daySlots =
                    subSlots.computeIfAbsent(interval.date(), key -> new HashMap<>());
            for (int minute = interval.startMinute(); minute < interval.endMinute(); minute += SUB_SLOT_MINUTES) {
                daySlots.merge(minute, interval.status(), this::stronger);
            }
        }

        final String pageText = document.text();
        if (subSlots.isEmpty() && !pageText.contains(BOOKING_PAGE_MARKER)) {
            log.warn("[시설 현황 파싱 실패] 슬롯 셀·쿼터 문구를 모두 찾지 못함 (구조 변경 의심) facilityId={}", facility.code());
            throw new CustomException(ExceptionCode.FACILITY_PARSING_FAILED);
        }

        warnIfOutsideWindow(subSlots.keySet(), from);

        final Integer operatingStart = subSlots.isEmpty() ? null : minStartMinute;
        final Integer operatingEnd = subSlots.isEmpty() ? null : maxEndMinute;

        final List<FacilityDayAvailabilityDto> days = new ArrayList<>();
        for (int i = 0; i < WINDOW_DAYS; i++) {
            final LocalDate date = from.plusDays(i);
            days.add(new FacilityDayAvailabilityDto(
                    date.format(DATE),
                    date.getDayOfWeek().name().substring(0, 3),
                    mergeToHalfHour(subSlots.getOrDefault(date, Map.of()), operatingStart, operatingEnd)));
        }

        return new FacilityAvailabilityResponseDto(
                facility.code(),
                parseFacilityName(pageText, facility),
                SLOT_MINUTES,
                formatMinutes(operatingStart),
                formatMinutes(operatingEnd),
                from.format(DATE),
                from.plusDays(WINDOW_DAYS - 1L).format(DATE),
                parseQuota(pageText),
                days);
    }

    // ------------------------------------------------------------------
    // 셀 → 구간
    // ------------------------------------------------------------------

    private Interval readInterval(final Element cell) {
        final String onclick = cell.attr("onclick");

        final Matcher available = SELECT_DATE.matcher(onclick);
        if (available.find()) {
            return toInterval(available, SlotStatus.AVAILABLE);
        }

        final Matcher reserved = VIEW_INFO.matcher(onclick);
        if (reserved.find()) {
            final SlotStatus status = cell.hasClass("bookingactNoMy")
                    ? SlotStatus.RESERVED_MINE
                    : SlotStatus.RESERVED_OTHER;
            return toInterval(reserved, status);
        }

        return null;
    }

    private Interval toInterval(final Matcher matcher, final SlotStatus status) {
        final int start = toMinutes(matcher.group(2));
        final int end = toMinutes(matcher.group(3));
        if (start < 0 || end <= start || end > MINUTES_PER_DAY) {
            return null;
        }
        return new Interval(LocalDate.parse(matcher.group(1), DATE), start, end, status);
    }

    /** "0600"/"600" → 360, "2400" → 1440 */
    private int toMinutes(final String rawTime) {
        final String padded = rawTime.length() == 3 ? "0" + rawTime : rawTime;
        final int hour = Integer.parseInt(padded.substring(0, 2));
        final int minute = Integer.parseInt(padded.substring(2, 4));
        return hour * 60 + minute;
    }

    // ------------------------------------------------------------------
    // 15분 → 30분 병합
    // ------------------------------------------------------------------

    private List<FacilitySlotDto> mergeToHalfHour(
            final Map<Integer, SlotStatus> daySubSlots,
            final Integer operatingStart,
            final Integer operatingEnd
    ) {
        if (operatingStart == null || operatingEnd == null) {
            return List.of();
        }

        final List<FacilitySlotDto> slots = new ArrayList<>();
        for (int start = operatingStart; start + SLOT_MINUTES <= operatingEnd; start += SLOT_MINUTES) {
            final SlotStatus first = daySubSlots.get(start);
            final SlotStatus second = daySubSlots.get(start + SUB_SLOT_MINUTES);

            slots.add(new FacilitySlotDto(
                    formatMinutes(start),
                    formatMinutes(start + SLOT_MINUTES),
                    merge(first, second)));
        }

        return slots;
    }

    /**
     * 30분을 이루는 두 15분 칸의 상태를 하나로 합친다.
     * RESERVED_MINE &gt; RESERVED_OTHER &gt; UNAVAILABLE(=null 포함) &gt; AVAILABLE.
     */
    private SlotStatus merge(final SlotStatus first, final SlotStatus second) {
        if (first == SlotStatus.RESERVED_MINE || second == SlotStatus.RESERVED_MINE) {
            return SlotStatus.RESERVED_MINE;
        }
        if (first == SlotStatus.RESERVED_OTHER || second == SlotStatus.RESERVED_OTHER) {
            return SlotStatus.RESERVED_OTHER;
        }
        if (first == SlotStatus.AVAILABLE && second == SlotStatus.AVAILABLE) {
            return SlotStatus.AVAILABLE;
        }
        return SlotStatus.UNAVAILABLE;
    }

    /** 같은 15분 칸에 상태가 겹치면 더 강한 제약을 남긴다. */
    private SlotStatus stronger(final SlotStatus a, final SlotStatus b) {
        return priority(a) >= priority(b) ? a : b;
    }

    private int priority(final SlotStatus status) {
        return switch (status) {
            case RESERVED_MINE -> 3;
            case RESERVED_OTHER -> 2;
            case UNAVAILABLE -> 1;
            case AVAILABLE -> 0;
        };
    }

    // ------------------------------------------------------------------
    // 이름 / 쿼터 / 기타
    // ------------------------------------------------------------------

    /** 분 → "HH:mm" (1440 → "24:00", null → null) */
    private String formatMinutes(final Integer minutes) {
        if (minutes == null) {
            return null;
        }
        return "%02d:%02d".formatted(minutes / 60, minutes % 60);
    }

    private String parseFacilityName(final String pageText, final Facility facility) {
        final Matcher matcher = FACILITY_NAME.matcher(pageText);
        if (matcher.find()) {
            final String name = matcher.group(1).trim();
            if (!name.isEmpty()) {
                return name;
            }
        }
        return facility.displayName();
    }

    private FacilityQuotaDto parseQuota(final String pageText) {
        try {
            final Matcher pair = QUOTA_PAIR.matcher(pageText);

            Integer totalRemaining = null;
            Integer totalLimit = null;
            Integer dailyRemaining = null;
            Integer dailyLimit = null;

            if (pair.find()) {
                totalRemaining = minutes(pair.group(1), pair.group(2));
                totalLimit = minutes(pair.group(3), pair.group(4));
            }
            if (pair.find()) {
                dailyRemaining = minutes(pair.group(1), pair.group(2));
                dailyLimit = minutes(pair.group(3), pair.group(4));
            }

            String resetDate = null;
            final Matcher reset = RESET_DATE.matcher(pageText);
            if (reset.find()) {
                resetDate = reset.group(1);
            }

            if (totalRemaining == null && dailyRemaining == null && resetDate == null) {
                return null;
            }

            return new FacilityQuotaDto(totalRemaining, totalLimit, dailyRemaining, dailyLimit, resetDate);
        } catch (final RuntimeException ex) {
            log.warn("[시설 쿼터 파싱 실패] 문구 변경 의심", ex);
            return null;
        }
    }

    private Integer minutes(final String hours, final String mins) {
        return Integer.parseInt(hours) * 60 + (mins != null ? Integer.parseInt(mins) : 0);
    }

    private void warnIfOutsideWindow(final Iterable<LocalDate> dates, final LocalDate from) {
        final LocalDate to = from.plusDays(WINDOW_DAYS - 1L);
        for (final LocalDate date : dates) {
            if (date.isBefore(from) || date.isAfter(to)) {
                log.warn("[시설 현황] 그리드가 요청 기준일 밖의 날짜를 포함함 from={} to={} got={} "
                        + "(원본이 창을 이동했을 수 있음)", from, to, date);
                return;
            }
        }
    }

    /** 원본 한 칸이 나타내는 예약 가능/불가 구간. */
    private record Interval(LocalDate date, int startMinute, int endMinute, SlotStatus status) { }
}
