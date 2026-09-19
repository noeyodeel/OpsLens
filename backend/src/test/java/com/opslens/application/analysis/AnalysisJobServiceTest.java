package com.opslens.application.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.TestTransaction;

import com.opslens.domain.analysis.AnalysisJobRepository;
import com.opslens.domain.analysis.AnalysisJobStatus;
import com.opslens.domain.incident.AnomalyType;
import com.opslens.domain.incident.DetectionRule;
import com.opslens.domain.incident.DetectionRuleRepository;
import com.opslens.domain.incident.Incident;
import com.opslens.domain.incident.IncidentRepository;
import com.opslens.domain.incident.IncidentSeverity;
import com.opslens.domain.incident.MetricType;
import com.opslens.domain.incident.ThresholdType;

@DataJpaTest(showSql = false)
@Import(AnalysisJobService.class)
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AnalysisJobServiceTest {

    @Autowired
    private DetectionRuleRepository detectionRuleRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private AnalysisJobRepository analysisJobRepository;

    @Autowired
    private AnalysisJobService analysisJobService;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void setUp() {
        DetectionRule rule = detectionRuleRepository.save(new DetectionRule(
            "Institution daily data missing",
            "treatment_records",
            MetricType.SOURCE_COUNT,
            ThresholdType.EQUALS_ZERO,
            new BigDecimal("0.0000")
        ));
        incidentRepository.save(new Incident(
            "INC-20260913-SOURCE-MISSING-INST_02",
            rule,
            IncidentSeverity.CRITICAL,
            "treatment_records",
            "INST_02",
            AnomalyType.SOURCE_MISSING,
            "INST_02 treatment records were not received.",
            Instant.parse("2026-09-14T00:00:00Z")
        ));
    }

    @Test
    void createsPendingJobAndPublishesMessage() {
        var view = analysisJobService.requestAnalysis(new AnalysisJobService.AnalysisJobRequest(
            "INC-20260913-SOURCE-MISSING-INST_02",
            "slack:U123",
            "Analyze this incident",
            "C123",
            "1726700000.000100",
            "https://hooks.slack.test/response"
        ));

        assertThat(view.jobId()).startsWith("JOB-");
        assertThat(view.incidentNo()).isEqualTo("INC-20260913-SOURCE-MISSING-INST_02");
        assertThat(view.status()).isEqualTo(AnalysisJobStatus.PENDING);
        assertThat(analysisJobRepository.findAll()).hasSize(1);

        TestTransaction.flagForCommit();
        TestTransaction.end();

        ArgumentCaptor<AnalysisJobMessage> messageCaptor = ArgumentCaptor.forClass(AnalysisJobMessage.class);
        verify(rabbitTemplate).convertAndSend(
            eq(AnalysisJobQueueConfig.EXCHANGE_NAME),
            eq(AnalysisJobQueueConfig.ROUTING_KEY),
            messageCaptor.capture()
        );

        AnalysisJobMessage message = messageCaptor.getValue();
        assertThat(message.jobId()).isEqualTo(view.jobId());
        assertThat(message.incidentNo()).isEqualTo("INC-20260913-SOURCE-MISSING-INST_02");
        assertThat(message.requestText()).isEqualTo("Analyze this incident");
    }

    @Test
    void updatesJobStatus() {
        var job = analysisJobService.requestAnalysis(new AnalysisJobService.AnalysisJobRequest(
            "INC-20260913-SOURCE-MISSING-INST_02",
            null,
            null,
            null,
            null,
            null
        ));

        var running = analysisJobService.updateStatus(
            job.jobId(),
            new AnalysisJobService.AnalysisJobStatusUpdate(AnalysisJobStatus.RUNNING, null)
        );
        var failed = analysisJobService.updateStatus(
            job.jobId(),
            new AnalysisJobService.AnalysisJobStatusUpdate(AnalysisJobStatus.FAILED, "LLM timeout")
        );

        assertThat(running.status()).isEqualTo(AnalysisJobStatus.RUNNING);
        assertThat(running.startedAt()).isNotNull();
        assertThat(failed.status()).isEqualTo(AnalysisJobStatus.FAILED);
        assertThat(failed.errorMessage()).isEqualTo("LLM timeout");
        assertThat(failed.completedAt()).isNotNull();
    }
}
