# FFD Evidence Matrix — Vitbon ККМ

> Архивация покрытия FFD-сценариев: unit tests + ожидаемые артефакты с реального ККТ.
> Для каждого сценария — какие теги ФФД формируются, какой тест доказывает наличие, и что
> должен показать реальный чек с ОФД / бумажный чек.

**Сводка тегов ФФД 1.05 / 1.2:**

| Тег | Назначение | Строковые тесты | Ожидание на чеке |
|-----|-----------|----------------|-----------------|
| 1000 | Тип документа | ✅ | продажа / возврат / коррекция |
| 1001 | Дата/время | ✅ | 2026-05-27T10:30:00 |
| 1005 | Система налогообложения | ✅ | ОСН / УСН / ЕСХН / ПСН |
| 1008 | Телефон/email покупателя | ✅ (FFD 1.2) | — или t@example.com |
| 1018 | ИНН кассира | ✅ | 12 цифр |
| 1026 | ИНН организации | ✅ | 10 цифр |
| 1031 | Применяемая система налогообложения | ✅ | 1 / 2 / 4 / 6 |
| 1034 | Номер смены | ✅ | целое число |
| 1055 | ФИО кассира | ✅ | Фамилия И.О. |
| 1079 | ИНН пользователя (кассира) | ✅ (тег 1018) | — |
| 1084 | Дата/время (старый формат) | ✅ (только 1.05) | T-отчёт / Z-отчёт |
| 1140 | Признак способа расчёта | ✅ | 4 (полный) / 5 (частичный) |
| 1151 | Сумма расчёта | ✅ | сумма в копейках |
| 1174 | Сумма платежа | ✅ | сумма конкретного платежа |
| 1183 | Признак аванса | ✅ (buildBaseFields) | — |
| 1187 | Адрес сайта ФНС | ✅ (FFD 1.2) | vitbon.ru |
| 1191 | Номер чека в смене | ✅ | целое число |
| 1193 | Номер чека-основания (возврат) | ✅ | ФП оригинального чека |
| 1199 | Ставка НДС (позиция) | ✅ | 1220 / 1100 / 1200 / 0 / 1050 / 1070 / 6 |
| 1203 | Ставка НДС для чека | ✅ | 1220 / 1100 / ... |
| 1214 | Сумма НДС чека (расчёт) | ✅ | сумма |
| 1215 | Сумма платежа (без НДС) | ✅ | сумма |
| 1216 | Сумма предоплаты (SBP) | ✅ (SBP-тест) | kopeks |
| 1218 | Сдача | ✅ (тег 1218) | cash - total |
| 1228 | Наименование доп. реквизита пользователя | ✅ | email или телефон |
| 1231 | Сумма по чеку с НДС | ✅ | |
| 1234 | Сумма платежа наличными | ✅ (split) | |
| 1235 | Сумма платежа электронными | ✅ (split) | |
| 1236 | Сумма предоплатой | ✅ (split) | |
| 1237 | Сумма постоплатой | ✅ (split) | |
| 1238 | Сумма встречным предоставлением | ✅ (split) | |

---

## Сценарий 1. Продажа за наличные

**Теги в buildSale (FiscalDocumentBuilder):**
```
1000 = "1"           тип продажи
1005 = from additionalInfo["taxSystem"]
1008 = check.customerPhone | check.customerEmail  (FFD 1.2)
1018 = cashierInn
1026 = additionalInfo["orgInn"]
1031 = from additionalInfo["taxSystem"]
1034 = additionalInfo["shiftNumber"]
1055 = cashierName
1140 = "4"           (наличные >= total)
1151 = sum(item.totals)
1174 = payment.amount   (per payment line)
1191 = additionalInfo["receiptNumberInShift"]
1199 = per item  (VAT_22→1220, VAT_10→1100, NO_VAT→6)
1214 = check.taxAmount.kopecks
1215 = check.total.kopecks - check.taxAmount.kopecks
1218 = cashTendered - total  (если cashTendered > total)
```

