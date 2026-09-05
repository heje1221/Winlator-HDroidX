package com.winlator.cmod.contents;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class Downloader {

    private static final String USER_AGENT = "Winlator-HDroidX/7.1.4x";
    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 30000;
    private static final int MAX_REDIRECTS = 5;

    public static boolean downloadFile(String address, File file) {
        try {
            InputStream input = openStream(address);
            if (input == null) return false;

            OutputStream output = new FileOutputStream(file.getAbsolutePath());
            byte[] data = new byte[4096];

            int count;
            while ((count = input.read(data)) != -1) {
                output.write(data, 0, count);
            }

            output.flush();
            output.close();
            input.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static String downloadString(String address) {
        try {
            InputStream input = openStream(address);
            if (input == null) return null;

            BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static InputStream openStream(String address) throws Exception {
        String current = address;
        for (int i = 0; i < MAX_REDIRECTS; i++) {
            URL url = new URL(current);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "application/vnd.github+json, application/json, */*");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setInstanceFollowRedirects(false);

            int code = connection.getResponseCode();
            if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP
                    || code == HttpURLConnection.HTTP_SEE_OTHER) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null) return null;
                current = location.startsWith("http") ? location : url.getProtocol() + "://" + url.getHost() + location;
                continue;
            }
            if (code != HttpURLConnection.HTTP_OK) {
                connection.disconnect();
                return null;
            }
            return connection.getInputStream();
        }
        return null;
    }
}