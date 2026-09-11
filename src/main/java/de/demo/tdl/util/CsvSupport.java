package de.demo.tdl.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class CsvSupport {

    private CsvSupport() {
    }

    public static List<String[]> readSemicolonFile(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                    .skip(1)
                    .filter(line -> !line.isBlank())
                    .map(line -> line.split(";", -1))
                    .toList();
        } catch (IOException ex) {
            throw new UncheckedIOException("CSV konnte nicht gelesen werden: " + file, ex);
        }
    }
}
