package com.lightschedule.integration.kingdee;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightschedule.config.KingdeeProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class HttpKingdeeWritebackClient implements KingdeeWritebackClient {

    private record ParsedStatus(boolean completed, boolean success) {
    }

    private final KingdeeProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public HttpKingdeeWritebackClient(KingdeeProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public KingdeeWritebackResult writeback(KingdeeWritebackPayload payload) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.baseUrl() + properties.writebackPath()))
                    .header("Content-Type", "application/json")
                    .header("X-App-Id", properties.appId())
                    .header("X-App-Secret", properties.appSecret())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();
            String externalRequestId = response.headers().firstValue("X-Request-Id")
                    .filter(requestId -> !requestId.isBlank())
                    .orElse(extractRequestId(responseBody));
            boolean accepted = response.statusCode() >= 200 && response.statusCode() < 300;
            ParsedStatus parsedStatus = parseStatus(responseBody);
            boolean success = accepted
                    && !externalRequestId.isBlank()
                    && (!parsedStatus.completed() || parsedStatus.success());
            return new KingdeeWritebackResult(success, externalRequestId, success ? extractWritebackSuccessMessage(responseBody) : responseBody);
        } catch (Exception exception) {
            return new KingdeeWritebackResult(false, "", exception.getMessage());
        }
    }

    private String extractWritebackSuccessMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (shouldPreferTopLevelWritebackSuccessMessage(root)) {
                String message = root.path("result").path("message").asText("");
                if (!message.isBlank()) {
                    return message;
                }
            }
            String message = resolveMessageNode(root).path("message").asText("");
            if (!message.isBlank()) {
                return message;
            }
            return extractJsonField(root, "message");
        } catch (Exception exception) {
            return extractMessage(responseBody);
        }
    }

    private boolean shouldPreferTopLevelWritebackSuccessMessage(JsonNode root) {
        if (!root.isObject()) {
            return false;
        }
        JsonNode result = root.path("result");
        if (!result.isObject() || !result.has("message")) {
            return false;
        }
        JsonNode data = root.path("data");
        JsonNode dataResult = data.path("result");
        return (dataResult.isObject() && dataResult.path("completed").asBoolean(false) && dataResult.path("success").asBoolean(false))
                || (data.isObject() && data.path("completed").asBoolean(false) && data.path("success").asBoolean(false));
    }

    private String extractRequestId(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (shouldKeepBlankRequestId(root)) {
                return "";
            }
            JsonNode fieldNode = resolveFieldNode(root, "requestId");
            String value = fieldNode.path("requestId").asText("").trim();
            if (!value.isBlank()) {
                return value;
            }
            return extractJsonField(root, "requestId").trim();
        } catch (Exception exception) {
            return "";
        }
    }

    private String extractMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (shouldReturnBlankMessageForNestedFailure(root)) {
                return "";
            }
            if (shouldReturnResponseBodyForNestedFailureWithoutMessage(root)) {
                return responseBody;
            }
            if (shouldReturnBlankMessageForExplicitRootFailure(root)) {
                return "";
            }
            String message = resolveMessageNode(root).path("message").asText("");
            if (!message.isBlank()) {
                return message;
            }
            message = extractJsonField(root, "message");
            if (!message.isBlank()) {
                return message;
            }
            String rawMessage = extractJsonField(responseBody, "message");
            return rawMessage.isBlank() ? responseBody : rawMessage;
        } catch (Exception exception) {
        }
        String message = extractJsonField(responseBody, "message");
        return message.isBlank() ? responseBody : message;
    }

    private String extractStatusMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String message = resolveMessageNode(root).path("message").asText("");
            if (!message.isBlank()) {
                return message;
            }
            return "";
        } catch (Exception exception) {
            return responseBody;
        }
    }

    private ParsedStatus parseStatus(String responseBody) {
        if (responseBody != null && !responseBody.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode statusNode = resolveStatusNode(root);
                if (statusNode.has("completed")) {
                    boolean completed = statusNode.path("completed").asBoolean(false);
                    boolean success = completed && statusNode.path("success").asBoolean(false);
                    return new ParsedStatus(completed, success);
                }
                String status = statusNode.path("status").asText("").trim().toLowerCase(Locale.ROOT);
                if (!status.isBlank()) {
                    if ("completed".equals(status)) {
                        return new ParsedStatus(true, statusNode.path("success").asBoolean(true));
                    }
                    if ("succeeded".equals(status)) {
                        return new ParsedStatus(true, statusNode.path("success").asBoolean(true));
                    }
                    if ("success".equals(status)) {
                        return statusNode.has("success") && !statusNode.path("success").asBoolean(true)
                                ? new ParsedStatus(true, false)
                                : new ParsedStatus(false, false);
                    }
                    if (isTerminalStatusValue(status)) {
                        return new ParsedStatus(true, false);
                    }
                    return new ParsedStatus(false, false);
                }
                if (statusNode.has("success")) {
                    boolean success = statusNode.path("success").asBoolean(false);
                    return new ParsedStatus(!success, false);
                }
            } catch (Exception exception) {
            }
        }
        String normalizedBody = responseBody == null ? "" : responseBody.toLowerCase(Locale.ROOT);
        boolean completed = normalizedBody.contains("completed")
                || normalizedBody.contains("succeeded")
                || normalizedBody.contains("success")
                || normalizedBody.contains("rejected")
                || normalizedBody.contains("failed")
                || normalizedBody.contains("failure")
                || normalizedBody.contains("error")
                || normalizedBody.contains("unsuccessful")
                || normalizedBody.contains("not successful")
                || normalizedBody.contains("not completed")
                || normalizedBody.contains("not succeeded")
                || normalizedBody.contains("not accepted")
                || normalizedBody.contains("not submitted")
                || normalizedBody.contains("declined")
                || normalizedBody.contains("denied")
                || normalizedBody.contains("cancelled")
                || normalizedBody.contains("canceled")
                || normalizedBody.contains("aborted")
                || normalizedBody.contains("timeout")
                || normalizedBody.contains("timed out")
                || normalizedBody.contains("forbidden")
                || normalizedBody.contains("blocked")
                || normalizedBody.contains("invalid")
                || normalizedBody.contains("expired");
        boolean success = completed
                && (normalizedBody.contains("completed")
                        || normalizedBody.contains("succeeded")
                        || normalizedBody.contains("success")
                        || normalizedBody.contains("without errors")
                        || normalizedBody.contains("with no errors")
                        || normalizedBody.contains("without error"))
                && !normalizedBody.contains("rejected")
                && !normalizedBody.contains("failed")
                && !normalizedBody.contains("failure")
                && (!normalizedBody.contains("error")
                        || normalizedBody.contains("without errors")
                        || normalizedBody.contains("with no errors")
                        || normalizedBody.contains("without error"))
                && !normalizedBody.contains("unsuccessful")
                && !normalizedBody.contains("not successful")
                && !normalizedBody.contains("not completed")
                && !normalizedBody.contains("not succeeded")
                && !normalizedBody.contains("not accepted")
                && !normalizedBody.contains("not submitted")
                && !normalizedBody.contains("declined")
                && !normalizedBody.contains("denied")
                && !normalizedBody.contains("cancelled")
                && !normalizedBody.contains("canceled")
                && !normalizedBody.contains("aborted")
                && !normalizedBody.contains("timeout")
                && !normalizedBody.contains("timed out")
                && !normalizedBody.contains("forbidden")
                && !normalizedBody.contains("blocked")
                && !normalizedBody.contains("invalid")
                && !normalizedBody.contains("expired");
        return new ParsedStatus(completed, success);
    }

    private JsonNode resolveStatusNode(JsonNode root) {
        JsonNode nestedStatusNode = resolveNestedStatusNode(root);
        if (root.has("completed")) {
            boolean completed = root.path("completed").asBoolean(false);
            boolean success = root.path("success").asBoolean(false);
            JsonNode data = root.path("data");
            JsonNode dataResult = data.path("result");
            JsonNode result = root.path("result");
            String dataResultStatus = dataResult.path("status").asText("").trim().toLowerCase(Locale.ROOT);
            boolean isGenericSuccessOnlyDataResult = dataResult.isObject()
                    && dataResult.has("success")
                    && dataResult.path("success").asBoolean(false)
                    && !dataResult.has("status")
                    && !dataResult.has("completed");
            boolean isCompletedSuccessDataResult = dataResult.isObject()
                    && dataResult.path("completed").asBoolean(false)
                    && dataResult.path("success").asBoolean(false);
            boolean isGenericSuccessOnlyData = data.isObject()
                    && data.has("success")
                    && data.path("success").asBoolean(false)
                    && !data.has("status")
                    && !data.has("completed");
            String resultStatus = result.path("status").asText("").trim().toLowerCase(Locale.ROOT);
            if (completed
                    && success
                    && (isGenericTransportStatus(dataResultStatus)
                            || isSuccessWrapperAlias(dataResultStatus)
                            || isGenericSuccessOnlyDataResult
                            || isCompletedSuccessDataResult
                            || isGenericSuccessOnlyData)
                    && (isOpenStatusNode(result)
                            || isTerminalStatusNode(result)
                            || isCompletedSuccessNode(result)
                            || "succeeded".equals(resultStatus)
                            || ("success".equals(resultStatus) && result.has("success") && !result.path("success").asBoolean(true)))) {
                return result;
            }
            if (shouldPreferNestedStatusNode(completed, success, nestedStatusNode)) {
                return nestedStatusNode;
            }
            return root;
        }
        String rootStatus = root.path("status").asText("").trim().toLowerCase(Locale.ROOT);
        if (!rootStatus.isBlank()) {
            JsonNode data = root.path("data");
            JsonNode dataResult = data.path("result");
            JsonNode result = root.path("result");
            String dataResultStatus = dataResult.path("status").asText("").trim().toLowerCase(Locale.ROOT);
            boolean isGenericSuccessOnlyDataResult = dataResult.isObject()
                    && dataResult.has("success")
                    && dataResult.path("success").asBoolean(false)
                    && !dataResult.has("status")
                    && !dataResult.has("completed");
            boolean isCompletedSuccessDataResult = dataResult.isObject()
                    && dataResult.path("completed").asBoolean(false)
                    && dataResult.path("success").asBoolean(false);
            boolean isGenericSuccessOnlyData = data.isObject()
                    && data.has("success")
                    && data.path("success").asBoolean(false)
                    && !data.has("status")
                    && !data.has("completed");
            String resultStatus = result.path("status").asText("").trim().toLowerCase(Locale.ROOT);
            if ((isGenericTransportStatus(rootStatus) || isSuccessWrapperAlias(rootStatus))
                    && !("success".equals(rootStatus) && root.has("success") && !root.path("success").asBoolean(true))
                    && (isGenericTransportStatus(dataResultStatus)
                            || isSuccessWrapperAlias(dataResultStatus)
                            || isGenericSuccessOnlyDataResult
                            || isCompletedSuccessDataResult
                            || isGenericSuccessOnlyData)
                    && (isOpenStatusNode(result)
                            || isTerminalStatusNode(result)
                            || isCompletedSuccessNode(result)
                            || "succeeded".equals(resultStatus)
                            || ("success".equals(resultStatus) && result.has("success") && !result.path("success").asBoolean(true)))) {
                return result;
            }
            if (shouldPreferNestedStatusNode(rootStatus, nestedStatusNode)) {
                return nestedStatusNode;
            }
            return root;
        }
        if (root.has("success")) {
            JsonNode data = root.path("data");
            JsonNode dataResult = data.path("result");
            JsonNode result = root.path("result");
            String dataResultStatus = dataResult.path("status").asText("").trim().toLowerCase(Locale.ROOT);
            String resultStatus = result.path("status").asText("").trim().toLowerCase(Locale.ROOT);
            boolean isGenericSuccessOnlyDataResult = dataResult.isObject()
                    && dataResult.has("success")
                    && dataResult.path("success").asBoolean(false)
                    && !dataResult.has("status")
                    && !dataResult.has("completed");
            boolean isCompletedSuccessDataResult = dataResult.isObject()
                    && dataResult.path("completed").asBoolean(false)
                    && dataResult.path("success").asBoolean(false);
            boolean isGenericSuccessOnlyData = data.isObject()
                    && data.has("success")
                    && data.path("success").asBoolean(false)
                    && !data.has("status")
                    && !data.has("completed");
            if (root.path("success").asBoolean(false)
                    && (isGenericTransportStatus(dataResultStatus)
                            || isSuccessWrapperAlias(dataResultStatus)
                            || isGenericSuccessOnlyDataResult
                            || isCompletedSuccessDataResult
                            || isGenericSuccessOnlyData)
                    && (isOpenStatusNode(result)
                            || isTerminalStatusNode(result)
                            || isCompletedSuccessNode(result)
                            || "succeeded".equals(resultStatus)
                            || ("success".equals(resultStatus) && result.has("success") && !result.path("success").asBoolean(true)))) {
                return result;
            }
            if (!root.path("success").asBoolean(true)) {
                return root;
            }
        }
        if (nestedStatusNode != null) {
            return nestedStatusNode;
        }
        return root;
    }

    private JsonNode resolveNestedStatusNode(JsonNode root) {
        JsonNode data = root.path("data");
        if (data.isObject()) {
            JsonNode dataResult = data.path("result");
            if (data.has("completed")) {
                boolean completed = data.path("completed").asBoolean(false);
                boolean success = data.path("success").asBoolean(false);
                JsonNode result = root.path("result");
                String dataResultStatus = dataResult.path("status").asText("").trim().toLowerCase(Locale.ROOT);
                boolean isGenericSuccessOnlyDataResult = dataResult.isObject()
                        && dataResult.has("success")
                        && dataResult.path("success").asBoolean(false)
                        && !dataResult.has("status")
                        && !dataResult.has("completed");
                boolean isCompletedSuccessDataResult = dataResult.isObject()
                        && dataResult.path("completed").asBoolean(false)
                        && dataResult.path("success").asBoolean(false);
                String resultStatus = result.path("status").asText("").trim().toLowerCase(Locale.ROOT);
                if (completed
                        && success
                        && (isGenericTransportStatus(dataResultStatus)
                                || isSuccessWrapperAlias(dataResultStatus)
                                || isGenericSuccessOnlyDataResult
                                || isCompletedSuccessDataResult)
                        && (isOpenStatusNode(result)
                                || isTerminalStatusNode(result)
                                || isCompletedSuccessNode(result)
                                || "succeeded".equals(resultStatus)
                                || ("success".equals(resultStatus) && result.has("success") && !result.path("success").asBoolean(true)))) {
                    return result;
                }
                if (shouldPreferNestedStatusNode(completed, success, dataResult)
                        && dataResult.isObject()
                        && (dataResult.has("completed") || dataResult.has("status") || dataResult.has("success"))) {
                    return dataResult;
                }
                if (completed && success && (isOpenStatusNode(result) || isTerminalStatusNode(result))) {
                    return result;
                }
                if (!completed && isTerminalStatusNode(result)) {
                    return result;
                }
                return data;
            }
            if (data.has("success")) {
                boolean success = data.path("success").asBoolean(false);
                JsonNode result = root.path("result");
                String resultStatus = result.path("status").asText("").trim().toLowerCase(Locale.ROOT);
                String dataResultStatus = dataResult.path("status").asText("").trim().toLowerCase(Locale.ROOT);
                if (success
                        && dataResult.isObject()
                        && isSuccessWrapperAlias(dataResultStatus)
                        && (isTerminalStatusNode(result)
                                || isCompletedSuccessNode(result)
                                || "completed".equals(resultStatus)
                                || "succeeded".equals(resultStatus)
                                || ("success".equals(resultStatus) && result.has("success") && !result.path("success").asBoolean(true)))) {
                    return result;
                }
                if ((success || isTerminalStatusNode(dataResult))
                        && dataResult.isObject()
                        && (dataResult.has("completed") || dataResult.has("status") || dataResult.has("success"))) {
                    return dataResult;
                }
                if (isTerminalStatusNode(result)
                        || isCompletedSuccessNode(result)
                        || "completed".equals(resultStatus)
                        || "succeeded".equals(resultStatus)) {
                    return result;
                }
                return data;
            }
            String dataStatus = data.path("status").asText("").trim().toLowerCase(Locale.ROOT);
            if (!dataStatus.isBlank()) {
                if (shouldPreferNestedStatusNode(dataStatus, dataResult)) {
                    return dataResult;
                }
                JsonNode result = root.path("result");
                String resultStatus = result.path("status").asText("").trim().toLowerCase(Locale.ROOT);
                if ((isGenericTransportStatus(dataStatus) || isSuccessWrapperAlias(dataStatus))
                        && (isOpenStatusNode(result)
                                || isTerminalStatusNode(result)
                                || isCompletedSuccessNode(result)
                                || "succeeded".equals(resultStatus)
                                || ("success".equals(resultStatus) && result.has("success") && !result.path("success").asBoolean(true)))) {
                    return result;
                }
                if ("ok".equals(dataStatus)
                        && (isTerminalStatusNode(dataResult) || isCompletedSuccessNode(dataResult)
                                || "succeeded".equals(dataResult.path("status").asText("").trim().toLowerCase(Locale.ROOT))
                                || (dataResult.path("success").asBoolean(false)
                                        && !dataResult.has("status")
                                        && !dataResult.has("completed")))) {
                    return dataResult;
                }
                return data;
            }
            if (dataResult.isObject() && (dataResult.has("completed") || dataResult.has("status") || dataResult.has("success"))) {
                return dataResult;
            }
        }
        JsonNode result = root.path("result");
        if (result.isObject() && (result.has("completed") || result.has("status") || result.has("success"))) {
            return result;
        }
        return null;
    }

    private boolean shouldPreferNestedStatusNode(boolean completed, boolean success, JsonNode nestedStatusNode) {
        return nestedStatusNode != null
                && ((completed && success) || (!completed && isTerminalStatusNode(nestedStatusNode)));
    }

    private boolean shouldPreferNestedStatusNode(String status, JsonNode nestedStatusNode) {
        return nestedStatusNode != null
                && !status.isBlank()
                && ((!isTerminalStatusValue(status) && isTerminalStatusNode(nestedStatusNode))
                        || ((isGenericTransportStatus(status) || isSuccessWrapperAlias(status)) && (isOpenStatusNode(nestedStatusNode)
                                || isCompletedSuccessNode(nestedStatusNode)
                                || isExplicitFailureNode(nestedStatusNode)
                                || "succeeded".equals(nestedStatusNode.path("status").asText("").trim().toLowerCase(Locale.ROOT)))));
    }

    private boolean isTerminalStatusNode(JsonNode statusNode) {
        if (!statusNode.isObject()) {
            return false;
        }
        if (statusNode.has("completed")) {
            boolean completed = statusNode.path("completed").asBoolean(false);
            boolean success = statusNode.path("success").asBoolean(false);
            return completed && !success;
        }
        String status = statusNode.path("status").asText("").trim().toLowerCase(Locale.ROOT);
        if (!status.isBlank()) {
            return isTerminalStatusValue(status);
        }
        return statusNode.has("success") && !statusNode.path("success").asBoolean(false);
    }

    private boolean isOpenStatusNode(JsonNode statusNode) {
        if (!statusNode.isObject()) {
            return false;
        }
        if (statusNode.has("completed")) {
            return !statusNode.path("completed").asBoolean(false);
        }
        String status = statusNode.path("status").asText("").trim().toLowerCase(Locale.ROOT);
        if (!status.isBlank()) {
            return !isTerminalStatusValue(status) && !isGenericTransportStatus(status);
        }
        return false;
    }

    private boolean isExplicitFailureNode(JsonNode statusNode) {
        if (!statusNode.isObject()) {
            return false;
        }
        if (statusNode.has("completed")) {
            return statusNode.path("completed").asBoolean(false) && !statusNode.path("success").asBoolean(false);
        }
        String status = statusNode.path("status").asText("").trim().toLowerCase(Locale.ROOT);
        if (!status.isBlank()) {
            return isTerminalStatusValue(status)
                    || ("success".equals(status) && statusNode.has("success") && !statusNode.path("success").asBoolean(true))
                    || ("succeeded".equals(status) && statusNode.has("success") && !statusNode.path("success").asBoolean(true));
        }
        return statusNode.has("success") && !statusNode.path("success").asBoolean(false);
    }

    private boolean isCompletedSuccessNode(JsonNode statusNode) {
        return statusNode.isObject()
                && statusNode.path("completed").asBoolean(false)
                && statusNode.path("success").asBoolean(false);
    }

    private boolean isTerminalStatusValue(String status) {
        return "failed".equals(status)
                || "failure".equals(status)
                || "rejected".equals(status)
                || "terminal_failed".equals(status)
                || "terminal failed".equals(status)
                || "terminal-failed".equals(status)
                || "terminalfailed".equals(status)
                || "error".equals(status)
                || "unsuccessful".equals(status)
                || "not_successful".equals(status)
                || "not successful".equals(status)
                || "not-successful".equals(status)
                || "notsuccessful".equals(status)
                || "not_completed".equals(status)
                || "not completed".equals(status)
                || "not-completed".equals(status)
                || "notcompleted".equals(status)
                || "not_succeeded".equals(status)
                || "not succeeded".equals(status)
                || "not-succeeded".equals(status)
                || "notsucceeded".equals(status)
                || "not_accepted".equals(status)
                || "not accepted".equals(status)
                || "not-accepted".equals(status)
                || "notaccepted".equals(status)
                || "not_submitted".equals(status)
                || "not submitted".equals(status)
                || "not-submitted".equals(status)
                || "notsubmitted".equals(status)
                || "declined".equals(status)
                || "denied".equals(status)
                || "cancelled".equals(status)
                || "canceled".equals(status)
                || "aborted".equals(status)
                || "timeout".equals(status)
                || "timed_out".equals(status)
                || "timed out".equals(status)
                || "timed-out".equals(status)
                || "timedout".equals(status)
                || "forbidden".equals(status)
                || "blocked".equals(status)
                || "invalid".equals(status)
                || "expired".equals(status);
    }

    private JsonNode resolveMessageNode(JsonNode root) {
        return resolveFieldNode(root, "message");
    }

    private JsonNode resolveFieldNode(JsonNode root, String fieldName) {
        JsonNode statusNode = resolveStatusNode(root);
        JsonNode data = root.path("data");
        JsonNode dataResult = data.path("result");
        JsonNode result = root.path("result");
        if ("requestId".equals(fieldName)
                && result.isObject()
                && result.has(fieldName)
                && ((dataResult.isObject()
                                && data.path("requestId").asText("").trim().isBlank()
                                && ((dataResult.path("completed").asBoolean(false) && dataResult.path("success").asBoolean(false))
                                        || isExplicitFailureNode(dataResult)))
                        || (data.isObject() && data.path("completed").asBoolean(false) && data.path("success").asBoolean(false)))
                && (isGenericTransportStatus(root.path("status").asText("").trim().toLowerCase(Locale.ROOT))
                        || isSuccessWrapperAlias(root.path("status").asText("").trim().toLowerCase(Locale.ROOT))
                        || isGenericTransportStatus(data.path("status").asText("").trim().toLowerCase(Locale.ROOT))
                        || isSuccessWrapperAlias(data.path("status").asText("").trim().toLowerCase(Locale.ROOT))
                        || root.path("status").asText("").isBlank())) {
            return result;
        }
        if (shouldPreferNestedFieldNode(statusNode, resolveNestedFieldNode(root, fieldName))) {
            JsonNode alignedFieldNode = resolveAlignedNestedFieldNode(root, statusNode, fieldName);
            if (alignedFieldNode != null) {
                return alignedFieldNode;
            }
        }
        if ("requestId".equals(fieldName)
                && statusNode == dataResult
                && data.isObject()
                && data.has(fieldName)
                && !data.path(fieldName).asText("").trim().isBlank()) {
            return data;
        }
        if (statusNode.has(fieldName)) {
            return statusNode;
        }
        if (statusNode == dataResult && data.isObject() && data.has(fieldName)) {
            return data;
        }
        JsonNode nestedFieldNode = resolveNestedFieldNode(root, fieldName);
        if (nestedFieldNode != null) {
            return nestedFieldNode;
        }
        return root;
    }

    private JsonNode resolveNestedFieldNode(JsonNode root, String fieldName) {
        JsonNode data = root.path("data");
        if (data.isObject()) {
            JsonNode dataResult = data.path("result");
            if (dataResult.isObject() && dataResult.has(fieldName)) {
                return dataResult;
            }
        }
        JsonNode topLevelFieldNode = resolveAmbiguousTopLevelFieldNode(root, fieldName);
        if (topLevelFieldNode != null) {
            return topLevelFieldNode;
        }
        if (data.isObject() && data.has(fieldName)) {
            return data;
        }
        JsonNode result = root.path("result");
        if (result.isObject() && result.has(fieldName)) {
            return result;
        }
        return null;
    }

    private JsonNode resolveAlignedNestedFieldNode(JsonNode root, JsonNode statusNode, String fieldName) {
        JsonNode data = root.path("data");
        JsonNode result = root.path("result");
        JsonNode dataResult = data.path("result");
        if (statusNode == dataResult) {
            if (dataResult.isObject() && dataResult.has(fieldName)) {
                String dataResultStatus = dataResult.path("status").asText("").trim().toLowerCase(Locale.ROOT);
                boolean completedSuccessWrapper = data.path("completed").asBoolean(false)
                        && data.path("success").asBoolean(false);
                boolean rootCompletedSuccessWrapper = root.path("completed").asBoolean(false)
                        && root.path("success").asBoolean(false);
                boolean rootSuccessOnlyWrapper = root.path("success").asBoolean(false)
                        && !root.path("completed").asBoolean(false)
                        && root.path("status").asText("").isBlank();
                boolean rootStatusSuccessWrapper = (isGenericTransportStatus(root.path("status").asText("").trim().toLowerCase(Locale.ROOT))
                                || isSuccessWrapperAlias(root.path("status").asText("").trim().toLowerCase(Locale.ROOT)))
                        && !("success".equals(root.path("status").asText("").trim().toLowerCase(Locale.ROOT))
                                && root.has("success")
                                && !root.path("success").asBoolean(true));
                boolean dataSuccessOnlyWrapper = data.path("success").asBoolean(false)
                        && !data.path("completed").asBoolean(false)
                        && data.path("status").asText("").isBlank();
                boolean dataStatusSuccessWrapper = (isGenericTransportStatus(data.path("status").asText("").trim().toLowerCase(Locale.ROOT))
                                || isSuccessWrapperAlias(data.path("status").asText("").trim().toLowerCase(Locale.ROOT)))
                        && !("success".equals(data.path("status").asText("").trim().toLowerCase(Locale.ROOT))
                                && data.has("success")
                                && !data.path("success").asBoolean(true));
                if ("requestId".equals(fieldName) && data.isObject() && data.has(fieldName) && !data.path(fieldName).asText("").trim().isBlank()) {
                    return data;
                }
                if ((completedSuccessWrapper
                                || rootCompletedSuccessWrapper
                                || rootSuccessOnlyWrapper
                                || rootStatusSuccessWrapper
                                || dataSuccessOnlyWrapper
                                || dataStatusSuccessWrapper)
                        && data.path("requestId").asText("").trim().isBlank()
                        && ((dataResult.path("success").asBoolean(false)
                                        && !dataResult.has("status")
                                        && !dataResult.has("completed"))
                                || isGenericTransportStatus(dataResultStatus)
                                || isSuccessWrapperAlias(dataResultStatus)
                                || (dataResult.path("completed").asBoolean(false)
                                        && dataResult.path("success").asBoolean(false))
                                || isExplicitFailureNode(dataResult))
                        && result.isObject()
                        && result.has(fieldName)) {
                    return result;
                }
                return dataResult;
            }
            if (data.isObject() && data.has(fieldName)) {
                return data;
            }
        }
        if (statusNode == data) {
            if (data.path("completed").asBoolean(false)
                    && data.path("success").asBoolean(false)
                    && result.isObject()
                    && result.has(fieldName)) {
                return result;
            }
            if (!data.path("success").asBoolean(true)) {
                String nestedStatus = dataResult.path("status").asText("").trim().toLowerCase(Locale.ROOT);
                boolean nestedSuccessOnlyPayload = dataResult.isObject()
                        && ((dataResult.path("success").asBoolean(false)
                                        && !dataResult.has("status")
                                        && !dataResult.has("completed"))
                                || isGenericTransportStatus(nestedStatus)
                                || isSuccessWrapperAlias(nestedStatus)
                                || dataResult.path("success").asBoolean(false)
                                || (dataResult.path("completed").asBoolean(false)
                                        && dataResult.path("success").asBoolean(false)));
                if (!nestedSuccessOnlyPayload && dataResult.isObject() && dataResult.has(fieldName)) {
                    return dataResult;
                }
                if (data.isObject() && data.has(fieldName)) {
                    return data;
                }
                if (result.isObject() && result.has(fieldName)) {
                    return result;
                }
            }
            if (data.path("success").asBoolean(false) && result.isObject() && result.has(fieldName)) {
                return result;
            }
            String dataStatus = data.path("status").asText("").trim().toLowerCase(Locale.ROOT);
            if ((isGenericTransportStatus(dataStatus) || isSuccessWrapperAlias(dataStatus))
                    && result.isObject()
                    && result.has(fieldName)
                    && (!dataResult.isObject()
                            || !dataResult.has(fieldName)
                            || !data.has(fieldName)
                            || (!dataResult.has("status")
                                    || (dataResult.path("success").asBoolean(false)
                                            && !dataResult.has("status")
                                            && !dataResult.has("completed"))
                                    || isGenericTransportStatus(dataResult.path("status").asText("").trim().toLowerCase(Locale.ROOT))
                                    || isSuccessWrapperAlias(dataResult.path("status").asText("").trim().toLowerCase(Locale.ROOT))
                                    || (dataResult.path("completed").asBoolean(false)
                                            && dataResult.path("success").asBoolean(false))))) {
                return result;
            }
            if (dataResult.isObject() && dataResult.has(fieldName)) {
                return dataResult;
            }
            if (data.isObject() && data.has(fieldName)) {
                return data;
            }
        }
        if (statusNode == result && result.isObject() && result.has(fieldName)) {
            return result;
        }
        if (statusNode == root
                && "message".equals(fieldName)
                && root.has("success")
                && !root.path("success").asBoolean(true)
                && !root.has("completed")
                && (root.path("status").asText("").isBlank() || "success".equals(root.path("status").asText("").trim().toLowerCase(Locale.ROOT)))) {
            JsonNode nestedStatusNode = resolveNestedStatusNode(root);
            if (nestedStatusNode != null
                    && nestedStatusNode != root
                    && isTerminalStatusNode(nestedStatusNode)
                    && nestedStatusNode.has(fieldName)) {
                return nestedStatusNode;
            }
            if (root.has(fieldName)) {
                return root;
            }
        }
        if (statusNode == root && (root.has("completed") || root.has("success") || !root.path("status").asText("").isBlank())) {
            JsonNode wrapperPayloadFieldNode = resolveWrapperPayloadFieldNode(root, fieldName);
            if (wrapperPayloadFieldNode != null) {
                return wrapperPayloadFieldNode;
            }
        }
        JsonNode requestIdBearingNode = resolveTopLevelRequestIdBearingFieldNode(root, fieldName);
        if (requestIdBearingNode != null) {
            return requestIdBearingNode;
        }
        return resolveNestedFieldNode(root, fieldName);
    }

    private boolean shouldPreferNestedFieldNode(JsonNode statusNode, JsonNode nestedFieldNode) {
        if (nestedFieldNode == null || !statusNode.isObject()) {
            return false;
        }
        if (statusNode.has("completed") && statusNode.path("completed").asBoolean(false) && statusNode.path("success").asBoolean(false)) {
            return true;
        }
        if (statusNode.has("success") && !statusNode.path("success").asBoolean(false) && !statusNode.has("status") && !statusNode.has("completed")) {
            return true;
        }
        String status = statusNode.path("status").asText("").trim().toLowerCase(Locale.ROOT);
        return isGenericTransportStatus(status)
                || isSuccessWrapperAlias(status)
                || !statusNode.has("status") && !statusNode.has("completed");
    }

    private boolean isGenericTransportStatus(String status) {
        return "success".equals(status) || "succeeded".equals(status) || "ok".equals(status);
    }

    private boolean isSuccessWrapperAlias(String status) {
        return "accepted".equals(status)
                || "submitted".equals(status)
                || "request accepted".equals(status)
                || "accepted without errors".equals(status)
                || "with no errors".equals(status)
                || "without error".equals(status);
    }

    private JsonNode resolveAmbiguousTopLevelFieldNode(JsonNode root, String fieldName) {
        JsonNode candidate = null;
        var fields = root.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            if (("data".equals(entry.getKey()) || "result".equals(entry.getKey()))
                    && entry.getValue().isObject()
                    && entry.getValue().has(fieldName)) {
                candidate = entry.getValue();
            }
        }
        return candidate;
    }

    private JsonNode resolveTopLevelRequestIdBearingFieldNode(JsonNode root, String fieldName) {
        JsonNode candidate = null;
        var fields = root.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            if (("data".equals(entry.getKey()) || "result".equals(entry.getKey()))
                    && entry.getValue().isObject()
                    && entry.getValue().has("requestId")
                    && entry.getValue().has(fieldName)) {
                candidate = entry.getValue();
            }
        }
        return candidate;
    }

    private JsonNode resolveWrapperPayloadFieldNode(JsonNode root, String fieldName) {
        JsonNode result = root.path("result");
        if (result.isObject() && result.has(fieldName)) {
            return result;
        }
        JsonNode data = root.path("data");
        JsonNode dataResult = data.path("result");
        if (dataResult.isObject() && dataResult.has(fieldName)) {
            return dataResult;
        }
        if (data.isObject() && data.has(fieldName)) {
            return data;
        }
        return null;
    }

    private boolean shouldKeepBlankRequestId(JsonNode root) {
        JsonNode data = root.path("data");
        if (!data.isObject()) {
            return false;
        }
        String ownRequestId = data.path("requestId").asText("").trim();
        if (!ownRequestId.isBlank()) {
            return false;
        }
        String dataStatus = data.path("status").asText("").trim().toLowerCase(Locale.ROOT);
        boolean terminalFailureWithoutRequestId = (data.has("success") && !data.path("success").asBoolean(true))
                || isTerminalStatusValue(dataStatus);
        if (!terminalFailureWithoutRequestId) {
            return false;
        }
        JsonNode dataResult = data.path("result");
        String nestedStatus = dataResult.path("status").asText("").trim().toLowerCase(Locale.ROOT);
        boolean nestedSuccessPayload = dataResult.isObject()
                && dataResult.has("requestId")
                && (dataResult.path("success").asBoolean(false)
                        || isGenericTransportStatus(nestedStatus)
                        || isSuccessWrapperAlias(nestedStatus));
        if (!nestedSuccessPayload) {
            return false;
        }
        JsonNode result = root.path("result");
        return result.isObject() && result.has("requestId");
    }

    private boolean shouldReturnBlankMessageForNestedFailure(JsonNode root) {
        JsonNode statusNode = resolveStatusNode(root);
        if (!statusNode.isObject() || !statusNode.has("message") || !statusNode.path("message").asText("").trim().isBlank()) {
            return false;
        }
        String status = statusNode.path("status").asText("").trim().toLowerCase(Locale.ROOT);
        return ("completed".equals(status)
                        || "success".equals(status)
                        || "succeeded".equals(status))
                && statusNode.has("success")
                && !statusNode.path("success").asBoolean(true);
    }

    private boolean shouldReturnResponseBodyForNestedFailureWithoutMessage(JsonNode root) {
        JsonNode data = root.path("data");
        boolean genericDataSuccessWrapper = data.isObject()
                && (isGenericTransportStatus(data.path("status").asText("").trim().toLowerCase(Locale.ROOT))
                        || isSuccessWrapperAlias(data.path("status").asText("").trim().toLowerCase(Locale.ROOT)))
                && data.has("message");
        boolean rootStatusSuccessWrapper = isSuccessWrapperAlias(root.path("status").asText("").trim().toLowerCase(Locale.ROOT))
                && root.has("message");
        boolean completedSuccessRootWrapper = root.path("completed").asBoolean(false)
                && root.path("success").asBoolean(false)
                && root.has("message");
        boolean successOnlyRootWrapper = root.path("success").asBoolean(false)
                && !root.path("completed").asBoolean(false)
                && root.path("status").asText("").isBlank()
                && root.has("message");
        boolean completedSuccessDataWrapper = data.isObject()
                && data.path("completed").asBoolean(false)
                && data.path("success").asBoolean(false)
                && data.has("message");
        boolean successOnlyDataWrapper = data.isObject()
                && data.path("success").asBoolean(false)
                && !data.path("completed").asBoolean(false)
                && data.path("status").asText("").isBlank();
        if (!genericDataSuccessWrapper
                && !rootStatusSuccessWrapper
                && !completedSuccessRootWrapper
                && !successOnlyRootWrapper
                && !completedSuccessDataWrapper
                && !successOnlyDataWrapper) {
            return false;
        }
        JsonNode dataResult = data.path("result");
        if (dataResult.isObject()) {
            JsonNode result = root.path("result");
            String resultStatus = result.path("status").asText("").trim().toLowerCase(Locale.ROOT);
            String nestedStatus = dataResult.path("status").asText("").trim().toLowerCase(Locale.ROOT);
            if (successOnlyDataWrapper
                    && isSuccessWrapperAlias(nestedStatus)
                    && (!result.has("message") || result.path("message").asText("").trim().isBlank())
                    && ("success".equals(resultStatus)
                            || "completed".equals(resultStatus)
                            || "succeeded".equals(resultStatus)
                            || "failed".equals(resultStatus)
                            || "error".equals(resultStatus)
                            || "terminal-failed".equals(resultStatus)
                            || "terminal_failed".equals(resultStatus)
                            || "terminalfailed".equals(resultStatus)
                            || "terminal failed".equals(resultStatus)
                            || "unsuccessful".equals(resultStatus)
                            || "not successful".equals(resultStatus)
                            || "declined".equals(resultStatus)
                            || "denied".equals(resultStatus)
                            || "cancelled".equals(resultStatus)
                            || "canceled".equals(resultStatus)
                            || "aborted".equals(resultStatus)
                            || "timeout".equals(resultStatus)
                            || "timed out".equals(resultStatus)
                            || "forbidden".equals(resultStatus)
                            || "blocked".equals(resultStatus)
                            || "invalid".equals(resultStatus)
                            || "expired".equals(resultStatus))
                    && result.has("success")
                    && !result.path("success").asBoolean(true)) {
                return true;
            }
            String nestedMessage = dataResult.path("message").asText("").trim();
            if (dataResult.has("message") && !nestedMessage.isBlank()) {
                return false;
            }
            return !dataResult.path("status").asText("").isBlank()
                    ? isTerminalStatusValue(dataResult.path("status").asText("").trim().toLowerCase(Locale.ROOT))
                    : dataResult.path("completed").asBoolean(false) && !dataResult.path("success").asBoolean(false);
        }
        if (data.isObject()) {
            JsonNode result = root.path("result");
            if (genericDataSuccessWrapper
                    && result.isObject()
                    && result.has("success")
                    && !result.path("success").asBoolean(true)
                    && (!result.has("message") || result.path("message").asText("").trim().isBlank())) {
                return true;
            }
            String dataMessage = data.path("message").asText("").trim();
            if (data.has("message") && !dataMessage.isBlank()) {
                return false;
            }
            String dataStatus = data.path("status").asText("").trim().toLowerCase(Locale.ROOT);
            if (!dataStatus.isBlank()) {
                return isTerminalStatusValue(dataStatus);
            }
            return (data.path("completed").asBoolean(false) && !data.path("success").asBoolean(false))
                    || (data.has("success") && !data.path("success").asBoolean(true) && !data.has("completed"));
        }
        JsonNode result = root.path("result");
        if (!result.isObject()) {
            return false;
        }
        String resultMessage = result.path("message").asText("").trim();
        if (result.has("message") && !resultMessage.isBlank()) {
            return false;
        }
        String resultStatus = result.path("status").asText("").trim().toLowerCase(Locale.ROOT);
        if (rootStatusSuccessWrapper
                && ("success".equals(resultStatus) || "succeeded".equals(resultStatus) || "completed".equals(resultStatus))
                && result.has("success")
                && !result.path("success").asBoolean(true)) {
            return true;
        }
        return !resultStatus.isBlank()
                ? isTerminalStatusValue(resultStatus)
                : result.path("completed").asBoolean(false) && !result.path("success").asBoolean(false);
    }

    private boolean shouldReturnBlankMessageForExplicitRootFailure(JsonNode root) {
        if (!root.isObject() || !root.has("message")) {
            return false;
        }
        String message = root.path("message").asText("").trim();
        if (!message.isBlank()) {
            return false;
        }
        if (root.has("completed")) {
            return root.path("completed").asBoolean(false) && !root.path("success").asBoolean(false);
        }
        String status = root.path("status").asText("").trim().toLowerCase(Locale.ROOT);
        if (status.isBlank()) {
            return root.has("success") && !root.path("success").asBoolean(true);
        }
        return isTerminalStatusValue(status)
                || ("completed".equals(status) && root.has("success") && !root.path("success").asBoolean(true))
                || ("success".equals(status) && root.has("success") && !root.path("success").asBoolean(true))
                || ("succeeded".equals(status) && root.has("success") && !root.path("success").asBoolean(true));
    }

    private String extractJsonField(String responseBody, String fieldName) {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            return extractJsonField(root, fieldName);
        } catch (Exception exception) {
            return "";
        }
    }

    private String extractJsonField(JsonNode root, String fieldName) {
        JsonNode data = root.path("data");
        if (data.isObject()) {
            JsonNode dataResult = data.path("result");
            if (dataResult.isObject()) {
                String value = dataResult.path(fieldName).asText("");
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        JsonNode topLevelFieldNode = resolveAmbiguousTopLevelFieldNode(root, fieldName);
        if (topLevelFieldNode != null) {
            String value = topLevelFieldNode.path(fieldName).asText("");
            if (!value.isBlank()) {
                return value;
            }
        }
        if (data.isObject()) {
            String value = data.path(fieldName).asText("");
            if (!value.isBlank()) {
                return value;
            }
        }
        JsonNode result = root.path("result");
        if (result.isObject()) {
            String value = result.path(fieldName).asText("");
            if (!value.isBlank()) {
                return value;
            }
        }
        String value = root.path(fieldName).asText("");
        if (!value.isBlank()) {
            return value;
        }
        return "";
    }

    @Override
    public KingdeeWritebackStatusResult queryStatus(String externalRequestId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.baseUrl()
                            + properties.writebackStatusPath()
                            + "?requestId="
                            + URLEncoder.encode(externalRequestId, StandardCharsets.UTF_8)))
                    .header("X-App-Id", properties.appId())
                    .header("X-App-Secret", properties.appSecret())
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();
            boolean accepted = response.statusCode() >= 200 && response.statusCode() < 300;
            ParsedStatus parsedStatus = parseStatus(responseBody);
            boolean completed = parsedStatus.completed() && (accepted || !parsedStatus.success());
            boolean success = completed && accepted && parsedStatus.success();
            return new KingdeeWritebackStatusResult(
                    completed,
                    success,
                    success ? extractStatusMessage(responseBody) : extractMessage(responseBody));
        } catch (Exception exception) {
            return new KingdeeWritebackStatusResult(false, false, exception.getMessage());
        }
    }
}
