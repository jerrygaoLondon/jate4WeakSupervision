package uk.ac.shef.dcs.jws;

import dragon.nlp.tool.lemmatiser.EngLemmatiser;
import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.SAXException;
import uk.ac.shef.dcs.jate.JATEException;
import uk.ac.shef.dcs.jate.JATEProperties;
import uk.ac.shef.dcs.jate.app.AppParams;
import uk.ac.shef.dcs.jate.eval.GSLoader;
import uk.ac.shef.dcs.jate.model.JATEDocument;
import uk.ac.shef.dcs.jate.nlp.Lemmatiser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Created by jieg on 5/24/2017.
 */
public class GeniaDataSetGenerator extends BaseEmbeddedSolr{
    private static Logger LOG = Logger.getLogger(GeniaDataSetGenerator.class.getName());

    public static String SOLR_CORE_NAME = "GENIA";

    public static final Path GENIA_CORPUS_ZIPPED_FILE = Paths.get(workingDir, "src", "test", "resource",
            "eval", "GENIA", "corpus.zip");

    public static final Path GENIA_CORPUS_CONCEPT_FILE = Paths.get(workingDir, "src", "test", "resource",
            "eval", "GENIA", "concept.txt");

    public static final int EXPECTED_CANDIDATE_SIZE=38805;
    static Lemmatiser lemmatiser = new Lemmatiser(new EngLemmatiser(
            Paths.get(workingDir, "src", "test", "resource", "lemmatiser").toString(), false, false
    ));

    JATEProperties jateProperties = null;

    List<String> gsTerms;
    Map<String, String> initParams = null;
    private static boolean isIndexed = false;

    // evaluation conditions for GENIA corpus
    private static int EVAL_CONDITION_MIN_TERM_CONTEXT_FREQUENCY = 1;
    private static int EVAL_CONDITION_MIN_TERM_TOTAL_FREQUENCY = 1;
    private static int EVAL_CONDITION_CUTOFF_TOP_K_PERCENT = 1;
    private static boolean EVAL_CONDITION_IGNORE_SYMBOL = true;
    private static boolean EVAL_CONDITION_IGNORE_DIGITS = false;
    private static boolean EVAL_CONDITION_CASE_INSENSITIVE = true;
    private static int EVAL_CONDITION_CHAR_RANGE_MIN = 1;
    private static int EVAL_CONDITION_CHAR_RANGE_MAX = -1;
    private static int EVAL_CONDITION_TOKEN_RANGE_MIN = 1;
    private static int EVAL_CONDITION_TOKEN_RANGE_MAX = -1;
    private static boolean exportData = true;
    private static int[] EVAL_CONDITION_TOP_N = {50, 100, 300, 500, 800, 1000, 1500,
            2000, 3000, 4000, 5000, 6000, 7000, 8000,9000,10000, 15000, 20000, 25000, 30000};


    @Override
    protected void setSolrCoreName() {

    }

    @Override
    protected void setReindex() {

    }

