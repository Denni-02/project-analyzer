package analyzer.git;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import analyzer.exception.GitOperationException;
import analyzer.model.TicketInfo;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.CommitTimeRevFilter;
import analyzer.util.Configuration;
import org.eclipse.jgit.revwalk.filter.MessageRevFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;

public final class GitRepository {
    private final Repository repo;
    private final Git git;

    public GitRepository(String localPath) throws IOException {
        File localPathDir = new File(localPath);
        git = Git.open(localPathDir); // apre un repository git già clonato in locale
        repo = git.getRepository(); // usa JGit per collegarsi a quel repository
    }

    public Git getGit() {
        return this.git;
    }

    public List<RevCommit> getCommitsBetweenDates(LocalDate from, LocalDate to) throws GitOperationException {
        try {
            Iterable<RevCommit> allCommits = this.getGit().log().call();
            List<RevCommit> filtered = new ArrayList<>();
            for (RevCommit commit : allCommits) {
                LocalDate date = commit.getAuthorIdent().getWhen().toInstant()
                        .atZone(ZoneId.systemDefault()).toLocalDate();
                if (!date.isBefore(from) && !date.isAfter(to)) {
                    filtered.add(commit);
                }
            }
            return filtered;
        } catch (Exception e) {
            throw new GitOperationException("Errore durante il filtraggio dei commit per intervallo di date.", e);
        }

    }

    public boolean isAuthorInTicket(RevCommit commit, TicketInfo ticket) {
        String author = commit.getAuthorIdent().getName();
        for (String commitId : ticket.getCommitIds()) {
            try {
                RevCommit linked = this.getGit().log()
                        .add(this.getGit().getRepository().resolve(commitId))
                        .call().iterator().next();
                if (linked.getAuthorIdent().getName().equals(author)) {
                    return true;
                }
            } catch (Exception e) {
                // Ignora errori singoli
            }
        }
        return false;
    }

    public Iterable<RevCommit> getCommitsByMessageContaining(String keyword) throws GitOperationException {
        try {
            return git.log()
                    .setRevFilter(MessageRevFilter.create(keyword))
                    .call();
        } catch (Exception e) {
            throw new GitOperationException("Errore durante il recupero dei commit con messaggi contenenti '" + keyword + "'", e);
        }
    }


    // Trova l'ultimo commit prima della data di una release
    public RevCommit findLastCommitBefore(LocalDate releaseDate) throws IOException {

        Date targetDate = java.sql.Date.valueOf(releaseDate);

        try (RevWalk walk = new RevWalk(repo)) {
            // Forza l'inizio da origin/master per evitare problemi di branch locali
            Ref master = repo.findRef("refs/remotes/origin/master");
            if (master == null) {
                throw new IOException("Impossibile trovare origin/master");
            }
            walk.markStart(walk.parseCommit(master.getObjectId()));
            walk.sort(RevSort.COMMIT_TIME_DESC); // Dal più recente al più vecchio

            for (RevCommit commit : walk) {
                Date commitDate = commit.getAuthorIdent().getWhen();
                if (commitDate.before(targetDate)) {
                    return commit; // Trovato il commit valido
                }
            }
        }

        return null; // Nessun commit trovato prima della release
    }

    // Esegue il checkout al commit indicato
    public void checkoutCommit(RevCommit commit) throws GitAPIException {
        if (Configuration.BASIC_DEBUG) Configuration.logger.info("Eseguo checkout al commit: " + commit.getName());
        git.checkout().setName(commit.getName()).call();
    }

    // Chiude la connessione con il repository
    public void close() {
        git.close();
    }

    public Iterable<RevCommit> getCommitsTouchingFileBefore(String filePath, LocalDate releaseDate) throws GitOperationException {
        try {
            return git.log()
                    .addPath(filePath)
                    .setRevFilter(CommitTimeRevFilter.before(java.sql.Date.valueOf(releaseDate)))
                    .call();
        } catch (Exception e) {
            throw new GitOperationException("Errore nel recupero dei commit che toccano il file prima della release: " + filePath, e);
        }
    }


    public RevCommit parseCommit(RevCommit commit) throws IOException {
        return repo.parseCommit(commit.getParent(0));
    }

    public Set<String> getTouchedJavaFiles(RevCommit commit) throws GitOperationException, IOException {
        Set<String> javaFiles = new HashSet<>();
        if (commit.getParentCount() == 0) return javaFiles; // Salta root commit

        RevCommit parent = parseCommit(commit);

        try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            df.setRepository(repo);
            df.setDetectRenames(true);
            List<DiffEntry> diffs = df.scan(parent.getTree(), commit.getTree());

            for (DiffEntry diff : diffs) {
                String path = diff.getNewPath();
                if (path.endsWith(".java") && !path.contains("/test/") && !path.contains("/target/")) {
                    javaFiles.add(path);
                }
            }

        } catch (Exception e) {
            throw new GitOperationException("Errore nel calcolo dei file .java toccati dal commit " + commit.getName(), e);
        }

        return javaFiles;
    }


}

