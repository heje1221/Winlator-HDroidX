package com.winlator.cmod.ai;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.contentdialog.DriverDownloadDialog;
import com.winlator.cmod.contentdialog.RepositoryManagerDialog;
import com.winlator.cmod.core.AppUtils;

public class AIConfigDialog extends ContentDialog {

    private final Context context;
    private final Shortcut shortcut;
    private final AIConfigEngine.DeviceSpec spec;
    private final AIConfigEngine.Game detectedGame;
    private final AIProfile aiProfile;
    private AIConfigEngine.Recommendation lastRec;
    private Spinner gameSpinner;
    private TextView promptText;

    public AIConfigDialog(Context context, Shortcut shortcut) {
        super(context, R.layout.ai_config_dialog);
        this.context = context;
        this.shortcut = shortcut;
        this.spec = AIConfigEngine.detect(context);
        this.detectedGame = AIConfigEngine.detectGame(shortcut.name, shortcut.path);
        this.aiProfile = AIProfile.forShortcut(context, shortcut, shortcut.container);

        setTitle(R.string.ai_config);

        TextView deviceInfo = findViewById(R.id.TVAIDeviceInfo);
        String detectLine = "Detected: " + spec.socName + " | " + spec.gpuFamily + " | "
                + spec.ramMb + " MB RAM | " + spec.cores + " cores";
        if (detectedGame != null) {
            detectLine += "\nGame found: " + detectedGame.name + " (" + shortcut.name + " -> "
                    + AIConfigEngine.exeNameOf(shortcut.path) + ")";
        } else {
            detectLine += "\nNo known game matched - defaulted to Custom / General. Pick below.";
        }
        deviceInfo.setText(detectLine);

        gameSpinner = findViewById(R.id.SPAIGame);
        AIConfigEngine.Game[] games = AIConfigEngine.GAMES;
        String[] names = new String[games.length];
        for (int i = 0; i < games.length; i++) names[i] = games[i].name;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        gameSpinner.setAdapter(adapter);

        if (detectedGame != null) {
            for (int i = 0; i < games.length; i++) {
                if (games[i].name.equals(detectedGame.name)) {
                    gameSpinner.setSelection(i);
                    break;
                }
            }
        } else {
            for (int i = 0; i < games.length; i++) {
                if (games[i].name.equals("Custom / General")) {
                    gameSpinner.setSelection(i);
                    break;
                }
            }
        }

        promptText = findViewById(R.id.TVAIPrompt);

        gameSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                updateSummary(position);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        setOnConfirmCallback(this::applyConfig);
        updateSummary(gameSpinner.getSelectedItemPosition());
    }

    private void updateSummary(int position) {
        AIConfigEngine.Game game = AIConfigEngine.GAMES[position];
        lastRec = AIConfigEngine.recommend(context, spec, game);
        AIConfigEngine.tuneFromProfile(lastRec, aiProfile);

        TextView summaryText = findViewById(R.id.TVAIRecSummary);
        summaryText.setText(lastRec.summary);

        if (!lastRec.graphicsDriverVersion.isEmpty() && !lastRec.graphicsDriverInstalled) {
            promptText.setText("Click APPLY to auto-download + install the required driver, then launch.");
            promptText.setVisibility(android.view.View.VISIBLE);
        } else {
            promptText.setVisibility(android.view.View.GONE);
        }
    }

    private void applyConfig() {
        if (lastRec == null) return;

        boolean driverNeeded = !lastRec.graphicsDriverVersion.isEmpty();
        boolean driverReady = driverNeeded && lastRec.graphicsDriverInstalled;

        if (driverNeeded && !driverReady) {
            dismiss();
            DriverDownloadDialog driverDialog = new DriverDownloadDialog(context,
                    RepositoryManagerDialog.getStevenMxzRepo().apiUrl);
            driverDialog.autoInstall(lastRec.graphicsDriverVersion, lastRec.gpuGeneration);
            driverDialog.setOnDismissCallback(() -> {
                String installed = driverDialog.getInstalledDriverName();
                if (installed != null && !installed.isEmpty()) {
                    lastRec.graphicsDriverVersion = installed;
                    lastRec.graphicsDriverInstalled = true;
                    applyNow();
                    AppUtils.showToast(getContext(),
                            "Driver installed: " + installed + ". AI Config applied!");
                } else {
                    AppUtils.showToast(getContext(),
                            "Driver install failed. Check connection or pick manually.");
                }
            });
            driverDialog.show();
            return;
        }

        applyNow();
    }

    private void applyNow() {
        if (lastRec == null) return;

        // Per-shortcut overrides (Extra Data in the .desktop file)
        shortcut.putExtra("screenSize", lastRec.resolution);
        shortcut.putExtra("box64Preset", lastRec.box64Preset);
        shortcut.putExtra("cpuList", lastRec.cpuList);
        if (lastRec.dxWrapper != null && !lastRec.dxWrapper.isEmpty()) {
            shortcut.putExtra("dxwrapper", lastRec.dxWrapper);
        }
        if (lastRec.dxWrapperConfig != null && !lastRec.dxWrapperConfig.isEmpty()) {
            shortcut.putExtra("dxwrapperConfig", lastRec.dxWrapperConfig);
        }
        if (!lastRec.graphicsDriverVersion.isEmpty()) {
            shortcut.putExtra("graphicsDriverConfig", setDriverVersion(
                    shortcut.getExtra("graphicsDriverConfig", shortcut.container.getGraphicsDriverConfig()),
                    lastRec.graphicsDriverVersion));
        }
        shortcut.saveData();

        AppUtils.showToast(getContext(),
                "AI Config applied: " + lastRec.resolution + " | " + lastRec.cpuList + " | " + lastRec.box64Name);
    }

    private String setDriverVersion(String config, String version) {
        if (config == null || config.isEmpty()) return "version=" + version;
        String[] parts = config.split(";");
        boolean replaced = false;
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append(";");
            if (part.startsWith("version=")) {
                sb.append("version=").append(version);
                replaced = true;
            } else {
                sb.append(part);
            }
        }
        if (!replaced) sb.append(";version=").append(version);
        return sb.toString();
    }
}