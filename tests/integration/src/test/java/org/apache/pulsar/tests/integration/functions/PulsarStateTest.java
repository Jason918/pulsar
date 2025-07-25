/*
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
package org.apache.pulsar.tests.integration.functions;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.pulsar.tests.integration.functions.utils.CommandGenerator.JAVAJAR;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;
import static org.testng.Assert.fail;
import com.google.common.base.Utf8;
import com.google.gson.Gson;
import java.util.Base64;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionType;
import org.apache.pulsar.common.functions.FunctionState;
import org.apache.pulsar.common.policies.data.SinkStatus;
import org.apache.pulsar.common.policies.data.SourceStatus;
import org.apache.pulsar.tests.integration.docker.ContainerExecException;
import org.apache.pulsar.tests.integration.docker.ContainerExecResult;
import org.apache.pulsar.tests.integration.functions.utils.CommandGenerator;
import org.apache.pulsar.tests.integration.functions.utils.CommandGenerator.Runtime;
import org.apache.pulsar.tests.integration.suites.PulsarStandaloneTestSuite;
import org.apache.pulsar.tests.integration.topologies.PulsarCluster;
import org.awaitility.Awaitility;
import org.testng.annotations.Test;

/**
 * State related test cases.
 */
@Slf4j
public class PulsarStateTest extends PulsarStandaloneTestSuite {

    public static final String WORDCOUNT_PYTHON_CLASS =
        "wordcount_function.WordCountFunction";

    public static final String WORDCOUNT_PYTHON_FILE = "wordcount_function.py";

    public static final String VALUE_BASE64 = "0a8001127e0a172e6576656e74732e437573746f6d65724372656174656412630a243"
                                              + "2336366666263652d623038342d346631352d616565342d326330643135356131666"
                                              + "36312026e311a3700000000000000000000000000000000000000000000000000000"
                                              + "000000000000000000000000000000000000000000000000000000000";

    @Test(groups = {"python_state", "state", "function", "python_function"})
    public void testPythonWordCountFunction() throws Exception {
        String functionName = "test-wordcount-py-fn-" + randomName(8);
        doTestPythonWordCountFunction(functionName);

        // after a function is deleted, its state should be clean
        // we just recreate and test the word count function again, and it should have same result
        doTestPythonWordCountFunction(functionName);
    }

    private void doTestPythonWordCountFunction(String functionName) throws Exception {
        String inputTopicName = "test-wordcount-py-input-" + randomName(8);
        String outputTopicName = "test-wordcount-py-output-" + randomName(8);

        final int numMessages = 10;
        // submit the exclamation function
        submitExclamationFunction(
                Runtime.PYTHON, inputTopicName, outputTopicName, functionName);

        // get function info
        getFunctionInfoSuccess(functionName);

        // publish and consume result
        publishAndConsumeMessages(inputTopicName, outputTopicName, numMessages);

        // get function status
        getFunctionStatus(functionName, numMessages);

        // get state
        queryState(functionName, "hello", numMessages, numMessages - 1);
        queryState(functionName, "test", numMessages, numMessages - 1);
        for (int i = 0; i < numMessages; i++) {
            queryState(functionName, "message-" + i, 1, 0);
        }

        // test put state
        String state = "{\"key\":\"test-string\",\"stringValue\":\"test value\"}";
        String expect = "\"stringValue\": \"test value\"";
        putAndQueryState(functionName, "test-string", state, expect);

        String numberState = "{\"key\":\"test-number\",\"numberValue\":20}";
        String expectNumber = "\"numberValue\": 20";
        putAndQueryState(functionName, "test-number", numberState, expectNumber);

        byte[] valueBytes = Base64.getDecoder().decode(VALUE_BASE64);
        String bytesString = Base64.getEncoder().encodeToString(valueBytes);
        String byteState = "{\"key\":\"test-bytes\",\"byteValue\":\"" + bytesString + "\"}";
        putAndQueryStateByte(functionName, "test-bytes", byteState, valueBytes);

        String valueStr = "hello pulsar";
        byte[] valueStrBytes = valueStr.getBytes(UTF_8);
        String bytesStrString = Base64.getEncoder().encodeToString(valueStrBytes);
        String byteStrState = "{\"key\":\"test-str-bytes\",\"byteValue\":\"" + bytesStrString + "\"}";
        putAndQueryState(functionName, "test-str-bytes", byteStrState, valueStr);

        String byteStrStateWithEmptyValues = "{\"key\":\"test-str-bytes\",\"byteValue\":\"" + bytesStrString + "\","
                + "\"stringValue\":\"\",\"numberValue\":0}";
        putAndQueryState(functionName, "test-str-bytes", byteStrStateWithEmptyValues, valueStr);

        // delete function
        deleteFunction(functionName);

        // get function info
        getFunctionInfoNotFound(functionName);
    }

