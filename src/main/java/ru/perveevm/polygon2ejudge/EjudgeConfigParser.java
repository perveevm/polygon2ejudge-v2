package ru.perveevm.polygon2ejudge;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class EjudgeConfigParser {
    private String prefix;
    private List<String> problems = new ArrayList<>();

    public EjudgeConfigParser() {
    }

    public void parse(final Path configPath) throws IOException {
        List<String> lines;
        try (BufferedReader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            lines = reader.lines().toList();
        }

        StringBuilder configPrefix = new StringBuilder();
        int row = 0;
        while (row < lines.size()) {
            if (lines.get(row).strip().startsWith("[problem]")) {
                prefix = configPrefix.toString();
                break;
            }
            configPrefix.append(lines.get(row)).append(System.lineSeparator());
            row++;
        }
        if (row == lines.size()) {
            prefix = configPrefix.toString();
        }

        problems.clear();
        while (row < lines.size()) {
            if (lines.get(row).strip().startsWith("[tester]")) {
                break;
            }

            StringBuilder currentProblem = new StringBuilder();
            do {
                currentProblem.append(lines.get(row)).append(System.lineSeparator());
                row++;
            } while (row != lines.size() && !lines.get(row).strip().startsWith("[problem]"));
            problems.add(currentProblem.toString());
        }
    }

    public String getPrefix() {
        return prefix;
    }

    public List<String> getProblems() {
        return problems;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append(prefix);
        for (String problem : problems) {
            result.append(System.lineSeparator()).append(problem);
        }
        return result.toString();
    }
}
