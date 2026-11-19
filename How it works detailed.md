# How it works — 자세히

게임 지원이 왜 일반 앱으로 열리지 않는지, GASH가 각 단계를 어떻게 판정하고 처리하는지 코드와 함께 설명합니다.

> 확인한 환경: OPPO Pad Mini (OPD2515) / ColorOS 17.0.1.201 (CN01) / 게임 지원 10.43.20 / 앱 향상 서비스 17.0.49 / Game Center 14.43.5.
> 다른 버전에서는 이름·동작이 다를 수 있습니다. 코드 인용은 [`Main.java`](src/io/github/jngvedere/gash/Main.java) 기준입니다.

---

## 1. 관련 패키지

| 패키지 | 이름 | 역할 |
|---|---|---|
| `com.oplus.games` | 게임 지원 (Game Assistant / Game Space) | 게임 목록·설정 화면. 기본 탑재, 런처 아이콘 숨김 |
| `com.oplus.cosa` | 앱 향상 서비스 (COSA) | 게임 실행을 감지해 게임 중 패널(HyperBoost)을 띄움 |
| `com.nearme.gamecenter` | Game Center | 게임 스토어. 지울 수 있는 기본 앱 (`/my_stock/del-app/Gamecenter/Gamecenter.apk`) |
| `com.android.launcher` | 시스템 런처 | 게임 지원의 아이콘·바로가기를 실행 |

## 2. 게임 지원은 왜 직접 열 수 없나

게임 지원의 액티비티에는 모두 `android:permission="oppo.permission.OPPO_COMPONENT_SAFE"` 가 걸려 있습니다.
이 권한은 OPPO 서명 시스템 앱만 받을 수 있습니다.

| 호출하는 쪽 | `OPPO_COMPONENT_SAFE` | 결과 |
|---|---|---|
| `com.android.launcher` | 있음 (`granted=true`) | 열림 |
| `com.nearme.gamecenter` | 있음 (시스템 앱) | 열림 |
| adb shell (uid 2000) | 없음 | `SecurityException: Permission Denial` |
| 일반 앱 (GASH 포함) | 없음 | `SecurityException` |

Android는 `startActivity()` 시 **호출한 앱(caller)** 의 권한을 검사합니다. 그래서 일반 앱이나 adb는 게임 지원의 어떤 화면도 직접 열 수 없습니다.

게임 지원의 런처 항목(activity-alias)도 처음엔 전부 꺼져 있습니다.

```
com.oplus.assistant.activity.StartActivity
com.oplus.assistant.activity.SpeedUpStartActivity      ← 6단계에서 켜지는 '게임 지원' 앱 서랍 아이콘
com.oplus.assistant.activity.GameManagerStartActivity
com.oplus.assistant.activity.WelfareAStartActivity
com.oplus.assistant.activity.WelfareBStartActivity
```

shell에서 `pm enable` / `pm disable` 로 바꾸려 하면 `Shell cannot change component state` 로 거부됩니다.
켜는 방법은 게임 지원 안의 More settings → Add to the Home screen 뿐입니다.

## 3. Game Center의 공개 입구

Game Center에는 권한 없이 누구나 호출할 수 있는 액티비티가 있습니다.

```xml
<activity
    android:name="com.nearme.gamespace.desktopspace.ui.DesktopSpaceShortCutActivity"
    android:exported="true">            <!-- android:permission 없음 -->
    <intent-filter>
        <action android:name="com.nearme.gamecenter.action.desktop.space.main" />
        ...
    </intent-filter>
</activity>
```

이 액티비티는 받은 요청을 **Game Center 자신의 권한**(`com.oplus.permission.safe.GAME`)으로 게임 지원의
`business.module.desktop.JumpSpaceActivity` 에 넘기고, 최종적으로 `DesktopSpaceMainActivity`(게임 지원 메인)가 열립니다.
처음 상태라면 여기서 "Welcome to Game Assistant." 동의 화면이 나옵니다.

