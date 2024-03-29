package ru.perveevm.polygon2ejudge;

import net.lingala.zip4j.ZipFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.select.Elements;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import ru.perveevm.polygon.api.PolygonSession;
import ru.perveevm.polygon.api.entities.*;
import ru.perveevm.polygon.api.entities.enums.PackageState;
import ru.perveevm.polygon.api.entities.enums.SolutionTag;
import ru.perveevm.polygon.api.entities.enums.TestGroupFeedbackPolicy;
import ru.perveevm.polygon.api.entities.enums.TestGroupPointsPolicy;
import ru.perveevm.polygon.exceptions.api.PolygonSessionException;
import ru.perveevm.polygon.exceptions.user.PolygonUserSessionException;
import ru.perveevm.polygon.user.PolygonUserSession;
import ru.perveevm.polygon2ejudge.exceptions.ContestManagerException;
import ru.perveevm.polygon2ejudge.exceptions.EjudgeSessionException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Mike Perveev (perveev_m@mail.ru)
 */
public class ContestManager {
    private final Logger log;
    private final PolygonSession session;
    private final PolygonUserSession userSession;

    private final Path contestsDir;
    private final String statementsLang;
    private final Path statementsDir;
    private final Path gvaluerPath;
    private final String statementsUrlPrefix;

    public ContestManager() throws ContestManagerException {
        try (InputStream in = ContestManager.class.getClassLoader().getResourceAsStream("logging.properties")) {
            LogManager.getLogManager().readConfiguration(in);
            log = Logger.getLogger(ContestManager.class.getName());
        } catch (IOException e) {
            throw new ContestManagerException("failed to initialize logger", e);
        }

        try (InputStream in = ContestManager.class.getClassLoader().getResourceAsStream("app.properties")) {
            Properties properties = new Properties();
            properties.load(in);

            session = new PolygonSession(properties.getProperty("polygon.key"),
                    properties.getProperty("polygon.secret"));
            userSession = new PolygonUserSession(properties.getProperty("polygon.login"),
                    properties.getProperty("polygon.password"));
            contestsDir = Path.of(properties.getProperty("ejudge.contestsDir"));
            statementsLang = properties.getProperty("ejudge.statementsLang");
            statementsDir = Path.of(properties.getProperty("ejudge.statementsDir"));
            gvaluerPath = Path.of(properties.getProperty("ejudge.gvaluerPath"));
            statementsUrlPrefix = properties.getProperty("ejudge.statementsUrlPrefix");
        } catch (IOException e) {
            throw new ContestManagerException("failed to load properties", e);
        }
    }

    private List<String> moveFiles(final Path tmpDir, final Path problemDirectory, final Problem problem,
                                   final ProblemInfo problemInfo)
            throws IOException, PolygonSessionException, ContestManagerException {
        List<String> names = new ArrayList<>();

        log.info("Moving tests...");
        Files.move(tmpDir.resolve("tests"), problemDirectory.resolve("tests"));

        log.info("Moving solutions...");
        Files.move(tmpDir.resolve("solutions"), problemDirectory.resolve("solutions"));

        log.info("Moving checker...");
        String polygonCheckerName = session.problemChecker(problem.getId());
        Path checkerPath = tmpDir.resolve("files").resolve(polygonCheckerName);
        if (!Files.exists(checkerPath)) {
            checkerPath = tmpDir.resolve("files").resolve("check.cpp");
        }
        Files.move(checkerPath, problemDirectory.resolve(checkerPath.getFileName()));
        names.add(checkerPath.getFileName().toString());

        log.info("Moving resource files...");
        ProblemFiles files = session.problemFiles(problem.getId());
        for (ProblemFile file : files.getResourceFiles()) {
            String extension = file.getName().split("\\.")[1];
            if (extension.equals("sty") || extension.equals("tex") || extension.equals("ftl")) {
                continue;
            }
            Files.move(tmpDir.resolve("files").resolve(file.getName()), problemDirectory.resolve(file.getName()));
        }

        log.info("Moving main correct solution...");
        Solution[] solutions = session.problemSolutions(problem.getId());
        Optional<Solution> mainSolution = Arrays.stream(solutions).filter(s -> s.getTag() == SolutionTag.MA).findAny();
        if (mainSolution.isEmpty()) {
            throw new ContestManagerException("there is no Main correct solution");
        }
        Files.copy(problemDirectory.resolve("solutions").resolve(mainSolution.get().getName()),
                problemDirectory.resolve(mainSolution.get().getName()));
        names.add(mainSolution.get().getName());

        if (problemInfo.getInteractive()) {
            log.info("Moving interactor...");
            String interactor = session.problemInteractor(problem.getId());
            Files.move(tmpDir.resolve("files").resolve(interactor), problemDirectory.resolve(interactor));
            names.add(interactor);
        }

        return names;
    }

