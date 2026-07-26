package com.profetai.industrymap.enums;

/**
 * 組成關係的必要性。品類層的組成並非總是成立（單速腳踏車沒有變速器），
 * 因此每筆 part-of 關係都必須標明必要性。
 */
public enum Necessity {

    /** 標配：該品類幾乎必然具備 */
    STANDARD,

    /** 常見：多數情況具備，但非必然 */
    COMMON,

    /** 選配：視型號或需求而定 */
    OPTIONAL
}
