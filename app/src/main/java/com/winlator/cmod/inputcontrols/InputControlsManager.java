package com.winlator.cmod.inputcontrols;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.util.JsonReader;

import androidx.preference.PreferenceManager;

import com.winlator.cmod.SettingsFragment;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.FileUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;

public class InputControlsManager {
    private final Context context;
    private ArrayList<ControlsProfile> profiles;
    private int maxProfileId;
    private boolean profilesLoaded = false;

    public InputControlsManager(Context context) {
        this.context = context;
    }

    public static File getProfilesDir(Context context) {
        File profilesDir = new File(context.getFilesDir(), "profiles");
        if (!profilesDir.isDirectory()) profilesDir.mkdir();
        return profilesDir;
    }

    public ArrayList<ControlsProfile> getProfiles() {
        return getProfiles(false);
    }

    public ArrayList<ControlsProfile> getProfiles(boolean ignoreTemplates) {
        if (!profilesLoaded) loadProfiles(ignoreTemplates);
        return profiles;
    }

    private void copyAssetProfilesIfNeeded() {
        File profilesDir = InputControlsManager.getProfilesDir(context);
        if (FileUtils.isEmpty(profilesDir)) {
            FileUtils.copy(context, "inputcontrols/profiles", profilesDir);
            return;
        }

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        int newVersion = AppUtils.getVersionCode(context);
        int oldVersion = preferences.getInt("inputcontrols_app_version", 0);
        if (oldVersion == newVersion) return;
        preferences.edit().putInt("inputcontrols_app_version", newVersion).apply();

        File[] files = profilesDir.listFiles();
        if (files == null) return;

        try {
            AssetManager assetManager = context.getAssets();
            String[] assetFiles = assetManager.list("inputcontrols/profiles");
            for (String assetFile : assetFiles) {
                String assetPath = "inputcontrols/profiles/"+assetFile;
                ControlsProfile originProfile = loadProfile(context, assetManager.open(assetPath));

                File targetFile = null;
                for (File file : files) {
                    ControlsProfile targetProfile = loadProfile(context, file);
                    if (originProfile.id == targetProfile.id && originProfile.getName().equals(targetProfile.getName())) {
                        targetFile = file;
                        break;
                    }
                }

                if (targetFile != null) {
                    FileUtils.copy(context, assetPath, targetFile);
                }
            }
        }
        catch (IOException e) {}
    }

    public void loadProfiles(boolean ignoreTemplates) {
        File profilesDir = InputControlsManager.getProfilesDir(context);
        copyAssetProfilesIfNeeded();

        ArrayList<ControlsProfile> profiles = new ArrayList<>();
        File[] files = profilesDir.listFiles();
        if (files != null) {
            for (File file : files) {
                ControlsProfile profile = loadProfile(context, file);
                if (!(ignoreTemplates && profile.isTemplate())) profiles.add(profile);
                maxProfileId = Math.max(maxProfileId, profile.id);
            }
        }

        Collections.sort(profiles);
        this.profiles = profiles;
        profilesLoaded = true;
    }

    public ControlsProfile createProfile(String name) {
        ControlsProfile profile = new ControlsProfile(context, ++maxProfileId);
        profile.setName(name);
        profile.save();
        profiles.add(profile);
        return profile;
    }

    public ControlsProfile duplicateProfile(ControlsProfile source) {
        String newName;
        for (int i = 1;;i++) {
            newName = source.getName() + " ("+i+")";
            boolean found = false;
            for (ControlsProfile profile : profiles) {
                if (profile.getName().equals(newName)) {
                    found = true;
                    break;
                }
            }
            if (!found) break;
        }

        int newId = ++maxProfileId;
        File newFile = ControlsProfile.getProfileFile(context, newId);

        try {
            JSONObject data = new JSONObject(FileUtils.readString(ControlsProfile.getProfileFile(context, source.id)));
            data.put("id", newId);
            data.put("name", newName);
            if (data.has("template")) data.remove("template");
            FileUtils.writeString(newFile, data.toString());
        }
        catch (JSONException e) {}

        ControlsProfile profile = loadProfile(context, newFile);
        profiles.add(profile);
        return profile;
    }

    public void removeProfile(ControlsProfile profile) {
        File file = ControlsProfile.getProfileFile(context, profile.id);
        if (file.isFile() && file.delete()) profiles.remove(profile);
    }