    private String removeExtension(final String name) {
        return name.substring(0, name.lastIndexOf('.'));
    }

    private List<TestInformation> getTests(final Path tmpDirectory)
            throws ParserConfigurationException, IOException, SAXException {
        Path configPath = tmpDirectory.resolve("problem.xml");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        org.w3c.dom.Document document = builder.parse(configPath.toFile());
        document.getDocumentElement().normalize();

        log.info("Parsing tests...");
        org.w3c.dom.Element testsElement = (org.w3c.dom.Element) document.getElementsByTagName("tests").item(0);
        List<TestInformation> tests = new ArrayList<>();
        NodeList allTestsElement = testsElement.getElementsByTagName("test");
        for (int i = 0; i < allTestsElement.getLength(); i++) {
            org.w3c.dom.Element testElement = (org.w3c.dom.Element) allTestsElement.item(i);
            TestInformation test = new TestInformation();
            if (testElement.hasAttribute("points")) {
                test.setPoints(Double.parseDouble(testElement.getAttribute("points")));
            }
            if (testElement.hasAttribute("group")) {
                test.setGroup(testElement.getAttribute("group"));
            }
            test.setUseInSamples(testElement.hasAttribute("sample") &&
                    testElement.getAttribute("sample").equals("true"));
            tests.add(test);
        }
        return tests;
    }

