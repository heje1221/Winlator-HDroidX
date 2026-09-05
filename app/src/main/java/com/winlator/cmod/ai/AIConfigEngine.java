package com.winlator.cmod.ai;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;

import java.util.Locale;

public class AIConfigEngine {

    public static class DeviceSpec {
        public String socModel = "";
        public String socName = "";
        public String socTier = "mid";
        public String gpuFamily = "adreno";
        public String android = "";
        public int cores = 4;
        public long ramMb = 4096;
    }

    public static class Game {
        public String name;
        public String[] exePatterns;
        public int dx;
        public String tier;
        public String resolution;
        public String notes;
        public String dxvkVersion;
        public String vkd3dVersion;

        public Game(String name, String[] exePatterns, int dx, String tier, String resolution, String notes,
                    String dxvkVersion, String vkd3dVersion) {
            this.name = name;
            this.exePatterns = exePatterns;
            this.dx = dx;
            this.tier = tier;
            this.resolution = resolution;
            this.notes = notes;
            this.dxvkVersion = dxvkVersion;
            this.vkd3dVersion = vkd3dVersion;
        }

        public boolean matchesExe(String exeName) {
            if (exeName == null || exePatterns == null) return false;
            String lower = exeName.toLowerCase(Locale.ROOT);
            for (String p : exePatterns) {
                if (lower.contains(p)) return true;
            }
            return false;
        }
    }

    public static class Recommendation {
        public String resolution;
        public String graphicsDriverVersion;
        public String dxWrapper;
        public String dxWrapperConfig;
        public String box64Preset;
        public String box64Name;
        public String cpuList;
        public boolean showFPS;
        public String notes;
        public String summary;
    }

    private static final String[][] SOC_TABLE = {
            {"sm7675", "Snapdragon 7 Gen 3", "high"},
            {"sm7635", "Snapdragon 7s Gen 3", "mid"},
            {"sm7315", "Snapdragon 7s Gen 2", "mid"},
            {"sm8550", "Snapdragon 8 Gen 2", "flagship"},
            {"sm8650", "Snapdragon 8 Gen 3", "flagship"},
            {"sm8750", "Snapdragon 8 Elite", "flagship"},
            {"sm8450", "Snapdragon 8 Gen 1", "high"},
            {"sm8475", "Snapdragon 8+ Gen 1", "high"},
            {"sm8350", "Snapdragon 888", "high"},
            {"sm8250", "Snapdragon 865", "high"},
            {"sm8150", "Snapdragon 855", "mid"},
            {"sm845", "Snapdragon 845", "mid"},
            {"sm7325", "Snapdragon 778G", "mid"},
            {"mt6895", "Dimensity 8100", "high"},
            {"mt6893", "Dimensity 1200", "mid"},
            {"mt6985", "Dimensity 9300", "flagship"},
            {"exynos", "Exynos", "mid"},
    };

    public static final String ADRENO_DRIVER_VERSION = "turnip26.2.0";

    public static final Game[] GAMES = {
            new Game("Jump Force", new String[]{"jump"}, 11, "medium", "800x600",
                    "DX11, Bandai Namco, Playable 15-30 FPS. Use BT controller.",
                    "2.3.1", "None"),
            new Game("GTA V", new String[]{"gta5", "gtav", "playgtav"}, 11, "medium", "1280x720",
                    "DX11, heavy world, ~20-30 FPS low. Shadows OFF.",
                    "2.3.1", "None"),
            new Game("GTA IV Complete Edition", new String[]{"gta4", "gtai", "gtaiv"}, 9, "medium", "1280x720",
                    "DX9, keep the SAVE_FIX workflow to avoid corrupt saves.",
                    "2.3.1", "None"),
            new Game("Resident Evil 3 Remake", new String[]{"re3", "resident3", "biohazard_3"}, 12, "heavy", "800x600",
                    "DX12 (RE Engine), expect 10-20 FPS. Shadows OFF, quality LOW.",
                    "2.3.1", "2.14.1"),
            new Game("Resident Evil 7 Biohazard", new String[]{"re7", "resident7", "biohazard7", "re7config"}, 12, "heavy", "800x600",
                    "DX12 (RE Engine), expect 10-20 FPS. Fullscreen OFF may help.",
                    "2.3.1", "2.14.1"),
            new Game("Resident Evil Village", new String[]{"village", "re8", "residentevilvillage"}, 12, "heavy", "800x600",
                    "DX12 (RE Engine), heavy, expect 10-15 FPS. Use low res.",
                    "2.3.1", "2.14.1"),
            new Game("Left 4 Dead 2", new String[]{"l4d2", "left4dead"}, 9, "light", "1280x720",
                    "DX9, Source engine, runs well even on weak GPUs.",
                    "1.10.3", "None"),
            new Game("MetaTrader 5", new String[]{"terminal64", "mt5", "metatrader"}, 9, "light", "800x600",
                    "Trading terminal, low requirements. FPS counter not needed.",
                    "1.10.3", "None"),
            new Game("Custom / General", null, 11, "medium", null,
                    "Generic profile based on your device tier. Pick a real game for tuned settings.",
                    "2.3.1", "None"),
    };

