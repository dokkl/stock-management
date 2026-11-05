# Contract: Outbound Ports (도메인 ↔ 외부 연동)

**Feature**: `001-stock-portfolio-ai-guide`

애플리케이션 계층은 아래 포트(Java interface)에만 의존한다. 구현체(어댑터)는 `adapter.out.*`
패키지에 둔다. 테스트는 각 포트의 가짜 구현이나 WireMock 뒤의 실제 어댑터로 계약을 검증한다.
아래 시그니처는 의미 계약이다. 정확한 코드는 구현 단계에서 정한다.

## MarketDataProvider (R2, R3)

```text
fetchQuotes(List<symbol>) -> List<QuoteResult>
    QuoteResult = Success(symbol, price, asOf)
                | Failure(symbol, reason: RATE_LIMITED | NOT_FOUND | UPSTREAM_ERROR)
fetchDailyCandles(SecurityRef, days: int<=120) -> List<Candle(date, open, high, low, close, volume)>
fetchSecurityMaster(Exchange) -> Stream<SecurityRecord>
fetchMarketCalendar(Market) -> MarketCalendar(today, regularOpen, regularClose, previousBusinessDay)
fetchPreviousClose(symbol, LocalDate referenceDate) -> Optional<BigDecimal>   // 기준일 직전 거래일 일봉 종가
fetchSuspendedSymbols(List<symbol>) -> Set<symbol>   // 거래정지 종목(국내만 제공)
```

**계약 조건**:

- 한 종목이 실패해도 나머지 결과는 반환한다. 부분 실패를 허용한다(FR-013).
- 금액은 `BigDecimal`이며 종목 통화 기준이다. 제공자의 문자열 응답을 그대로 파싱하고, 부동소수점을
  거치지 않는다.
- 호출량 제한을 지킨다. 어댑터 내부에 토스증권 Rate Limits Group별 레이트 리미터를 둔다.
- 전일 종가는 현재가 응답에 없으므로 시세와 별도로 받는다. 장 상태와 거래일 판단은 도메인(`MarketCalendar`)이 한다.
- `fetchDailyCandles`(가이드용 60거래일 요약)는 US3에서 추가한다.

## AiGuideProvider (R4)

```text
provider() -> AiProvider            // ANTHROPIC | OPENAI | ...
verifyKey(ApiKey) -> KeyCheck = VALID | INVALID | UNAVAILABLE
generateGuide(ApiKey, GuidePrompt) -> GuideOutcome
    GuideOutcome = Generated(model, GuideContent | rawText)
                 | CredentialError(message)      // 401/403, 잔액 부족
                 | TransientError(message)       // 타임아웃(60s), 429, 5xx
```

- `GuidePrompt`는 도메인이 만든 입력이다. 들어가는 값은 다음과 같다.
  - 종목 정보: 시장, 이름, 통화
  - 현재가, 전일 종가
  - 최근 60거래일 일봉 요약(종가 목록, 20/60일 이동평균, 최고·최저, 변동성)
  - 보유 정보: 평단가, 수량, 평가손익, 수익률
- 개인 식별 정보(계좌명)는 보내지 않는다.
- 출력 스키마: [`ai-guide-output.schema.json`](./ai-guide-output.schema.json)
- 구현체를 추가하면 `AiProvider` enum 값이 하나 늘고 빈이 하나 늘어난다. 그 외 코드는 바뀌지
  않는다(FR-024).

## CredentialCipher (R5)

```text
encrypt(ApiKey) -> EncryptedSecret(ciphertext, iv, keyVersion, last4)
decrypt(EncryptedSecret) -> ApiKey
```

- 같은 평문을 두 번 암호화하면 서로 다른 암호문이 나온다(IV 무작위).
- 암호문이나 IV가 변조되면 예외가 나고, 예외 메시지에 평문이나 키가 없다.

## Clock

- 모든 "오늘(KST)", 장 상태, 잠금 만료 계산은 주입된 `java.time.Clock`을 쓴다(헌법 원칙 II 결정성).
