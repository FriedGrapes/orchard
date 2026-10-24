# Orchard 인계 메모

## 디자인 방향
- 애플 감성(아이폰 에어팟 팝업처럼 큰 기기 그림 + 링 배터리) + 각 OS 마감(안드로이드 Material 3, 윈도우 Fluent 아크릴).
- 글꼴: Pretendard(OFL). 원본의 SF Pro·SF Symbols 문자는 쓰지 않는다(재배포 불가 → 일반 유니코드 기호).
- 배터리 색: 초록 `#34C759`(충전 중이거나 21% 이상) · 노랑 `#FFCC00`(11~20%) · 빨강 `#FF3B30`(10% 이하). 모르는 값은 링 없이 "–".

## 모션 토큰 (성격: 프리미엄·차분)
| 동작 | 안드로이드(Compose) | 윈도우(WPF) |
|---|---|---|
| 기기 그림 등장 | spring(damping 0.9, StiffnessMediumLow), alpha 0→1, scale 0.94→1, 위로 12dp | 이미지 opacity 320ms CubicEase out |
| 링 차오름 | 0에서 spring(damping 1, StiffnessLow), 링마다 120/180/240ms 늦게, 숫자 함께 굴림 | DoubleAnimation 650ms CubicEase out |
| 팝업 등장 | — | opacity 220ms + 위로 14px 320ms BackEase(0.25) |
| 팝업 퇴장 | — | 140ms EaseIn (등장보다 빠르게) |
| 눌림 | scale 0.97 | scale 0.97 |

## 화면 확인 (폰 없이)
- 안드로이드: `./gradlew testFossDebugUnitTest` → `app/build/screenshots/*.png`(전체 화면), `frames/*.png`(시간대별 프레임). Roborazzi + Robolectric, ARM 윈도우에서도 동작.
- 윈도우: `Orchard.exe --demo 202D --snap <파일.png>` (202D=맥스 2, 2027=프로 3, 2030=에어팟 5).

## 모델 번호
- 기준 표: `shared/models.json`. 윈도우 `Orchard.Core/Models.cs`, 안드로이드 `data/AirPods.kt`(A-번호)·`bluetooth/BLEManager.kt`(바이트 뒤집힌 값)와 맞춘다.