GASH는 이 입구를 이렇게 부릅니다.

```java
// Main.launch()
startActivity(new Intent("com.nearme.gamecenter.action.desktop.space.main")
        .setPackage("com.nearme.gamecenter")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
```

같은 입구는 adb(`am start -a com.nearme.gamecenter.action.desktop.space.main -p com.nearme.gamecenter`)나
F-Droid의 Activity Launcher 앱으로도 열립니다. Game Center 앱 자체의 메뉴(Me → 설정 → Game Assistant 등)에는
게임 지원 메인으로 들어가는 버튼이 없었습니다(설정 페이지는 토글만 있는 `GameAssistantSettingActivity`).

## 4. 설정 후 게임 지원에 들어가는 세 가지 입구

| 입구 | 만들어지는 곳 | 실행 주체 | Game Center 필요 |
|---|---|---|---|
| ① Game Center 입구 | Game Center 설치 | Game Center | 필요 |
| ② 홈 화면 바로가기 | 동의 화면의 "Add to Home screen" | 런처 (게임 지원이 만든 pinned shortcut이라 게임 지원 신원으로 실행) | 불필요 |
| ③ 앱 서랍 '게임 지원' | More settings → Add to the Home screen → Add (`SpeedUpStartActivity` 활성화) | 런처 | 불필요 |

- ②③은 권한을 가진 런처가 실행하므로 Game Center 없이도 열립니다.
- GASH는 ②③을 **실행할 수 없습니다.** 권한 검사가 GASH 기준으로 이뤄지기 때문입니다. 그래서 GASH는 항상 ①만 씁니다.
- 대신 ③이 켜졌는지는 **확인할 수 있습니다.** LAUNCHER 항목 조회는 권한이 필요 없습니다.

```java
// Main.drawerIcon()
Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage("com.oplus.games");
return !getPackageManager().queryIntentActivities(i, 0).isEmpty();
```

- 게임 지원을 '사용 안 함'으로 바꾸면 ②(pinned shortcut)는 런처에서 지워집니다. ③의 활성화 상태는 게임 지원의 데이터를 지워도 남았습니다.
- 그래서 **③(앱 서랍 아이콘)은 반드시 활성화해야 합니다.** Game Center 없이 남는 입구는 사실상 ③뿐이고, GASH도 ③이 켜지기 전에는 7단계(Game Center 삭제)를 막습니다.
- 두 "홈 화면에 추가" 버튼은 서로 다릅니다. 동의 화면의 **Add to Home screen** 토글은 ②(홈 화면 바로가기)를 만들고,
  More settings의 **Add to the Home screen → Add** 는 ③(앱 서랍 아이콘)만 켭니다(홈 화면에는 놓이지 않음).
  10.43.20의 동의 화면에서는 토글이 보이지 않았습니다.

## 5. 게임 중 패널은 별개

게임 중 패널(왼쪽 가장자리 스와이프, `GameFloatBarView`)은 게임 지원 화면이 아니라 **앱 향상 서비스(COSA)** 가 띄웁니다.
아래 상태만 맞으면 ①②③이 모두 없어도 패널은 동작합니다.

| 상태 | 저장 위치 | 값 |
|---|---|---|
| 게임 지원 동의 | `Settings.System com.oplus.games_get_cta_auth_state` | 동의 `1`, 미동의 `-1`, 데이터 삭제 직후 값 없음 |
| 앱 향상 서비스 | `Settings.Secure key_com_oplus_cosa` | 켬 `1`, 끔 `0` |
| 개선 서비스의 '게임 지원' 스위치 | `Settings.Secure key_com_oplus_games_boot_setting`, `com_oplus_games_boot_start` | 동의와 연동. 끄면 `0`, 동의하면 다시 켜짐 |
| 게임 등록 | 게임 지원 내부 데이터 | 설치된 게임은 자동으로 잡히는 경우가 많음 |

