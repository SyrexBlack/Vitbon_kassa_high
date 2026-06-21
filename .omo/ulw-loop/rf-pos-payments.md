# ULW Notepad: РФ кассовый контур

Issue: vitbon-kassa-oiw
Goal: Довести проверяемый рабочий контур кассового приложения под РФ оплаты.

## Skills
- ulw-loop: evidence-led goal execution, RED→GREEN, manual QA.
- frontend-ui-ux: кассовый UI должен быть usable на рабочей поверхности.
- debugging: runtime/manual QA через реальную поверхность и cleanup artifacts.
- programming: применится только при изменениях .py/.rs/.ts/.tsx/.go; текущий проект сначала проверяется.

## Binding Success Criteria (initial)
1. Happy path: продажа с оплатой картой/СБП/наличными создает оплаченный чек с суммами по РФ кассе.
2. Edge path: некорректная/отмененная оплата не закрывает чек как оплаченный.
3. Regression: существующие продажи/отчеты/авторизация не ломаются.

## Evidence Log
