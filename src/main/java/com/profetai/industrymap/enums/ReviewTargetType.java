package com.profetai.industrymap.enums;

/**
 * 審核端點支援的資料類型白名單（design D1）。
 *
 * <p>審核端點以類型參數驅動，型別安全較弱；以白名單限制可接受的類型，
 * 不在清單內的值在 API 邊界就被擋成 400，而不是進到 service 才炸成 500。</p>
 *
 * <p>清單必須與帶有來源與審核欄位的十張內容表（{@code ProvenanceEntity} 的子類）一致；
 * 日後新增內容表時，這裡與 {@code ReviewLookupService} 的 repository 註冊要一起補。</p>
 */
public enum ReviewTargetType {

    /** 品類節點 */
    ITEM,

    /** 品類節點別名 */
    ITEM_ALIAS,

    /** part-of 組成關係 */
    ITEM_COMPOSITION,

    /** 公司主檔 */
    COMPANY,

    /** 公司別名 */
    COMPANY_ALIAS,

    /** 公司識別碼 */
    COMPANY_IDENTIFIER,

    /** 公司對零件的供應角色 */
    COMPANY_ITEM_ROLE,

    /** 市佔率 */
    MARKET_SHARE,

    /** 品類節點的圖片 */
    ITEM_IMAGE,

    /** 圖片上的可點擊區域 */
    ITEM_HOTSPOT
}