- 이 값들은 **읽기는 누구나** 할 수 있어서 GASH가 상태 판정에 씁니다.
- `key_com_oplus_cosa` 는 실제 토글을 비추는 값일 뿐이라, adb `settings put` 으로 바꾸면 화면과 실제 상태가 어긋납니다. 반드시 설정 화면에서 바꿔야 합니다.

```java
// Main.setupDone(): 동의 여부
"1".equals(Settings.System.getString(getContentResolver(), "com.oplus.games_get_cta_auth_state"));

// Main.cosaOff(): "0"일 때만 꺼짐으로 봄 (읽을 수 없거나 값이 없으면 막지 않음)
"0".equals(Settings.Secure.getString(getContentResolver(), "key_com_oplus_cosa"));
```

## 6. GASH의 단계 판정

`Main.steps()` 가 매번(`onResume` → `refresh()`) 아래처럼 상태를 다시 읽어 7개 단계를 만듭니다.
`pkg()` 는 `0` 없음 / `1` 꺼짐 / `2` 켜짐을 돌려줍니다(`ApplicationInfo.enabled`).

| # | 단계 | 완료 조건 | 카드를 누르면 |
|---|---|---|---|
| 1 | 게임 지원 앱 | `pkg(GA) == 2` | 없음 → 기기 안 원본 APK 설치 시도 / 꺼짐 → 앱 정보 |
| 2 | Game Center | `pkg(GC) == 2` 또는 `setupDone() && drawerIcon()` (더 이상 불필요) | 없음 → 기기 안 APK 설치 / 꺼짐 → 앱 정보 |
| 3 | 동의 | `setupDone()` | `launch()` (① 입구) |
| 4 | 앱 향상 서비스 | `!cosaOff()` | `openEnhancement()` |
| 5 | 게임 등록 | 판정 불가 (항상 "직접 확인") | `launch()` |
| 6 | 앱 서랍 아이콘 | `drawerIcon()` | `launch()` (More settings 경로 안내) |
| 7 | Game Center 삭제 (선택) | Game Center 없음 && 2단계가 불필요 / 또는 '유지하기' 선택(`keepGc`) | `ACTION_DELETE` (6단계 전에는 막음) |

- 진행 중 단계 = 정보용(5단계)을 뺀 첫 번째 미완료 단계.
- 7단계를 6단계 전에 막는 이유: ③ 없이 Game Center를 지우면 게임 지원에 들어갈 입구가 하나도 남지 않습니다.

### 아이콘을 누르면 바로 열리는 조건

```java
// Main.ready()
pkg(GA) == 2 && pkg(GC) == 2 && setupDone() && !cosaOff() && drawerIcon() && prefs().getBoolean("keepGc", false)
```

이 조건이면 `onResume()` 에서 단계 화면을 그리지 않고 `launch()` 후 `done()` 합니다.
모든 단계가 끝났는데 `ready()` 가 아닌 경우(예: Game Center 삭제)에는 단계 화면 맨 아래에 **GASH 지우기** 버튼(`ACTION_DELETE` → 자기 패키지)이 나타납니다.
`done()` 은 `finishAndRemoveTask()` 로 최근 앱에서 지우고, `onDestroy()` 에서 프로세스까지 종료합니다.

## 7. 설치 처리 (1·2단계)

기기 펌웨어에 들어 있는 원본 APK를 `PackageInstaller` 세션으로 설치합니다.

| 대상 | 경로 |
|---|---|
| Game Center | `/my_stock/del-app/Gamecenter/Gamecenter.apk` |
| 게임 지원 | `/product/priv-app/OplusGames/OplusGames.apk` (재설치는 미검증) |

1. `canRequestPackageInstalls()` 가 거짓이면 '이 출처의 앱 설치 허용' 화면을 열고 `pendingInstall` 에 기억해 둡니다.
   돌아왔을 때(`onResume`) 권한이 생겼으면 설치를 자동으로 이어갑니다.
