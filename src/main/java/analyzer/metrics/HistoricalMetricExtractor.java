package analyzer.metrics;

import analyzer.model.MethodHistoryStats;
import analyzer.model.MethodInfo;
import analyzer.model.Release;
import analyzer.git.GitRepository;
import analyzer.util.Configuration;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class HistoricalMetricExtractor {

    private final GitRepository repo;

    public HistoricalMetricExtractor(GitRepository repo) {
        this.repo = repo;
    }

    public void analyzeHistoryForMethods(List<MethodInfo> methods, Release release) {
        Map<String, List<MethodInfo>> methodsByFile = methods.stream()
                .collect(Collectors.groupingBy(m -> extractFilePathFromMethodName(m.getMethodName())));

        Map<String, MethodHistoryStats> statsMap = new HashMap<>();
        Map<String, MethodInfo> methodByKey = new HashMap<>();

        for (String filePath : methodsByFile.keySet()) {

            if (Configuration.HISTORY_DEBUG) Configuration.logger.info("Analizzo storico file: " + filePath);

            List<MethodInfo> methodList = methodsByFile.get(filePath);

            for (MethodInfo m : methodList) {
                String key = buildMethodKey(m);
                methodByKey.put(key, m);
            }

            try {
                Iterable<RevCommit> commits = repo.getCommitsTouchingFileBefore(filePath, release.getReleaseDate());

                for (RevCommit commit : commits) {
                    if (commit.getParentCount() == 0) {
                        if (Configuration.HISTORY_DEBUG) Configuration.logger.info("Skip root commit: " + commit.getName());
                        continue;
                    }
                    RevCommit parent = repo.parseCommit(commit);
                    analyzeDiffBetweenCommits(filePath, parent, commit, methodList, statsMap);
                }

            } catch (Exception e) {
                System.err.println("Errore analizzando la storia per il file: " + filePath);
                e.printStackTrace();
            }
        }

        // Alla fine, applica i valori raccolti ai MethodInfo
        for (Map.Entry<String, MethodHistoryStats> entry : statsMap.entrySet()) {
            String key = entry.getKey();
            MethodInfo method = methodByKey.get(key);
            MethodHistoryStats stats = entry.getValue();

            method.setMethodHistories(stats.getMethodHistories());
            method.setStmtAdded(stats.getStmtAdded());
            method.setStmtDeleted(stats.getStmtDeleted());
            method.setChurn(stats.getChurn());
            method.setDistinctAuthors(stats.getDistinctAuthors());
        }
    }

    private String buildMethodKey(MethodInfo m) {
        return m.getMethodName() + "@" + m.getReleaseId() + "#" + m.getStartLine();
    }

    private String extractFilePathFromMethodName(String fullName) {
        int idx = fullName.lastIndexOf(".java");
        if (idx != -1) {
            String relative = fullName.substring(0, idx + 5);
            // Normalizza rimuovendo il path assoluto
            if (relative.contains(Configuration.PROJECT1_SUBSTRING)) {
                return relative.substring(relative.indexOf(Configuration.PROJECT1_SUBSTRING) + Configuration.PROJECT1_SUBSTRING.length());
            }
            return relative;
        }
        return fullName;
    }

    private void analyzeDiffBetweenCommits(String filePath, RevCommit parent, RevCommit current,
                                           List<MethodInfo> methods,
                                           Map<String, MethodHistoryStats> statsMap) {

        try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            df.setRepository(repo.getGit().getRepository());
            df.setDiffComparator(RawTextComparator.DEFAULT);
            df.setDetectRenames(true);

            List<DiffEntry> diffs = df.scan(parent.getTree(), current.getTree());

            if (Configuration.HISTORY_DEBUG)
                Configuration.logger.info("Analizzo diff tra commit " + parent.getName() + " → " + current.getName());

            for (DiffEntry diff : diffs) {
                if (!diff.getNewPath().equals(filePath)) continue;

                List<Edit> edits = df.toFileHeader(diff).toEditList();

                for (MethodInfo method : methods) {
                    int start = method.getStartLine();
                    int end = method.getEndLine();

                    boolean touched = false;
                    int added = 0, deleted = 0;

                    for (Edit edit : edits) {
                        int editStart = edit.getBeginB();
                        int editEnd = edit.getEndB();

                        if (editEnd > start && editStart < end) {
                            touched = true;

                            int overlapStart = Math.max(editStart, start);
                            int overlapEnd = Math.min(editEnd, end);
                            int addedInMethod = Math.max(0, overlapEnd - overlapStart);
                            added += addedInMethod;

                            int editStartA = edit.getBeginA();
                            int editEndA = edit.getEndA();
                            int overlapStartA = Math.max(editStartA, start);
                            int overlapEndA = Math.min(editEndA, end);
                            int deletedInMethod = Math.max(0, overlapEndA - overlapStartA);
                            deleted += deletedInMethod;
                        }
                    }

                    if (touched) {
                        String key = buildMethodKey(method);
                        MethodHistoryStats stats = statsMap.computeIfAbsent(key, k -> new MethodHistoryStats());
                        stats.addEdit(added, deleted, current.getAuthorIdent().getName());
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Errore nel diff tra commit " + parent.getName() + " e " + current.getName());
            e.printStackTrace();
        }
    }


}
