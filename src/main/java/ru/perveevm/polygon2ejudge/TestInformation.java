package ru.perveevm.polygon2ejudge;

/**
 * @author Mike Perveev (perveev_m@mail.ru)
 */
public class TestInformation {
    private Double points;
    private String group;
    private boolean useInSamples;

    public TestInformation() {
    }

    public Double getPoints() {
        return points;
    }

    public String getGroup() {
        return group;
    }

    public boolean getUseInStatements() {
        return useInSamples;
    }

    public void setPoints(final Double points) {
        this.points = points;
    }

    public void setGroup(final String group) {
        this.group = group;
    }

    public void setUseInSamples(final boolean useInSamples) {
        this.useInSamples = useInSamples;
    }
}
