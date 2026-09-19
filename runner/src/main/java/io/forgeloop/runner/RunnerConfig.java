package io.forgeloop.runner;
import java.net.URI; import java.util.List;
/** Runner-local configuration; provider and Git credentials remain outside this object. */
public record RunnerConfig(URI controlPlane,String registrationToken,String name,String version,List<String> capabilities){public RunnerConfig{if(controlPlane==null||registrationToken==null||registrationToken.isBlank()||name==null||name.isBlank()||version==null||version.isBlank()||capabilities==null||capabilities.isEmpty())throw new IllegalArgumentException("Runner configuration is incomplete");capabilities=List.copyOf(capabilities);}}
