package io.zipit.core.engine;

import io.zipit.core.api.Pass;
import io.zipit.core.api.PassContext;
import io.zipit.core.api.ProgressListener;
import io.zipit.core.api.RuntimeSupport;
import io.zipit.core.config.ZipitConfig;
import io.zipit.core.enterprise.AuditReport;
import io.zipit.core.enterprise.LicenseException;
import io.zipit.core.enterprise.LicenseInfo;
import io.zipit.core.enterprise.LicenseOnlineValidator;
import io.zipit.core.enterprise.LicenseValidator;
import io.zipit.core.enterprise.RiskReport;
import io.zipit.core.model.ClassPool;
import io.zipit.core.model.Hierarchy;
import io.zipit.core.pass.impl.AntiLlmPass;
import io.zipit.core.pass.impl.ControlFlowFlatteningPass;
import io.zipit.core.pass.impl.AntiDebugPass;
import io.zipit.core.pass.impl.DebugInfoPass;
import io.zipit.core.pass.impl.ExceptionObfuscationPass;
import io.zipit.core.pass.impl.FieldInitializationPass;
import io.zipit.core.pass.impl.FlowObfuscationPass;
import io.zipit.core.pass.impl.IntegerEncryptionPass;
import io.zipit.core.pass.impl.InvokeWrapperPass;
import io.zipit.core.pass.impl.JunkMemberPass;
import io.zipit.core.pass.impl.MemberShufflePass;
import io.zipit.core.pass.impl.MemberTrimPass;
import io.zipit.core.pass.impl.ParameterObfuscationPass;
import io.zipit.core.pass.impl.ReferenceObfuscationPass;
import io.zipit.core.pass.impl.ReflectionAnalysisPass;
import io.zipit.core.pass.impl.RenamePass;
import io.zipit.core.pass.impl.ResourcePathObfuscationPass;
import io.zipit.core.pass.impl.ResourceRewritePass;
import io.zipit.core.pass.impl.StringEncryptionPass;
import io.zipit.core.pass.impl.VirtualizationPass;
import io.zipit.core.pass.impl.WatermarkPass;
import io.zipit.core.rules.DefaultRuleSet;
import io.zipit.core.rules.RuleSet;
import io.zipit.core.runtime.DefaultRuntimeSupport;
import io.zipit.core.util.Jars;
import io.zipit.core.util.Log;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Orchestrates loading, transforming and writing a program. */
public final class ZipitEngine {

    private final Log log;

    public ZipitEngine(Log log) {
        this.log = log;
    }

