package io.forgeloop.runner;
import org.junit.jupiter.api.Test;
import java.net.URI;
import static org.junit.jupiter.api.Assertions.*;
class PairingRequestTest {
    @Test void browserNeverReceivesSecret(){var request=new PairingRequest();String url=request.approvalUri(URI.create("https://forge.example"),"My laptop & server").toString();assertFalse(url.contains(request.verifier()));assertTrue(url.contains(request.fingerprint()));assertFalse(request.toString().contains(request.verifier()));assertEquals(64,request.verifier().length());assertNotEquals(request.verifier(),new PairingRequest().verifier());}
    @Test void rejectsInsecureAndAmbiguousOrigins(){for(String origin:new String[]{"http://remote.example","https://user:password@example.com","https://example.com/path","https://example.com?token=1"})assertThrows(IllegalArgumentException.class,()->PairingRequest.validateEndpoint(URI.create(origin)));PairingRequest.validateEndpoint(URI.create("http://localhost:8090"));}
}
