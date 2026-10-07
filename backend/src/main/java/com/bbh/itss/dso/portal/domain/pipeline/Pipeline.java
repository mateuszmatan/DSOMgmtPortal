package com.bbh.itss.dso.portal.domain.pipeline;

import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class Pipeline {

    public static final String REPLACED_REASON = "Replaced by a new key";
    public static final String UNKNOWN_KEY = "Unknown DevSecOps pipeline key";

    private final Long id;
    private final ServiceRef service;
    private final PipelineType type;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;
    private PipelineSettings settings;
    private List<PipelineKey> keys;

    private Pipeline(Long id, ServiceRef service, PipelineType type, PipelineSettings settings, List<PipelineKey> keys,
                     long version, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.service = Objects.requireNonNull(service, "a pipeline belongs to a service");
        this.type = Objects.requireNonNull(type, "a pipeline needs its type");
        this.settings = Objects.requireNonNull(settings, "a pipeline needs its settings").forType(type);
        this.keys = List.copyOf(keys);
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        if (this.keys.stream().filter(PipelineKey::isActive).count() > 1) {
            throw new IllegalArgumentException("a pipeline has at most one active key");
        }
    }

    public static Pipeline create(ServiceRef service, PipelineType type, PipelineSettings settings, KeyGenerator generator,
                                  Instant now) {
        Pipeline pipeline = new Pipeline(null, service, type, valid(settings), List.of(), 0, null, null);
        pipeline.issueKey(generator, now);
        return pipeline;
    }

    public static Pipeline restore(Long id, ServiceRef service, PipelineType type, PipelineSettings settings,
                                   List<PipelineKey> keys, long version, Instant createdAt, Instant updatedAt) {
        return new Pipeline(id, service, type, settings, keys, version, createdAt, updatedAt);
    }

    public void reconfigure(PipelineType requestedType, PipelineSettings requestedSettings) {
        if (requestedType != type) {
            throw new IllegalStateException("The type of a pipeline cannot change; add a new pipeline instead");
        }
        this.settings = valid(Objects.requireNonNull(requestedSettings, "a pipeline needs its settings")).forType(type);
    }

    private static PipelineSettings valid(PipelineSettings settings) {
        ValidationProblems problems = new ValidationProblems();
        if (settings != null) {
            settings.validate(problems);
        }
        problems.throwIfAny();
        return settings;
    }

    public PipelineKey issueKey(KeyGenerator generator, Instant now) {
        List<PipelineKey> history = new ArrayList<>();
        PipelineKey issued = PipelineKey.issue(generator.newKey(), now);
        history.add(issued);
        keys.forEach(key -> history.add(key.isActive() ? key.revoke(REPLACED_REASON, now) : key));
        keys = List.copyOf(history);
        return issued;
    }

    public PipelineKey revokeActiveKey(String reason, Instant now) {
        PipelineKey active = activeKey()
                .orElseThrow(() -> new IllegalStateException("The pipeline has no active key to invalidate"));
        PipelineKey revoked = active.revoke(reason, now);
        keys = keys.stream().map(key -> key == active ? revoked : key).toList();
        return revoked;
    }

    public Optional<PipelineKey> activeKey() {
        return keys.stream().filter(PipelineKey::isActive).findFirst();
    }

    public boolean isEnabled() {
        return activeKey().isPresent();
    }

    public Long id() {
        return id;
    }

    public ServiceRef service() {
        return service;
    }

    public PipelineType type() {
        return type;
    }

    public PipelineSettings settings() {
        return settings;
    }

    public List<PipelineKey> keys() {
        return keys;
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
