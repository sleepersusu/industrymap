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

