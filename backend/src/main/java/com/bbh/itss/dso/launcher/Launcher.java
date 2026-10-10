package com.bbh.itss.dso.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Comparator.reverseOrder;
import static java.util.concurrent.TimeUnit.SECONDS;

public final class Launcher {

    static final List<String> APPLICATIONS = List.of("dso-portal", "beadle");

    private Launcher() {
    }

    public static void main(String[] args) throws IOException {
        System.exit(run(APPLICATIONS, Launcher::packagedJar, List.of(args)));
    }

    static int run(List<String> applications, Function<String, InputStream> jars, List<String> arguments) throws IOException {
        Path folder = Files.createTempDirectory("bbh-devsecops-");
        String java = ProcessHandle.current().info().command().orElse("java");
        List<Process> processes = new ArrayList<>();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> stop(processes, folder)));
        for (String application : applications) {
            Path jar = folder.resolve(application + ".jar");
            try (InputStream content = jars.apply(application)) {
                Files.copy(content, jar);
            }
            List<String> command = new ArrayList<>(List.of(java, "-jar", jar.toString()));
            command.addAll(arguments);
            processes.add(new ProcessBuilder(command).inheritIO().start());
        }
        Process first = (Process) CompletableFuture.anyOf(processes.stream().map(Process::onExit).toArray(CompletableFuture[]::new)).join();
        stop(processes, folder);
        return first.exitValue();
    }

    private static InputStream packagedJar(String application) {
        return Launcher.class.getResourceAsStream("/applications/" + application + ".jar");
    }

    private static void stop(List<Process> processes, Path folder) {
        processes.forEach(Process::destroy);
        processes.forEach(Launcher::awaitEnd);
        try (Stream<Path> files = Files.walk(folder)) {
            files.sorted(reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException ignored) {
        }
    }

    private static void awaitEnd(Process process) {
        try {
            process.waitFor(10, SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
