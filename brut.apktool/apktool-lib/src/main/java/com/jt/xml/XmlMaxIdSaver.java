package com.jt.xml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;
import java.util.logging.Logger;

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
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import brut.androlib.res.xml.ResXmlPatcher;
import brut.directory.DirectoryException;
import brut.directory.ExtFile;

public class XmlMaxIdSaver {
    // public.xml increment id
    static HashMap<String, Long> auto_id_maps = new HashMap<>();

    public static Long getCanUseId(File file, String type) throws IOException, ParserConfigurationException, SAXException, XPathExpressionException {
        Long id = auto_id_maps.get(type);
        long maxId = 0;
        // 如果在字典中没有查询到类型存储id，则通过public.xml遍历获取到最大id
        if (auto_id_maps.get(type) == null) {
            Document doc = loadDocument(file);
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
        // 当前id+1
        maxId++;
        auto_id_maps.put(type, maxId);
        return maxId;
    }

    public static void mergeXmlData(File appDir, ExtFile RFile) throws IOException, ParserConfigurationException, SAXException, TransformerException, XPathExpressionException, DirectoryException {
        File publicFile = new File(appDir, "res/values/public.xml");
        Document doc = loadDocument(publicFile);
        XPath xPath = XPathFactory.newInstance().newXPath();
        InputStream in = RFile.getDirectory().getFileInput("R.txt");
        Scanner scanner = new Scanner(in);
        ArrayList<String> idNames = new ArrayList<>();
        // read line
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            String[] strData = line.split(" ");
            String type = strData[1];
            String name = strData[2];
            // 将R.txt的数据，合并入public.xml和ids.xml
            long id = XmlMaxIdSaver.getCanUseId(publicFile, type);
            NodeList nodes = (NodeList) xPath.evaluate("//public[@type='" + type + "' and @name='" + name + "']", doc, XPathConstants.NODESET);

            if (nodes.getLength() == 0) {
                Node resources = doc.getElementsByTagName("resources").item(0);
                Element publicTag = doc.createElement("public");
                if (type.equals("id")) {
                    // need to append ids.xml
                    idNames.add(name);
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
            for (String name : idNames) {
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

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "no");

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


    private static final String ACCESS_EXTERNAL_DTD = "http://javax.xml.XMLConstants/property/accessExternalDTD";
    private static final String ACCESS_EXTERNAL_SCHEMA = "http://javax.xml.XMLConstants/property/accessExternalSchema";
    private static final String FEATURE_LOAD_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd";
    private static final String FEATURE_DISABLE_DOCTYPE_DECL = "http://apache.org/xml/features/disallow-doctype-decl";
    private static final Logger LOGGER = Logger.getLogger(ResXmlPatcher.class.getName());
}
