package io.forgeloop.control.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Runs the real GitHub-mode filter chain: a cross-site GET must not be able to end an operator session. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:logout-method;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
    "forgeloop.security.mode=github", "forgeloop.github.client-id=Iv1.test", "forgeloop.github.client-secret=test-secret"
})
class LogoutMethodTest {
    @Autowired WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach void securedMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @Test void getDoesNotSignOperatorsOut() throws Exception {
        MockHttpSession session = new MockHttpSession();
        var response = mvc.perform(get("/logout").session(session)).andReturn().getResponse();
        assertFalse(session.isInvalid(), "GET /logout must not invalidate the session");
        assertNotEquals("/", response.getRedirectedUrl());
    }

    @Test void postSignsOperatorsOutAndReturnsHome() throws Exception {
        MockHttpSession session = new MockHttpSession();
        var response = mvc.perform(post("/logout").session(session)).andReturn().getResponse();
        assertTrue(session.isInvalid());
        assertEquals(302, response.getStatus());
        assertEquals("/", response.getRedirectedUrl());
    }
}
