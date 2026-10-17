import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** 把给定 PNG 转成同尺寸的全透明 PNG：java MakeTransparent.java "原图=输出" ... */
public class MakeTransparent {
    public static void main(String[] args) throws Exception {
        for (String arg : args) {
            String[] parts = arg.split("=", 2);
            BufferedImage src = ImageIO.read(new File(parts[0]));
            if (src == null) {
                throw new IllegalStateException("读不到图片：" + parts[0]);
            }
            BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
            File out = new File(parts[1]);
            if (out.getParentFile() != null) {
                out.getParentFile().mkdirs();
            }
            ImageIO.write(dst, "PNG", out);
            System.out.println(parts[1] + " <- " + src.getWidth() + "x" + src.getHeight());
        }
    }
}
