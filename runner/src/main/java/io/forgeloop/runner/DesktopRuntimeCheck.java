package io.forgeloop.runner;
import java.nio.file.*;

/** Offline packaged-runtime verification: temporary fake key only, no identity or model API. */
public final class DesktopRuntimeCheck {
    private DesktopRuntimeCheck(){}
    public static void verify()throws Exception{
        Path directory=Files.createTempDirectory("forgeloop-runtime-check-");DesktopFiles.protect(directory);
        DesktopSecretStore store=new DesktopSecretStore(directory,"check");
        try{
            store.save("fake-packaged-runtime-check");
            if(!"fake-packaged-runtime-check".equals(store.load()))throw new IllegalStateException("Native credential round trip failed");
            store.save("rotated-fake-runtime-check");
            if(!"rotated-fake-runtime-check".equals(store.load()))throw new IllegalStateException("Native credential rotation failed");
        }finally{store.remove();Files.deleteIfExists(directory);}
        System.out.println("Packaged runtime and native key storage passed; no provider calls.");
    }
}
