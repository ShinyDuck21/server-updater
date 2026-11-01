package dev.shinyduck.mincecraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;

public class updater {
    public static void main(String[] args) {
        String baseURL = "https://api.curseforge.com/";
        Dotenv dotenv = Dotenv.load();
        String apiKey = dotenv.get("api-key");
        String gameVersion = dotenv.get("game-version");
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
                .build();

        try {
            HttpRequest versionReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.curseforge.com/v1/minecraft/version"))
                    .header("User-Agent", "ShinyDuck")
                    .header("Accept", "application/json")
                    .header("x-api-key", apiKey).build();

            HttpResponse<String> versionHttpResponse = httpClient.send(versionReq, HttpResponse.BodyHandlers.ofString());

            if (versionHttpResponse.statusCode() == 200) {
                JsonObject root = JsonParser.parseString(versionHttpResponse.body()).getAsJsonObject();
                JsonArray versions = root.getAsJsonArray("data");


            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        for (String projectId : projectIds) {
            try {
                AtomicReference<Boolean> correctVersion = new AtomicReference<>(false);
                Boolean correctLoader = false;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI(baseURL + "v1/mods/" + projectId))
                        .header("User-Agent", "ShinyDuck")
                        .header("Accept", "application/json")
                        .header("x-api-key", apiKey).build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int statusCode = response.statusCode();
                JsonObject correctModVersion;

                if (statusCode == 200) {
                    String body = response.body();
                    JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                    JsonObject data = root.getAsJsonObject("data");
                    JsonArray latestFiles = data.getAsJsonArray("latestFiles");

                    latestFiles.forEach(jsonElement -> {
                        JsonObject jsonObj = jsonElement.getAsJsonObject();

                        JsonArray gameVersions = jsonObj.getAsJsonArray("gameVersions");
                        gameVersions.forEach(jsonElement1 -> {
                            String elem = jsonElement1.getAsString();

                            if (elem.equals(correctVersion)) {
                                correctVersion.set(true);
                            } else if ()
                        });
                    });
                } else {
                    System.err.println(response.statusCode() + " " + response.body());
                }

            } catch (IOException | URISyntaxException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
