package com.seohamin.hisnetmobile.domain.meal.constant;

/**
 * 로그인 페이지 식단 위젯의 식당 탭.
 * <p>
 * 위젯은 {@code ShowBox2(1~4)} 로 전환되는 4개 패널이고, 각 패널의 컨테이너 행 id 는
 * {@code #tr_box11_1 ~ #tr_box11_4} 다. {@link #panelIndex} 가 그 숫자다.
 * 학생식당(1)만 코너가 여러 개고, 나머지는 패널 하나에 메뉴 표 하나다.
 */
public enum Cafeteria {

    STUDENT(1, "학생식당"),
    MARS_KITCHEN(2, "말스키친"),
    HANDONG_LOUNGE(3, "한동라운지"),
    GRACE_TABLE(4, "더그레이스테이블");

    private final int panelIndex;
    private final String displayName;

    Cafeteria(final int panelIndex, final String displayName) {
        this.panelIndex = panelIndex;
        this.displayName = displayName;
    }

    public int panelIndex() {
        return panelIndex;
    }

    public String displayName() {
        return displayName;
    }
}
