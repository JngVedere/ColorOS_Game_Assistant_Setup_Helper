# GASH — Game Assistant Setup Helper

중국 롬 ColorOS 태블릿에 숨겨져 있는 **게임 지원(Game Assistant)** 을 켜는 과정을 단계별로 안내하는 Android 앱입니다.
게임 지원은 게임 중 화면 왼쪽 가장자리를 밀면 나오는 성능 모드·터치 샘플링·주사율 패널(HyperBoost)입니다.

*A step-by-step helper for enabling the hidden Game Assistant on Chinese-ROM ColorOS tablets.*

## 설치

[Releases](https://github.com/JngVedere/ColorOS_Game_Assistant_Setup_Helper/releases)에서 `GASH-x.y.z.apk` 를 받아 설치하세요.

- 확인한 환경: OPPO Pad Mini (OPD2515), ColorOS 17.0.1 (CN), 게임 지원 10.43.20, Game Center 14.43.5
- 최소 Android 11

## 사용법

GASH를 열고 **진행 중** 으로 표시된 단계를 눌러 안내대로 한 뒤 돌아오면 됩니다. 모든 단계가 ✓ 가 될 때까지 반복합니다.

| # | 단계 | 하는 일 |
|---|---|---|
| 1 | 게임 지원 앱 확인 | 꺼져 있으면 앱 정보에서 '사용' |
| 2 | Game Center 준비 | 기기 안의 설치 파일로 설치 (Install 두 번) |
| 3 | 게임 지원 동의 | 게임 지원을 열어 'Agree and continue' |
| 4 | 앱 향상 서비스 켜기 | 개선 서비스에서 '앱 향상 서비스' 켜기 |
| 5 | 게임 등록 | '+ Manage' 에서 게임 추가 (직접 확인) |
| 6 | 앱 서랍 아이콘 켜기 (**필수**) | More settings → Add to the Home screen → Add |
| 7 | Game Center 삭제 (선택) | 지우거나 '유지하기' |

> **6단계의 앱 서랍 아이콘은 꼭 켜 두세요.** 버튼 이름은 "Add to the Home screen" 이지만 홈 화면이 아니라
> **앱 서랍에 '게임 지원' 아이콘**을 켭니다. Game Center 없이 게임 지원을 여는 입구는 이 아이콘뿐입니다.
> (동의 화면에서 생기는 홈 화면 바로가기는 게임 지원을 껐다 켜면 지워지지만, 앱 서랍 아이콘은 남습니다.)

설정이 끝나면 게임을 실행하고 화면 왼쪽 가장자리를 안쪽으로 밀면 패널이 열립니다.
Game Center를 유지하면 GASH 아이콘이 게임 지원을 바로 열고, 지웠다면 앱 서랍의 '게임 지원' 아이콘을 쓰면 됩니다.
GASH가 더 필요 없으면 설정이 끝난 화면 맨 아래의 **GASH 지우기** 로 지울 수 있습니다.

## 왜 Game Center가 필요한가

게임 지원 화면은 OPPO 시스템 권한이 있어야 열리므로 일반 앱이나 adb로는 직접 열 수 없습니다.
Game Center에는 누구나 부를 수 있는 입구가 있어, **처음 한 번은** Game Center를 거쳐 게임 지원을 엽니다.
6단계에서 게임 지원 자체의 앱 서랍 아이콘을 켜 두면 그 뒤로는 Game Center 없이도 열 수 있습니다.

자세한 내용(권한 구조, 단계 판정 코드, 설치 처리)은 [How it works detailed](How%20it%20works%20detailed.md)를 참고하세요.

## 빌드

JDK와 Android SDK(build-tools, platform)가 필요합니다. Gradle은 쓰지 않습니다.

```bash
bash build.sh                              # 디버그 서명 → GameAssistantSetup.apk
GASH_KS=/path/to/release.jks bash build.sh # 릴리즈 서명 → GASH-release.apk
```

버전은 `build.sh` 의 `VERSION` 에 있고 [Semantic Versioning](https://semver.org/)을 따릅니다. 변경 내역은 [CHANGELOG.md](CHANGELOG.md).

## 참고

- 이 프로젝트는 OPPO와 관련이 없습니다. OPPO, ColorOS, Game Center는 각 소유자의 상표이며, GASH는 OPPO의 코드나 이미지를 포함하지 않습니다.
- 설치, 동의, 설정 변경은 사용자가 직접 눌러야 합니다. GASH는 해당 화면으로 안내만 합니다.

## License

[MIT](LICENSE)
