package io.github.jngvedere.gash;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Step-by-step setup for Game Assistant. Each step shows done / in progress / waiting and opens the screen
 * where it is done. Once everything is set up (and Game Center is kept), tapping the icon opens Game Assistant directly.
 * Game Assistant can only be opened through Game Center's exported entry, the only route a normal app has.
 */
public class Main extends Activity {
    static final String GA = "com.oplus.games";
    static final String GC = "com.nearme.gamecenter";
    static final String MARKET = "com.heytap.market";
    static final String GC_APK = "/my_stock/del-app/Gamecenter/Gamecenter.apk"; // firmware copy (removable preload)
    static final String GA_APK = "/product/priv-app/OplusGames/OplusGames.apk";  // firmware copy (system app)
    static final String ACT_OPEN_SPACE = "com.nearme.gamecenter.action.desktop.space.main";
    static final String ACT_INSTALL = "io.github.jngvedere.gash.INSTALL_RESULT";
    static final String KEY_COSA = "key_com_oplus_cosa"; // Settings.Secure: "1" on, "0" off
    static final String KEY_CTA = "com.oplus.games_get_cta_auth_state"; // Settings.System: "1" = consent given
    static final int ACCENT = 0xFF2E9E5B;

    static final int WAIT = 0, NOW = 1, DONE = 2, INFO = 3;

    boolean busy, launched;
    String installing; // package currently being installed
    TextView progress, banner;
    LinearLayout list;
    Button openBtn;