    @Test(groups = {"java_state", "state", "function", "java_function"})
    public void testSourceState() throws Exception {
        String outputTopicName = "test-state-source-output-" + randomName(8);
        String sourceName = "test-state-source-" + randomName(8);

        submitSourceConnector(sourceName, outputTopicName, "org.apache.pulsar.tests.integration.io.TestStateSource",
                JAVAJAR);

        // get source info
        getSourceInfoSuccess(sourceName);

        // get source status
        getSourceStatus(sourceName);

        try (PulsarAdmin admin = PulsarAdmin.builder().serviceHttpUrl(container.getHttpServiceUrl()).build()) {

            Awaitility.await().ignoreExceptions().untilAsserted(() -> {
                SourceStatus status = admin.sources().getSourceStatus("public", "default", sourceName);
                assertEquals(status.getInstances().size(), 1);
                assertTrue(status.getInstances().get(0).getStatus().numWritten > 0);
            });

            {
                FunctionState functionState =
                        admin.functions().getFunctionState("public", "default", sourceName, "initial");
                assertEquals(functionState.getStringValue(), "val1");
            }

            // query a non-exist key should get a 404 error
            {
                PulsarAdminException e = expectThrows(PulsarAdminException.class, () -> {
                    admin.functions().getFunctionState("public", "default", sourceName, "non-exist");
                });
                assertEquals(e.getStatusCode(), 404);
            }

            // query a non-exist instance should get a 404 error
            {
                PulsarAdminException e = expectThrows(PulsarAdminException.class, () -> {
                    admin.functions().getFunctionState("public", "default", "non-exist", "non-exist");
                });
                assertEquals(e.getStatusCode(), 404);
            }

            Awaitility.await().ignoreExceptions().untilAsserted(() -> {
                FunctionState functionState =
                        admin.functions().getFunctionState("public", "default", sourceName, "now");
                assertTrue(functionState.getStringValue().matches("val1-.*"));
            });
        }

        // delete source
        deleteSource(sourceName);

        getSourceInfoNotFound(sourceName);
    }

