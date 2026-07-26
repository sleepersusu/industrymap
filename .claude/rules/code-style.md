# Code Style & Conventions（程式碼風格與規範）

本文件描述 `industrymap` 預計採用的 Java / Spring Boot 程式風格，對齊 `ais-backend` 現況。
請優先保持專案內部一致，而不是套用泛用模板。

## General

- 語言版本：Java 21。縮排 4 空白；行長 soft limit 120 字元，鏈式 builder / 註解 / OpenAPI 註解可適度換行。
- 新增註解、Javadoc、文件內容一律使用繁體中文。
- 優先保持現有模組風格一致，不要在單一檔案混入另一套完全不同的寫法。

## Naming

- package：全小寫；依領域切分，例如 `service.product`、`service.company`、`service.market`。
- 專案特有類別分工：`*TransactionService`（交易邊界）、`*AsyncService` / `*AsyncProcessor`（非同步）、`*SyncService`（外部資料同步）。
- Payload 命名：`*Payload`、`*Request`、`*Response`、`*Result`；API 契約放 `payloads` package（複數）。

## Spring 慣例

- 設定檔用 `application.properties`（對齊 `ais-backend`，不是 yml）；成組設定可搭配 `@ConfigurationProperties`。
- DI：constructor injection + `final` 欄位 + `@RequiredArgsConstructor`；不用 field injection；例外需在程式碼註解原因。

## Lombok

- 沿用 Lombok（`@Data` / `@Builder` / `@Getter` / `@RequiredArgsConstructor` / `@Log4j2`）。
- Java `record` 非主流，僅限非常簡單、不可變、與既有模組風格不衝突的結構。

## DTO / Payload / Entity 風格

- API request / response 結構放在 `payloads` package（複數；主套件下無 `dto`，`dto` 僅存在於 `clients/dto` 子包，用於封裝外部 API 回傳格式）。
- 不假設必須引入 `ModelMapper` 或 `MapStruct`；優先手動轉換、builder 組裝。
- 新類型若只是 API 契約或中介結果，優先在 `payloads` 下建立對應類別，不要濫用 entity 直接承擔所有角色。

## JSON 持久化欄位（`@Type(JsonType.class)`）序列化紀律

任何以 hypersistence `@Type(JsonType.class)` 持久化的**自訂 POJO**（例如零組件的彈性規格欄位、
外部來源回傳的 raw payload 快照），其**整個物件圖每一層 class 都必須 `implements Serializable`**，
否則存檔時拋 `JpaSystemException: Object (or its inner child Objects) is not Serializable`。

- 成因：hypersistence-utils（隨 Spring Boot 3.5）的 `JsonType` 於 Hibernate dirty-check 以 **Java 序列化 deep-clone** 儲存值；物件圖任一層漏補 Serializable，`ObjectOutputStream.writeObject` 即拋 `NotSerializableException`。`com.fasterxml.jackson...JsonNode` 為唯一豁免；`Map`/`List`/`Set` 內若全是 JSON scalar（String/Number/Boolean/enum）本身已 Serializable。
- 逐層檢查：新增或擴充 JsonType 欄位、或其巢狀型別（含 **static 巢狀 class** 需各自加介面）時，沿整個圖確認每一層都實作 Serializable。
- **多型 `Object` / `List<Object>` 欄位**：日後往裡塞的任何新自訂 POJO 都必須 implements Serializable，否則同樣 500。
- 慣例：加 `implements Serializable` 時**不加** `serialVersionUID`。

## 註解與可讀性

- 註解說明「為什麼」或「這段流程的責任」，不重述程式碼字面意思。

## Error Handling

### 現況原則

- 統一使用 `ServerException` 表達業務與 HTTP 錯誤。
- Service / Consumer / Producer 發現錯誤時，可直接拋出 `ServerException` 並帶 `HttpStatus`。
- 一般 JSON API 成功回應多半使用 `ServerResponse<T>`。

### 例外處理紀律

- 禁止空 catch 或 catch 後吞掉不處理（只 `log.error` 但不拋出、不回傳錯誤狀態、無降級行為，視同吞掉）。
- catch 到例外只有三種合法出路：
  1. 翻成帶語意的 `ServerException`（含 `HttpStatus`）拋出
  2. rethrow 給上層
  3. 明確的降級行為（fallback 值、跳過該筆繼續批次、保留上次成功同步的資料）＋ log 記錄原因與上下文 id
- 禁止 `catch (Exception e)` 大小通吃；僅邊界層（consumer、scheduler、批次迴圈——例如外部股價/新聞來源批次同步）允許，且必須記錄後決定中斷或續行。

### 不要硬套的規則

- 不要預設一定要建立自訂 Exception hierarchy。
- 不要假設所有錯誤都一定經過 `@ControllerAdvice` 才算正確。

## Logging

- 使用 Lombok `@Log4j2`，**不是 `@Slf4j`**（對齊 `ais-backend`）。
- 使用 `{}` placeholder，不要先字串拼接再丟進 log。
- 訊息帶可追蹤的業務上下文 id（companyCode、jobId、productId、數量統計與狀態）。
- 高頻迴圈不用 `info` 洗版；細節用 `debug` 或關鍵節點輸出。

## 禁止事項

- 不要把專案主要 logger 風格寫成 `@Slf4j`。
- 不要強推 `ModelMapper` / `MapStruct` 當成既定標準。
- 不要新增英文註解或文件去破壞專案以繁體中文為主的風格。
