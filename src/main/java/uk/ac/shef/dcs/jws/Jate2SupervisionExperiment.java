package uk.ac.shef.dcs.jws;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import uk.ac.shef.dcs.jate.JATEException;
import uk.ac.shef.dcs.jate.JATEProperties;
import uk.ac.shef.dcs.jate.model.JATEDocument;
import uk.ac.shef.dcs.jate.model.JATETerm;
import uk.ac.shef.dcs.jate.util.IOUtil;
import uk.ac.shef.dcs.jate.util.JATEUtil;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.List;

/**
 * Created by jieg on 5/19/2017.
 */
public class Jate2SupervisionExperiment extends ACLRDTECTest {

    public static int ACL_RDTEC_VERSION_1 = 1;
    public static int ACL_RDTEC_VERSION_2 = 2;

    // C:\Data\jate\jate4supervision\aclrdtec2-distribution\distribution\raw_abstract_txt
    static Path ACLRDTEC_2_CORPUS_DIR = Paths.get("c:", "Data", "jate", "jate4supervision", "aclrdtec2-distribution", "distribution", "raw_abstract_txt");

    void exportACLCorpus(Path exportDir, int version) throws JATEException {
        List<Path> files;

        if (ACL_RDTEC_VERSION_1 == version) {
            files = JATEUtil.loadFiles(corpusDir);
        } else {
            files = JATEUtil.loadFiles(ACLRDTEC_2_CORPUS_DIR);
        }

        for (Path file : files) {
            if (file == null || file.toString().contains(".DS_Store")) {
                return;
            }

            FileInputStream fileStream = null;
            try {
                fileStream = new FileInputStream(file.toFile());

                JATEDocument jateDocument;

                if (ACL_RDTEC_VERSION_1 == version) {
                    jateDocument = JATEUtil.loadACLRDTECDocument(fileStream);
                } else {
                    jateDocument = loadJATEDocFromACL2XML(fileStream);
                }

                PrintWriter printWriter = null;
                try {
                    String currentFileName = Paths.get(exportDir.toString(), jateDocument.getId()+".txt").toString();
                    File currentFile = new File(currentFileName);
                    try {
                        currentFile.createNewFile();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    System.out.println(String.format("writing file [%s] ...", currentFileName));

                    printWriter = (PrintWriter) IOUtil.getUTF8Writer(currentFileName);
                    printWriter.println(jateDocument.getContent());
                    printWriter.flush();
                } finally {
                    if (printWriter!=null) {
                        printWriter.close();
                    }
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     *
     * @param fileInputStream  ACL XML file
     * @return JATEDocument JATE Document Object
     * @throws JATEException
     */
    private static JATEDocument loadJATEDocFromACL2XML(InputStream fileInputStream) throws JATEException {
        SAXParserFactory factory = SAXParserFactory.newInstance();

        SAXParser saxParser = null;
        JATEDocument jateDocument = null;
        try {
            saxParser = factory.newSAXParser();

            StringBuffer paperParagraphs = new StringBuffer();
            StringBuffer paperId = new StringBuffer();
            StringBuffer paperTitle = new StringBuffer();

            DefaultHandler handler = new DefaultHandler() {

                boolean paper = false;
                boolean title = false;
                boolean section = false;
                boolean sectionTitle = false;
                boolean paragraph = false;
                boolean reference = false;

                public void startElement(String uri, String localName,
                                         String qName, org.xml.sax.Attributes attributes)
                        throws SAXException {
                    if (qName.equalsIgnoreCase("Paper")) {
                        paper = true;
                        paperId.append(attributes.getValue("acl-id"));
                    }

                    //TODO: need to skip title of reference, test data:P06-1139_cln.xml
                    if (qName.equalsIgnoreCase("title")) {
                        title = true;
                    }

                    if (qName.equalsIgnoreCase("Section")) {
                        section = true;
                    }

                    if (qName.equalsIgnoreCase("SectionTitle")) {
                        sectionTitle = true;
                    }

                    if (qName.equalsIgnoreCase("S")) {
                        paragraph = true;
                    }

                    if (qName.equalsIgnoreCase("Reference")) {
                        reference = true;
                    }
                }

                public void endElement(String uri, String localName,
                                       String qName) throws SAXException {
                    if (qName.equalsIgnoreCase("S")) {
                        paragraph = false;
                    }
                }

                public void characters(char ch[], int start, int length) throws SAXException {
                    if (paper) {
                        paper = false;
                    }

                    if (title) {
                        title = false;

                        if (!reference) {
                            paperTitle.append(new String(ch, start, length)).append("\n");
                        }
                        reference = false;
                    }

                    if (section) {
                        section = false;
                    }

                    if (sectionTitle) {
                        sectionTitle = false;
                    }

                    if (paragraph) {
                        String paragraph = new String(ch, start, length);
                        paperParagraphs.append(paragraph);
                        paperParagraphs.append(" ");
                    }
                }
            };

            saxParser.parse(fileInputStream, handler);

            StringBuffer fullText = new StringBuffer();
            fullText.append(paperTitle).append("\n").append(paperParagraphs);

            String normalizedText = Normalizer.normalize(fullText.toString(), Normalizer.Form.NFD);
            normalizedText = StringEscapeUtils.unescapeXml(normalizedText);
            String cleanedText = JATEUtil.cleanText(normalizedText);
            jateDocument = new JATEDocument(paperId.toString());
            jateDocument.setContent(cleanedText.trim());

        } catch (ParserConfigurationException e) {
            throw new JATEException("Failed to initialise SAXParser!" + e.toString());
        } catch (SAXException e) {
            throw new JATEException("Failed to initialise SAXParser!" + e.toString());
        } catch (IOException ioe) {
            throw new JATEException("I/O Exception when parsing input file!" + ioe.toString());
        }
        return jateDocument;
    }

    @Override
    List<JATETerm> rankAndFilter(EmbeddedSolrServer server, String solrCoreName, JATEProperties jateProp) throws JATEException {
        return null;
    }

    public static void main(String[] args) {
        Jate2SupervisionExperiment jse = new Jate2SupervisionExperiment();
        try {
//            jse.exportACLCorpus(Paths.get("c:", "Data", "jate", "jate4supervision", "ACLRDTEC-1"), Jate2SupervisionExperiment.ACL_RDTEC_VERSION_1);
            jse.exportACLCorpus(Paths.get("c:", "Data", "jate", "jate4supervision", "ACLRDTEC-2"), Jate2SupervisionExperiment.ACL_RDTEC_VERSION_2);
        } catch (JATEException e) {
            e.printStackTrace();
        }
    }

}