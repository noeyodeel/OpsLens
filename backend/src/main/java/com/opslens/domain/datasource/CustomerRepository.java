package com.opslens.domain.datasource;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByCustomerNo(String customerNo);

    List<Customer> findBySourceSystem(SourceSystem sourceSystem);
}
