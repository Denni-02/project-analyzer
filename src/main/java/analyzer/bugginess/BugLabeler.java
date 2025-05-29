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
        for (TicketInfo t : tickets.values()) {
            if (!t.getAffectedVersions().isEmpty()) {
                estimator.registerValidTicket(t); // serve per stima incrementale
            }
        }

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


        // 8. CSV di debug opzionale
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
            if (Configuration.LABELING_DEBUG && Configuration.logger.isLoggable(Level.INFO)) {
                Configuration.logger.info(TICKET_PREFIX + ticket.getId() + " NON ha AV → provo stima IV");
                Configuration.logger.info("   → FV: " + ticket.getFixVersionName() + ", OV: " + ticket.getOpeningVersion());
            }

            String estIV = estimator.estimateIV(ticket);
            String fv = estimator.normalizeVersionName(ticket.getFixVersionName());

            if (Configuration.LABELING_DEBUG && Configuration.logger.isLoggable(Level.INFO)) {
                if (estIV == null) {
                    Configuration.logger.info(String.format("Ticket %s: stima IV fallita.", ticket.getId()));
                } else {
                    Configuration.logger.info(String.format("Ticket %s: stima IV riuscita -> %s", ticket.getId(), estIV));
                }
            }

            if (estIV != null && fv != null) {
                ReleaseIndexMapper mapper = estimator.getMapper();
                int ivIndex = mapper.getIndex(estIV);
                int fvIndex = mapper.getIndex(fv);

                for (int i = ivIndex; i < fvIndex; i++) {
                    String rel = mapper.getReleaseName(i);
                    if (rel != null) buggyReleases.add(rel);
                }
            }
        }

        return buggyReleases;
    }

    private static boolean filterValidBuggyReleases(TicketInfo ticket, Set<String> buggyReleases, List<MethodInfo> methods) {
        Set<String> availableReleases = new HashSet<>();
        for (MethodInfo m : methods) {
            availableReleases.add(m.getReleaseId());
        }

        buggyReleases.retainAll(availableReleases);

        if (buggyReleases.isEmpty()) {
            if (Configuration.LABELING_DEBUG) {
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
        int buggyFromAV = 0;
        int buggyFromProportion = 0;

        for (String commitHash : ticket.getCommitIds()) {
            RevCommit commit;
            try {
                commit = repo.getGit().log()
                        .add(repo.getGit().getRepository().resolve(commitHash))
                        .call().iterator().next();
            } catch (Exception e) {
                Configuration.logger.severe(String.format("Errore leggendo commit %s", commitHash));
                continue;
            }

            Set<String> files = new HashSet<>(ticket.getFixedFiles());

            for (String filePath : files) {
                for (String releaseId : buggyReleases) {
                    String key = filePath + "@" + releaseId;
                    if (!methodsByFileAndRelease.containsKey(key)) continue;
                    List<MethodInfo> candidates = methodsByFileAndRelease.get(key);

                    Set<MethodInfo> touched = analyzer.getTouchedMethods(commit, filePath, candidates);

                    if (touched.isEmpty() && Configuration.LABELING_DEBUG) {
                        Configuration.logger.info(String.format("Commit %s su file %s @ %s NON tocca metodi metricati.", commit.getName(), filePath, releaseId));
                        Configuration.logger.info(String.format("   → File toccato, metodi candidati: %d", candidates.size()));
                    }

                    for (MethodInfo m : touched) {
                        if (!m.isBugginess()) {
                            m.setBugginess(true);
                            if (!ticket.getAffectedVersions().isEmpty()) {
                                buggyFromAV++;
                            } else {
                                buggyFromProportion++;
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
        }

        return new int[]{buggyFromAV, buggyFromProportion};
    }



}
