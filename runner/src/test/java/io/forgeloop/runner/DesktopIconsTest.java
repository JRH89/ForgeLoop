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
    @TempDir Path directory;
    @Test void encodesNativeIconsFromBundledFavicon() throws Exception {
        DesktopIcons.write(directory);
        assertEquals(512, ImageIO.read(directory.resolve("forgeloop.png").toFile()).getWidth());
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
