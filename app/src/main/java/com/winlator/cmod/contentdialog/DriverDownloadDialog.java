package com.winlator.cmod.contentdialog;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.winlator.cmod.R;
import com.winlator.cmod.contents.AdrenotoolsManager;
import com.winlator.cmod.contents.Downloader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class DriverDownloadDialog {
    private final Context context;
    private final AdrenotoolsManager adrenotoolsManager;
    private AlertDialog dialog;
    private RecyclerView recyclerView;
    private Runnable onDismissCallback;
    private final String repoUrl;
    private String targetAutoVersion;
    private String targetGpuGen;
    private String installedDriverName;
    private android.app.ProgressDialog progressDialog;

    public DriverDownloadDialog(Context context, String repoUrl) {
        this.context = context;
        this.adrenotoolsManager = new AdrenotoolsManager(context);
        this.repoUrl = repoUrl;
    }

    public void setOnDismissCallback(Runnable callback) {
        this.onDismissCallback = callback;
    }

    public String getInstalledDriverName() {
        return installedDriverName;
    }

    public void autoInstall(String targetVersion, String gpuGeneration) {
        this.targetAutoVersion = targetVersion;
        this.targetGpuGen = gpuGeneration == null ? "" : gpuGeneration;
    }

    public void show() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Available Drivers"); // English

        recyclerView = new RecyclerView(context);
        recyclerView.setBackgroundColor(Color.BLACK);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));

        builder.setView(recyclerView);
        builder.setNegativeButton("Back", null); // English

        dialog = builder.create();
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));

        if (targetAutoVersion != null && !targetAutoVersion.isEmpty()) {
            autoDownloadAndInstall(targetAutoVersion);
        } else {
            fetchDrivers();
        }
    }

    private void fetchDrivers() {
        Executors.newSingleThreadExecutor().execute(() -> {
            String jsonStr = Downloader.downloadString(repoUrl);
            
            if (jsonStr == null) {
                runOnUi(() -> Toast.makeText(context, "Connection failed!", Toast.LENGTH_SHORT).show());
                return;
            }

            List<ReleaseItem> releases = new ArrayList<>();
            try {
                JSONArray array = new JSONArray(jsonStr);
                
                for (int i = 0; i < array.length(); i++) {
                    JSONObject releaseObj = array.getJSONObject(i);
                    
                    
                    String rawName = releaseObj.optString("name", releaseObj.optString("tag_name", "Unknown Driver"));
                    String cleanName = cleanDriverName(rawName);
                    
                    String description = releaseObj.optString("body", "");
                    
                    
                    List<DriverAsset> assets = new ArrayList<>();
                    if (releaseObj.has("assets")) {
                        JSONArray assetsArr = releaseObj.getJSONArray("assets");
                        for (int j = 0; j < assetsArr.length(); j++) {
                            JSONObject asset = assetsArr.getJSONObject(j);
                            String url = asset.getString("browser_download_url");
                            String filename = asset.optString("name", "driver.zip");
                            
                            if (url.endsWith(".zip") || url.endsWith(".tzst")) {
                                assets.add(new DriverAsset(filename, url));
                            }
                        }
                    } else if (releaseObj.has("url")) {
                        
                        assets.add(new DriverAsset(cleanName + ".zip", releaseObj.getString("url")));
                    }

                    if (!assets.isEmpty()) {
                        releases.add(new ReleaseItem(cleanName, description, assets));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            runOnUi(() -> setupAdapter(releases));
        });
    }

    private void autoDownloadAndInstall(String targetVersion) {
        showProgress("Downloading driver\n" + targetVersion + "..."); // English
        Executors.newSingleThreadExecutor().execute(() -> {
            String jsonStr = Downloader.downloadString(repoUrl);
            if (jsonStr == null) {
                runOnUi(() -> {
                    hideProgress();
                    dialog.dismiss();
                    Toast.makeText(context, "Connection failed!", Toast.LENGTH_LONG).show();
                });
                return;
            }

            ReleaseItem best = null;
            try {
                JSONArray array = new JSONArray(jsonStr);
                String ver = targetVersion.replace("turnip", "").trim();
                String verLower = ver.toLowerCase();

                for (int i = 0; i < array.length(); i++) {
                    ReleaseItem item = parseRelease(array.getJSONObject(i));
                    if (item == null) continue;

                    String name = item.name.toLowerCase();
                    String ref = (item.name + " " + item.description).toLowerCase();

                    boolean isGen8 = ref.contains("gen8") || ref.contains("a8xx");
                    boolean wantGen8 = targetGpuGen.contains("a8xx");

                    if (wantGen8 && !isGen8) continue;
                    if (!wantGen8 && isGen8 && verLower.contains("gen8")) continue;

                    if (name.contains(verLower) || ref.contains(verLower)) {
                        if (best == null || rank(item.name) > rank(best.name)) {
                            best = item;
                        }
                    }
                }

                if (best == null) {
                    for (int i = 0; i < array.length(); i++) {
                        ReleaseItem item = parseRelease(array.getJSONObject(i));
                        if (item == null) continue;
                        String ref = (item.name + " " + item.description).toLowerCase();
                        boolean isGen8 = ref.contains("gen8") || ref.contains("a8xx");
                        boolean wantGen8 = targetGpuGen.contains("a8xx");
                        if (wantGen8 && !isGen8) continue;
                        if (!wantGen8 && isGen8) continue;
                        if (item.name.toLowerCase().contains("turn") && !item.name.toLowerCase().contains("oneui")) {
                            if (best == null || rank(item.name) > rank(best.name)) best = item;
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            ReleaseItem selected = best;
            if (selected == null) {
                runOnUi(() -> {
                    hideProgress();
                    dialog.dismiss();
                    Toast.makeText(context, "No matching driver found in repo.", Toast.LENGTH_LONG).show();
                });
                return;
            }

            DriverAsset asset = pickAsset(selected);
            if (asset == null) {
                runOnUi(() -> {
                    hideProgress();
                    dialog.dismiss();
                    Toast.makeText(context, "No downloadable asset for " + selected.name, Toast.LENGTH_LONG).show();
                });
                return;
            }

            showProgress("Downloading " + asset.name + "..."); // English

            boolean ok = Downloader.downloadFile(asset.url, new File(context.getCacheDir(), "driver_temp.zip"));
            if (!ok) {
                runOnUi(() -> {
                    hideProgress();
                    dialog.dismiss();
                    Toast.makeText(context, "Download failed!", Toast.LENGTH_LONG).show();
                });
                return;
            }

            runOnUi(() -> {
                hideProgress();
                File f = new File(context.getCacheDir(), "driver_temp.zip");
                String installed = adrenotoolsManager.installDriver(Uri.fromFile(f));
                f.delete();
                installedDriverName = installed;
                if (!installed.isEmpty()) {
                    Toast.makeText(context, "Installed: " + installed, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(context, "Installation failed! Invalid ZIP.", Toast.LENGTH_LONG).show();
                }
                dialog.dismiss();
                if (onDismissCallback != null) onDismissCallback.run();
            });
        });
    }

    private ReleaseItem parseRelease(JSONObject releaseObj) {
        try {
            String rawName = releaseObj.optString("name", releaseObj.optString("tag_name", "Unknown Driver"));
            String cleanName = cleanDriverName(rawName);
            String description = releaseObj.optString("body", "");

            List<DriverAsset> assets = new ArrayList<>();
            if (releaseObj.has("assets")) {
                JSONArray assetsArr = releaseObj.getJSONArray("assets");
                for (int j = 0; j < assetsArr.length(); j++) {
                    JSONObject asset = assetsArr.getJSONObject(j);
                    String url = asset.getString("browser_download_url");
                    String filename = asset.optString("name", "driver.zip");
                    if (url.endsWith(".zip") || url.endsWith(".tzst")) {
                        assets.add(new DriverAsset(filename, url));
                    }
                }
            }
            if (assets.isEmpty()) return null;
            return new ReleaseItem(cleanName, description, assets);
        } catch (Exception e) {
            return null;
        }
    }

    private DriverAsset pickAsset(ReleaseItem item) {
        DriverAsset plain = null;
        for (DriverAsset a : item.assets) {
            String lower = a.name.toLowerCase();
            if (lower.contains("oneui") || lower.contains("one_ui")) continue;
            if (!lower.contains("sync")) {
                return a;
            }
            if (plain == null) plain = a;
        }
        return plain;
    }

    private int rank(String name) {
        int score = 0;
        String lower = name.toLowerCase();
        if (lower.contains("turnip")) score += 10;
        if (lower.contains("mesa")) score += 5;
        if (lower.contains("gen8")) score += 3;
        if (lower.matches(".*\\d+.*")) score += 1;
        return score;
    }

    private void showProgress(String message) {
        runOnUi(() -> {
            if (progressDialog == null) {
                progressDialog = new android.app.ProgressDialog(context);
                progressDialog.setCancelable(false);
            }
            progressDialog.setMessage(message);
            if (!progressDialog.isShowing()) progressDialog.show();
        });
    }

    private void hideProgress() {
        runOnUi(() -> {
            if (progressDialog != null && progressDialog.isShowing()) progressDialog.dismiss();
        });
    }

    
    private String cleanDriverName(String raw) {
    
        String clean = raw.replace("Mesa Turnip driver ", "")
                          .replace("Mesa Turnip ", "")
                          .replace("Qualcomm Driver ", "");
        return clean.trim();
    }

    private void onDownloadClick(ReleaseItem item) {
        if (item.assets.isEmpty()) return;

    
        if (item.assets.size() == 1) {
            startDownload(item.assets.get(0));
        } else {
            
            String[] assetNames = new String[item.assets.size()];
            for (int i = 0; i < item.assets.size(); i++) {
                assetNames[i] = item.assets.get(i).name;
            }

            new AlertDialog.Builder(context)
                .setTitle("Select Variant")
                .setItems(assetNames, (dialogInterface, which) -> {
                    startDownload(item.assets.get(which));
                })
                .show();
        }
    }

    private void startDownload(DriverAsset asset) {
        Toast.makeText(context, "Downloading " + asset.name + "...", Toast.LENGTH_SHORT).show();
        
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                File tmpFile = new File(context.getCacheDir(), "driver_temp.zip");
                if (tmpFile.exists()) tmpFile.delete();

                boolean success = Downloader.downloadFile(asset.url, tmpFile);

                if (success) {
                    Uri fileUri = Uri.fromFile(tmpFile);
                    runOnUi(() -> {
                        String installedName = adrenotoolsManager.installDriver(fileUri);
                        if (!installedName.isEmpty()) {
                            Toast.makeText(context, "Installed: " + installedName, Toast.LENGTH_SHORT).show();
                            if (onDismissCallback != null) onDismissCallback.run();
                        } else {
                            Toast.makeText(context, "Installation failed! Invalid ZIP.", Toast.LENGTH_LONG).show();
                        }
                        tmpFile.delete();
                    });
                } else {
                    runOnUi(() -> Toast.makeText(context, "Download failed!", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void setupAdapter(List<ReleaseItem> releases) {
        if (releases.isEmpty()) {
            Toast.makeText(context, "No drivers found.", Toast.LENGTH_LONG).show();
            return;
        }
        recyclerView.setAdapter(new DriverAdapter(releases));
    }

    
    private static class ReleaseItem {
        String name, description;
        List<DriverAsset> assets;
        ReleaseItem(String n, String d, List<DriverAsset> a) { name = n; description = d; assets = a; }
    }
    
    private static class DriverAsset {
        String name, url;
        DriverAsset(String n, String u) { name = n; url = u; }
    }

    
    private class DriverAdapter extends RecyclerView.Adapter<DriverAdapter.ViewHolder> {
        private final List<ReleaseItem> list;
        public DriverAdapter(List<ReleaseItem> list) { this.list = list; }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.adrenotools_list_item, parent, false); 
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            ReleaseItem item = list.get(position);
            
            
            holder.title.setText(item.name);
            holder.title.setTextColor(Color.WHITE);
            holder.subtitle.setTextColor(Color.parseColor("#AAAAAA"));
            
            
            if (item.assets.size() > 1) {
                holder.subtitle.setText(item.assets.size() + " variants available (Click to choose)");
            } else {
                
                String shortDesc = item.description.replace("\n", " ").trim();
                if (shortDesc.length() > 50) shortDesc = shortDesc.substring(0, 50) + "...";
                if (shortDesc.isEmpty()) shortDesc = "No description";
                holder.subtitle.setText(shortDesc);
            }

            holder.actionButton.setImageResource(android.R.drawable.stat_sys_download);
            holder.actionButton.setOnClickListener(v -> onDownloadClick(item));
        }

        @Override
        public int getItemCount() { return list.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView title, subtitle;
            ImageButton actionButton;
            ViewHolder(View v) {
                super(v);
                title = v.findViewById(R.id.TVName);
                subtitle = v.findViewById(R.id.TVVersion);
                actionButton = v.findViewById(R.id.BTMenu);
            }
        }
    }

    private void runOnUi(Runnable action) {
        if (context instanceof Activity) ((Activity) context).runOnUiThread(action);
    }
}
