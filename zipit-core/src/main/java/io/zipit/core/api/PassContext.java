package io.zipit.core.api;

import io.zipit.core.config.ZipitConfig;
import io.zipit.core.engine.MappingSet;
import io.zipit.core.engine.Stats;
import io.zipit.core.model.ClassPool;
import io.zipit.core.model.Hierarchy;
import io.zipit.core.rules.RuleSet;
import io.zipit.core.util.Log;

import java.util.Random;

/** Shared, mutable state for one Zipit run. */
public final class PassContext {

    private final ZipitConfig config;
    private final ClassPool classes;
    private final Hierarchy hierarchy;
    private final RuleSet rules;
    private final RuntimeSupport runtime;
    private final MappingSet mappings;
    private final Stats stats;
    private final Log log;
    private final Random random;
    private final ProgressListener progress;

    public PassContext(ZipitConfig config, ClassPool classes, Hierarchy hierarchy, RuleSet rules,
                       RuntimeSupport runtime, MappingSet mappings, Stats stats, Log log, Random random,
                       ProgressListener progress) {
        this.config = config;
        this.classes = classes;
        this.hierarchy = hierarchy;
        this.rules = rules;
        this.runtime = runtime;
        this.mappings = mappings;
        this.stats = stats;
        this.log = log;
        this.random = random;
        this.progress = progress;
    }

    public ZipitConfig config() { return config; }
    public ClassPool classes() { return classes; }
    public Hierarchy hierarchy() { return hierarchy; }
    public RuleSet rules() { return rules; }
    public RuntimeSupport runtime() { return runtime; }
    public MappingSet mappings() { return mappings; }
    public Stats stats() { return stats; }
    public Log log() { return log; }
    public Random random() { return random; }
    public ProgressListener progress() { return progress; }
}
