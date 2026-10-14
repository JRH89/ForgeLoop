package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DesktopIconsTest {
    @Test void bundlesTheApprovedCurrentBrandMark() throws Exception {
        // Pin the reviewed web/runner source asset so old branding cannot ship silently.
        try (var source = DesktopIcons.class.getResourceAsStream("/desktop/favicon.png")) {
            assertNotNull(source);
            String digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(source.readAllBytes()));
            assertEquals("e20001b3d232659174f15f68b0c4ccfc96c67ceb2119d53eb1afb43e8f0f008d", digest);
        }
    }
    @TempDir Path directory;
    @Test void providesSharpDeviceResolutionVariantsForSidebarBrand() {
        var icon=DesktopIcons.displayIcon(30);
        assertEquals(30,icon.getIconWidth());
        var image=(java.awt.image.MultiResolutionImage)icon.getImage();
        assertEquals(4,image.getResolutionVariants().size());
        assertEquals(90,image.getResolutionVariant(90,90).getWidth(null));
        assertEquals(120,image.getResolutionVariant(120,120).getWidth(null));
    }
    @Test void encodesNativeIconsFromBundledFavicon() throws Exception {
        DesktopIcons.write(directory);
        assertEquals(512, ImageIO.read(directory.resolve("forgeloop.png").toFile()).getWidth());
        var rounded=ImageIO.read(directory.resolve("forgeloop.png").toFile());
        assertEquals(0,rounded.getRGB(0,0)>>>24,"Launcher corners must be transparent");
        assertEquals(0,rounded.getRGB(20,20)>>>24,"Corners must be heavily rounded");
        assertTrue((rounded.getRGB(256,256)>>>24)>0,"Brand mark remains visible");
        byte[] bytes = Files.readAllBytes(directory.resolve("forgeloop.ico"));
        var ico = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0, ico.getShort()); assertEquals(1, ico.getShort()); assertEquals(6, ico.getShort());
        for (int expected : new int[]{16,32,48,64,128,256}) {
            int width = Byte.toUnsignedInt(ico.get()); int height = Byte.toUnsignedInt(ico.get());
            assertEquals(expected % 256, width); assertEquals(width, height);
            ico.getShort(); assertEquals(1, ico.getShort()); assertEquals(32, ico.getShort());
            int length = ico.getInt(), offset = ico.getInt();
            var image = ImageIO.read(new ByteArrayInputStream(bytes, offset, length));
            assertEquals(expected, image.getWidth()); assertEquals(expected, image.getHeight());
        }
        var icns = ByteBuffer.wrap(Files.readAllBytes(directory.resolve("forgeloop.icns")));
        assertEquals(0x69636e73, icns.getInt()); assertEquals(icns.capacity(), icns.getInt());
        for (int index = 0; index < 4; index++) {
            assertEquals(new int[]{0x69633037,0x69633038,0x69633039,0x69633130}[index], icns.getInt());
            byte[] png = new byte[icns.getInt() - 8]; icns.get(png);
            assertEquals(128 << index, ImageIO.read(new ByteArrayInputStream(png)).getWidth());
        }
        assertFalse(icns.hasRemaining());
        byte[] original = Files.readAllBytes(directory.resolve("forgeloop.ico"));
        DesktopIcons.write(directory);
        assertArrayEquals(original, Files.readAllBytes(directory.resolve("forgeloop.ico")));
    }
}
