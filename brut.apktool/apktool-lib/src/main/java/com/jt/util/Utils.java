package com.jt.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import brut.androlib.Androlib;
import brut.androlib.AndrolibException;
import brut.common.BrutException;
import brut.directory.Directory;
import brut.directory.DirectoryException;
import brut.directory.ExtFile;
import brut.util.Jar;
import brut.util.OS;

public class Utils {
    public static class FileUtils {
        public static File getAndroidJar(Class clazz) throws IOException, BrutException {
            File file = Jar.getResourceAsFile("/brut/androlib/android.jar", clazz);
            file.setExecutable(true);

            return file;
        }

        public static File getD8Jar(Class clazz) throws IOException, BrutException {
            File file = Jar.getResourceAsFile("/brut/androlib/d8.jar", clazz);
            file.setExecutable(true);
            return file;
        }
    }

    public static class BuildPackage {
        public static File compileJavaToClass(File javaDir, File classDir) throws Exception {
            List<String> cmd = new ArrayList<>();
            cmd.add("javac");
            cmd.add(javaDir.getAbsolutePath() + "\\*.java");
            cmd.add("-d");
            cmd.add(classDir.getAbsolutePath());
            Utils.OSCMD.runCMD(cmd);

            return classDir;
        }

        public static void movDexToPkg(ExtFile appDir, File srcDexFile, File dstDir) throws DirectoryException {
            int maxIdx = 1;
            Map<String, Directory> dirs = appDir.getDirectory().getDirs();
            for (Map.Entry<String, Directory> directory : dirs.entrySet()) {
                String name = directory.getKey();
                if (name.startsWith("smali_classes")) {
                    String[] parts = name.split("smali_classes");
                    int idx = Integer.parseInt(parts[1]);
                    if (idx > maxIdx) {
                        maxIdx = idx;
                    }
                }
            }
            maxIdx++;
            String dexName = "classes" + maxIdx + ".dex";
            File dexFile = new File(dstDir, dexName);
            dexFile.delete();
            srcDexFile.renameTo(dexFile);
        }


        public static File compileClassToJar(File clsDir) throws Exception {
            List<String> cmd = new ArrayList<>();
            cmd.add("jar");
            cmd.add("-cfM");
            File aarJar = new File(clsDir.getParent(), "aar_classes.jar");
            cmd.add(aarJar.getAbsolutePath());
            cmd.add(clsDir.getAbsolutePath());
            Utils.OSCMD.runCMD(cmd);

            return aarJar;
        }

        public static File dx2dexfiles(File jarPath, Class clazz) throws Exception {
            if (!jarPath.exists()) {
                throw new Exception("jar not exist");
            }
            List<String> cmd = new ArrayList<>();
            File d8jar = Utils.FileUtils.getD8Jar(clazz);
            cmd.add("java");
            cmd.add("-jar");
            cmd.add(d8jar.getAbsolutePath());
            cmd.add("--lib");
            File androidJarTmp = Utils.FileUtils.getAndroidJar(Androlib.class);
            File androidJar = new File(androidJarTmp.getParent(), "android.jar");
            androidJarTmp.renameTo(androidJar);
            cmd.add(androidJar.getAbsolutePath());
            cmd.add("--output");
            cmd.add(jarPath.getParent());
            cmd.add(jarPath.getAbsolutePath());
            Utils.OSCMD.runCMD(cmd);
            File dexFile = new File(jarPath.getParent(), "classes.dex");
            return dexFile;
        }
    }


    public static class OSCMD {
        private final static Logger LOGGER = Logger.getLogger(Utils.class.getName());

        public static void runCMD(List<String> cmd) throws AndrolibException {
            try {
                OS.exec(cmd.toArray(new String[0]));
                LOGGER.fine("command ran: " + cmd.toString());
//                LOGGER.info(cmd.toString());
            } catch (BrutException ex) {
                throw new AndrolibException(ex);
            }
        }
    }
}
