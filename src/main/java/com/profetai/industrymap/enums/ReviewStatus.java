package com.profetai.industrymap.enums;

/**
 * 審核狀態（design D8）。新資料一律為 DRAFT，對外查詢預設只回 VERIFIED，
 * REJECTED 保留資料但不外露。
 */
public enum ReviewStatus {

    /** 草稿：尚未經人工審核 */
    DRAFT,

    /** 已驗證：通過人工審核，對外查詢的預設範圍 */
    VERIFIED,

    /** 已駁回：保留紀錄但不出現在任何對外查詢結果 */
    REJECTED
}
