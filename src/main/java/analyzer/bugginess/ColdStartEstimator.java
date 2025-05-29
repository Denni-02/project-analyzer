package analyzer.bugginess;

import analyzer.jira.TicketParser;
import analyzer.model.TicketInfo;
import analyzer.model.Release;
import java.time.LocalDate;
import java.util.*;

public class ColdStartEstimator {

    private static final String[] PROJECTS = {"AVRO", "OPENJPA", "ZOOKEEPER", "SYNCOPE", "TAJO"};

    public static double computeColdStartP() {

        List<Double> proportions = new ArrayList<>();

        for (String project : PROJECTS) {

            try {
                Map<String, TicketInfo> tickets = TicketParser.parseTicketsFromProject(project);
                List<Release> releases = TicketParser.getReleasesFromProject(project);

                ReleaseIndexMapper mapper = new ReleaseIndexMapper(releases);

                for (TicketInfo t : tickets.values()) {
                    if (!t.getAffectedVersions().isEmpty()) {
                        String av = t.getAffectedVersions().get(0);
                        String fv = t.getFixVersionName();

                        int avIdx = mapper.getIndex(av);
                        int fvIdx = mapper.getIndex(fv);
                        int ovIdx = findClosestReleaseBefore(t.getOpeningVersion(), releases);

                        if (avIdx == -1 || fvIdx == -1 || ovIdx == -1) continue;

                        double p = (fvIdx == ovIdx) ? (fvIdx - avIdx)
                                : (double) (fvIdx - avIdx) / (fvIdx - ovIdx);
                        if (p > 0 && p <= 1.5) proportions.add(p);
                    }
                }
            } catch (Exception e) {
                System.err.println("Errore analizzando il progetto " + project);
                e.printStackTrace();
            }
        }

        if (proportions.isEmpty()) return 1.0;

        double sum = proportions.stream().mapToDouble(Double::doubleValue).sum();
        return sum / proportions.size();
    }

    private static int findClosestReleaseBefore(LocalDate targetDate, List<Release> releases) {
        int bestIdx = -1;
        for (int i = 0; i < releases.size(); i++) {
            if (!releases.get(i).getReleaseDate().isAfter(targetDate)) {
                bestIdx = i;
            }
        }
        return bestIdx;
    }
}