    /**
     * Runs the complete pipeline.
     *
     * @param config   validated configuration
     * @param progress progress callback, never {@code null}
     * @return run statistics
     * @throws Exception when loading, transforming or writing fails
     */
    public EngineResult run(ZipitConfig config, ProgressListener progress) throws Exception {
        long start = System.currentTimeMillis();
        ProgressListener listener = progress == null ? ProgressListener.NONE : progress;

        listener.onProgress(2, "Loading input " + config.input());
        ClassPool pool = new ClassPool();
        Jars.read(config.input(), pool, log);
        log.info("Loaded %d classes and %d resources from %s",
                pool.classes().size(), pool.resources().size(), config.input());

        listener.onProgress(8, "Building hierarchy");
        Hierarchy hierarchy = new Hierarchy();
        for (ClassNode node : pool.classes().values()) {
            hierarchy.addInput(node);
        }
        for (Path library : config.libraries()) {
            loadLibrary(library, hierarchy);
        }

        Stats stats = new Stats();
        stats.add("input.classes", pool.classes().size());
        stats.add("input.resources", pool.resources().size());
        RiskReport risk = RiskReport.scan(pool, hierarchy);
        LicenseInfo license = validateLicense(config, log, stats);

        Map<String, String> inputHashes = new LinkedHashMap<String, String>();
        for (Map.Entry<String, ClassNode> entry : pool.classes().entrySet()) {
            byte[] bytes = pool.originalBytes(entry.getKey());
            if (bytes != null) {
                inputHashes.put(entry.getKey(), IncrementalCache.hash(bytes));
            }
        }
        IncrementalCache.Plan incremental = IncrementalCache.load(config, inputHashes, log);

        if (config.dryRun()) {
            stats.add("duration.ms", System.currentTimeMillis() - start);
            Path html = reportPath(config, config.report().htmlFile());
            Path json = reportPath(config, config.report().jsonFile());
            EngineResult result = new EngineResult(config.output(), pool.classes().size(), pool.resources().size(),
                    System.currentTimeMillis() - start, true, new LinkedHashMap<String, Long>(),
                    stats, risk, license, html, json);
            if (reportEnabled(config)) {
                AuditReport.write(html, json, config, result, stats, risk, license);
            }
            listener.onProgress(100, "Dry run complete");
            return result;
        }

        RuleSet rules = new DefaultRuleSet(config.keep(), config.softKeep(),
                config.keepPublicApi(), config.softKeepPublicApi());
        RuntimeSupport runtime = new DefaultRuntimeSupport();
        MappingSet mappings = new MappingSet();
        Random random = new Random(config.seed());
        PassContext context = new PassContext(config, pool, hierarchy, rules, runtime,
                mappings, stats, log, random, listener);

        List<Pass> passes = passes();
        Collections.sort(passes, new Comparator<Pass>() {
            @Override
            public int compare(Pass a, Pass b) {
                return Integer.compare(a.order(), b.order());
            }
        });

        Map<String, Long> timings = new LinkedHashMap<String, Long>();
        int index = 0;
        for (Pass pass : passes) {
            index++;
            if (listener.isCancelled()) {
                throw new java.util.concurrent.CancellationException("Zipit run cancelled");
            }
            if (!pass.enabled(context)) {
                log.debug("Pass %s is disabled", pass.name());
                continue;
            }
            int percent = 10 + (int) (80.0 * (index - 1) / Math.max(1, passes.size()));
            listener.onProgress(percent, "Running " + pass.name());
            long passStart = System.currentTimeMillis();
            log.info("Pass %s started", pass.name());
            pass.run(context);
            long elapsed = System.currentTimeMillis() - passStart;
            timings.put(pass.name(), Long.valueOf(elapsed));
            stats.add("pass." + pass.name() + ".ms", elapsed);
            log.info("Pass %s finished in %d ms", pass.name(), elapsed);
        }

        if (incremental != null) {
            incremental.apply(pool, mappings, stats);
        }
        listener.onProgress(92, "Writing " + config.output());
        Jars.write(config.output(), pool, hierarchy, config.preserveTimestamps(), log);
        IncrementalCache.write(config, inputHashes, mappings, log);
        listener.onProgress(100, "Done");

        stats.add("output.classes", pool.classes().size());
        stats.add("output.resources", pool.resources().size());
        stats.add("duration.ms", System.currentTimeMillis() - start);
        Path html = reportPath(config, config.report().htmlFile());
        Path json = reportPath(config, config.report().jsonFile());
        EngineResult result = new EngineResult(config.output(), pool.classes().size(), pool.resources().size(),
                System.currentTimeMillis() - start, false, timings, stats, risk, license, html, json);
        if (reportEnabled(config)) {
            AuditReport.write(html, json, config, result, stats, risk, license);
            log.info("Audit report written (%s, %s)", html, json);
        }
        log.info("Output written to %s (%d classes, %d resources, %d ms)",
                config.output(), result.classes(), result.resources(), result.millis());
        return result;
    }

