package uk.ac.shef.dcs.jws;

import dragon.nlp.tool.lemmatiser.EngLemmatiser;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.core.CoreContainer;
//import org.junit.Assert;
import uk.ac.shef.dcs.jate.JATEException;
import uk.ac.shef.dcs.jate.JATEProperties;
import uk.ac.shef.dcs.jate.eval.ATEResultLoader;
import uk.ac.shef.dcs.jate.eval.GSLoader;
import uk.ac.shef.dcs.jate.eval.Scorer;
import uk.ac.shef.dcs.jate.model.JATEDocument;
import uk.ac.shef.dcs.jate.model.JATETerm;
import uk.ac.shef.dcs.jate.nlp.Lemmatiser;
import uk.ac.shef.dcs.jate.util.JATEUtil;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Tests for App* of a set of ATE algorithms & The ACL RD-TEC based benchmarking test based on Embedded Solr.
 * <p>
 * ACL RD-TEC contains almost 11,000 scientific publications. Due to the performance and efficiency reasons,
 * the test and benchmarking tests can only be run manually.
 *
 * @see "uk.ac.shef.dcs.jate.app.AppATEACLRDTECTest"
 * @see <p>
 * The ACL RD-TEC stands for The ACL Reference Dataset for Terminology Extraction and Classification.
 * @see <a href="http://catalog.elra.info/product_info.php?products_id=1236">European Language Resources Association</a>
 * @see <a href="http://acl-arc.comp.nus.edu.sg/">ACL Anthology Reference Corpus (ACL ARC)</a>
 * @see <a href="http://atmykitchen.info/datasets/acl_rd_tec/">The dataset</a>
 * <p>
 * Raw text files can be downloaded via
 * <a href="Resources from ACL ARC">
 * http://atmykitchen.info/datasets/acl_rd_tec/external_resource/index_external_resource.htm</a>
 * <p>
 * For more details about ACL RD-TEC corpus, please refer to the paper:
 * <p>
 * Zadeh, B. Q., & Handschuh, S. (2014).
 * The ACL RD-TEC: a dataset for benchmarking terminology extraction and classification in computational linguistics.
 * In COLING (pp. 52-63).
 */
public abstract class ACLRDTECTest {

    private static Logger LOG = Logger.getLogger(ACLRDTECTest.class.getName());

    static String workingDir = System.getProperty("user.dir");

    static String solrCoreName = "ACLRDTEC";

    static Path corpusDir = Paths.get(workingDir, "src", "test", "resources", "eval", "ACL_RD-TEC", "corpus", "full","xml");

    static Path solrHome = Paths.get(workingDir, "testdata", "solr-testbed");

    static Path FREQ_GENIC_FILE = Paths.get(workingDir, "testdata","solr-testbed", "resource",
            "bnc_unifrqs.normal");

    static Path allAnnCandidTerms = Paths.get(workingDir, "src", "test", "resources", "eval",
            "ACL_RD-TEC", "terms.txt");

    // The corpus can be downloaded via
    // <a href="http://atmykitchen.info/datasets/acl_rd_tec/cleansed_text/index_cleansed_text.htm">
    // Cleansed Text Files in XML Format</a>
    public static final Path ACL_RD_TEC_CORPUS_ZIPPED_FILE =
            Paths.get(workingDir, "src", "test", "resources", "eval", "ACL_RD-TEC", "corpus", "full","xml.zip");

    static EmbeddedSolrServer server = null;
    static List<String> gsTerms = null;
    JATEProperties jateProp = null;

    public static int EVAL_CONDITION_MIN_TERM_CONTEXT_FREQUENCY = 1;
    public static int EVAL_CONDITION_MIN_TERM_TOTAL_FREQUENCY = 1;
    public static int EVAL_CONDITION_CUTOFF_TOP_K_PERCENT = 1;
    public static boolean EVAL_CONDITION_IGNORE_SYMBOL = true;
    public static boolean EVAL_CONDITION_IGNORE_DIGITS = false;
    public static boolean EVAL_CONDITION_CASE_INSENSITIVE = true;
    public static int EVAL_CONDITION_CHAR_RANGE_MIN = 1;
    public static int EVAL_CONDITION_CHAR_RANGE_MAX = -1;
    public static int EVAL_CONDITION_TOKEN_RANGE_MIN = 1;
    public static int EVAL_CONDITION_TOKEN_RANGE_MAX = -1;
    private static int[] EVAL_CONDITION_TOP_N = {50, 100, 300, 500, 800, 1000, 1500,
            2000, 3000, 4000, 5000, 6000, 7000, 8000,9000,10000, 15000, 20000, 25000, 30000};

