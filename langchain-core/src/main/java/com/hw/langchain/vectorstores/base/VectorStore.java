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

package com.hw.langchain.vectorstores.base;

import com.hw.langchain.embeddings.base.Embeddings;
import com.hw.langchain.schema.Document;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static com.hw.langchain.vectorstores.base.SearchType.SIMILARITY;

/**
 * @author HamaWhite
 */
public abstract class VectorStore {

    private static final Logger LOG = LoggerFactory.getLogger(VectorStore.class);

    /**
     * Run more texts through the embeddings and add to the vectorStore.
     *
     * @param texts     Iterable of strings to add to the vectorStore.
     * @param metadatas list of metadatas associated with the texts.
     * @param kwargs    vectorStore specific parameters
     * @return List of ids from adding the texts into the vectorStore.
     */
    public abstract List<String> addTexts(List<String> texts, List<Map<String, Object>> metadatas,
            Map<String, Object> kwargs);

    /**
     * Delete by vector ID.
     *
     * @param ids List of ids to delete.
     * @return true if deletion is successful, false otherwise
     */
    public abstract boolean delete(List<String> ids);

    /**
     * Run more documents through the embeddings and add to the vectorStore.
     *
     * @param documents Documents to add to the vectorStore.
     * @param kwargs    vectorStore specific parameters
     * @return List of IDs of the added texts.
     */
    public List<String> addDocuments(List<Document> documents, Map<String, Object> kwargs) {
        var texts = documents.stream().map(Document::getPageContent).toList();
        var metadatas = documents.stream().map(Document::getMetadata).toList();
        return addTexts(texts, metadatas, kwargs);
    }

    public List<Document> search(String query, SearchType searchType) {
        return switch (searchType) {
            case SIMILARITY -> similaritySearch(query);
            case MMR -> maxMarginalRelevanceSearch(query);
            default -> throw new IllegalArgumentException(
                    "searchType of " + searchType + " not allowed. Expected searchType to be 'similarity' or 'mmr'.");
        };
    }

    /**
     * Return docs most similar to query.
     */
    public List<Document> similaritySearch(String query) {
        return similaritySearch(query, 4);
    }

    /**
     * Return docs most similar to query.
     */
    public abstract List<Document> similaritySearch(String query, int k);

    /**
     * Return docs and relevance scores in the range [0, 1]. 0 is dissimilar, 1 is most similar.
     */
    public List<Pair<Document, Float>> similaritySearchWithRelevanceScores(String query) {
        return similaritySearchWithRelevanceScores(query, 4);
    }

    /**
     * Return docs and relevance scores in the range [0, 1]. 0 is dissimilar, 1 is most similar.
     *
     * @param query input text
     * @param k     Number of Documents to return.
     * @return List of Tuples of (doc, similarity_score)
     */
    public List<Pair<Document, Float>> similaritySearchWithRelevanceScores(String query, int k) {
        List<Pair<Document, Float>> docsAndSimilarities = _similaritySearchWithRelevanceScores(query, k);

        // Check relevance scores and filter by threshold
        if (docsAndSimilarities.stream().anyMatch(pair -> pair.getRight() < 0.0f || pair.getRight() > 1.0f)) {
            LOG.warn("Relevance scores must be between 0 and 1, got {} ", docsAndSimilarities);
        }
        return docsAndSimilarities;
    }

    /**
     * Return docs and relevance scores, normalized on a scale from 0 to 1. 0 is dissimilar, 1 is most similar.
     */
    protected abstract List<Pair<Document, Float>> _similaritySearchWithRelevanceScores(String query, int k);

    /**
     * Return docs most similar to embedding vector.
     *
     * @param embedding Embedding to look up documents similar to.
     * @param k         Number of Documents to return. Defaults to 4.
     * @param kwargs    kwargs to be passed to similarity search
     * @return List of Documents most similar to the query vector.
     */
    public abstract List<Document> similarSearchByVector(List<Float> embedding, int k, Map<String, Object> kwargs);

    public List<Document> maxMarginalRelevanceSearch(String query) {
        return maxMarginalRelevanceSearch(query, 4, 20, 0.5f);
    }

    /**
     * Return docs selected using the maximal marginal relevance.
     * Maximal marginal relevance optimizes for similarity to query AND diversity among selected documents.
     *
     * @param query      Text to look up documents similar to.
     * @param k          Number of Documents to return.
     * @param fetchK     Number of Documents to fetch to pass to MMR algorithm.
     * @param lambdaMult Number between 0 and 1 that determines the degree of diversity among the results with 0
     *                   corresponding to maximum diversity and 1 to minimum diversity.
     * @return List of Documents selected by maximal marginal relevance.
     */
    public abstract List<Document> maxMarginalRelevanceSearch(String query, int k, int fetchK, float lambdaMult);

    public List<Document> maxMarginalRelevanceSearchByVector(List<Float> embedding) {
        return maxMarginalRelevanceSearchByVector(embedding, 4, 20, 0.5f);
    }

    /**
     * Return docs selected using the maximal marginal relevance.
     * Maximal marginal relevance optimizes for similarity to query AND diversity among selected documents.
     *
     * @param embedding  Embedding to look up documents similar to.
     * @param k          Number of Documents to return.
     * @param fetchK     Number of Documents to fetch to pass to MMR algorithm.
     * @param lambdaMult Number between 0 and 1 that determines the degree of diversity among the results with 0 corresponding
     *                   to maximum diversity and 1 to minimum diversity.
     * @return List of Documents selected by maximal marginal relevance.
     */
    public abstract List<Document> maxMarginalRelevanceSearchByVector(List<Float> embedding, int k, int fetchK,
            float lambdaMult);

    /**
     * Return VectorStore initialized from documents and embeddings.
     */
    public int fromDocuments(List<Document> documents, Embeddings embedding) {
        List<String> texts = documents.stream().map(Document::getPageContent).toList();
        List<Map<String, Object>> metadatas = documents.stream().map(Document::getMetadata).toList();
        return fromTexts(texts, embedding, metadatas);
    }

    /**
     * Return VectorStore initialized from texts and embeddings.
     */
    public abstract int fromTexts(List<String> texts, Embeddings embedding, List<Map<String, Object>> metadatas);

    public VectorStoreRetriever asRetriever() {
        return asRetriever(SIMILARITY);
    }

    public VectorStoreRetriever asRetriever(SearchType searchType) {
        return new VectorStoreRetriever(this, searchType);
    }
}