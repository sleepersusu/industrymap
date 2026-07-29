package com.profetai.industrymap.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 熱區多邊形的一個頂點，座標為相對於圖片寬高的比例（design D5）。
 *
 * <p>不存絕對像素：像素會把熱區綁死在某一次的圖片解析度上，換圖或在手機上縮放就整片錯位。</p>
 *
 * <p>{@code implements Serializable} 是刻意的（{@code .claude/rules/code-style.md}）：
 * 這個 POJO 以 JSON 形式持久化，而 Hibernate 的 dirty-check 在某些設定下會以 Java 序列化
 * deep-clone 欄位值，物件圖任一層漏實作就會在存檔時炸成 500。依慣例不加
 * {@code serialVersionUID}。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HotspotPoint implements Serializable {

    /** 相對 X 座標，0 為圖片最左、1 為最右 */
    private double x;

    /** 相對 Y 座標，0 為圖片最上、1 為最下 */
    private double y;
}
