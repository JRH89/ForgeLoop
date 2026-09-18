package io.forgeloop.support.application;
import io.forgeloop.support.domain.*; import static org.junit.jupiter.api.Assertions.*; import static org.mockito.Mockito.*; import java.util.*; import org.junit.jupiter.api.*;
class TicketCreationServiceTest { Organization org=new Organization("o","Org"); Member admin=new Member("a","Admin",Role.ADMIN,org), member=new Member("m","Member",Role.MEMBER,org); TicketRepository tickets=mock(TicketRepository.class); MemberRepository members=mock(MemberRepository.class); TicketCreationService service=new TicketCreationService(tickets,members);
 @Test void adminCreatesScopedTicket(){when(members.findById("a")).thenReturn(Optional.of(admin));when(tickets.save(any(Ticket.class))).thenAnswer(call->call.getArgument(0));Ticket ticket=service.create("a","Export is failing");assertEquals("Export is failing",ticket.getTitle());assertEquals("o",ticket.getOrganizationId());}
 @Test void memberCannotCreateTicket(){when(members.findById("m")).thenReturn(Optional.of(member));assertThrows(AssignmentForbiddenException.class,()->service.create("m","Nope"));}
}
