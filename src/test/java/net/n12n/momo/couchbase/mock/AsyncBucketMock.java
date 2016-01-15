/*
 * Copyright 2015 Niklas Grossmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.n12n.momo.couchbase.mock;

import com.couchbase.client.core.ClusterFacade;
import com.couchbase.client.deps.io.netty.buffer.ByteBuf;
import com.couchbase.client.java.AsyncBucket;
import com.couchbase.client.java.PersistTo;
import com.couchbase.client.java.ReplicaMode;
import com.couchbase.client.java.ReplicateTo;
import com.couchbase.client.java.bucket.AsyncBucketManager;
import com.couchbase.client.java.document.BinaryDocument;
import com.couchbase.client.java.document.Document;
import com.couchbase.client.java.document.JsonDocument;
import com.couchbase.client.java.document.JsonLongDocument;
import com.couchbase.client.java.document.json.JsonObject;
import com.couchbase.client.java.env.CouchbaseEnvironment;
import com.couchbase.client.java.query.AsyncN1qlQueryResult;
import com.couchbase.client.java.query.N1qlQuery;
import com.couchbase.client.java.query.Statement;
import com.couchbase.client.java.repository.AsyncRepository;
import com.couchbase.client.java.view.*;
import rx.Observable;

import java.util.HashMap;
import java.util.Map;

public class AsyncBucketMock implements AsyncBucket {
    private Map<String, BinaryDocument> data;
    private AsyncViewRow[] rows;

    public AsyncBucketMock() {
        data = new HashMap<>();
        rows = new AsyncViewRow[0];
    }

    public AsyncBucketMock(Map<String, BinaryDocument> data, String[] targets) {
        this.data = data;
        this.rows = new AsyncViewRow[targets.length];
        for (int i = 0; i < targets.length; i++) {
            this.rows[i] = new AsyncViewRowMock(targets[i]);
        }
    }

    public AsyncBucketMock(Map<String, BinaryDocument> data,
                           AsyncViewRow[] rows) {
        this.data = data;
        this.rows = rows;
    }

    @Override
    public String name() {
        return "mock-bucket";
    }

    @Override
    public Observable<ClusterFacade> core() {
        return null;
    }

    @Override
    public Observable<JsonDocument> get(String s) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<D> get(D d) {
        D doc = (D) data.get(d.id());
        if (doc == null) {
            return Observable.empty();
        } else {
            ((ByteBuf) doc.content()).resetReaderIndex();
            return Observable.just(doc);
        }
    }

    @Override
    public <D extends Document<?>> Observable<D> get(String s, Class<D> aClass) {
        return null;
    }

    @Override
    public Observable<JsonDocument> getFromReplica(String s, ReplicaMode replicaMode) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<D> getFromReplica(D d, ReplicaMode replicaMode) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<D> getFromReplica(String s, ReplicaMode replicaMode, Class<D> aClass) {
        return null;
    }

    @Override
    public Observable<JsonDocument> getAndLock(String s, int i) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<D> getAndLock(D d, int i) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<D> getAndLock(String s, int i, Class<D> aClass) {
        return null;
    }

    @Override
    public Observable<JsonDocument> getAndTouch(String s, int i) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<D> getAndTouch(D d) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<D> getAndTouch(String s, int i, Class<D> aClass) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<D> insert(D d) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<D> insert(D d, PersistTo persistTo, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<D> insert(D d, PersistTo persistTo) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<D> insert(D d, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<D> upsert(D d) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<D> upsert(D d, PersistTo persistTo, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<D> upsert(D d, PersistTo persistTo) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<D> upsert(D d, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<D> replace(D d) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<D> replace(D d, PersistTo persistTo, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<D> replace(D d, PersistTo persistTo) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<D> replace(D d, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<D> remove(D d) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<D> remove(D d, PersistTo persistTo, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<D> remove(D d, PersistTo persistTo) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<D> remove(D d, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public Observable<JsonDocument> remove(String s) {
        return null;
    }

    @Override
    public Observable<JsonDocument> remove(String s, PersistTo persistTo, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public Observable<JsonDocument> remove(String s, PersistTo persistTo) {
        return null;
    }

    @Override
    public Observable<JsonDocument> remove(String s, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<D> remove(String s, Class<D> aClass) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<D> remove(String s, PersistTo persistTo, ReplicateTo replicateTo, Class<D> aClass) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<D> remove(String s, PersistTo persistTo, Class<D> aClass) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<D> remove(String s, ReplicateTo replicateTo, Class<D> aClass) {
        return null;
    }

    @Override
    public Observable<AsyncViewResult> query(ViewQuery viewQuery) {
        return Observable.just(new AsyncViewResult() {
            @Override
            public Observable<AsyncViewRow> rows() {
                return Observable.from(rows);
            }

            @Override
            public int totalRows() {
                return 0;
            }

            @Override
            public boolean success() {
                return false;
            }

            @Override
            public Observable<JsonObject> error() {
                return null;
            }

            @Override
            public JsonObject debug() {
                return null;
            }
        });
    }

    @Override
    public Observable<Boolean> unlock(String s, long l) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<Boolean> unlock(D d) {
        return null;
    }

    @Override
    public Observable<Boolean> touch(String s, int i) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<Boolean> touch(D d) {
        return null;
    }

    @Override
    public Observable<JsonLongDocument> counter(String s, long l) {
        return null;
    }

    @Override
    public Observable<JsonLongDocument> counter(String s, long l, long l1) {
        return null;
    }

    @Override
    public Observable<JsonLongDocument> counter(String s, long l, long l1, int i) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<D> append(D d) {
        return Observable.just(d);
    }

    @Override
    public <D extends Document<?>> Observable<D> prepend(D d) {
        return null;
    }

    @Override
    public Observable<AsyncBucketManager> bucketManager() {
        return null;
    }

    @Override
    public Observable<Boolean> close() {
        return null;
    }

    @Override
    public CouchbaseEnvironment environment() {
        return null;
    }

    @Override
    public Observable<Boolean> exists(String s) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<Boolean> exists(D d) {
        return null;
    }

    @Override
    public Observable<AsyncSpatialViewResult> query(SpatialViewQuery spatialViewQuery) {
        return null;
    }

    @Override
    public Observable<AsyncN1qlQueryResult> query(Statement statement) {
        return null;
    }

    @Override
    public Observable<AsyncN1qlQueryResult> query(N1qlQuery n1qlQuery) {
        return null;
    }

    @Override
    public Observable<JsonLongDocument> counter(String s, long l, PersistTo persistTo) {
        return null;
    }

    @Override
    public Observable<JsonLongDocument> counter(String s, long l, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public Observable<JsonLongDocument> counter(String s, long l, PersistTo persistTo, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public Observable<JsonLongDocument> counter(String s, long l, long l1, PersistTo persistTo) {
        return null;
    }

    @Override
    public Observable<JsonLongDocument> counter(String s, long l, long l1, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public Observable<JsonLongDocument> counter(String s, long l, long l1, PersistTo persistTo, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public Observable<JsonLongDocument> counter(String s, long l, long l1, int i, PersistTo persistTo) {
        return null;
    }

    @Override
    public Observable<JsonLongDocument> counter(String s, long l, long l1, int i, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public Observable<JsonLongDocument> counter(String s, long l, long l1, int i, PersistTo persistTo, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<D> append(D d, PersistTo persistTo) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<D> append(D d, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<D> append(D d, PersistTo persistTo, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<D> prepend(D d, PersistTo persistTo) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<D> prepend(D d, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<D> prepend(D d, PersistTo persistTo, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public Observable<Integer> invalidateQueryCache() {
        return null;
    }

    @Override
    public Observable<AsyncRepository> repository() {
        return null;
    }

    public static class AsyncViewRowMock implements AsyncViewRow {
        String key;
        Object value;

        public AsyncViewRowMock(String key, Object value) {
            this.key = key;
            this.value = value;
        }

        public AsyncViewRowMock(String key) {
            this(key, null);
        }

        @Override
        public String id() {
            return key;
        }

        @Override
        public Object key() {
            return key;
        }

        @Override
        public Object value() {
            return value;
        }

        @Override
        public Observable<JsonDocument> document() {
            return null;
        }

        @Override
        public <D extends Document<?>> Observable<D> document(Class<D> aClass) {
            return null;
        }
    }
}
