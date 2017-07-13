package uk.ac.shef.dcs.jws;

import dragon.nlp.tool.lemmatiser.EngLemmatiser;
import org.json.simple.parser.ParseException;
import uk.ac.shef.dcs.jate.eval.Scorer;
import uk.ac.shef.dcs.jate.nlp.Lemmatiser;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Created by jieg on 5/29/2017.
 */
public class EvaluatorJate4Ngrams extends Scorer {
    protected final static String FILE_TYPE_JSON = "json";
    protected final static String FILE_TYPE_CSV = "csv";

    protected static int EVAL_CONDITION_MIN_TERM_CONTEXT_FREQUENCY = 1;
    protected static int EVAL_CONDITION_MIN_TERM_TOTAL_FREQUENCY = 1;
    protected static int EVAL_CONDITION_CUTOFF_TOP_K_PERCENT = 1;
    protected static boolean EVAL_CONDITION_IGNORE_SYMBOL = true;
    protected static boolean EVAL_CONDITION_IGNORE_DIGITS = false;
    protected static boolean EVAL_CONDITION_CASE_INSENSITIVE = true;
    protected static int EVAL_CONDITION_CHAR_RANGE_MIN = 2;
    protected static int EVAL_CONDITION_CHAR_RANGE_MAX = -1;
    protected static int EVAL_CONDITION_TOKEN_RANGE_MIN = 1;
    protected static int EVAL_CONDITION_TOKEN_RANGE_MAX = 5;
    // 50, 100, 300, 500, 800, 1000, 1500, 2000, 3000, 4000, 5000, 6000
    protected static int[] EVAL_CONDITION_TOP_N = {};

    // top K percentage of candidates; K means percentage here
    //1, 2, 3, 4 , 5, 6, 7, 8, 9, 10, 15, 20, 25, 30
    public static int[] EVAL_CONDITION_TOP_K = {};
    // whether to compute the AvP defined in [Astrakhantsev 2016]
    // Astrakhantsev, N. (2016). ATR4S: Toolkit with State-of-the-art Automatic Terms Recognition Methods in Scala. arXiv preprint arXiv:1611.07804.
    public static Boolean IS_COMPUTE_ATR4S_AvP = Boolean.FALSE;


    public static void main(String[] args) throws IOException, ParseException {
        String workingDir = System.getProperty("user.dir");
        Lemmatiser lemmatiser = new Lemmatiser(new EngLemmatiser(
                Paths.get(workingDir, "src", "test", "resources", "lemmatiser").toString(), false, false
        ));
        Path GENIA_CORPUS_CONCEPT_FILE = Paths.get(workingDir, "src", "test", "resources",
                "eval", "GENIA", "concept.txt");
        Path ACL_1_CORPUS_CONCEPT_FILE = Paths.get(workingDir, "src", "test", "resources",
                "eval", "ACL_RD-TEC", "terms.txt");

        Path TTC_EN_MOBILE_CORPUS_CONCEPT_FILE = Paths.get("c:", "Users", "jieg",
                "Google Drive", "OAK Group", "jateResult4Yuan", "goldstandards", "gsTerms",
                "ttc-mobile-tech-gsTerms.txt");
        Path TTC_EN_WIND_CORPUS_CONCEPT_FILE = Paths.get("c:", "Users", "jieg",
                "Google Drive", "OAK Group", "jateResult4Yuan", "goldstandards", "gsTerms",
                "ttc-wind-energy-gsTerms.txt");

        // genia, aclrdtec1, ttcMobileEn, ttcWindEn
        String datasetName = "ttcWindEn";//args[0];
        String ateOutputFolder = "C:\\Users\\jieg\\Google Drive\\OAK Group\\jateResult4Yuan\\Training_testingData\\test\\en_wind";// args[1];
        String ateOutputType = "csv";//args[2];
        String outFile = "C:\\Users\\jieg\\Google Drive\\OAK Group\\jateResult4Yuan\\Training_testingData\\test\\en_wind\\en_wind_4_test_eval.txt";//args[3];
        String gsFile = TTC_EN_WIND_CORPUS_CONCEPT_FILE.toString();//args[4];


        if (datasetName.equals("genia")) {
            /* gsFile = GENIA_CORPUS_CONCEPT_FILE.toString()*/
            createReportGenia(lemmatiser, ateOutputFolder, ateOutputType,
                    gsFile, outFile,
                    EVAL_CONDITION_IGNORE_SYMBOL, EVAL_CONDITION_IGNORE_DIGITS, EVAL_CONDITION_CASE_INSENSITIVE,
                    EVAL_CONDITION_CHAR_RANGE_MIN, EVAL_CONDITION_CHAR_RANGE_MAX,
                    EVAL_CONDITION_TOKEN_RANGE_MIN, EVAL_CONDITION_TOKEN_RANGE_MAX,
                    EVAL_CONDITION_TOP_N, EVAL_CONDITION_TOP_K, IS_COMPUTE_ATR4S_AvP);
        } else {
            // no need to do prune&lemmatisation for ACL RD-TEC
            createReportACLRD(lemmatiser, ateOutputFolder, ateOutputType,
                    gsFile, outFile,
                    false, false, true,
                    -1, -1,
                    -1, -1,
                    EVAL_CONDITION_TOP_N, EVAL_CONDITION_TOP_K, IS_COMPUTE_ATR4S_AvP);
        }
    }
}
