/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pulsar.broker.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.SubscriptionType;
import org.apache.pulsar.client.impl.MessageIdImpl;
import org.apache.pulsar.client.impl.MessageImpl;
import org.apache.pulsar.common.api.proto.BrokerEntryMetadata;
import org.assertj.core.util.Sets;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test for the broker entry metadata.
 */
@Slf4j
@Test(groups = "broker")
public class BrokerEntryMetadataE2ETest extends BrokerTestBase {


    @DataProvider(name = "subscriptionTypes")
    public static Object[] subscriptionTypes() {
        return new Object[] {
                SubscriptionType.Exclusive,
                SubscriptionType.Failover,
                SubscriptionType.Shared,
                SubscriptionType.Key_Shared
        };
    }

    @BeforeClass
    protected void setup() throws Exception {
        conf.setBrokerEntryMetadataInterceptors(Sets.newTreeSet(
                "org.apache.pulsar.common.intercept.AppendBrokerTimestampMetadataInterceptor",
                "org.apache.pulsar.common.intercept.AppendIndexMetadataInterceptor"
                ));
        baseSetup();
    }

    @AfterClass(alwaysRun = true)
    protected void cleanup() throws Exception {
        internalCleanup();
    }

    @Test(dataProvider = "subscriptionTypes")
    public void testProduceAndConsume(SubscriptionType subType) throws Exception {
        final String topic = newTopicName();
        final int messages = 10;

        @Cleanup
        Producer<byte[]> producer = pulsarClient.newProducer()
                .topic(topic)
                .create();

        @Cleanup
        Consumer<byte[]> consumer = pulsarClient.newConsumer()
                .topic(topic)
                .subscriptionType(subType)
                .subscriptionName("my-sub")
                .subscribe();

        for (int i = 0; i < messages; i++) {
            producer.send(String.valueOf(i).getBytes());
        }

        int receives = 0;
        for (int i = 0; i < messages; i++) {
            Message<byte[]> received = consumer.receive();
            ++ receives;
            Assert.assertEquals(i, Integer.valueOf(new String(received.getValue())).intValue());
        }

        Assert.assertEquals(messages, receives);
    }

    @Test(timeOut = 20000)
    public void testPeekMessage() throws Exception {
        final String topic = newTopicName();
        final String subscription = "my-sub";
        final long eventTime= 200;
        final long deliverAtTime = 300;

        @Cleanup
        Producer<byte[]> producer = pulsarClient.newProducer()
                .topic(topic)
                .create();

        long sendTime = System.currentTimeMillis();
        producer.newMessage()
                .eventTime(eventTime)
                .deliverAt(deliverAtTime)
                .value("hello".getBytes())
                .send();

        admin.topics().createSubscription(topic, subscription, MessageId.earliest);
        final List<Message<byte[]>> messages = admin.topics().peekMessages(topic, subscription, 1);
        Assert.assertEquals(messages.size(), 1);
        MessageImpl message = (MessageImpl) messages.get(0);
        Assert.assertEquals(message.getData(), "hello".getBytes());
        Assert.assertEquals(message.getEventTime(), eventTime);
        Assert.assertEquals(message.getDeliverAtTime(), deliverAtTime);
        Assert.assertTrue(message.getPublishTime() >= sendTime);

        BrokerEntryMetadata entryMetadata = message.getBrokerEntryMetadata();
        Assert.assertEquals(entryMetadata.getIndex(), 0);
        Assert.assertTrue(entryMetadata.getBrokerTimestamp() >= sendTime);
    }

    @Test(timeOut = 20000)
    public void testGetMessageById() throws Exception {
        final String topic = newTopicName();
        final String subscription = "my-sub";
        final long eventTime= 200;
        final long deliverAtTime = 300;

        @Cleanup
        Producer<byte[]> producer = pulsarClient.newProducer()
                .topic(topic)
                .create();

        long sendTime = System.currentTimeMillis();
        MessageIdImpl messageId = (MessageIdImpl) producer.newMessage()
                .eventTime(eventTime)
                .deliverAt(deliverAtTime)
                .value("hello".getBytes())
                .send();

        admin.topics().createSubscription(topic, subscription, MessageId.earliest);
        MessageImpl message = (MessageImpl) admin.topics()
                .getMessageById(topic, messageId.getLedgerId(), messageId.getEntryId());
        Assert.assertEquals(message.getData(), "hello".getBytes());
        Assert.assertEquals(message.getEventTime(), eventTime);
        Assert.assertEquals(message.getDeliverAtTime(), deliverAtTime);
        Assert.assertTrue(message.getPublishTime() >= sendTime);

        BrokerEntryMetadata entryMetadata = message.getBrokerEntryMetadata();
        Assert.assertEquals(entryMetadata.getIndex(), 0);
        Assert.assertTrue(entryMetadata.getBrokerTimestamp() >= sendTime);
    }