    public ControlsProfile importProfile(JSONObject data) {
        try {
            if (!data.has("id") || !data.has("name")) return null;
            int newId = ++maxProfileId;
            File newFile = ControlsProfile.getProfileFile(context, newId);
            data.put("id", newId);
            FileUtils.writeString(newFile, data.toString());
            ControlsProfile newProfile = loadProfile(context, newFile);

            int foundIndex = -1;
            for (int i = 0; i < profiles.size(); i++) {
                ControlsProfile profile = profiles.get(i);
                if (profile.getName().equals(newProfile.getName())) {
                    foundIndex = i;
                    break;
                }
            }

            if (foundIndex != -1) {
                profiles.set(foundIndex, newProfile);
            }
            else profiles.add(newProfile);
            return newProfile;
        }
        catch (JSONException e) {
            return null;
        }
    }

    public File exportProfile(ControlsProfile profile) {
        File destination;
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        String winlatorPath = sp.getString("winlator_path_uri", null);
        if (winlatorPath != null) {
            Uri winlatorUri = Uri.parse(winlatorPath);
            destination = new File(FileUtils.getFilePathFromUri(context, winlatorUri), "profiles/" + profile.getName() + ".icp");
        }
        else {
            destination = new File(SettingsFragment.DEFAULT_WINLATOR_PATH, "profiles/" + profile.getName() + ".icp");
        }
        FileUtils.copy(ControlsProfile.getProfileFile(context, profile.id), destination);
        MediaScannerConnection.scanFile(context, new String[]{destination.getAbsolutePath()}, null, null);
        return destination.isFile() ? destination : null;
    }

    public static ControlsProfile loadProfile(Context context, File file) {
        try {
            return loadProfile(context, new FileInputStream(file));
        }
        catch (FileNotFoundException e) {
            return null;
        }
    }