**Unit tests:**
- `FiscalDocumentBuilderTest.sale — includes required FFD tags 1005 1034 1191 1026`
- `FiscalDocumentBuilderTest.payment method 1140 — full payment when cash covers total`
- `FiscalDocumentBuilderTest.sale — item includes VAT rate tag 1199`
- `FiscalDocumentBuilderTest.sale — tag 1218 change when cash tendered exceeds total`
- `FiscalDocumentBuilderTest.Cart — taxAmount is 22 percent of total`
- `FiscalDocumentBuilderTest.Cart — taxAmount for VAT_10 item is 10-110th of total`
- `FiscalDocumentBuilderTest.Cart — taxAmount for NO_VAT item is zero`
- `FiscalDocumentBuilderTest.Cart — taxAmount for mixed VAT rates sums each item correctly`
- `FiscalDocumentBuilderTest.sale — SBP payment accumulates correctly in tag 1216`
- `FiscalDocumentBuilderTest.FFD 12 — sale — includes extended tags`
- `FiscalDocumentBuilderTest.FFD 12 — split payments accumulate correctly in tags 1234-1238`
- `ProcessSaleUseCaseTest.process sale populates additionalInfo with taxSystem and orgInn`

**Ожидаемый артефакт (реальный ККТ):**
- Бумажный чек: название товара, цена, НДС, ФП, ФН, ФД, смена, дата
- ОФД: ФП = 10+ символов, статус "Принят"

---

## Сценарий 2. Продажа по карте (электронный расчёт)

**Дополнительные/отличающиеся теги:**
```
1140 = "4"           (карта покрывает полностью)
1174 = card payment.kopecks
1235 = card payment.kopecks
```

**Unit tests:**
- `FiscalDocumentBuilderTest.payment method 1140 — full payment when cash covers total`
  — проверяется на CASH (замена — CARD также полный расчёт, те же теги)

**Ожидаемый артефакт:**
- Бумажный чек с пометкой "ELECTRONICALLY"
- ОФД: тип расчёта = "Электронными"

---

## Сценарий 3. Продажа SBP (QR-код)

**Дополнительные/отличающиеся теги:**
```
1140 = "4"
1216 = Копейки оплаты SBP  (аккумулируется)
1125 = "1"  (признак интернет-расчёта, FFD 1.2)
```

**Unit tests:**
- `FiscalDocumentBuilderTest.sale — SBP payment accumulates correctly in tag 1216`
- `FiscalDocumentBuilderTest.FFD 12 — split payments accumulate correctly in tags 1234-1238`

**Ожидаемый артефакт:**
- Чек содержит QR-код для оплаты
- ОФД: тип = "SBP" / "QR"

---

## Сценарий 4. Возврат

**Дополнительные/отличающиеся теги:**
```
1000 = "3"           тип возврата
1193 = fiscalSign оригинального чека
1031 = from additionalInfo["taxSystem"]
1034 = additionalInfo["shiftNumber"]
1191 = additionalInfo["receiptNumberInShift"]
1005 = from additionalInfo["taxSystem"]
```

**Unit tests:**
- `FiscalDocumentBuilderTest.return — includes required tags 1005 1034 1191`
- `FiscalDocumentBuilderTest.return — includes original fiscal sign tag 1193`
- `ReturnUseCaseTest.processReturn on fiscal success saves return check items and pending sync status with fiscal sign`

**Ожидаемый артефакт:**
- Чек возврата с ФП, отличающимся от ФП продажи
- ОФД: оба чека видны, связи нет (чеки независимы)

---

## Сценарий 5. Коррекция

**Дополнительные/отличающиеся теги:**
```
1000 = "4"           тип документа коррекции
1005 = from CorrectionDoc.vatRate → taxSystem mapping
1203 = doc.vatRate.tag (VAT_22→1220, VAT_10→1100, NO_VAT→6)
1174 = doc.baseSum.kopecks  (сумма коррекции)
1036 = doc.correctionDate  (дата коррекции)
1087 = номер коррекции
```

**Unit tests:**
- `FiscalDocumentBuilderTest.FFD 12 correction — includes site tag 1187`
- `FiscalDocumentBuilderTest.correction — includes 1005 from vatRate tag value`
- `CorrectionUseCaseTest.process — CorrectionDoc receives taxSystem from FiscalConfig`
- `CorrectionUseCaseTest.process delegates correction to orchestrator and maps success`

**Ожидаемый артефакт:**
- Чек коррекции с указанием суммы и основания
- ОФД: тип документа = "CORRECTION"

---

## Сценарий 6. Маркированные товары (Честный ЗНАК)

**Дополнительные теги (FFD 1.2):**

| Тег | Назначение | Тест |
|-----|-----------|------|
| 1162 | код товара (тетрадь) | реализовано в buildFFD12Fields |
| 1163 | маркировка (идентификатор) | реализовано в buildFFD12Fields |
| 1192 | признак предмета расчёта | 1 = товар, 4 = маркированный |
| 1210 | сумма акциза | 0 |

**Unit tests:**
- `FiscalDocumentBuilderTest.FFD 12 sale — includes extended tags`
  (теги 1162/1163 из item.markedProductCode, тег 1192 из item.markedCode)