    private String generateProblemConfig(final ProblemInfo problemInfo, final Problem problem,
                                         final int ejudgeProblemId, final String problemShortName,
                                         final List<String> fileNames, final Path problemDirectory)
            throws PolygonSessionException, ContestManagerException {
        int timeLimit = problemInfo.getTimeLimit();
        int memoryLimit = problemInfo.getMemoryLimit();
        List<TestInformation> tests;
        try {
            tests = getTests(problemDirectory.resolve("tmp"));
        } catch (ParserConfigurationException | IOException | SAXException e) {
            throw new ContestManagerException("could not parse problem.xml");
        }

        Map<String, Statement> statements = session.problemStatements(problem.getId());
        boolean pointsEnabled = false;
        boolean groupsEnabled = false;
        int totalScore = 0;
        for (TestInformation test : tests) {
            if (test.getPoints() != null) {
                pointsEnabled = true;
                totalScore += test.getPoints();
            }
            if (test.getGroup() != null) {
                groupsEnabled = true;
            }
        }
        String title = "Undefined";
        if (statements.containsKey(statementsLang)) {
            title = statements.get(statementsLang).getName();
        } else {
            log.warning(String.format("There is no statements in %s", statementsLang));
        }

        log.info("Generating problem config...");
        Map<String, String> config = new LinkedHashMap<>();
        config.put("id", String.valueOf(ejudgeProblemId));
        config.put("short_name", "\"" + problemShortName + "\"");
        config.put("long_name", "\"" + title + "\"");
        config.put("internal_name", "\"" + problem.getName() + "\"");
        config.put("extid", "\"polygon:" + problem.getId() + "\"");
        config.put("use_stdin", "");
        config.put("use_stdout", "");
        config.put("xml_file", "\"statement.xml\"");
        config.put("test_pat", "\"%02d\"");
        config.put("use_corr", "");
        config.put("corr_pat", "\"%02d.a\"");
        if (timeLimit % 1000 == 0) {
            config.put("time_limit", String.valueOf(timeLimit / 1000));
        } else {
            config.put("time_limit_millis", String.valueOf(timeLimit));
        }
        config.put("real_time_limit", String.valueOf((2 * timeLimit + 999) / 1000));
        config.put("max_vm_size", memoryLimit + "M");
        config.put("max_stack_size", memoryLimit + "M");
        if (pointsEnabled) {
            int lastSample = 0;
            for (TestInformation test : tests) {
                if (test.getUseInStatements()) {
                    lastSample++;
                } else {
                    break;
                }
            }
            config.put("full_score", String.valueOf(totalScore));
            config.put("full_user_score", String.valueOf(totalScore));
            config.put("run_penalty", "0");
            config.put("test_score_list", tests.stream()
                    .map(TestInformation::getPoints)
                    .map(Double::intValue)
                    .map(String::valueOf)
                    .collect(Collectors.joining(" ", "\"", "\"")));
            if (lastSample == 0) {
                config.put("open_tests", String.format("\"1-%d:brief\"", tests.size()));
            } else {
                config.put("open_tests",
                        String.format("\"1-%d:full,%d-%d:brief\"", lastSample, lastSample + 1, tests.size()));
            }
            config.put("final_open_tests", String.format("\"1-%d:full\"", tests.size()));
        }
        if (groupsEnabled) {
            log.info("Generating valuer.cfg...");
            TestGroup[] groups = session.problemViewTestGroup(problem.getId(), "tests", null);
            Map<String, List<Integer>> groupTests = new HashMap<>();
            for (int i = 0; i < tests.size(); i++) {
                if (!groupTests.containsKey(tests.get(i).getGroup())) {
                    groupTests.put(tests.get(i).getGroup(), new ArrayList<>());
                }
                groupTests.get(tests.get(i).getGroup()).add(i);
            }

            StringBuilder openTests = new StringBuilder();
            StringBuilder valuer = new StringBuilder();

            valuer.append("""
                    global {
                    	stat_to_users;
                    }
                    """).append(System.lineSeparator());

            for (TestGroup group : groups) {
                int first = groupTests.get(group.getName()).get(0);
                int last = groupTests.get(group.getName()).get(groupTests.get(group.getName()).size() - 1);

                if (!openTests.isEmpty()) {
                    openTests.append(",");
                }
                switch (group.getFeedbackPolicy()) {
                    case NONE, POINTS -> openTests.append(String.format("%d-%d:hidden", first + 1, last + 1));
                    case ICPC -> openTests.append(String.format("%d-%d:brief", first + 1, last + 1));
                    case COMPLETE -> openTests.append(String.format("%d-%d:full", first + 1, last + 1));
                }

                int score = 0;
                int minScore = Integer.MAX_VALUE;
                int maxScore = Integer.MIN_VALUE;
                for (int testId : groupTests.get(group.getName())) {
                    int currentScore = tests.get(testId).getPoints().intValue();
                    score += currentScore;
                    minScore = Math.min(minScore, currentScore);
                    maxScore = Math.max(maxScore, currentScore);
                }
                String dependencies = String.join(",", group.getDependencies());

                if (last - first + 1 != groupTests.get(group.getName()).size()) {
                    throw new ContestManagerException("some groups are not continuous");
                }

                valuer.append(String.format("group %s {", group.getName())).append(System.lineSeparator());

                valuer.append(String.format("\ttests %d-%d;", first + 1, last + 1)).append(System.lineSeparator());
                valuer.append(String.format("\tscore %d;", score)).append(System.lineSeparator());
                if (group.getDependencies() != null && group.getDependencies().length > 0) {
                    valuer.append(String.format("\trequires %s;", dependencies)).append(System.lineSeparator());
                }
                if (group.getFeedbackPolicy() == TestGroupFeedbackPolicy.COMPLETE
                        || group.getPointsPolicy() == TestGroupPointsPolicy.EACH_TEST) {
                    valuer.append("\ttest_all;").append(System.lineSeparator());
                }
                if (group.getPointsPolicy() == TestGroupPointsPolicy.EACH_TEST) {
                    if (minScore != maxScore) {
                        throw new ContestManagerException("group with EACH_TEST policy has tests with different scores");
                    }
                    valuer.append(String.format("\ttest_score %d;", minScore)).append(System.lineSeparator());
                }
                valuer.append("}").append(System.lineSeparator());
            }

            config.put("open_tests", openTests.toString());
            try {
                Files.copy(gvaluerPath, problemDirectory.resolve("gvaluer"));
            } catch (IOException e) {
                throw new ContestManagerException("failed to copy gvaluer", e);
            }
            config.put("valuer_cmd", "gvaluer");
            config.put("interactive_valuer", "");

            try (BufferedWriter writer = Files.newBufferedWriter(problemDirectory.resolve("valuer.cfg"))) {
                writer.write(valuer.toString());
            } catch (IOException e) {
                throw new ContestManagerException("failed to write valuer.cfg");
            }
        }
        config.put("check_cmd", "\"" + removeExtension(fileNames.get(0)) + "\"");
        config.put("solution_cmd", "\"" + removeExtension(fileNames.get(1)) + "\"");
        if (fileNames.size() == 3) {
            config.put("interactor_cmd", "\"" + removeExtension(fileNames.get(2)) + "\"");
        }
        config.put("enable_testlib_mode", "");
        config.put("enable_text_form", "");
        config.put("enable_user_input", "");

        StringBuilder configString = new StringBuilder();
        configString.append("[problem]").append(System.lineSeparator());
        for (Map.Entry<String, String> entry : config.entrySet()) {
            if (entry.getValue().isEmpty()) {
                configString.append(entry.getKey());
            } else {
                configString.append(entry.getKey()).append(" = ").append(entry.getValue());
            }
            configString.append(System.lineSeparator());
        }

        return configString.toString();
    }

