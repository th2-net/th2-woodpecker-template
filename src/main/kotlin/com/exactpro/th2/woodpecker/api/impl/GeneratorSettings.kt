/*
 * Copyright 2022 Exactpro (Exactpro Systems Limited)
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

import com.exactpro.th2.common.grpc.Direction
import com.exactpro.th2.woodpecker.api.IMessageGeneratorSettings

data class GeneratorSettings(
    val sessionAlias: String,
    val templates: Map<MessageTypes, MessageGeneratorSettings>
) : IMessageGeneratorSettings

data class MessageGeneratorSettings(
    val direction: Direction = Direction.SECOND,
    val fields: Map<String, Any?> = mapOf()
)