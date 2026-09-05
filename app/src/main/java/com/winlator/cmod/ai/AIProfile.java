package com.winlator.cmod.ai;

import android.content.Context;
import android.util.Log;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.Shortcut;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class AIProfile {

    public static class Run {
        public long timestamp;
        public long durationMs;
        public float fpsAvg;
        public float fpsMin;
        public boolean crashed;
    }

    private static final int MAX_RUNS = 8;
    private static final String DIR_NAME = "ai_profiles";
    private static final String TAG = "AIProfile";

    private final File file;
    private JSONObject data;
    private final java.util.List<Float> currentSamples = new java.util.ArrayList<>();
    private long runStartMs = 0;
    private boolean runCrashed = false;
    private Context context;

    private AIProfile(File file) {
        this.file = file;
        this.data = load(file);
    }

    public static AIProfile forShortcut(Context context, Shortcut shortcut, Container container) {
        if (shortcut == null || shortcut.path == null || container == null) return null;
        String exeName = AIConfigEngine.exeNameOf(shortcut.path);
        if (exeName == null || exeName.isEmpty()) return null;

        File dir = new File(container.getRootDir(), DIR_NAME);
        if (!dir.exists() && !dir.mkdirs()) return null;
        return new AIProfile(new File(dir, exeName.replaceAll("[^A-Za-z0-9._-]", "_") + ".json"));
    }

    private static JSONObject load(File file) {
        if (!file.exists()) return new JSONObject();
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[(int) Math.min(file.length(), 1 << 20)];
            int read = fis.read(buf);
            if (read > 0) {
                String json = new String(buf, 0, read, StandardCharsets.UTF_8);
                JSONObject obj = new JSONObject(json);
                if (!obj.has("runs")) obj.put("runs", new JSONArray());
                return obj;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load profile", e);
        }
        return new JSONObject();
    }

    public void beginRun() {
        currentSamples.clear();
        runCrashed = false;
        runStartMs = System.currentTimeMillis();
    }

    public void addFpsSample(float fps) {
        if (fps > 0) currentSamples.add(fps);
    }

    public void markCrashed() {
        runCrashed = true;
    }

    public void endRun() {
        if (runStartMs == 0) return;
        long durationMs = System.currentTimeMillis() - runStartMs;

        float sum = 0;
        float min = Float.MAX_VALUE;
        for (Float s : currentSamples) {
            sum += s;
            if (s < min) min = s;
        }
        float avg = currentSamples.isEmpty() ? 0 : sum / currentSamples.size();

        try {
            JSONObject run = new JSONObject();
            run.put("timestamp", System.currentTimeMillis());
            run.put("durationMs", durationMs);
            run.put("fpsAvg", Math.round(avg * 10) / 10f);
            run.put("fpsMin", currentSamples.isEmpty() ? 0 : Math.round(min * 10) / 10f);
            run.put("samples", currentSamples.size());
            run.put("crashed", runCrashed);

            JSONArray runs = data.getJSONArray("runs");
            runs.put(run);
            while (runs.length() > MAX_RUNS) runs.remove(0);
            data.put("runs", runs);
            save();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save run", e);
        }

        currentSamples.clear();
        runStartMs = 0;
    }

    private void save() {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.e(TAG, "Failed to write profile", e);
        }
    }

    public Run getLastRun() {
        try {
            JSONArray runs = data.getJSONArray("runs");
            if (runs.length() == 0) return null;
            JSONObject o = runs.getJSONObject(runs.length() - 1);
            Run run = new Run();
            run.timestamp = o.optLong("timestamp");
            run.durationMs = o.optLong("durationMs");
            run.fpsAvg = (float) o.optDouble("fpsAvg", 0);
            run.fpsMin = (float) o.optDouble("fpsMin", 0);
            run.crashed = o.optBoolean("crashed", false);
            return run;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean hadCrashRecently(int lookback) {
        try {
            JSONArray runs = data.getJSONArray("runs");
            int start = Math.max(0, runs.length() - lookback);
            for (int i = start; i < runs.length(); i++) {
                if (runs.getJSONObject(i).optBoolean("crashed", false)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    public float getRecentAvgFPS(int lookback) {
        float sum = 0;
        int n = 0;
        try {
            JSONArray runs = data.getJSONArray("runs");
            int start = Math.max(0, runs.length() - lookback);
            for (int i = start; i < runs.length(); i++) {
                float avg = (float) runs.getJSONObject(i).optDouble("fpsAvg", 0);
                if (avg > 0) {
                    sum += avg;
                    n++;
                }
            }
        } catch (Exception ignored) {}
        return n == 0 ? 0 : sum / n;
    }

    public boolean hasRunData() {
        try {
            return data.getJSONArray("runs").length() > 0;
        } catch (Exception e) {
            return false;
        }
    }
}