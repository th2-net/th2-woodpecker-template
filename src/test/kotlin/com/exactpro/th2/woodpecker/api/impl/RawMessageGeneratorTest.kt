/*
 * Copyright 2023 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.woodpecker.api.impl

import com.exactpro.th2.common.schema.factory.AbstractCommonFactory
import com.exactpro.th2.common.schema.strategy.route.json.RoutingStrategyModule
import com.exactpro.th2.woodpecker.api.impl.raw.RandomGenerator
import com.exactpro.th2.woodpecker.api.impl.raw.RawMessageGenerator
import com.exactpro.th2.woodpecker.api.impl.raw.RawMessageGeneratorSettings
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RawMessageGeneratorTest {

    @Test
    fun test() {
        val generator = RawMessageGenerator(RawMessageGeneratorSettings("book", random = RandomGenerator()))
        val batch = generator.onNext(100)
        assertEquals(100, batch.groupsCount)
    }

    @Test
    fun `read settings test`() {
        val objectMapper = ObjectMapper().registerModules(
            KotlinModule.Builder()
                .withReflectionCacheSize(512)
                .configure(KotlinFeature.NullToEmptyCollection, false)
                .configure(KotlinFeature.NullToEmptyMap, false)
                .configure(KotlinFeature.NullIsSameAsDefault, false)
                .configure(KotlinFeature.SingletonSupport, false)
                .configure(KotlinFeature.StrictNullChecks, false)
                .build(),
            RoutingStrategyModule(AbstractCommonFactory.MAPPER),
            JavaTimeModule()
        )
        RawMessageGeneratorTest::class.java.classLoader.getResourceAsStream("custom-config.json").use {
            assertNotNull(objectMapper.readValue(it, RawMessageGeneratorSettings::class.java))
        }
    }
}