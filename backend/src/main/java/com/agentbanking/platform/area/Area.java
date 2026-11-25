package com.agentbanking.platform.area;

import com.agentbanking.platform.common.AppClock;
import com.agentbanking.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** A geographical operating area supervised by an area manager. */
@Entity
@Table(name = "areas")
@Getter
@Setter
@NoArgsConstructor
public class Area extends BaseEntity {

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 80)
    private String province;

    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal centerLatitude;

    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal centerLongitude;

    @Column(nullable = false)
    private Instant createdAt;

    @Override
    protected void onCreate() {
        createdAt = AppClock.now();
    }
}