    private void generateStatement(final Path tmpDir, final Problem problem, final Path problemDirectory,
                                   final String statementsUrl)
            throws PolygonSessionException, IOException {
        Map<String, Statement> statements = session.problemStatements(problem.getId());

        log.info("Generating statement...");
        log.info("Copying statement resources...");
        Path statementPath = tmpDir.resolve("statements").resolve(".html").resolve(statementsLang).resolve("problem.html");
        Files.createDirectory(problemDirectory.resolve("attachments"));
        try (Stream<Path> files = Files.list(tmpDir.resolve("statements").resolve(".html").resolve(statementsLang))) {
            files.map(Path::getFileName)
                    .map(Path::toString)
                    .filter(p -> !p.endsWith(".html") && !p.endsWith(".css"))
                    .forEach(p -> {
                        try {
                            Files.copy(tmpDir.resolve("statements").resolve(".html").resolve(statementsLang).resolve(p),
                                    problemDirectory.resolve("attachments").resolve(p));
                        } catch (IOException e) {
                            log.warning(String.format("Could not copy statement resource %s", p));
                        }
                    });
        }

        String content = "No statement available";
        if (Files.exists(statementPath)) {
            Document document = Jsoup.parse(statementPath.toFile());
            document.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
            document.outputSettings().escapeMode(Entities.EscapeMode.xhtml);
            document.outputSettings().indentAmount(0).prettyPrint(false);
            Elements legendElements = document.getElementsByClass("problem-statement");
            if (!legendElements.isEmpty()) {
                Element legendElement = legendElements.get(0);
                legendElement.select(".header").remove();
                legendElement.select("img").forEach(element -> {
                    String src = element.attr("src");
                    element.attr("src", "${getfile}=" + src);
                });

                content = legendElement.toString().replace("$$$$$$", "$$").replace("$$$", "$");
            }
        } else {
            log.warning(String.format("There is no statements in %s", statementsLang));
        }

        String statement = String.format("""
                <?xml version="1.0" encoding="utf-8" ?>
                <problem>
                <statement language="ru_RU">
                <description>
                <p><a href = "%s">[Условия всех задач в pdf]</a></p>
                %s
                </description>
                </statement>
                </problem>
                """, statementsUrl, content);

        try (BufferedWriter writer = Files.newBufferedWriter(problemDirectory.resolve("statement.xml"),
                StandardCharsets.UTF_8)) {
            writer.write(statement);
        }
    }

