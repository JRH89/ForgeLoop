package io.forgeloop.runner;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Native launcher formats derived from the same favicon used by the desktop window. */
public final class DesktopIcons {
    private DesktopIcons() { }
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("An output directory is required");
        write(Path.of(args[0]));
    }
    static void write(Path directory) throws Exception {
        BufferedImage source;
        try (var resource = DesktopIcons.class.getResourceAsStream("/desktop/favicon.png")) {
            if (resource == null) throw new IllegalStateException("ForgeLoop favicon is missing");
            source = ImageIO.read(resource);
        }
        if (source == null) throw new IllegalStateException("ForgeLoop favicon is invalid");
        Files.createDirectories(directory);
        Files.write(directory.resolve("forgeloop.png"), png(source, 512));
        int[] sizes = {16, 32, 48, 64, 128, 256};
        byte[][] images = new byte[sizes.length][];
        int length = 6 + 16 * sizes.length;
        for (int index = 0; index < sizes.length; index++) { images[index] = png(source, sizes[index]); length += images[index].length; }
        // ICO directory entries point at PNG images, supported by modern Windows shells.
        var ico = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
        ico.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
        int offset = 6 + 16 * sizes.length;
        for (int index = 0; index < sizes.length; index++) {
            ico.put((byte) sizes[index]).put((byte) sizes[index]).put((byte) 0).put((byte) 0);
            ico.putShort((short) 1).putShort((short) 32).putInt(images[index].length).putInt(offset);
            offset += images[index].length;
        }
        for (byte[] image : images) ico.put(image);
        Files.write(directory.resolve("forgeloop.ico"), ico.array());
        var chunks = new ByteArrayOutputStream();
        String[] types = {"ic07", "ic08", "ic09", "ic10"};
        for (int index = 0; index < types.length; index++) {
            byte[] image = png(source, 128 << index);
            chunks.write(types[index].getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            chunks.write(ByteBuffer.allocate(4).putInt(image.length + 8).array());
            chunks.write(image);
        }
        var icns = ByteBuffer.allocate(8 + chunks.size());
        icns.putInt(0x69636e73).putInt(icns.capacity()).put(chunks.toByteArray());
        Files.write(directory.resolve("forgeloop.icns"), icns.array());
    }
    private static byte[] png(BufferedImage source, int size) throws Exception {
        var scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, 0, 0, size, size, null);
        } finally { graphics.dispose(); }
        var bytes = new ByteArrayOutputStream();
        if (!ImageIO.write(scaled, "png", bytes)) throw new IllegalStateException("PNG encoder unavailable");
        return bytes.toByteArray();
    }
}
