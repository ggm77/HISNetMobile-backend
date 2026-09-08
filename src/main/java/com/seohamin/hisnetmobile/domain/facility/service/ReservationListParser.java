package com.seohamin.hisnetmobile.domain.facility.service;

import com.seohamin.hisnetmobile.domain.facility.dto.ReservationDto;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 예약/초대내역조회 페이지(PSTU420L.php)의 "예약내역" 표를 파싱하는 컴포넌트.
 * <p>
 * 하위 탭마다 표 구조가 다르다(2026-09-08 실측):
 * <ul>
 *   <li><b>진행중</b>(gubun=0): {@code No | 제 목 | 장 소 | 일 시 | 예약취소가능기간 | 바로가기} 6칸.
 *       바로가기 칸의 참석자등록/예약변경/예약취소 링크에서 예약 코드({@code bcode})를 뽑는다
 *       ({@code BookinCancel(755844)} / {@code ...?bcode=755844}).</li>
 *   <li><b>사용완료·취소·미사용취소</b>(gubun=1/2/3): {@code No | 제 목 | 장 소 | 일 시} 4칸.
 *       링크가 없어 {@code bookingCode}·{@code cancelableUntil} 은 null 이다.</li>
 * </ul>
 * 원본은 라벨에 자간용 공백을 넣고("제 목"), 바로가기 칸의 버튼은 중첩 표라 {@code row.select("td")} 는
 * 하위 셀까지 긁는다 — 그래서 헤더는 공백 제거 후 비교하고, 행은 <b>직속 셀만</b> 본다.
 * <p>
 * 헤더/구조가 바뀌어 표를 못 찾으면 빈 목록으로 본다(로그인 만료는 {@code HisnetClient} 가 먼저 걸러낸다).
 */
@Component
@Slf4j
public class ReservationListParser {

    private static final Pattern BOOKING_CODE = Pattern.compile("BookinCancel\\((\\d+)\\)");
    private static final Pattern BCODE_PARAM = Pattern.compile("bcode=(\\d+)");
    private static final Pattern ROW_NO = Pattern.compile("^\\d+$");

    // "2026.05.30(토) 15:00 ~ 18:00"
    private static final Pattern DATE = Pattern.compile("(\\d{4})\\.(\\d{2})\\.(\\d{2})");
    private static final Pattern TIME_RANGE = Pattern.compile("(\\d{1,2}:\\d{2})\\s*~\\s*(\\d{1,2}:\\d{2})");
    // 예약취소가능기간 "~ 2026.05.30 14:00"
    private static final Pattern DATE_TIME =
            Pattern.compile("(\\d{4})\\.(\\d{2})\\.(\\d{2})\\s+(\\d{1,2}:\\d{2})");

    /**
     * 예약내역 페이지를 파싱해 예약 목록으로 변환한다.
     *
     * @param document EUC-KR 로 디코딩된 PSTU420L.php 페이지
     * @return 예약 내역 (원본 표기 순서 유지, 없으면 빈 리스트)
     */
    public List<ReservationDto> parse(final Document document) {
        final Element table = findReservationTable(document);
        if (table == null) {
            log.warn("[예약 내역 파싱] '제목/장소/일시' 헤더 표를 찾지 못함 (구조 변경 의심)");
            return List.of();
        }

        final List<ReservationDto> reservations = new ArrayList<>();
        for (final Element row : table.select("tr")) {
            final ReservationDto reservation = parseRow(row);
            if (reservation != null) {
                reservations.add(reservation);
            }
        }

        return reservations;
    }

    /**
     * 헤더 행({@code No | 제 목 | 장 소 | 일 시 …})을 가진 표를 찾는다.
     * <p>
     * 자간용 공백을 지우고 {@code 장소}·{@code 일시} 로 판별한다("바로가기" 는 진행중 탭에만 있어 쓰지 않는다).
     * 헤더 행(직속 셀 4~8개)을 직접 찾아 그 표를 반환한다(바깥 레이아웃 표가 오탐되지 않게).
     */
    private Element findReservationTable(final Document document) {
        for (final Element row : document.select("tr")) {
            final int cellCount = row.children().size();
            if (cellCount < 4 || cellCount > 8) {
                continue;
            }
            final String stripped = row.text().replaceAll("\\s+", "");
            if (stripped.contains("장소") && stripped.contains("일시")) {
                return row.closest("table");
            }
        }
        return null;
    }

    private ReservationDto parseRow(final Element row) {
        // 이 행의 직속 셀만 본다 (바로가기 칸 버튼이 중첩 표라 row.select("td") 는 하위 셀까지 긁힘):
        // 진행중  No | 제목 | 장소 | 일시 | 예약취소가능기간 | 바로가기
        // 이력    No | 제목 | 장소 | 일시
        final Elements cells = row.children();
        if (cells.size() < 4) {
            return null;
        }
        if (!ROW_NO.matcher(cells.first().text().trim()).matches()) {
            return null;
        }

        // 진행중 탭에서만 링크로 예약 코드를 얻는다. 이력 탭은 null.
        final Long bookingCode = extractBookingCode(row.html());

        final String titleText = cells.get(1).text().trim();
        final String placeText = cells.get(2).text().trim();
        final String dateTimeText = cells.get(3).text();
        final String cancelText = cells.size() > 4 ? cells.get(4).text() : "";

        final Matcher date = DATE.matcher(dateTimeText);
        final Matcher timeRange = TIME_RANGE.matcher(dateTimeText);
        final boolean hasTime = timeRange.find();

        return new ReservationDto(
                bookingCode,
                titleText.isEmpty() ? null : titleText,
                placeText.isEmpty() ? null : placeText,
                date.find() ? "%s-%s-%s".formatted(date.group(1), date.group(2), date.group(3)) : null,
                hasTime ? normalizeTime(timeRange.group(1)) : null,
                hasTime ? normalizeTime(timeRange.group(2)) : null,
                parseCancelableUntil(cancelText));
    }

    private Long extractBookingCode(final String rowHtml) {
        final Matcher byFn = BOOKING_CODE.matcher(rowHtml);
        if (byFn.find()) {
            return Long.parseLong(byFn.group(1));
        }
        final Matcher byParam = BCODE_PARAM.matcher(rowHtml);
        if (byParam.find()) {
            return Long.parseLong(byParam.group(1));
        }
        return null;
    }

    private String parseCancelableUntil(final String text) {
        final Matcher matcher = DATE_TIME.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return "%s-%s-%s %s".formatted(
                matcher.group(1), matcher.group(2), matcher.group(3), normalizeTime(matcher.group(4)));
    }

    /** "9:00" → "09:00" */
    private String normalizeTime(final String time) {
        final String[] parts = time.split(":");
        return "%02d:%s".formatted(Integer.parseInt(parts[0]), parts[1]);
    }
}