    public static Game detectGame(String exeName) {
        if (exeName == null) return null;
        for (Game game : GAMES) {
            if (game.matchesExe(exeName)) return game;
        }
        return null;
    }

    public static DeviceSpec detect(Context context) {
        DeviceSpec spec = new DeviceSpec();
        spec.android = Build.VERSION.RELEASE;

        if (Build.VERSION.SDK_INT >= 31) {
            spec.socModel = Build.SOC_MODEL;
            String socManu = Build.SOC_MANUFACTURER == null ? "" : Build.SOC_MANUFACTURER.toLowerCase();
            if (socManu.contains("qualcomm")) spec.gpuFamily = "adreno";
            else if (socManu.contains("mediatek") || socManu.contains("samsung")) spec.gpuFamily = "mali";
        } else {
            String board = Build.BOARD == null ? "" : Build.BOARD.toLowerCase();
            if (board.contains("qcom") || board.contains("sm")) spec.gpuFamily = "adreno";
        }

        String[] soc = resolveSOC(spec.socModel);
        spec.socName = soc[0];
        spec.socTier = soc[1];

        spec.cores = Math.max(1, Runtime.getRuntime().availableProcessors());

        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                spec.ramMb = mi.totalMem / (1024L * 1024L);
            }
        } catch (Exception ignored) {}

        return spec;
    }

    private static String[] resolveSOC(String model) {
        String m = model == null ? "" : model.toLowerCase().trim();
        if (!m.isEmpty()) {
            for (String[] row : SOC_TABLE) {
                if (m.contains(row[0])) return new String[]{row[1], row[2]};
            }
            return new String[]{model, "mid"};
        }
        return new String[]{"Unknown SoC", "mid"};
    }

    private static int deviceScore(String tier) {
        switch (tier) {
            case "low": return 1;
            case "high": return 3;
            case "flagship": return 4;
            default: return 2;
        }
    }

    private static int gameScore(String tier) {
        switch (tier) {
            case "light": return 1;
            case "heavy": return 3;
            default: return 2;
        }
    }

    private static String coresString(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (sb.length() > 0) sb.append(",");
            sb.append(i);
        }
        return sb.toString();
    }

    private static String pickDXVKVersion(Game game, DeviceSpec spec) {
        String baseVersion = game.dxvkVersion != null ? game.dxvkVersion : "2.3.1";
        boolean lowEnd = "mid".equals(spec.socTier) || "low".equals(spec.socTier);
        boolean isARM64EC = false;
        String requested = baseVersion + (isARM64EC ? "-arm64ec-gplasync" : "");
        if (lowEnd && baseVersion.startsWith("2.") && !"heavy".equals(game.tier)) {
            return "1.10.3";
        }
        return requested;
    }

    private static String pickVKD3DVersion(Game game, DeviceSpec spec) {
        String baseVersion = game.vkd3dVersion != null ? game.vkd3dVersion : "None";
        if ("None".equals(baseVersion)) return "None";
        boolean lowEnd = "mid".equals(spec.socTier) || "low".equals(spec.socTier);
        if (lowEnd && baseVersion.startsWith("2.14")) return "2.8";
        return baseVersion;
    }

    private static String buildDXWrapperConfig(String dxvkVersion, String vkd3dVersion) {
        return "version=" + dxvkVersion
                + ",framerate=0,async=0,asyncCache=0"
                + ",vkd3dVersion=" + vkd3dVersion
                + ",vkd3dLevel=12_1"
                + ",ddrawrapper=None,csmt=3"
                + ",gpuName=NVIDIA GeForce GTX 480,videoMemorySize=2048"
                + ",strict_shader_math=1,OffscreenRenderingMode=fbo,renderer=gl";
    }

    public static Recommendation recommend(DeviceSpec spec, Game game) {
        Recommendation rec = new Recommendation();
        rec.notes = game.notes;

        if (game.resolution != null) {
            rec.resolution = game.resolution;
        } else {
            int combo = deviceScore(spec.socTier) - gameScore(game.tier) + 1;
            if (combo <= 0) rec.resolution = "640x480";
            else if (combo == 1) rec.resolution = "800x600";
            else if (combo == 2) rec.resolution = "1280x720";
            else rec.resolution = "1920x1080";
        }

        if (game.dx >= 12) rec.dxWrapper = "dxvk+vkd3d";
        else rec.dxWrapper = "dxvk+vkd3d";

        String dxvkVersion = pickDXVKVersion(game, spec);
        String vkd3dVersion = pickVKD3DVersion(game, spec);
        rec.dxWrapperConfig = buildDXWrapperConfig(dxvkVersion, vkd3dVersion);

        if ("adreno".equals(spec.gpuFamily)) {
            rec.graphicsDriverVersion = ADRENO_DRIVER_VERSION;
        } else {
            rec.graphicsDriverVersion = "";
        }

        if ("heavy".equals(game.tier)) {
            rec.box64Preset = "STABILITY";
            rec.box64Name = "Stability";
            rec.cpuList = coresString(Math.max(3, spec.cores / 2));
        } else if ("medium".equals(game.tier)) {
            rec.box64Preset = "PERFORMANCE";
            rec.box64Name = "Performance";
            rec.cpuList = coresString(Math.max(4, spec.cores - 2));
        } else {
            rec.box64Preset = "PERFORMANCE";
            rec.box64Name = "Performance";
            rec.cpuList = coresString(spec.cores);
        }

        boolean showFPSSuggested = !"MetaTrader 5".equals(game.name);
        rec.showFPS = showFPSSuggested;

        StringBuilder sb = new StringBuilder();
        sb.append("Device: ").append(spec.socName).append(" (").append(spec.socTier).append(" tier)\n");
        sb.append("GPU: ").append(spec.gpuFamily).append(" | RAM: ").append(spec.ramMb).append(" MB | Cores: ").append(spec.cores).append("\n\n");
        sb.append("GAME: ").append(game.name).append("\n");
        sb.append("Resolution: ").append(rec.resolution).append("\n");
        sb.append("Graphics driver: ").append(rec.graphicsDriverVersion.isEmpty() ? "system/virgl" : rec.graphicsDriverVersion).append("\n");
        sb.append("DX wrapper: ").append(rec.dxWrapper).append(" (DXVK ").append(dxvkVersion);
        if (!"None".equals(vkd3dVersion)) sb.append(" + VKD3D ").append(vkd3dVersion);
        sb.append(")\n");
        sb.append("CPU cores: ").append(rec.cpuList).append("\n");
        sb.append("Box64 preset: ").append(rec.box64Name).append("\n");
        if (rec.showFPS) sb.append("FPS counter: recommend ON\n");
        if (rec.notes != null) sb.append("\nNote: ").append(rec.notes);

        rec.summary = sb.toString();
        return rec;
    }

    public static String exeNameOf(String exePath) {
        if (exePath == null) return null;
        String p = exePath.replace("\\", "/").trim();
        int idx = p.lastIndexOf('/');
        String name = idx >= 0 ? p.substring(idx + 1) : p;
        if (name.startsWith("\"") && name.endsWith("\""))
            name = name.substring(1, name.length() - 1);
        return name;
    }

    private static final String[] RESOLUTION_LADDER = {
            "640x480", "800x600", "1280x720", "1920x1080"
    };

    public static String stepResolution(String current, int dir) {
        int idx = -1;
        for (int i = 0; i < RESOLUTION_LADDER.length; i++) {
            if (RESOLUTION_LADDER[i].equals(current)) {
                idx = i;
                break;
            }
        }
        if (idx == -1) return current;
        int next = idx + dir;
        if (next < 0) next = 0;
        if (next > RESOLUTION_LADDER.length - 1) next = RESOLUTION_LADDER.length - 1;
        return RESOLUTION_LADDER[next];
    }

    public static String tuneFromProfile(Recommendation rec, AIProfile profile) {
        if (profile == null || !profile.hasRunData()) return null;

        AIProfile.Run last = profile.getLastRun();
        boolean crashRecent = profile.hadCrashRecently(2);
        float avgFPS = profile.getRecentAvgFPS(3);

        StringBuilder hint = new StringBuilder();
        String origPreset = rec.box64Preset;

        if (crashRecent || (last != null && last.crashed)) {
            rec.box64Preset = "STABILITY";
            rec.box64Name = "Stability";
            rec.resolution = stepResolution(rec.resolution, -1);
            hint.append("crashed recently -> lower res + Box64 Stability");
        } else if (avgFPS > 0 && avgFPS < 18) {
            rec.resolution = stepResolution(rec.resolution, -1);
            hint.append("avg FPS ").append(String.format(Locale.US, "%.0f", avgFPS))
                    .append(" -> lowered resolution");
        } else if (avgFPS > 30) {
            rec.resolution = stepResolution(rec.resolution, 1);
            hint.append("avg FPS ").append(String.format(Locale.US, "%.0f", avgFPS))
                    .append(" -> raised resolution");
        }

        if (hint.length() == 0) return null;

        rec.summary += "\n\n[AUTO-TUNE] " + hint + ".\nLast run: avg "
                + String.format(Locale.US, "%.1f", last != null ? last.fpsAvg : 0)
                + " FPS, crash=" + (crashRecent ? "yes" : "no");
        return hint.toString();
    }
}