    @Test(groups = {"java_state", "state", "function", "java_function"})
    public void testSinkState() throws Exception {
        String inputTopicName = "test-state-sink-input-" + randomName(8);
        String sinkName = "test-state-sink-" + randomName(8);
        int numMessages = 10;

        submitSinkConnector(sinkName, inputTopicName, "org.apache.pulsar.tests.integration.io.TestStateSink",  JAVAJAR);

        // get sink info
        getSinkInfoSuccess(sinkName);

        // get sink status
        getSinkStatus(sinkName);

        try (PulsarAdmin admin = PulsarAdmin.builder().serviceHttpUrl(container.getHttpServiceUrl()).build()) {

            // java supports schema
            @Cleanup PulsarClient client = PulsarClient.builder()
                    .serviceUrl(container.getPlainTextServiceUrl())
                    .build();
            @Cleanup Producer<String> producer = client.newProducer(Schema.STRING)
                    .topic(inputTopicName)
                    .create();

            {
                FunctionState functionState =
                        admin.functions().getFunctionState("public", "default", sinkName, "initial");
                assertEquals(functionState.getStringValue(), "val1");
            }

            // query a non-exist key should get a 404 error
            {
                PulsarAdminException e = expectThrows(PulsarAdminException.class, () -> {
                    admin.functions().getFunctionState("public", "default", sinkName, "non-exist");
                });
                assertEquals(e.getStatusCode(), 404);
            }

            // query a non-exist instance should get a 404 error
            {
                PulsarAdminException e = expectThrows(PulsarAdminException.class, () -> {
                    admin.functions().getFunctionState("public", "default", "non-exist", "non-exist");
                });
                assertEquals(e.getStatusCode(), 404);
            }

            for (int i = 0; i < numMessages; i++) {
                producer.send("foo");
            }

            Awaitility.await().ignoreExceptions().untilAsserted(() -> {
                SinkStatus status = admin.sinks().getSinkStatus("public", "default", sinkName);
                assertEquals(status.getInstances().size(), 1);
                assertTrue(status.getInstances().get(0).getStatus().numWrittenToSink > 0);
            });

            Awaitility.await().ignoreExceptions().untilAsserted(() -> {
                FunctionState functionState = admin.functions().getFunctionState("public", "default", sinkName, "now");
                assertEquals(functionState.getStringValue(), String.format("val1-%d", numMessages - 1));
            });
        }

        // delete source
        deleteSink(sinkName);

        getSinkInfoNotFound(sinkName);
    }

    @Test(groups = {"python_state", "state", "function", "python_function"})
    public void testNonExistFunction() throws Exception {
        String functionName = "non-exist-function-" + randomName(8);
        try (PulsarAdmin admin = PulsarAdmin.builder().serviceHttpUrl(container.getHttpServiceUrl()).build()) {
            // query a non-exist instance should get a 404 error
            {
                PulsarAdminException e = expectThrows(PulsarAdminException.class, () -> {
                    admin.functions().getFunctionState("public", "default", functionName, "non-exist");
                });
                assertEquals(e.getStatusCode(), 404);
            }
        }
    }

    @Test(groups = {"java_state", "state", "function", "java_function"})
    public void testBytes2StringNotUTF8() {
        byte[] valueBytes = Base64.getDecoder().decode(VALUE_BASE64);
        assertFalse(Utf8.isWellFormed(valueBytes));
        assertNotEquals(valueBytes, new String(valueBytes, UTF_8).getBytes(UTF_8));
    }

    @Test(groups = {"java_state", "state", "function", "java_function"})
    public void testSourceByteState() throws Exception {
        String outputTopicName = "test-state-source-output-" + randomName(8);
        String sourceName = "test-state-source-" + randomName(8);

        submitSourceConnector(sourceName, outputTopicName,
                "org.apache.pulsar.tests.integration.io.TestByteStateSource", JAVAJAR);

        // get source info
        getSourceInfoSuccess(sourceName);

        // get source status
        getSourceStatus(sourceName);

        try (PulsarAdmin admin = PulsarAdmin.builder().serviceHttpUrl(container.getHttpServiceUrl()).build()) {

            Awaitility.await().ignoreExceptions().untilAsserted(() -> {
                SourceStatus status = admin.sources().getSourceStatus("public", "default", sourceName);
                assertEquals(status.getInstances().size(), 1);
                assertTrue(status.getInstances().get(0).getStatus().numWritten > 0);
            });

            {
                FunctionState functionState =
                        admin.functions().getFunctionState("public", "default", sourceName, "initial");
                assertNull(functionState.getStringValue());
                assertEquals(functionState.getByteValue(), Base64.getDecoder().decode(VALUE_BASE64));
            }

            Awaitility.await().ignoreExceptions().untilAsserted(() -> {
                FunctionState functionState =
                        admin.functions().getFunctionState("public", "default", sourceName, "now");
                assertNull(functionState.getStringValue());
                assertEquals(functionState.getByteValue(), Base64.getDecoder().decode(VALUE_BASE64));
            });
        }

        // delete source
        deleteSource(sourceName);

        getSourceInfoNotFound(sourceName);
    }

