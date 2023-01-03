package com.jt.xml;

import com.jt.util.Utils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Scanner;
import java.util.logging.Logger;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import brut.androlib.res.xml.ResXmlPatcher;
import brut.directory.DirectoryException;
import brut.directory.ExtFile;

public class XmlPatcher {
    // public.xml increment id
    static HashMap<String, Long> auto_id_maps = new HashMap<>();

    public static Long getCanUsePublicId(File file, String type) throws IOException, ParserConfigurationException, SAXException, XPathExpressionException {
        Long id = auto_id_maps.get(type);
        long maxId = 0;
        Document doc = null;
        // 如果在字典中没有查询到类型存储id，则通过public.xml遍历获取到最大id
        if (auto_id_maps.get(type) == null) {
            doc = loadDocument(file);
            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList nodes = (NodeList) xPath.evaluate("//public[@type='" + type + "']", doc, XPathConstants.NODESET);
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                NamedNodeMap attrs = node.getAttributes();
                Node idNode = attrs.getNamedItem("id");
                String idVal = idNode.getNodeValue();
                long currentId = Long.parseLong(idVal.replace("0x", ""), 16);
                if (currentId > maxId) {
                    maxId = currentId;
                }
            }
        } else {
            maxId = id;
        }
        if (maxId == 0) {
            // If no type id is found, create one
            if (doc == null) {
                doc = loadDocument(file);
            }
            Node resources = doc.getElementsByTagName("resources").item(0);
            NodeList childs = resources.getChildNodes();
            Node lastNode = childs.item(childs.getLength() - 2);
            NamedNodeMap map = lastNode.getAttributes();
            Node idAttr = map.getNamedItem("id");
            maxId = ((Long.parseLong(idAttr.getNodeValue().replace("0x", ""), 16) >> 16) + 1) << 16;
        }