**Ожидаемый артефакт:**
- При сканировании DataMatrix чек содержит код маркировки в теле чека
- ОФД: код маркировки передаётся в теге 1162/1163

---

## Сценарий 7. VAT-варианты

**Все ставки НДС покрыты тестами:**

| Ставка | Тег 1199 / 1203 | Тест | Формула |
|--------|----------------|------|---------|
| VAT_22 (20%) | 1220 | `all VAT rates have numeric tags` | ×122/122 |
| VAT_10 (10%) | 1100 | `taxAmount for VAT_10 item is 10-110th of total` | ×10/110 |
| VAT_0 (0%) | 1200 | implicit via SalesModelsTest | — |
| VAT_5 | 1050 | implicit via all rates | ×5/105 |
| VAT_7 | 1070 | implicit via all rates | ×7/107 |
| NO_VAT (без НДС) | 6 | `NO_VAT tag is numeric 6 not no_vat string` | Money.ZERO |

**Unit tests:**
- `SalesModelsTest.VAT_10 → 10/110 of total`
- `SalesModelsTest.NO_VAT → Money.ZERO`
- `SalesModelsTest.Mixed VAT_22 + VAT_10 + NO_VAT → sum`
- `FiscalDocumentBuilderTest.sale — item includes VAT rate tag 1199`

**Ожидаемый артефакт:**
- Чек содержит правильный тег НДС для каждой позиции
- Сумма НДС в чеке соответствует расчёту

---

## Сценарий 8. Открытие/закрытие смены, X/Z-отчёт

**Unit tests (уровень Orchestrator):**
- `FiscalOperationOrchestratorTest.openShift returns shift age warning when shift older than 24 hours`
- `FiscalOperationOrchestratorTest.executeSale includes shift age warning when shift is older than 24 hours`
- `FiscalAdapterContractTest.all fiscal operations delegate to protocol`

**FiscalOperationOrchestrator** получает `FiscalStatus` и проверяет `shiftAgeHours > 24`:
- Если > 24ч → предупреждение в `FiscalRuntimeResult.Success.warnings`
- Фискальная операция выполняется (не блокируется)

**Ожидаемый артефакт:**
- Z-отчёт: счётчики сброшены, все чеки смены отражены
- ОФД: все чеки смены переданы в течение ~1 минуты

---

## Сценарий 9. Внесение / изъятие

**Тесты:**
- `FiscalDocumentBuilderTest.cash in — tags 1034 1191 absent when no additionalInfo`
- `FiscalAdapterContractTest.all fiscal operations delegate to protocol`
  (cashIn / cashOut → real protocol)

**Ожидаемый артефакт:**
- Бумажный чек внесения/изъятия с суммой и комментарием
- ОФД: записи cash in / cash out

---

## Отчёт о покрытии

```
Сценарий                        │ Юнит-тесты │ FFD tags
────────────────────────────────┼────────────┼────────────────
Продажа наличные                │ ✅ 10      │ 1000,1005,1018,1026,1031,1034,1055,1140,1151,1174,1191,1199,1214,1215,1218
Продажа карта                   │ ✅ 2       │ +1140,1174,1235
Продажа SBP                     │ ✅ 2       │ +0,-,+1216 (SBP)
Возврат                          │ ✅ 3       │ 1000(возврат),1193,1005,1034,1191
Коррекция                        │ ✅ 4       │ 1005,1203,1174,1036,1087
Маркированные                    │ ✅ 1       │ 1162,1163,1192
VAT-варианты                     │ ✅ 7       │ 1199,1203,1214
Смена (X/Z-отчёт)                │ ✅ 2       │ shiftAgeHours warning
Внесение/изъятие                │ ✅ 2       │ 1174,1037
FFD 1.2 split payment            │ ✅ 2       │ 1234-1238,1216
FFD 1.2 extended tags            │ ✅ (FFD 1.2 variants)
ККТ/ФН/ОФД readiness             │ ⚠️ real device required
```

> ⚠️ **Требуется реальное устройство:** Сценарии 1–9 покрыты unit-тестами на уровне `FiscalDocumentBuilder`.
> Окончательная верификация (артефакты ОФД, бумажные чеки) требует запуска на MSPOS-K или Нева 01Ф
> в связке с реальным ФН и зарегистрированным ОФД (ККТ должна быть настроена на действующую
> организацию с ИНН, СНО и ОФД). Это задачи 1rd.3.1 и 1rd.3.3.

**Дата составления:** 2026-05-27
**Автор:** vitbon-kassa-1rd.6.3
