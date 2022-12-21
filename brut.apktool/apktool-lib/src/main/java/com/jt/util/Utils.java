package com.jt.util;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import brut.androlib.Androlib;
import brut.androlib.AndrolibException;
import brut.common.BrutException;
import brut.directory.Directory;
import brut.directory.DirectoryException;
import brut.directory.ExtFile;
import brut.util.Jar;
import brut.util.OS;

public class Utils {
    private final static Logger LOGGER = Logger.getLogger(Utils.class.getName());

    public static class FileUtils {
        public static File getAndroidJar(Class clazz) throws IOException, BrutException {
            File file = Jar.getResourceAsFile("/brut/androlib/android.jar", clazz);
            file.setExecutable(true);

            return file;
        }

        public static void trimWhitespace(Node node) {
            NodeList children = node.getChildNodes();
            for (int i = 0; i < children.getLength(); ++i) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.TEXT_NODE) {
                    child.setTextContent(child.getTextContent().trim());
                }
                trimWhitespace(child);
            }
        }

        public static File getD8Jar(Class clazz) throws IOException, BrutException {
            File file = Jar.getResourceAsFile("/brut/androlib/d8.jar", clazz);
            file.setExecutable(true);
            return file;
        }

        public static void replacePkgName(Path rootPath, String oldName, String newName) throws IOException {
            String exclude1 = new File(rootPath.toString(), "build").toString();
            String exclude2 = new File(rootPath.toString(), "dist").toString();
            String exclude3 = new File(rootPath.toString(), "original").toString();
            String smaliPath = new File(rootPath.toString(), "smali").toString();
            String oldSmaliName = oldName.replace(".", "/");
            String newSmaliName = newName.replace(".", "/");
            LOGGER.info("Start replacing the package name...");
            ArrayList<String> excludeSmali = new ArrayList<>();
            ArrayList<String> excludeOrgSmali = new ArrayList<>();
            Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String filePath = file.toAbsolutePath().toString();
                    String fileName = file.getFileName().toString();

                    Pattern pattern = Pattern.compile("R(\\.|\\$).*smali");
                    Matcher matcher = pattern.matcher(fileName);

                    boolean isMatch = matcher.matches();

                    if (filePath.startsWith(exclude1) || filePath.startsWith(exclude2) || filePath.startsWith(exclude3) || isMatch) {
                        return FileVisitResult.CONTINUE;
                    }

                    byte[] bytes = Files.readAllBytes(file);
                    // read content
                    String content = new String(bytes, StandardCharsets.ISO_8859_1);

                    // write content
                    String replaced = content.replace(oldName, newName);

                    // smali additional handling
                    if (filePath.startsWith(smaliPath) && replaced.contains(oldSmaliName)) {
                        replaced = replaced.replace(oldSmaliName, newSmaliName);
                        // if contains load so
                        if (replaced.contains("Ljava/lang/System;->load")) {
                            // add to exclude file
                            String[] lines = replaced.split("\\r?\\n");
                            String[] values = lines[0].split(" ");
                            if (values.length > 0) {
                                String str1 = values[values.length - 1];
                                String str2 = str1.replace("L", "")
                                    .replace("/", ".")
                                    .replace(";", "");

                                excludeSmali.add(str1);
                                excludeSmali.add(str2);

                                excludeOrgSmali.add(str1.replace(newSmaliName, oldSmaliName));
                                excludeOrgSmali.add(str2.replace(newName, oldName));
                            }
                        }
                    }

                    // 写入替换后的内容
                    Files.write(file, replaced.getBytes(StandardCharsets.ISO_8859_1), StandardOpenOption.TRUNCATE_EXISTING);

                    return FileVisitResult.CONTINUE;
                }
            });

            // need to Restore
            if (excludeSmali.size() > 0) {
                // Restore package names to manifest
                Path manifest = new File(rootPath.toString(), "AndroidManifest.xml").toPath();
                byte[] bytes = Files.readAllBytes(manifest);
                // read content
                String content = new String(bytes, StandardCharsets.ISO_8859_1);

                for (int i = 0; i < excludeSmali.size(); i++) {
                    String exclude = excludeSmali.get(i);
                    content = content.replace(exclude, excludeOrgSmali.get(i));
                }
                Files.write(manifest, content.getBytes(StandardCharsets.ISO_8859_1), StandardOpenOption.TRUNCATE_EXISTING);

                // Restore package names to smali folder
                Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String fileString = file.toAbsolutePath().toString();
                        if (fileString.startsWith(smaliPath)) {
                            byte[] bytes = Files.readAllBytes(file);
                            // read content
                            String content = new String(bytes, StandardCharsets.ISO_8859_1);
                            if (content.contains(newSmaliName)) {
                                // start replace
                                for (String exclude : excludeSmali) {
                                    content = content.replace(exclude, exclude.replace(newSmaliName, oldSmaliName));
                                }
                            }
                            // 写入替换后的内容
                            Files.write(file, content.getBytes(StandardCharsets.ISO_8859_1), StandardOpenOption.TRUNCATE_EXISTING);
                            return FileVisitResult.CONTINUE;
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
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