        // 当前id+1
        maxId++;
        auto_id_maps.put(type, maxId);
        return maxId;
    }

    public static String getPackageName(File appDir) {
        File publicFile = new File(appDir, "AndroidManifest.xml");
        Document doc = null;
        try {
            doc = loadDocument(publicFile);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }
        Node manifest = doc.getFirstChild();
        NamedNodeMap attr = manifest.getAttributes();
        Node vPackage = attr.getNamedItem("package");
        return vPackage.getNodeValue();
    }

    public static void addActivity(File appDir, String pkgName, String actName) throws IOException, ParserConfigurationException, SAXException, XPathExpressionException, TransformerException {
        File manifestFile = new File(appDir, "AndroidManifest.xml");

        Document doc = loadDocument(manifestFile);

        XPath xpath = XPathFactory.newInstance().newXPath();

//        Node mainNode = (Node) xpath.evaluate("//intent-filter/action[@name='android.intent.action.MAIN']", doc, XPathConstants.NODE);
        Node mainNode = (Node) xpath.evaluate("//intent-filter/category[@name='android.intent.category.LAUNCHER']/preceding-sibling::action[@name='android.intent.action.MAIN']", doc, XPathConstants.NODE);
        Node launcherNode = (Node) xpath.evaluate("//intent-filter/category[@name='android.intent.category.LAUNCHER']", doc, XPathConstants.NODE);
        if (mainNode == null || launcherNode == null) {
            LOGGER.warning("not found node");
            return;
        }


        // edit data
        NamedNodeMap mainNodeMap = mainNode.getAttributes();
        Node nameAttr = mainNodeMap.getNamedItem("android:name");
        nameAttr.setNodeValue(actName);
        mainNodeMap = launcherNode.getAttributes();
        nameAttr = mainNodeMap.getNamedItem("android:name");
        nameAttr.setNodeValue("android.intent.category.DEFAULT");
//        mainNode.getParentNode().removeChild(mainNode);

        // add new android.intent.action.MAIN
        Node appNode = (Node) xpath.evaluate("//application", doc, XPathConstants.NODE);

        // activity
        org.w3c.dom.Element activity = doc.createElement("activity");
        activity.setAttribute("android:exported", "true");
        activity.setAttribute("android:name", pkgName + ".MainActivity");

        // create intent-filter
        org.w3c.dom.Element intentFilter = doc.createElement("intent-filter");

        // create action
        org.w3c.dom.Element action = doc.createElement("action");
        action.setAttribute("android:name", "android.intent.action.MAIN");
        intentFilter.appendChild(action);

        // create category
        org.w3c.dom.Element category = doc.createElement("category");
        category.setAttribute("android:name", "android.intent.category.LAUNCHER");
        intentFilter.appendChild(category);

        // create meta-data
        org.w3c.dom.Element metaData = doc.createElement("meta-data");
        metaData.setAttribute("android:name", "android.app.lib_name");
        metaData.setAttribute("android:value", "");

        activity.appendChild(intentFilter);
        activity.appendChild(metaData);
        appNode.appendChild(activity);
        saveDocument(manifestFile, doc);

    }

    public static void editApplicationAttr(File manifest, String attrName, String attrVal) throws IOException, ParserConfigurationException, SAXException, TransformerException {

        Document doc = loadDocument(manifest);
        Node application = doc.getElementsByTagName("application").item(0);

        // load attr
        NamedNodeMap attrMap = application.getAttributes();
        Node attrNode = attrMap.getNamedItem(attrName);
        if (attrNode != null) {
            attrNode.setNodeValue(attrVal);
            saveDocument(manifest, doc);
        }
    }

    public static void addResourceId(File appDir, String name, String type) throws IOException, ParserConfigurationException, SAXException, XPathExpressionException, TransformerException {
        File publicFile = new File(appDir, "res/values/public.xml");
        Document doc = loadDocument(publicFile);
        long id = XmlPatcher.getCanUsePublicId(publicFile, type);
        XPath xPath = XPathFactory.newInstance().newXPath();
        NodeList nodes = (NodeList) xPath.evaluate("//public[@type='" + type + "' and @name='" + name + "']", doc, XPathConstants.NODESET);
        if (nodes.getLength() == 0) {
            Node resources = doc.getElementsByTagName("resources").item(0);
            Element publicTag = doc.createElement("public");
            publicTag.setAttribute("type", type);
            publicTag.setAttribute("name", name);
            publicTag.setAttribute("id", "0x" + Long.toHexString(id));
            resources.appendChild(publicTag);
            saveDocument(publicFile, doc);
        }
    }

    public static void mergeXmlData(File appDir, ExtFile RFile) throws IOException, ParserConfigurationException, SAXException, XPathExpressionException, DirectoryException {
        File publicFile = new File(appDir, "res/values/public.xml");
        Document doc = loadDocument(publicFile);
        XPath xPath = XPathFactory.newInstance().newXPath();
        InputStream in = RFile.getDirectory().getFileInput("R.txt");
        Scanner scanner = new Scanner(in);
        ArrayList<String> idTags = new ArrayList<>();
        // read line
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            String[] strData = line.split(" ");
            String type = strData[1];
            String name = strData[2];
            // 将R.txt的数据，合并入public.xml和ids.xml
            long id = XmlPatcher.getCanUsePublicId(publicFile, type);
            NodeList nodes = (NodeList) xPath.evaluate("//public[@type='" + type + "' and @name='" + name + "']", doc, XPathConstants.NODESET);

            if (nodes.getLength() == 0) {
                Node resources = doc.getElementsByTagName("resources").item(0);
                Element publicTag = doc.createElement("public");
                if (type.equals("id")) {
                    // need to append ids.xml
                    idTags.add(name);
                }
                publicTag.setAttribute("type", type);
                publicTag.setAttribute("name", name);
                publicTag.setAttribute("id", "0x" + Long.toHexString(id));
                resources.appendChild(publicTag);
            }
        }
        try {
            File idsFile = new File(appDir, "res/values/ids.xml");
            Document idsDoc = loadDocument(idsFile);
            Node resources = idsDoc.getElementsByTagName("resources").item(0);
            for (String name : idTags) {
                Element itemTag = idsDoc.createElement("item");
                itemTag.setAttribute("type", "id");
                itemTag.setAttribute("name", name);
                resources.appendChild(itemTag);
            }

            saveDocument(publicFile, doc);
            saveDocument(idsFile, idsDoc);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @param file File to save Document to (ie AndroidManifest.xml)
     * @param doc  Document being saved
     * @throws IOException
     * @throws SAXException
     * @throws ParserConfigurationException
     * @throws TransformerException
     */
    private static void saveDocument(File file, Document doc)
        throws IOException, SAXException, ParserConfigurationException, TransformerException {
        // clear space
        Utils.FileUtils.trimWhitespace(doc.getFirstChild());

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");

        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(file);

        transformer.transform(source, result);

    }

    /**
     * @param file File to load into Document
     * @return Document
     * @throws IOException
     * @throws SAXException
     * @throws ParserConfigurationException
     */
    private static Document loadDocument(File file)
        throws IOException, SAXException, ParserConfigurationException {

        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        docFactory.setFeature(FEATURE_DISABLE_DOCTYPE_DECL, true);
        docFactory.setFeature(FEATURE_LOAD_DTD, false);

        try {
            docFactory.setAttribute(ACCESS_EXTERNAL_DTD, " ");
            docFactory.setAttribute(ACCESS_EXTERNAL_SCHEMA, " ");
        } catch (IllegalArgumentException ex) {
            LOGGER.warning("JAXP 1.5 Support is required to validate XML");
        }

        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        // Not using the parse(File) method on purpose, so that we can control when
        // to close it. Somehow parse(File) does not seem to close the file in all cases.
        try (FileInputStream inputStream = new FileInputStream(file)) {
            return docBuilder.parse(inputStream);
        }
    }


    // Finds key in strings.xml file and update value

    public static boolean UpdateValueFromStrings(File directory, String key, String value) {
        if (key == null) {
            return false;
        }

        File file = new File(directory, "build/aar/res/values/values.xml");
        key = key.replace("@string/", "");

        if (file.exists()) {
            try {
                Document doc = loadDocument(file);
                XPath xPath = XPathFactory.newInstance().newXPath();
                XPathExpression expression = xPath.compile("/resources/string[@name=" + '"' + key + "\"]");

                Node result = (Node) expression.evaluate(doc, XPathConstants.NODE);
                result.setTextContent(value);

                if (result != null) {
//                    return (String) result;
                    saveDocument(file, doc);
                    return true;
                }

            } catch (SAXException | ParserConfigurationException | IOException | XPathExpressionException ignored) {
            } catch (TransformerException e) {
                e.printStackTrace();
            } finally {
                return false;
            }
        }
        return false;
    }


    private static final String ACCESS_EXTERNAL_DTD = "http://javax.xml.XMLConstants/property/accessExternalDTD";
    private static final String ACCESS_EXTERNAL_SCHEMA = "http://javax.xml.XMLConstants/property/accessExternalSchema";
    private static final String FEATURE_LOAD_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd";
    private static final String FEATURE_DISABLE_DOCTYPE_DECL = "http://apache.org/xml/features/disallow-doctype-decl";
    private static final Logger LOGGER = Logger.getLogger(ResXmlPatcher.class.getName());
}
