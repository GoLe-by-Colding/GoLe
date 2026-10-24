# Mobile Splash Design

## 그림 — `build-brand-icons.mjs`의 `splash-icon` 변형

- 캔버스는 **288 단위 = Android 288dp**다. `app.json` `imageWidth: 288`이라 1단위 = 1dp(iOS 1pt)로 읽힌다.
  PNG는 xxxhdpi(×4)를 덮는 1152px 정사각형, 투명 배경이다(배경 흰색은 `backgroundColor`가 준다).
- 고래: 정본 다섯 경로를 정본 `fill` 그대로(`mark.svg`에서 읽는다) 폭 150으로 앉힌다. 무게중심 보정(`NUDGE_X/Y`)은
  기존 `placed()`를 그대로 쓴다.
- 워드마크: 굵은 둥근 선(stroke, `round` 끝·이음)으로 그린 기하 글자 `G o L e`. 글꼴 파일이 없어도 librsvg가 같은 결과를
  내고, 둥근 선이 브릭 고래의 둥근 모서리와 어울린다. `Go`는 정본 face 색(`#131E4F`), `Le`는 정본 body 색(`#1D4ED8`).
- 세로 배치: 고래(높이 ≈84) + 간격 + 워드마크(높이 ≈34) 묶음을 원의 중심에 둔다. 생성 후 PNG의 불투명 픽셀 최원점을
  288 기준 dp로 재고 94dp를 넘으면 실패한다(기존 어댑티브 세이프존 검사와 같은 방식).

## 설정 — `app.json`

```json
["expo-splash-screen", { "backgroundColor": "#FFFFFF", "image": "./assets/images/splash-icon.png", "imageWidth": 288 }]
```

- 루트 `imageWidth`가 Android·iOS 둘 다에 쓰인다. Android 플러그인은 그 폭으로 줄인 그림을 288dp 캔버스에 놓는다.

## 시작 흐름 — `shared/lib/app-splash.ts`

- `releaseSplash()`: 한 번만 `SplashScreen.hideAsync()`를 부르는 멱등 함수.
- `RootLayout`: 마운트 즉시 숨기던 것을 **3초 상한 타이머**로 바꾼다(언마운트 시 정리).
- `WebScreen`: 첫 문서 로드 완료(`finishLoad`)와 실패(`fail`)에서 `releaseSplash()`. 탭 WebView가 여럿이어도 첫 호출만 효과가 있다.
- 실패 화면·오프라인은 그대로다. 스플래시가 그 위를 덮지 않도록 실패 즉시 내린다.

## 하지 않는 것

- 앱 아이콘·어댑티브·알림 아이콘·favicon·웹 마스코트 변경, 정면 고래, 다크 모드 별도 그림, JS 애니메이션 스플래시.
