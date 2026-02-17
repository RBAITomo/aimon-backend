package dev.aimon.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * Response payload for embedding generation.
 */
public class LiteLlmEmbeddingResponse {

    @JsonProperty("data")
    private List<Item> data;

    @JsonProperty("usage")
    private LiteLlmChatResponse.Usage usage;

    public List<Item> getData() {
        return data == null ? Collections.emptyList() : data;
    }

    public void setData(List<Item> data) {
        this.data = data;
    }

    public LiteLlmChatResponse.Usage getUsage() {
        return usage;
    }

    public void setUsage(LiteLlmChatResponse.Usage usage) {
        this.usage = usage;
    }

    /**
     * Convenience helper returning the first embedding as a float array.
     */
    public float[] firstEmbeddingAsFloatArray() {
        return getData().stream()
            .findFirst()
            .map(Item::getEmbedding)
            .map(list -> {
                float[] array = new float[list.size()];
                for (int i = 0; i < list.size(); i++) {
                    array[i] = list.get(i).floatValue();
                }
                return array;
            })
            .orElse(new float[0]);
    }

    public static class Item {
        @JsonProperty("object")
        private String object;

        @JsonProperty("embedding")
        private List<Double> embedding;

        @JsonProperty("index")
        private int index;

        public String getObject() {
            return object;
        }

        public void setObject(String object) {
            this.object = object;
        }

        public List<Double> getEmbedding() {
            return embedding;
        }

        public void setEmbedding(List<Double> embedding) {
            this.embedding = embedding;
        }

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }
    }
}
