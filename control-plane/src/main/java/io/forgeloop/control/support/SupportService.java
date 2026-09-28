package io.forgeloop.control.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/** Ownership uses immutable principals or a high-entropy bearer capability, never an unverified email. */
@Service
public class SupportService {
    private static final Logger log=LoggerFactory.getLogger(SupportService.class);
    private static final java.time.Duration RECOVERY_LIFETIME=java.time.Duration.ofMinutes(30);
    private static final java.time.Duration RECOVERY_WINDOW=java.time.Duration.ofHours(1);
    private static final long MAX_RECOVERY_EMAILS_PER_TICKET=3;
    public static final Set<String> STATUSES=Set.of("OPEN","IN_PROGRESS","WAITING_ON_CUSTOMER","RESOLVED","CLOSED");
    public static final Set<String> CATEGORIES=Set.of("GENERAL","BUG","BILLING","ACCOUNT","FEATURE");
    private final SupportRepository repository;
    private final SupportIdentity identity;
    private final SupportRecoveryDelivery recoveryDelivery;
    private final SecureRandom random=new SecureRandom();
    public SupportService(SupportRepository repository, SupportIdentity identity, SupportRecoveryDelivery recoveryDelivery) {
        this.repository=repository;this.identity=identity;this.recoveryDelivery=recoveryDelivery;
    }
    public record Create(String name,String email,String subject,String category,String message,String website) {}
    public record Reply(String message,boolean internalNote,long version) {}
    public record Status(String status,long version) {}
    public record Summary(String id,String name,String email,String subject,String category,String status,Instant emailVerifiedAt,Instant createdAt,Instant updatedAt,long version) {}
    public record Detail(Summary ticket,List<SupportRepository.Message> messages) {}
    public record Receipt(Detail detail,String trackingKey) {}
    public record RecoveryRequest(String ticketId,String email) {}
    public record TicketPage(List<Summary> tickets,boolean hasMore,int page) {}
    public boolean recoveryAvailable(){return recoveryDelivery.available();}
    @Transactional public Receipt create(Create input) {
        if(input==null || (input.website()!=null&&!input.website().isBlank()))throw bad("Invalid submission");
        String name=text(input.name(),"Name",100),email=text(input.email(),"Email",254);
        if(!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw bad("Enter a valid email address");
        String subject=text(input.subject(),"Subject",160),message=text(input.message(),"Message",8000);
        if(input.category()==null||!CATEGORIES.contains(input.category()))throw bad("Choose a valid category");
        byte[] bytes=new byte[32];random.nextBytes(bytes);String key=HexFormat.of().formatHex(bytes);
        Instant now=Instant.now();
        var ticket=new SupportRepository.Ticket(UUID.randomUUID().toString(),identity.subject(),digest(key),name,email,subject,input.category(),"OPEN",null,now,now,0);
        repository.create(ticket);repository.message(ticket.id(),identity.subject(),"CUSTOMER",message,false,now);
        return new Receipt(detail(ticket,false),key);
    }
    /** Always behaves identically for unknown tickets, wrong addresses, signed-in tickets, and throttled requests. */
    @Transactional public void requestRecovery(RecoveryRequest input) {
        if(!recoveryDelivery.available()||input==null||input.ticketId()==null||!input.ticketId().matches("[a-f0-9-]{36}")
                ||input.email()==null||input.email().length()>254||!input.email().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))return;
        Instant now=Instant.now();
        var ticket=repository.find(input.ticketId(),true).orElse(null);
        if(ticket==null||ticket.owner()!=null||!ticket.email().strip().equalsIgnoreCase(input.email().strip()))return;
        if(repository.recoveryCountSince(ticket.id(),now.minus(RECOVERY_WINDOW))>=MAX_RECOVERY_EMAILS_PER_TICKET)return;

        // Invalidate a previously emailed proof before creating a replacement.
        repository.expirePendingRecoveries(ticket.id(),now);
        repository.purgeOldRecoveries(now.minus(java.time.Duration.ofDays(30)));
        String token=randomKey();
        repository.createRecovery(new SupportRepository.Recovery(UUID.randomUUID().toString(),ticket.id(),digest(token),now,now.plus(RECOVERY_LIFETIME),null));
        sendRecoveryAfterCommit(ticket.email(),token);
    }
    /** Redeeming the email proof rotates the ticket capability and makes the proof single-use. */
    @Transactional public Receipt confirmRecovery(String token) {
        if(token==null||!token.matches("[a-f0-9]{64}"))throw recoveryInvalid();
        Instant now=Instant.now();
        var recovery=repository.findRecovery(digest(token),true).orElseThrow(SupportService::recoveryInvalid);
        if(recovery.consumedAt()!=null||!recovery.expiresAt().isAfter(now))throw recoveryInvalid();
        var ticket=find(recovery.ticketId(),true);
        if(ticket.owner()!=null)throw recoveryInvalid();
        String replacement=randomKey();
        repository.expirePendingRecoveries(ticket.id(),now);
        repository.rotateAccessKey(ticket.id(),digest(replacement));
        repository.verifyRequesterEmail(ticket.id(),now);
        return new Receipt(detail(find(ticket.id(),false),false),replacement);
    }
    @Transactional(readOnly=true) public Detail get(String id,String key,boolean staff) {
        if(staff)identity.requireAdministrator();
        var ticket=find(id,false);if(!staff)authorize(ticket,key);
        return detail(ticket,staff);
    }
    @Transactional(readOnly=true) public TicketPage list(boolean staff,String status,String query,int page) {
        if(staff)identity.requireAdministrator();else identity.requireSubject();
        if(page<0||page>10000||query==null||query.length()>160)throw bad("Invalid search or page");
        if(status==null||(!status.isEmpty()&&!STATUSES.contains(status)))throw bad("Invalid status");
        var rows=repository.list(staff?"":identity.requireSubject(),status,query.strip(),page);
        return new TicketPage(rows.stream().limit(25).map(this::summary).toList(),rows.size()>25,page);
    }
    @Transactional public Detail reply(String id,String key,boolean staff,Reply input) {
        if(staff)identity.requireAdministrator();
        var ticket=find(id,true);if(!staff)authorize(ticket,key);
        if(input==null)throw bad("A reply is required");
        version(ticket,input.version());
        if(input.internalNote()&&!staff)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only support staff can add internal notes");
        if(ticket.status().equals("CLOSED"))throw bad("This ticket is closed. Submit a new request if you need more help.");
        String next=!staff&&(ticket.status().equals("RESOLVED")||ticket.status().equals("WAITING_ON_CUSTOMER"))?"OPEN":ticket.status();
        if(repository.messageCount(id)+(next.equals(ticket.status())?1:2)>200)throw bad("This thread is full. Please open a follow-up ticket.");
        String body=text(input.message(),"Reply",8000);Instant now=Instant.now();
        repository.message(id,identity.subject(),staff?"SUPPORT":"CUSTOMER",body,input.internalNote(),now);
        if(!next.equals(ticket.status()))repository.message(id,identity.subject(),"SYSTEM","Status changed to "+next,false,now.plusNanos(1000));
        repository.update(id,next,now);return detail(find(id,false),staff);
    }
    @Transactional public Detail status(String id,Status input) {
        identity.requireAdministrator();var ticket=find(id,true);
        if(input==null||input.status()==null||!STATUSES.contains(input.status()))throw bad("Invalid status");
        version(ticket,input.version());
        if(!ticket.status().equals(input.status())){
            if(repository.messageCount(id)>=200)throw bad("This thread is full");
            Instant now=Instant.now();repository.message(id,identity.subject(),"SYSTEM","Status changed from "+ticket.status()+" to "+input.status(),false,now);
            repository.update(id,input.status(),now);
        }
        return detail(find(id,false),true);
    }
    private void authorize(SupportRepository.Ticket ticket,String key) {
        String subject=identity.subject();
        if(subject!=null&&subject.equals(ticket.owner()))return;
        if(key!=null&&key.matches("[a-f0-9]{64}")&&MessageDigest.isEqual(digest(key).getBytes(StandardCharsets.US_ASCII),ticket.accessHash().getBytes(StandardCharsets.US_ASCII)))return;
        throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Ticket not found or access link is invalid");
    }
    private SupportRepository.Ticket find(String id,boolean lock) {
        if(id==null||!id.matches("[a-f0-9-]{36}"))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Ticket not found or access link is invalid");
        return repository.find(id,lock).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Ticket not found or access link is invalid"));
    }
    private void version(SupportRepository.Ticket ticket,long version) {
        if(ticket.version()!=version)throw new ResponseStatusException(HttpStatus.CONFLICT,"Ticket changed. Refresh it before sending again; your draft has been kept.");
    }
    private Summary summary(SupportRepository.Ticket t) { return new Summary(t.id(),t.name(),t.email(),t.subject(),t.category(),t.status(),t.emailVerifiedAt(),t.createdAt(),t.updatedAt(),t.version()); }
    private Detail detail(SupportRepository.Ticket t,boolean staff) { return new Detail(summary(t),repository.messages(t.id(),staff)); }
    private static String text(String value,String field,int maximum) {
        if(value==null||value.isBlank()||value.strip().length()>maximum||value.indexOf('\0')>=0)throw bad(field+" is required and must be at most "+maximum+" characters");
        return value.strip();
    }
    static String digest(String value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private String randomKey(){byte[] bytes=new byte[32];random.nextBytes(bytes);return HexFormat.of().formatHex(bytes);}
    private void sendRecoveryAfterCommit(String email,String token) {
        Runnable send=()->{
            try{recoveryDelivery.sendRecovery(email,token);}
            catch(RuntimeException failure){log.warn("Support recovery email delivery failed ({}); no ticket data or token logged",failure.getClass().getSimpleName());}
        };
        if(TransactionSynchronizationManager.isSynchronizationActive()){
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
                @Override public void afterCommit(){send.run();}
            });
        }else send.run();
    }
    private static ResponseStatusException recoveryInvalid(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Recovery link is invalid or expired");}
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
}
