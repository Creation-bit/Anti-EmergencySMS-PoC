package dev.goyo.alertcontrol;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import dev.goyo.alertcontrol.core.RestoreStore;
import dev.goyo.alertcontrol.core.Targets;

final class PreferenceRestoreStore implements RestoreStore {
    private final SharedPreferences preferences;
    PreferenceRestoreStore(Context context, int userId) {
        preferences = context.getSharedPreferences("restore_user_" + userId, Context.MODE_PRIVATE);
    }

    @Override public synchronized Map<String, Integer> load() throws Exception {
        JSONObject json = new JSONObject(preferences.getString("states", "{}"));
        Map<String, Integer> states = new LinkedHashMap<>();
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String name = keys.next();
            int state = json.getInt(name);
            Targets.requirePackage(name);
            Targets.requireState(state);
            states.put(name, state);
        }
        return states;
    }

    @Override public synchronized void save(Map<String, Integer> states) throws Exception {
        JSONObject json = new JSONObject();
        for (Map.Entry<String, Integer> entry : states.entrySet()) {
            Targets.requirePackage(entry.getKey());
            Targets.requireState(entry.getValue());
            json.put(entry.getKey(), entry.getValue());
        }
        // apply() is asynchronous and cannot guarantee restore data survives a crash.
        if (!preferences.edit().putString("states", json.toString()).commit()) {
            throw new IllegalStateException("복원 기록을 저장하지 못해 변경을 중단했습니다.");
        }
    }
}
