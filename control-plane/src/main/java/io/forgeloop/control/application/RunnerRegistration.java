package io.forgeloop.control.application;
import java.util.List;
public record RunnerRegistration(String token,String name,String version,List<String> capabilities){public RunnerRegistration{if(token==null||token.isBlank()||name==null||name.isBlank()||version==null||version.isBlank()||capabilities==null||capabilities.isEmpty())throw new IllegalArgumentException("Token, name, version, and capabilities are required");capabilities=List.copyOf(capabilities);}}
