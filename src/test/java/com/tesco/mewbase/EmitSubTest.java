package com.tesco.mewbase;

import com.tesco.mewbase.bson.BsonObject;
import com.tesco.mewbase.client.*;
import com.tesco.mewbase.client.impl.ClientImpl;
import com.tesco.mewbase.common.SubDescriptor;
import com.tesco.mewbase.server.Server;
import com.tesco.mewbase.server.ServerOptions;
import com.tesco.mewbase.server.impl.ServerImpl;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by tim on 26/09/16.
 */
@RunWith(VertxUnitRunner.class)
public class EmitSubTest {

    private final static Logger log = LoggerFactory.getLogger(EmitSubTest.class);

    private static final String TEST_STREAM1 = "com.tesco.basket";
    private static final String TEST_EVENT_TYPE1 = "addItem";

    private Server server;
    private Client client;

    @Before
    public void before(TestContext context) throws Exception {
        log.trace("in before");
        server = new ServerImpl(new ServerOptions());
        CompletableFuture<Void> cfStart = server.start();
        cfStart.get();
        client = new ClientImpl();
    }

    @After
    public void after(TestContext context) throws Exception {
        log.trace("in after");
        client.close().get();
        server.stop().get();
    }

    @Test
    public void testSimpleEmitSubscribe(TestContext context) throws Exception {
        Connection conn = client.connect(new ConnectionOptions()).get();
        SubDescriptor descriptor = new SubDescriptor();
        descriptor.setStreamName(TEST_STREAM1);
        Subscription sub = conn.subscribe(descriptor).get();
        Producer prod = conn.createProducer(TEST_STREAM1);
        Async async = context.async();
        long now = System.currentTimeMillis();
        BsonObject sent = new BsonObject().put("foo", "bar");
        sub.setHandler(re -> {
            context.assertEquals(TEST_STREAM1, re.streamName());
            context.assertEquals(TEST_EVENT_TYPE1, re.eventType());
            context.assertEquals(0l, re.sequenceNumber());
            context.assertTrue(re.timeStamp() >= now);
            BsonObject event = re.event();
            context.assertEquals(sent, event);
            async.complete();
        });
        prod.emit(TEST_EVENT_TYPE1, sent).get();
    }

    @Test
    public void testSubscribeRetro(TestContext context) throws Exception {
        Connection conn = client.connect(new ConnectionOptions()).get();
        Producer prod = conn.createProducer(TEST_STREAM1);
        int numEvents = 10;
        for (int i = 0; i < numEvents; i++) {
            BsonObject event = new BsonObject().put("foo", "bar").put("num", i);
            CompletableFuture<Void> cf = prod.emit(TEST_EVENT_TYPE1, event);
            if (i == numEvents - 1) {
                cf.get();
            }
        }
        SubDescriptor descriptor = new SubDescriptor();
        descriptor.setStreamName(TEST_STREAM1);
        descriptor.setStartSeq(0);
        Subscription sub = conn.subscribe(descriptor).get();
        Async async = context.async();
        AtomicLong cnt = new AtomicLong();
        sub.setHandler(re -> {
            context.assertEquals(TEST_STREAM1, re.streamName());
            context.assertEquals(TEST_EVENT_TYPE1, re.eventType());
            long c = cnt.getAndIncrement();
            context.assertEquals(c, re.sequenceNumber());
            BsonObject event = re.event();
            context.assertEquals(c, (long)event.getInteger("num"));
            if (c == numEvents - 1) {
                async.complete();
            }
        });

    }
}