    static Lemmatiser lemmatiser = new Lemmatiser(new EngLemmatiser(
            Paths.get(workingDir, "src", "test", "resources", "lemmatiser").toString(), false, false
    ));

    public void initialise(String solrHomeDir, String solrCoreName) throws JATEException, IOException {
        if (server == null) {

            File lock = Paths.get(solrHome.toString(), solrCoreName, "data", "index", "write.lock").toFile();
            if (lock.exists()) {
                System.err.println("Previous solr did not shut down cleanly. Unlock it ...");
//                Assert.assertTrue(lock.delete());
            }

            CoreContainer solrContainer = new CoreContainer(solrHomeDir);
            solrContainer.load();

            server = new EmbeddedSolrServer(solrContainer, solrCoreName);
        }

        gsTerms = GSLoader.loadACLRD(allAnnCandidTerms.toString());

        if (gsTerms == null) {
            throw new JATEException("ACLRDTEC CORPUS_DIR CONCEPT FILE CANNOT BE LOADED SUCCESSFULLY!");
        }

        jateProp = new JATEProperties();

    }

    /**
     * Corpus indexing and candidate term (at index-time)
     *
     * @param corpusDir, ACL RD-TEC cleansed text xml.zip corpus directory
     * @return List<JATETerm>
     * @throws JATEException
     */
    public void indexAndExtract(Path corpusDir) throws JATEException {
        List<Path> files = JATEUtil.loadFiles(corpusDir);

        LOG.info("indexing and extracting candidates from "+files.size()+" files...");
        int count = 0;
        for (Path file : files) {
            try {
                indexJATEDocuments(file, jateProp, false);
                count++;
                if (count % 100 == 0)
                    LOG.info("indexing done: " + count + "/" + files.size());
            }catch (NullPointerException e){
                e.printStackTrace();
            }
        }

        try {
            server.commit();
            LOG.info("complete indexing and candidate extraction.");
        } catch (SolrServerException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void indexJATEDocuments(Path file, JATEProperties jateProp, boolean commit) throws JATEException {
        if (file == null || file.toString().contains(".DS_Store")) {
            return;
        }
        FileInputStream fileStream = null; 
        try {
        	fileStream = new FileInputStream(file.toFile());
            JATEDocument jateDocument = JATEUtil.loadACLRDTECDocument(fileStream);

            if(jateDocument.getContent().trim().length()!=0)
                JATEUtil.addNewDoc(server, jateDocument.getId(), jateDocument.getId(), jateDocument.getContent(), jateProp, commit);
        } catch (FileNotFoundException ffe) {
            throw new JATEException(ffe.toString());
        } catch (IOException ioe) {
            throw new JATEException(String.format("failed to index [%s]", file.toString()) + ioe.toString());
        } catch (SolrServerException sse) {
            throw new JATEException(String.format("failed to index [%s] ", file.toString()) + sse.toString());
        } finally {
        	try {
        		if (fileStream!=null)
        			fileStream.close();
			} catch (IOException e) {
				LOG.error(e.toString());
			}
        }
    }

    public static long validate_indexing() {
        ModifiableSolrParams params = new ModifiableSolrParams();
        params.set("q", "*:*");

        try {
            QueryResponse qResp = server.query(params);
            SolrDocumentList docList = qResp.getResults();

            long numDocs = docList.getNumFound();
            LOG.info(String.format("[%s] documents processed!", numDocs));
            return numDocs;
        } catch (SolrServerException e) {
            e.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return 0;
    }

    abstract List<JATETerm> rankAndFilter(EmbeddedSolrServer server, String solrCoreName, JATEProperties jateProp) throws JATEException;

    public void evaluate(List<JATETerm> jateTerms, String algorithmName) throws JATEException {
        LOG.info(String.format("evaluating %s ...", algorithmName));
        List<String> rankedTerms = ATEResultLoader.load(jateTerms);

        double[] scores = Scorer.computePrecisionAtRank(lemmatiser,gsTerms, rankedTerms,
                EVAL_CONDITION_IGNORE_SYMBOL, EVAL_CONDITION_IGNORE_DIGITS, EVAL_CONDITION_CASE_INSENSITIVE,
                EVAL_CONDITION_CHAR_RANGE_MIN, EVAL_CONDITION_CHAR_RANGE_MAX,
                EVAL_CONDITION_TOKEN_RANGE_MIN, EVAL_CONDITION_TOKEN_RANGE_MAX,
                EVAL_CONDITION_TOP_N);

        double precision = Scorer.computeOverallPrecision(lemmatiser,gsTerms, rankedTerms, EVAL_CONDITION_IGNORE_SYMBOL,
                EVAL_CONDITION_IGNORE_DIGITS, EVAL_CONDITION_CASE_INSENSITIVE,
                EVAL_CONDITION_CHAR_RANGE_MIN, EVAL_CONDITION_CHAR_RANGE_MAX,
                EVAL_CONDITION_TOKEN_RANGE_MIN, EVAL_CONDITION_TOKEN_RANGE_MAX);

        double recall = Scorer.computeOverallRecall(gsTerms, rankedTerms, lemmatiser, EVAL_CONDITION_IGNORE_SYMBOL,
                EVAL_CONDITION_IGNORE_DIGITS, EVAL_CONDITION_CASE_INSENSITIVE,
                EVAL_CONDITION_CHAR_RANGE_MIN, EVAL_CONDITION_CHAR_RANGE_MAX,
                EVAL_CONDITION_TOKEN_RANGE_MIN, EVAL_CONDITION_TOKEN_RANGE_MAX);

        LOG.info(String.format("=============%s ACL RD-TEC Benchmarking Results==================", algorithmName));

        printResults(scores, precision, recall);

    }

    private void printResults(double[] scores, double precision, double recall) {
        int topNIndex = 0;
        for (int topN : EVAL_CONDITION_TOP_N) {
            LOG.info(String.format("  top %s Precision: %s", topN, scores[topNIndex]) );
            topNIndex++;
        }

        LOG.info("  overall precision: " + precision);
        LOG.info("  overall computeOverallRecall: " + recall);
        LOG.info("  overall F-measure: " + Scorer.getFMeasure(precision, recall));
    }

    /**
     * Using a set of heuristics, sections such as ‘bibliography’ and ‘acknowledgements’ are removed from the corpus
     * and are organized in separate files. In addition, text cleaning is performed, e.g. broken words and text
     * segments are joined, footnotes and captions are removed and sections are organised into paragraphs.
     *
     * @return List<JATEDocument>, documents unzipped and loaded from ACL RD TEC xml.zip
     * @throws JATEException
     */
    protected static List<JATEDocument> loadCorpus() throws JATEException {
        List<JATEDocument> jateDocuments = new ArrayList<>();
        ZipFile aclCorpus = null;
        ZipInputStream zipIn = null;
        try {
            aclCorpus = new ZipFile(ACL_RD_TEC_CORPUS_ZIPPED_FILE.toFile());

            zipIn = new ZipInputStream(new FileInputStream(ACL_RD_TEC_CORPUS_ZIPPED_FILE.toFile()));
            ZipEntry entry = zipIn.getNextEntry();

            // iterates over entries in the zip file
            while (entry != null) {
                if (!entry.isDirectory()) {
                    InputStream docInputStream = aclCorpus.getInputStream(entry);
                    JATEDocument jateDocument;
                    jateDocument = JATEUtil.loadACLRDTECDocument(docInputStream);
                    jateDocuments.add(jateDocument);

                    docInputStream.close();

                    zipIn.closeEntry();
                    entry = zipIn.getNextEntry();
                }
                zipIn.close();
            }
        } catch (IOException e) {
            throw new JATEException(ACL_RD_TEC_CORPUS_ZIPPED_FILE.toString() + " not found!");
        } finally {
        	if (zipIn != null){
				try {
					zipIn.close();
				} catch (IOException e) {
					LOG.error(e.toString());
				}
			}
        	try {
        		if (aclCorpus != null)
        			aclCorpus.close();
			} catch (IOException e) {
				LOG.error(e.toString());
			}
        }

        LOG.info("number of jate Documents:" + jateDocuments.size());

        return jateDocuments;
    }

    public static void cleanWriteLock(String solrHome, String coreName) {
        File writeLockFile = Paths.get(solrHome, coreName, "data", "index", "write.lock").toFile();

        if (writeLockFile.exists()) {
            boolean isSuccess = FileUtils.deleteQuietly(writeLockFile);
            if (!isSuccess) {
                LOG.warn("writeLock has not been deleted from ["+coreName+"]. Do it manually!");
            }
        }
    }
}
