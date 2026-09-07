package com.seohamin.hisnetmobile.domain.meal.service;

import com.seohamin.hisnetmobile.domain.meal.constant.Cafeteria;
import com.seohamin.hisnetmobile.domain.meal.dto.CafeteriaMealDto;
import com.seohamin.hisnetmobile.domain.meal.dto.MealCornerDto;
import com.seohamin.hisnetmobile.domain.meal.dto.MealResponseDto;
import com.seohamin.hisnetmobile.domain.meal.dto.MealSlotDto;
import com.seohamin.hisnetmobile.global.exception.CustomException;
import com.seohamin.hisnetmobile.global.exception.constants.ExceptionCode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 로그인 페이지({@code /login/login.php})에 서버사이드로 렌더링된 당일 식단 위젯을 파싱하는 컴포넌트.
 * <p>
 * 위젯은 레거시 테이블 레이아웃이고 구조가 식당마다 제각각이라, 클래스명 대신 <b>고정 id</b>를 앵커로 쓴다.
 * <ul>
 *   <li>위젯 루트: {@code #tr_box_11}</li>
 *   <li>식당 패널: {@code #tr_box11_1}(학생식당) ~ {@code #tr_box11_4}(더그레이스테이블)</li>
 *   <li>학생식당 코너 탭: {@code div#tKOREA}(든든한동) {@code #tFLY}(H:plate) {@code #tNOODLE}(Asian Market)
 *       {@code #tGRACE}(Han's Deli) {@code #tMIX}(따스한동) — {@code #tHAO} 는 주석 처리돼 DOM 에 없다</li>
 *   <li>학생식당 코너 본문: {@code #tr_box11_001} ~ {@code #tr_box11_006} (탭과 1:1, 004 는 미사용)</li>
 * </ul>
 * 메뉴 원문은 화면 텍스트가 "..." 로 잘려 있어 신뢰할 수 없고, 셀(또는 행)의 {@code title} 속성에
 * 개행으로 구분된 전체 메뉴가 들어있다. 그걸 우선으로 읽는다.
 */
@Component
@Slf4j
public class MealParser {

    // 위젯 루트 / 식당 패널
    private static final String WIDGET_ROOT_SELECTOR = "#tr_box_11";
    private static final String PANEL_ID_PREFIX = "#tr_box11_";

    // 패널 안에서 "끼니 헤더"(아침/점심/저녁 등) 셀
    private static final String SLOT_HEADER_SELECTOR = "td.cls_td_food_title";

    // 메뉴 데이터 행: 항상 td.cls_td_food_text 안의 tr[align=center]
    private static final String DATA_ROW_SELECTOR = "td.cls_td_food_text tr[align=center]";

    // 학생식당 코너 탭 div id → 코너 본문 행 id (setFood(1..6) 순서, 004=미사용 코너라 제외)
    private static final Map<String, String> STUDENT_CORNER_ROW_BY_TAB = new LinkedHashMap<>();
    // 코너 탭 div 가 비어 있을 때 쓰는 표시명 폴백
    private static final Map<String, String> STUDENT_CORNER_FALLBACK_NAME = new LinkedHashMap<>();

    static {
        STUDENT_CORNER_ROW_BY_TAB.put("tKOREA", "tr_box11_001");
        STUDENT_CORNER_ROW_BY_TAB.put("tFLY", "tr_box11_002");
        STUDENT_CORNER_ROW_BY_TAB.put("tNOODLE", "tr_box11_003");
        STUDENT_CORNER_ROW_BY_TAB.put("tGRACE", "tr_box11_005");
        STUDENT_CORNER_ROW_BY_TAB.put("tMIX", "tr_box11_006");

        STUDENT_CORNER_FALLBACK_NAME.put("tKOREA", "든든한동");
        STUDENT_CORNER_FALLBACK_NAME.put("tFLY", "H:plate");
        STUDENT_CORNER_FALLBACK_NAME.put("tNOODLE", "Asian Market");
        STUDENT_CORNER_FALLBACK_NAME.put("tGRACE", "Han's Deli");
        STUDENT_CORNER_FALLBACK_NAME.put("tMIX", "따스한동");
    }

    // 점심 전용 코너(H:plate 등)는 끼니 헤더가 없어서 이 라벨로 묶는다
    private static final String DEFAULT_SLOT = "점심";

    // 인쇄 아이콘 URL(dung_print...png?time='2026-09-07')에서 식단 기준일 추출
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    // 줄 끝에 붙는 " -  - " (빈 2~3번째 칸 자리표시) 제거용.
    // 대시 앞의 공백을 필수로 둬서 "…참조-" 처럼 단어에 붙은 대시는 건드리지 않는다.
    private static final Pattern TRAILING_DASHES = Pattern.compile("(\\s+-)+\\s*$");
    // 끼니 헤더에 붙는 시간 안내 "(7:30am~9:30am)" 제거용
    private static final Pattern PARENTHETICAL = Pattern.compile("\\s*\\([^)]*\\)");
    // 개행 / <br> 로 메뉴 줄 나누기
    private static final Pattern LINE_SPLIT = Pattern.compile("\\r?\\n|<br\\s*/?>", Pattern.CASE_INSENSITIVE);
    // 공백류(일반/전각/비분리) 정규화
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00a0\\u3000]+");

    /**
     * 로그인 페이지 Document 를 파싱해 식단 응답으로 변환하는 메서드.
     * @param document EUC-KR 로 디코딩된 로그인 페이지
     * @return 식단표
     */
    public MealResponseDto parse(final Document document) {

        final Element widget = document.selectFirst(WIDGET_ROOT_SELECTOR);
        final Element scope = widget != null ? widget : document;

        final List<CafeteriaMealDto> cafeterias = new ArrayList<>();
        for (final Cafeteria cafeteria : Cafeteria.values()) {
            final Element panel = scope.selectFirst(PANEL_ID_PREFIX + cafeteria.panelIndex());
            if (panel == null) {
                continue;
            }

            final List<MealCornerDto> corners = cafeteria == Cafeteria.STUDENT
                    ? parseStudentCorners(panel)
                    : parseSingleCorner(panel, cafeteria.displayName());

            if (!corners.isEmpty()) {
                cafeterias.add(new CafeteriaMealDto(cafeteria.name(), cafeteria.displayName(), corners));
            }
        }

        if (cafeterias.isEmpty()) {
            log.warn("[식단 파싱 실패] 식당 패널에서 메뉴를 하나도 찾지 못함 (구조 변경 의심) / widget={}", widget != null);
            throw new CustomException(ExceptionCode.MEAL_PARSING_FAILED);
        }

        return new MealResponseDto(extractDate(document), cafeterias);
    }

    /**
     * 학생식당 패널(#tr_box11_1)의 코너별 메뉴를 뽑는 메서드.
     * 코너 탭 div(#tKOREA 등) 순서대로 대응하는 본문 행(#tr_box11_00N)을 파싱한다.
     */
    private List<MealCornerDto> parseStudentCorners(final Element panel) {

        final List<MealCornerDto> corners = new ArrayList<>();
        for (final Map.Entry<String, String> entry : STUDENT_CORNER_ROW_BY_TAB.entrySet()) {
            final String tabId = entry.getKey();
            final Element row = panel.selectFirst("#" + entry.getValue());
            if (row == null) {
                continue;
            }

            final Element tab = panel.selectFirst("#" + tabId);
            final String name = tab != null && !tab.text().isBlank()
                    ? tab.text().trim()
                    : STUDENT_CORNER_FALLBACK_NAME.get(tabId);

            corners.addAll(parseSingleCorner(row, name));
        }

        return corners;
    }

    /**
     * 코너/식당 하나의 메뉴 표(scope) 를 파싱해 0~1개의 코너 DTO 로 변환하는 메서드.
     * <ul>
     *   <li>끼니 헤더 셀(td.cls_td_food_title)이 있으면: 헤더와 데이터 셀을 위치로 짝지어 끼니별로 나눈다
     *       (든든한동 / 말스키친 / 한동라운지 / 더그레이스테이블)</li>
     *   <li>헤더가 없으면: 점심 전용 코너로 보고 데이터 행 전체를 {@code 점심} 한 끼로 묶는다
     *       (H:plate / Asian Market / Han's Deli / 따스한동)</li>
     * </ul>
     */
    private List<MealCornerDto> parseSingleCorner(
            final Element scope,
            final String cornerName
    ) {
        final Element dataRow = scope.selectFirst(DATA_ROW_SELECTOR);
        if (dataRow == null) {
            return List.of();
        }

        final List<String> headers = new ArrayList<>();
        for (final Element headerCell : scope.select(SLOT_HEADER_SELECTOR)) {
            headers.add(cleanHeader(headerCell.text()));
        }

        final List<MealSlotDto> meals = new ArrayList<>();

        if (headers.isEmpty()) {
            // 헤더 없는 코너: 메뉴 전체가 행 자체의 title 에 개행으로 들어있다 (셀 텍스트는 "..." 로 잘림)
            final List<String> items = menuItems(
                    dataRow.hasAttr("title") ? dataRow.attr("title") : dataRow.wholeText());
            if (!items.isEmpty()) {
                meals.add(new MealSlotDto(DEFAULT_SLOT, items));
            }
        } else {
            final Elements cells = dataRow.select("td");
            for (int i = 0; i < cells.size(); i++) {
                final Element cell = cells.get(i);
                final List<String> items = menuItems(
                        cell.hasAttr("title") && !cell.attr("title").isBlank()
                                ? cell.attr("title")
                                : cell.wholeText());
                if (items.isEmpty()) {
                    continue;
                }
                final String slot = i < headers.size() && !headers.get(i).isBlank()
                        ? headers.get(i)
                        : "메뉴" + (i + 1);
                meals.add(new MealSlotDto(slot, items));
            }
        }

        return meals.isEmpty() ? List.of() : List.of(new MealCornerDto(cornerName, meals));
    }

    /**
     * title/텍스트 원문을 메뉴 항목 리스트로 쪼개는 메서드.
     * 개행(또는 &lt;br&gt;) 으로 나누고, 줄 끝의 " -  - " 자리표시와 빈 줄/외톨이 대시를 버리며, 연속 중복을 접는다.
     */
    private List<String> menuItems(final String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        final List<String> items = new ArrayList<>();
        for (final String line : LINE_SPLIT.split(raw, -1)) {
            String item = WHITESPACE.matcher(line).replaceAll(" ").trim();
            item = TRAILING_DASHES.matcher(item).replaceAll("").trim();
            if (item.isEmpty() || item.equals("-")) {
                continue;
            }
            if (items.isEmpty() || !items.get(items.size() - 1).equals(item)) {
                items.add(item);
            }
        }

        return items;
    }

    /**
     * 끼니 헤더 텍스트 정리: 여러 공백을 하나로, 시간 안내 "(7:30am~9:30am)" 제거.
     */
    private String cleanHeader(final String raw) {
        if (raw == null) {
            return "";
        }
        final String withoutParen = PARENTHETICAL.matcher(raw).replaceAll("");
        return WHITESPACE.matcher(withoutParen).replaceAll(" ").trim();
    }

    /**
     * 식단 기준일을 뽑는 메서드. 위젯 인쇄 아이콘 URL 의 {@code ?time='YYYY-MM-DD'} 를 쓰고,
     * 없으면 서버(KST) 기준 오늘로 폴백한다.
     */
    private LocalDate extractDate(final Document document) {
        for (final Element img : document.select("img[src*=dung_print]")) {
            final Matcher matcher = DATE_PATTERN.matcher(img.attr("src"));
            if (matcher.find()) {
                try {
                    return LocalDate.of(
                            Integer.parseInt(matcher.group(1)),
                            Integer.parseInt(matcher.group(2)),
                            Integer.parseInt(matcher.group(3)));
                } catch (final RuntimeException ignored) {
                    // 형식이 이상하면 폴백으로
                }
            }
        }

        return LocalDate.now(KST);
    }
}
