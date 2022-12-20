package com.jt;

import com.jt.xml.XmlPatcher;

import org.xml.sax.SAXException;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;


public class AppBuilder {
    /**
     * resize image
     */
    public static BufferedImage resize(BufferedImage source, int targetW, int targetH) {
        int type = source.getType();
        BufferedImage target = null;
        double sx = (double) targetW / source.getWidth();
        double sy = (double) targetH / source.getHeight();
        if (sx > sy) {
            sx = sy;
            targetW = (int) (sx * source.getWidth());
        } else {
            sy = sx;
            targetH = (int) (sy * source.getHeight());
        }
        if (type == BufferedImage.TYPE_CUSTOM) { //handmade
            ColorModel cm = source.getColorModel();
            WritableRaster raster = cm.createCompatibleWritableRaster(targetW, targetH);
            boolean alphaPremultiplied = cm.isAlphaPremultiplied();
            target = new BufferedImage(cm, raster, alphaPremultiplied, null);
        } else {
            target = new BufferedImage(targetW, targetH, type);
        }
        Graphics2D g = target.createGraphics();
        //smoother than exlax:
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawRenderedImage(source, AffineTransform.getScaleInstance(sx, sy));
        g.dispose();
        return target;
    }

    /**
     * outputimg
     */
    public static void saveImageAsJpg(String fromFileStr, String saveToFileStr, int width, int height)
        throws Exception {
        BufferedImage srcImage;
        // String ex = fromFileStr.substring(fromFileStr.indexOf("."),fromFileStr.length());
        String imgType = "JPEG";
        if (fromFileStr.toLowerCase().endsWith(".png")) {
            imgType = "PNG";
        }
        // System.out.println(ex);
        File saveFile = new File(saveToFileStr);
        File fromFile = new File(fromFileStr);
        srcImage = ImageIO.read(fromFile);
        if (width > 0 || height > 0) {
            srcImage = resize(srcImage, width, height);
        }
        ImageIO.write(srcImage, imgType, saveFile);
    }

    public static void buildLogo(File appDir, File logoFile) throws Exception {
        String logoName = "jt_ic_launcher";
        // save image
        File dstFile1 = new File(appDir, "/res/mipmap-hdpi/" + logoName + ".png");
        File dstFile2 = new File(appDir, "/res/mipmap-mdpi/" + logoName + ".png");
        File dstFile3 = new File(appDir, "/res/mipmap-xhdpi/" + logoName + ".png");
        File dstFile4 = new File(appDir, "/res/mipmap-xxhdpi/" + logoName + ".png");
        File dstFile5 = new File(appDir, "/res/mipmap-xxxhdpi/" + logoName + ".png");
        dstFile1.mkdirs();
        dstFile2.mkdirs();
        dstFile3.mkdirs();
        dstFile4.mkdirs();
        dstFile5.mkdirs();

        saveImageAsJpg(logoFile.getAbsolutePath(), dstFile1.getAbsolutePath(), 78, 78);
        saveImageAsJpg(logoFile.getAbsolutePath(), dstFile2.getAbsolutePath(), 48, 48);
        saveImageAsJpg(logoFile.getAbsolutePath(), dstFile3.getAbsolutePath(), 96, 96);
        saveImageAsJpg(logoFile.getAbsolutePath(), dstFile4.getAbsolutePath(), 144, 144);
        saveImageAsJpg(logoFile.getAbsolutePath(), dstFile5.getAbsolutePath(), 192, 192);

        // add resource id to public.xml
        XmlPatcher.addResourceId(appDir, logoName, "mipmap");

        // edit androidManifest.xml
        XmlPatcher.editApplicationAttr(new File(appDir, "AndroidManifest.xml"), "android:icon", "@mipmap/" + logoName);
        XmlPatcher.editApplicationAttr(new File(appDir, "AndroidManifest.xml"), "android:roundIcon", "@mipmap/" + logoName);

    }


    public static void editAppName(File appDir, String appName) throws IOException, ParserConfigurationException, TransformerException, SAXException {
        XmlPatcher.editApplicationAttr(new File(appDir, "AndroidManifest.xml"), "android:label", appName);
    }
}