    @Override
    protected void onCreate(android.os.Bundle b) {
        super.onCreate(b);
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!busy && ready() && launch()) {
            done();
            return;
        }
        refresh();
        // Back from "install unknown apps": carry on with the install the user asked for.
        String p = pendingInstall;
        pendingInstall = null;
        if (p != null && getPackageManager().canRequestPackageInstalls()) install(GA.equals(p) ? GA_APK : GC_APK, p);
    }

    String pendingInstall; // install that was waiting for the "install unknown apps" permission

    /** Everything set up and Game Center kept: the icon just opens Game Assistant. */
    boolean ready() {
        return pkg(GA) == 2 && pkg(GC) == 2 && setupDone() && !cosaOff() && drawerIcon()
                && prefs().getBoolean("keepGc", false);
    }

    /** Game Assistant is open: drop this app from Recents and end its process. */
    void done() {
        launched = true;
        finishAndRemoveTask();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (launched) android.os.Process.killProcess(android.os.Process.myPid());
    }

    /** Open Game Assistant through Game Center. false = not possible right now. */
    boolean launch() {
        try {
            startActivity(new Intent(ACT_OPEN_SPACE).setPackage(GC).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Step actions that need Game Assistant open; this app stays in the background and refreshes on return. */
    void openGa() {
        if (pkg(GA) != 2) toast("1단계를 먼저 끝내세요.");
        else if (pkg(GC) != 2) toast(setupDone()
                ? "Game Center가 없어 이 앱으로는 열 수 없습니다. 앱 서랍의 '게임 지원' 아이콘으로 여세요."
                : "2단계(Game Center)를 먼저 끝내세요.");
        else if (!launch()) toast("게임 지원을 열지 못했습니다. Game Center를 업데이트한 뒤 다시 시도하세요.");
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (!ACT_INSTALL.equals(i.getAction())) return;
        int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) startActivity(confirm);
            return;
        }
        busy = false;
        // ColorOS's own installer (com.oplus.appdetail) takes over the install and can report a non-success status
        // even though the app ended up installed, so trust the package state, not the status code.
        if (st != PackageInstaller.STATUS_SUCCESS && installing != null && pkg(installing) == 0) {
            toast("설치되지 않았습니다 (" + st + ": " + i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) + ")");
        }
        // onResume runs next and refreshes the steps.
    }

    // ---------- state ----------

    /** First setup is done once the user agreed on Game Assistant's welcome screen. */
    boolean setupDone() {
        try {
            return "1".equals(Settings.System.getString(getContentResolver(), KEY_CTA));
        } catch (Exception e) {
            return false;
        }
    }

    /** App Enhancement Services runs the in-game panel. Only "0" counts as off; unreadable/unset doesn't block. */
    boolean cosaOff() {
        try {
            return "0".equals(Settings.Secure.getString(getContentResolver(), KEY_COSA));
        } catch (Exception e) {
            return false;
        }
    }

    /** Game Assistant's own app-drawer icon (More settings → Add to the Home screen) is turned on. */
    boolean drawerIcon() {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(GA);
        return !getPackageManager().queryIntentActivities(i, 0).isEmpty();
    }

    /** 0 = not installed, 1 = disabled, 2 = enabled */
    int pkg(String name) {
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(name, 0);
            return ai.enabled ? 2 : 1;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    android.content.SharedPreferences prefs() {
        return getSharedPreferences("s", MODE_PRIVATE);
    }

    // ---------- steps ----------

    static class Step {
        String title, desc;
        boolean done, info;
        View.OnClickListener act;
        String altLabel;
        View.OnClickListener alt;

        Step(String title, boolean done, String desc, View.OnClickListener act) {
            this.title = title;
            this.done = done;
            this.desc = desc;
            this.act = act;
        }
    }

    Step[] steps() {
        int ga = pkg(GA), gc = pkg(GC);
        boolean setup = setupDone(), cosa = !cosaOff(), drawer = drawerIcon(), keep = prefs().getBoolean("keepGc", false);
        String installingNote = "설치 파일을 준비하는 중입니다…";

        Step s1 = new Step("게임 지원 앱 확인", ga == 2,
                busy && GA.equals(installing) ? installingNote
                        : ga == 2 ? "기본 탑재된 게임 지원이 켜져 있습니다."
                        : ga == 1 ? "꺼져 있습니다. 앱 정보에서 '사용'을 누르세요."
                        : "지워져 있습니다. 눌러서 기기 안의 원본으로 다시 설치합니다.",
                v -> { if (ga == 0) install(GA_APK, GA); else appDetails(GA); });

        boolean gcNeeded = !(setup && drawer);
        Step s2 = new Step("Game Center 준비", gc == 2 || !gcNeeded,
                busy && GC.equals(installing) ? installingNote
                        : gc == 2 ? "설치되어 있습니다. 게임 지원을 처음 여는 데 필요합니다."
                        : !gcNeeded ? "최초 설정이 끝나 더 이상 필요 없습니다."
                        : gc == 1 ? "꺼져 있습니다. 앱 정보에서 '사용'을 누르세요."
                        : "게임 지원을 처음 열려면 필요합니다. 눌러서 설치합니다. (설치 화면에서 Install 두 번)",
                v -> {
                    if (gc == 0) install(GC_APK, GC);
                    else if (gc == 1) appDetails(GC);
                    else toast("이미 준비됐습니다.");
                });

        Step s3 = new Step("게임 지원 동의", setup,
                setup ? "동의를 마쳤습니다."
                        : "눌러서 게임 지원을 연 뒤 'Agree and continue'를 누르세요. 'Add to Home screen'은 켜 두는 걸 권장합니다.",
                v -> openGa());

        Step s4 = new Step("앱 향상 서비스 켜기", cosa,
                cosa ? "켜져 있습니다. 게임 중 패널이 동작합니다."
                        : "게임 중 패널을 띄우는 서비스입니다. 눌러서 목록 아래쪽의 '앱 향상 서비스'를 켜세요.",
                v -> openEnhancement());

        Step s5 = new Step("게임 등록", false,
                "게임 지원 왼쪽의 '+ Manage' → 게임 토글 → 'Add'. 이 앱에서는 등록 여부를 확인할 수 없으니 게임 지원에서 직접 확인하세요.",
                v -> openGa());
        s5.info = true;

        Step s6 = new Step("앱 서랍 아이콘 만들기", drawer,
                drawer ? "앱 서랍에 '게임 지원' 아이콘이 있습니다. Game Center 없이도 이 아이콘으로 열립니다."
                        : "눌러서 게임 지원을 연 뒤 왼쪽 위 프로필 → More settings → Add to the Home screen → 'Add'.",
                v -> openGa());

        // Game Center missing only counts as "deleted" once it is no longer needed (setup done + drawer icon).
        Step s7 = new Step("Game Center 삭제 (선택)", gc == 0 ? !gcNeeded : keep,
                gc == 0 ? (gcNeeded ? "지금은 Game Center가 필요합니다. 설정이 끝난 뒤에 지울 수 있습니다."
                        : "삭제했습니다. 게임 지원은 앱 서랍의 '게임 지원' 아이콘으로 여세요.")
                        : keep ? "유지하기로 했습니다. 이 앱 아이콘을 누르면 게임 지원이 바로 열립니다. 눌러서 삭제할 수도 있습니다."
                        : "설정이 끝나면 지워도 됩니다. 대신 이 앱으로는 게임 지원을 열 수 없고 앱 서랍 아이콘을 써야 합니다.",
                v -> {
                    if (gc == 0) toast(gcNeeded ? "2단계(Game Center)부터 진행하세요." : "이미 삭제했습니다.");
                    else if (!drawer) toast("6단계(앱 서랍 아이콘)를 먼저 끝내세요. 아이콘 없이 지우면 게임 지원을 열 입구가 없습니다.");
                    else open(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + GC)));
                });
        if (gc != 0 && !keep) {
            s7.altLabel = "유지하기";
            s7.alt = v -> {
                prefs().edit().putBoolean("keepGc", true).apply();
                refresh();
            };
        }
        return new Step[]{s1, s2, s3, s4, s5, s6, s7};
    }

    void refresh() {
        ensureUi();
        Step[] st = steps();
        int doneCount = 0, total = 0, current = -1;
        for (int i = 0; i < st.length; i++) {
            if (st[i].info) continue;
            total++;
            if (st[i].done) doneCount++;
            else if (current < 0) current = i;
        }
        progress.setText(doneCount + " / " + total + " 단계 완료");

        boolean allDone = current < 0;
        banner.setVisibility(allDone ? View.VISIBLE : View.GONE);
        banner.setText("✅ 설정이 끝났습니다. 등록한 게임을 실행하고 화면 왼쪽 가장자리를 안쪽으로 밀면 패널이 열립니다."
                + (pkg(GC) == 2 ? "" : "\n게임 지원은 앱 서랍의 '게임 지원' 아이콘으로 여세요."));

        list.removeAllViews();
        for (int i = 0; i < st.length; i++) {
            int state = st[i].info ? INFO : st[i].done ? DONE : i == current ? NOW : WAIT;
            list.addView(card(i + 1, st[i], state));
        }

        boolean canOpen = pkg(GA) == 2 && pkg(GC) == 2 && setupDone();
        openBtn.setVisibility(canOpen ? View.VISIBLE : View.GONE);
    }

    // ---------- UI ----------

    /** Built only when the steps are actually shown, so a direct launch shows nothing. */
    void ensureUi() {
        if (list != null) return;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(56), dp(24), dp(48));

        TextView title = text("게임 지원 설정", 26, true);
        progress = text("", 15, false);
        progress.setTextColor(ACCENT);
        progress.setPadding(0, dp(4), 0, dp(16));
        banner = text("", 15, false);
        banner.setPadding(dp(16), dp(14), dp(16), dp(14));
        banner.setBackground(round(0x332E9E5B, 0, 0));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(12), 0, dp(4));
        openBtn = new Button(this);
        openBtn.setText("게임 지원 열기");
        openBtn.setOnClickListener(v -> {
            if (launch()) done(); else toast("게임 지원을 열지 못했습니다.");
        });

        root.addView(title);
        root.addView(progress);
        root.addView(banner);
        root.addView(list);
        root.addView(openBtn);
        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }

    View card(int n, Step s, int state) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(14), dp(12), dp(14));
        card.setBackground(state == NOW ? round(0x222E9E5B, 2, ACCENT) : round(0x14808080, 0, 0));
        if (state == WAIT) card.setAlpha(0.55f);
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        card.setForeground(getDrawable(tv.resourceId));
        card.setOnClickListener(s.act);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);

        TextView badge = text(state == DONE ? "✓" : String.valueOf(n), 16, true);
        badge.setGravity(Gravity.CENTER);
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        if (state == DONE || state == NOW) {
            oval.setColor(ACCENT);
            badge.setTextColor(Color.WHITE);
        } else {
            oval.setColor(0x33808080);
        }
        badge.setBackground(oval);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(36), dp(36));
        bp.rightMargin = dp(14);
        card.addView(badge, bp);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(s.title, 17, true);
        TextView tag = text(new String[]{"대기", "진행 중 — 눌러서 진행", "완료", "직접 확인"}[state], 13, false);
        tag.setTextColor(state == NOW || state == DONE ? ACCENT : 0xFF888888);
        TextView desc = text(s.desc, 14, false);
        desc.setPadding(0, dp(4), 0, 0);
        col.addView(title);
        col.addView(tag);
        col.addView(desc);
        if (s.altLabel != null) {
            TextView alt = text(s.altLabel, 14, true);
            alt.setTextColor(ACCENT);
            alt.setPadding(0, dp(10), dp(16), dp(2));
            alt.setOnClickListener(s.alt);
            col.addView(alt, new LinearLayout.LayoutParams(-2, -2));
        }
        card.addView(col, new LinearLayout.LayoutParams(0, -2, 1));

        TextView arrow = text("›", 24, false);
        arrow.setPadding(dp(8), 0, 0, 0);
        arrow.setTextColor(0xFF888888);
        card.addView(arrow);
        return card;
    }

    TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    GradientDrawable round(int fill, int strokeDp, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(16));
        g.setColor(fill);
        if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeColor);
        return g;
    }

    // ---------- actions ----------

    /** Opens inside this app's task, so finishAndRemoveTask() also clears these screens from Recents. */
    void open(Intent i) {
        try {
            startActivity(i);
        } catch (Exception e) {
            toast("열 수 없습니다: " + e.getMessage());
        }
    }

    /** Settings → System & update → Enhancement services (OPPO action, no permission required). */
    void openEnhancement() {
        try {
            startActivity(new Intent("oplus.intent.action.settings.STRENGTHEN_SERVICE"));
        } catch (Exception e) {
            toast("설정 → 시스템 및 업데이트 → 개선 서비스 → 앱 향상 서비스");
            open(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    void appDetails(String p) {
        open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + p)));
    }

    void openStore() {
        Uri u = Uri.parse("market://details?id=" + GC);
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, u).setPackage(MARKET));
        } catch (Exception e) {
            open(new Intent(Intent.ACTION_VIEW, u));
        }
    }

    /** Can't install from the firmware copy: Game Center has a store page, Game Assistant does not. */
    void installFallback(String pkg, String why) {
        if (GC.equals(pkg)) {
            toast(why + " 앱 스토어로 이동합니다.");
            openStore();
        } else {
            toast(why + " PC에서 'adb shell pm install-existing " + GA + "'를 실행하거나 공장 초기화가 필요합니다.");
        }
    }

    void install(String path, String pkg) {
        if (busy) return;
        if (!getPackageManager().canRequestPackageInstalls()) {
            toast("'이 출처의 앱 설치 허용'을 켠 뒤 돌아오면 설치가 이어집니다.");
            pendingInstall = pkg;
            open(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            return;
        }
        File apk = new File(path);
        if (!apk.canRead()) {
            installFallback(pkg, "기기 안의 설치 파일을 읽을 수 없습니다.");
            return;
        }
        prefs().edit().remove("keepGc").apply(); // a reinstalled Game Center gets the "delete?" step again
        installing = pkg;
        busy = true;
        refresh();
        new Thread(() -> {
            try {
                PackageInstaller pi = getPackageManager().getPackageInstaller();
                int id = pi.createSession(new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL));
                try (PackageInstaller.Session s = pi.openSession(id)) {
                    try (InputStream in = new FileInputStream(apk); OutputStream out = s.openWrite("base.apk", 0, apk.length())) {
                        byte[] buf = new byte[1 << 16];
                        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                        s.fsync(out);
                    }
                    Intent cb = new Intent(this, Main.class).setAction(ACT_INSTALL);
                    PendingIntent pend = PendingIntent.getActivity(this, 0, cb,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                    s.commit(pend.getIntentSender());
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    busy = false;
                    installFallback(pkg, "기기 안의 설치 파일로 설치하지 못했습니다(" + e.getMessage() + ").");
                    refresh();
                });
            }
        }).start();
    }

    void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