    @Test(timeOut = 20000)
    public void testGetMessageByIndex() throws Exception {
        final String topic = newTopicName();

        Producer<byte[]> producer = pulsarClient.newProducer()
                .topic(topic)
                .enableBatching(false)
                .create();

        byte[] data = ("hello" + System.currentTimeMillis()).getBytes();
        MessageIdImpl messageId = (MessageIdImpl) producer.newMessage()
                .value(data)
                .send();
        producer.close();

        admin.topics().createSubscription(topic, "sub", MessageId.earliest);
        MessageImpl<byte[]> message;
        message = (MessageImpl<byte[]>) admin.topics().getMessageByIndex(topic, 0);
        Assert.assertEquals(message.getMessageId(), messageId);
        Assert.assertEquals(message.getBrokerEntryMetadata().getIndex(), 0);

        log.info("------get index -1");
        Assert.assertNull(admin.topics().getMessageByIndex(topic, -1));
        log.info("------get index 1");
        Assert.assertNull(admin.topics().getMessageByIndex(topic, 1));

        producer = pulsarClient.newProducer()
                .topic(topic)
                .enableBatching(true)
                .batchingMaxPublishDelay(10, TimeUnit.MINUTES)
                .batchingMaxMessages(5)
                .create();
        List<CompletableFuture<MessageId>> messageIds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            messageIds.add(producer.newMessage()
                    .value(("hello" + i).getBytes())
                    .sendAsync());
        }
        messageIds.get(9).get();

        Assert.assertNull(admin.topics().getMessageByIndex(topic, -1));

        for (int i = 1; i <= 10; i++) {
            message = (MessageImpl<byte[]>) admin.topics().getMessageByIndex(topic, i);
            Assert.assertEquals(message.getMessageId(), messageIds.get(i - 1).get());
            Assert.assertEquals(message.getBrokerEntryMetadata().getIndex(), i);
        }
        Assert.assertNull(admin.topics().getMessageByIndex(topic, 11));

        producer.close();

    }


    @Test(timeOut = 20000)
    public void testExamineMessage() throws Exception {
        final String topic = newTopicName();
        final String subscription = "my-sub";
        final long eventTime = 200;
        final long deliverAtTime = 300;

        @Cleanup
        Producer<byte[]> producer = pulsarClient.newProducer()
                .topic(topic)
                .create();

        long sendTime = System.currentTimeMillis();
        producer.newMessage()
                .eventTime(eventTime)
                .deliverAt(deliverAtTime)
                .value("hello".getBytes())
                .send();

        admin.topics().createSubscription(topic, subscription, MessageId.earliest);
        MessageImpl message =
                (MessageImpl) admin.topics().examineMessage(topic, "earliest", 1);
        Assert.assertEquals(message.getData(), "hello".getBytes());
        Assert.assertEquals(message.getEventTime(), eventTime);
        Assert.assertEquals(message.getDeliverAtTime(), deliverAtTime);
        Assert.assertTrue(message.getPublishTime() >= sendTime);

        BrokerEntryMetadata entryMetadata = message.getBrokerEntryMetadata();
        Assert.assertEquals(entryMetadata.getIndex(), 0);
        Assert.assertTrue(entryMetadata.getBrokerTimestamp() >= sendTime);
    }

    @Test(timeOut = 20000)
    public void testGetLastMessageId() throws Exception {
        final String topic = "persistent://prop/ns-abc/topic-test";
        final String subscription = "my-sub";

        @Cleanup
        Producer<byte[]> producer = pulsarClient.newProducer()
                .topic(topic)
                .create();
        producer.newMessage().value("hello".getBytes()).send();

        @Cleanup
        Consumer<byte[]> consumer = pulsarClient.newConsumer()
                .topic(topic)
                .subscriptionType(SubscriptionType.Exclusive)
                .subscriptionName(subscription)
                .subscribe();
        consumer.getLastMessageId();
    }

    public void testGetLastIndex() throws Exception {
        final String topic = "persistent://prop/ns-abc/topic-testGetLastIndex";


        @Cleanup
        Producer<byte[]> producer = pulsarClient.newProducer()
                .topic(topic)
                .create();

        Assert.assertEquals(admin.topics().getInternalStats(topic).lastIndex, -1);
        producer.newMessage().value("hello".getBytes()).send();

        Assert.assertEquals(admin.topics().getInternalStats(topic).lastIndex, 0);

        producer.newMessage().value("hello".getBytes()).send();
        Assert.assertEquals(admin.topics().getInternalStats(topic).lastIndex, 1);
    }
}