    private void submitSourceConnector(String sourceName,
                                         String outputTopicName,
                                         String className,
                                         String archive) throws Exception {
        String[] commands = {
                PulsarCluster.ADMIN_SCRIPT,
                "sources", "create",
                "--name", sourceName,
                "--destinationTopicName", outputTopicName,
                "--archive", archive,
                "--classname", className
        };
        log.info("Run command : {}", StringUtils.join(commands, ' '));
        ContainerExecResult result = container.execCmd(commands);
        assertTrue(
                result.getStdout().contains("Created successfully"),
                result.getStdout());
    }

    private void submitSinkConnector(String sinkName,
                                         String inputTopicName,
                                         String className,
                                         String archive) throws Exception {
        String[] commands = {
                PulsarCluster.ADMIN_SCRIPT,
                "sinks", "create",
                "--name", sinkName,
                "--inputs", inputTopicName,
                "--archive", archive,
                "--classname", className
        };
        log.info("Run command : {}", StringUtils.join(commands, ' '));
        ContainerExecResult result = container.execCmd(commands);
        assertTrue(
                result.getStdout().contains("Created successfully"),
                result.getStdout());
    }

    private void submitExclamationFunction(Runtime runtime,
                                                  String inputTopicName,
                                                  String outputTopicName,
                                                  String functionName) throws Exception {
        submitFunction(
            runtime,
            inputTopicName,
            outputTopicName,
            functionName,
            getExclamationClass(runtime),
            Schema.BYTES);
    }

    protected static String getExclamationClass(Runtime runtime) {
        if (Runtime.PYTHON == runtime) {
            return WORDCOUNT_PYTHON_CLASS;
        } else {
            throw new IllegalArgumentException("Unsupported runtime : " + runtime);
        }
    }

    private <T> void submitFunction(Runtime runtime,
                                           String inputTopicName,
                                           String outputTopicName,
                                           String functionName,
                                           String functionClass,
                                           Schema<T> inputTopicSchema) throws Exception {
        CommandGenerator generator;
        generator = CommandGenerator.createDefaultGenerator(inputTopicName, functionClass);
        generator.setSinkTopic(outputTopicName);
        generator.setFunctionName(functionName);
        String command;
        if (Runtime.JAVA == runtime) {
            command = generator.generateCreateFunctionCommand();
        } else if (Runtime.PYTHON == runtime) {
            generator.setRuntime(runtime);
            command = generator.generateCreateFunctionCommand(WORDCOUNT_PYTHON_FILE);
        } else {
            throw new IllegalArgumentException("Unsupported runtime : " + runtime);
        }
        String[] commands = {
            "sh", "-c", command
        };
        ContainerExecResult result = container.execCmd(
            commands);
        assertTrue(result.getStdout().contains("Created successfully"));

        ensureSubscriptionCreated(inputTopicName, String.format("public/default/%s", functionName), inputTopicSchema);
    }

    @SuppressWarnings("try")
    private <T> void ensureSubscriptionCreated(String inputTopicName,
                                                      String subscriptionName,
                                                      Schema<T> inputTopicSchema)
            throws Exception {
        // ensure the function subscription exists before we start producing messages
        try (PulsarClient client = PulsarClient.builder()
            .serviceUrl(container.getPlainTextServiceUrl())
            .build()) {
            try (Consumer<T> ignored = client.newConsumer(inputTopicSchema)
                .topic(inputTopicName)
                .subscriptionType(SubscriptionType.Shared)
                .subscriptionName(subscriptionName)
                .subscribe()) {
            }
        }
    }

