## ADDED Requirements

### Requirement: 公司識別碼類型須涵蓋主要境外交易所

系統 SHALL 為公司實際會登記的交易所提供對應的識別碼類型，
使呼叫端能以「交易所類型 + 該交易所的證券代號」登記，不需在值中自行編碼交易所。

`identifierValue` MUST NOT 內嵌交易所名稱或前綴——交易所資訊 MUST 只由 `identifierType` 承載。

#### Scenario: 登記香港交易所上市公司
- **WHEN** 以香港交易所類型與該公司股份代號登記識別碼
- **THEN** 系統 SHALL 建立成功，且 `identifierValue` 只含代號本身

#### Scenario: 登記法蘭克福交易所上市公司
- **WHEN** 以法蘭克福交易所類型與該公司代號登記識別碼
- **THEN** 系統 SHALL 建立成功，且 `identifierValue` 只含代號本身

#### Scenario: 值中不得內嵌交易所前綴
- **WHEN** 檢視任一已登記的識別碼
- **THEN** 其 `identifierValue` MUST NOT 含交易所名稱前綴（如 `HKEX:`、`FSE:`）

### Requirement: `OTHER` 類型的適用界線

`OTHER` SHALL 只用於**非交易所**的識別體系，且該體系未被既有類型涵蓋。

當公司在某個交易所掛牌、而系統尚無對應的識別碼類型時，
MUST NOT 以 `OTHER` 搭配自訂前綴登記，而 SHALL 提出擴充識別碼類型。

#### Scenario: 交易所缺類型時不得以 OTHER 代替
- **WHEN** 需要登記一個系統尚無對應類型的交易所代號
- **THEN** 正確做法 SHALL 是擴充識別碼類型，MUST NOT 以 `OTHER` 加前綴寫入

#### Scenario: 非交易所識別體系可用 OTHER
- **WHEN** 需要登記一個既非交易所代號、也不屬於既有類型的識別碼
- **THEN** 系統 SHALL 允許以 `OTHER` 登記

### Requirement: 既有以 OTHER 登記的交易所識別碼須轉為正規類型

系統 MUST 將既有以 `OTHER` 搭配交易所前綴登記的識別碼，
轉換為對應的交易所類型，並移除值中的前綴。轉換 MUST NOT 改變該識別碼所指向的公司。

#### Scenario: 轉換後可用純代號查詢
- **WHEN** 以轉換後的純代號查詢該公司
- **THEN** 系統 SHALL 回傳原本的公司

#### Scenario: 轉換不影響公司的其他資料
- **WHEN** 識別碼完成轉換
- **THEN** 該公司的名稱、別名、供應角色與審核狀態 MUST NOT 被變更

#### Scenario: 轉換後不留下 OTHER 前綴資料
- **WHEN** 轉換完成後檢視所有識別碼
- **THEN** MUST NOT 存在 `identifierType` 為 `OTHER` 且值含交易所前綴的資料
