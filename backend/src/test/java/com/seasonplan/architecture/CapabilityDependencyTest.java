package com.seasonplan.architecture;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import org.junit.jupiter.api.Test;
import javax.tools.ToolProvider;
import javax.tools.SimpleJavaFileObject;
import java.lang.reflect.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Parses Java syntax: comments, strings and directory placement cannot hide or invent an edge. */
class CapabilityDependencyTest {
    private static final String PREFIX = "com.seasonplan.";
    private static final Path ROOT = Path.of("src/main/java/com/seasonplan");
    private record Source(String owner, String file, Set<String> references, Set<String> declarations) { }

    @Test void allCapabilitiesIncludingPublicApisFormADirectedAcyclicGraph() throws Exception {
        Map<String, Set<String>> graph = new TreeMap<>();
        for (Source source : productionSources()) {
            graph.computeIfAbsent(source.owner(), ignored -> new TreeSet<>());
            for (String reference : source.references()) {
                String target = owner(reference);
                if (target != null && !target.equals(source.owner())) graph.get(source.owner()).add(target);
            }
        }
        assertThat(cycle(graph)).as("capability cycle; full graph: %s", graph).isEmpty();
    }

    @Test void consumersUsePublicContractsAndExplicitImmutableContractValuesNotOwnerImplementations() throws Exception {
        List<Source> sources = productionSources();
        Map<String, String> declarations = new HashMap<>();
        for (Source source : sources) for (String name : source.declarations()) declarations.put(name, source.owner());
        Set<String> values = new HashSet<>();
        for (String name : declarations.keySet()) {
            if (name.contains(".api.")) collectContractValues(Class.forName(name), values, new HashSet<>());
        }
        List<String> violations = new ArrayList<>();
        for (Source source : sources) {
            if (source.owner().equals("bootstrap")) continue;
            for (String reference : source.references()) {
                String target = owner(reference);
                if (target == null || target.equals(source.owner()) || target.equals("shared")) continue;
                if (reference.startsWith(PREFIX + target + ".api")) continue;
                if (reference.endsWith(".*")) {
                    violations.add(source.file() + " -> " + reference);
                    continue;
                }
                declarations.keySet().stream().filter(name -> reference.equals(name) || reference.startsWith(name + "."))
                        .filter(name -> !values.contains(name)).forEach(name -> violations.add(source.file() + " -> " + name));
            }
        }
        assertThat(violations).as("foreign implementation/policy dependencies").isEmpty();
    }

    @Test void parserDetectsApiImportsAndFullyQualifiedReferencesButNotStrings() throws Exception {
        Source source = parse("Probe.java", "package com.seasonplan.alpha.api; import com.seasonplan.beta.api.Contract; class Probe { com.seasonplan.gamma.api.Value value; String text = \"com.seasonplan.fake.api.NotAReference\"; }");
        assertThat(source.references()).anyMatch(ref -> ref.startsWith(PREFIX + "beta.api.Contract"))
                .anyMatch(ref -> ref.startsWith(PREFIX + "gamma.api.Value"))
                .noneMatch(ref -> ref.contains("fake"));
        assertThat(cycle(Map.of("alpha", Set.of("beta"), "beta", Set.of("alpha"))))
                .containsExactly("alpha", "beta", "alpha");
    }

    private static List<Source> productionSources() throws Exception {
        List<Source> result = new ArrayList<>();
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("architecture checks require the JDK compiler").isNotNull();
        try (var files = Files.walk(ROOT); var manager = compiler.getStandardFileManager(null, null, null)) {
            var inputs = manager.getJavaFileObjectsFromPaths(files.filter(path -> path.toString().endsWith(".java")).sorted().toList());
            var task = (JavacTask) compiler.getTask(null, manager, null, List.of("-proc:none", "--release", "17"), null, inputs);
            for (CompilationUnitTree unit : task.parse()) result.add(inspect(unit.getSourceFile().getName(), unit));
        }
        return result;
    }

