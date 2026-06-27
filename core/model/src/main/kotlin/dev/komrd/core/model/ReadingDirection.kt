package dev.komrd.core.model

enum class ReadingDirection {
    LEFT_TO_RIGHT,
    RIGHT_TO_LEFT,
    VERTICAL,
    WEBTOON,
    ;

    /** RTLならtrue。横ページャの`reverseLayout`へ渡す。 */
    val isRtl: Boolean get() = this == RIGHT_TO_LEFT

    /** 横ページャ(LTR/RTL)ならtrue。表示モード切替で使用。 */
    val isHorizontal: Boolean get() = this == LEFT_TO_RIGHT || this == RIGHT_TO_LEFT
}
