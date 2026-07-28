package com.profetai.industrymap.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 公司識別碼類型（design D5）。代號不是單一欄位能裝的東西：
 * 類型不只一種、數量不只一個（台積電有 TWSE 2330 與 NYSE TSM），且可能一個都沒有。
 *
 * <p>交易所資訊只由本類型承載，識別碼值只放代號本身；
 * 值中不得內嵌 {@code HKEX:} 這類前綴，否則 {@code unique(type, value)} 的語意會被稀釋，
 * 也無法依交易所查詢。遇到尚無對應類型的交易所時，正確做法是擴充本列舉，
 * 而非以 {@link #OTHER} 加自訂前綴登記。</p>
 */
@Schema(description = """
        公司識別碼類型。交易所類型：TWSE（台灣證交所）、TPEX（櫃買中心）、TSE（東京）、\
        NASDAQ、NYSE、HKEX（香港）、FSE（法蘭克福）、KRX（韓國）、SSE（上海）、SZSE（深圳）、LSE（倫敦）；\
        非交易所類型：TAX_ID（統一編號）、DUNS、OTHER。\
        值只填代號本身，交易所資訊由類型承載，不得寫成 HKEX:6088 這種前綴形式。""")
public enum IdentifierType {

    /** 台灣證券交易所代號 */
    TWSE,

    /** 證券櫃檯買賣中心（上櫃）代號 */
    TPEX,

    /** 東京證券交易所代號 */
    TSE,

    /** NASDAQ 代號 */
    NASDAQ,

    /** NYSE 代號 */
    NYSE,

    /** 香港交易所股份代號 */
    HKEX,

    /** 法蘭克福證券交易所代號 */
    FSE,

    /** 韓國交易所代號 */
    KRX,

    /** 上海證券交易所代號 */
    SSE,

    /** 深圳證券交易所代號 */
    SZSE,

    /** 倫敦證券交易所代號 */
    LSE,

    /** 統一編號 / 稅籍編號 */
    TAX_ID,

    /** DUNS 編號 */
    DUNS,

    /**
     * 其他未列舉的識別碼類型。
     *
     * <p>只適用於<b>非交易所</b>的識別體系，且該體系未被既有類型涵蓋。
     * 公司在某交易所掛牌但本列舉尚無對應值時，不得改用本類型搭配自訂前綴，
     * 而應擴充本列舉——那是純 Java 改動，不需要 schema migration
     * （{@code identifier_type} 為 VARCHAR 且無 check constraint）。</p>
     */
    OTHER
}
