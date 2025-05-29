package analyzer.util;

import java.util.logging.Logger;

public class Configuration {

    private Configuration() {}

    public static final boolean BASIC_DEBUG = true;
    public static final boolean ACTIVATE_LOG = false;
    public static final boolean HISTORY_DEBUG = false;
    public static final boolean TICKET_DEBUG = false;
    public static final boolean LABELING_DEBUG = false;
    public static final boolean ML_DEBUG = true;

    public static final Logger logger = Logger.getLogger(Configuration.class.getName());

    public static final String PROJECT1_NAME = ConfigurationLoader.get("project1.name");
    public static final String PROJECT1_PATH = ConfigurationLoader.get("project1.path");
    public static final String OUTPUT_CSV1_PATH = ConfigurationLoader.get("project1.output_csv");
    public static final String OUTPUT_ARFF1_PATH = ConfigurationLoader.get("project1.output_arff");
    public static final String PROJECT1_SUBSTRING = "bookkeeper/";
    public static final String PROJECT1_COLUMN = "Bookkeeper";
    public static final String DEBUG_SAMPLED_METHODS_PATH1 = ConfigurationLoader.get("debug.sampled_methods_path1");

}
