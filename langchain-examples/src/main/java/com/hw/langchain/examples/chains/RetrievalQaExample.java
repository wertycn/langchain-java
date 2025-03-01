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

package com.hw.langchain.examples.chains;

import com.hw.langchain.chains.retrieval.qa.base.RetrievalQA;
import com.hw.langchain.document.loaders.text.TextLoader;
import com.hw.langchain.embeddings.openai.OpenAIEmbeddings;
import com.hw.langchain.llms.openai.OpenAI;
import com.hw.langchain.text.splitter.CharacterTextSplitter;
import com.hw.langchain.vectorstores.pinecone.Pinecone;
import com.hw.pinecone.PineconeClient;
import com.hw.pinecone.entity.index.CreateIndexRequest;
import com.hw.pinecone.entity.index.IndexDescription;

import org.awaitility.Awaitility;

import java.time.Duration;

import static com.hw.langchain.chains.question.answering.ChainType.STUFF;
import static com.hw.langchain.examples.utils.PrintUtils.println;

/**
 * <a href="https://python.langchain.com/docs/modules/chains/popular/vector_db_qa">Retrieval QA</a>
 * <p>
 * export PINECONE_API_KEY=xxx
 * export PINECONE_ENV=xxx
 *
 * @author HamaWhite
 */
public class RetrievalQaExample {

    public static final String INDEX_NAME = "langchain-demo";

    public static void main(String[] args) {
        var filePath = "docs/extras/modules/state_of_the_union.txt";
        var loader = new TextLoader(filePath);
        var documents = loader.load();
        var textSplitter = CharacterTextSplitter.builder().chunkSize(1000).chunkOverlap(0).build();
        var docs = textSplitter.splitDocuments(documents);

        var client = PineconeClient.builder().requestTimeout(30).build().init();

        createPineconeIndex(client);

        var embeddings = OpenAIEmbeddings.builder().requestTimeout(60).build().init();
        var pinecone = Pinecone.builder().client(client).indexName(INDEX_NAME)
                .embeddingFunction(embeddings::embedQuery).build().init();
        pinecone.fromDocuments(docs, embeddings);

        var llm = OpenAI.builder().temperature(0).requestTimeout(30).build().init();
        var qa = RetrievalQA.fromChainType(llm, STUFF, pinecone.asRetriever());

        var query = "What did the president say about Ketanji Brown Jackson";
        var result = qa.run(query);
        println(result);
    }

    /**
     * If the index does not exist, it creates a new index with the specified name and dimension.
     * It also waits until the index is ready before returning.
     */
    private static void createPineconeIndex(PineconeClient client) {
        if (!client.listIndexes().contains(INDEX_NAME)) {
            // the text-embedding-ada-002 model has an output dimension of 1536.
            var request = CreateIndexRequest.builder()
                    .name(INDEX_NAME)
                    .dimension(1536)
                    .build();
            client.createIndex(request);

            awaitIndexReady(client);
        }
    }

    private static void awaitIndexReady(PineconeClient client) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(120))
                .pollInterval(Duration.ofSeconds(5))
                .until(() -> {
                    IndexDescription indexDescription = client.describeIndex(INDEX_NAME);
                    return indexDescription != null && indexDescription.getStatus().isReady();
                });
    }
}
