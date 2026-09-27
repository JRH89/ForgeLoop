package io.forgeloop.control.support;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.web.server.ResponseStatusException;

class SupportServiceTest {
    SupportIdentity identity;SupportService service;SupportRepository repository;JdbcTemplate db;
    @BeforeEach void setup(){
        var source=new DriverManagerDataSource("jdbc:h2:mem:support-"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V28__customer_support.sql")).execute(source);
        db=new JdbcTemplate(source);repository=new SupportRepository(db);identity=mock(SupportIdentity.class);service=new SupportService(repository,identity);
    }
    SupportService.Create request(){return new SupportService.Create("Customer","customer@example.com","Runner will not connect","BUG","Expected a connection; received an error.","");}
    @Test void guestGetsUnstoredCapabilityAndPersistentThread(){
        var receipt=service.create(request());String id=receipt.detail().ticket().id();
        assertEquals(64,receipt.trackingKey().length());assertEquals("OPEN",receipt.detail().ticket().status());
        assertNotEquals(receipt.trackingKey(),repository.find(id,false).orElseThrow().accessHash());
        assertEquals(1,service.get(id,receipt.trackingKey(),false).messages().size());
        assertEquals("customer@example.com",service.get(id,receipt.trackingKey(),false).ticket().email());
    }
    @Test void missingWrongKeysAndOtherPrincipalsCannotRead(){
        var receipt=service.create(request());String id=receipt.detail().ticket().id();
        when(identity.subject()).thenReturn("github:other");
        for(String key:new String[]{null,"bad","f".repeat(64)})assertEquals(404,assertThrows(ResponseStatusException.class,()->service.get(id,key,false)).getStatusCode().value());
    }
    @Test void authenticatedOwnerCanReadWithoutKeyButSameEmailDoesNotGrantOwnership(){
        when(identity.subject()).thenReturn("github:1");var a=service.create(request());
        assertNotNull(service.get(a.detail().ticket().id(),null,false));
        when(identity.subject()).thenReturn("github:2");var b=service.create(request());
        when(identity.requireSubject()).thenReturn("github:2");
        var mine=service.list(false,"","",0);assertEquals(1,mine.tickets().size());assertEquals(b.detail().ticket().id(),mine.tickets().getFirst().id());
        assertThrows(ResponseStatusException.class,()->service.get(a.detail().ticket().id(),null,false));
    }
    @Test void staffNotesStayPrivateAndStatusChangesAreRecorded(){
        var receipt=service.create(request());String id=receipt.detail().ticket().id();
        var note=service.reply(id,null,true,new SupportService.Reply("Private triage note",true,0));
        assertEquals(2,note.messages().size());assertEquals(1,service.get(id,receipt.trackingKey(),false).messages().size());
        var updated=service.status(id,new SupportService.Status("WAITING_ON_CUSTOMER",1));
        assertEquals("WAITING_ON_CUSTOMER",updated.ticket().status());
        var reply=service.reply(id,receipt.trackingKey(),false,new SupportService.Reply("Here are the steps",false,2));
        assertEquals("OPEN",reply.ticket().status());assertTrue(reply.messages().stream().anyMatch(m->m.body().equals("Status changed to OPEN")));
        verify(identity,atLeast(2)).requireAdministrator();
    }
    @Test void customerCannotPostPrivateNotesOrChangeStatus(){
        var receipt=service.create(request());String id=receipt.detail().ticket().id();
        assertEquals(403,assertThrows(ResponseStatusException.class,()->service.reply(id,receipt.trackingKey(),false,new SupportService.Reply("secret",true,0))).getStatusCode().value());
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN)).when(identity).requireAdministrator();
        assertThrows(ResponseStatusException.class,()->service.status(id,new SupportService.Status("CLOSED",0)));
        assertThrows(ResponseStatusException.class,()->service.list(true,"","",0));
        assertThrows(ResponseStatusException.class,()->service.get(id,receipt.trackingKey(),true));
    }
    @Test void staleWritesConflictAndClosedTicketsRejectReplies(){
        var receipt=service.create(request());String id=receipt.detail().ticket().id();
        service.reply(id,receipt.trackingKey(),false,new SupportService.Reply("Follow-up",false,0));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.reply(id,receipt.trackingKey(),false,new SupportService.Reply("stale",false,0))).getStatusCode().value());
        service.status(id,new SupportService.Status("CLOSED",1));
        assertThrows(ResponseStatusException.class,()->service.reply(id,receipt.trackingKey(),false,new SupportService.Reply("closed",false,2)));
    }
    @Test void validationAndHoneypotRejectInvalidRequests(){
        assertThrows(ResponseStatusException.class,()->service.create(new SupportService.Create("A","a@b.com","B",null,"C","")));
        assertThrows(ResponseStatusException.class,()->service.create(new SupportService.Create("A","bad","B","BUG","C","")));
        assertThrows(ResponseStatusException.class,()->service.create(new SupportService.Create("A","a@b.com","B","BUG","C","spam")));
        assertThrows(ResponseStatusException.class,()->service.create(new SupportService.Create("A","a@b.com","B","BUG","x".repeat(8001),"")));
        assertThrows(ResponseStatusException.class,()->service.list(true,"INVALID","",0));
        assertThrows(ResponseStatusException.class,()->service.list(true,"","",-1));
    }
    @Test void nullStatusIsBadRequestAndReopenReservesRoomForAuditEvent(){
        var receipt=service.create(request());String id=receipt.detail().ticket().id();
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.status(id,new SupportService.Status(null,0))).getStatusCode().value());
        service.status(id,new SupportService.Status("RESOLVED",0));
        for(int i=2;i<199;i++)repository.message(id,null,"CUSTOMER","Earlier message",false,java.time.Instant.now());
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.reply(id,receipt.trackingKey(),false,new SupportService.Reply("Reopen",false,1))).getStatusCode().value());
        assertEquals(199,repository.messageCount(id));
    }
    @Test void inboxIsPagedAndSearchWildcardsAreLiteral(){
        for(int i=0;i<27;i++)service.create(request());
        assertEquals(25,service.list(true,"","",0).tickets().size());assertTrue(service.list(true,"","",0).hasMore());
        assertEquals(2,service.list(true,"","",1).tickets().size());assertFalse(service.list(true,"","",1).hasMore());
        assertEquals(0,service.list(true,"","%",0).tickets().size());
        assertEquals(25,service.list(true,"OPEN","Runner",0).tickets().size());
    }
}
