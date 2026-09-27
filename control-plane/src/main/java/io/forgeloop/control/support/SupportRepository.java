package io.forgeloop.control.support;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Parameterized persistence; row locking serializes replies and status changes per ticket. */
@Repository
public class SupportRepository {
    private final JdbcTemplate db;
    public SupportRepository(JdbcTemplate db) { this.db = db; }
    public record Ticket(String id, String owner, String accessHash, String name, String email, String subject,
                         String category, String status, Instant createdAt, Instant updatedAt, long version) {}
    public record Message(String id, String authorKind, String body, boolean internalNote, Instant createdAt) {}
    private Ticket ticket(ResultSet r, int row) throws SQLException {
        return new Ticket(r.getString("id"), r.getString("owner_subject"), r.getString("access_hash"),
                r.getString("requester_name"), r.getString("requester_email"), r.getString("subject"),
                r.getString("category"), r.getString("status"), r.getTimestamp("created_at").toInstant(),
                r.getTimestamp("updated_at").toInstant(), r.getLong("version"));
    }
    public void create(Ticket t) {
        db.update("INSERT INTO support_ticket(id,owner_subject,access_hash,requester_name,requester_email,subject,category,status,created_at,updated_at,version) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                t.id(),t.owner(),t.accessHash(),t.name(),t.email(),t.subject(),t.category(),t.status(),
                java.sql.Timestamp.from(t.createdAt()),java.sql.Timestamp.from(t.updatedAt()),t.version());
    }
    public Optional<Ticket> find(String id, boolean lock) {
        return db.query("SELECT * FROM support_ticket WHERE id=?" + (lock ? " FOR UPDATE" : ""), this::ticket, id).stream().findFirst();
    }
    public List<Ticket> list(String owner, String status, String query, int page) {
        String search = "%" + query.toLowerCase(java.util.Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_") + "%";
        return db.query("SELECT * FROM support_ticket WHERE (?='' OR owner_subject=?) AND (?='' OR status=?) AND (LOWER(subject) LIKE ? ESCAPE '!' OR LOWER(requester_email) LIKE ? ESCAPE '!' OR id=?) ORDER BY updated_at DESC,id LIMIT 26 OFFSET ?",
                this::ticket,owner,owner,status,status,search,search,query,page*25);
    }
    public List<Message> messages(String id, boolean staff) {
        return db.query("SELECT * FROM support_message WHERE ticket_id=? AND (?=TRUE OR internal_note=FALSE) ORDER BY created_at,id",
                (r,n)->new Message(r.getString("id"),r.getString("author_kind"),r.getString("body"),r.getBoolean("internal_note"),r.getTimestamp("created_at").toInstant()),id,staff);
    }
    public int messageCount(String id) { return db.queryForObject("SELECT COUNT(*) FROM support_message WHERE ticket_id=?",Integer.class,id); }
    public void message(String id, String author, String kind, String body, boolean internal, Instant at) {
        db.update("INSERT INTO support_message(id,ticket_id,author_subject,author_kind,body,internal_note,created_at) VALUES(?,?,?,?,?,?,?)",
                java.util.UUID.randomUUID().toString(),id,author,kind,body,internal,java.sql.Timestamp.from(at));
    }
    public void update(String id, String status, Instant at) {
        db.update("UPDATE support_ticket SET status=?,updated_at=?,version=version+1 WHERE id=?",status,java.sql.Timestamp.from(at),id);
    }
}
