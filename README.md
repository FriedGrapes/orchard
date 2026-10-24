# Orchard

에어팟을 안드로이드와 윈도우에서 아이폰처럼 쓰게 해 주는 앱이에요.
[LibrePods](https://github.com/librepods-org/librepods)(GPL-3.0)를 포크해서 안정성·모델 인식·화면을 손봤고, 윈도우 앱을 새로 만들었어요.

## 내려받기

[Releases](https://github.com/FriedGrapes/orchard/releases/latest)에서 받으세요. 한 번 설치하면 두 앱 모두 스스로 업데이트돼요.

| 파일 | 대상 |
|---|---|
| `Orchard-android_v….apk` | 안드로이드 13 이상 |
| `Orchard-win-Setup.exe` | 윈도우 10/11 (일반 PC, x64) |
| `Orchard-win-arm64-Setup.exe` | 윈도우 11 ARM (Surface Pro 등) |

## 기능

| | 안드로이드 | 윈도우 |
|---|---|---|
| 배터리(왼쪽·오른쪽·케이스, 맥스) | ✅ | ✅ |
| 귀에서 빼면 일시정지 / 다시 끼면 재생 | ✅ | ✅ |
| 케이스를 열면 팝업 | ✅ | ✅ |
| 정확한 모델 인식 (AirPods Max 2·Pro 3·AirPods 5 포함) | ✅ | ✅ |
| 노이즈 캔슬링·주변음 전환, 대화 인식 | ✅ | 2단계(드라이버) 예정 |
| 머리 제스처, 보청 기능 등 | ✅ (일부 루팅 필요) | – |
| 기기 간 자동 전환(재생하면 가져오기) / 수동 "여기로 가져오기" | ✅ | ✅ |
| 에어팟 찾기(마지막으로 함께 있던 곳, 근처 신호 세기) | ✅ | – |
| 자동 업데이트 | ✅ | ✅ |

## 원본에서 바뀐 점

- **연결 안정성**: 연결 시도 겹침 방지, 실패 시 1·2·4초 자동 재시도, 블로킹 연결이 메인 스레드를 멈추던 문제(ANR), 포그라운드 서비스 시작 지연으로 인한 강제 종료, 끊길 때 소켓 정리, ATT 리더 순서, `!!` 로 인한 서비스 크래시.
- **모델**: AirPods Max(라이트닝·USB-C·2세대)가 일반 에어팟으로 보이던 문제. 모르는 모델은 기본 기능으로 동작. 배터리 1칸(맥스) 패킷과 측정 불가 값(127%) 처리.
- **자동 업데이트**: GitHub Releases 확인용으로만 인터넷 권한을 씀.

## 소스 구성

- `android/` — 안드로이드 앱 (Kotlin, Jetpack Compose). 패키지 이름은 원본과 같은 `me.kavishdevar.librepods`(원본 변경을 받아오기 쉽게), 앱 ID는 `app.orchard.android`.
- `windows/` — 윈도우 트레이 앱 (.NET 8, WPF). `Orchard.Core`는 근접 신호 해석·모델 표(시험 포함).
- `shared/models.json` — 두 앱이 같이 쓰는 모델 번호 표의 기준.

## 라이선스

GPL-3.0. 원본 LibrePods © kavishdevar 와 기여자들. "LibrePods" 이름과 로고는 원본 프로젝트의 것이며 이 앱은 그 이름을 쓰지 않아요.
AirPods는 Apple Inc.의 상표이고, 이 프로젝트는 Apple과 관계가 없어요.
