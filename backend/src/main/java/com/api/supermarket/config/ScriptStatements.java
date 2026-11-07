package com.api.supermarket.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;

/** Đọc file script (CQL, Cypher) trong classpath và tách thành từng câu lệnh theo dấu ';'. */
final class ScriptStatements {
    private ScriptStatements() {
    }

    static List<String> load(String classpathLocation) {
        try {
            return split(new ClassPathResource(classpathLocation).getContentAsString(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    static List<String> split(String text) {
        String withoutComments = text.lines()
                .filter(line -> !line.strip().startsWith("--") && !line.strip().startsWith("//"))
                .collect(Collectors.joining("\n"));
        return Arrays.stream(withoutComments.split(";"))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
