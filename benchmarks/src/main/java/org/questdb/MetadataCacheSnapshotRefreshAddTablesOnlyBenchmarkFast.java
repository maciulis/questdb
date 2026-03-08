/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2026 QuestDB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package org.questdb;

import com.sun.management.HotSpotDiagnosticMXBean;
import io.questdb.cairo.CairoConfiguration;
import io.questdb.cairo.CairoEngine;
import io.questdb.cairo.CairoTable;
import io.questdb.cairo.DefaultCairoConfiguration;
import io.questdb.cairo.MetadataCacheReader;
import io.questdb.cairo.TableToken;
import io.questdb.griffin.SqlException;
import io.questdb.griffin.SqlExecutionContext;
import io.questdb.griffin.SqlExecutionContextImpl;
import io.questdb.std.CharSequenceObjHashMap;
import io.questdb.std.str.CharSink;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode({Mode.Throughput})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class MetadataCacheSnapshotRefreshAddTablesOnlyBenchmarkFast {
    public static final String max_tables_10_000 = "10000";
    private final CairoConfiguration configuration = new DefaultCairoConfiguration(".");
    private CairoEngine engine;
    private CharSequenceObjHashMap<CairoTable> cache = new CharSequenceObjHashMap<>();
    private CharSequenceObjHashMap<CairoTable> tempTableMap = new CharSequenceObjHashMap<>();
    private List<CairoTable> tables = new ArrayList<>();

    @Param({"100", "1000", max_tables_10_000})
    public String size;

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(MetadataCacheSnapshotRefreshAddTablesOnlyBenchmarkFast.class.getSimpleName())
                .warmupIterations(5)
                .warmupTime(TimeValue.seconds(6))
                .measurementTime(TimeValue.seconds(5))
                .measurementIterations(5)
                .forks(1)
                .jvmArgs("-Xms2G", "-Xmx2G")
                .build();

        new Runner(opt).run();
    }

    @Benchmark
    public void testCacheRefresh(Blackhole blackhole) {
        cache.clear();
        try (MetadataCacheReaderDecorator metadataCacheReader = new MetadataCacheReaderDecorator(engine.getMetadataCache().readLock())) {
            metadataCacheReader.pullFromCairoTablesIntoCache(cache, tempTableMap);
            blackhole.consume(cache);
        }
    }

    @Setup(Level.Trial)
    public void preCalculateTables() {
        engine = new CairoEngine(configuration);
        System.out.println("Max heap: " + Runtime.getRuntime().maxMemory() / (1024 * 1024) + "MB");
        for (int i = 0; i < Integer.parseInt(size); i++) {
            tables.add(new CairoTable(new TableToken("table" + i, "", "", i, false, false, false, false, false, true)));
        }
    }

    @Setup(Level.Iteration)
    public void iterationSetup() {
        int max = Integer.parseInt(size);
        for (int i = 0; i < max; i++) {
            CairoTable table = tables.get(i);
            tempTableMap.put(table.getTableName(), table);
        }
    }

    @TearDown(Level.Trial)
    public void trialTearDown() throws SqlException {
        engine.close();
    }

    private static class MetadataCacheReaderDecorator implements MetadataCacheReader {

        private final MetadataCacheReader metadataCacheReader;

        MetadataCacheReaderDecorator(MetadataCacheReader metadataCacheReader) {
            this.metadataCacheReader = metadataCacheReader;
        }

        @Override
        public void close() {
            metadataCacheReader.close();
        }

        @Override
        public @Nullable CairoTable getTable(@NotNull TableToken tableToken) {
            return null;
        }

        @Override
        public int getTableCount() {
            return 0;
        }

        @Override
        public long getVersion() {
            return 0;
        }

        @Override
        public boolean isVisibleTable(@NotNull CharSequence tableName) {
            return false;
        }

        @Override
        public long snapshot(CharSequenceObjHashMap<CairoTable> localCache, long priorVersion) {
            return 0;
        }

        @Override
        public void toSink(@NotNull CharSink<?> sink) {

        }

        @Override
        @CompilerControl(CompilerControl.Mode.DONT_INLINE)
        public void pullFromCairoTablesIntoCache(CharSequenceObjHashMap<CairoTable> localCache, CharSequenceObjHashMap<CairoTable> tempTableMap) {
            metadataCacheReader.pullFromCairoTablesIntoCache(localCache, tempTableMap);
        }
    }
}