    private static Source parse(String file, String text) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("architecture checks require the JDK compiler").isNotNull();
        var input = new SimpleJavaFileObject(URI.create("string:///" + file.replace('\\', '/').replace(' ', '_')), javax.tools.JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return text; }
        };
        try (var manager = compiler.getStandardFileManager(null, null, null)) {
            var task = (JavacTask) compiler.getTask(null, manager, null, List.of("-proc:none", "--release", "17"), null, List.of(input));
            return inspect(file, task.parse().iterator().next());
        }
    }

    private static Source inspect(String file, CompilationUnitTree unit) {
        Set<String> references = new TreeSet<>(), declarations = new TreeSet<>();
        String packageName = unit.getPackageName().toString();
        for (Tree declaration : unit.getTypeDecls()) {
            if (declaration instanceof ClassTree type) declarations.add(packageName + "." + type.getSimpleName());
        }
        new TreeScanner<Void, Void>() {
            @Override public Void visitMemberSelect(MemberSelectTree node, Void unused) {
                String name = node.toString();
                if (name.startsWith(PREFIX)) references.add(name);
                return super.visitMemberSelect(node, unused);
            }
        }.scan(unit, null);
        return new Source(owner(packageName), file, references, declarations);
    }

    private static String owner(String name) {
        // The root Spring application is composition, part of bootstrap, not an omitted module.
        if (name.equals("com.seasonplan")) return "bootstrap";
        if (!name.startsWith(PREFIX)) return null;
        String suffix = name.substring(PREFIX.length());
        int separator = suffix.indexOf('.');
        String first = separator < 0 ? suffix : suffix.substring(0, separator);
        return Character.isUpperCase(first.charAt(0)) ? "bootstrap" : first;
    }

    private static List<String> cycle(Map<String, Set<String>> graph) {
        Set<String> complete = new HashSet<>();
        for (String node : new TreeSet<>(graph.keySet())) {
            List<String> found = cycleFrom(node, graph, complete, new ArrayList<>());
            if (!found.isEmpty()) return found;
        }
        return List.of();
    }
    private static List<String> cycleFrom(String node, Map<String, Set<String>> graph, Set<String> complete, List<String> path) {
        int prior = path.indexOf(node);
        if (prior >= 0) {
            List<String> result = new ArrayList<>(path.subList(prior, path.size()));
            result.add(node);
            return result;
        }
        if (complete.contains(node)) return List.of();
        path.add(node);
        for (String target : new TreeSet<>(graph.getOrDefault(node, Set.of()))) {
            List<String> found = cycleFrom(target, graph, complete, path);
            if (!found.isEmpty()) return found;
        }
        path.remove(path.size() - 1);
        complete.add(node);
        return List.of();
    }

    private static void collectContractValues(Type type, Set<String> values, Set<Type> visited) {
        if (!visited.add(type)) return;
        if (type instanceof ParameterizedType parameterized) {
            collectContractValues(parameterized.getRawType(), values, visited);
            for (Type argument : parameterized.getActualTypeArguments()) collectContractValues(argument, values, visited);
        } else if (type instanceof WildcardType wildcard) {
            for (Type bound : wildcard.getUpperBounds()) collectContractValues(bound, values, visited);
        } else if (type instanceof Class<?> value) {
            if (value.isRecord() || value.isEnum()) {
                values.add(value.getCanonicalName());
                if (value.isRecord()) for (RecordComponent component : value.getRecordComponents()) collectContractValues(component.getGenericType(), values, visited);
            }
            if (value.getPackageName().contains(".api")) {
                for (Method method : value.getDeclaredMethods()) {
                    collectContractValues(method.getGenericReturnType(), values, visited);
                    for (Type parameter : method.getGenericParameterTypes()) collectContractValues(parameter, values, visited);
                }
            }
        }
    }
}
