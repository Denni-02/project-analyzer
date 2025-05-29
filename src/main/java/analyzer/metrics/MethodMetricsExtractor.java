package analyzer.metrics;

import analyzer.git.GitRepository;
import analyzer.model.MethodInfo;
import analyzer.csv.CsvHandler;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import analyzer.model.Release;
import analyzer.util.Configuration;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import net.sourceforge.pmd.*;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.LanguageVersion;
import net.sourceforge.pmd.reporting.Report;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

public class MethodMetricsExtractor {

    private final JavaParser parser = new JavaParser();
    private final List<MethodInfo> methodInfos = new ArrayList<>();
    private String currentRelease;
    private LocalDate currentReleaseDate;
    private final StaticMetricCalculator staticCalc = new StaticMetricCalculator();
    private final HistoricalMetricExtractor historicalExtractor;


    public MethodMetricsExtractor(GitRepository gitRepository) {
        this.historicalExtractor = new HistoricalMetricExtractor(gitRepository);
    }

    public List<MethodInfo> getAnalyzedMethods() {
        return methodInfos;
    }

    public void setCurrentRelease(String releaseId) {
        this.currentRelease = releaseId;
    }

    public void setCurrentReleaseDate(LocalDate currentReleaseDate) {
        this.currentReleaseDate = currentReleaseDate;
    }

    // Esplora i file nella cartella filtrando quelli .java e chiamando analyzeFile()
    public void analyzeProject(String projectPath, Release currentRelease) {

        int fileCount = 0;

        try {
            List<Path> javaFiles = Files.walk(Paths.get(projectPath))
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .filter(path -> !path.toString().contains("/test/"))
                    .filter(path -> !path.toString().contains("/generated/"))
                    .filter(path -> !path.toString().contains("/build/"))
                    .toList();

            for (Path path : javaFiles) {
                analyzeFile(path);
                fileCount++;
            }

            if(Configuration.BASIC_DEBUG && Configuration.logger.isLoggable(Level.INFO)){
                Configuration.logger.info(String.format("File .java analizzati: %d", fileCount));
                Configuration.logger.info(String.format("Chiamo analisi storica su %d metodi.", methodInfos.size()));
            }

            historicalExtractor.analyzeHistoryForMethods(methodInfos, currentRelease);


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Cerca metodi nel file
    private void analyzeFile(Path path) {

        try {
            CompilationUnit cu = parser.parse(path).getResult().orElse(null);
            if (cu == null) return;

            List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
            if (methods.isEmpty()) return;

            // PMD config
            LanguageVersion javaVersion = LanguageRegistry.PMD.getLanguageVersionById("java", "1.6");

            Files.readString(path, StandardCharsets.UTF_8);

            PMDConfiguration config = new PMDConfiguration();
            config.setDefaultLanguageVersion(javaVersion);
            config.addRuleSet("category/java/design.xml");
            config.addRuleSet("category/java/bestpractices.xml");
            config.addInputPath(path);

            // Analizza il file intero con PMD
            try (PmdAnalysis pmd = PmdAnalysis.create(config)) {

                Report report = pmd.performAnalysisAndCollectReport();

                // Per ogni metodo, filtra gli smell che ci cadono dentro
                for (MethodDeclaration method : methods) {
                    MethodInfo info = analyzeMethod(method, path);

                    if (info == null) continue;

                    int start = method.getBegin().map(p -> p.line).orElse(-1);
                    int end = method.getEnd().map(p -> p.line).orElse(-1);

                    info.setStartLine(start);
                    info.setEndLine(end);


                    //AGGIUNTA PER DEBUG -------------------------------------------------------------------------------
                    info.setMethodCode(method.toString());

                    List<String> smellNames = report.getViolations().stream()
                            .filter(v -> v.getBeginLine() >= start && v.getBeginLine() <= end)
                            .map(v -> v.getRule().getName()) // oppure getRule().toString()
                            .distinct()
                            .toList();

                    info.setDetectedSmells(smellNames);
                    info.setNumberOfSmells(smellNames.size()); //4. Number of Code Smells

                    methodInfos.add(info);

                    if (Configuration.BASIC_DEBUG && methodInfos.size() % 1000 == 0) {
                        String debugPath = Configuration.DEBUG_SAMPLED_METHODS_PATH1;
                        MethodInfo sampled = methodInfos.get(methodInfos.size() - 1);
                        logDebugSample(methodInfos.size(), sampled, debugPath);
                    }

                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // Analizza un singolo metodo e ne calcola tutte le metriche
    private MethodInfo analyzeMethod(MethodDeclaration method, Path path) {

        try {

            MethodInfo info = new MethodInfo();

            info.setProjectName(Configuration.PROJECT1_COLUMN);
            info.setMethodName(path.toString() + "/" + method.getNameAsString());
            info.setReleaseId(currentRelease);
            info.setReleaseDate(currentReleaseDate);


            // Metriche statiche principali:

            info.setLoc(staticCalc.calculateLoc(method)); // 1. LOC
            info.setCyclomaticComplexity(staticCalc.calculateCyclomaticComplexity(method)); // 2. Cyclomatic Complexity
            info.setCognitiveComplexity(staticCalc.calculateCognitiveComplexity(method)); // 3. Cognitive Complexity
            info.setParameterCount(staticCalc.calculateParameterCount(method)); // 5. Parameter Count
            info.setNestingDepth(staticCalc.calculateNestingDepth(method)); // 6. Nesting Depth
            info.setStatementCount(staticCalc.calculateStatementCount(method));
            info.setReturnTypeComplexity(staticCalc.calculateReturnTypeComplexity(method));
            info.setLocalVariableCount(staticCalc.calculateLocalVariableCount(method));

            // Target
            info.setBugginess(false);

            return info;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Esporta il contenuto analizzato nel file CSV
    public void exportResults(String outputPath) {
        CsvHandler csvHandler = new CsvHandler();
        csvHandler.writeCsv(outputPath, methodInfos);
    }

    private void logDebugSample(int index, MethodInfo sampled, String debugPath) {
        try (FileWriter fw = new FileWriter(debugPath, true)) {
            fw.write("========== METHOD #" + index + " ==========\n");
            fw.write("Method: " + sampled.getMethodName() + "\n");
            fw.write("Release: " + sampled.getReleaseId() + "\n\n");
            fw.write("Code:\n" + sampled.getMethodCode() + "\n\n");

            fw.write("METRICS:\n");
            fw.write("LOC: " + sampled.getLoc() + "\n");
            fw.write("Cyclomatic Complexity: " + sampled.getCyclomaticComplexity() + "\n");
            fw.write("Cognitive Complexity: " + sampled.getCognitiveComplexity() + "\n");
            fw.write("Parameter Count: " + sampled.getParameterCount() + "\n");
            fw.write("Nesting Depth: " + sampled.getNestingDepth() + "\n");
            fw.write("Smells: " + sampled.getNumberOfSmells() + "\n");

            fw.write("Smell types:\n");
            for (String s : sampled.getDetectedSmells()) {
                fw.write("  - " + s + "\n");
            }

            fw.write("\n\n");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