    private static List<Pass> passes() {
        List<Pass> passes = new ArrayList<Pass>();
        passes.add(new ReflectionAnalysisPass());
        passes.add(new MemberTrimPass());
        passes.add(new FieldInitializationPass());
        passes.add(new DebugInfoPass());
        passes.add(new MemberShufflePass());
        passes.add(new InvokeWrapperPass());
        passes.add(new ParameterObfuscationPass());
        passes.add(new RenamePass());
        passes.add(new AntiLlmPass());
        passes.add(new VirtualizationPass());
        passes.add(new ResourcePathObfuscationPass());
        passes.add(new ReferenceObfuscationPass());
        passes.add(new StringEncryptionPass());
        passes.add(new IntegerEncryptionPass());
        passes.add(new FlowObfuscationPass());
        passes.add(new ControlFlowFlatteningPass());
        passes.add(new ExceptionObfuscationPass());
        passes.add(new JunkMemberPass());
        passes.add(new WatermarkPass());
        passes.add(new AntiDebugPass());
        passes.add(new ResourceRewritePass());
        return passes;
    }

    private static LicenseInfo validateLicense(ZipitConfig config, Log log, Stats stats) {
        ZipitConfig.LicenseOptions options = config.license();
        if (options.file().trim().isEmpty()) {
            if (options.require()) {
                throw new LicenseException("Enterprise license required but license.file is empty");
            }
            return null;
        }
        Path licensePath = resolveRelative(config, options.file());
        Path keyPath = options.publicKey().trim().isEmpty()
                ? null : resolveRelative(config, options.publicKey());
        LicenseInfo license;
        try {
            license = LicenseValidator.validate(licensePath, keyPath);
        } catch (IOException e) {
            throw new LicenseException("Unable to read license: " + e.getMessage(), e);
        }
        if (!license.isValid()) {
            if (options.require()) {
                throw new LicenseException("License invalid: " + license.error());
            }
            log.warn("License problem: %s", license.error());
            return license;
        }
        for (String feature : options.requiredFeatures()) {
            if (!license.hasFeature(feature)) {
                if (options.require()) {
                    throw new LicenseException("License does not include required feature: " + feature);
                }
                log.warn("License does not include feature: %s", feature);
            }
        }
        if (!options.server().trim().isEmpty()) {
            long started = System.currentTimeMillis();
            LicenseOnlineValidator.Result online;
            try {
                online = LicenseOnlineValidator.check(licensePath, options.server());
            } catch (IOException e) {
                online = new LicenseOnlineValidator.Result(false, e.getMessage());
            }
            stats.add("license.online.ms", System.currentTimeMillis() - started);
            stats.add("license.online.valid", online.isValid() ? 1 : 0);
            if (!online.isValid()) {
                if (options.require()) {
                    throw new LicenseException("Online license validation failed: " + online.reason());
                }
                log.warn("Online license validation failed: %s", online.reason());
            } else {
                log.info("Online license check: OK (%s)", options.server());
            }
        }
        log.info("License: %s", license);
        return license;
    }

    private static Path resolveRelative(ZipitConfig config, String value) {
        Path path = java.nio.file.Paths.get(value.trim());
        if (path.isAbsolute()) {
            return path;
        }
        Path parent = config.output().toAbsolutePath().getParent();
        return parent == null ? path : parent.resolve(path);
    }

    private static boolean reportEnabled(ZipitConfig config) {
        return config.report().enabled();
    }

    private static Path reportPath(ZipitConfig config, String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        Path path = java.nio.file.Paths.get(value.trim());
        if (path.isAbsolute()) {
            return path;
        }
        Path parent = config.output().toAbsolutePath().getParent();
        return parent == null ? path : parent.resolve(path);
    }

    private void loadLibrary(Path path, Hierarchy hierarchy) throws IOException {
        if (!Files.exists(path)) {
            log.warn("Library not found: %s", path);
            return;
        }
        if (Files.isDirectory(path)) {
            List<Path> files = new ArrayList<Path>();
            try (java.util.stream.Stream<Path> stream = Files.walk(path)) {
                stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".class"))
                        .forEach(files::add);
            }
            for (Path file : files) {
                try {
                    hierarchy.addLibrary(Files.readAllBytes(file));
                } catch (RuntimeException e) {
                    log.debug("Skipping library class %s: %s", file, e.getMessage());
                }
            }
            log.info("Loaded library directory %s (%d classes)", path, files.size());
        } else {
            hierarchy.addLibraryJar(path);
            log.info("Loaded library %s", path);
        }
    }
}
