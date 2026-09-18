package io.forgeloop.support.application;
import io.forgeloop.support.domain.*; import jakarta.transaction.Transactional; import org.springframework.stereotype.Service;
@Service public class TicketAssignmentService {
  private final TicketRepository tickets; private final MemberRepository members; private final AuditEventRepository audits;
  public TicketAssignmentService(TicketRepository tickets, MemberRepository members, AuditEventRepository audits){this.tickets=tickets;this.members=members;this.audits=audits;}
  @Transactional public Ticket assign(String actorId,String ticketId,String assigneeId){
    Ticket ticket=tickets.findById(ticketId).orElseThrow(()->new IllegalArgumentException("Ticket not found"));
    Member actor=members.findById(actorId).orElseThrow(()->new AssignmentForbiddenException("Unknown actor"));
    Member assignee=members.findById(assigneeId).orElseThrow(()->new IllegalArgumentException("Assignee not found"));
    if(actor.getRole()!=Role.ADMIN || !actor.getOrganization().getId().equals(ticket.getOrganization().getId())) throw new AssignmentForbiddenException("Only an organization administrator may assign this ticket");
    if(!assignee.getOrganization().getId().equals(ticket.getOrganization().getId())) throw new AssignmentForbiddenException("Assignee must belong to the ticket organization");
    ticket.assignTo(assignee); audits.save(new AuditEvent(ticketId,actorId,assigneeId)); return tickets.save(ticket);
  }
}
