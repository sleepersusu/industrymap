## ADDED Requirements

### Requirement: 供應關係與市佔率以公司代號指定公司

建立供應角色與市佔率時，呼叫端 SHALL 以公司代號指定公司，系統 MUST NOT 要求呼叫端提供
公司的內部識別碼。查無對應代號時 MUST 回傳 404。

#### Scenario: 以公司代號建立供應角色
- **WHEN** 以公司代號 5306 與零件識別碼建立供應角色
- **THEN** 系統 SHALL 解析出對應公司並建立關係

#### Scenario: 以公司代號建立市佔率
- **WHEN** 以公司代號與零件識別碼建立市佔率
- **THEN** 系統 SHALL 解析出對應公司並寫入

#### Scenario: 查無公司代號
- **WHEN** 以不存在的公司代號建立供應角色
- **THEN** 系統 SHALL 回傳 404

#### Scenario: 建立公司後可直接建立供應關係
- **WHEN** 先以 API 建立一家公司並取得其回應，再以回應中的代號建立供應角色
- **THEN** 系統 SHALL 成功建立，全程 MUST NOT 需要查詢資料庫取得內部識別碼
