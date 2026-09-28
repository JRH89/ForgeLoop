package io.forgeloop.control.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

class GithubLoginSuccessHandlerTest {
    @Test void successfulSupportLoginReturnsToInboxAndConsumesMarker() throws Exception {
        var provisioner=mock(GithubLoginProvisioner.class);
        var user=new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")),Map.of("id",123),"id");
        var authentication=new OAuth2AuthenticationToken(user,user.getAuthorities(),"github");
        var handler=new GithubLoginSuccessHandler(provisioner);
        for(String target:List.of("/support#admin","/support#mine","https://evil.example")){
            var request=new MockHttpServletRequest();var response=new MockHttpServletResponse();
            request.getSession().setAttribute("SUPPORT_RETURN",target);
            handler.onAuthenticationSuccess(request,response,authentication);
            assertEquals(target.startsWith("/support#")?target:"/app",response.getRedirectedUrl());
            assertNull(request.getSession().getAttribute("SUPPORT_RETURN"));
        }
        verify(provisioner,times(3)).requireMembership("123", null);
    }
}
