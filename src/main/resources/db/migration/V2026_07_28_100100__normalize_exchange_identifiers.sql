-- 交易所識別碼轉正：三輪資料灌入期間，IdentifierType 沒有香港與法蘭克福交易所的類型，
-- 只能寫成 OTHER + 'HKEX:6088' 這種把交易所塞進值裡的形式（docs/data-loading-playbook.md 第八節 G4）。
-- 列舉值已補齊，這裡把既有資料轉為正規類型，值只留代號本身。
--
-- 定位條件用「OTHER + 值的交易所前綴」這組自然鍵，不用內部自增 id（各環境的 id 不同）。
-- 前綴模式而非逐筆列舉精確值，是為了讓整合測試能對自建 fixture 跑同一段 SQL 驗證行為；
-- 今天命中的仍是既有那 3 筆（HKEX:6088、HKEX:0992、FSE:IFX）。
-- 轉換後值不再帶前綴，條件自然不再命中，符合 Flyway 一次性執行的前提。
--
-- 只含 UPDATE，不含任何 DDL——identifier_type 是 VARCHAR(32) 且無 check constraint，
-- 新增列舉值不需要 schema 變更。

UPDATE company_identifier
SET identifier_type = 'HKEX',
    identifier_value = substring(identifier_value FROM char_length('HKEX:') + 1),
    updated_at = now()
WHERE identifier_type = 'OTHER'
  AND identifier_value LIKE 'HKEX:%';

UPDATE company_identifier
SET identifier_type = 'FSE',
    identifier_value = substring(identifier_value FROM char_length('FSE:') + 1),
    updated_at = now()
WHERE identifier_type = 'OTHER'
  AND identifier_value LIKE 'FSE:%';