    private void getSinkInfoSuccess(String sinkName) throws Exception {
        ContainerExecResult result = container.execCmd(
                PulsarCluster.ADMIN_SCRIPT,
                "sinks",
                "get",
                "--tenant", "public",
                "--namespace", "default",
                "--name", sinkName
        );
        assertTrue(result.getStdout().contains("\"name\": \"" + sinkName + "\""));
    }

    private void getSourceInfoSuccess(String sourceName) throws Exception {
        ContainerExecResult result = container.execCmd(
                PulsarCluster.ADMIN_SCRIPT,
                "sources",
                "get",
                "--tenant", "public",
                "--namespace", "default",
                "--name", sourceName
        );
        assertTrue(result.getStdout().contains("\"name\": \"" + sourceName + "\""));
    }

    private void getFunctionInfoSuccess(String functionName) throws Exception {
        ContainerExecResult result = container.execCmd(
            PulsarCluster.ADMIN_SCRIPT,
            "functions",
            "get",
            "--tenant", "public",
            "--namespace", "default",
            "--name", functionName
        );
        assertTrue(result.getStdout().contains("\"name\": \"" + functionName + "\""));
    }

    private void getFunctionInfoNotFound(String functionName) throws Exception {
        try {
            container.execCmd(
                    PulsarCluster.ADMIN_SCRIPT,
                    "functions",
                    "get",
                    "--tenant", "public",
                    "--namespace", "default",
                    "--name", functionName);
            fail("Command should have exited with non-zero");
        } catch (ContainerExecException e) {
            assertTrue(e.getResult().getStderr().contains("Reason: Function " + functionName + " doesn't exist"));
        }
    }

    private void getSinkStatus(String sinkName) throws Exception {
        ContainerExecResult result = container.execCmd(
                PulsarCluster.ADMIN_SCRIPT,
                "sinks",
                "status",
                "--tenant", "public",
                "--namespace", "default",
                "--name", sinkName
        );
        assertTrue(result.getStdout().contains("\"running\" : true"));
    }

    private void getSourceStatus(String sourceName) throws Exception {
        ContainerExecResult result = container.execCmd(
                PulsarCluster.ADMIN_SCRIPT,
                "sources",
                "status",
                "--tenant", "public",
                "--namespace", "default",
                "--name", sourceName
        );
        assertTrue(result.getStdout().contains("\"running\" : true"));
    }

    private void getFunctionStatus(String functionName, int numMessages) throws Exception {
        ContainerExecResult result = container.execCmd(
            PulsarCluster.ADMIN_SCRIPT,
            "functions",
            "getstatus",
            "--tenant", "public",
            "--namespace", "default",
            "--name", functionName
        );
        assertTrue(result.getStdout().contains("\"running\" : true"));
        assertTrue(result.getStdout().contains("\"numSuccessfullyProcessed\" : " + numMessages));
    }

    private void queryState(String functionName, String key, int amount, long version)
        throws Exception {
        ContainerExecResult result = container.execCmd(
            PulsarCluster.ADMIN_SCRIPT,
            "functions",
            "querystate",
            "--tenant", "public",
            "--namespace", "default",
            "--name", functionName,
            "--key", key
        );
        assertTrue(result.getStdout().contains("\"numberValue\": " + amount));
        assertTrue(result.getStdout().contains("\"version\": " + version));
        assertFalse(result.getStdout().contains("stringValue"));
        assertFalse(result.getStdout().contains("byteValue"));
    }

    private void putAndQueryState(String functionName, String key, String state, String expect)
            throws Exception {
        container.execCmd(
                PulsarCluster.ADMIN_SCRIPT,
                "functions",
                "putstate",
                "--tenant", "public",
                "--namespace", "default",
                "--name", functionName,
                "--state", state
        );

        ContainerExecResult result = container.execCmd(
                PulsarCluster.ADMIN_SCRIPT,
                "functions",
                "querystate",
                "--tenant", "public",
                "--namespace", "default",
                "--name", functionName,
                "--key", key
        );
        assertTrue(result.getStdout().contains(expect));
    }

