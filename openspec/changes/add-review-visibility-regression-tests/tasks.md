## 1. 可見性矩陣與骨架

- [ ] 1.1 盤點並記錄矩陣：10 個對外 GET 端點 × 各自可觸及的內容表（8 張表）。
      逐一確認觸及範圍時以 repository 的實際 SQL 為準，不憑端點名稱推測
- [ ] 1.2 建立 `ReviewVisibilityMatrixTest`（`@Tag("integration")`，繼承 `AbstractPostgresIntegrationTest`），
      矩陣以資料結構表達，每格帶「端點、內容表、遺漏的層次」供失敗訊息使用（design D4）
- [ ] 1.3 fixture 一律帶 `FIXTURE_PREFIX`，斷言限定在自建資料上，不對全表數字下斷言（design D3）

## 2. 覆蓋率守衛（本次的核心價值）

- [ ] 2.1 先寫失敗測試：**刻意**在矩陣中略過一個既有端點，確認守衛會 fail 並指名該端點；
      再實作以 `RequestMappingHandlerMapping` 取出所有 GET 端點並比對矩陣（design D2）
- [ ] 2.2 補回略過的端點，確認守衛轉綠——這一步證明守衛真的會抓，而非恆綠的裝飾
- [ ] 2.3 支援「該端點不觸及任何內容表」的登記形式：可通過但必須明示理由，不得省略登記

## 3. 直接外露：已駁回資料本身不得出現在回應中

- [ ] 3.1 先寫失敗測試（若既有行為已正確則為綠，須註明）：已駁回的識別碼不得列進 `GET /api/companies/{code}` 的回應
- [ ] 3.2 已駁回的品類節點不得出現在 `GET /api/products`、`GET /api/items`
- [ ] 3.3 已駁回的組成關係不得出現在 `GET /api/products/{id}/components`、`GET /api/items/{id}/compositions`
- [ ] 3.4 已駁回的供應角色不得出現在 `GET /api/items/{id}/suppliers`
- [ ] 3.5 已駁回的市佔率不得出現在 `GET /api/items/{id}/market-share`
- [ ] 3.6 已駁回的公司不得出現在 `GET /api/companies`

## 4. 間接影響：已駁回資料不得改變結果（三次實際缺陷有兩次屬此類）

- [ ] 4.1 已駁回的別名不得使公司被搜尋到（`GET /api/companies?name=`）
- [ ] 4.2 已駁回的品類別名不得使節點被解析到（`GET /api/items?name=`）
- [ ] 4.3 已駁回的識別碼不得使合法的裸代號查詢變成 409（`GET /api/companies/{code}`）
- [ ] 4.4 已駁回的識別碼不得被寫進 409 錯誤訊息的候選清單
- [ ] 4.5 已駁回的公司不得使裸代號查詢變成 409
- [ ] 4.6 已駁回的資料不得計入任何分頁端點的 `totalElements`

## 5. 關係指向的實體（三次中最不直覺、最晚被抓到的一層）

- [ ] 5.1 已驗證的供應角色指向已駁回的品類節點時，不得使公司出現在 `GET /api/companies?companyRole=`
- [ ] 5.2 已驗證的組成關係指向已駁回的子節點時，該枝不得出現在組成樹
- [ ] 5.3 已驗證的組成關係指向已駁回的父節點時，不得出現在 `GET /api/items/{id}/end-products`
- [ ] 5.4 已驗證的供應角色指向已駁回的公司時，不得出現在 `GET /api/items/{id}/suppliers`
- [ ] 5.5 已驗證的市佔率指向已駁回的公司時，不得出現在 `GET /api/items/{id}/market-share`

## 6. 收尾

- [ ] 6.1 逐項檢視測試揭露的失敗：判斷屬「程式漏過濾」或「既有設計取捨」。
      **前者在本次修正；後者記錄下來另開 change，不在本次夾帶行為變更**（design Risks）
- [ ] 6.2 特別處理已知的不一致：指名已駁回的品類節點時，`/api/products/{id}/components` 回該節點、
      `/api/companies?itemId=` 回空清單。判定何者正確並記錄結論；若需統一則另案
- [ ] 6.3 `./mvnw clean verify` 跑全量，附實際輸出
- [ ] 6.4 `.claude/rules/testing.md` 補一條：新增對外查詢端點時必須登記進可見性矩陣
- [ ] 6.5 以 fresh-context diff review 審查（`/code-review`），只修正確性 findings
- [ ] 6.6 不記 CHANGELOG（純測試，使用者不可感知）——除非 6.1 產生了行為修正，則該修正需記錄
