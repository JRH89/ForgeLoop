package io.forgeloop.support.domain;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AuditEventRepository extends JpaRepository<AuditEvent,String>{ long countByTicketIdAndAction(String ticketId,String action); java.util.List<AuditEvent> findByTicketIdOrderByOccurredAtDesc(String ticketId); }
