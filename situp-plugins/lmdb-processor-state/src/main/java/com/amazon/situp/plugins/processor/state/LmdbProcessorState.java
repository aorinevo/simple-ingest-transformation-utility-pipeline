package com.amazon.situp.plugins.processor.state;

import com.amazon.situp.processor.state.ProcessorState;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.lmdbjava.Cursor;
import org.lmdbjava.CursorIterable;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.Txn;

public class LmdbProcessorState<T> implements ProcessorState<byte[], T> {
    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final Dbi<ByteBuffer> db;
    private final Env<ByteBuffer> env;
    private final Class<T> clazz; //Needed for deserialization

    /**
     * Constructor for LMDB processor state. See LMDB-Java for more info:
     * https://github.com/lmdbjava/lmdbjava
     * @param dbPath The directory in which to store the LMDB data files
     * @param dbName Name of the database
     * @param clazz Class type for value storage
     */
    public LmdbProcessorState(final File dbPath, final String dbName, final Class<T> clazz) {
        //TODO: These need to be configurable
        env = Env.create()
                .setMapSize(10_485_760)
                .setMaxDbs(1)
                .setMaxReaders(10)
                .open(dbPath, EnvFlags.MDB_NOTLS);
        db = env.openDbi(dbName, DbiFlags.MDB_CREATE);
        this.clazz = clazz;
    }

    private ByteBuffer toDirectByteBuffer(final byte[] in) {
        return ByteBuffer.allocateDirect(in.length).put(in).flip();
    }

    private T byteBufferToObject(final ByteBuffer valueBuffer) {
        try {
            final byte[] arr = new byte[valueBuffer.remaining()];
            valueBuffer.get(arr);
            valueBuffer.rewind();
            return OBJECT_MAPPER.readValue(arr, clazz);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public void put(byte[] key, T value) {
        try {
            final ByteBuffer keyBuffer = toDirectByteBuffer(key);
            final ByteBuffer valueBuffer = toDirectByteBuffer(OBJECT_MAPPER.writeValueAsBytes(value));
            db.put(keyBuffer, valueBuffer);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    //TODO: Test performance with single puts as above, and also with a putAll function which takes in a batch
    // of items to put into the lmdb

    @Override
    public T get(byte[] key) {
        try (Txn<ByteBuffer> txn = env.txnRead()) {
            final ByteBuffer value = db.get(txn, toDirectByteBuffer(key));
            if (value == null) {
                return null;
            }
            return byteBufferToObject(value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] getBytes(ByteBuffer bb) {
        bb.rewind();
        byte[] b = new byte[bb.remaining()];
        bb.get(b);
        bb.rewind();
        return b;
    }

    @Override
    public Map<byte[], T> getAll() {
        try (final Txn<ByteBuffer> txn = env.txnRead()) {
            final Map<byte[], T> dbMap = new HashMap<>();
            db.iterate(txn).iterator().forEachRemaining(byteBufferKeyVal -> {
                dbMap.put(
                        getBytes(byteBufferKeyVal.key()),
                        byteBufferToObject(byteBufferKeyVal.val()));
            });

            return dbMap;
        }
    }

    @Override
    public void clear() {
        //TODO: we can delete and recreate a new DB
        try (final Txn<ByteBuffer> txn = env.txnWrite()) {
            db.drop(txn, true);
        }
    }

    @Override
    public<R> List<R> iterate(BiFunction<byte[], T, R> fn) {
        try (Txn<ByteBuffer> txn = env.txnRead()) {
            final List<R> returnVal = new ArrayList<>();
            for (CursorIterable.KeyVal<ByteBuffer> byteBufferKeyVal : db.iterate(txn)) {
                final R val = fn.apply(getBytes(byteBufferKeyVal.key()),
                        byteBufferToObject(byteBufferKeyVal.val()));
                returnVal.add(val);
            }
            return returnVal;
        }
    }

    @Override
    public long size() {
        try (Txn<ByteBuffer> txn = env.txnRead()) {
            return db.stat(txn).entries;
        }
    }

    /**
     * LMDB specific iterate function, which iterates over an index range using the LMDB cursor
     * @param fn Function to apply to elements
     * @param start Start index
     * @param end End index
     * @param <R> Result type
     * @return List of R objects representing the application of the function to the elements in the index range
     */
    public<R> List<R> iterate(BiFunction<byte[], T, R> fn, final long start, final long end) {
        try (Txn<ByteBuffer> txn = env.txnRead()) {
            Cursor<ByteBuffer> cursor = db.openCursor(txn);
            final List<R> returnVal = new ArrayList<>();
            cursor.first();
            //TODO: Look into faster way to move cursor up N elements
            for(long i=0; i<start; i++) {
                cursor.next();
            }
            for(long i=start; i<end; i++) {
                final R val = fn.apply(getBytes(cursor.key()),
                        byteBufferToObject(cursor.val()));
                returnVal.add(val);
                if(!cursor.next()) {
                    break;
                }
            }
            cursor.close();
            return returnVal;
        }
    }

    @Override
    public void close() {
        env.close();
    }
}