    private String importProblem(final Problem problem, final int ejudgeContestId, final int ejudgeProblemId,
                                 final String problemShortName, final String statementsUrl)
            throws PolygonSessionException, IOException, ContestManagerException {
        Path contestDirectory = contestsDir.resolve(String.format("%06d", ejudgeContestId));
        Path problemDirectory = contestDirectory.resolve("problems").resolve(problem.getName());
        log.info(String.format("Importing problem %s to %s", problem.getName(), contestDirectory));

        Path tmpDir = problemDirectory.resolve("tmp");
        Files.createDirectory(problemDirectory);
        Files.createDirectory(tmpDir);

        ProblemInfo problemInfo = session.problemInfo(problem.getId());
        ProblemPackage[] packages = session.problemPackages(problem.getId());
        Optional<ProblemPackage> lastPackage = Arrays.stream(packages)
                .filter(p -> p.getState() == PackageState.READY)
                .max(Comparator.comparingInt(ProblemPackage::getId));
        if (lastPackage.isEmpty()) {
            throw new ContestManagerException("there is no generated READY package");
        }

        log.info("Downloading package #" + lastPackage.get().getId());
        Path packagePath = tmpDir.resolve("package.zip");
        session.problemPackage(problem.getId(), lastPackage.get().getId(), "linux", packagePath.toFile());

        log.info("Extracting archive...");
        try (ZipFile zipFile = new ZipFile(packagePath.toFile())) {
            zipFile.extractAll(tmpDir.toString());
        }

        List<String> fileNames = moveFiles(tmpDir, problemDirectory, problem, problemInfo);
        String configString = generateProblemConfig(problemInfo, problem, ejudgeProblemId, problemShortName, fileNames,
                problemDirectory);
        generateStatement(tmpDir, problem, problemDirectory, statementsUrl);

        log.info("Cleaning up...");
        FileUtils.deleteDirectory(tmpDir.toFile());

        return configString;
    }

