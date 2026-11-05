package dev.shinyduck.mincecraft;

import io.github.cdimascio.dotenv.Dotenv;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class updater {
    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.load();
        List<String> argList = new ArrayList<>(Arrays.asList(args));
        File output = new File("output");


        if (argList.isEmpty() || argList.contains("--help") || argList.contains("-h")) {
            System.out.println("Minecraft modpack downloader");
            System.out.println("Usage: mcdownloader.jar <provider> <id/slug> <version>");
            System.out.println("Run with the following arguments:");
            System.out.println("    - provider: Option to pull from either Curseforge or Modrinth");
            System.out.println("    - id: Id of the modpack");
            System.out.println("    - version: Version of Minecraft");
            System.out.println("Environment Variables are used to store sensitive info");
            System.out.println("Key environment variables are:");
            System.out.println("    curseforge-api-key (Note: If you are not using curseforge you do not need to provide an API key");

            System.exit(0);
        }

        if (argList.contains("-o") || argList.contains("--output")) {
            int index = argList.indexOf("--output");
            if (index == -1) {
                index = argList.indexOf("-o");
            }

            if (argList.get(index + 1).equalsIgnoreCase("curseforge") || argList.get(index + 1).equalsIgnoreCase("modrinth")) {
                System.err.println("Please provide a valid output directory");
                System.exit(1);
            }

            output = new File(argList.get(index + 1));

            argList.remove("-o");
            argList.remove("--output");
        }

        if (argList.getFirst().equalsIgnoreCase("modrinth")) {
            Modrinth modrinth = new Modrinth();
            modrinth.downloadModpack(argList.get(1), argList.get(2), output);
        } else if (argList.getFirst().equalsIgnoreCase("curseforge")) {
            Curseforge curseforge = new Curseforge();
            Dotenv dotenv = Dotenv.load();
            String curseforgeApiKey = dotenv.get("curseforge-api-key");
            curseforge.downloadModpack(argList.get(1), argList.get(2), curseforgeApiKey, output);
        } else {
            System.out.println("That provider is not allowed. If you would like to add it please make a issue on github.");
        }
    }
}