    public static ControlsProfile loadProfile(Context context, InputStream inStream) {
        try (JsonReader reader = new JsonReader(new InputStreamReader(inStream, StandardCharsets.UTF_8))) {
            int profileId = 0;
            String profileName = null;
            float cursorSpeed = Float.NaN;
            int fieldsRead = 0;

            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();

                if (name.equals("id")) {
                    profileId = reader.nextInt();
                    fieldsRead++;
                }
                else if (name.equals("name")) {
                    profileName = reader.nextString();
                    fieldsRead++;
                }
                else if (name.equals("cursorSpeed")) {
                    cursorSpeed = (float) reader.nextDouble();
                    fieldsRead++;
                }
                else {
                    if (fieldsRead == 3) break;
                    reader.skipValue();
                }
            }

            ControlsProfile profile = new ControlsProfile(context, profileId);
            profile.setName(profileName);
            profile.setCursorSpeed(cursorSpeed);
            return profile;
        }
        catch (IOException e) {
            return null;
        }
    }

    public ControlsProfile getProfile(int id) {
        for (ControlsProfile profile : getProfiles()) if (profile.id == id) return profile;
        return null;
    }

    /**
     * Get profile by ID without loading all profiles
     * @param id the profile ID to search for
     * @return the found profile or null if not found
     */
    public ControlsProfile getProfileById(int id) {
        // First check in already loaded profiles
        if (profilesLoaded) {
            for (ControlsProfile profile : profiles) {
                if (profile.id == id) {
                    return profile;
                }
            }
        }
        
        // If not found, try to load directly from file
        File profileFile = ControlsProfile.getProfileFile(context, id);
        if (profileFile.exists()) {
            return loadProfile(context, profileFile);
        }
        
        return null;
    }

    /**
     * Check if a profile with the given ID exists
     * @param id the profile ID to check
     * @return true if the profile exists
     */
    public boolean profileExists(int id) {
        if (id == 0) return false;
        
        if (profilesLoaded) {
            for (ControlsProfile profile : profiles) {
                if (profile.id == id) {
                    return true;
                }
            }
        }
        
        // Check if file exists
        File profileFile = ControlsProfile.getProfileFile(context, id);
        return profileFile.exists();
    }

    /**
     * Get all available profile names for UI display
     * @param excludeId profile ID to exclude from the list (usually current profile)
     * @return array of profile names
     */
    public String[] getProfileNames(int excludeId) {
        ArrayList<ControlsProfile> allProfiles = getProfiles(true); // ignoreTemplates = true
        ArrayList<String> names = new ArrayList<>();
        
        for (ControlsProfile profile : allProfiles) {
            if (profile.id != excludeId) {
                names.add(profile.getName());
            }
        }
        
        return names.toArray(new String[0]);
    }

    /**
     * Get all available profile IDs for UI display
     * @param excludeId profile ID to exclude from the list (usually current profile)
     * @return array of profile IDs
     */
    public int[] getProfileIds(int excludeId) {
        ArrayList<ControlsProfile> allProfiles = getProfiles(true); // ignoreTemplates = true
        ArrayList<Integer> ids = new ArrayList<>();
        
        for (ControlsProfile profile : allProfiles) {
            if (profile.id != excludeId) {
                ids.add(profile.id);
            }
        }
        
        // Convert to int array
        int[] result = new int[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            result[i] = ids.get(i);
        }
        return result;
    }

    /**
     * Get profile name by ID
     * @param id the profile ID
     * @return profile name or "Unknown Profile" if not found
     */
    public String getProfileNameById(int id) {
        if (id == 0) return "None";
        
        ControlsProfile profile = getProfileById(id);
        return profile != null ? profile.getName() : "Unknown Profile";
    }

    /**
     * Reload profiles from disk
     */
    public void reloadProfiles() {
        profilesLoaded = false;
        loadProfiles(false);
    }

    /**
     * Get all profiles that contain Vertical Scroll Bar elements
     * @return list of profiles with scroll bars
     */
    public ArrayList<ControlsProfile> getProfilesWithScrollBars() {
        ArrayList<ControlsProfile> profilesWithScrollBars = new ArrayList<>();
        for (ControlsProfile profile : getProfiles()) {
            if (profile.hasVerticalScrollBarElements()) {
                profilesWithScrollBars.add(profile);
            }
        }
        return profilesWithScrollBars;
    }

    /**
     * Get all profiles that contain profile switching elements
     * @return list of profiles with profile switching
     */
    public ArrayList<ControlsProfile> getProfilesWithProfileSwitching() {
        ArrayList<ControlsProfile> profilesWithSwitching = new ArrayList<>();
        for (ControlsProfile profile : getProfiles()) {
            if (profile.hasProfileSwitchingElements()) {
                profilesWithSwitching.add(profile);
            }
        }
        return profilesWithSwitching;
    }

    /**
     * Get all profiles that contain multi-binding elements
     * @return list of profiles with multi-binding
     */
    public ArrayList<ControlsProfile> getProfilesWithMultiBinding() {
        ArrayList<ControlsProfile> profilesWithMultiBinding = new ArrayList<>();
        for (ControlsProfile profile : getProfiles()) {
            if (profile.hasMultiBindingElements()) {
                profilesWithMultiBinding.add(profile);
            }
        }
        return profilesWithMultiBinding;
    }

    /**
     * Create a default profile with common elements including a Vertical Scroll Bar
     * @param profileName the name for the new profile
     * @return the created profile
     */
    public ControlsProfile createDefaultProfileWithScrollBar(String profileName) {
        ControlsProfile profile = createProfile(profileName);
        
        // This would typically be called from the UI to add default elements
        // The actual element creation would happen in the editor activity
        
        return profile;
    }

    /**
     * Check if any profile contains elements of the specified type
     * @param elementType the type of element to check for
     * @return true if at least one profile contains the element type
     */
    public boolean hasProfilesWithElementType(ControlElement.Type elementType) {
        for (ControlsProfile profile : getProfiles()) {
            if (!profile.isElementsLoaded()) {
                // For performance, we might want to load elements only when needed
                continue;
            }
            
            for (ControlElement element : profile.getElements()) {
                if (element.getType() == elementType) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Get statistics about element types across all profiles
     * @return map with element type counts
     */
    public java.util.Map<ControlElement.Type, Integer> getElementTypeStatistics() {
        java.util.Map<ControlElement.Type, Integer> stats = new java.util.HashMap<>();
        
        // Initialize all known types
        for (ControlElement.Type type : ControlElement.Type.values()) {
            stats.put(type, 0);
        }
        
        for (ControlsProfile profile : getProfiles()) {
            if (!profile.isElementsLoaded()) continue;
            
            for (ControlElement element : profile.getElements()) {
                ControlElement.Type type = element.getType();
                stats.put(type, stats.get(type) + 1);
            }
        }
        
        return stats;
    }
}