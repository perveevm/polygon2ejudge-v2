package ru.perveevm.polygon2ejudge;

import picocli.CommandLine;
import ru.perveevm.polygon2ejudge.cli.Commands;

/**
 * @author Mike Perveev (perveev_m@mail.ru)
 */
public class Main {
    public static void main(String[] args) {
        int rc = new CommandLine(new Commands()).execute(args);
        System.exit(rc);
    }
}
