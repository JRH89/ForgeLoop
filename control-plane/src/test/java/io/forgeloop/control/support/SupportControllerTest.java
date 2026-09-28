package io.forgeloop.control.support;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

/** Exercise JSON binding, HTTP authorization failures and the request boundary together. */
class SupportControllerTest extends SupportServiceTest {
    @Test void httpReceiptsNeverExposeDatabaseCapabilityHashes() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new SupportController(service,identity)).addFilters(new SupportRequestFilter()).build();
        var result=mvc.perform(post("/api/support/tickets").header("X-ForgeLoop-Support","1").contentType("application/json")
            .content("{\"name\":\"Guest\",\"email\":\"guest@example.com\",\"subject\":\"Help\",\"category\":\"GENERAL\",\"message\":\"Question\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.trackingKey").isString()).andExpect(jsonPath("$.detail.ticket.accessHash").doesNotExist()).andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("owner_subject"));
        doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sign in")).when(identity).requireAdministrator();
        mvc.perform(get("/api/support/admin/tickets").header("X-ForgeLoop-Support","1")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/support/tickets").contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
    }
    @Test void loginUsesOnlyFixedReturnDestinations() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new SupportController(service,identity)).build();
        var result=mvc.perform(get("/api/support/login?admin=true&redirect=https://evil.example"))
            .andExpect(status().isFound()).andExpect(header().string("Location","/oauth2/authorization/github")).andReturn();
        assertEquals("/support#admin",result.getRequest().getSession().getAttribute("SUPPORT_RETURN"));
    }
    @Test void recoveryRequestsReturnIdenticalAcceptedResponsesWithoutEnumeratingTickets() throws Exception {
        var guest=service.create(request());
        var mvc=MockMvcBuilders.standaloneSetup(new SupportController(service,identity)).addFilters(new SupportRequestFilter()).build();
        String response=mvc.perform(post("/api/support/tickets/recovery").header("X-ForgeLoop-Support","1").contentType("application/json")
                .content("{\"ticketId\":\""+guest.detail().ticket().id()+"\",\"email\":\"customer@example.com\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/support/tickets/recovery").header("X-ForgeLoop-Support","1").contentType("application/json")
                .content("{\"ticketId\":\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\",\"email\":\"customer@example.com\"}"))
                .andExpect(status().isAccepted()).andExpect(content().string(response));
        mvc.perform(post("/api/support/tickets/recovery").header("X-ForgeLoop-Support","1").contentType("application/json")
                .content("{\"ticketId\":\""+guest.detail().ticket().id()+"\",\"email\":\"other@example.com\"}"))
                .andExpect(status().isAccepted()).andExpect(content().string(response));
        verify(recoveryDelivery, times(1)).sendRecovery(eq("customer@example.com"),any());
    }
    @Test void ownerAndStaffHttpFlowsKeepNotesOutOfCustomerResponses() throws Exception {
        when(identity.subject()).thenReturn("github:customer");when(identity.requireSubject()).thenReturn("github:customer");
        var receipt=service.create(request());String id=receipt.detail().ticket().id();
        var mvc=MockMvcBuilders.standaloneSetup(new SupportController(service,identity)).build();
        mvc.perform(get("/api/support/tickets")).andExpect(status().isOk()).andExpect(jsonPath("$.tickets[0].id").value(id));
        mvc.perform(post("/api/support/admin/tickets/"+id+"/replies").contentType("application/json").content("{\"message\":\"Staff secret\",\"internalNote\":true,\"version\":0}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.messages.length()").value(2));
        mvc.perform(get("/api/support/tickets/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.messages.length()").value(1));
        mvc.perform(patch("/api/support/admin/tickets/"+id).contentType("application/json").content("{\"status\":\"IN_PROGRESS\",\"version\":1}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.ticket.status").value("IN_PROGRESS"));
        when(identity.subject()).thenReturn("github:other");
        mvc.perform(get("/api/support/tickets/"+id)).andExpect(status().isNotFound());
        verify(identity,atLeast(2)).requireAdministrator();
    }
}
