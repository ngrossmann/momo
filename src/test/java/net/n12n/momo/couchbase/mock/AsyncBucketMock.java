package net.n12n.momo.couchbase.mock;

import com.couchbase.client.java.AsyncBucket;
import com.couchbase.client.java.PersistTo;
import com.couchbase.client.java.ReplicaMode;
import com.couchbase.client.java.ReplicateTo;
import com.couchbase.client.java.bucket.AsyncBucketManager;
import com.couchbase.client.java.document.Document;
import com.couchbase.client.java.document.JsonDocument;
import com.couchbase.client.java.document.JsonLongDocument;
import com.couchbase.client.java.query.AsyncQueryResult;
import com.couchbase.client.java.query.Query;
import com.couchbase.client.java.view.AsyncViewResult;
import com.couchbase.client.java.view.ViewQuery;
import rx.Observable;

public class AsyncBucketMock implements AsyncBucket {
    @Override
    public String name() {
        return null;
    }

    @Override
    public Observable<JsonDocument> get(String s) {
        return null;
    }

    @Override
    public <D extends Document<?>> Observable<D> get(D d) {
        return null;
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
        return null;
    }

    @Override
    public Observable<AsyncQueryResult> query(Query query) {
        return null;
    }

    @Override
    public Observable<AsyncQueryResult> query(String s) {
        return null;
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
        return null;
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
}
