package com.seasonplan.bootstrap;

import com.seasonplan.audit.api.AuditTrail;
import com.seasonplan.audit.application.AuditLogService;
import com.seasonplan.audit.application.port.AuditLogRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration
public class AuditConfiguration {
    @Bean
    AuditTrail auditTrail(AuditLogRepository repository, Clock clock) {
        return new AuditLogService(repository, clock);
    }
}
