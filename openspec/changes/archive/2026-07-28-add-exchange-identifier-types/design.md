## Context

三輪資料灌入（腳踏車、主機板、桌上型電腦）累積了 50 家公司，其中 3 家的識別碼因為
`IdentifierType` 沒有對應的交易所類型，被迫寫成 `OTHER` + 自訂前綴值。

這個 change 刻意**與 `add-item-listing-and-amendment` 分開**：兩者零依賴，
且那個 change 完全沒有 schema／資料變更，而本 change 需要一支 data migration——
混在一起會讓不相關的工作共擔 migration 風險。兩個 change 誰先做都可以。

## Goals / Non-Goals

**Goals:**
- 讓境外交易所的識別碼能以正規類型登記，不需要把交易所塞進值裡
- 把既有 3 筆變形資料轉正
- 讓「遇到新交易所該怎麼辦」有明確答案，而不是各自發明前綴格式

**Non-Goals:**
- 不做識別碼的修正端點（既有資料以 migration 轉換即可，不值得為 3 筆開端點）
- 不動其他 enum
- 不做「依交易所查詢」的端點——本 change 只讓那件事**變得可能**，不實作它

## Decisions

### D1：只補「已經遇到」與「明顯即將遇到」的交易所，不預先窮舉

- **必要**：`HKEX`（鴻騰精密、Lenovo）、`FSE`（Infineon）——已經有資料卡在那裡
- **建議一併補**：`KRX`（韓國，記憶體供應鏈必然遇到三星、SK 海力士）、
  `SSE` / `SZSE`（中國，PCB 與被動元件供應鏈）、`LSE`（英國，ARM 等）
- **不補**：其餘交易所等實際遇到再加。加一個列舉值的成本極低（見 D2），
  沒有必要為了完整性先列 40 個永遠用不到的值
- **理由**：這個 enum 的價值在於「值是受控的」。預先塞滿反而讓人以為必須從中挑一個最接近的，
  而不是在真的缺類型時提出來

### D2：新增列舉值不需要 schema migration，但轉換既有資料需要

已查證：

- `V2026_07_26_100500__create_company_identifier.sql` 中 `identifier_type` 為 `VARCHAR(32) NOT NULL`，
  **沒有 check constraint**，也沒有使用 PostgreSQL enum type
- `CompanyIdentifier` 以 `@Enumerated(EnumType.STRING)` 持久化

因此新增列舉值是純 Java 改動。**唯一需要 migration 的是既有 3 筆資料的轉換**，
且該 migration 只有 `UPDATE`，不含任何 DDL。

- **migration 必須以自然鍵定位**（`identifier_type = 'OTHER'` 加值的前綴），**不可用內部 id**——各環境的自增 id 不同
- **條件用前綴模式而非逐筆列舉精確值**（實作時修正）：
  `WHERE identifier_type = 'OTHER' AND identifier_value LIKE 'HKEX:%'`，值取冒號後段。
  原本規劃寫死 `= 'HKEX:6088'` 這類精確值，但那樣**寫不出誠實的整合測試**——
  乾淨的 Testcontainer 上沒有那幾筆資料可斷言，共用的本機開發 DB 上 Flyway 已在
  context 啟動時轉換完畢，測試自建的同值 fixture 轉換後會與真實資料撞唯一鍵。
  前綴條件讓測試能用自己的 fixture（如 `HKEX:it-6088`）跑同一段 SQL，兩種環境都成立；
  今天命中的仍是同樣那 3 筆
- **必須具冪等性考量**：轉換後值不再帶前綴，重跑時條件自然不再命中，符合 Flyway 一次性執行的前提
- 版號依 `.claude/rules/flyway.md`：先查主線 `db/migration` 最新一支再訂，不可憑空假設

### D3：`unique(type, value)` 在轉換過程可能撞號——必須先確認

轉換後 `(HKEX, 6088)` 與 `(HKEX, 0992)` 是新組合，理論上不會與既有資料衝突，
但**這是必須驗證而非假設的事**：若某公司已用 `HKEX` 之外的類型登記過相同數字值，
或未來有人已手動修正過部分資料，`UPDATE` 會違反唯一鍵而讓 migration 失敗。

- **tasks 中要求先查一次實際資料**再寫 migration，不靠推論
- migration 失敗時 Flyway 會中止，不會產生半套資料——這是可接受的失敗模式，
  但要在 tasks 註明「失敗即代表資料現況與假設不符，須重查而非強改」

### D4：`OTHER` 的界線寫進 spec，而不是只寫在註解

問題的根源不是「少了 HKEX」，是**沒有人規定遇到新交易所該怎麼辦**，
於是第一個遇到的人發明了 `HKEX:6088` 這種格式，第二個人照抄。

- **採用**：spec 明確 `OTHER` 只用於**非交易所**的識別體系（且該體系未被既有類型涵蓋），
  MUST NOT 用於「交易所存在但 enum 沒有對應值」的情況——後者應提出擴充 enum
- **可測的驗收**：`identifierValue` MUST NOT 內嵌交易所前綴。
  這條讓「又走回老路」在 review 時看得出來

## Risks / Trade-offs

- **本 change 唯一的行為變更是那 3 筆資料的 `identifierType` / `identifierValue` 改變** →
  以這些值查詢公司的呼叫端會受影響。目前無外部呼叫端；仍需在 CHANGELOG 記錄
- **enum 值加得不夠多，日後還要再開 change** → 這是 D1 刻意接受的代價。
  加列舉值成本極低，分次加優於預先窮舉
- **未提供識別碼修正端點** → 日後若再出現需要改的識別碼，仍得走 migration。
  若這種情況出現第二次，就應該做端點而不是再寫一支 migration；tasks 中留下這個判準

## Migration Plan

1. 先擴充 `IdentifierType`（純 Java，無 migration）
2. 查實際資料確認轉換不會違反 `unique(type, value)`（D3）
3. 寫 data migration 轉換既有 3 筆，版號先查主線最新一支
4. 更新 `docs/data-loading-playbook.md` 第八節 G4 的狀態
