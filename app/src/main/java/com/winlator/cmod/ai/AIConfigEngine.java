package com.winlator.cmod.ai;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;

import com.winlator.cmod.contents.AdrenotoolsManager;
import com.winlator.cmod.contents.ContentProfile;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.core.DefaultVersion;
import com.winlator.cmod.core.GPUInformation;

import java.util.List;
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
            String lower = exeName.toLowerCase(Locale.ROOT).replace(" ", "");
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
        public String dxvkVersion;
        public String vkd3dVersion;
        public String box64Preset;
        public String box64Name;
        public String cpuList;
        public boolean showFPS;
        public String notes;
        public String summary;
        public boolean graphicsDriverInstalled;
        public boolean dxWrapperReady = true;
        public String gpuGeneration = "a7xx";
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

    public static Game detectGame(String shortcutName, String exePath) {
        StringBuilder sb = new StringBuilder();
        if (shortcutName != null) sb.append(shortcutName);
        if (exePath != null && !exePath.isEmpty()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(exePath);
        }
        if (sb.length() == 0) return null;
        for (Game game : GAMES) {
            if (game.matchesExe(sb.toString())) return game;
        }
        return null;
    }

    public static Game detectGame(String exeName) {
        return detectGame(null, exeName);
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

    public static String gpuGeneration(String gpuFamily, String socModel) {
        if (!"adreno".equals(gpuFamily)) return "";
        String m = socModel == null ? "" : socModel.toLowerCase();
        if (m.startsWith("sm8") || m.startsWith("a8")) return "a8xx";
        if (m.startsWith("sm7") || m.startsWith("a7")) return "a7xx";
        if (m.startsWith("sm6") || m.startsWith("a6")) return "a6xx";
        return "a7xx";
    }

    public static String resolveInstalledDriver(Context context, String gpuFamily, String gpuGeneration) {
        if (!"adreno".equals(gpuFamily)) return "";
        List<String> installed = null;
        try {
            installed = new AdrenotoolsManager(context).enumarateInstalledDrivers();
        } catch (Throwable t) {
            t.printStackTrace();
        }
        if (installed != null) {
            String best = "";
            for (String id : installed) {
                String lower = id.toLowerCase();
                if (!(lower.contains("turn") || lower.contains("mesa") || lower.contains("freedreno"))) continue;
                if ("a8xx".equals(gpuGeneration) && !lower.contains("8")) continue;
                if (best.isEmpty() || compareVersions(id, best) > 0) best = id;
            }
            if (!best.isEmpty()) return best;
            if (!installed.isEmpty()) return installed.get(0);
        }
        try {
            if (GPUInformation.isDriverSupported(DefaultVersion.WRAPPER_ADRENO, context))
                return DefaultVersion.WRAPPER_ADRENO;
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return "";
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

    private static String defaultDXVKVersion(Game game, DeviceSpec spec) {
        String baseVersion = game.dxvkVersion != null ? game.dxvkVersion : "2.3.1";
        boolean lowEnd = "mid".equals(spec.socTier) || "low".equals(spec.socTier);
        if (lowEnd && baseVersion.startsWith("2.") && !"heavy".equals(game.tier)) {
            return "1.10.3";
        }
        return baseVersion;
    }

    private static String defaultVKD3DVersion(Game game, DeviceSpec spec) {
        String baseVersion = game.vkd3dVersion != null ? game.vkd3dVersion : "None";
        if ("None".equals(baseVersion)) return "None";
        boolean lowEnd = "mid".equals(spec.socTier) || "low".equals(spec.socTier);
        if (lowEnd && baseVersion.startsWith("2.14")) return "2.8";
        return baseVersion;
    }

    public static String pickInstalledVersion(Context context, ContentProfile.ContentType type) {
        try {
            ContentsManager cm = new ContentsManager(context);
            cm.syncContents();
            List<ContentProfile> list = cm.getProfiles(type);
            ContentProfile best = null;
            if (list != null) {
                for (ContentProfile p : list) {
                    if (best == null || entryRank(p) > entryRank(best)) best = p;
                }
            }
            if (best != null) return best.verName + "-" + best.verCode;
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return null;
    }

    private static int entryRank(ContentProfile p) {
        String n = p.verName == null ? "" : p.verName.toLowerCase(Locale.ROOT);
        int score = Math.max(0, p.verCode) * 2;
        if (n.contains("arm64ec")) score += 30;
        if (n.contains("gplasync") || n.contains("async")) score += 10;
        return score;
    }

    private static String buildDXWrapperConfig(String dxvkVersion, String vkd3dVersion) {
        return "version=" + dxvkVersion
                + ",framerate=0,async=1,asyncCache=1"
                + ",vkd3dVersion=" + vkd3dVersion
                + ",vkd3dLevel=12_1"
                + ",ddrawrapper=None,csmt=3"
                + ",gpuName=NVIDIA GeForce GTX 480,videoMemorySize=2048"
                + ",strict_shader_math=1,OffscreenRenderingMode=fbo,renderer=gl";
    }

    public static Recommendation recommend(DeviceSpec spec, Game game) {
        return buildRecommendation(spec, game,
                defaultDXVKVersion(game, spec), defaultVKD3DVersion(game, spec));
    }

    public static Recommendation recommend(Context context, DeviceSpec spec, Game game) {
        String dxvkVersion = pickInstalledVersion(context, ContentProfile.ContentType.CONTENT_TYPE_DXVK);
        if (dxvkVersion == null) dxvkVersion = defaultDXVKVersion(game, spec);
        String vkd3dVersion = pickInstalledVersion(context, ContentProfile.ContentType.CONTENT_TYPE_VKD3D);
        if (vkd3dVersion == null) vkd3dVersion = defaultVKD3DVersion(game, spec);

        Recommendation rec = buildRecommendation(spec, game, dxvkVersion, vkd3dVersion);
        rec.dxvkVersion = dxvkVersion;
        rec.vkd3dVersion = vkd3dVersion;

        rec.dxWrapperReady = isContentAvailable(context,
                ContentProfile.ContentType.CONTENT_TYPE_DXVK, dxvkVersion)
                && ("None".equals(vkd3dVersion)
                || isContentAvailable(context, ContentProfile.ContentType.CONTENT_TYPE_VKD3D, vkd3dVersion));
        if (!rec.dxWrapperReady) {
            rec.summary += "\nDX wrapper (DXVK/VKD3D) not installed - will auto-download the best version on Apply.";
        }

        if ("adreno".equals(spec.gpuFamily)) {
            String installed = resolveInstalledDriver(context, spec.gpuFamily, rec.gpuGeneration);
            rec.graphicsDriverVersion = ADRENO_DRIVER_VERSION;
            rec.graphicsDriverInstalled = installedDriverReady(installed, ADRENO_DRIVER_VERSION);
            rec.summary = rec.summary.replace("(NOT installed)",
                    rec.graphicsDriverInstalled ? "(installed)" : "(NOT installed - auto-download on Apply)");
        }
        return rec;
    }

    public static boolean isContentAvailable(Context context, ContentProfile.ContentType type, String version) {
        if (version == null || version.isEmpty()) return false;
        try {
            ContentsManager cm = new ContentsManager(context);
            cm.syncContents();
            List<ContentProfile> list = cm.getProfiles(type);
            if (list != null) {
                for (ContentProfile p : list) {
                    String entry = ContentsManager.getEntryName(p);
                    String suffix = entry.substring(entry.indexOf('-') + 1);
                    if (suffix.equals(version) || p.verName.equals(version)) return true;
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return isBundledWrapper(context, type, version);
    }

    public static boolean isBundledWrapper(Context context, ContentProfile.ContentType type, String version) {
        try {
            String asset = type == ContentProfile.ContentType.CONTENT_TYPE_DXVK
                    ? "dxwrapper/dxvk-" + version + ".tzst"
                    : "dxwrapper/vkd3d-" + version + ".tzst";
            context.getAssets().open(asset).close();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public interface OnContentInstalledCallback {
        void onInstalled(boolean success, String message);
    }

    public static void autoInstallContent(Context context, ContentProfile.ContentType type, String version,
                                          OnContentInstalledCallback callback) {
        new Thread(() -> {
            try {
                ContentsManager manager = new ContentsManager(context);
                manager.syncContents();

                String json = com.winlator.cmod.contents.Downloader.downloadString(ContentsManager.REMOTE_PROFILES);
                if (json == null) {
                    if (callback != null) callback.onInstalled(false, "Failed to fetch content list (network)");
                    return;
                }
                manager.setRemoteProfiles(json);

                ContentProfile target = null;
                List<ContentProfile> list = manager.getProfiles(type);
                if (list != null) {
                    for (ContentProfile p : list) {
                        if (p.remoteUrl == null) continue;
                        String entry = ContentsManager.getEntryName(p);
                        String suffix = entry.substring(entry.indexOf('-') + 1);
                        if (suffix.startsWith(version + "-") || suffix.equals(version)) {
                            if (target == null || entryRank(p) > entryRank(target)) target = p;
                        }
                    }
                }
                if (target == null) {
                    if (callback != null) callback.onInstalled(false, "No remote profile for " + type + " " + version);
                    return;
                }

                final ContentProfile profile = target;
                java.io.File out = new java.io.File(context.getCacheDir(),
                        "ai_" + type.toString() + "_" + version + ".tmp");
                if (!com.winlator.cmod.contents.Downloader.downloadFile(profile.remoteUrl, out)) {
                    if (callback != null) callback.onInstalled(false, "Download failed");
                    return;
                }

                manager.extraContentFile(android.net.Uri.fromFile(out), new ContentsManager.OnInstallFinishedCallback() {
                    @Override
                    public void onFailed(ContentsManager.InstallFailedReason reason, Exception e) {
                        if (callback != null) callback.onInstalled(false, "Extract failed: " + reason);
                    }

                    @Override
                    public void onSucceed(ContentProfile extracted) {
                        manager.finishInstallContent(extracted, new ContentsManager.OnInstallFinishedCallback() {
                            @Override
                            public void onFailed(ContentsManager.InstallFailedReason reason, Exception e) {
                                if (callback != null) callback.onInstalled(false, "Install failed: " + reason);
                            }

                            @Override
                            public void onSucceed(ContentProfile inst) {
                                manager.syncContents();
                                if (callback != null) callback.onInstalled(true, inst.verName);
                            }
                        });
                    }
                });
            } catch (Throwable t) {
                t.printStackTrace();
                if (callback != null) callback.onInstalled(false, "Error: " + t.getMessage());
            }
        }).start();
    }

    public static boolean installedDriverReady(String installedDriverName, String targetVersion) {
        if (installedDriverName == null || installedDriverName.isEmpty()) return false;
        String lower = installedDriverName.toLowerCase(Locale.ROOT);
        if (!(lower.contains("turnip") || lower.contains("mesa") || lower.contains("freedreno"))) return false;
        return compareVersions(installedDriverName, targetVersion) >= 0;
    }

    private static int compareVersions(String a, String b) {
        int[] va = parseVersion(a);
        int[] vb = parseVersion(b);
        for (int i = 0; i < 3; i++) {
            if (va[i] != vb[i]) return Integer.compare(va[i], vb[i]);
        }
        return 0;
    }

    private static int[] parseVersion(String s) {
        int[] out = new int[3];
        if (s == null) return out;
        int idx = 0;
        StringBuilder num = new StringBuilder();
        for (int i = 0; i < s.length() && idx < 3; i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) {
                num.append(c);
            } else if (num.length() > 0) {
                out[idx++] = Integer.parseInt(num.toString());
                num.setLength(0);
            }
        }
        if (num.length() > 0 && idx < 3) out[idx] = Integer.parseInt(num.toString());
        return out;
    }

    private static Recommendation buildRecommendation(DeviceSpec spec, Game game, String dxvkVersion, String vkd3dVersion) {
        Recommendation rec = new Recommendation();
        rec.notes = game.notes;
        rec.gpuGeneration = gpuGeneration(spec.gpuFamily, spec.socModel);

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
        else rec.dxWrapper = "dxvk";

        rec.dxWrapperConfig = buildDXWrapperConfig(dxvkVersion, vkd3dVersion);

        if ("adreno".equals(spec.gpuFamily)) {
            rec.graphicsDriverVersion = ADRENO_DRIVER_VERSION;
            rec.graphicsDriverInstalled = false;
        } else {
            rec.graphicsDriverVersion = "";
            rec.graphicsDriverInstalled = true;
        }

        return finishRecommendation(rec, spec, game, dxvkVersion, vkd3dVersion);
    }

    private static Recommendation finishRecommendation(Recommendation rec, DeviceSpec spec, Game game,
                                                      String dxvkVersion, String vkd3dVersion) {
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
        sb.append("GPU: ").append(spec.gpuFamily);
        if (!rec.gpuGeneration.isEmpty()) sb.append(" ").append(rec.gpuGeneration);
        sb.append(" | RAM: ").append(spec.ramMb).append(" MB | Cores: ").append(spec.cores).append("\n\n");
        sb.append("GAME: ").append(game.name).append("\n");
        sb.append("Resolution: ").append(rec.resolution).append("\n");
        sb.append("Graphics driver: ").append(rec.graphicsDriverVersion.isEmpty() ? "system/virgl" : rec.graphicsDriverVersion);
        sb.append(rec.graphicsDriverInstalled ? " (installed)" : " (NOT installed)").append("\n");
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