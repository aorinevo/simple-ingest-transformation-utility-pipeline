package com.amazon.situp.plugins.source;

import com.amazon.situp.model.PluginType;
import com.amazon.situp.model.annotations.SitupPlugin;
import com.amazon.situp.model.buffer.Buffer;
import com.amazon.situp.model.configuration.Configuration;
import com.amazon.situp.model.record.Record;
import com.amazon.situp.model.source.Source;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SitupPlugin(name = "test-source", type = PluginType.SOURCE)
public class TestSource implements Source<Record<String>> {
    public static final List<Record<String>> TEST_DATA = Stream.of("THIS", "IS", "TEST", "DATA")
            .map(Record::new).collect(Collectors.toList());
    private static final int WRITE_TIMEOUT = 5_000;
    private boolean isStopRequested;

    public TestSource(final Configuration configuration) {
        this();
    }

    public TestSource() {
        isStopRequested = false;
    }

    @Override
    public void start(Buffer<Record<String>> buffer) {
        final Iterator<Record<String>> iterator = TEST_DATA.iterator();
            while (iterator.hasNext() && !isStopRequested) {
                try {
                    buffer.write(iterator.next(), WRITE_TIMEOUT);
                } catch (TimeoutException e) {
                    throw new RuntimeException("Timed out writing to buffer");
                }
            }
    }

    @Override
    public void stop() {
        isStopRequested = true;
    }
}
