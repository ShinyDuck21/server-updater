package dev.shinyduck.mincecraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Client for interacting with the CurseForge API to download mods and modpacks.
 *
 * <p>This class provides methods to query the CurseForge API (v1) for mod files
 * and modpacks, then download them to the local filesystem. It handles API
 * authentication via the {@code x-api-key} header required by CurseForge.
 *
 * <p>Typical workflow:
 * <ol>
 *   <li>Build an HTTP request to the CurseForge API with the appropriate endpoint and API key.</li>
 *   <li>Parse the JSON response to find the latest file by date.</li>
 *   <li>Download the file from the provided download URL.</li>
 *   <li>Save the file to disk (and optionally extract it for modpacks).</li>
 * </ol>
 *
 * @author ShinyDuck21
 */
public class Curseforge {
    /**
     * The base URL for all CurseForge API v1 endpoints.
     */
    public String baseURL = "https://api.curseforge.com/";

    /**
     * Will work on this function later. Not a priority right now.
     * <br>
     * The Goal for the function later is to allow users to provide a file with mod ids to allow for custom lists of mods.
     * @param projectIds
     * @param gameVersion
     * @param modLoader
     * @deprecated
     * @author ShinyDuck21
     */
    @Deprecated
    public void downloadModList(List<String> projectIds, String gameVersion, Modloaders modLoader, String curseforgeApiKey) {
        // Create an HTTP client with a 20-second connection timeout and automatic redirect following.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        try {
            // Iterate over each mod project ID and download the latest file for that mod.
            for (String projectId : projectIds) {
                // Step 1: Query the CurseForge API for all files of this mod matching the game version and mod loader.
                // The endpoint returns a JSON object with a "data" array containing file metadata.
                HttpRequest modReq = HttpRequest.newBuilder()
                        .uri(URI.create(baseURL + "v1/mods/" + projectId + "/files?gameVersion=%s&modLoaderType=%d".formatted(gameVersion, modLoader.getId())))
                        .header("Accept", "application/json")
                        .header("x-api-key", curseforgeApiKey)
                        .build();

                HttpResponse<String> modSend = httpClient.send(modReq, HttpResponse.BodyHandlers.ofString());

                if (modSend.statusCode() != 200) {
                    throw new RuntimeException("Mod with Project ID: %s could not be reached".formatted(projectId));
                }

                // Step 2: Parse the JSON response and extract the "data" array containing file entries.
                JsonArray data = JsonParser.parseString(modSend.body()).getAsJsonObject().getAsJsonArray("data");
                JsonObject newest = null;
                Instant newestDate = null;

                // Step 3: Find the most recently modified file by comparing the "fileDate" field of each entry.
                for (JsonElement item : data) {
                    Instant lastModified = Instant.parse(item.getAsJsonObject().get("fileDate").getAsString());

                    if (newestDate == null) {
                        newestDate = lastModified;
                        newest = item.getAsJsonObject();
                    } else if (newestDate.isBefore(lastModified)) {
                        newest = item.getAsJsonObject();
                        newestDate = lastModified;
                    }
                }

                // Step 4: Extract the filename and download URL from the newest file entry.
                String filename = newest.get("fileName").getAsString();
                String downloadUrl = newest.get("downloadUrl").getAsString();

                // Step 5: Download the mod file as a raw input stream from the provided URL.
                HttpRequest downloadReq = HttpRequest.newBuilder()
                        .uri(URI.create(downloadUrl))
                        .build();

                HttpResponse<InputStream> downloadSend = httpClient.send(downloadReq, HttpResponse.BodyHandlers.ofInputStream());

                if (downloadSend.statusCode() != 200) {
                    throw new RuntimeException("Download with Project ID: %s could not be reached %s".formatted(projectId, downloadSend.statusCode()));
                }

                // Step 6: Save the downloaded file to the current working directory.
                Files.copy(downloadSend.body(), Paths.get(filename));
            }

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Downloads a Modpack from CurseForge via the CurseForge API
     *
     * @param projectId        Project ID of the Modpack
     * @param version          Version of Minecraft you would like to use
     * @param curseforgeApiKey CurseForge API requires an API key to be able to use the API
     * @param output           The Output directory. As a File Object.
     * @author ShinyDuck21
     */
    public void downloadModpack(String projectId, String version, String curseforgeApiKey, File output) {
        // Create an HTTP client that follows normal redirects (temporary redirects only).
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        try {
            // Step 1: Query the CurseForge API for all files of this modpack matching the Minecraft version.
            // The response contains a "data" array with file metadata including download URLs and dates.
            HttpRequest fileReq = HttpRequest.newBuilder()
                    .uri(URI.create(baseURL + "v1/mods/" + projectId + "/files?gameVersion=" + version))
                    .header("x-api-key", curseforgeApiKey)
                    .build();

            HttpResponse<String> fileRes = httpClient.send(fileReq, HttpResponse.BodyHandlers.ofString());
            JsonArray fileArray = JsonParser.parseString(fileRes.body()).getAsJsonObject().get("data").getAsJsonArray();

            // Step 2: Find the latest modpack file by comparing the "fileDate" field of each entry.
            // We iterate through all files and keep track of the one with the most recent date.
            JsonObject latestProject = null;
            Instant latestDate = null;
            for (JsonElement jsonElement : fileArray) {
                JsonObject jsonObject = jsonElement.getAsJsonObject();
                Instant date = Instant.parse(jsonObject.get("fileDate").getAsString());

                if (latestDate == null) {
                    latestDate = date;
                    latestProject = jsonObject;
                } else if (latestDate.isAfter(date)) {
                    latestDate = date;
                    latestProject = jsonObject;
                }
            }

            // Step 3: Download the modpack archive (typically a .zip file) from the provided download URL.
            HttpRequest downloadReq = HttpRequest.newBuilder()
                    .uri(URI.create(latestProject.get("downloadUrl").getAsString()))
                    .header("x-api-key", curseforgeApiKey)
                    .build();

            HttpResponse<InputStream> downloadSend = httpClient.send(downloadReq, HttpResponse.BodyHandlers.ofInputStream());
            String fileName = latestProject.get("fileName").getAsString();
            // Save the downloaded archive to the current working directory.
            Files.copy(downloadSend.body(), Paths.get(fileName));

            // Step 4: Extract the modpack archive to the specified output directory.
            // The unzip utility is shared with the Modrinth class since both use .zip archives.
            Modrinth.unzip(Paths.get(fileName).toAbsolutePath().toString(), output);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
