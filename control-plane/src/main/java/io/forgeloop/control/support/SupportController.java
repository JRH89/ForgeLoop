package io.forgeloop.control.support;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Public endpoints authorize every ticket independently; staff routes never accept guest capabilities. */
@RestController
@RequestMapping("/api/support")
public class SupportController {
    private final SupportService service;
    private final SupportIdentity identity;
    public SupportController(SupportService service,SupportIdentity identity){this.service=service;this.identity=identity;}
    @GetMapping("/session") public Map<String,Boolean> session(){return Map.of("authenticated",identity.subject()!=null,"administrator",identity.administrator(),"recoveryAvailable",service.recoveryAvailable());}
    @GetMapping("/login") public ResponseEntity<Void> login(HttpServletRequest request,@RequestParam(defaultValue="false") boolean admin){
        request.getSession().setAttribute("SUPPORT_RETURN",admin?"/support#admin":"/support#mine");
        return ResponseEntity.status(302).header("Location","/oauth2/authorization/github").build();
    }
    @PostMapping(value="/tickets",consumes="application/json") @ResponseStatus(HttpStatus.CREATED)
    public SupportService.Receipt create(@RequestBody SupportService.Create input){return service.create(input);}
    @PostMapping(value="/tickets/recovery",consumes="application/json")
    public ResponseEntity<Map<String,String>> requestRecovery(@RequestBody(required=false) SupportService.RecoveryRequest input){
        service.requestRecovery(input);
        return ResponseEntity.accepted().body(Map.of("message","If a matching guest ticket can be recovered, instructions will be sent to its email address."));
    }
    @PostMapping(value="/tickets/recovery/confirm",consumes="application/json")
    public SupportService.Receipt confirmRecovery(@RequestBody(required=false) Map<String,String> input){
        return service.confirmRecovery(input==null?null:input.get("token"));
    }
    @GetMapping("/tickets") public SupportService.TicketPage mine(@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page){return service.list(false,status,q,page);}
    @GetMapping("/tickets/{id}") public SupportService.Detail ticket(@PathVariable String id,@RequestHeader(value="X-Support-Key",required=false) String key){return service.get(id,key,false);}
    @PostMapping(value="/tickets/{id}/replies",consumes="application/json") public SupportService.Detail reply(@PathVariable String id,@RequestHeader(value="X-Support-Key",required=false) String key,@RequestBody SupportService.Reply input){return service.reply(id,key,false,input);}
    @GetMapping("/admin/tickets") public SupportService.TicketPage inbox(@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page){return service.list(true,status,q,page);}
    @GetMapping("/admin/tickets/{id}") public SupportService.Detail staffTicket(@PathVariable String id){return service.get(id,null,true);}
    @PostMapping(value="/admin/tickets/{id}/replies",consumes="application/json") public SupportService.Detail staffReply(@PathVariable String id,@RequestBody SupportService.Reply input){return service.reply(id,null,true,input);}
    @PatchMapping(value="/admin/tickets/{id}",consumes="application/json") public SupportService.Detail status(@PathVariable String id,@RequestBody SupportService.Status input){return service.status(id,input);}
    @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<Map<String,String>> problem(ResponseStatusException error){
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("error",error.getReason()==null?"Request failed":error.getReason()));
    }
}
