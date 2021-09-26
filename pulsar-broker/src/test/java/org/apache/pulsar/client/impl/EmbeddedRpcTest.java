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

package org.apache.pulsar.client.impl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.broker.protocol.AbstractEmbeddedRpcHandlerImpl;
import org.apache.pulsar.broker.service.BrokerTestBase;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.EmbeddedRpcObject;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@Slf4j
public class EmbeddedRpcTest extends BrokerTestBase {

    public static final int CODE = 1234;
    private RpcTestHandler handler;

    static class RpcTestHandler extends AbstractEmbeddedRpcHandlerImpl<RpcRequest, RpcResponse> {

        @Override
        public long getCode() {
            return CODE;
        }

        @Override
        public String getName() {
            return "RPC-TEST";
        }

        @Override
        public CompletableFuture<RpcResponse> handleRPCAsync(RpcRequest rpcRequest) {
            log.info("handleRPCAsync, req={}", rpcRequest);
            RpcResponse rsp = new RpcResponse();
            rsp.setCost(rpcRequest.getIndex() / 2);
            rsp.setActualNum(rpcRequest.getNum() / 2);
            rsp.setMsg(rpcRequest.getExtra() + rpcRequest.getExtra());
            return CompletableFuture.completedFuture(rsp);
        }

        @Override
        protected RpcRequest getRequestObject(ByteBuf payload) {
            RpcRequest r = new RpcRequest();
            r.parseFrom(payload);
            return r;
        }

        @Override
        protected RpcResponse getResponseObject(ByteBuf payload) {
            RpcResponse rsp = new RpcResponse();
            rsp.parseFrom(payload);
            return rsp;
        }

        public CompletableFuture<RpcResponse> callRpcAsync(Consumer<byte[]> consumer, long index, int num, String msg) {

            RpcRequest request = new RpcRequest();
            request.setIndex(index);
            request.setNum(num);
            request.setExtra(msg);
            return callRPCAsync(consumer, request);

        }
    }

    @Data
    @ToString
    static class RpcRequest implements EmbeddedRpcObject {
        long index;
        int num;
        String extra;

        @Override
        public ByteBuf getPayload() {
            ByteBuf payload = Unpooled.buffer();
            payload.writeLong(index);
            payload.writeInt(num);
            byte[] data = extra.getBytes(StandardCharsets.UTF_8);
            payload.writeInt(data.length);
            payload.writeBytes(data);
            return payload;
        }

        @Override
        public void parseFrom(ByteBuf payload) {
            index = payload.readLong();
            num = payload.readInt();
            int len = payload.readInt();
            extra = payload.readCharSequence(len, StandardCharsets.UTF_8).toString();
        }
    }

    @Data
    @ToString
    static class RpcResponse implements EmbeddedRpcObject {

        int actualNum;
        long cost;
        String msg;

        public ByteBuf getPayload() {
            ByteBuf payload = Unpooled.buffer();
            payload.writeInt(actualNum);
            payload.writeLong(cost);
            byte[] data = msg.getBytes(StandardCharsets.UTF_8);
            payload.writeInt(data.length);
            payload.writeBytes(data);
            return payload;
        }

        @Override
        public void parseFrom(ByteBuf payload) {
            actualNum = payload.readInt();
            cost = payload.readLong();
            int len = payload.readInt();
            msg = payload.readCharSequence(len, StandardCharsets.UTF_8).toString();
        }
    }


    @BeforeClass
    @Override
    protected void setup() throws Exception {
        super.baseSetup();
        handler = new RpcTestHandler();
        pulsar.getEmbeddedRpcHandlers().put(handler.getCode(), handler);
    }

    @Test
    public void testRpc() throws Exception {
        String topic = newTopicName();
        String sub = "sub-" + topic;

        Consumer<byte[]> consumer = pulsarClient.newConsumer().subscriptionName(sub).topic(topic).subscribe();


        int num = 3;
        long index = System.nanoTime();
        String msg = "testRPC-msg" + System.currentTimeMillis();
        RpcResponse response = handler.callRpcAsync(consumer, index, num, msg).get();


        Assert.assertNotNull(response);
        Assert.assertEquals(response.getActualNum(), num / 2);
        Assert.assertEquals(response.getCost(), index / 2);
        Assert.assertEquals(response.getMsg(), msg + msg);
    }

    @AfterClass
    @Override
    protected void cleanup() throws Exception {
        internalCleanup();
    }
}
