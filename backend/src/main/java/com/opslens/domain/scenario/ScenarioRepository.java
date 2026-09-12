package com.opslens.domain.scenario;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScenarioRepository extends JpaRepository<Scenario, Long> {

    List<Scenario> findByScenarioTypeOrderByInjectedAtDesc(ScenarioType scenarioType);
}
