package analyzer;

import analyzer.git.GitRepository;
import analyzer.jira.GetReleaseInfo;
import analyzer.model.Commit;
import analyzer.model.MethodInfo;
import analyzer.model.Release;
import analyzer.metrics.MethodMetricsExtractor;
import analyzer.csv.CsvDebugWriter;
import org.eclipse.jgit.revwalk.RevCommit;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import analyzer.util.Configuration;
import org.slf4j.LoggerFactory;
import analyzer.model.TicketInfo;
import analyzer.jira.TicketParser;
import analyzer.bugginess.BugLinker;
import analyzer.bugginess.BugLabeler;
import analyzer.csv.CsvHandler;

public class DatasetApp {

    public static void main(String[] args) {

        if (!Configuration.LOGGER) {
            // Disabilita i log di PMD
            ch.qos.logback.classic.Logger pmdLogger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("net.sourceforge.pmd");
            pmdLogger.setLevel(ch.qos.logback.classic.Level.ERROR);
            // Disabilita log DEBUG di JGit
            ch.qos.logback.classic.Logger jgitLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.eclipse.jgit");
            jgitLogger.setLevel(ch.qos.logback.classic.Level.ERROR);
        }

        try {
            List<MethodInfo> methods;
            List<Release> datasetReleases = GetReleaseInfo.getDatasetReleases();

            Map<String, LocalDate> releaseDatesById = new HashMap<>();
            for (Release r : datasetReleases) {
                releaseDatesById.put(r.getName(), r.getReleaseDate());
            }

            // Inizializza Git + estrattore delle metriche
            GitRepository repo = new GitRepository(Configuration.PROJECT1_PATH);
            MethodMetricsExtractor extractor = new MethodMetricsExtractor(repo);

            //Struttura dati per commit
            List<Commit> selectedCommits = new ArrayList<>();

            if (Configuration.BASIC_DEBUG) System.out.println("\nAnalisi delle metriche statiche avviata:");

            // Itera su ogni release valida
            for (Release rel : datasetReleases) {

                if (Configuration.BASIC_DEBUG)
                    System.out.println("\nAnalizzo release: " + rel.getName() + " (" + rel.getReleaseDate() + ")");

                // Trova il commit più recente prima della data di release
                RevCommit commit = repo.findLastCommitBefore(rel.getReleaseDate());
                if (commit == null) {
                    System.out.println("Nessun commit trovato prima della release " + rel.getName());
                    continue;
                }

                if (Configuration.BASIC_DEBUG) {
                    System.out.println(" Commit selezionato:");
                    System.out.println(" → ID: " + commit.getId().getName());
                    System.out.println(" → Data: " + commit.getAuthorIdent().getWhen());
                    System.out.println(" → Messaggio: " + commit.getShortMessage());
                }

                Commit c = new Commit();
                c.setId(commit.getName());
                c.setAuthor(commit.getAuthorIdent().getName());
                c.setDate(commit.getAuthorIdent().getWhen().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
                c.setMessage(commit.getShortMessage());
                c.setFilesTouched(null);
                selectedCommits.add(c);
                repo.checkoutCommit(commit); // Fai il checkout al commit

                extractor.setCurrentRelease(rel.getName()); // Imposta release corrente
                extractor.setCurrentReleaseDate(rel.getReleaseDate());
                extractor.analyzeProject(Configuration.PROJECT1_PATH, rel);

                CsvDebugWriter.writeCommitCsv("/home/denni/isw2/project-analyzer/debug_file/commits_per_release.csv", selectedCommits);
            }

            // Scrivi i risultati finali nel file CSV
            extractor.exportResults(Configuration.OUTPUT_CSV1_PATH);

            // Chiude correttamente la connessione con la repository Git
            repo.close();
            methods = extractor.getAnalyzedMethods();

            if (Configuration.BASIC_DEBUG) System.out.println("\nInizio fase di etichettatura ...");

            // 1. Ticket da JIRA
            Map<String, TicketInfo> tickets = TicketParser.parseTicketsFromJira();

            // 2. Collega commit → ticket
            BugLinker linker = new BugLinker(repo);
            linker.linkCommitsToTickets(tickets);
            linker.applyMissingCommitLinkageHeuristic(tickets);

            // 3. Etichetta i metodi
            List<Release> allReleases = GetReleaseInfo.getAllReleases();
            BugLabeler.labelMethods(methods, tickets, repo, allReleases);

            // 4. Riscrivi CSV aggiornato
            CsvHandler csvHandler = new CsvHandler();
            csvHandler.writeCsv(Configuration.OUTPUT_CSV1_PATH, methods);

            if (Configuration.BASIC_DEBUG) System.out.println("\nAnalisi completata. File salvato in: " + Configuration.OUTPUT_CSV1_PATH + "\n");

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Errore durante l'esecuzione.");
        }
    }
}
