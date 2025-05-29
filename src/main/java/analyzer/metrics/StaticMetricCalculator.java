package analyzer.metrics;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.Type;

public class StaticMetricCalculator {

    public int calculateLoc(MethodDeclaration method) {
        String[] lines = method.toString().split("\\r?\\n");
        int loc = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.equals("{") && !trimmed.equals("}") && !trimmed.startsWith("//")) {
                loc++;
            }
        }
        return loc;
    }

    public int calculateCyclomaticComplexity(MethodDeclaration method) {
        int complexity = 1;
        complexity += method.findAll(IfStmt.class).size();
        complexity += method.findAll(ForStmt.class).size();
        complexity += method.findAll(ForEachStmt.class).size();
        complexity += method.findAll(WhileStmt.class).size();
        complexity += method.findAll(DoStmt.class).size();
        complexity += method.findAll(SwitchEntry.class).size();
        complexity += method.findAll(CatchClause.class).size();
        complexity += method.findAll(ConditionalExpr.class).size();
        return complexity;
    }

    public int calculateCognitiveComplexity(MethodDeclaration method) {
        int complexity = 0;
        complexity += method.findAll(IfStmt.class).size();
        complexity += method.findAll(ForStmt.class).size();
        complexity += method.findAll(ForEachStmt.class).size();
        complexity += method.findAll(WhileStmt.class).size();
        complexity += method.findAll(DoStmt.class).size();
        complexity += method.findAll(SwitchStmt.class).size();
        complexity += method.findAll(CatchClause.class).size();
        complexity += method.findAll(ConditionalExpr.class).size();
        int nestingPenalty = Math.max(0, calculateNestingDepth(method) - 1);
        return complexity + nestingPenalty;
    }

    public int calculateParameterCount(MethodDeclaration method) {
        return method.getParameters().size();
    }

    public int calculateNestingDepth(MethodDeclaration method) {
        return calculateNestingDepthRecursive(method, 0);
    }

    private int calculateNestingDepthRecursive(Node node, int currentDepth) {
        int maxDepth = currentDepth;

        if (node instanceof IfStmt ||
                node instanceof ForStmt ||
                node instanceof ForEachStmt ||
                node instanceof WhileStmt ||
                node instanceof DoStmt ||
                node instanceof SwitchStmt ||
                node instanceof TryStmt ||
                node instanceof CatchClause) {
            currentDepth++;
        }

        for (Node child : node.getChildNodes()) {
            int childDepth = calculateNestingDepthRecursive(child, currentDepth);
            if (childDepth > maxDepth) {
                maxDepth = childDepth;
            }
        }

        return maxDepth;
    }

    public int calculateStatementCount(MethodDeclaration method) {
        return method.getBody().map(b -> b.getStatements().size()).orElse(0);
    }


    public int calculateReturnTypeComplexity(MethodDeclaration method) {
        Type returnType = method.getType();
        return computeTypeComplexity(returnType);
    }

    private int computeTypeComplexity(Type type) {
        if (type.isPrimitiveType()) return 1;
        if (type.isArrayType()) return 1 + computeTypeComplexity(type.asArrayType().getComponentType());

        if (type.isClassOrInterfaceType()) {
            ClassOrInterfaceType cit = type.asClassOrInterfaceType();
            int complexity = 1; // tipo base

            if (cit.getTypeArguments().isPresent()) {
                for (Type arg : cit.getTypeArguments().get()) {
                    complexity += computeTypeComplexity(arg); // ricorsione su ciascun parametro
                }
            }

            return complexity;
        }

        // fallback per altri tipi
        return 1;
    }


    public int calculateLocalVariableCount(MethodDeclaration method) {
        return method.findAll(VariableDeclarator.class).stream()
                .filter(v -> v.getParentNode().isPresent() &&
                        !(v.getParentNode().get() instanceof com.github.javaparser.ast.body.Parameter))
                .toList()
                .size();
    }
}
