package io.zealler.googleregion;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

/**
 * 设置界面：点选一个区域预设即保存。
 * 配置存本应用的 SharedPreferences（region_config），并把文件设为全局可读，
 * 供目标进程里的 hook 侧用 XSharedPreferences 读取。
 */
public class MainActivity extends Activity {

    static final String PREFS_NAME = "region_config";
    static final String KEY_PRESET_ID = "preset_id";

    private ListView listView;
    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusView = findViewById(R.id.status);
        listView = findViewById(R.id.preset_list);
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        String[] labels = new String[RegionPreset.ALL.length];
        for (int i = 0; i < RegionPreset.ALL.length; i++) {
            RegionPreset p = RegionPreset.ALL[i];
            labels[i] = p.displayName + "（" + p.countryIso + " · " + p.operatorName
                    + " " + p.operatorNumeric + " · " + p.timeZoneId + "）";
        }
        listView.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_single_choice, labels));

        RegionPreset current = RegionPreset.byId(
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .getString(KEY_PRESET_ID, "us"));
        for (int i = 0; i < RegionPreset.ALL.length; i++) {
            if (RegionPreset.ALL[i].id.equals(current.id)) {
                listView.setItemChecked(i, true);
                break;
            }
        }
        updateStatus(current);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                RegionPreset picked = RegionPreset.ALL[position];
                save(picked);
                updateStatus(picked);
                Toast.makeText(MainActivity.this,
                        "已保存：" + picked.displayName
                                + "。请强停对应的谷歌应用（Play 商店再清一次缓存）后生效",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateStatus(RegionPreset p) {
        statusView.setText("当前预设：" + p.displayName
                + "\n国家码 " + p.countryIso + " · 运营商 " + p.operatorName
                + "（" + p.operatorNumeric + "）"
                + "\n语言 " + p.language + "-" + p.country + " · 时区 " + p.timeZoneId
                + "\n\n本模块只伪装应用读到的区域信号（SIM/网络国家、运营商、语言、时区），"
                + "不改变 IP 出口，IP 请配合代理使用。");
    }

    private void save(RegionPreset p) {
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        sp.edit().putString(KEY_PRESET_ID, p.id).commit();
        makePrefsWorldReadable();
    }

    /** 让目标进程的 XSharedPreferences 能读到配置文件。失败也不影响保存本身。 */
    private void makePrefsWorldReadable() {
        try {
            File dataDir = new File(getApplicationInfo().dataDir);
            File prefsDir = new File(dataDir, "shared_prefs");
            File prefsFile = new File(prefsDir, PREFS_NAME + ".xml");
            dataDir.setExecutable(true, false);
            prefsDir.setExecutable(true, false);
            prefsDir.setReadable(true, false);
            if (prefsFile.exists()) {
                prefsFile.setReadable(true, false);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 首次打开就把默认配置落盘并放开读权限，hook 侧随时有配置可读
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (!sp.contains(KEY_PRESET_ID)) {
            sp.edit().putString(KEY_PRESET_ID, "us").commit();
        }
        makePrefsWorldReadable();
    }
}
