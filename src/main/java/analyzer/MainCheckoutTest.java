package analyzer;

import analyzer.bugginess.BugLabeler;
import analyzer.bugginess.BugLinker;
import analyzer.git.GitRepository;
import analyzer.jira.GetReleaseInfo;
import analyzer.jira.TicketParser;
import analyzer.metrics.MethodMetricsExtractor;
import analyzer.model.MethodInfo;
import analyzer.model.Release;
import analyzer.model.TicketInfo;
import analyzer.csv.CsvHandler;
import org.slf4j.LoggerFactory;
import util.Configuration;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.revwalk.RevCommit;

import java.time.LocalDate;
import java.util.*;

public class MainCheckoutTest {

    public static void main(String[] args) {

        if (!Configuration.ACTIVATE_LOG) {
            ch.qos.logback.classic.Logger pmdLogger = (ch.qos.logback.classic.Logger)
                    org.slf4j.LoggerFactory.getLogger("net.sourceforge.pmd");
            pmdLogger.setLevel(ch.qos.logback.classic.Level.ERROR);

            ch.qos.logback.classic.Logger jgitLogger = (ch.qos.logback.classic.Logger)
                    LoggerFactory.getLogger("org.eclipse.jgit");
            jgitLogger.setLevel(ch.qos.logback.classic.Level.ERROR);
        }

        final String commitHash = "7f66117b5aa372d6ec7179b2a1edd47ed46efbe3";
        final String targetReleaseName = "0.9.6";

        try {
            GitRepository repo = new GitRepository(Configuration.getProjectPath());

            repo.getGit().reset().setMode(ResetCommand.ResetType.HARD).call();
            repo.getGit().clean().setCleanDirectories(true).setForce(true).call();

            RevCommit commit = repo.getGit().log()
                    .add(repo.getGit().getRepository().resolve(commitHash))
                    .call().iterator().next();
            repo.checkoutCommit(commit);
            Configuration.logger.info("✔️ Checkout completato su commit: " + commitHash);

            List<Release> allReleases = GetReleaseInfo.getAllReleases();
            Optional<Release> targetReleaseOpt = allReleases.stream()
                    .filter(r -> r.getName().equals(targetReleaseName))
                    .findFirst();

            if (targetReleaseOpt.isEmpty()) {
                Configuration.logger.severe("❌ Release non trovata: " + targetReleaseName);
                return;
            }
            Release targetRelease = targetReleaseOpt.get();

            MethodMetricsExtractor extractor = new MethodMetricsExtractor(repo);
            extractor.setCurrentRelease(targetRelease.getName());
            extractor.setCurrentReleaseDate(targetRelease.getReleaseDate());
            extractor.analyzeProject(Configuration.getProjectPath(), targetRelease);

            List<MethodInfo> methods = extractor.getAnalyzedMethods();
            Configuration.logger.info("✔️ Estrazione metodi completata: " + methods.size());

            Map<String, TicketInfo> tickets = TicketParser.parseTicketsFromJira();
            Configuration.logger.info("✔️ Ticket JIRA ottenuti: " + tickets.size());

            BugLinker linker = new BugLinker(repo);
            linker.linkCommitsToTickets(tickets);
            linker.applyMissingCommitLinkageHeuristic(tickets);
            Configuration.logger.info("✔️ Heuristics commit-ticket completata");

            BugLabeler.labelMethods(methods, tickets, repo, allReleases);
            Configuration.logger.info("✔️ Labeling completato");

            String output = Configuration.getOutputCsvPath().replace(".csv", "_0.9.6_test.csv");
            CsvHandler handler = new CsvHandler();
            handler.writeCsv(output, methods);
            Configuration.logger.info("✔️ CSV scritto in: " + output);

            repo.close();

        } catch (Exception e) {
            e.printStackTrace();
            Configuration.logger.severe("❌ ERRORE durante test commit singolo");
        }
    }
}
