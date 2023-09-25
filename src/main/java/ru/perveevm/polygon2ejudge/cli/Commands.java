package ru.perveevm.polygon2ejudge.cli;

import picocli.CommandLine;
import ru.perveevm.polygon2ejudge.ContestManager;
import ru.perveevm.polygon2ejudge.exceptions.ContestManagerException;
import ru.perveevm.polygon2ejudge.exceptions.EjudgeSessionException;

import java.util.concurrent.Callable;

/**
 * @author Mike Perveev (perveev_m@mail.ru)
 */
@CommandLine.Command(name = "polygon2ejudge")
public class Commands implements Callable<Integer> {
    @CommandLine.Command(name = "ic", description = "Import contest from Polygon to ejudge")
    public Integer importContest(
            @CommandLine.Parameters(index = "0", description = "Polygon contest ID") final int polygonContestId,
            @CommandLine.Parameters(index = "1", description = "Ejudge contest ID") final int ejudgeContestId,
            @CommandLine.Option(names = {"-h", "--help"}, usageHelp = true, description = "Display this help message")
            boolean usageHelpRequested) {
        try {
            ContestManager manager = new ContestManager();
            manager.importContest(polygonContestId, ejudgeContestId);
            return 0;
        } catch (ContestManagerException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    @CommandLine.Command(name = "rp", description = "Remove one problem from ejudge contest")
    public Integer removeProblem(
            @CommandLine.Parameters(index = "0", description = "Ejudge contest ID") final int ejudgeContestId,
            @CommandLine.Parameters(index = "1", description = "Problem ID in contest") final int problemId,
            @CommandLine.Option(names = {"-h", "--help"}, usageHelp = true, description = "Display this help message")
            boolean usageHelpRequested) {
        try {
            ContestManager manager = new ContestManager();
            manager.removeProblem(ejudgeContestId, problemId);
            return 0;
        } catch (ContestManagerException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    @CommandLine.Command(name = "rc", description = "Remove all problems from ejudge contest")
    public Integer removeContest(
            @CommandLine.Parameters(index = "0", description = "Ejudge contest ID") final int ejudgeContestId,
            @CommandLine.Option(names = {"-h", "--help"}, usageHelp = true, description = "Display this help message")
            boolean usageHelpRequested) {
        try {
            ContestManager manager = new ContestManager();
            manager.removeContest(ejudgeContestId);
            return 0;
        } catch (ContestManagerException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    @CommandLine.Command(name = "sp",
            description = "Submit all problem solutions to ejudge. Only C++, Python, Java ans Pascal are supported")
    public Integer submitProblem(
            @CommandLine.Parameters(index = "0", description = "Ejudge contest ID") final int ejudgeContestId,
            @CommandLine.Parameters(index = "1", description = "Problem ID") final int problemId,
            @CommandLine.Option(names = {"-h", "--help"}, usageHelp = true, description = "Display this help message")
            boolean usageHelpRequested) {
        try {
            ContestManager manager = new ContestManager();
            manager.submitProblem(ejudgeContestId, problemId);
            return 0;
        } catch (ContestManagerException | EjudgeSessionException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    @CommandLine.Command(name = "sc",
            description = "Submit all contest solutions to ejudge. Only C++, Python, Java and Pascal are supported")
    public Integer submitContest(
            @CommandLine.Parameters(index = "0", description = "Ejudge contest ID") final int ejudgeContestId,
            @CommandLine.Option(names = {"-h", "--help"}, usageHelp = true, description = "Display this help message")
            boolean usageHelpRequested) {
        try {
            ContestManager manager = new ContestManager();
            manager.submitContest(ejudgeContestId);
            return 0;
        } catch (ContestManagerException | EjudgeSessionException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    @CommandLine.Option(names = {"-h", "--help"}, usageHelp = true, description = "Display this help message")
    boolean usageHelpRequested;

    @Override
    public Integer call() {
        System.out.println("Subcommand expected, please, read help");
        return 0;
    }
}