    public void setup() throws Exception {
        super.setup();
        LOG.info("Initialising evaluation/test of available ATE algorithms on GENIA dataset ... ");
        jateProperties = new JATEProperties();
        System.out.println("======================================== is Indexed ? : "+ isIndexed);
        if (!isIndexed || reindex) {
            try {
                LOG.info("starting to indexing genia corpus ... ");
                indexCorpus(loadGENIACorpus());
                LOG.info("complete document and term candidates indexing.");
            } catch (IOException ioe) {
                throw new JATEException("Unable to delete index data. Please clean index directory " +
                        "[testdata/solr-testbed/jate/data] manually!");
            }
        } else {
            LOG.info(" Skip document and term candidate indexing. ");
        }

        gsTerms = GSLoader.loadGenia(GENIA_CORPUS_CONCEPT_FILE.toString());

        if (gsTerms == null) {
            throw new JATEException("GENIA CORPUS_DIR CONCEPT FILE CANNOT BE LOADED SUCCESSFULLY!");
        }
        initParams = new HashMap<>();

        initParams.put(AppParams.PREFILTER_MIN_TERM_TOTAL_FREQUENCY.getParamKey(), String.valueOf(EVAL_CONDITION_MIN_TERM_TOTAL_FREQUENCY));
        initParams.put(AppParams.CUTOFF_TOP_K_PERCENT.getParamKey(), String.valueOf(EVAL_CONDITION_CUTOFF_TOP_K_PERCENT));
        LOG.info("<<TEST BEGINS WITH following conditions: >>");

        LOG.info(String.format("Evaluation of topN precision and overall P/R/F based on on lemmatised terms, " +
                        "ignore symbol? [%s], ignore digits? [%s], case-insensitive? [%s], " +
                        "char range filtering: [%s,%s], token-range filtering: [%s,%s], " +
                        "pre-filtering min total freq: [%s], cut-off Top K precent: [%s] " +
                        "and min context (co-occur) frequency: [%s]",
                EVAL_CONDITION_IGNORE_SYMBOL, EVAL_CONDITION_IGNORE_DIGITS,
                EVAL_CONDITION_CASE_INSENSITIVE,
                EVAL_CONDITION_CHAR_RANGE_MIN, EVAL_CONDITION_CHAR_RANGE_MAX,
                EVAL_CONDITION_TOKEN_RANGE_MIN, EVAL_CONDITION_TOKEN_RANGE_MAX,
                EVAL_CONDITION_MIN_TERM_TOTAL_FREQUENCY, EVAL_CONDITION_CUTOFF_TOP_K_PERCENT,
                EVAL_CONDITION_MIN_TERM_CONTEXT_FREQUENCY));
    }

    protected List<JATEDocument> loadGENIACorpus() throws JATEException {
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = null;
        Metadata metadata = new Metadata();

        List<JATEDocument> corpus = new ArrayList<>();
        ZipFile geniaCorpus = null;
        try {
            geniaCorpus = new ZipFile(GENIA_CORPUS_ZIPPED_FILE.toFile());
            Enumeration<? extends ZipEntry> entries = geniaCorpus.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String fileName = entry.getName();
                //skip file in MAC OS
                if (entry.isDirectory() || fileName.startsWith("__MACOSX/") || fileName.contains(".DS_Store"))
                    continue;

                InputStream stream = geniaCorpus.getInputStream(entry);
                handler = new BodyContentHandler(-1);
                try {
                    parser.parse(stream, handler, metadata);
                    String content = handler.toString();
                    JATEDocument doc = new JATEDocument(fileName);
                    doc.setContent(content);
                    corpus.add(doc);
                } catch (SAXException e) {
                    e.printStackTrace();
                } catch (TikaException e) {
                    e.printStackTrace();
                } finally {
                    stream.close();
                }
            }
        } catch (IOException e) {
            throw new JATEException(String.format("GENIA Corpus not found from %s", GENIA_CORPUS_ZIPPED_FILE));
        } finally {
            if (geniaCorpus != null) {
                try {
                    geniaCorpus.close();
                } catch (IOException e) {
                    LOG.error(e.toString());
                }
            }
        }

        return corpus;
    }

    protected void indexCorpus(List<JATEDocument> corpus) throws IOException, SolrServerException {
        int count = 0;
        long startTime = System.currentTimeMillis();
        for (JATEDocument doc : corpus) {
            try {
                count++;
                super.addNewDoc(doc.getId(), doc.getId(), doc.getContent(), jateProperties, false);
                if (count % 500 == 0) {
                    LOG.info(String.format("%s documents indexed.", count));
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (SolrServerException e) {
                e.printStackTrace();
            } catch (JATEException jateEx) {
                jateEx.printStackTrace();
                LOG.warn(String.format("failed to index document. Please check JATE properties " +
                                "for current setting for [%s] and [%s]", JATEProperties.PROPERTY_SOLR_FIELD_CONTENT_NGRAMS,
                        JATEProperties.PROPERTY_SOLR_FIELD_CONTENT_TERMS));
            }
        }
        long endTime = System.currentTimeMillis();
        LOG.info(String.format("Indexing and candidate extraction took [%s] milliseconds", (endTime - startTime)));
        server.commit();
        isIndexed = true;
    }

    public static void cleanData() {
        try {
            cleanIndexDirectory(solrHome.toString(), SOLR_CORE_NAME);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        GeniaDataSetGenerator geniaDataSetGenerator = new GeniaDataSetGenerator();
        geniaDataSetGenerator.setup();

        // TODO
    }

}
