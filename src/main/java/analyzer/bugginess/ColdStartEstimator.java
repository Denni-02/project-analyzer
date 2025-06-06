package analyzer.bugginess;

import analyzer.jira.TicketParser;
import analyzer.model.TicketInfo;
import analyzer.model.Release;
import util.Configuration;

import java.time.LocalDate;
import java.util.*;
import java.util.logging.Level;

public class ColdStartEstimator {

    private ColdStartEstimator(){
        // Prevent instantation
    }

    private static final String[] PROJECTS = {"AVRO", "OPENJPA", "ZOOKEEPER", "SYNCOPE", "TAJO"};

    public static double computeColdStartP() {
        List<Double> proportions = new ArrayList<>();
        for (String project : PROJECTS) {
            proportions.addAll(extractProportionsForProject(project));
        }

        if (proportions.isEmpty()) return 1.0;

        double sum = proportions.stream().mapToDouble(Double::doubleValue).sum();
        return sum / proportions.size();
    }


    private static Double calculateProportion(TicketInfo t, ReleaseIndexMapper mapper, List<Release> releases) {
        if (t.getAffectedVersions().isEmpty()) return null;

        String av = t.getAffectedVersions().get(0);
        String fv = t.getFixVersionName();
        int avIdx = mapper.getIndex(av);
        int fvIdx = mapper.getIndex(fv);
        int ovIdx = findClosestReleaseBefore(t.getOpeningVersion(), releases);

        if (avIdx == -1 || fvIdx == -1 || ovIdx == -1) return null;

        return (fvIdx == ovIdx)
                ? (double) (fvIdx - avIdx)
                : (double) (fvIdx - avIdx) / (fvIdx - ovIdx);
    }

    private static List<Double> extractProportionsForProject(String project) {
        List<Double> result = new ArrayList<>();
        try {
            Map<String, TicketInfo> tickets = TicketParser.parseTicketsFromProject(project);
            List<Release> releases = TicketParser.getReleasesFromProject(project);
            ReleaseIndexMapper mapper = new ReleaseIndexMapper(releases);

            for (TicketInfo t : tickets.values()) {
                Double p = calculateProportion(t, mapper, releases);
                if (p != null && p > 0 && p <= 1.5) {
                    result.add(p);
                }
            }
        } catch (Exception e) {
            Configuration.logger.log(Level.SEVERE, String.format("Errore analizzando il progetto %s", project), e);
        }
        return result;
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