    private void putAndQueryStateByte(String functionName, String key, String state, byte[] expect)
            throws Exception {
        container.execCmd(
                PulsarCluster.ADMIN_SCRIPT,
                "functions",
                "putstate",
                "--tenant", "public",
                "--namespace", "default",
                "--name", functionName,
                "--state", state
        );

        ContainerExecResult result = container.execCmd(
                PulsarCluster.ADMIN_SCRIPT,
                "functions",
                "querystate",
                "--tenant", "public",
                "--namespace", "default",
                "--name", functionName,
                "--key", key
        );

        FunctionState byteState = new Gson().fromJson(result.getStdout(), FunctionState.class);
        assertNull(byteState.getStringValue());
        assertEquals(byteState.getByteValue(), expect);
    }

    private void publishAndConsumeMessages(String inputTopic,
                                                  String outputTopic,
                                                  int numMessages) throws Exception {
        @Cleanup PulsarClient client = PulsarClient.builder()
            .serviceUrl(container.getPlainTextServiceUrl())
            .build();
        @Cleanup Consumer<byte[]> consumer = client.newConsumer(Schema.BYTES)
            .topic(outputTopic)
            .subscriptionType(SubscriptionType.Exclusive)
            .subscriptionName("test-sub")
            .subscribe();
        @Cleanup Producer<byte[]> producer = client.newProducer(Schema.BYTES)
            .topic(inputTopic)
            .create();

        for (int i = 0; i < numMessages; i++) {
            producer.send(("hello test message-" + i).getBytes(UTF_8));
        }

        for (int i = 0; i < numMessages; i++) {
            Message<byte[]> msg = consumer.receive();
            assertEquals("hello test message-" + i + "!", new String(msg.getValue(), UTF_8));
        }
    }

    private void deleteFunction(String functionName) throws Exception {
        ContainerExecResult result = container.execCmd(
            PulsarCluster.ADMIN_SCRIPT,
            "functions",
            "delete",
            "--tenant", "public",
            "--namespace", "default",
            "--name", functionName
        );
        assertTrue(result.getStdout().contains("Deleted successfully"));
        result.assertNoStderr();
    }

    private void deleteSource(String sourceName) throws Exception {
        ContainerExecResult result = container.execCmd(
                PulsarCluster.ADMIN_SCRIPT,
                "sources",
                "delete",
                "--tenant", "public",
                "--namespace", "default",
                "--name", sourceName
        );
        assertTrue(result.getStdout().contains("Delete source successfully"));
        result.assertNoStderr();
    }

    private void deleteSink(String sinkName) throws Exception {
        ContainerExecResult result = container.execCmd(
                PulsarCluster.ADMIN_SCRIPT,
                "sinks",
                "delete",
                "--tenant", "public",
                "--namespace", "default",
                "--name", sinkName
        );
        assertTrue(result.getStdout().contains("Deleted successfully"));
        result.assertNoStderr();
    }

    private void getSourceInfoNotFound(String sourceName) throws Exception {
        try {
            container.execCmd(
                    PulsarCluster.ADMIN_SCRIPT,
                    "sources",
                    "get",
                    "--tenant", "public",
                    "--namespace", "default",
                    "--name", sourceName);
            fail("Command should have exited with non-zero");
        } catch (ContainerExecException e) {
            assertTrue(e.getResult().getStderr().contains("Reason: Source " + sourceName + " doesn't exist"));
        }
    }

    private void getSinkInfoNotFound(String sinkName) throws Exception {
        try {
            container.execCmd(
                    PulsarCluster.ADMIN_SCRIPT,
                    "sinks",
                    "get",
                    "--tenant", "public",
                    "--namespace", "default",
                    "--name", sinkName);
            fail("Command should have exited with non-zero");
        } catch (ContainerExecException e) {
            assertTrue(e.getResult().getStderr().contains("Reason: Sink " + sinkName + " doesn't exist"));
        }
    }

}
