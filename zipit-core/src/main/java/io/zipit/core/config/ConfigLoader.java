package io.zipit.core.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads {@link ZipitConfig} from a YAML file.
 *
 * <p>Relative {@code input}, {@code output} and {@code libraries} paths are
 * resolved against the directory containing the configuration file.</p>
 */
public final class ConfigLoader {

    private ConfigLoader() {
    }

    /**
     * Reads and validates a configuration file.
     *
     * @param file YAML configuration
     * @return immutable configuration
     * @throws IOException     if the file cannot be read
     * @throws ConfigException if the document is malformed or incomplete
     */
    public static ZipitConfig load(Path file) throws IOException, ConfigException {
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        Map<String, Object> root = YamlParser.parse(text);
        Path base = file.toAbsolutePath().getParent();
        return fromMap(root, base);
    }

    /**
     * Builds a configuration from an already parsed YAML tree.
     *
     * @param root parsed root mapping
     * @param base base directory for relative paths, or {@code null} for the CWD
     * @return immutable configuration
     * @throws ConfigException if required keys are missing or malformed
     */
    public static ZipitConfig fromMap(Map<String, Object> root, Path base) throws ConfigException {
        Map<String, Object> map = normalize(root);
        ZipitConfig.Builder b = new ZipitConfig.Builder();

        String input = str(map, "input", null);
        String output = str(map, "output", null);
        if (input != null) {
            b.input(resolve(base, input));
        }
        if (output != null) {
            b.output(resolve(base, output));
        }

        List<String> libraries = stringList(map, "libraries");
        for (String library : libraries) {
            b.addLibrary(resolve(base, library));
        }

        List<String> keep = stringList(map, "keep");
        keep.addAll(stringList(map, "keepClasses"));
        keep.addAll(stringList(map, "keepMembers"));
        b.keep(keep);
        b.softKeep(stringList(map, "softKeep"));

        b.seed(longValue(map, "seed", 0x5A17L));
        b.preserveTimestamps(bool(map, "preserveTimestamps", true));
        b.dryRun(bool(map, "dryRun", false));
        b.preset(str(map, "preset", ""));
        b.keepPublicApi(bool(map, "keepPublicApi", false));
        b.softKeepPublicApi(bool(map, "softKeepPublicApi", false));

        Map<String, Object> renamer = normalize(asMap(map.get("renamer")));
        b.renamer(new ZipitConfig.RenamerOptions(
                bool(renamer, "enabled", true),
                str(renamer, "prefix", ""),
                bool(renamer, "repackage", false),
                str(renamer, "repackagePackage", ""),
                bool(renamer, "renameClasses", true),
                bool(renamer, "renameMethods", true),
                bool(renamer, "renameFields", true),
                str(renamer, "mappingFile", "zipit-mapping.txt"),
                str(renamer, "dictionary", "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"),
                intValue(renamer, "minimumNameLength", 1),
                stringList(renamer, "repackageKept")));

        Map<String, Object> stringEnc = normalize(asMap(map.get("stringEncryption")));
        b.stringEncryption(new ZipitConfig.StringEncryptionOptions(
                bool(stringEnc, "enabled", true),
                intValue(stringEnc, "minLength", 2)));

        Map<String, Object> intEnc = normalize(asMap(map.get("integerEncryption")));
        b.integerEncryption(new ZipitConfig.IntegerEncryptionOptions(
                bool(intEnc, "enabled", true),
                bool(intEnc, "includeLong", true)));

        Map<String, Object> flow = normalize(asMap(map.get("flowObfuscation")));
        b.flow(new ZipitConfig.FlowOptions(
                bool(flow, "enabled", true),
                str(flow, "level", "light"),
                intValue(flow, "maxInstructions", 8000),
                bool(flow, "flattening", false),
                stringList(flow, "flatteningExclude")));

        Map<String, Object> debug = normalize(asMap(map.get("debugInfo")));
        b.debug(new ZipitConfig.DebugOptions(
                bool(debug, "enabled", true),
                bool(debug, "removeSourceFile", true),
                bool(debug, "removeLineNumbers", true),
                bool(debug, "removeLocalVariables", true),
                bool(debug, "removeSignatures", true),
                bool(debug, "removeInnerClasses", false),
                bool(debug, "removeKotlin", true)));

        Map<String, Object> shuffle = normalize(asMap(map.get("memberShuffle")));
        b.shuffle(new ZipitConfig.ShuffleOptions(bool(shuffle, "enabled", true)));

        Map<String, Object> wrapper = normalize(asMap(map.get("invokeWrapper")));
        b.invokeWrapper(new ZipitConfig.WrapperOptions(
                bool(wrapper, "enabled", false),
                intValue(wrapper, "maxPerClass", 200)));

        Map<String, Object> watermark = normalize(asMap(map.get("watermark")));
        b.watermark(new ZipitConfig.WatermarkOptions(
                bool(watermark, "enabled", true),
                str(watermark, "id", "zipit")));

        Map<String, Object> mapping = normalize(asMap(map.get("mapping")));
        String inputMapping = str(mapping, "inputFile", "");
        if (inputMapping.isEmpty()) {
            inputMapping = str(mapping, "inputMappings", "");
        }
        if (inputMapping.isEmpty()) {
            inputMapping = str(mapping, "inputMapping", "");
        }
        b.mapping(new ZipitConfig.MappingOptions(
                bool(mapping, "enabled", true),
                str(mapping, "file", "zipit-mapping.txt"),
                inputMapping));

        Map<String, Object> report = normalize(asMap(map.get("report")));
        String html = str(report, "htmlFile", "");
        String json = str(report, "jsonFile", "");
        b.report(new ZipitConfig.ReportOptions(
                bool(report, "enabled", !html.isEmpty() || !json.isEmpty()),
                html, json));

        Map<String, Object> junk = normalize(asMap(map.get("junkMembers")));
        b.junk(new ZipitConfig.JunkOptions(
                bool(junk, "enabled", true),
                intValue(junk, "methods", 2),
                intValue(junk, "fields", 2)));

        Map<String, Object> reflection = normalize(asMap(map.get("reflection")));
        b.reflection(new ZipitConfig.ReflectionOptions(
                bool(reflection, "enabled", true),
                bool(reflection, "rewriteClassStrings", false)));

        Map<String, Object> trim = normalize(asMap(map.get("trim")));
        b.trim(new ZipitConfig.TrimOptions(
                bool(trim, "enabled", false),
                bool(trim, "removeUnusedPrivateMethods", true),
                bool(trim, "removeUnusedPrivateFields", true)));

        Map<String, Object> parameters = normalize(asMap(map.get("parameterObfuscation")));
        b.parameterObfuscation(new ZipitConfig.ParameterOptions(
                bool(parameters, "enabled", false)));

        Map<String, Object> license = normalize(asMap(map.get("license")));
        b.license(new ZipitConfig.LicenseOptions(
                bool(license, "enabled", !str(license, "file", "").isEmpty()),
                str(license, "file", ""),
                str(license, "publicKey", ""),
                bool(license, "require", false),
                stringList(license, "features"),
                str(license, "server", "")));

        Map<String, Object> resources = normalize(asMap(map.get("resources")));
        b.resources(new ZipitConfig.ResourceOptions(
                bool(resources, "rewriteContent", false),
                bool(resources, "obfuscatePaths", false),
                stringList(resources, "rewriteDomains")));

        Map<String, Object> references = normalize(asMap(map.get("referenceObfuscation")));
        b.referenceObfuscation(new ZipitConfig.ReferenceOptions(
                bool(references, "enabled", false),
                bool(references, "staticCalls", true),
                bool(references, "virtualCalls", false),
                bool(references, "fields", false),
                bool(references, "constructors", false)));

        Map<String, Object> exceptions = normalize(asMap(map.get("exceptionObfuscation")));
        b.exceptionObfuscation(new ZipitConfig.ExceptionOptions(
                bool(exceptions, "enabled", false)));

        Map<String, Object> incremental = normalize(asMap(map.get("incremental")));
        b.incremental(new ZipitConfig.IncrementalOptions(
                bool(incremental, "enabled", false),
                str(incremental, "previousOutput", ""),
                str(incremental, "metadataFile", "zipit-incremental.tsv")));

        Map<String, Object> virtualization = normalize(asMap(map.get("methodVirtualization")));
        if (virtualization.isEmpty()) {
            virtualization = normalize(asMap(map.get("virtualization")));
        }
        b.methodVirtualization(new ZipitConfig.VirtualizationOptions(
                bool(virtualization, "enabled", false),
                intValue(virtualization, "maxInstructions", 2000),
                intValue(virtualization, "maxMethods", 0)));

        Map<String, Object> antiLlm = normalize(asMap(map.get("antiLlm")));
        b.antiLlm(new ZipitConfig.AntiLlmOptions(
                bool(antiLlm, "enabled", false),
                intValue(antiLlm, "decoyMethodsPerClass", 2),
                intValue(antiLlm, "decoyCallSitesPerClass", 2),
                intValue(antiLlm, "wrapperLayers", 1),
                intValue(antiLlm, "maxWrappedMethodsPerClass", 12),
                bool(antiLlm, "shieldVirtualization", true)));

        Map<String, Object> protection = normalize(asMap(map.get("protection")));
        b.antiDebug(bool(protection, "antiDebug", bool(map, "antiDebug", false)));

        ZipitConfig config = b.build();
        return Presets.apply(config);
    }

