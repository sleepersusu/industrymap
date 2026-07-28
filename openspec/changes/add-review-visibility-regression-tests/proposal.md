## Why

「已駁回資料一律不外露」是專案憲章等級的規則，`ReviewScopes` 就是為集中這條規則而存在，
其 javadoc 明寫「不會有某支 API 漏擋 REJECTED」。實際上**同一個模式已經犯了三次**
（見 `~/.claude/dev-errors/error-log.md`）：漏過濾識別碼、漏過濾別名、漏過濾「供應角色所指向的品類節點」。
三次都是事後由 code review 抓到才修。

剛升格的 `.claude/rules/architecture.md`「審核狀態過濾是查詢的預設義務」是**提醒型**規則——
它進得了 context，但沒有任何東西會在下次又漏掉時攔住。彙整本身的結論就是
「提醒型的文件無效，需要的是寫入固定流程的檢查點」。

本次把這條規則從「靠人記得」變成「build 會紅」。

現況：8 張內容表帶 `review_status`（`item`、`item_alias`、`item_composition`、`company`、
`company_alias`、`company_identifier`、`company_item_role`、`market_share`），
10 個對外 GET 端點。可見性目前靠每支端點各自的測試零散覆蓋，這正是會一漏再漏的原因。

## What Changes

- 新增一組集中的整合測試，以「端點 × 該端點可觸及的內容表」矩陣驅動，
  逐格驗證**已駁回的資料不得出現、也不得影響結果**
- **新增覆蓋率守衛**：測試會掃描所有 `@GetMapping` 端點，若有端點未登記在可見性矩陣中即 fail。
  這是真正能擋住**未來**新增查詢遺漏過濾的機制——新端點不登記就過不了 build
- 不改動任何既有端點行為；本次是純測試與紀律，不是功能

## Capabilities

### Modified Capabilities
- `data-provenance`: 「已駁回資料不外露」由分散在各能力的個別 scenario，
  提升為一條**橫跨所有對外查詢**的可驗證要求，並要求新增對外查詢時須納入該驗證

## Impact

- **新增測試**：`src/test/.../ReviewVisibilityMatrixTest`（`@Tag("integration")`），沿用
  `AbstractPostgresIntegrationTest` 與 `FIXTURE_PREFIX` 隔離慣例
- **既有程式**：預期不需改動。但**測試可能揭露既有的不一致**（見 design 風險段），
  屆時逐項判斷是修程式還是修預期，不預設一定無事
- **無 schema 變更、無 API 變更、無 CHANGELOG**（純測試，使用者不可感知）
- **文件**：`.claude/rules/testing.md` 補一條「新增對外查詢須登記進可見性矩陣」
