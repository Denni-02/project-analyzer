package analyzer.bugginess;

import analyzer.csv.CsvTicketCommitWriter;
import analyzer.exception.TicketLinkageException;
import analyzer.git.GitRepository;
import analyzer.model.TicketInfo;
import analyzer.util.Configuration;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BugLinker {

    private final GitRepository repo;

    public BugLinker(GitRepository repo) {
        this.repo = repo;
    }

    public static void main(String[] args) throws Exception {

        // Disabilita i log di PMD
        ch.qos.logback.classic.Logger pmdLogger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("net.sourceforge.pmd");
        pmdLogger.setLevel(ch.qos.logback.classic.Level.ERROR);

        // Disabilita log DEBUG di JGit
        ch.qos.logback.classic.Logger jgitLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.eclipse.jgit");
        jgitLogger.setLevel(ch.qos.logback.classic.Level.ERROR);

        var repo = new GitRepository(Configuration.PROJECT1_PATH);
        var tickets = analyzer.jira.TicketParser.parseTicketsFromJira();

        var linker = new BugLinker(repo);
        linker.linkCommitsToTickets(tickets);

        CsvTicketCommitWriter.write("/home/denni/isw2/project-analyzer/debug_file/ticket_commits_files.csv", tickets);
    }

    public void linkCommitsToTickets(Map<String, TicketInfo> tickets) throws TicketLinkageException {

        try {
            for (Map.Entry<String, TicketInfo> entry : tickets.entrySet()) {
                String ticketId = entry.getKey();
                TicketInfo ticket = entry.getValue();

                Iterable<RevCommit> commits = repo.getCommitsByMessageContaining(ticketId);

                for (RevCommit commit : commits) {
                    String commitHash = commit.getName();
                    ticket.addCommitId(commitHash);

                    Set<String> javaFiles = repo.getTouchedJavaFiles(commit);
                    for (String file : javaFiles) {
                        ticket.addFixedFile(file);
                    }
                }
            }
        } catch (Exception e) {
            throw new TicketLinkageException("Errore durante il collegamento commit-ticket", e);
        }
    }

    public void applyMissingCommitLinkageHeuristic(Map<String, TicketInfo> tickets) throws TicketLinkageException {

        try {
            for (TicketInfo ticket : tickets.values()) {
                if (ticket.getFixVersion() == null || ticket.getOpeningVersion() == null) continue;

                LocalDate start = ticket.getOpeningVersion();
                LocalDate end = ticket.getFixVersion();

                List<RevCommit> candidateCommits = repo.getCommitsBetweenDates(start, end);

                for (RevCommit commit : candidateCommits) {
                    // Se già collegato via messaggio, salta
                    if (ticket.getCommitIds().contains(commit.getName())) continue;

                    Set<String> touchedFiles = repo.getTouchedJavaFiles(commit);

                    for (String file : touchedFiles) {
                        // se il file è stato toccato da commit già collegati
                        boolean isFileMatch = ticket.getFixedFiles().contains(file);

                        // se autore combacia con commit già collegato
                        boolean isAuthorMatch = repo.isAuthorInTicket(commit, ticket);

                        if (isFileMatch && isAuthorMatch) {
                            ticket.addCommitId(commit.getName());
                            for (String f : touchedFiles) {
                                ticket.addFixedFile(f);
                            }
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new TicketLinkageException("Errore durante il collegamento commit-ticket", e);
        }
    }

}


