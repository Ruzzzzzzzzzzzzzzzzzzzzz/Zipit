package io.zipit.core.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Built-in policy presets.
 *
 * <p>Presets only add keep rules or relax transform defaults; explicit user
 * configuration still wins for scalar settings because presets are applied
 * after parsing and never remove user keep rules.</p>
 */
public final class Presets {

    private Presets() {
    }

    public static ZipitConfig apply(ZipitConfig base) throws ConfigException {
        return apply(base, true);
    }

    /**
     * Applies the preset named in the configuration, optionally tolerating a
     * configuration without {@code input}/{@code output} (CLI override loading).
     */
    public static ZipitConfig apply(ZipitConfig base, boolean requirePaths) throws ConfigException {
        if (base.preset() == null || base.preset().trim().isEmpty()) {
            return base;
        }
        return apply(base, base.preset(), requirePaths);
    }

    public static ZipitConfig apply(ZipitConfig base, String preset) throws ConfigException {
        return apply(base, preset, true);
    }

    public static ZipitConfig apply(ZipitConfig base, String preset, boolean requirePaths)
            throws ConfigException {
        String name = preset == null ? "" : preset.trim().toLowerCase(Locale.ROOT);
        if (name.isEmpty() || "default".equals(name) || "none".equals(name)) {
            return base;
        }
        ZipitConfig.Builder b = base.toBuilder();
        if ("library".equals(name)) {
            // Preserve public API names while still allowing string/constant/flow transforms.
            b.softKeepPublicApi(true);
            b.keep(merge(base.keep(), "@io.zipit.annotations.Keep"));
        } else if ("spring".equals(name) || "spring-boot".equals(name)) {
            b.softKeepPublicApi(true);
            List<String> keep = new ArrayList<String>(base.keep());
            keep.addAll(Arrays.asList(
                    "**Application",
                    "**Controller",
                    "**RestController",
                    "**Service",
                    "**Repository",
                    "**Configuration",
                    "**Properties",
                    "**AutoConfiguration",
                    "**WebMvcConfigurer",
                    "**Filter",
                    "**Interceptor",
                    "**Aspect",
                    "**Advice",
                    "**Listener",
                    "@org.springframework.**",
                    "@javax.annotation.**",
                    "@jakarta.annotation.**"));
            b.keep(dedupe(keep));
        } else if ("jackson".equals(name) || "persistence".equals(name)) {
            b.softKeepPublicApi(true);
            List<String> keep = new ArrayList<String>(base.keep());
            keep.addAll(Arrays.asList(
                    "@com.fasterxml.jackson.**",
                    "@javax.persistence.**",
                    "@jakarta.persistence.**",
                    "@javax.xml.bind.annotation.**",
                    "@jakarta.xml.bind.annotation.**",
                    "@org.hibernate.**",
                    "@org.hibernate.validator.**"));
            b.keep(dedupe(keep));
        } else if ("service-loader".equals(name) || "serviceloader".equals(name)) {
            b.softKeepPublicApi(true);
        } else if ("micronaut".equals(name)) {
            b.softKeepPublicApi(true);
            List<String> keep = new ArrayList<String>(base.keep());
            keep.addAll(Arrays.asList("**Controller", "**Factory", "**Configuration", "@io.micronaut.**"));
            b.keep(dedupe(keep));
        } else if ("aggressive".equals(name)) {
            b.keepPublicApi(false);
            b.flow(new ZipitConfig.FlowOptions(true, "normal",
                    base.flow().maxInstructions(), true));
            b.invokeWrapper(new ZipitConfig.WrapperOptions(true, base.invokeWrapper().maxPerClass()));
            b.junk(new ZipitConfig.JunkOptions(true, 4, 4));
        } else {
            throw new ConfigException("Unknown preset: " + preset
                    + " (expected library, spring-boot, jackson, service-loader, micronaut or aggressive)");
        }
        b.preset(name);
        return b.build(requirePaths);
    }

    private static List<String> dedupe(List<String> values) {
        return new ArrayList<String>(new java.util.LinkedHashSet<String>(values));
    }

    private static List<String> merge(List<String> existing, String... extra) {
        List<String> out = new ArrayList<String>(existing);
        for (String value : extra) {
            if (!out.contains(value)) {
                out.add(value);
            }
        }
        return out;
    }
}