    private static Path resolve(Path base, String path) {
        Path p = Paths.get(path);
        if (p.isAbsolute() || base == null) {
            return p;
        }
        return base.resolve(p).normalize();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new LinkedHashMap<String, Object>();
    }

    /** Normalizes keys to lower camel case plus case/underscore-insensitive aliases. */
    private static Map<String, Object> normalize(Map<String, Object> raw) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        if (raw == null) {
            return out;
        }
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            out.put(canonical(e.getKey()), normalizeValue(e.getValue()));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Object normalizeValue(Object value) {
        if (value instanceof Map) {
            return normalize((Map<String, Object>) value);
        }
        if (value instanceof List) {
            List<Object> list = new ArrayList<Object>();
            for (Object item : (List<Object>) value) {
                list.add(normalizeValue(item));
            }
            return list;
        }
        return value;
    }

    private static String canonical(String key) {
        StringBuilder out = new StringBuilder();
        boolean upper = false;
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == '_' || c == '-' || c == ' ') {
                upper = true;
                continue;
            }
            if (out.length() == 0) {
                out.append(Character.toLowerCase(c));
            } else if (upper) {
                out.append(Character.toUpperCase(c));
                upper = false;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String str(Map<String, Object> map, String key, String def) {
        Object value = map.get(key);
        return value == null ? def : String.valueOf(value);
    }

    private static boolean bool(Map<String, Object> map, String key, boolean def) {
        Object value = map.get(key);
        if (value == null) {
            return def;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static int intValue(Map<String, Object> map, String key, int def) {
        Object value = map.get(key);
        if (value == null) {
            return def;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static long longValue(Map<String, Object> map, String key, long def) {
        Object value = map.get(key);
        if (value == null) {
            return def;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Map<String, Object> map, String key) {
        List<String> out = new ArrayList<String>();
        Object value = map.get(key);
        if (value == null) {
            return out;
        }
        if (value instanceof List) {
            for (Object item : (List<Object>) value) {
                if (item != null) {
                    out.add(String.valueOf(item));
                }
            }
        } else {
            out.add(String.valueOf(value));
        }
        return out;
    }
}
