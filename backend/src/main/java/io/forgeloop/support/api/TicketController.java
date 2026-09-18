package io.forgeloop.support.api;
import io.forgeloop.support.application.*; import io.forgeloop.support.domain.*; import org.springframework.graphql.data.method.annotation.*; import org.springframework.stereotype.Controller; import java.util.*;
@Controller public class TicketController {
 private final TicketRepository tickets; private final MemberRepository members; private final AuditEventRepository audits; private final TicketAssignmentService assignments; private final TicketCreationService creations;
 public TicketController(TicketRepository tickets,MemberRepository members,AuditEventRepository audits,TicketAssignmentService assignments,TicketCreationService creations){this.tickets=tickets;this.members=members;this.audits=audits;this.assignments=assignments;this.creations=creations;}
 @QueryMapping public Ticket ticket(@Argument String id){return tickets.findById(id).orElseThrow(()->new IllegalArgumentException("Ticket not found"));}
 @QueryMapping public List<Ticket> tickets(){return tickets.findAll();}
 @QueryMapping public List<Member> organizationMembers(@Argument String organizationId){return members.findAll().stream().filter(m->m.getOrganization().getId().equals(organizationId)).toList();}
 @QueryMapping public List<AuditEvent> auditEvents(@Argument String ticketId){return audits.findByTicketIdOrderByOccurredAtDesc(ticketId);}
 @QueryMapping public DeliveryRun deliveryRun(@Argument String featureId){return DeliveryRun.demo(featureId);}
 @MutationMapping public Ticket assignTicket(@Argument String ticketId,@Argument String assigneeId,@ContextValue("actorId") String actorId){return assignments.assign(actorId,ticketId,assigneeId);}
 @MutationMapping public Ticket createTicket(@Argument String title,@ContextValue("actorId") String actorId){return creations.create(actorId,title);}
}
