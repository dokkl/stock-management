# Contract: Web Routes (003 시장 지표)

| 메서드·경로 | 인증 | 응답 | 설명 |
|---|---|---|---|
| `GET /` | 필요 | 200 HTML | 대시보드. 상단에 `#market-indicators` 영역(저장값 포함), 하단 `footer.site-footer` |
| `GET /fragments/indicators` | 필요(HX) | 200 HTML 프래그먼트 | 지표 영역 다시 그리기. htmx `every 60s` 폴링. 외부 호출 없음 |

- 미인증: htmx 요청 401, 일반 요청 `/login` 리다이렉트(기존 규칙).
- 지표 카드 마크업: 이름, 값(단위), 등락(▲/▼/– + 숨김 텍스트 "상승/하락/보합"), 기준 시각 문구
  (`LIVE` "HH:mm 기준", `STALE` "지연 (HH:mm 기준)", `CLOSED` "장 마감 (MM-DD HH:mm)", `DAILY_CLOSE` "MM-DD 종가"), 값이 없으면 "정보 없음".
- 영역 상태: 모든 지표가 없으면 empty, 조회 실패면 error(공용 `fragments/states`), 그 밖에는 ready.
