package io.forgeloop.control.support;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SupportRequestFilterTest {
    MockHttpServletRequest request(){var r=new MockHttpServletRequest("POST","/api/support/tickets");r.addHeader("X-ForgeLoop-Support","1");r.addHeader("Host","localhost:5173");r.addHeader("Origin","http://localhost:5173");return r;}
    @Test void blocksCrossOriginAndMissingCustomHeader()throws Exception{
        var filter=new SupportRequestFilter();var r=request();r.removeHeader("X-ForgeLoop-Support");var response=new MockHttpServletResponse();filter.doFilter(r,response,(a,b)->fail());assertEquals(403,response.getStatus());
        r=request();r.removeHeader("Origin");r.addHeader("Origin","https://attacker.invalid");response=new MockHttpServletResponse();filter.doFilter(r,response,(a,b)->fail());assertEquals(403,response.getStatus());
    }
    @Test void boundsChunkedBodyAndPreservesNormalJson()throws Exception{
        var filter=new SupportRequestFilter();var r=request();r.setContent("{}".getBytes());var response=new MockHttpServletResponse();filter.doFilter(r,response,(a,b)->assertEquals("{}",new String(a.getInputStream().readAllBytes())));assertEquals("no-store",response.getHeader("Cache-Control"));
        r=request();r.setContent(new byte[32769]);response=new MockHttpServletResponse();filter.doFilter(r,response,(a,b)->fail());assertEquals(413,response.getStatus());
    }
    @Test void rateLimitsCreatesWithoutTrustingSpoofedForwardedHeaders()throws Exception{
        var filter=new SupportRequestFilter();
        for(int i=0;i<11;i++){var r=request();r.addHeader("X-Forwarded-For","1.2.3."+i);var response=new MockHttpServletResponse();filter.doFilter(r,response,(a,b)->{});assertEquals(i<10?200:429,response.getStatus());}
    }
    @Test void rateLimitsRecoveryEmailsMoreTightlyPerPeer()throws Exception{
        var filter=new SupportRequestFilter();
        for(int i=0;i<6;i++){var r=request();r.setRequestURI("/api/support/tickets/recovery");var response=new MockHttpServletResponse();filter.doFilter(r,response,(a,b)->{});assertEquals(i<5?200:429,response.getStatus());}
    }
}
