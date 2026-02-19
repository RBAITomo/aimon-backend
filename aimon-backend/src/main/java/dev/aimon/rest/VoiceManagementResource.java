package dev.aimon.rest;

import dev.aimon.dto.tts.VoiceCloneResponse;
import dev.aimon.dto.tts.VoiceInfo;
import dev.aimon.service.tts.VieNeuTtsService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/**
 * REST endpoints for TTS voice management (list, clone, delete).
 *
 * Proxies requests to the VieNeu-TTS FastAPI service.
 *
 * Endpoints:
 *   GET    /api/voices              - List all voices (preset + custom)
 *   POST   /api/voices/clone        - Clone a new voice from reference audio
 *   DELETE /api/voices/custom/{id}  - Delete a custom cloned voice
 */
@Path("/api/voices")
@Produces(MediaType.APPLICATION_JSON)
public class VoiceManagementResource {

    private static final Logger LOG = Logger.getLogger(VoiceManagementResource.class);

    @Inject
    VieNeuTtsService vieNeuTtsService;

    /**
     * List all available TTS voices (preset + custom cloned).
     */
    @GET
    public Response listVoices() {
        try {
            List<VoiceInfo> voices = vieNeuTtsService.listVoices();
            return Response.ok(Map.of("voices", voices, "count", voices.size())).build();
        } catch (Exception e) {
            LOG.errorf(e, "Failed to list voices");
            return Response.serverError()
                    .entity(Map.of("error", "Failed to retrieve voices"))
                    .build();
        }
    }

    /**
     * Clone a new voice from a reference audio file.
     *
     * Form fields:
     *   audio_file  - Audio file (WAV or MP3, 3-10s of clean speech)
     *   transcript  - Exact transcript of what is spoken in the audio
     *   voice_id    - (optional) custom ID; auto-generated if blank
     *   description - (optional) human-readable label
     *
     * Returns the assigned voice_id which can be used in TTS synthesis.
     */
    @POST
    @Path("/clone")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response cloneVoice(
            @RestForm("audio_file") FileUpload audioFile,
            @RestForm("transcript") String transcript,
            @RestForm("voice_id") String voiceId,
            @RestForm("description") String description) {

        if (audioFile == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "audio_file is required"))
                    .build();
        }
        if (transcript == null || transcript.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "transcript is required"))
                    .build();
        }

        try {
            byte[] audioBytes = Files.readAllBytes(audioFile.filePath());
            String filename = audioFile.fileName();

            LOG.infof("Cloning voice: file=%s size=%d bytes transcript=%d chars",
                    filename, audioBytes.length, transcript.length());

            VoiceCloneResponse result = vieNeuTtsService.cloneVoice(
                    audioBytes,
                    filename,
                    transcript.strip(),
                    voiceId != null ? voiceId.strip() : "",
                    description != null ? description.strip() : ""
            );

            return Response.ok(result).build();

        } catch (IOException e) {
            LOG.errorf(e, "Failed to read uploaded audio file");
            return Response.serverError()
                    .entity(Map.of("error", "Failed to read audio file"))
                    .build();
        } catch (Exception e) {
            LOG.errorf(e, "Voice cloning failed");
            return Response.serverError()
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    /**
     * Delete a custom cloned voice by ID.
     */
    @DELETE
    @Path("/custom/{voiceId}")
    public Response deleteVoice(@PathParam("voiceId") String voiceId) {
        try {
            boolean deleted = vieNeuTtsService.deleteCustomVoice(voiceId);
            if (!deleted) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Voice '" + voiceId + "' not found"))
                        .build();
            }
            return Response.ok(Map.of("voice_id", voiceId, "status", "deleted")).build();
        } catch (Exception e) {
            LOG.errorf(e, "Failed to delete voice '%s'", voiceId);
            return Response.serverError()
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }
}
