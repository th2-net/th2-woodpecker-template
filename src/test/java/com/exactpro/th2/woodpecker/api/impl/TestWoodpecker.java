/*
 * Copyright 2021-2021 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exactpro.th2.woodpecker.api.impl;

import com.exactpro.th2.common.grpc.Message;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Objects;

public class TestWoodpecker {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestWoodpecker.class);
    public static final int NUMBER_OF_MESSAGES = 25;

    @BeforeEach
    void beforeTest() {
    }

    @Test
    void testGeneration() {
        ClassLoader loader = getClass().getClassLoader();
        File fileYAML = new File(Objects.requireNonNull(loader.getResource("wood.yml")).getFile());
        Assertions.assertTrue(fileYAML.exists(), "File not found");
        MessageGeneratorSettings messageGeneratorSettings = null;
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            messageGeneratorSettings = mapper.readValue(fileYAML, new TypeReference<>() {});
        } catch (Exception e) {
            e.printStackTrace();
        }
        Assertions.assertNotNull(messageGeneratorSettings, "MessageGeneratorSettings is NULL");
        LOGGER.info("MessageGeneratorSettings = {} - {}", messageGeneratorSettings.getMessageType(), messageGeneratorSettings.getFields());
        MessageGenerator messageGenerator = new MessageGenerator(messageGeneratorSettings);

        for (int x = 0; x < NUMBER_OF_MESSAGES; x++) {
            Message msg = messageGenerator.onNext().getMessages(0).getMessage();
            LOGGER.info("msgType = {}, msgMAP = \n{}", msg.getMetadata().getMessageType(), msg.getFieldsMap());
        }

    }

}
