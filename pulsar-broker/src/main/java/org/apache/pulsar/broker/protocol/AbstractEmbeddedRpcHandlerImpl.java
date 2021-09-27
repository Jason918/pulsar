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

package org.apache.pulsar.broker.protocol;

import io.netty.buffer.ByteBuf;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.EmbeddedRpcObject;
import org.apache.pulsar.common.api.proto.CommandEmbeddedRpcRequest;
import org.apache.pulsar.common.api.proto.CommandEmbeddedRpcResponse;

/**
 * Created by JiangHaiting@didichuxing.com on 2021/9/26.
 */
@Slf4j
public abstract class AbstractEmbeddedRpcHandlerImpl<ReqT extends EmbeddedRpcObject, RspT extends EmbeddedRpcObject>
        implements EmbeddedRpcHandler<ReqT, RspT> {

    protected abstract ReqT getRequestObject(ByteBuf payload);

    protected abstract RspT getResponseObject(ByteBuf payload);

    @Override
    public CompletableFuture<Pair<CommandEmbeddedRpcResponse, ByteBuf>> handle(CommandEmbeddedRpcRequest request,
                                                                               ByteBuf buffer) {
        if (log.isDebugEnabled()) {
            log.debug("handle request={}", request);
        }
        ReqT r = getRequestObject(buffer);
        long reqId = request.getRequestId();

        return handleRPCAsync(request.getTopic(), request.getSubscription(), r).thenApply(response -> {
            CommandEmbeddedRpcResponse cmdResponse = new CommandEmbeddedRpcResponse();
            cmdResponse.setResponseCode(0);
            cmdResponse.setRequestId(reqId);
            return Pair.of(cmdResponse, response.getPayload());
        });
    }

    @Override
    public CompletableFuture<RspT> callRPCAsync(Consumer<?> consumer, ReqT req) {
        return consumer.embeddedRpcAsync(getCode(), req.getPayload())
                .thenApply(response -> getResponseObject(response.getPayload()));
    }
}