2. 세션에 APK를 쓰고 `commit()` 합니다. 결과는 `PendingIntent` 로 GASH(`onNewIntent`)에 돌아옵니다.
3. `STATUS_PENDING_USER_ACTION` 이면 시스템 확인 화면을 띄웁니다. ColorOS는 자체 설치 화면(`com.oplus.appdetail`)이 이어받아
   확인을 **두 번** 받습니다("Personal information will be collected" → "No risks found").
4. ColorOS 설치 화면이 끼어들면 실제로는 설치됐는데도 실패 상태 코드가 오는 경우가 있어, 상태 코드 대신 **패키지가 실제로 생겼는지**로 판정합니다.

```java
// Main.onNewIntent()
if (st != PackageInstaller.STATUS_SUCCESS && installing != null && pkg(installing) == 0) {
    toast("설치되지 않았습니다 ...");
}
```

5. 원본 APK를 읽을 수 없으면 Game Center는 OPPO 앱 스토어(`market://details?id=com.nearme.gamecenter`)로, 게임 지원은 adb `pm install-existing` 안내로 넘어갑니다.

## 8. 개선 서비스 화면 바로 열기 (4단계)

```java
// Main.openEnhancement()
startActivity(new Intent("oplus.intent.action.settings.STRENGTHEN_SERVICE"));
```

이 액션은 `oplus.permission.settings.LAUNCH_FOR_EXPORT` 권한을 요구합니다. 이 권한은 protectionLevel `normal` 이라
매니페스트에 선언만 하면 설치 시 자동으로 부여됩니다. (adb shell에는 이 권한이 없어 같은 명령이 거부됩니다.)
열지 못하면 일반 설정 화면을 열고 경로(설정 → 시스템 및 업데이트 → 개선 서비스)를 안내합니다.

## 9. 매니페스트

| 항목 | 이유 |
|---|---|
| `REQUEST_INSTALL_PACKAGES` | 1·2단계 설치 |
| `REQUEST_DELETE_PACKAGES` | 7단계 Game Center 삭제 화면 |
| `oplus.permission.settings.LAUNCH_FOR_EXPORT` | 4단계 개선 서비스 화면 |
| `<queries>` `com.oplus.games`, `com.nearme.gamecenter`, `com.heytap.market` | Android 11+ 패키지 가시성: 설치·켜짐 상태 조회, 스토어 열기 |
| 액티비티 `launchMode="singleTop"` | 설치 결과 `PendingIntent` 를 같은 화면의 `onNewIntent` 로 받기 위해 |

## 10. 이 펌웨어에서 막혀 있는 것 (adb)

| 시도 | 결과 |
|---|---|
| `pm uninstall --user 0 com.oplus.games` | `only root can delete system app for a particular user` (비활성화만 가능) |
| `pm uninstall --user 0 com.nearme.gamecenter` | `DELETE_FAILED_INTERNAL_ERROR` (ColorOS 보호 앱, 일반 삭제 화면으로만 가능) |
| `pm clear com.oplus.games` | `adb clearing user data is forbidden` (앱 정보 화면에서만 가능) |
| `pm enable/disable com.oplus.games/...SpeedUpStartActivity` | `Shell cannot change component state` |
| `am start` 로 게임 지원 화면 직접 열기 | `Permission Denial` (`OPPO_COMPONENT_SAFE`) |
| `am start -a oplus.intent.action.settings.STRENGTHEN_SERVICE` | `requires oplus.permission.settings.LAUNCH_FOR_EXPORT` |

## 11. 상태 확인 명령 (adb)

```bash
# 동의 (1 = 완료)
adb shell settings get system com.oplus.games_get_cta_auth_state

# 앱 향상 서비스 (1 = 켜짐)
adb shell settings get secure key_com_oplus_cosa

# 앱 서랍 '게임 지원' 아이콘(③)이 켜져 있는지
adb shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER com.oplus.games

# 게임 실행 중 패널 핸들이 붙었는지
adb shell dumpsys window windows | grep GameFloatBarView
```
