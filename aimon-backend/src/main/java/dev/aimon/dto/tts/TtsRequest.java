package dev.aimon.dto.tts;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public class TtsRequest {
    @NotBlank(message = "Nội dung văn bản không được để trống")
    private String text;

    private String voiceCode; // Tùy chọn, nếu không có sẽ dùng giọng mặc định

    private Double speedRate; // Tùy chọn, tốc độ nói

    private Double pitchShift; // Tùy chọn, per-request pitch override

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getVoiceCode() {
        return voiceCode;
    }

    public void setVoiceCode(String voiceCode) {
        this.voiceCode = voiceCode;
    }

    public Double getSpeedRate() {
        return speedRate;
    }

    public void setSpeedRate(Double speedRate) {
        this.speedRate = speedRate;
    }

    public Double getPitchShift() {
        return pitchShift;
    }

    public void setPitchShift(Double pitchShift) {
        this.pitchShift = pitchShift;
    }
}