    public void importContest(final int polygonContestId, final int ejudgeContestId) throws ContestManagerException {
        Path contestDirectory = contestsDir.resolve(String.format("%06d", ejudgeContestId));
        log.info(String.format("Importing contest %d to %s", polygonContestId, contestDirectory));

        Random random = new Random(ejudgeContestId);
        String statementsFile = "contest-" + ejudgeContestId + "-" +
                RandomStringUtils.random(8, 'a', 'z', true, false, null, random) + ".pdf";

        Map<String, Problem> problems;
        try {
            problems = session.contestProblems(polygonContestId);
            log.info(String.format("Problems list loaded, found %d problems: %s",
                    problems.size(),
                    problems.values().stream()
                            .map(Problem::getName)
                            .collect(Collectors.joining(System.lineSeparator() + "\t",
                                    System.lineSeparator() + "\t", ""))));
        } catch (PolygonSessionException e) {
            throw new ContestManagerException("failed to load problems list", e);
        }

        try {
            Files.createDirectory(contestDirectory.resolve("problems"));
        } catch (IOException e) {
            throw new ContestManagerException("failed to create problems directory", e);
        }

        log.info("Parsing serve.cfg");
        EjudgeConfigParser parser = new EjudgeConfigParser();
        Path serveCfgPath = contestDirectory.resolve("conf").resolve("serve.cfg");
        try {
            parser.parse(serveCfgPath);
            Files.move(serveCfgPath, contestDirectory.resolve("conf").resolve("serve.cfg.old"));
        } catch (IOException e) {
            try {
                Files.delete(contestDirectory.resolve("problems"));
            } catch (IOException ignored) {
            }

            throw new ContestManagerException("failed to create problems directory", e);
        }

        int problemId = 0;
        for (String shortName : problems.keySet()) {
            Problem problem = problems.get(shortName);
            problemId++;

            try {
                String problemConfig = importProblem(problem, ejudgeContestId, problemId, shortName,
                        statementsUrlPrefix + "/" + statementsFile);
                parser.getProblems().add(problemConfig);
            } catch (PolygonSessionException | IOException e) {
                log.warning(String.format("Failed to load problem %s (%s)", problem.getName(), e.getMessage()));

                try {
                    FileUtils.deleteDirectory(contestDirectory.resolve("problems").resolve(problem.getName()).toFile());
                } catch (IOException ignored) {
                }
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter(contestDirectory.resolve("conf").resolve("serve.cfg"),
                StandardCharsets.UTF_8)) {
            writer.write(parser.toString());
        } catch (IOException e) {
            try {
                Files.delete(contestDirectory.resolve("problems"));
            } catch (IOException ignored) {
            }

            throw new ContestManagerException("failed to write serve.cfg file", e);
        }

        if (Files.exists(statementsDir.resolve(statementsFile))) {
            try {
                Files.delete(statementsDir.resolve(statementsFile));
            } catch (IOException e) {
                throw new ContestManagerException("failed to delete old statements file", e);
            }
        }

        log.info("Downloading PDF statements...");
        try {
            userSession.contestGetStatementsFromPackages(polygonContestId, statementsDir.resolve(statementsFile));
        } catch (PolygonUserSessionException e) {
            throw new ContestManagerException("failed to download pdf statements", e);
        }
    }

    public void removeProblem(final int ejudgeContestId, final int problemId) throws ContestManagerException {
        Path contestDirectory = contestsDir.resolve(String.format("%06d", ejudgeContestId));
        log.info(String.format("Removing problem %d from contest %s", problemId, contestDirectory));

        log.info("Getting problem internal name");
        EjudgeConfigParser parser = new EjudgeConfigParser();
        try {
            parser.parse(contestDirectory.resolve("conf").resolve("serve.cfg"));
        } catch (IOException e) {
            throw new ContestManagerException("failed to parse serve.cfg", e);
        }

        String internalName = parser.getProblemInternalNameById(problemId);

        log.info("Cleaning serve.cfg");
        parser.removeProblemById(problemId);

        log.info(String.format("Removing \"%s\" directory", internalName));
        Path problemsDirectory = contestDirectory.resolve("problems");
        if (Files.exists(problemsDirectory.resolve(internalName))) {
            try {
                FileUtils.deleteDirectory(problemsDirectory.resolve(internalName).toFile());
            } catch (IOException e) {
                throw new ContestManagerException("failed to delete problem directory", e);
            }
        }
    }

    public void removeContest(final int ejudgeContestId) throws ContestManagerException {
        Path contestDirectory = contestsDir.resolve(String.format("%06d", ejudgeContestId));
        log.info(String.format("Removing contest %s", contestDirectory));

        log.info("Removing \"problems\" directory");
        Path problemsDirectory = contestDirectory.resolve("problems");
        if (Files.exists(problemsDirectory)) {
            try {
                FileUtils.deleteDirectory(problemsDirectory.toFile());
            } catch (IOException e) {
                throw new ContestManagerException("failed to delete problems directory", e);
            }
        }

        log.info("Cleaning serve.cfg");
        EjudgeConfigParser parser = new EjudgeConfigParser();
        try {
            parser.parse(contestDirectory.resolve("conf").resolve("serve.cfg"));
        } catch (IOException e) {
            throw new ContestManagerException("failed to parse serve.cfg", e);
        }

        parser.getProblems().clear();

        try (BufferedWriter writer = Files.newBufferedWriter(contestDirectory.resolve("conf").resolve("serve.cfg"),
                StandardCharsets.UTF_8)) {
            writer.write(parser.toString());
        } catch (IOException e) {
            throw new ContestManagerException("failed to write serve.cfg", e);
        }
    }

    public void submitProblem(final int ejudgeContestId, final int problemId)
            throws EjudgeSessionException, ContestManagerException {
        EjudgeSession ejudgeSession = new EjudgeSession();
        Path contestDirectory = contestsDir.resolve(String.format("%06d", ejudgeContestId));
        log.info(String.format("Submitting all runs for contest %d and problem %d", ejudgeContestId, problemId));

        EjudgeConfigParser parser = new EjudgeConfigParser();
        try {
            parser.parse(contestDirectory.resolve("conf").resolve("serve.cfg"));
        } catch (IOException e) {
            throw new ContestManagerException("failed to parse serve.cfg", e);
        }
        String internalName = parser.getProblemInternalNameById(problemId);
        Path problemsDirectory = contestDirectory.resolve("problems");
        Path problemPath = problemsDirectory.resolve(internalName);
        Path solutionsPath = problemPath.resolve("solutions");
        try (Stream<Path> solutionsStream = Files.list(solutionsPath)) {
            solutionsStream
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .map(filename -> filename.split("\\."))
                    .filter(parts -> parts.length == 2)
                    .forEach(parts -> {
                        String source;
                        Path solutionPath = solutionsPath.resolve(String.join(".", parts));
                        try (BufferedReader reader = Files.newBufferedReader(solutionPath, StandardCharsets.UTF_8)) {
                            source = reader.lines().collect(Collectors.joining(System.lineSeparator()));
                        } catch (IOException e) {
                            log.warning("Could not read solution file");
                            return;
                        }

                        try {
                            ejudgeSession.submitSolution(ejudgeContestId, source, problemId, parts[1]);
                        } catch (EjudgeSessionException e) {
                            log.warning(String.format("Could not read solution file %s",
                                    String.join(".", parts)));
                        }
                    });
        } catch (IOException e) {
            log.warning(String.format("Failed to iterate over solutions of problem %s",
                    problemPath.getFileName()));
        }
    }

    public void submitContest(final int ejudgeContestId) throws EjudgeSessionException, ContestManagerException {
        EjudgeSession ejudgeSession = new EjudgeSession();
        Path contestDirectory = contestsDir.resolve(String.format("%06d", ejudgeContestId));
        log.info(String.format("Submitting all runs from %s", contestDirectory));

        EjudgeConfigParser parser = new EjudgeConfigParser();
        try {
            parser.parse(contestDirectory.resolve("conf").resolve("serve.cfg"));
        } catch (IOException e) {
            throw new ContestManagerException("failed to parse serve.cfg", e);
        }

        Path problemsDirectory = contestDirectory.resolve("problems");
        try (Stream<Path> stream = Files.list(problemsDirectory)) {
            List<Path> problemPaths = stream.toList();
            for (Path problemPath : problemPaths) {
                String internalName = problemPath.getFileName().toString();
                int problemId = parser.getProblemIdByInternalName(internalName);
                submitProblem(ejudgeContestId, problemId);
            }
        } catch (IOException e) {
            throw new EjudgeSessionException("failed to iterate the problems", e);
        }
    }
}
