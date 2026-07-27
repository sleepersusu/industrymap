## 1. 擴充 `IdentifierType`（design D1）

- [ ] 1.1 確認 `identifier_type` 於資料庫為 `VARCHAR(32)` 且無 check constraint、entity 為 `@Enumerated(EnumType.STRING)`（design D2 已查證，實作前再確認一次），據此確認新增列舉值不需 schema migration
- [ ] 1.2 先寫失敗測試：以香港交易所類型與純代號登記識別碼應成功；再新增 `HKEX` 列舉值
- [ ] 1.3 先寫失敗測試：以法蘭克福交易所類型與純代號登記識別碼應成功；再新增 `FSE` 列舉值
- [ ] 1.4 一併新增 `KRX`、`SSE`、`SZSE`、`LSE`（design D1 的「明顯即將遇到」清單），並補 `@Schema` 說明各類型對應的交易所
- [ ] 1.5 先寫失敗測試：`unique(type, value)` 在不同交易所類型下同一數字值應可各自登記（例如 `HKEX/0992` 與 `SSE/0992` 並存）

## 2. 轉換既有資料（design D2、D3）

- [ ] 2.1 **先查實際資料**：列出所有 `identifier_type = 'OTHER'` 的識別碼，確認待轉換筆數與值的實際格式（不可依本文件記載的 3 筆推論）
- [ ] 2.2 **驗證轉換不會違反唯一鍵**：確認轉換後的 `(type, value)` 組合都不與既有資料衝突；若有衝突，停下來重查資料現況，MUST NOT 強改
- [ ] 2.3 查主線 `db/migration` 最新一支的版號，據此訂新 migration 的日期版號（`.claude/rules/flyway.md`；不可憑空假設）
- [ ] 2.4 撰寫 data migration：以 `identifier_type = 'OTHER'` 加特定 `identifier_value` 為條件定位，改寫 type 與 value；**只含 `UPDATE`，不含任何 DDL**，且不得使用內部自增 id
- [ ] 2.5 先寫失敗測試（`@Tag("integration")`，走 Flyway）：migration 執行後，以轉換後的純代號查詢應回傳原公司
- [ ] 2.6 先寫失敗測試：轉換後該公司的名稱、別名、供應角色與審核狀態皆未變更
- [ ] 2.7 先寫失敗測試：轉換完成後不存在 `identifierType` 為 `OTHER` 且值含交易所前綴的資料

## 3. 界線與文件（design D4）

- [ ] 3.1 於 `IdentifierType` 補註解說明 `OTHER` 的適用界線：只用於非交易所的識別體系；交易所缺類型時應擴充 enum 而非以 `OTHER` 加前綴
- [ ] 3.2 更新 `.claude/rules/api-design.md`（若該文件列有 `identifierType` 可用值）與 `docs/data-loading-playbook.md` 步驟 6 的類型清單
- [ ] 3.3 將 `docs/data-loading-playbook.md` 第八節 G4 標記為已解決，並註明 `OTHER` 的新界線
- [ ] 3.4 留下判準供日後參考：**若再出現需要修改既有識別碼的情況，應做識別碼修正端點而非再寫一支 migration**（本次因只有個位數筆數才選 migration）

## 4. 驗證與收尾

- [ ] 4.1 收尾執行一次 `./mvnw clean verify`，附實際輸出（長時間 build 背景執行，靠完成通知取回結果，禁止輪詢迴圈）
- [ ] 4.2 對本次 diff 跑 `/code-review`（fresh context），只修正確性 findings
- [ ] 4.3 於 `docs/CHANGELOG.md` 的 `[Unreleased]` 記錄行為變化（識別碼新增交易所類型；既有 3 筆 `OTHER` 資料的 type 與 value 改變），hash 欄寫 `(pending)`；追加前先掃既有項目
- [ ] 4.4 commit（changelog 與程式同一筆）
