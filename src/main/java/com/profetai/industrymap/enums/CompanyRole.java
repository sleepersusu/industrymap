package com.profetai.industrymap.enums;

/**
 * 公司對零件扮演的角色（design D6）。
 *
 * <p>同一顆晶片可能由聯發科設計、台積電製造、日月光封測，
 * 只有一條「做這個零件」的關係會讓三家長得一樣，因此關係必須帶角色。
 * 原料供應不另設角色——由組成關係的遞迴路徑推導即可。</p>
 */
public enum CompanyRole {

    /** 設計 */
    DESIGN,

    /** 製造 */
    MANUFACTURE,

    /** 代工組裝 */
    ASSEMBLY,

    /** 品牌 */
    BRAND,

    /** 封裝測試 */
    PACKAGING_TESTING
}
