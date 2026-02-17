package dev.aimon.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * Response payload for image generation.
 */
public class LiteLlmImageResponse {

    @JsonProperty("created")
    private long created;

    @JsonProperty("data")
    private List<ImageData> data;

    public long getCreated() {
        return created;
    }

    public void setCreated(long created) {
        this.created = created;
    }

    public List<ImageData> getData() {
        return data == null ? Collections.emptyList() : data;
    }

    public void setData(List<ImageData> data) {
        this.data = data;
    }

    public static class ImageData {
        @JsonProperty("url")
        private String url;

        @JsonProperty("b64_json")
        private String base64Json;

        @JsonProperty("revised_prompt")
        private String revisedPrompt;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getBase64Json() {
            return base64Json;
        }

        public void setBase64Json(String base64Json) {
            this.base64Json = base64Json;
        }

        public String getRevisedPrompt() {
            return revisedPrompt;
        }

        public void setRevisedPrompt(String revisedPrompt) {
            this.revisedPrompt = revisedPrompt;
        }
    }
}
