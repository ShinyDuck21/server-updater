package dev.shinyduck.mincecraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Modrinth {
    String baseUrl = "https://api.modrinth.com/v2/";

    public static String generateHash(String filePath, String algorithm) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        try (FileInputStream fis = new FileInputStream(filePath)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        byte[] hashBytes = digest.digest();
        // Convert to hex string
        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }

    public static void unzip(String zipFile, File output) throws IOException {
        byte[] buffer = new byte[1024];
        ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
        ZipEntry zipEntry = zis.getNextEntry();

        while (zipEntry != null) {
            File newFile = new File(output, String.valueOf(zipEntry));
            if (zipEntry.isDirectory()) {
                if (!newFile.isDirectory() && !newFile.mkdirs()) {
                    throw new IOException("Could not create directory " + newFile.getAbsolutePath());
                }
            } else {
                // Fix for windows created zip files
                File parent = newFile.getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs()) {
                    throw new IOException("Could not create directory " + parent.getAbsolutePath());
                }

                FileOutputStream fos = new FileOutputStream(newFile);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }

                fos.close();
            }

            zipEntry = zis.getNextEntry();
        }

        zis.closeEntry();
        zis.close();
    }

    /**
     * Downloads a Minecraft modpack from the Modrinth provider
     *
     * @param slug    The slug/id of the modpack found on modrinth
     * @param version The version of Minecraft
     * @param output  The output directory
     * @throws IOException              May be thrown if Files not Found or URL doesn't work
     * @throws InterruptedException     May be thrown if URL doesn't work
     * @throws NullPointerException     JSON may throw if URL doesn't work
     * @throws NoSuchAlgorithmException Should not be thrown but caught just in case
     * @throws RuntimeException         Catch all for all the exceptions
     */

    public void downloadModpack(String slug, String version, File output) {
        HttpClient httpclient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        String gameVersionEncode = URLEncoder.encode("[\"" + version + "\"]", StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "project/" + slug + "/version?game_versions=" + gameVersionEncode))
                .build();

        try {
            HttpResponse<String> res = httpclient.send(request, HttpResponse.BodyHandlers.ofString());

            JsonArray root = JsonParser.parseString(res.body()).getAsJsonArray();
            JsonObject latestProject = null;
            Instant latestDate = null;
            for (JsonElement jsonElement : root) {
                JsonObject jsonObject = (JsonObject) jsonElement;
                Instant date = Instant.parse(jsonObject.get("date_published").getAsString());

                if (latestDate == null) {
                    latestDate = date;
                    latestProject = jsonObject;
                } else if (latestDate.isAfter(date)) {
                    latestDate = date;
                    latestProject = jsonObject;
                }
            }

            JsonObject files = latestProject.getAsJsonArray("files").get(0).getAsJsonObject();
            String downloadUrl = files.get("url").getAsString();
            String filename = files.get("filename").getAsString();
            HttpRequest downloadReq = HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .build();

            HttpResponse<InputStream> downloadSend = httpclient.send(downloadReq, HttpResponse.BodyHandlers.ofInputStream());
            Path mrpackPath = Paths.get(filename);
            Files.copy(downloadSend.body(), mrpackPath);

            File mrpack = new File(mrpackPath.getFileName().toString());
            String hash = generateHash(mrpack.getAbsolutePath(), "sha512");

            if (!hash.equals(files.get("hashes").getAsJsonObject().get("sha512").getAsString())) {
                throw new IOException("Download error occured. please try again later.");
            }
            System.out.println("Downloaded: " + filename);
            filename = filename.replace(".mrpack", ".zip");
            mrpack.renameTo(new File(mrpack.getParentFile(), filename));
            System.out.println("Renamed: " + filename);

            unzip(Paths.get(filename).toAbsolutePath().toString(), output);

            File modrinthIndexJson = new File(output, "modrinth.index.json");
            StringBuilder builder = new StringBuilder();
            if (modrinthIndexJson.exists() && modrinthIndexJson.canRead()) {
                try (Scanner reader = new Scanner(modrinthIndexJson)) {
                    while (reader.hasNextLine()) {
                        builder.append(reader.nextLine());
                    }
                }
            }

            JsonArray modrinthJsonFiles = JsonParser.parseString(builder.toString()).getAsJsonObject().get("files").getAsJsonArray();
            HashMap<String, String> downloadMods = new HashMap<>();

            for (JsonElement file : modrinthJsonFiles) {
                JsonObject fileObj = file.getAsJsonObject();
                downloadMods.put(fileObj.get("path").getAsString(), fileObj.get("downloads").getAsJsonArray().get(0).getAsString());
            }

            downloadMods.forEach((key, value) -> {
                String[] keySplit = key.split("/");
                File dir = new File(output.getAbsoluteFile() + "/" + keySplit[0]);
                if (!dir.exists()) {
                    dir.mkdirs();
                }

                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(value))
                        .build();

                try {
                    HttpResponse<InputStream> send = httpclient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
                    Files.copy(send.body(), Paths.get(output.getAbsolutePath() + "/" + key));

                    System.out.println("Downloaded mods: " + keySplit[1]);
                } catch (IOException | InterruptedException e) {
                    System.err.println("Error downloading: " + keySplit[1]);
                }
            });

        } catch (IOException | InterruptedException | NullPointerException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        System.out.println("Successfully downloaded: " + slug);
    }
}
