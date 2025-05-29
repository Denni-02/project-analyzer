package analyzer.bugginess;

import analyzer.csv.CsvBugLabelerDebug;
import analyzer.git.GitRepository;
import analyzer.model.MethodInfo;
import analyzer.model.TicketInfo;
import analyzer.model.Release;
import analyzer.util.Configuration;
import org.eclipse.jgit.revwalk.RevCommit;
import java.util.*;

public class BugLabeler {

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

            // Validazione minima: ci serve almeno una FV e un commit
            if (ticket.getFixVersion() == null || ticket.getCommitIds().isEmpty()) continue;

            Set<String> buggyReleases;

            // 6. Se ha AV → usa AV; altrimenti → stima IV
            if (!ticket.getAffectedVersions().isEmpty()) {
                buggyReleases = new HashSet<>(ticket.getAffectedVersions());
            } else {
                buggyReleases = new HashSet<>();

                if (Configuration.LABELING_DEBUG) {
                    Configuration.logger.info("Ticket " + ticket.getId() + " NON ha AV → provo stima IV");
                    Configuration.logger.info("   → FV: " + ticket.getFixVersionName() + ", OV: " + ticket.getOpeningVersion());
                }

                String estIV = estimator.estimateIV(ticket);
                String fv = estimator.normalizeVersionName(ticket.getFixVersionName());

                if (Configuration.LABELING_DEBUG) {
                    if (estIV == null) {
                        Configuration.logger.info(String.format("Ticket " + ticket.getId() + ": stima IV fallita."));
                    } else {
                        Configuration.logger.info("Ticket " + ticket.getId() + ": stima IV riuscita → " + estIV);
                    }
                }

                if (estIV != null && fv != null) {
                    ReleaseIndexMapper mapper = estimator.getMapper();
                    int ivIndex = mapper.getIndex(estIV);
                    int fvIndex = mapper.getIndex(fv);

                    // Genera release buggy: da IV (inclusa) a FV (esclusa)
                    for (int i = ivIndex; i < fvIndex; i++) {
                        String rel = mapper.getReleaseName(i);
                        if (rel != null) buggyReleases.add(rel);
                    }
                }
            }

            if (Configuration.LABELING_DEBUG)
                Configuration.logger.info("Ticket " + ticket.getId() + ": buggyReleases stimate → " + buggyReleases);

            if (buggyReleases.isEmpty()) continue;


            // Filtra solo le release per cui abbiamo metodi metricati
            Set<String> availableReleases = new HashSet<>();
            for (MethodInfo m : methods) {
                availableReleases.add(m.getReleaseId());
            }
            buggyReleases.retainAll(availableReleases);

            if (buggyReleases.isEmpty()) {
                if (Configuration.LABELING_DEBUG)
                    System.out.printf("Ticket %s: tutte le buggyReleases fuori dal dataset\n", ticket.getId());
                continue;
            }

            // 7. Per ogni commit collegato al ticket
            for (String commitHash : ticket.getCommitIds()) {
                RevCommit commit;
                try {
                    commit = repo.getGit().log()
                            .add(repo.getGit().getRepository().resolve(commitHash))
                            .call().iterator().next();
                } catch (Exception e) {
                    System.err.println("Errore leggendo commit " + commitHash);
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
                            Configuration.logger.info("⚠️ Commit " + commit.getName() +
                                    " su file " + filePath +
                                    " @ " + releaseId + " NON tocca metodi metricati.");
                            Configuration.logger.info("   → File toccato, metodi candidati: " + candidates.size());
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

            if (Configuration.LABELING_DEBUG) {
                Configuration.logger.info("\nSTATISTICHE FINE ETICHETTATURA:");
                Configuration.logger.info("→ Metodi etichettati buggy grazie ad AV: " + buggyFromAV);
                Configuration.logger.info("→ Metodi etichettati buggy grazie a Proportion: " + buggyFromProportion);
                Configuration.logger.info("→ Totale etichettati buggy: " + (buggyFromAV + buggyFromProportion));
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
            if (relative.contains("bookkeeper/")) {
                return relative.substring(relative.indexOf("bookkeeper/") + "bookkeeper/".length());
            }
            return relative;
        }
        return methodName;
    }
}
