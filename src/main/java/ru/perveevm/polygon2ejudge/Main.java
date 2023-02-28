package ru.perveevm.polygon2ejudge;

public class Main {
    public static void main(String[] args) throws Exception {
        int polygonId = Integer.parseInt(args[0]);
        int ejudgeId = Integer.parseInt(args[1]);

        ContestManager manager = new ContestManager();
        manager.importContest(polygonId, ejudgeId);
    }
}
