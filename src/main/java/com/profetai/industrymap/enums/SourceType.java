package com.profetai.industrymap.enums;

/**
 * 資料來源類型（design D8）。資料以 AI 生成初稿 + 人工審核方式進來，
 * 每一筆內容資料都必須標明出處，否則日後無從追溯。
 */
public enum SourceType {

    /** AI 生成，必須另帶信心度（confidence） */
    AI_GENERATED,

    /** 人工建立 */
    MANUAL,

    /** 外部來源（研究報告、公開資料集等） */
    EXTERNAL
}
