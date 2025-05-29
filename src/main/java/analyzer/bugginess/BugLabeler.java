package analyzer.bugginess;

import analyzer.csv.CsvBugLabelerDebug;
import analyzer.git.GitRepository;
import analyzer.model.MethodInfo;
import analyzer.model.TicketInfo;
import analyzer.model.Release;
import analyzer.util.Configuration;
import org.eclipse.jgit.revwalk.RevCommit;
import java.util.*;
import java.util.logging.Level;

public class BugLabeler {

    private static final String TICKET_PREFIX = "Ticket ";

    private BugLabeler() {
        // Utility class → no instance
    }

    private static Map<String, List<MethodInfo>> groupMethodsByFileAndRelease(List<MethodInfo> methods) {
        Map<String, List<MethodInfo>> map = new HashMap<>();
        for (MethodInfo m : methods) {
            String filePath = extractFileFromMethodName(m.getMethodName());
            String key = filePath + "@" + m.getReleaseId();
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(m);
        }
        return map;
    }

    private static String extractFileFromMethodName(String methodName) {
        int idx = methodName.lastIndexOf(".java");
        if (idx != -1) {
            String relative = methodName.substring(0, idx + 5);
            if (relative.contains(Configuration.PROJECT1_SUBSTRING)) {
                return relative.substring(relative.indexOf(Configuration.PROJECT1_SUBSTRING) + Configuration.PROJECT1_SUBSTRING.length());
            }
            return relative;
        }
        return methodName;
    }

    public static void labelMethods(List<MethodInfo> methods, Map<String, TicketInfo> tickets, GitRepository repo, List<Release> releases) {

        int buggyFromAV = 0;
        int buggyFromProportion = 0;

        // 1. Raggruppa i metodi per file+release
        Map<String, List<MethodInfo>> methodsByFileAndRelease = groupMethodsByFileAndRelease(methods);

        // 2. Inizializza helper
        MethodTouchAnalyzer analyzer = new MethodTouchAnalyzer(repo);

        // 3. Inizializza ProportionEstimator per stimare IV
        ProportionEstimator estimator = new ProportionEstimator(releases);
        registerValidTickets(tickets, estimator);

        // 4. Colleziona metodi etichettati per debug
        List<String[]> debugRows = new ArrayList<>();

        // 5. Per ogni ticket...
        for (TicketInfo ticket : tickets.values()) {

            Set<String> buggyReleases = estimateBuggyReleases(ticket, estimator);

            if (!isProcessable(ticket, buggyReleases, methods)) {
                if (Configuration.LABELING_DEBUG && Configuration.logger.isLoggable(Level.INFO)) {
                    Configuration.logger.info(String.format("%s%s: ticket ignorato (non processabile)", TICKET_PREFIX, ticket.getId()));
                }
                continue;
            }

            if (Configuration.LABELING_DEBUG && Configuration.logger.isLoggable(Level.INFO)) {
                Configuration.logger.info(String.format("%s%s: buggyReleases stimate → %s", TICKET_PREFIX, ticket.getId(), buggyReleases));
            }

            int[] result = processTicketCommits(ticket, repo, buggyReleases, methodsByFileAndRelease, analyzer, debugRows);
            buggyFromAV += result[0];
            buggyFromProportion += result[1];

            if (Configuration.LABELING_DEBUG && Configuration.logger.isLoggable(Level.INFO)) {
                Configuration.logger.info("STATISTICHE FINE ETICHETTATURA:");
                Configuration.logger.info(String.format(
                        "→ Etichettati AV: %d | Proportion: %d | Totale: %d",
                        buggyFromAV, buggyFromProportion, buggyFromAV + buggyFromProportion
                ));
            }
        }

        // 6. CSV di debug opzionale
        writeDebugCsv(debugRows);

    }

    private static void registerValidTickets(Map<String, TicketInfo> tickets, ProportionEstimator estimator) {
        for (TicketInfo t : tickets.values()) {
            if (!t.getAffectedVersions().isEmpty()) {
                estimator.registerValidTicket(t);
            }
        }
    }

    private static void writeDebugCsv(List<String[]> debugRows) {
        if (Configuration.LABELING_DEBUG) {
            CsvBugLabelerDebug.writeCsv(
                    "/home/denni/isw2/project-analyzer/debug_file/debug_buggy_methods.csv",
                    debugRows
            );
        }
    }

    private static boolean isProcessable(TicketInfo ticket, Set<String> buggyReleases, List<MethodInfo> methods) {
        if (ticket.getFixVersion() == null || ticket.getCommitIds().isEmpty()) return false;
        if (buggyReleases.isEmpty()) return false;
        return filterValidBuggyReleases(ticket, buggyReleases, methods);
    }

