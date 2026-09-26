package io.forgeloop.runner;

import java.net.URI;
import java.math.BigDecimal;
import java.util.Map;

/** Serializable settings deliberately exclude provider keys and pairing proofs. */
public record DesktopConfiguration(String endpoint,String provider,String model,BigDecimal inputUsdPerMillion,BigDecimal outputUsdPerMillion,boolean startAtLogin) {
    public DesktopConfiguration(String endpoint,String provider,String model,BigDecimal inputUsdPerMillion,BigDecimal outputUsdPerMillion){this(endpoint,provider,model,inputUsdPerMillion,outputUsdPerMillion,false);}
    public DesktopConfiguration {
        PairingRequest.validateEndpoint(URI.create(endpoint));
        if(!Map.of("anthropic","ANTHROPIC_API_KEY","openai","OPENAI_API_KEY","gemini","GEMINI_API_KEY").containsKey(provider))throw new IllegalArgumentException("Select a supported provider");
        if(model==null||model.isBlank()||model.length()>200)throw new IllegalArgumentException("Enter a model ID");
        if(inputUsdPerMillion==null||outputUsdPerMillion==null||inputUsdPerMillion.signum()<0||outputUsdPerMillion.signum()<0)throw new IllegalArgumentException("Prices must be nonnegative USD per million tokens");
    }
    public String keyVariable(){return Map.of("anthropic","ANTHROPIC_API_KEY","openai","OPENAI_API_KEY","gemini","GEMINI_API_KEY").get(provider);}
    public Map<String,Object> policy(){return Map.of("default",new ProviderExecutionPolicy(provider,model,2,inputUsdPerMillion,outputUsdPerMillion));}
}
