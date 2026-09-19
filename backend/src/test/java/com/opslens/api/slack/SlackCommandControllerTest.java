package com.opslens.api.slack;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.opslens.application.slack.InvalidSlackCommandException;
import com.opslens.application.slack.SlackCommandService;
import com.opslens.application.slack.SlackCommandService.SlackCommandResult;

@WebMvcTest(SlackCommandController.class)
class SlackCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SlackCommandService slackCommandService;

    @Test
    void acceptsSlackCommandAndReturnsJobMessage() throws Exception {
        when(slackCommandService.handle(any()))
            .thenReturn(new SlackCommandResult(
                "JOB-ABC123",
                "INC-20260913-SOURCE-MISSING-INST_02",
                "ephemeral",
                "Analysis job started. Job ID: JOB-ABC123, Incident: INC-20260913-SOURCE-MISSING-INST_02"
            ));

        mockMvc.perform(post("/api/slack/commands")
                .contentType("application/json")
                .content("""
                    {
                      "userId": "U123",
                      "channelId": "C123",
                      "text": "analyze INC-20260913-SOURCE-MISSING-INST_02",
                      "responseUrl": "https://hooks.slack.test/response",
                      "threadTs": "1726700000.000100"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jobId").value("JOB-ABC123"))
            .andExpect(jsonPath("$.incidentNo").value("INC-20260913-SOURCE-MISSING-INST_02"))
            .andExpect(jsonPath("$.responseType").value("ephemeral"))
            .andExpect(jsonPath("$.text").value("Analysis job started. Job ID: JOB-ABC123, Incident: INC-20260913-SOURCE-MISSING-INST_02"));
    }

    @Test
    void acceptsSlackSlashCommandFormPayload() throws Exception {
        when(slackCommandService.handle(argThat(command ->
            command.userId().equals("U123")
                && command.channelId().equals("C123")
                && command.text().equals("analyze INC-20260913-SOURCE-MISSING-INST_02")
                && command.responseUrl().equals("https://hooks.slack.test/response")
                && command.threadTs().equals("1726700000.000100")
        )))
            .thenReturn(new SlackCommandResult(
                "JOB-FORM123",
                "INC-20260913-SOURCE-MISSING-INST_02",
                "ephemeral",
                "Analysis job started. Job ID: JOB-FORM123, Incident: INC-20260913-SOURCE-MISSING-INST_02"
            ));

        mockMvc.perform(post("/api/slack/commands")
                .contentType("application/x-www-form-urlencoded")
                .param("user_id", "U123")
                .param("channel_id", "C123")
                .param("text", "analyze INC-20260913-SOURCE-MISSING-INST_02")
                .param("response_url", "https://hooks.slack.test/response")
                .param("thread_ts", "1726700000.000100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jobId").value("JOB-FORM123"))
            .andExpect(jsonPath("$.text").value("Analysis job started. Job ID: JOB-FORM123, Incident: INC-20260913-SOURCE-MISSING-INST_02"));
    }

    @Test
    void returnsBadRequestForInvalidCommand() throws Exception {
        when(slackCommandService.handle(any()))
            .thenThrow(new InvalidSlackCommandException("Slack command must include an incident number."));

        mockMvc.perform(post("/api/slack/commands")
                .contentType("application/json")
                .content("""
                    {
                      "userId": "U123",
                      "channelId": "C123",
                      "text": "analyze latest"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.responseType").value("ephemeral"))
            .andExpect(jsonPath("$.text").value("Slack command must include an incident number."));
    }
}
