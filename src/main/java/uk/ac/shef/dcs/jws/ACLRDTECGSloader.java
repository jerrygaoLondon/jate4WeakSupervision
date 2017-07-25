package uk.ac.shef.dcs.jws;

import org.apache.commons.lang.StringEscapeUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import uk.ac.shef.dcs.jate.JATEException;
import uk.ac.shef.dcs.jate.eval.GSLoader;
import uk.ac.shef.dcs.jate.model.JATEDocument;
import uk.ac.shef.dcs.jate.util.ACLRDCorpusParser;
import uk.ac.shef.dcs.jate.util.IOUtil;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.*;

/**
 *
 * see also https://stackoverflow.com/questions/15755991/sax-parser-stringbuilder-only-returning-one-line
 *
 * Created by jieg on 7/24/2017.
 */
public class ACLRDTECGSloader {
    static String workingDir = System.getProperty("user.dir");
    // NOTE: be aware of using '-', ',', etc as splitter as term may include those characters in the raw surface form
    // e.g., terms in ACL RD-TEC 2.0 include "theory of attention, intention, and aggregation of utterances",
    //  "supervised, non-training intensive framework", "lexical, syntactic, semantic, and structural information"
    // "hidden Markov model (HMM) and maximum entropy (Maxent) classifiers"
    // "identification of 2-character and 3-character Chinese names without title"
    // "theory of attention, intention, and aggregation of utterances"
    // "hand-built, symbolic resources"
    // "multilingual, multimedia data"
    // "comparable, non-parallel corpora"
    // "stochastic, graph-based method"
    // "gaze, utterance and conversational context features"
    final static String TERM_SPLITTER = "-IAMSPLITTER-";

    /**
     * export annotated terms from ACL RD-TEC (1.0) into txt file
     *
     * original annotation file can be downloaded via http://pars.ie/lr/acl-rd-tec-terminology
     *
     * @throws IOException
     */
    public void exportACL1GSTerms() throws IOException {
        Path acl1GSFile = Paths.get(workingDir, "src", "test", "resources", "eval",
                "ACL_RD-TEC", "terms.txt");
        String exportDir = "C:\\Users\\jieg\\Google Drive\\OAK Group\\cicling2017\\data\\gs-terms";

        List<String> aclrdtec1Terms =  GSLoader.loadACLRD(acl1GSFile.toString());
        System.out.println(String.format("total [%s] ACL RD-TEC 1.0 terms are loaded.", aclrdtec1Terms.size()));

        exportToGSTermFile(exportDir, "acl1_gs_terms.txt", aclrdtec1Terms);
    }

