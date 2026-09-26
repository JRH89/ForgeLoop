package io.forgeloop.runner;

import com.sun.jna.*;
import com.sun.jna.ptr.*;
import com.sun.jna.platform.win32.Crypt32Util;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Uses OS-protected storage and never falls back to plaintext or secret command arguments. */
public final class DesktopSecretStore {
    private final Path directory;
    public DesktopSecretStore(Path directory){this.directory=directory;}
    public void save(String secret) throws Exception {
        if(secret==null||secret.isBlank()||secret.length()>8192||secret.contains("\n")||secret.contains("\r"))throw new IllegalArgumentException("Enter a valid provider API key");
        byte[] bytes=secret.getBytes(StandardCharsets.UTF_8);
        try {
            if(Platform.isWindows())Files.write(directory.resolve("provider-key.dpapi"),Crypt32Util.cryptProtectData(bytes));
            else if(Platform.isMac())macSave(bytes);
            else linux(List.of("secret-tool","store","--label=ForgeLoop Runner provider key","application","forgeloop-runner","installation",directory.toString()),bytes);
        } finally {Arrays.fill(bytes,(byte)0);}
    }
    public String load() throws Exception {
        byte[] bytes;
        if(Platform.isWindows())bytes=Crypt32Util.cryptUnprotectData(Files.readAllBytes(directory.resolve("provider-key.dpapi")));
        else if(Platform.isMac())bytes=macLoad();
        else bytes=linux(List.of("secret-tool","lookup","application","forgeloop-runner","installation",directory.toString()),new byte[0]);
        try {String result=new String(bytes,StandardCharsets.UTF_8).stripTrailing();if(result.isBlank())throw new IllegalStateException("Provider key not found; save it again");return result;}
        finally{Arrays.fill(bytes,(byte)0);}
    }
    private static byte[] linux(List<String> command,byte[] input)throws Exception{
        Process process=new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try(var stdin=process.getOutputStream()){stdin.write(input);}
        if(!process.waitFor(30,TimeUnit.SECONDS)){process.destroyForcibly();throw new IllegalStateException("Unlock your desktop keyring and retry");}
        if(process.exitValue()!=0)throw new IllegalStateException("Install libsecret tools and unlock your desktop keyring; plaintext fallback is disabled");
        return process.getInputStream().readNBytes(8193);
    }
    /** Keychain API receives byte buffers, so no key appears in a process listing. */
    interface Keychain extends Library {
        int SecKeychainFindGenericPassword(Pointer keychain,int serviceLength,byte[] service,int accountLength,byte[] account,IntByReference length,PointerByReference data,PointerByReference item);
        int SecKeychainAddGenericPassword(Pointer keychain,int serviceLength,byte[] service,int accountLength,byte[] account,int length,byte[] data,PointerByReference item);
        int SecKeychainItemModifyAttributesAndData(Pointer item,Pointer attributes,int length,byte[] data);
        int SecKeychainItemFreeContent(Pointer attributes,Pointer data);
    }
    interface CoreFoundation extends Library {void CFRelease(Pointer reference);}
    private byte[] account(){return directory.toString().getBytes(StandardCharsets.UTF_8);}
    private static byte[] service(){return "io.forgeloop.runner".getBytes(StandardCharsets.UTF_8);}
    private void macSave(byte[] bytes){
        Keychain api=Native.load("Security",Keychain.class);var item=new PointerByReference();byte[] service=service(),account=account();
        int status=api.SecKeychainFindGenericPassword(null,service.length,service,account.length,account,null,null,item);
        if(status==-25300)status=api.SecKeychainAddGenericPassword(null,service.length,service,account.length,account,bytes.length,bytes,null);
        else if(status==0){try{status=api.SecKeychainItemModifyAttributesAndData(item.getValue(),null,bytes.length,bytes);}finally{Native.load("CoreFoundation",CoreFoundation.class).CFRelease(item.getValue());}}
        if(status!=0)throw new IllegalStateException("Keychain denied access ("+status+"); unlock it and retry");
    }
    private byte[] macLoad(){
        Keychain api=Native.load("Security",Keychain.class);var data=new PointerByReference();var length=new IntByReference();byte[] service=service(),account=account();
        int status=api.SecKeychainFindGenericPassword(null,service.length,service,account.length,account,length,data,null);
        if(status!=0)throw new IllegalStateException("Keychain key unavailable ("+status+"); unlock it and retry");
        try{return data.getValue().getByteArray(0,length.getValue());}finally{api.SecKeychainItemFreeContent(null,data.getValue());}
    }
}
