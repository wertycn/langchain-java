/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hw.langchain.output.parsers.structured;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hw.langchain.chat.models.openai.ChatOpenAI;
import com.hw.langchain.llms.openai.OpenAI;
import com.hw.langchain.prompts.chat.ChatPromptTemplate;
import com.hw.langchain.prompts.chat.HumanMessagePromptTemplate;
import com.hw.langchain.prompts.prompt.PromptTemplate;
import com.hw.langchain.schema.OutputParserException;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * <a href="https://python.langchain.com/docs/modules/model_io/output_parsers/structured">Structured output parser</a>
 *
 * @author HamaWhite
 */
class StructuredOutputParserTest {

    @Test
    void testParse() {
        var responseSchemas = List.of(
                new ResponseSchema("name", "desc"),
                new ResponseSchema("age", "desc"));
        var parser = StructuredOutputParser.fromResponseSchemas(responseSchemas);

        var text = "```json\n{\"name\": \"John\", \"age\": 30}\n```";
        var result = parser.parse(text);

        var expectedResult = new ObjectMapper().createObjectNode()
                .put("name", "John")
                .put("age", 30);

        assertEquals(expectedResult, result);
    }

    @Test
    void testInvalidJsonInput() {
        var responseSchemas = List.of(
                new ResponseSchema("name", "desc"),
                new ResponseSchema("age", "desc"));
        var parser = StructuredOutputParser.fromResponseSchemas(responseSchemas);

        var text = "```json\n{\"name\": \"John\"}\n```";
        assertThrows(OutputParserException.class, () -> parser.parse(text));
    }

    private StructuredOutputParser createOutputParser() {
        List<ResponseSchema> responseSchemas = List.of(
                new ResponseSchema("answer", "answer to the user's question"),
                new ResponseSchema("source", "source used to answer the user's question, should be a website."));
        return StructuredOutputParser.fromResponseSchemas(responseSchemas);
    }

    @Test
    @Disabled("Test requires costly OpenAI calls, can be run manually.")
    void testStructuredOutputParserWithLLM() {
        var outputParser = createOutputParser();
        var prompt = new PromptTemplate(
                "answer the users question as best as possible.\n{format_instructions}\n{question}",
                List.of("question"),
                Map.of("format_instructions", outputParser.getFormatInstructions()));

        var llm = OpenAI.builder().temperature(0).build().init();
        var input = prompt.formatPrompt(Map.of("question", "what's the capital of france?"));
        var output = llm.call(input.toString());

        var actual = outputParser.parse(output);
        var expected = new ObjectMapper().createObjectNode()
                .put("answer", "Paris")
                .put("source", "https://www.worldatlas.com/articles/what-is-the-capital-of-france.html");
        assertEquals(expected, actual);
    }

    @Test
    @Disabled("Test requires costly OpenAI calls, can be run manually.")
    void testStructuredOutputParserWithChatModel() {
        var outputParser = createOutputParser();

        var prompt = new ChatPromptTemplate(
                List.of("question"),
                List.of(HumanMessagePromptTemplate.fromTemplate(
                        "answer the users question as best as possible.\n{format_instructions}\n{question}")),
                Map.of("format_instructions", outputParser.getFormatInstructions()));

        var chatModel = ChatOpenAI.builder().temperature(0).build().init();

        var input = prompt.formatPrompt(Map.of("question", "what's the capital of france?"));
        var output = chatModel.call(input.toMessages());

        var actual = outputParser.parse(output.getContent());
        var expected = new ObjectMapper().createObjectNode()
                .put("answer", "The capital of France is Paris.")
                .put("source", "https://en.wikipedia.org/wiki/Paris");
        assertEquals(expected, actual);
    }
}