/*
 * Copyright 2021-2023 Exactpro (Exactpro Systems Limited)
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

import com.exactpro.th2.woodpecker.api.IGeneratorFactory
import com.exactpro.th2.woodpecker.api.IMessageGeneratorContext
import com.exactpro.th2.woodpecker.api.event.IEventGeneratorContext
import com.exactpro.th2.woodpecker.api.impl.event.EventGenerator
import com.exactpro.th2.woodpecker.api.impl.raw.RawMessageGenerator

@Suppress("unused")
class GeneratorFactory : IGeneratorFactory<GeneratorSettings> {
    override val settingsClass = GeneratorSettings::class.java
    override fun createMessageGenerator(context: IMessageGeneratorContext<GeneratorSettings>): RawMessageGenerator = RawMessageGenerator(context.settings.messageGeneratorSettings)
    override fun createEventGenerator(context: IEventGeneratorContext<GeneratorSettings>): EventGenerator = EventGenerator(context.settings.eventGeneratorSettings)

}