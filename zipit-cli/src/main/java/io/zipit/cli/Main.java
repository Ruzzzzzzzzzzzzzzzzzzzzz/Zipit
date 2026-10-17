package io.zipit.cli;

import io.zipit.core.api.ProgressListener;
import io.zipit.core.config.ConfigException;
import io.zipit.core.config.ConfigLoader;
import io.zipit.core.config.Presets;
import io.zipit.core.config.ZipitConfig;
import io.zipit.core.enterprise.LicenseException;
import io.zipit.core.enterprise.LicenseInfo;
import io.zipit.core.enterprise.LicenseValidator;
import io.zipit.core.enterprise.MappingDiff;
import io.zipit.core.enterprise.WatermarkTracer;
import io.zipit.core.engine.EngineResult;
import io.zipit.core.engine.ZipitEngine;
import io.zipit.core.engine.ZipitVerifier;
import io.zipit.core.util.Log;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Command-line entry point. */
public final class Main {

    private static final String VERSION = "1.0.0";

    private Main() {
    }

    public static void main(String[] args) {
        try {
            Args parsed = Args.parse(args);
            if (parsed.help) {
                usage();
                return;
            }
            if (parsed.version) {
                System.out.println("Zipit " + VERSION);
                return;
            }
            if (parsed.diffOld != null) {
                System.out.println(MappingDiff.compare(Paths.get(parsed.diffOld), Paths.get(parsed.diffNew)));
                return;
            }
            if (parsed.traceWatermark != null) {
                List<String> marks = WatermarkTracer.trace(Paths.get(parsed.traceWatermark));
                if (marks.isEmpty()) {
                    System.out.println("No Zipit watermark found");
                } else {
                    for (String mark : marks) {
                        System.out.println(mark);
                    }
                }
                return;
            }
            final boolean quiet = parsed.quiet;
            Log log = new Log(new Log.Sink() {
                @Override
                public void line(String level, String message) {
                    if (quiet && !"ERROR".equals(level) && !"WARN".equals(level)) {
                        return;
                    }
                    if ("ERROR".equals(level)) {
                        System.err.println("[" + level + "] " + message);
                    } else {
                        System.out.println("[" + level + "] " + message);
                    }
                }
            }, parsed.verbose);

            if (parsed.license != null || parsed.requireLicense) {
                if (parsed.license == null) {
                    System.err.println("Enterprise license required but --license was not provided");
                    System.exit(3);
                }
                LicenseInfo license = LicenseValidator.validate(
                        Paths.get(parsed.license),
                        parsed.licenseKey == null ? null : Paths.get(parsed.licenseKey));
                if (license.isValid()) {
                    log.info("License: %s", license);
                } else {
                    log.warn("License problem: %s", license.error());
                    if (parsed.requireLicense) {
                        System.exit(3);
                    }
                }
            }

            ZipitConfig config = buildConfig(parsed, log);
            if (!Files.exists(config.input())) {
                throw new ConfigException("Input does not exist: " + config.input());
            }

            ProgressListener progress = new ProgressListener() {
                @Override
                public void onProgress(int percent, String message) {
                    if (!quiet) {
                        System.out.println(String.format("[%3d%%] %s", percent, message));
                    }
                }
            };

            ZipitEngine engine = new ZipitEngine(log);
            EngineResult result = engine.run(config, progress);

            if (!result.dryRun()) {
                System.out.println("Zipit finished: " + result.classes() + " classes, "
                        + result.resources() + " resources in " + result.millis() + " ms");
                if (parsed.verify) {
                    List<String> errors = ZipitVerifier.verify(config.output(), config.libraries());
                    if (errors.isEmpty()) {
                        System.out.println("Verification: OK");
                    } else {
                        System.err.println("Verification failed for " + errors.size() + " class(es):");
                        for (String error : errors) {
                            System.err.println("  " + error);
                        }
                        System.exit(2);
                    }
                }
            }
        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            usage();
            System.exit(1);
        } catch (LicenseException e) {
            System.err.println("License error: " + e.getMessage());
            System.exit(3);
        } catch (Exception e) {
            System.err.println("Zipit failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static ZipitConfig buildConfig(Args args, Log log) throws Exception {
        ZipitConfig base;
        if (args.config != null) {
            base = ConfigLoader.load(Paths.get(args.config));
        } else {
            ZipitConfig.Builder builder = new ZipitConfig.Builder();
            if (args.input == null || args.output == null) {
                throw new ConfigException("Either -c <config> or both -i <input> and -o <output> are required");
            }
            builder.input(Paths.get(args.input));
            builder.output(Paths.get(args.output));
            base = builder.build();
        }

        ZipitConfig.Builder b = base.toBuilder();
        if (args.input != null) {
            b.input(Paths.get(args.input));
        }
        if (args.output != null) {
            b.output(Paths.get(args.output));
        }
        for (String library : args.libraries) {
            b.addLibrary(Paths.get(library));
        }
        if (!args.keep.isEmpty()) {
            List<String> keep = new ArrayList<String>(base.keep());
            keep.addAll(args.keep);
            b.keep(keep);
        }
        if (args.seed != null) {
            b.seed(args.seed);
        }
        if (args.dryRun) {
            b.dryRun(true);
        }
        if (args.fixedTime) {
            b.preserveTimestamps(false);
        }

        ZipitConfig.RenamerOptions r = base.renamer();
        if (args.noRename || args.dictionary != null || args.minNameLength != null) {
            r = new ZipitConfig.RenamerOptions(!args.noRename && r.enabled(), r.prefix(), r.repackage(),
                    r.repackagePackage(), r.renameClasses(), r.renameMethods(), r.renameFields(),
                    r.mappingFile(),
                    args.dictionary == null ? r.dictionary() : args.dictionary,
                    args.minNameLength == null ? r.minimumNameLength() : args.minNameLength.intValue());
        }
        b.renamer(r);

        ZipitConfig.StringEncryptionOptions s = base.stringEncryption();
        if (args.noString) {
            s = new ZipitConfig.StringEncryptionOptions(false, s.minLength());
        }
        b.stringEncryption(s);

        ZipitConfig.IntegerEncryptionOptions i = base.integerEncryption();
        if (args.noInteger) {
            i = new ZipitConfig.IntegerEncryptionOptions(false, i.includeLong());
        }
        b.integerEncryption(i);

        ZipitConfig.FlowOptions f = base.flow();
        if (args.noFlow) {
            f = new ZipitConfig.FlowOptions(false, f.level(), f.maxInstructions(), f.flattening());
        } else if (args.flatten) {
            f = new ZipitConfig.FlowOptions(f.enabled(), f.level(), f.maxInstructions(), true);
        }
        b.flow(f);

        ZipitConfig.VirtualizationOptions virtualization = base.methodVirtualization();
        if (args.virtualize || args.virtualizeMaxInstructions != null || args.virtualizeMaxMethods != null) {
            virtualization = new ZipitConfig.VirtualizationOptions(
                    args.virtualize || args.virtualizeMaxInstructions != null
                            || args.virtualizeMaxMethods != null || virtualization.enabled(),
                    args.virtualizeMaxInstructions == null
                            ? virtualization.maxInstructions() : args.virtualizeMaxInstructions.intValue(),
                    args.virtualizeMaxMethods == null
                            ? virtualization.maxMethods() : args.virtualizeMaxMethods.intValue());
        }
        b.methodVirtualization(virtualization);

        ZipitConfig.AntiLlmOptions antiLlm = base.antiLlm();
        if (args.antiLlm || args.antiLlmDecoys != null || args.antiLlmLayers != null) {
            antiLlm = new ZipitConfig.AntiLlmOptions(
                    true,
                    args.antiLlmDecoys == null
                            ? antiLlm.decoyMethodsPerClass() : args.antiLlmDecoys.intValue(),
                    antiLlm.decoyCallSitesPerClass(),
                    args.antiLlmLayers == null
                            ? antiLlm.wrapperLayers() : args.antiLlmLayers.intValue(),
                    antiLlm.maxWrappedMethodsPerClass(),
                    antiLlm.shieldVirtualization());
        }
        b.antiLlm(antiLlm);

        ZipitConfig.ShuffleOptions sh = base.shuffle();
        if (args.noShuffle) {
            sh = new ZipitConfig.ShuffleOptions(false);
        }
        b.shuffle(sh);

        ZipitConfig.JunkOptions j = base.junk();
        if (args.noJunk) {
            j = new ZipitConfig.JunkOptions(false, j.methods(), j.fields());
        }
        b.junk(j);

        ZipitConfig.WatermarkOptions w = base.watermark();
        if (args.noWatermark) {
            w = new ZipitConfig.WatermarkOptions(false, w.id());
        }
        b.watermark(w);

        ZipitConfig.MappingOptions mapping = base.mapping();
        if (args.inputMapping != null) {
            mapping = new ZipitConfig.MappingOptions(mapping.enabled(), mapping.file(), args.inputMapping);
        }
        b.mapping(mapping);

        if (args.reportHtml != null || args.reportJson != null) {
            String html = args.reportHtml == null ? base.report().htmlFile() : args.reportHtml;
            String json = args.reportJson == null ? base.report().jsonFile() : args.reportJson;
            b.report(new ZipitConfig.ReportOptions(true, html, json));
        }
        if (args.keepPublicApi) {
            b.keepPublicApi(true);
        }
        if (args.softKeepPublicApi) {
            b.softKeepPublicApi(true);
        }
        if (args.noReflection) {
            b.reflection(new ZipitConfig.ReflectionOptions(false));
        }
        if (args.trim) {
            b.trim(new ZipitConfig.TrimOptions(true, true, true));
        }
        if (args.parameterObfuscation) {
            b.parameterObfuscation(new ZipitConfig.ParameterOptions(true));
        }
        if (args.rewriteResources) {
            b.resources(new ZipitConfig.ResourceOptions(true));
        }
        ZipitConfig.ReferenceOptions references = base.referenceObfuscation();
        boolean referenceEnabled = references.enabled() || args.referenceObfuscation
                || args.referenceVirtual || args.referenceFields || args.referenceConstructors;
        if (referenceEnabled) {
            boolean staticCalls = references.staticCalls() || args.referenceObfuscation;
            boolean virtualCalls = references.virtualCalls() || args.referenceVirtual;
            boolean fields = references.fields() || args.referenceFields;
            boolean constructors = references.constructors() || args.referenceConstructors;
            b.referenceObfuscation(new ZipitConfig.ReferenceOptions(true, staticCalls,
                    virtualCalls, fields, constructors));
        }
        if (args.exceptionObfuscation) {
            b.exceptionObfuscation(new ZipitConfig.ExceptionOptions(true));
        }
        ZipitConfig.IncrementalOptions incremental = base.incremental();
        if (args.incrementalPrevious != null || args.incrementalMetadata != null) {
            incremental = new ZipitConfig.IncrementalOptions(true,
                    args.incrementalPrevious == null ? incremental.previousOutput() : args.incrementalPrevious,
                    args.incrementalMetadata == null ? incremental.metadataFile() : args.incrementalMetadata);
        }
        b.incremental(incremental);
        if (args.antiDebug) {
            b.antiDebug(true);
        }
        ZipitConfig.LicenseOptions license = base.license();
        String server = args.licenseServer == null ? license.server() : args.licenseServer;
        if (server.trim().isEmpty()) {
            String environment = System.getenv("ZIPIT_LICENSE_SERVER");
            if (environment != null) {
                server = environment.trim();
            }
        }
        boolean licenseEnabled = license.enabled();
        String licenseFile = license.file();
        String licenseKey = license.publicKey();
        boolean requireLicense = license.require();
        List<String> licenseFeatures = license.requiredFeatures();
        if (args.license != null) {
            licenseEnabled = true;
            licenseFile = args.license;
            licenseKey = args.licenseKey == null ? "" : args.licenseKey;
            requireLicense = args.requireLicense;
        } else if (args.requireLicense) {
            licenseEnabled = license.enabled() || !license.file().isEmpty();
            requireLicense = true;
        }
        if (!args.licenseFeatures.isEmpty()) {
            licenseFeatures = args.licenseFeatures;
        }
        b.license(new ZipitConfig.LicenseOptions(licenseEnabled, licenseFile, licenseKey,
                requireLicense, licenseFeatures, server));

        ZipitConfig result = b.build();
        if (args.preset != null) {
            result = Presets.apply(result, args.preset);
        }
        if (args.verbose) {
            log.debug("Effective configuration: input=%s output=%s seed=%d preset=%s",
                    result.input(), result.output(), result.seed(), result.preset());
        }
        return result;
    }

    private static void usage() {
        System.out.println("Zipit " + VERSION + " - Java bytecode obfuscator");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java -jar zipit-all.jar -c <config.yml>");
        System.out.println("  java -jar zipit-all.jar -i <input.jar> -o <output.jar> [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -c, --config <file>    YAML configuration");
        System.out.println("  -i, --input <jar>      input JAR");
        System.out.println("  -o, --output <jar>     output JAR");
        System.out.println("  --lib <path>           library JAR or directory (repeatable)");
        System.out.println("  --keep <pattern>       keep glob or @annotation (repeatable/comma-separated)");
        System.out.println("  --preset <name>        library|spring-boot|jackson|service-loader|micronaut|aggressive");
        System.out.println("  --keep-public-api      keep public/protected classes completely");
        System.out.println("  --soft-keep-public-api keep public/protected names only (other transforms still run)");
        System.out.println("  --reflection-off       disable automatic reflection keep analysis");
        System.out.println("  --trim                 remove unused private members");
        System.out.println("  --parameter-obfuscation add unused parameters to private methods");
        System.out.println("  --rewrite-resources    rewrite class names in text resources");
        System.out.println("  --reference-obfuscation hide static calls behind invokedynamic");
        System.out.println("  --reference-virtual    hide virtual/interface calls behind invokedynamic");
        System.out.println("  --reference-fields     hide field access behind invokedynamic");
        System.out.println("  --reference-constructors hide new/constructor sequences behind factory methods");
        System.out.println("  --exception-obfuscation add synthetic catch-all exception handlers");
        System.out.println("  --virtualize           virtualize eligible method bodies (custom VM)");
        System.out.println("  --virtualize-max-instructions <n>  skip methods larger than n instructions");
        System.out.println("  --virtualize-max-methods <n>       cap virtualized methods per class (0 = unlimited)");
        System.out.println("  --anti-llm             enable anti-LLM countermeasures (decoys + wrapper walls)");
        System.out.println("  --anti-llm-decoys <n>  decoy methods injected per class");
        System.out.println("  --anti-llm-layers <n>  nested forwarding layers per wrapped method (0-4)");
        System.out.println("  --incremental <prev.jar> reuse unchanged classes from a previous output");
        System.out.println("  --incremental-metadata <file> incremental metadata path");
        System.out.println("  --anti-debug           add a startup debug-agent guard");
        System.out.println("  --dictionary <chars>   identifier character dictionary");
        System.out.println("  --min-name-length <n>  minimum generated name length");
        System.out.println("  --input-mapping <file> reuse a previous mapping for stable builds");
        System.out.println("  --report <html>        write an HTML audit report");
        System.out.println("  --report-json <json>   write a JSON audit report");
        System.out.println("  --diff-mapping <old> <new>  print a mapping difference and exit");
        System.out.println("  --trace-watermark <jar>     print embedded Zipit watermark(s) and exit");
        System.out.println("  --license <file>       signed enterprise license");
        System.out.println("  --license-key <pem>    RSA public key for license verification");
        System.out.println("  --license-server <url> online license server for revocation/feature checks");
        System.out.println("  --require-license      exit with code 3 when the license is missing/invalid");
        System.out.println("  --require-feature <f>  require a license feature (repeatable): audit, policy, ...");
        System.out.println("  --seed <value>         deterministic seed");
        System.out.println("  --dry-run              analyze only, do not write output");
        System.out.println("  --verify               load output classes to force JVM verification");
        System.out.println("  --fixed-time           write a fixed ZIP timestamp (reproducible builds)");
        System.out.println("  --no-rename            disable renaming");
        System.out.println("  --no-string            disable string encryption");
        System.out.println("  --no-integer           disable integer constant encryption");
        System.out.println("  --flatten              enable state-machine control-flow flattening");
        System.out.println("  --no-flow              disable control-flow obfuscation");
        System.out.println("  --no-shuffle           disable member shuffling");
        System.out.println("  --no-junk              disable junk members");
        System.out.println("  --no-watermark         disable watermark");
        System.out.println("  -v, --verbose          verbose logging");
        System.out.println("  -q, --quiet            only warnings and errors");
        System.out.println("  -h, --help             show this help");
        System.out.println("  --version              print version");
    }

    private static final class Args {
        String config;
        String input;
        String output;
        final List<String> libraries = new ArrayList<String>();
        final List<String> keep = new ArrayList<String>();
        String preset;
        boolean keepPublicApi;
        boolean softKeepPublicApi;
        boolean noReflection;
        boolean trim;
        boolean parameterObfuscation;
        boolean rewriteResources;
        boolean referenceObfuscation;
        boolean referenceVirtual;
        boolean referenceFields;
        boolean referenceConstructors;
        boolean exceptionObfuscation;
        boolean virtualize;
        Integer virtualizeMaxInstructions;
        Integer virtualizeMaxMethods;
        boolean antiLlm;
        Integer antiLlmDecoys;
        Integer antiLlmLayers;
        String incrementalPrevious;
        String incrementalMetadata;
        boolean antiDebug;
        String dictionary;
        Integer minNameLength;
        final List<String> licenseFeatures = new ArrayList<String>();
        String inputMapping;
        String reportHtml;
        String reportJson;
        String diffOld;
        String diffNew;
        String traceWatermark;
        String license;
        String licenseKey;
        String licenseServer;
        boolean requireLicense;
        Long seed;
        boolean dryRun;
        boolean verify;
        boolean fixedTime;
        boolean noRename;
        boolean noString;
        boolean noInteger;
        boolean noFlow;
        boolean flatten;
        boolean noShuffle;
        boolean noJunk;
        boolean noWatermark;
        boolean verbose;
        boolean quiet;
        boolean help;
        boolean version;

        static Args parse(String[] argv) throws ConfigException {
            Args a = new Args();
            for (int i = 0; i < argv.length; i++) {
                String arg = argv[i];
                if ("-c".equals(arg) || "--config".equals(arg)) {
                    a.config = value(argv, ++i, arg);
                } else if ("-i".equals(arg) || "--input".equals(arg)) {
                    a.input = value(argv, ++i, arg);
                } else if ("-o".equals(arg) || "--output".equals(arg)) {
                    a.output = value(argv, ++i, arg);
                } else if ("--lib".equals(arg) || "--library".equals(arg)) {
                    a.libraries.add(value(argv, ++i, arg));
                } else if ("--keep".equals(arg)) {
                    for (String part : value(argv, ++i, arg).split(",")) {
                        if (!part.trim().isEmpty()) {
                            a.keep.add(part.trim());
                        }
                    }
                } else if ("--preset".equals(arg)) {
                    a.preset = value(argv, ++i, arg);
                } else if ("--keep-public-api".equals(arg)) {
                    a.keepPublicApi = true;
                } else if ("--soft-keep-public-api".equals(arg)) {
                    a.softKeepPublicApi = true;
                } else if ("--reflection-off".equals(arg)) {
                    a.noReflection = true;
                } else if ("--trim".equals(arg)) {
                    a.trim = true;
                } else if ("--parameter-obfuscation".equals(arg)) {
                    a.parameterObfuscation = true;
                } else if ("--rewrite-resources".equals(arg)) {
                    a.rewriteResources = true;
                } else if ("--reference-obfuscation".equals(arg)) {
                    a.referenceObfuscation = true;
                } else if ("--reference-virtual".equals(arg)) {
                    a.referenceVirtual = true;
                } else if ("--reference-fields".equals(arg)) {
                    a.referenceFields = true;
                } else if ("--reference-constructors".equals(arg)) {
                    a.referenceConstructors = true;
                } else if ("--exception-obfuscation".equals(arg)) {
                    a.exceptionObfuscation = true;
                } else if ("--virtualize".equals(arg)) {
                    a.virtualize = true;
                } else if ("--virtualize-max-instructions".equals(arg)) {
                    a.virtualizeMaxInstructions = Integer.valueOf(value(argv, ++i, arg));
                } else if ("--virtualize-max-methods".equals(arg)) {
                    a.virtualizeMaxMethods = Integer.valueOf(value(argv, ++i, arg));
                } else if ("--anti-llm".equals(arg)) {
                    a.antiLlm = true;
                } else if ("--anti-llm-decoys".equals(arg)) {
                    a.antiLlmDecoys = Integer.valueOf(value(argv, ++i, arg));
                } else if ("--anti-llm-layers".equals(arg)) {
                    a.antiLlmLayers = Integer.valueOf(value(argv, ++i, arg));
                } else if ("--incremental".equals(arg)) {
                    a.incrementalPrevious = value(argv, ++i, arg);
                } else if ("--incremental-metadata".equals(arg)) {
                    a.incrementalMetadata = value(argv, ++i, arg);
                } else if ("--anti-debug".equals(arg)) {
                    a.antiDebug = true;
                } else if ("--dictionary".equals(arg)) {
                    a.dictionary = value(argv, ++i, arg);
                } else if ("--min-name-length".equals(arg)) {
                    a.minNameLength = Integer.valueOf(value(argv, ++i, arg));
                } else if ("--require-feature".equals(arg)) {
                    a.licenseFeatures.add(value(argv, ++i, arg));
                } else if ("--input-mapping".equals(arg)) {
                    a.inputMapping = value(argv, ++i, arg);
                } else if ("--report".equals(arg)) {
                    a.reportHtml = value(argv, ++i, arg);
                } else if ("--report-json".equals(arg)) {
                    a.reportJson = value(argv, ++i, arg);
                } else if ("--diff-mapping".equals(arg)) {
                    a.diffOld = value(argv, ++i, arg);
                    a.diffNew = value(argv, ++i, arg);
                } else if ("--trace-watermark".equals(arg)) {
                    a.traceWatermark = value(argv, ++i, arg);
                } else if ("--license".equals(arg)) {
                    a.license = value(argv, ++i, arg);
                } else if ("--license-key".equals(arg)) {
                    a.licenseKey = value(argv, ++i, arg);
                } else if ("--license-server".equals(arg)) {
                    a.licenseServer = value(argv, ++i, arg);
                } else if ("--require-license".equals(arg)) {
                    a.requireLicense = true;
                } else if ("--seed".equals(arg)) {
                    a.seed = Long.valueOf(value(argv, ++i, arg));
                } else if ("--dry-run".equals(arg)) {
                    a.dryRun = true;
                } else if ("--verify".equals(arg)) {
                    a.verify = true;
                } else if ("--fixed-time".equals(arg)) {
                    a.fixedTime = true;
                } else if ("--no-rename".equals(arg)) {
                    a.noRename = true;
                } else if ("--no-string".equals(arg)) {
                    a.noString = true;
                } else if ("--no-integer".equals(arg)) {
                    a.noInteger = true;
                } else if ("--flatten".equals(arg)) {
                    a.flatten = true;
                } else if ("--no-flow".equals(arg)) {
                    a.noFlow = true;
                } else if ("--no-shuffle".equals(arg)) {
                    a.noShuffle = true;
                } else if ("--no-junk".equals(arg)) {
                    a.noJunk = true;
                } else if ("--no-watermark".equals(arg)) {
                    a.noWatermark = true;
                } else if ("-v".equals(arg) || "--verbose".equals(arg)) {
                    a.verbose = true;
                } else if ("-q".equals(arg) || "--quiet".equals(arg)) {
                    a.quiet = true;
                } else if ("-h".equals(arg) || "--help".equals(arg)) {
                    a.help = true;
                } else if ("--version".equals(arg)) {
                    a.version = true;
                } else {
                    throw new ConfigException("Unknown argument: " + arg);
                }
            }
            return a;
        }

        private static String value(String[] argv, int index, String option) throws ConfigException {
            if (index >= argv.length) {
                throw new ConfigException("Missing value for " + option);
            }
            return argv[index];
        }
    }
}
