package dev.shinyduck.mincecraft;

import io.github.cdimascio.dotenv.Dotenv;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class updater {
    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.load();
        String curseforgeApiKey = dotenv.get("curseforge-api-key");
        String gameVersion = dotenv.get("game-version");
        //Modloaders modLoader = Modloaders.valueOf(dotenv.get("mod-loader").toUpperCase());
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

        Modrinth modrinth = new Modrinth();
        modrinth.downloadModpack("i4XHCd7Q", "1.21.1");
        }
    }
