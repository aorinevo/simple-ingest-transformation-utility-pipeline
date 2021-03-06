package com.amazon.situp.plugins.source.oteltrace;

import com.amazon.situp.model.configuration.PluginSetting;
import com.amazon.situp.model.record.Record;
import com.amazon.situp.plugins.buffer.BlockingBuffer;
import com.amazon.situp.plugins.source.oteltracesource.OTelTraceSource;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.linecorp.armeria.client.Clients;
import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.*;
import io.grpc.StatusRuntimeException;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class OTelTraceSourceTest {

    private static OTelTraceSource SOURCE;

    private static String getUri() {
        return "gproto+http://127.0.0.1:" + SOURCE.getoTelTraceSourceConfig().getPort() + '/';
    }

    @BeforeAll
    private static void beforeEach() {
        final HashMap<String, Object> integerHashMap = new HashMap<>();
        integerHashMap.put("request_timeout", 1);
        SOURCE = new OTelTraceSource(new PluginSetting("otel_trace_source", integerHashMap));
        SOURCE.start(getBuffer());
        addOneRequest();
    }

    @AfterAll
    private static void afterEach() {
        SOURCE.stop();
    }

    private static BlockingBuffer<Record<ExportTraceServiceRequest>> getBuffer() {
        final HashMap<String, Object> integerHashMap = new HashMap<>();
        integerHashMap.put("buffer_size", 1);
        return new BlockingBuffer<>(new PluginSetting("blocking_buffer", integerHashMap));
    }


    static void addOneRequest() {
        final TraceServiceGrpc.TraceServiceBlockingStub client = Clients.newClient(getUri(), TraceServiceGrpc.TraceServiceBlockingStub.class);
        client.export(ExportTraceServiceRequest.newBuilder().build());
    }

    @Test
    void testBufferFull() {
        final TraceServiceGrpc.TraceServiceBlockingStub client = Clients.newClient(getUri(), TraceServiceGrpc.TraceServiceBlockingStub.class);
        try {
            client.export(ExportTraceServiceRequest.newBuilder().build());
        } catch (RuntimeException ex) {
            System.out.println("Printing the exception:" + ex);
        }
    }

    @Test
    void testHttpFullJson() throws InvalidProtocolBufferException {
        final AggregatedHttpResponse res = WebClient.of().execute(RequestHeaders.builder()
                        .scheme(SessionProtocol.HTTP)
                        .authority("127.0.0.1:21890")
                        .method(HttpMethod.POST)
                        .path("/opentelemetry.proto.collector.trace.v1.TraceService/Export")
                        .contentType(MediaType.JSON_UTF_8)
                        .build(),
                HttpData.copyOf(JsonFormat.printer().print(ExportTraceServiceRequest.newBuilder().build()).getBytes())).aggregate().join();
        System.out.println("Printing the response code:" + res.status().codeAsText());
    }

    @Test
    void testHttpFullBytes() {
        final AggregatedHttpResponse res = WebClient.of().execute(RequestHeaders.builder()
                        .scheme(SessionProtocol.HTTP)
                        .authority("127.0.0.1:21890")
                        .method(HttpMethod.POST)
                        .path("/opentelemetry.proto.collector.trace.v1.TraceService/Export")
                        .contentType(MediaType.PROTOBUF)
                        .build(),
                HttpData.copyOf(ExportTraceServiceRequest.newBuilder().build().toByteArray())).aggregate().join();
        System.out.println("Printing the response code:" + res.status().codeAsText());
    }
}
