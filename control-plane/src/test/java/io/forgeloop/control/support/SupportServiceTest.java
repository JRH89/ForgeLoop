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
    SupportIdentity identity;SupportRecoveryDelivery recoveryDelivery;SupportService service;SupportRepository repository;JdbcTemplate db;
    @BeforeEach void setup(){
        var source=new DriverManagerDataSource("jdbc:h2:mem:support-"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V28__customer_support.sql"),new ClassPathResource("db/migration/V30__guest_support_recovery.sql")).execute(source);
        db=new JdbcTemplate(source);repository=spy(new SupportRepository(db));identity=mock(SupportIdentity.class);recoveryDelivery=mock(SupportRecoveryDelivery.class);
        when(recoveryDelivery.available()).thenReturn(true);service=new SupportService(repository,identity,recoveryDelivery);
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
    @Test void emailRecoveryVerifiesAddressRotatesCapabilityAndCannotBeReplayed(){
        var receipt=service.create(request());String id=receipt.detail().ticket().id();
        var capture=org.mockito.ArgumentCaptor.forClass(String.class);
        service.requestRecovery(new SupportService.RecoveryRequest(id,"CUSTOMER@example.com"));
        verify(recoveryDelivery).sendRecovery(eq("customer@example.com"),capture.capture());
        String proof=capture.getValue();assertEquals(64,proof.length());

        clearInvocations(repository);
        var replacement=service.confirmRecovery(proof);
        var lockOrder=inOrder(repository);
        lockOrder.verify(repository).findRecovery(SupportService.digest(proof),false);
        lockOrder.verify(repository).find(id,true);
        lockOrder.verify(repository).findRecovery(SupportService.digest(proof),true);
        assertNotEquals(receipt.trackingKey(),replacement.trackingKey());
        assertNotNull(replacement.detail().ticket().emailVerifiedAt());
        assertNotNull(service.get(id,replacement.trackingKey(),false));
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.get(id,receipt.trackingKey(),false)).getStatusCode().value());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.confirmRecovery(proof)).getStatusCode().value());
    }
    @Test void recoveryIsEnumerationResistantRateLimitedAndLimitedToGuestTickets(){
        var guest=service.create(request());String guestId=guest.detail().ticket().id();
        service.requestRecovery(new SupportService.RecoveryRequest(guestId,"wrong@example.com"));
        service.requestRecovery(new SupportService.RecoveryRequest("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","customer@example.com"));
        verify(recoveryDelivery,never()).sendRecovery(any(),any());

        service.requestRecovery(new SupportService.RecoveryRequest(guestId,"customer@example.com"));
        service.requestRecovery(new SupportService.RecoveryRequest(guestId,"customer@example.com"));
        service.requestRecovery(new SupportService.RecoveryRequest(guestId,"customer@example.com"));
        service.requestRecovery(new SupportService.RecoveryRequest(guestId,"customer@example.com"));
        verify(recoveryDelivery,times(3)).sendRecovery(eq("customer@example.com"),any());

        when(identity.subject()).thenReturn("github:customer");
        var owned=service.create(request());
        service.requestRecovery(new SupportService.RecoveryRequest(owned.detail().ticket().id(),"customer@example.com"));
        verify(recoveryDelivery,times(3)).sendRecovery(eq("customer@example.com"),any());
    }
    @Test void expiredProofAndSupersededProofCannotRotateAccess(){
        var receipt=service.create(request());String id=receipt.detail().ticket().id();
        var first=org.mockito.ArgumentCaptor.forClass(String.class);
        service.requestRecovery(new SupportService.RecoveryRequest(id,"customer@example.com"));
        verify(recoveryDelivery).sendRecovery(eq("customer@example.com"),first.capture());
        service.requestRecovery(new SupportService.RecoveryRequest(id,"customer@example.com"));
        String oldProof=first.getValue();
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.confirmRecovery(oldProof)).getStatusCode().value());

        var second=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(recoveryDelivery,times(2)).sendRecovery(eq("customer@example.com"),second.capture());
        String activeProof=second.getAllValues().getLast();
        db.update("UPDATE support_recovery SET expires_at=? WHERE token_hash=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)),SupportService.digest(activeProof));
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.confirmRecovery(activeProof)).getStatusCode().value());
        assertNotNull(service.get(id,receipt.trackingKey(),false));
    }
    @Test void recoveryEmailIsSentOnlyAfterTheDatabaseTransactionCommits(){
        var receipt=service.create(request());
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try{
            service.requestRecovery(new SupportService.RecoveryRequest(receipt.detail().ticket().id(),"customer@example.com"));
            verify(recoveryDelivery,never()).sendRecovery(any(),any());
            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                    .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
            verify(recoveryDelivery).sendRecovery(eq("customer@example.com"),any());
        }finally{org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();}
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
