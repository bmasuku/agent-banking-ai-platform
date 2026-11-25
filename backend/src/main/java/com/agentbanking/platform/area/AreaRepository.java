package com.agentbanking.platform.area;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AreaRepository extends JpaRepository<Area, UUID> {

    Optional<Area> findByCode(String code);

    boolean existsByCode(String code);

    List<Area> findAllByOrderByName();
}
