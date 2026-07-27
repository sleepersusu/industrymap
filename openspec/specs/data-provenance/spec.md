# data-provenance Specification

## Purpose
TBD - created by archiving change add-industry-map-core-model. Update Purpose after archive.
## Requirements
### Requirement: 所有內容資料必須記錄來源

`item`、`item_alias`、`item_composition`、`company`、`company_alias`、`company_identifier`、
`company_item_role`、`market_share` 每一筆 MUST 記錄來源類型（AI 生成、人工建立、外部來源）
與來源明細（模型名稱、報告名稱或 URL）。缺少來源類型 MUST 拒絕寫入。

#### Scenario: AI 生成資料標記來源
- **WHEN** 由 AI 生成流程寫入一筆組成關係
- **THEN** 系統 SHALL 記錄來源類型為 AI 生成，且來源明細包含產生該筆資料的模型識別

#### Scenario: 缺少來源類型
- **WHEN** 寫入任一內容資料但未提供來源類型
- **THEN** 系統 SHALL 拒絕寫入並回傳 400 驗證錯誤

### Requirement: AI 生成資料必須記錄信心度

來源類型為 AI 生成的資料 MUST 帶有信心度數值。人工建立與外部來源的資料 MAY 不帶信心度。

#### Scenario: AI 生成未帶信心度
- **WHEN** 寫入來源類型為 AI 生成的資料但未提供信心度
- **THEN** 系統 SHALL 拒絕寫入並回傳 400 驗證錯誤

#### Scenario: 人工建立不帶信心度
- **WHEN** 寫入來源類型為人工建立的資料且未提供信心度
- **THEN** 系統 SHALL 成功寫入

### Requirement: 審核狀態流轉

每一筆內容資料 MUST 具備審核狀態（草稿、已驗證、已駁回），預設為草稿。
狀態變更為已驗證或已駁回時 MUST 記錄審核者與審核時間。

#### Scenario: 新資料預設為草稿
- **WHEN** 寫入一筆新資料且未指定審核狀態
- **THEN** 系統 SHALL 將其審核狀態設為草稿，且審核者與審核時間為空

#### Scenario: 通過審核
- **WHEN** 將一筆草稿資料標記為已驗證並提供審核者
- **THEN** 系統 SHALL 更新審核狀態並記錄審核者與當下審核時間

#### Scenario: 審核未提供審核者
- **WHEN** 將資料標記為已驗證但未提供審核者
- **THEN** 系統 SHALL 拒絕寫入並回傳 400 驗證錯誤

#### Scenario: 已駁回資料保留不刪除
- **WHEN** 將一筆資料標記為已駁回
- **THEN** 系統 SHALL 保留該筆資料並更新狀態，MUST NOT 實際刪除

### Requirement: 查詢預設排除未通過審核的資料

對外查詢 API SHALL 預設只回傳審核狀態為已驗證的資料，並 MUST 提供明確參數以納入草稿資料。
已駁回的資料 MUST NOT 出現在任何對外查詢結果中。

#### Scenario: 預設查詢只見已驗證資料
- **WHEN** 查詢某零件的供應公司且未指定審核狀態參數
- **THEN** 系統 SHALL 只回傳審核狀態為已驗證的關係

#### Scenario: 明確納入草稿資料
- **WHEN** 查詢時明確指定納入草稿資料
- **THEN** 系統 SHALL 一併回傳草稿與已驗證的資料，且每筆標示其審核狀態

#### Scenario: 已駁回資料不外露
- **WHEN** 查詢時指定納入草稿資料
- **THEN** 系統 SHALL 仍排除審核狀態為已駁回的資料

### Requirement: 審核目標可用自然鍵定位

審核 API SHALL 支援以各資料類型的自然鍵定位目標，呼叫端 MUST NOT 被迫先取得內部自增 id。
內部 id 定位方式 MUST 繼續可用，兩種方式擇一提供即可。

各類型的自然鍵如下：

| 資料類型 | 自然鍵 |
|---|---|
| 品類節點 | 正規化名稱 |
| 節點別名 | 正規化別名 |
| 組成關係 | 上層節點 + 下層節點 |
| 公司 | 公司代號或正規化名稱 |
| 公司別名 | 正規化別名 |
| 公司識別碼 | 識別碼類型 + 識別碼值 |
| 供應角色 | 公司識別 + 零件 + 角色 |
| 市佔率 | 公司識別 + 零件 + 期間 + 地區 + 口徑 + 來源 |

#### Scenario: 以識別碼類型與值審核公司識別碼
- **WHEN** 對一筆草稿狀態的公司識別碼，以識別碼類型與識別碼值指定目標並標記為已驗證
- **THEN** 系統 SHALL 完成審核，呼叫端全程 MUST NOT 需要知道該筆的內部 id

#### Scenario: 以上下層節點審核組成關係
- **WHEN** 以上層節點與下層節點指定一筆組成關係並標記為已驗證
- **THEN** 系統 SHALL 完成審核並回傳更新後的狀態

#### Scenario: 自然鍵查無對應資料
- **WHEN** 以不存在的自然鍵組合指定審核目標
- **THEN** 系統 SHALL 回傳 404

#### Scenario: 同時提供 id 與自然鍵
- **WHEN** 同一筆審核請求同時帶了內部 id 與自然鍵
- **THEN** 系統 SHALL 以內部 id 為準完成審核，MUST NOT 回傳錯誤

#### Scenario: 兩種定位方式都未提供
- **WHEN** 審核請求既未提供內部 id 也未提供自然鍵
- **THEN** 系統 SHALL 回傳 400 驗證錯誤

#### Scenario: 自然鍵欄位不足以定位
- **WHEN** 以市佔率為目標但只提供公司與零件，缺少期間、地區、口徑或來源
- **THEN** 系統 SHALL 回傳 400 驗證錯誤，訊息 MUST 指出缺少哪些維度

#### Scenario: 批次審核混用兩種定位方式
- **WHEN** 一批審核請求中，部分項目用內部 id、部分用自然鍵
- **THEN** 系統 SHALL 逐筆各自解析並完成審核

