package io.forgeloop.control.support;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.net.URI;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Same-origin custom-header boundary, bounded JSON bodies and conservative per-peer abuse limits. */
@Component
@Order(1)
public class SupportRequestFilter extends OncePerRequestFilter {
    private record Window(long minute,int count) {}
    private final ConcurrentHashMap<String,Window> requests=new ConcurrentHashMap<>();
    @Override protected boolean shouldNotFilter(HttpServletRequest r){return !r.getRequestURI().startsWith("/api/support/");}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws IOException,ServletException {
        response.setHeader("Cache-Control","no-store");response.setHeader("X-Robots-Tag","noindex");
        if(request.getRequestURI().equals("/api/support/login")&&request.getMethod().equals("GET")){chain.doFilter(request,response);return;}
        if(!"1".equals(request.getHeader("X-ForgeLoop-Support"))||"cross-site".equals(request.getHeader("Sec-Fetch-Site"))||!sameOrigin(request)){
            reject(response,403,"Use the same-origin support form");return;
        }
        long minute=Instant.now().getEpochSecond()/60;
        String path=request.getRequestURI();
        boolean create=request.getMethod().equals("POST")&&path.equals("/api/support/tickets");
        boolean recoveryStart=request.getMethod().equals("POST")&&path.equals("/api/support/tickets/recovery");
        boolean recoveryConfirm=request.getMethod().equals("POST")&&path.equals("/api/support/tickets/recovery/confirm");
        String key=request.getRemoteAddr()+":"+(create?"create":recoveryStart?"recovery-start":recoveryConfirm?"recovery-confirm":request.getMethod().equals("GET")?"read":"write");
        requests.entrySet().removeIf(entry->entry.getValue().minute()!=minute);
        if(requests.size()>=10000&&!requests.containsKey(key)){reject(response,429,"Support is busy. Try again shortly.");return;}
        Window window=requests.compute(key,(k,old)->new Window(minute,old==null||old.minute()!=minute?1:old.count()+1));
        int limit=create?10:recoveryStart?5:recoveryConfirm?10:request.getMethod().equals("GET")?240:30;
        if(window.count()>limit){
            response.setHeader("Retry-After","60");reject(response,429,"Too many support requests. Try again in a minute.");return;
        }
        if(request.getMethod().equals("POST")||request.getMethod().equals("PATCH")){
            byte[] body=request.getInputStream().readNBytes(32769);
            if(body.length>32768){reject(response,413,"Support request is too large");return;}
            chain.doFilter(new HttpServletRequestWrapper(request){
                @Override public ServletInputStream getInputStream(){
                    ByteArrayInputStream input=new ByteArrayInputStream(body);
                    return new ServletInputStream(){public int read(){return input.read();}public boolean isFinished(){return input.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener listener){throw new UnsupportedOperationException("Synchronous JSON endpoint");}};
                }
                @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),java.nio.charset.StandardCharsets.UTF_8));}
            },response);
        } else chain.doFilter(request,response);
    }
    private boolean sameOrigin(HttpServletRequest request){
        String origin=request.getHeader("Origin");if(origin==null)return true;
        try{
            URI uri=URI.create(origin),host=URI.create(request.getScheme()+"://"+request.getHeader("Host"));
            int port=uri.getPort()<0?("https".equals(uri.getScheme())?443:80):uri.getPort();
            int hostPort=host.getPort()<0?("https".equals(host.getScheme())?443:80):host.getPort();
            return uri.getScheme().equals(request.getScheme())&&uri.getHost().equalsIgnoreCase(host.getHost())&&port==hostPort;
        }catch(RuntimeException invalid){return false;}
    }
    private static void reject(HttpServletResponse response,int status,String error)throws IOException{
        response.setStatus(status);response.setContentType("application/json");response.getWriter().write("{\"error\":\""+error+"\"}");
    }
}
