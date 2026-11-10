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

    /**
     * Generates a hash for the given file
     *
     * @param filePath  File Path of the target file. As a string
     * @param algorithm The Hashing Algorithm used
     * @return The Hash of the File
     * @throws IOException              Thrown when Files don't exist or cannot retrieve file
     * @throws NoSuchAlgorithmException Thrown when the Hashing Function doesn't recognize the Algorithm Provided
     * @author ShinyDuck21
     */
    @org.jetbrains.annotations.NotNull
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

    /**
     * Unzips a Zip File
     * @param zipFile The Zip File to unzip (As a string path)
     * @param output Where to output the Zip File contents (As a File object)
     * @throws IOException Thrown when the IO Functions fail for some reason.
     * @author ShinyDuck21
     */
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
     * @author ShinyDuck21
     */
    public void downloadModpack(String slug, String version, File output) {
        // Sets up the HTTP Client for the Function
        HttpClient httpclient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        // Corrects the Formatting for the Version parameter.
        String gameVersionEncode = URLEncoder.encode("[\"" + version + "\"]", StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "project/" + slug + "/version?game_versions=" + gameVersionEncode))
                .build();

        try {
            // Sends the Request ot modrinth for the list versions of the modpack.
            HttpResponse<String> res = httpclient.send(request, HttpResponse.BodyHandlers.ofString());

            // Filters down to the most recent version
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

            // Retrieves the file's download URL and filename
            JsonObject files = latestProject.getAsJsonArray("files").get(0).getAsJsonObject();
            String downloadUrl = files.get("url").getAsString();
            String filename = files.get("filename").getAsString();
            HttpRequest downloadReq = HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .build();

            // Downloads the File
            HttpResponse<InputStream> downloadSend = httpclient.send(downloadReq, HttpResponse.BodyHandlers.ofInputStream());
            Path mrpackPath = Paths.get(filename);
            Files.copy(downloadSend.body(), mrpackPath);

            File mrpack = new File(mrpackPath.getFileName().toString());
            // Ensures the file downloaded correctly with the hash
            String hash = generateHash(mrpack.getAbsolutePath(), "sha512");

            if (!hash.equals(files.get("hashes").getAsJsonObject().get("sha512").getAsString())) {
                throw new IOException("Download error occured. please try again later.");
            }
            System.out.println("Downloaded: " + filename);

            /*
            Modrinth has a .mrpack file which is a version of a zip file containing the overrides
            or and the manifest file inside it.

            This Renames the file to a zip file to access the manifest file.
             */
            filename = filename.replace(".mrpack", ".zip");
            mrpack.renameTo(new File(mrpack.getParentFile(), filename));
            System.out.println("Renamed: " + filename);

            // Unzips the zip file
            unzip(Paths.get(filename).toAbsolutePath().toString(), output);

            // Reads the manifest file
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

            // Maps each file to its file name
            for (JsonElement file : modrinthJsonFiles) {
                JsonObject fileObj = file.getAsJsonObject();
                downloadMods.put(fileObj.get("path").getAsString(), fileObj.get("downloads").getAsJsonArray().get(0).getAsString());
            }

            downloadMods.forEach((key, value) -> {
                /* The name contains it's type (mod, resource pack, etc.) in the string.
                (ex. "mod/crazymod.jar") this converts the name to its type and it's name
                 */
                String[] keySplit = key.split("/");
                // Creates the directory of the type if it doesn't exist
                File dir = new File(output.getAbsoluteFile() + "/" + keySplit[0]);
                if (!dir.exists()) {
                    dir.mkdirs();
                }

                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(value))
                        .build();

                try {
                    // Downloads all the mods and puts it in the correct location.
                    HttpResponse<InputStream> send = httpclient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
                    Files.copy(send.body(), Paths.get(output.getAbsolutePath() + "/" + key));

                    System.out.println("Downloaded mod: " + keySplit[1]);
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
