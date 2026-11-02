package dev.shinyduck.mincecraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class updater {
    public static void main(String[] args) {
        String baseURL = "https://api.curseforge.com/";
        Dotenv dotenv = Dotenv.load();
        String apiKey = dotenv.get("api-key");
        String gameVersion = dotenv.get("game-version");
        Modloaders modLoader = Modloaders.valueOf(dotenv.get("mod-loader").toUpperCase());
        String file = "";
        List<String> projectIds = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            if (arg.equals("--file") || arg.equals("-f")) {
                if (i + 1 >= args.length) {
                    System.err.println("Missing value for " + arg);
                    return;
                }
                file = args[i + 1];
                break;
            }
        }

        File lockfile = new File(file);
        if (lockfile.exists()) {
            try (Scanner scanner = new Scanner(lockfile)) {
                while (scanner.hasNextLine()) {
                    projectIds.add(scanner.nextLine());
                }
            } catch (FileNotFoundException e) {
                System.err.println("File " + file + " not found");
            }
        } else {
            projectIds.addAll(Arrays.asList(args));
        }

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        try {
            for (String projectId : projectIds) {
                HttpRequest modReq = HttpRequest.newBuilder()
                        .uri(URI.create(baseURL + "v1/mods/" + projectId + "/files?gameVersion=%s&modLoaderType=%d".formatted(gameVersion, modLoader.getId())))
                        .header("Accept", "application/json")
                        .header("x-api-key", apiKey)
                        .build();

                HttpResponse<String> modSend = httpClient.send(modReq, HttpResponse.BodyHandlers.ofString());

                if (modSend.statusCode() != 200) {
                    throw new RuntimeException("Mod with Project ID: %s could not be reached".formatted(projectId));
                }

                JsonArray data = JsonParser.parseString(modSend.body()).getAsJsonObject().getAsJsonArray("data");
                JsonObject newest = null;
                Instant newestDate = null;

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

                String filename = newest.get("fileName").getAsString();
                String downloadUrl = newest.get("downloadUrl").getAsString();

                HttpRequest downloadReq = HttpRequest.newBuilder()
                        .uri(URI.create(downloadUrl))
                        .build();

                HttpResponse<InputStream> downloadSend = httpClient.send(downloadReq, HttpResponse.BodyHandlers.ofInputStream());

                if (downloadSend.statusCode() != 200) {
                    throw new RuntimeException("Download with Project ID: %s could not be reached %s".formatted(projectId, downloadSend.statusCode()));
                }

                Files.copy(downloadSend.body(), Paths.get(filename));
            }

            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
