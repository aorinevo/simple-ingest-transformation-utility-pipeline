package com.amazon.situp.plugins.sink.elasticsearch;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.common.util.concurrent.EsRejectedExecutionException;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.shard.ShardId;
import org.junit.Test;

import java.io.IOException;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BulkRetryStrategyTests {
    @Test
    public void testCanRetry() {
        final BulkRetryStrategy bulkRetryStrategy = new BulkRetryStrategy(
                bulkRequest -> new BulkResponse(new BulkItemResponse[bulkRequest.requests().size()], 10),
                (docWriteRequest, throwable) -> {}, BulkRequest::new);
        final String testIndex = "foo";
        final BulkItemResponse bulkItemResponse1 = successItemResponse(testIndex);
        final BulkItemResponse bulkItemResponse2 = badRequestItemResponse(testIndex);
        BulkResponse response = new BulkResponse(
                new BulkItemResponse[]{bulkItemResponse1, bulkItemResponse2}, 10);
        assertFalse(bulkRetryStrategy.canRetry(response));

        final BulkItemResponse bulkItemResponse3 = tooManyRequestItemResponse(testIndex);
        response = new BulkResponse(
                new BulkItemResponse[]{bulkItemResponse1, bulkItemResponse3}, 10);
        assertTrue(bulkRetryStrategy.canRetry(response));

        final BulkItemResponse bulkItemResponse4 = internalServerErrorItemResponse(testIndex);
        response = new BulkResponse(
                new BulkItemResponse[]{bulkItemResponse2, bulkItemResponse4}, 10);
        assertTrue(bulkRetryStrategy.canRetry(response));
    }

    @Test
    public void testExecute() throws Exception {
        final String testIndex = "bar";
        final FakeClient client = new FakeClient(testIndex);
        final FakeLogger logger = new FakeLogger();
        final BulkRetryStrategy bulkRetryStrategy = new BulkRetryStrategy(
                client::bulk, logger::logFailure, BulkRequest::new);
        final BulkRequest testBulkRequest = new BulkRequest();
        testBulkRequest.add(new IndexRequest(testIndex).id("1"));
        testBulkRequest.add(new IndexRequest(testIndex).id("2"));
        testBulkRequest.add(new IndexRequest(testIndex).id("3"));
        testBulkRequest.add(new IndexRequest(testIndex).id("4"));

        bulkRetryStrategy.execute(testBulkRequest);

        assertEquals(3, client.attempt);
        assertEquals(2, client.finalResponse.getItems().length);
        assertFalse(client.finalResponse.hasFailures());
        assertEquals("3", client.finalRequest.requests().get(0).id());
        assertEquals("4", client.finalRequest.requests().get(1).id());
        assertTrue(logger.msg.contains("[bar][_doc][2]"));
        assertFalse(logger.msg.contains("[bar][_doc][1]"));
    }

    private static BulkItemResponse successItemResponse(final String index) {
        final String docId = UUID.randomUUID().toString();
        return new BulkItemResponse(1, DocWriteRequest.OpType.INDEX,
                new IndexResponse(new ShardId(new Index(index, "fakeUUID"), 1),
                        "_doc", docId, 1, 1, 1, true));
    }

    private static BulkItemResponse badRequestItemResponse(final String index) {
        final String docId = UUID.randomUUID().toString();
        return new BulkItemResponse(1, DocWriteRequest.OpType.INDEX,
                new BulkItemResponse.Failure(index, "_doc", docId,
                        new IllegalArgumentException()));
    }

    private static BulkItemResponse tooManyRequestItemResponse(final String index) {
        final String docId = UUID.randomUUID().toString();
        return new BulkItemResponse(1, DocWriteRequest.OpType.INDEX,
                new BulkItemResponse.Failure(index, "_doc", docId,
                        new EsRejectedExecutionException()));
    }

    private static BulkItemResponse internalServerErrorItemResponse(final String index) {
        final String docId = UUID.randomUUID().toString();
        return new BulkItemResponse(1, DocWriteRequest.OpType.INDEX,
                new BulkItemResponse.Failure(index, "_doc", docId,
                        new IllegalAccessException()));
    }

    private static class FakeClient {

        int attempt = 0;
        String index;
        BulkRequest finalRequest;
        BulkResponse finalResponse;

        public FakeClient(final String index) {
            this.index = index;
        }

        public BulkResponse bulk(final BulkRequest bulkRequest) throws IOException {
            finalRequest = bulkRequest;
            final int requestSize = bulkRequest.requests().size();
            if (attempt == 0) {
                attempt++;
                throw new IOException();
            } else if (attempt == 1) {
                attempt++;
                throw new ElasticsearchException(new EsRejectedExecutionException());
            } else if (attempt == 2) {
                assert requestSize == 4;
                attempt++;
                finalResponse = bulkFirstResponse(bulkRequest);
                return finalResponse;
            } else {
                assert requestSize == 2;
                finalResponse = bulkSecondResponse(bulkRequest);
                return finalResponse;
            }
        }

        private BulkResponse bulkFirstResponse(final BulkRequest bulkRequest) {
            final int requestSize = bulkRequest.requests().size();
            assert requestSize == 4;
            final BulkItemResponse[] bulkItemResponses = new BulkItemResponse[]{
                    successItemResponse(index), badRequestItemResponse(index), internalServerErrorItemResponse(index),
                    tooManyRequestItemResponse(index)};
            return new BulkResponse(bulkItemResponses, 10);
        }

        private BulkResponse bulkSecondResponse(final BulkRequest bulkRequest) {
            final int requestSize = bulkRequest.requests().size();
            assert requestSize == 2;
            final BulkItemResponse[] bulkItemResponses = new BulkItemResponse[]{
                    successItemResponse(index), successItemResponse(index)};
            return new BulkResponse(bulkItemResponses, 10);
        }
    }

    private static class FakeLogger {
        String msg;

        public void logFailure(final DocWriteRequest<?> docWriteRequest, final Throwable t) {
            msg = String.format("Document [%s] has failure: %s", docWriteRequest.toString(), t);
        }
    }
}
