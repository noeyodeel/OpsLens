package com.opslens.api.slack;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.opslens.application.incident.IncidentNotFoundException;
import com.opslens.application.slack.InvalidSlackCommandException;
import com.opslens.application.slack.SlackCommandService;
import com.opslens.application.slack.SlackCommandService.SlackCommandRequest;
import com.opslens.application.slack.SlackCommandService.SlackCommandResult;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/slack")
public class SlackCommandController {

    private final SlackCommandService slackCommandService;

    public SlackCommandController(SlackCommandService slackCommandService) {
        this.slackCommandService = slackCommandService;
    }

    @PostMapping(value = "/commands", consumes = MediaType.APPLICATION_JSON_VALUE)
    public SlackCommandResult command(@Valid @RequestBody SlackCommandPayload payload) {
        return slackCommandService.handle(payload.toCommand());
    }

    @PostMapping(value = "/commands", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public SlackCommandResult slashCommand(
        @RequestParam(name = "user_id", required = false) String userId,
        @RequestParam(name = "channel_id", required = false) String channelId,
        @RequestParam String text,
        @RequestParam(name = "response_url", required = false) String responseUrl,
        @RequestParam(name = "thread_ts", required = false) String threadTs
    ) {
        return slackCommandService.handle(new SlackCommandRequest(
            userId,
            channelId,
            text,
            responseUrl,
            threadTs
        ));
    }

    @ExceptionHandler(InvalidSlackCommandException.class)
    public ResponseEntity<SlackCommandResult> handleInvalidCommand(InvalidSlackCommandException exception) {
        return ResponseEntity.badRequest().body(new SlackCommandResult(
            null,
            null,
            "ephemeral",
            exception.getMessage()
        ));
    }

    @ExceptionHandler(IncidentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleIncidentNotFound() {
    }

    public record SlackCommandPayload(
        String userId,
        String channelId,
        @NotBlank String text,
        String responseUrl,
        String threadTs
    ) {

        private SlackCommandRequest toCommand() {
            return new SlackCommandRequest(userId, channelId, text, responseUrl, threadTs);
        }
    }
}