    public void exportToGSTermFile (String exportDir, String exportFileName, Collection<String> aclAnnotatedTerms) {
        String exportFile = Paths.get(exportDir, exportFileName).toString();
        PrintWriter printWriter = null;
        try {
            File currentFile = new File(exportFile);
            try {
                currentFile.createNewFile();
                System.out.println(String.format("writing file [%s] ...", currentFile.toString()));

                printWriter = (PrintWriter) IOUtil.getUTF8Writer(currentFile.toString());
                for (String term : aclAnnotatedTerms) {
                    printWriter.println(term);
                }
                printWriter.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } finally {
            if (printWriter!=null) {
                printWriter.close();
            }
        }
        System.out.println("complete. All real terms are written into " + exportFile);
    }


    /**
     * export  ACL RD-TEC 2.0 annotated terms (surface form) by two annotator into txt file
     *
     * download original annotation file from http://pars.ie/lr/acl_rd-tec
     *
     * @throws FileNotFoundException
     * @throws JATEException
     */
    public void exportACL2GSTerms() throws FileNotFoundException, JATEException {
        String annotatedXMLFolder1 = "C:\\Data\\jate\\jate4supervision\\aclrdtec2-distribution\\distribution\\annoitation_files\\annotator1";
        String annotatedXMLFolder2 = "C:\\Data\\jate\\jate4supervision\\aclrdtec2-distribution\\distribution\\annoitation_files\\annotator2";

        Collection<File>  annotator1Files = ACLRDCorpusParser.listFileTree(new File(annotatedXMLFolder1));
        Collection<File>  annotator2Files = ACLRDCorpusParser.listFileTree(new File(annotatedXMLFolder2));

        //see http://pars.ie/lr/acl_rd-tec
        System.out.println(String.format("total [%s] annotation files found in annotator 1 directory. Should be 189", annotator1Files.size()));
        System.out.println(String.format("total [%s] annotation files in annotator 2 directory. Should be 282", annotator2Files.size()));

        Set<String> allAnnotatedTerms = new HashSet<>();

        System.out.println("extracting terms from first annotator directory ...");

        Set<String> annotator1AnnTerms = extractTermsFromFileCollection(annotator1Files);

        System.out.println(String.format("[%s] annotated terms from annotator 1 directory. ", annotator1AnnTerms.size()));

        System.out.println("extracting terms from second annotator directory ...");

        Set<String> annotator2AnnTerms = extractTermsFromFileCollection(annotator2Files);

        System.out.println(String.format("[%s] annotated terms from annotator 2 directory. ", annotator2AnnTerms.size()));

        allAnnotatedTerms.addAll(annotator1AnnTerms);
        allAnnotatedTerms.addAll(annotator2AnnTerms);

        System.out.println("total annotated terms: " + allAnnotatedTerms.size());

        String exportDir = "C:\\Users\\jieg\\Google Drive\\OAK Group\\cicling2017\\data\\gs-terms";
        exportToGSTermFile(exportDir, "acl2_gs_terms.txt", allAnnotatedTerms);

    }

    private static Set<String> extractTermsFromFileCollection(Collection<File>  annotatedFiles) {
        Iterator<File> annotationIter = annotatedFiles.iterator();
        Set<String> allAnnotatedTermsFromCollection = new HashSet<>();
        while (annotationIter.hasNext()) {
            File annotatedFile = annotationIter.next();
            try {
                Set<String> annotatedTermForOneDoc = loadAnnotatedTermsFromACLRDTEC2XML(new FileInputStream(annotatedFile));
                allAnnotatedTermsFromCollection.addAll(annotatedTermForOneDoc);
            } catch (JATEException e) {
               System.err.println(String.format("failed to extract terms from [%s]", annotatedFile.toString()));
               System.err.println(e.toString());
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        return allAnnotatedTermsFromCollection;
    }

    /**
     * getting the entire text for an element in one call is not guaranteed.
     *
     * The characters method needs to accumulate the text found into a StringBuffer (or StringBuilder or other data structure)
     *
     * @param fileInputStream
     * @return
     * @throws JATEException
     */
    private static Set<String> loadAnnotatedTermsFromACLRDTEC2XML(InputStream fileInputStream) throws JATEException {
        final Set<String> paperAnnotatedTermSet = new HashSet<>();

        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser saxParser = null;

        try {
            saxParser = factory.newSAXParser();
            final StringBuffer paperParagraphs = new StringBuffer();
            final StringBuffer paperId = new StringBuffer();
            final StringBuffer paperTitle = new StringBuffer();
            //
            final StringBuffer paperAnnotatedTerms = new StringBuffer();

            DefaultHandler handler = new DefaultHandler() {
                boolean paper = false;
                boolean title = false;
                boolean section = false;
                boolean sectionTitle = false;
                boolean paragraph = false;
                boolean reference = false;
                boolean term = false;
                boolean sentence = false;

                public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
                    if(qName.equalsIgnoreCase("Paper")) {
                        this.paper = true;
                        paperId.append(attributes.getValue("acl-id"));
                    }

                    if(qName.equalsIgnoreCase("title")) {
                        this.title = true;
                    }

                    if(qName.equalsIgnoreCase("Section")) {
                        this.section = true;
                    }

                    if(qName.equalsIgnoreCase("SectionTitle")) {
                        this.sectionTitle = true;
                    }

                    if(qName.equalsIgnoreCase("Paragraph")) {
                        this.paragraph = true;
                    }

                    if(qName.equalsIgnoreCase("Reference")) {
                        this.reference = true;
                    }

                    if(qName.equalsIgnoreCase("s")) {
                        this.sentence = true;
                    }

                    if (qName.equalsIgnoreCase("term")) {
                        this.term = true;
                    }

                }

                public void endElement(String uri, String localName, String qName) throws SAXException {
                    if(qName.equalsIgnoreCase("Paragraph")) {
                        this.paragraph = false;
                    }

                    if(qName.equalsIgnoreCase("term")) {
                        this.term = false;
                        paperAnnotatedTerms.append(TERM_SPLITTER);
                    }

                    if(qName.equalsIgnoreCase("s")) {
                        this.sentence = false;
                    }

                    if(qName.equalsIgnoreCase("SectionTitle")) {
                        this.sectionTitle = false;
                    }
                }

                public void characters(char[] ch, int start, int length) throws SAXException {
                    if(this.paper) {
                        this.paper = false;
                    }

                    if(this.title) {
                        this.title = false;
                        if(!this.reference) {
                            paperTitle.append(new String(ch, start, length)).append("\n");
                        }

                        this.reference = false;
                    }

                    if(this.section) {
                        this.section = false;
                    }

                    if(this.sectionTitle) {
                        this.sectionTitle = false;
                    }

                    if(this.paragraph) {
                        String paragraph = new String(ch, start, length);
                        paperParagraphs.append(paragraph);
                    }

                    if (this.term) {
                        String termSurfaceForm = new String(ch, start, length);
                        paperAnnotatedTerms.append(termSurfaceForm);

                        if (termSurfaceForm.contains(",")) {
                            System.out.println("term contain ',', paper id: " + paperId.toString());
                        }
                    }

                }
            };
            saxParser.parse(fileInputStream, handler);
            ;
            paperAnnotatedTermSet.addAll(Arrays.asList(paperAnnotatedTerms.toString().split(TERM_SPLITTER)));

            return paperAnnotatedTermSet;
        } catch (ParserConfigurationException var11) {
            throw new JATEException("Failed to initialise SAXParser!" + var11.toString());
        } catch (SAXException var12) {
            throw new JATEException("Failed to initialise SAXParser!" + var12.toString());
        } catch (IOException var13) {
            throw new JATEException("I/O Exception when parsing input file!" + var13.toString());
        }
    }

    public static void main(String[] args) throws IOException, JATEException {
        ACLRDTECGSloader gsExporter = new ACLRDTECGSloader();

        // gsExporter.exportACL1GSTerms();
        gsExporter.exportACL2GSTerms();
//        gsExporter.loadAnnotatedTermsFromACLRDTEC2XML(
//                new FileInputStream(new File("C:\\Data\\jate\\jate4supervision\\aclrdtec2-distribution\\distribution\\annoitation_files\\annotator1\\01\\P01-1007_abstr.xml")));
    }

}
