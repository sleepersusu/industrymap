# company-registry Specification

## Purpose
TBD - created by archiving change add-industry-map-core-model. Update Purpose after archive.
## Requirements
### Requirement: 公司主檔

系統 SHALL 以 `company` 儲存公司基本資料，包含正規化名稱（唯一鍵）、顯示名稱、所屬國家、
是否為公開發行公司。未上市公司 MUST 可正常建立，不得因缺少股票代號而無法登錄。

#### Scenario: 建立未上市公司
- **WHEN** 建立一家沒有任何交易所代號的公司（例：SRAM）
- **THEN** 系統 SHALL 成功建立，且該公司不具備任何識別碼資料

#### Scenario: 正規化名稱重複時拒絕建立
- **WHEN** 嘗試建立正規化名稱與既有公司相同的資料
- **THEN** 系統 SHALL 拒絕寫入並回傳 409 衝突

### Requirement: 公司代號以獨立識別碼表儲存

系統 SHALL 以 `company_identifier` 儲存公司代號，每筆包含類型（交易所代號、統一編號、DUNS 等）、
代號值、是否為主要識別碼。同一家公司 MUST 可擁有多筆識別碼；同一組（類型, 代號值）MUST 全域唯一。

#### Scenario: 多地掛牌公司
- **WHEN** 台積電同時登錄 TWSE 2330 與 NYSE TSM 兩筆識別碼，並將 TWSE 2330 標記為主要
- **THEN** 系統 SHALL 保留兩筆資料，且查詢主要識別碼時回傳 TWSE 2330

#### Scenario: 同類型同代號重複
- **WHEN** 嘗試為另一家公司建立已存在的（TWSE, 2330）識別碼
- **THEN** 系統 SHALL 拒絕寫入並回傳 409 衝突

#### Scenario: 以代號查詢公司
- **WHEN** 以交易所代號 2330 查詢
- **THEN** 系統 SHALL 回傳對應的公司資料

#### Scenario: 多筆主要識別碼
- **WHEN** 嘗試為同一公司標記第二筆主要識別碼
- **THEN** 系統 SHALL 拒絕寫入，確保每家公司至多一筆主要識別碼

### Requirement: 公司別名支援去重與搜尋

系統 SHALL 以 `company_alias` 儲存公司同義詞（例：台積電／TSMC／台灣積體電路製造股份有限公司）。
別名 MUST 可用於查詢既有公司，避免 AI 生成資料時產生重複公司。

#### Scenario: 以別名查得既有公司
- **WHEN** 以「TSMC」查詢，且該詞已登記為台積電的別名
- **THEN** 系統 SHALL 回傳台積電的公司資料

#### Scenario: 別名與既有公司名稱衝突
- **WHEN** 嘗試將某字串登記為別名，但該字串已是另一家公司的正規化名稱
- **THEN** 系統 SHALL 拒絕寫入並回傳 409 衝突

### Requirement: 公司查詢以代號為對外識別

對外 API SHALL 以公司代號作為路徑識別，MUST NOT 於路徑曝露內部自增主鍵。

#### Scenario: 依代號取得公司資料
- **WHEN** 以 `/api/companies/2330` 查詢
- **THEN** 系統 SHALL 回傳台積電的公司資料與其所有識別碼

#### Scenario: 查無此代號
- **WHEN** 以不存在的代號查詢
- **THEN** 系統 SHALL 回傳 404

