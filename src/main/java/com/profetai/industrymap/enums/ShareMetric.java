package com.profetai.industrymap.enums;

/**
 * 市佔率口徑（design D7）。同一組公司零件在營收與出貨量兩種口徑下排名可能完全不同，
 * 因此口徑是市佔率的必要維度，不可省略。
 */
public enum ShareMetric {

    /** 營收口徑 */
    REVENUE,

    /** 出貨量口徑 */
    VOLUME
}