    private static Set<String> estimateBuggyReleases(TicketInfo ticket, ProportionEstimator estimator) {
        Set<String> buggyReleases = new HashSet<>();

        if (!ticket.getAffectedVersions().isEmpty()) {
            buggyReleases.addAll(ticket.getAffectedVersions());
        } else {
            logNoAV(ticket);

            String estIV = estimator.estimateIV(ticket);
            String fv = estimator.normalizeVersionName(ticket.getFixVersionName());

            logEstimationResult(estIV, ticket);

            buggyReleases.addAll(computeIntervalReleases(estIV, fv, estimator));
        }

        return buggyReleases;
    }

    private static void logNoAV(TicketInfo ticket) {
        if (Configuration.LABELING_DEBUG && Configuration.logger.isLoggable(Level.INFO)) {
            Configuration.logger.info(TICKET_PREFIX + ticket.getId() + " NON ha AV → provo stima IV");
            Configuration.logger.info("   → FV: " + ticket.getFixVersionName() + ", OV: " + ticket.getOpeningVersion());
        }
    }

    private static void logEstimationResult(String estIV, TicketInfo ticket) {
        if (Configuration.LABELING_DEBUG && Configuration.logger.isLoggable(Level.INFO)) {
            if (estIV == null) {
                Configuration.logger.info(String.format("Ticket %s: stima IV fallita.", ticket.getId()));
            } else {
                Configuration.logger.info(String.format("Ticket %s: stima IV riuscita -> %s", ticket.getId(), estIV));
            }
        }
    }

    private static Set<String> computeIntervalReleases(String estIV, String fv, ProportionEstimator estimator) {
        Set<String> releases = new HashSet<>();
        if (estIV == null || fv == null) return releases;

        ReleaseIndexMapper mapper = estimator.getMapper();
        int ivIndex = mapper.getIndex(estIV);
        int fvIndex = mapper.getIndex(fv);

        for (int i = ivIndex; i < fvIndex; i++) {
            String rel = mapper.getReleaseName(i);
            if (rel != null) releases.add(rel);
        }

        return releases;
    }

    private static boolean filterValidBuggyReleases(TicketInfo ticket, Set<String> buggyReleases, List<MethodInfo> methods) {
        Set<String> availableReleases = new HashSet<>();
        for (MethodInfo m : methods) {
            availableReleases.add(m.getReleaseId());
        }

        buggyReleases.retainAll(availableReleases);

        if (buggyReleases.isEmpty()) {
            if (Configuration.LABELING_DEBUG && Configuration.logger.isLoggable(Level.INFO)) {
                Configuration.logger.info(String.format("%s%s: tutte le buggyReleases fuori dal dataset", TICKET_PREFIX, ticket.getId()));
            }
            return false;
        }

        return true;
    }

    private static int[] processTicketCommits(
            TicketInfo ticket,
            GitRepository repo,
            Set<String> buggyReleases,
            Map<String, List<MethodInfo>> methodsByFileAndRelease,
            MethodTouchAnalyzer analyzer,
            List<String[]> debugRows
    ) {
        int[] counters = new int[]{0, 0}; // counters[0] = buggyFromAV, counters[1] = buggyFromProportion

        for (String commitHash : ticket.getCommitIds()) {
            RevCommit commit = resolveCommit(commitHash, repo);
            if (commit == null) continue;

            Set<String> files = new HashSet<>(ticket.getFixedFiles());
            for (String filePath : files) {
                for (String releaseId : buggyReleases) {
                    String key = filePath + "@" + releaseId;
                    if (!methodsByFileAndRelease.containsKey(key)) continue;

                    List<MethodInfo> candidates = methodsByFileAndRelease.get(key);
                    Set<MethodInfo> touched = analyzer.getTouchedMethods(commit, filePath, candidates);

                    processTouchedMethods(touched, ticket, commit, debugRows, counters);
                }
            }
        }

        return counters;
    }

    private static RevCommit resolveCommit(String commitHash, GitRepository repo) {
        try {
            return repo.getGit().log()
                    .add(repo.getGit().getRepository().resolve(commitHash))
                    .call().iterator().next();
        } catch (Exception e) {
            Configuration.logger.severe(String.format("Errore leggendo commit %s", commitHash));
            return null;
        }
    }

    private static void processTouchedMethods(
            Set<MethodInfo> touched,
            TicketInfo ticket,
            RevCommit commit,
            List<String[]> debugRows,
            int[] counters
    ) {
        for (MethodInfo m : touched) {
            if (!m.isBugginess()) {
                m.setBugginess(true);
                if (!ticket.getAffectedVersions().isEmpty()) {
                    counters[0]++;
                } else {
                    counters[1]++;
                }

                if (Configuration.LABELING_DEBUG) {
                    debugRows.add(new String[]{
                            ticket.getId(),
                            commit.getName(),
                            m.getMethodName(),
                            m.getReleaseId()
                    });
                }
            }
        }
    }

}
