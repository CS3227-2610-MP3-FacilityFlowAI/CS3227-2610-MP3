package sg.edu.nus.facilityflow.ai;

import java.util.List;
import java.util.UUID;

/** REQ-AI-005: typed role-to-gateway seam; no transport details or business mutation tools. */
public interface AiGateway {
    enum Status { SUCCEEDED, AI_UNAVAILABLE, AI_LIMITED, AI_INVALID_REQUEST, AI_CONFIGURATION_ERROR, AI_TIMEOUT, AI_INVALID_OUTPUT }
    record Context(String roughText, String title, String description, String location,
            String selectedCategory, String selectedUrgency, List<String> activeCategories) {
        public Context { activeCategories = List.copyOf(activeCategories); }
    }
    record Call(String feature, UUID actorId, UUID correlationId, Context context) { }
    record Suggestion(String draftTitle, String draftDescription, String draftLocation, String suggestedCategory,
            String suggestedUrgency, List<String> missingInformationQuestions, String rationale) {
        public Suggestion { missingInformationQuestions = List.copyOf(missingInformationQuestions); }
    }
    record Result(Status status, Suggestion suggestion, int retryCount, Long inputTokens, Long outputTokens, Long totalTokens) {
        public static Result failure(Status status) { return new Result(status, null, 0, null, null, null); }
    }
    boolean available();
    String model();
    int timeoutSeconds();
    Result suggest(Call call);
}
