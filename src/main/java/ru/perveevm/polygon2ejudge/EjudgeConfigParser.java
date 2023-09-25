package ru.perveevm.polygon2ejudge;

import ru.perveevm.polygon2ejudge.exceptions.ContestManagerException;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Mike Perveev (perveev_m@mail.ru)
 */
public class EjudgeConfigParser {
    private String prefix;
    private final List<String> problems = new ArrayList<>();

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

    public List<String> getProblems() {
        return problems;
    }

    public void removeProblemById(final int id) throws ContestManagerException {
        for (int i = 0; i < problems.size(); i++) {
            String problemConfig = problems.get(i);
            String currentId = getArgumentValue(problemConfig, "id");
            if (currentId == null) {
                throw new ContestManagerException("id is null for some of the problems");
            }

            if (Integer.parseInt(currentId) == id) {
                problems.remove(i);
                return;
            }
        }

        throw new ContestManagerException(String.format("problem with id %d not found", id));
    }

    public String getProblemInternalNameById(final int id) throws ContestManagerException {
        for (String problemConfig : problems) {
            String currentName = getArgumentValue(problemConfig, "internal_name");
            String currentId = getArgumentValue(problemConfig, "id");
            if (currentName == null || currentId == null) {
                throw new ContestManagerException("internal_name or id is null for some of the problems");
            }

            if (Integer.parseInt(currentId) == id) {
                return currentName;
            }
        }

        throw new ContestManagerException(String.format("problem internal_name not found for problem %d", id));
    }

    public int getProblemIdByInternalName(final String internalName) throws ContestManagerException {
        for (String problemConfig : problems) {
            String currentName = getArgumentValue(problemConfig, "internal_name");
            String currentId = getArgumentValue(problemConfig, "id");
            if (currentName == null || currentId == null) {
                throw new ContestManagerException("internal_name or id is null for some of the problems");
            }

            if (internalName.equals(currentName)) {
                return Integer.parseInt(currentId);
            }
        }

        throw new ContestManagerException(String.format("problem id not found for problem %s", internalName));
    }

    private String getArgumentValue(final String problemConfig, final String argumentName) {
        String[] lines = problemConfig.split(System.lineSeparator());
        for (String line : lines) {
            if (getArgumentName(line).equals(argumentName)) {
                return getArgumentValue(line);
            }
        }
        return null;
    }

    private String getArgumentName(final String line) {
        if (line.contains("=")) {
            return line.substring(0, line.indexOf("=")).strip();
        } else {
            return line.strip();
        }
    }

    private String getArgumentValue(final String line) {
        if (line.contains("=")) {
            String value = line.substring(line.indexOf("=") + 1).strip();
            if (value.startsWith("\"")) {
                return value.substring(value.indexOf("\"") + 1, value.lastIndexOf("\""));
            } else {
                return value;
            }
        } else {
            return null;
        }
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
