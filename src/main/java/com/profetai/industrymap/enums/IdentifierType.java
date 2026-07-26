package com.profetai.industrymap.enums;

/**
 * 公司識別碼類型（design D5）。代號不是單一欄位能裝的東西：
 * 類型不只一種、數量不只一個（台積電有 TWSE 2330 與 NYSE TSM），且可能一個都沒有。
 */
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

    /** 統一編號 / 稅籍編號 */
    TAX_ID,

    /** DUNS 編號 */
    DUNS,

    /** 其他未列舉的識別碼類型 */
    OTHER
}
