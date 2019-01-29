package org.mib.cochat.repo;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mib.common.validator.Validator.validateObjectNotNull;

@Slf4j
public abstract class InMemoryWithFSPersistenceRepository<K, V> extends InMemoryRepository<K, V> {

    private final String fsPath;
    private final PersistStrategy strategy;
    private final AtomicInteger editAccumulator;

    public InMemoryWithFSPersistenceRepository(final String fsPath, final PersistStrategy strategy) {
        validateObjectNotNull(fsPath, "persistent file path");
        validateObjectNotNull(strategy, "persist strategy");
        this.fsPath = fsPath;
        this.strategy = strategy;
        this.editAccumulator = strategy.getMaxEditsAllowedBetweenPersists() > 0 ? new AtomicInteger(0) : null;

        loadPersisted();

        if (strategy.getPeriodicalPersistIntervalSeconds() > 0) {
            int interval = strategy.getPeriodicalPersistIntervalSeconds();
            ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
            ses.scheduleAtFixedRate(this::persist, interval, interval, TimeUnit.SECONDS);
            Runtime.getRuntime().addShutdownHook(new Thread(ses::shutdown));
        }
        Runtime.getRuntime().addShutdownHook(new Thread(this::persist));
    }

    @Override
    public boolean store(K key, V value) {
        boolean result = super.store(key, value);
        accumulateAndPersistIfNecessary();
        return result;
    }

    @Override
    public boolean delete(K key) {
        boolean result = super.delete(key);
        accumulateAndPersistIfNecessary();
        return result;
    }

    private void accumulateAndPersistIfNecessary() {
        if (editAccumulator == null) return;
        int accumulated = editAccumulator.incrementAndGet();
        if (accumulated >= strategy.getMaxEditsAllowedBetweenPersists()) {
            editAccumulator.addAndGet(-accumulated);
            new Thread(this::persist).start();
        }
    }

    private synchronized void loadPersisted() {
        File file = new File(fsPath);
        if (!file.exists()) return;
        String line;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            while ((line = br.readLine()) != null) {
                if (StringUtils.isBlank(line)) continue;
                int separatorIndex = line.indexOf('\t');
                if (separatorIndex <= 0) continue;
                line = line.replaceAll("\\n", "\n");
                K key = fromSerKey(line.substring(0, separatorIndex));
                V value = fromSerValue(line.substring(separatorIndex + 1));
                map.put(key, value);
            }
        } catch (IOException e) {
            log.error("failed to load persisted from {}", fsPath, e);
            throw new RuntimeException(e);
        }
    }

    private synchronized void persist() {
        File tmp = new File(fsPath + "." + System.currentTimeMillis());
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(tmp))) {
            for (Map.Entry<K, V> kv : map.entrySet()) {
                String line = serKey(kv.getKey()) + '\t' + serValue(kv.getValue());
                bw.write(line.replaceAll("\n", "\\n"));
                bw.newLine();
            }
        } catch (IOException e) {
            log.error("failed to persist to {}", tmp.getAbsolutePath(), e);
            if (!tmp.delete()) log.error("failed to delete tmp file {}", tmp.getAbsolutePath());
            throw new RuntimeException(e);
        }
        File cur = new File(fsPath), origin = new File(fsPath + ".origin");
        if (cur.exists()) {
            if (!cur.renameTo(origin)) {
                log.error("failed to rename {} to {}", cur.getAbsolutePath(), origin.getAbsolutePath());
                throw new RuntimeException("unable to rename cur file " + cur.getAbsolutePath());
            }
        }
        if (!tmp.renameTo(cur)) {
            log.error("failed to rename {} to {}", tmp.getAbsolutePath(), cur.getAbsolutePath());
            if (!origin.renameTo(cur)) {
                log.error("failed to rename origin {} back to {}", origin.getAbsolutePath(), cur.getAbsolutePath());
                throw new RuntimeException("failed to roll back transaction for tmp " + tmp.getAbsolutePath());
            }
            throw new RuntimeException("unable to rename tmp file " + tmp.getAbsolutePath());
        }
        if (origin.exists()) {
            if (!origin.delete()) {
                log.error("failed to delete original file {}", origin.getAbsolutePath());
                throw new RuntimeException("unable to delete original file " + origin.getAbsolutePath());
            }
        }
    }

    protected abstract String serKey(K key);

    protected abstract String serValue(V value);

    protected abstract K fromSerKey(String serKey);

    protected abstract V fromSerValue(String serValue);
}
