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
import java.util.logging.Level;
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

        for (Map.Entry<String, List<MethodInfo>> entry : methodsByFile.entrySet()) {

            String filePath = entry.getKey();
            List<MethodInfo> methodList = entry.getValue();


            for (MethodInfo m : methodList) {
                String key = buildMethodKey(m);
                methodByKey.put(key, m);
            }

            try {
                Iterable<RevCommit> commits = repo.getCommitsTouchingFileBefore(filePath, release.getReleaseDate());

                for (RevCommit commit : commits) {
                    if (commit.getParentCount() == 0) {
                        continue;
                    }
                    RevCommit parent = repo.parseCommit(commit);
                    analyzeDiffBetweenCommits(filePath, parent, commit, methodList, statsMap);
                }

            } catch (Exception e) {
                Configuration.logger.log(Level.SEVERE,
                        String.format("Errore analizzando la storia per il file: %s", filePath), e);
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

            for (DiffEntry diff : diffs) {
                if (!diff.getNewPath().equals(filePath)) continue;

                List<Edit> edits = df.toFileHeader(diff).toEditList();

                for (MethodInfo method : methods) {
                    calculateStatsForEdit(method, edits, current, statsMap);
                }

            }

        } catch (Exception e) {
            Configuration.logger.log(Level.SEVERE,
                    String.format("Errore nel diff tra commit %s e %s", parent.getName(), current.getName()), e);
        }
    }

    private void calculateStatsForEdit(MethodInfo method, List<Edit> edits, RevCommit current, Map<String, MethodHistoryStats> statsMap) {
        int start = method.getStartLine();
        int end = method.getEndLine();
        int added = 0;
        int deleted = 0;
        boolean touched = false;

        for (Edit edit : edits) {
            int editStart = edit.getBeginB();
            int editEnd = edit.getEndB();

            if (editEnd > start && editStart < end) {
                touched = true;

                int overlapStart = Math.max(editStart, start);
                int overlapEnd = Math.min(editEnd, end);
                added += Math.max(0, overlapEnd - overlapStart);

                int editStartA = edit.getBeginA();
                int editEndA = edit.getEndA();
                int overlapStartA = Math.max(editStartA, start);
                int overlapEndA = Math.min(editEndA, end);
                deleted += Math.max(0, overlapEndA - overlapStartA);
            }
        }

        if (touched) {
            String key = buildMethodKey(method);
            MethodHistoryStats stats = statsMap.computeIfAbsent(key, k -> new MethodHistoryStats());
            stats.addEdit(added, deleted, current.getAuthorIdent().getName());
        }
    }



}
