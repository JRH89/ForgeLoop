package io.forgeloop.runner;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

/** Proof stays local; only its non-secret hash appears in browser history. */
public final class PairingRequest {
    private final String verifier;
    private final String challenge;
    public PairingRequest() {
        byte[] random=new byte[32];new SecureRandom().nextBytes(random);
        verifier=HexFormat.of().formatHex(random);
        try {challenge=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public String verifier(){return verifier;}
    public String fingerprint(){return challenge.substring(0,12);}
    public URI approvalUri(URI endpoint,String name) {
        validateEndpoint(endpoint);
        if(name==null||name.isBlank()||name.length()>100)throw new IllegalArgumentException("Enter a runner name (up to 100 characters)");
        return endpoint.resolve("/app/runner-connect#challenge="+challenge+"&name="+URLEncoder.encode(name,StandardCharsets.UTF_8));
    }
    public static void validateEndpoint(URI endpoint){
        boolean local="http".equals(endpoint.getScheme())&&java.util.Set.of("localhost","127.0.0.1","[::1]").contains(endpoint.getHost()==null?"":endpoint.getHost());
        if(endpoint.getHost()==null||endpoint.getUserInfo()!=null||endpoint.getQuery()!=null||endpoint.getFragment()!=null||(!"https".equals(endpoint.getScheme())&&!local)||!(endpoint.getPath().isEmpty()||endpoint.getPath().equals("/")))throw new IllegalArgumentException("Use the HTTPS ForgeLoop origin (HTTP is allowed only on localhost)");
    }
    @Override public String toString(){return "PairingRequest[fingerprint="+fingerprint()+"]";}
}
