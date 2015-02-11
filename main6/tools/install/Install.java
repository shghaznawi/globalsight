/**
 * Copyright 2009 Welocalize, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.
 * 
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 * 
 */

import installer.InputOption;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;
import java.util.ResourceBundle;

import org.apache.commons.codec.binary.Base64;

import util.Action;
import util.InstallUtil;
import util.Page;

public class Install extends installer.EventBroadcaster
{
    private static final String BACKSLASH = "\\";

    private static final String DOUBLEBACKSLASH = "\\\\";

    private static final String FORWARDSLASH = "/";

    private static final String DOUBLEBACKSLASH_REG = "\\\\\\\\";

    private static final String DOUBLEBACKSLASH_REP = "\\\\\\\\\\\\\\\\";

    // constants for operating systems
    public static final int OS_WINDOWS = 1;

    public static final int OS_SOLARIS = 2;

    public static final int OS_HPUX = 3;

    public static final int OS_LINUX = 4;

    public static final String SERVER_JBOSS = "jboss";

    public static final String INSTALLATION_DATA_DIRECTORY = "data";

    public static final String INSTALLATION_MYSQL_DIRECTORY = "data/mysql";

    public static final String INSTALLATION_OPENLDAP_DIRECTORY = "data/openldap";

    public static final String INSTALLATION_OPENLDAP_SUB_DIRECTORY = INSTALLATION_OPENLDAP_DIRECTORY
            + "/globalsight";

    public static final String INSTALLATION_OPENLDAP_DIRECTORY_LINUX = "data/openldap-linux";

    public static final String INSTALLATION_OPENLDAP_SUB_DIRECTORY_LINUX = INSTALLATION_OPENLDAP_DIRECTORY_LINUX
            + "/globalsight";

    // public static final String DEPLOYMENT_DIRECTORY = "../deployment";
    public static final String DEPLOYMENT_DIRECTORY = "../jboss/jboss_server/server/default/deploy";

    public static Page PAGE = new Page();

    public static String GS_HOME = determineGsHome();

    public static String JAVA_SERVICE_WRAPPER_HOME = GS_HOME + File.separator
            + "install" + File.separator + "JavaServiceWrapper"
            + File.separator + "bin";

    public static String JBOSS_HOME = GS_HOME + File.separator + "jboss"
            + File.separator + "jboss_server";

    public static String JBOSS_BIN = JBOSS_HOME + File.separator + "bin";

    public static String NETEGRITY = GS_HOME + File.separator + "netegrity";

    public static String MYSQL_SQL_FILE = GS_HOME + File.separator + "install"
            + File.separator + "data" + File.separator + "mysql"
            + File.separator;

    public static String EAR_HOME = GS_HOME + File.separator + "deployment";

    public static final String TEMP_DIRECTORY = System
            .getProperty("java.io.tmpdir");

    // For the properties inside the installation directory, use
    // forward slashes in the path, which will work if they are
    // in a jar file also.
    public static final String INSTALL_ORDER_PROPERTIES_FILE = INSTALLATION_DATA_DIRECTORY
            + "/installOrder.properties";

    public static final String DEFAULT_INSTALL_VALUES_PROPERTIES_FILE = INSTALLATION_DATA_DIRECTORY
            + "/installDefaultValues.properties";

    public static final String DEFAULT_INSTALL_VALUES_PROPERTIES_FILE_NO_UI = INSTALLATION_DATA_DIRECTORY
            + "/installDefaultValuesNoUI.properties";

    public static String INSTALL_VALUES_PROPERTIES_FILE = INSTALLATION_DATA_DIRECTORY
            + "/installValues.properties";

    public static final String SYSTEM4_LAST_INSTALL_VALUES_PROPERTIES_FILE = concatPath(
            TEMP_DIRECTORY, "installValues.properties");

    private static final String INSTALL_GROUPS_PROPERTIES_FILE = "data/installOrderUI.properties";

    public static final String GS_KEY_DIR = concatPath(
            INSTALLATION_DATA_DIRECTORY, "key");

    private int m_operatingSystem;

    private ArrayList<String> m_installOrderArrayList = new ArrayList<String>();

    private Properties m_installValues = new Properties();

    private Properties m_groupProperties = new Properties();

    private Properties m_installDisplay = new Properties();

    private Properties m_installAmbassador = new Properties();

    private static final String SERVER_HOST = "server_host";

    private static final String[] creationSqlFiles = { "create_cap_mysql.sql",
            "create_snippet_tables_mysql.sql", "vendor_management_mysql.sql",
            "create_new_job_table_mysql.sql", "create_cxe_mysql.sql",
            "insert_locales_mysql.sql", "insert_currency_codes_mysql.sql",
            "insert_template_formats_mysql.sql",
            "insert_known_formats_mysql.sql",
            "insert_system_parameters_mysql.sql",
            "insert_UImodifiable_system_parameters_mysql.sql",
            "insert_extensions_mysql.sql", "insert_exportlocation_mysql.sql",
            "insert_default_calendar_mysql.sql",
            "insert_leverage_group_mysql.sql", "create_tm_mysql.sql",
            "create_in_progress_tm_mysql.sql", "create_corpus_map_mysql.sql",
            "create_termbase_tables_mysql.sql",
            "insert_termbase_data_mysql.sql", "create_jbpm_tables.sql",
            "create_quartz_tables_mysql.sql" };

    private static final String[] cleanSqlFiles = { "drop_all_mysql.sql",
            "drop_jbpm_tables.sql", "drop_quartz_tables_mysql.sql" };

    // Files to copy, stored in tables so they can be counted. Note that
    // they only store the names of individual files, not directories.
    private Hashtable<String, String> m_configFileList = null;

    private static ResourceBundle RESOURCE = ResourceBundle
            .getBundle("data/installNoUI");

    private static ResourceBundle INSTALL_OPTION_RESOURCE = ResourceBundle
            .getBundle("data/installAmbassador");

    private static String TITLE = RESOURCE.getString("title");

    private static final int WIDTH = 60;

    private static final int LINE_START = 16;

    private static final String DIVIDE_CHAR = "=";

    private static final String Line_CHAR = "*";

    public final static Action QUIT_ACTION = new Action(RESOURCE
            .getString("quit_key"), RESOURCE.getString("quit_name"), 0)
    {
        public void doAction()
        {
            System.exit(0);
        }
    };

    public final Action BACK_ACTION = new Action(
            RESOURCE.getString("back_key"), RESOURCE.getString("back_name"), -1)
    {
        public void doAction()
        {
            gotoPropertiesPage(PAGE.previous());
        }
    };

    public final Action NEXT_ACTION = new Action(
            RESOURCE.getString("next_key"), RESOURCE.getString("next_name"), -2)
    {
        public void doAction()
        {
            gotoPropertiesPage(PAGE.next());
        }
    };

    public final Action UP_ACTION = new Action(RESOURCE.getString("up_key"),
            RESOURCE.getString("up_name"), -3)
    {
        public void doAction()
        {
            gotoPropertiesPage(PAGE.getIndex(), PAGE.up());
        }
    };

    public final Action DOWN_ACTION = new Action(
            RESOURCE.getString("down_key"), RESOURCE.getString("down_name"), -4)
    {
        public void doAction()
        {
            gotoPropertiesPage(PAGE.getIndex(), PAGE.down());
        }
    };

    public final Action INSTALL_ACTION = new Action(RESOURCE
            .getString("install_key"), RESOURCE.getString("install_name"), -5)
    {
        public void doAction()
        {
            try
            {
                install();
                System.exit(0);
            }
            catch (Exception e)
            {
                System.err.println("Problem running installer.");
                e.printStackTrace();
            }
        }
    };

    public final Action WELCOLM_PAGE_ACTION = new Action(RESOURCE
            .getString("welcolm_page_key"), RESOURCE
            .getString("welcolm_page_name"), -6)
    {
        public void doAction()
        {
            gotoWelcomePage();
        }
    };

    public final Action INSTALL_PAGE_ACTION = new Action(RESOURCE
            .getString("install_page_key"), RESOURCE
            .getString("install_page_name"), -7)
    {
        public String toString()
        {
            return getKey() + ". " + getDisplayName();
        }

        public void doAction()
        {
            gotoPropertiesPage(InstallUtil.getPropertiesSize());
        }
    };

    public final Action LOAD_SETTINGS_ACTION = new Action(RESOURCE
            .getString("load_settings_key"), RESOURCE
            .getString("load_settings_name"), -8)
    {
        public void doAction()
        {
            File file = InstallUtil.getSettingsFile();

            if (file != null)
            {
                try
                {
                    loadProperties(file.getAbsolutePath(), m_installValues);
                }
                catch (IOException e)
                {
                    System.out.println(RESOURCE
                            .getString("error_settings_path")
                            + file.getAbsolutePath());
                }
            }

            gotoPropertiesPage(PAGE.getIndex(), PAGE.getPageNumber());
        }
    };

    public static void main(String[] args)
    {
        try
        {
            Install installer = new Install();

            for (int i = 0; i < args.length; i++)
            {
                if ("-properties".equals(args[i]) && i + 1 < args.length)
                {
                    setLastInstallValuesLocation(args[i + 1]);
                    ++i;
                }
            }

            installer.installSystem4();
        }
        catch (Exception e)
        {
            System.err.println("Problem running installer.");
            e.printStackTrace();
        }
    }

    private static void printChar(String c, int n)
    {
        for (int i = 0; i < n; i++)
        {
            System.out.print(c);
        }
        System.out.println("");
    }

    private static void printLine(String pageName, int start)
    {
        int length = pageName.length();
        System.out.print(Line_CHAR);
        for (int i = 0; i < start; i++)
        {
            System.out.print(" ");
        }
        System.out.print(pageName);
        for (int i = 0; i < WIDTH - length - start - 2; i++)
        {
            System.out.print(" ");
        }
        System.out.print(Line_CHAR);
        System.out.println("");
    }

    private void printWelcomePage(List<Action> actions)
    {
        System.out.println();
        printChar(DIVIDE_CHAR, WIDTH);
        printLine("", 0);
        printLine(TITLE, (WIDTH - TITLE.length()) / 2);
        printLine("", 0);

        List<String> pages = InstallUtil.getPages();
        for (int i = 0; i < pages.size(); i++)
        {
            int index = i + 1;
            printLine(index + ". " + (String) pages.get(i), LINE_START);
        }
        printLine("", 0);
        String actionList = "";
        for (int i = 0; i < actions.size(); i++)
        {

            Action action = (Action) actions.get(i);
            actionList += action.toString() + "    ";
        }

        printLine(actionList, (WIDTH - actionList.length()) / 2);
        printLine("", 0);
        printChar(DIVIDE_CHAR, WIDTH);
    }

    private void printPage(int pageIndex, List<Action> actions, int page)
    {
        Properties storeProperty = m_installValues;
        if (pageIndex == InstallUtil.getPropertiesSize())
        {
            storeProperty = InstallUtil.getInstallOptions();
        }

        List<InputOption> properties = InstallUtil.getPageOptions(pageIndex);
        int min = 0;
        int max = properties.size();

        if (min < page)
        {
            min = page * Page.MAX_ROW;
            actions.add(UP_ACTION);
        }

        if (max > (page + 1) * Page.MAX_ROW)
        {
            max = (page + 1) * Page.MAX_ROW;
            actions.add(DOWN_ACTION);
        }

        System.out.print("\n--");
        StringBuffer pageName = new StringBuffer();
        List<String> pages = InstallUtil.getPages();
        pageName.append(" ").append((String) pages.get(pageIndex - 1)).append(
                " ");
        if (properties.size() > Page.MAX_ROW)
        {
            int pageNum = properties.size() / Page.MAX_ROW;
            if (properties.size() % Page.MAX_ROW > 0)
            {
                pageNum++;
            }

            pageName.append("(");
            pageName.append(page + 1);
            pageName.append("/");
            pageName.append(pageNum);
            pageName.append(")");
        }
        System.out.print(pageName);
        for (int i = 0; i < WIDTH - pageName.length() - 2; i++)
        {
            System.out.print("-");
        }
        System.out.println("\n");

        for (int i = min; i < max; i++)
        {
            InputOption option = (InputOption) properties.get(i);
            String value = storeProperty.getProperty(option.getKey());
            if (value != null)
            {
                String returnValue = option.checkValue(value);
                if (!returnValue.equals(value))
                {
                    value = returnValue;
                    storeProperty.put(option.getKey(), returnValue);
                }
            }
            System.out.println(i + 1 + " : " + option.getDesplayValue() + " = "
                    + value);
        }

        System.out.println("");
        for (int i = 0; i < actions.size(); i++)
        {
            Action action = (Action) actions.get(i);

            System.out.print(action.toString());
        }

        System.out.println("\n");
        for (int i = 0; i < WIDTH; i++)
        {
            System.out.print("-");
        }
        System.out.println();
    }

    private void loadProperties() throws Exception
    {
        loadProperties(INSTALL_GROUPS_PROPERTIES_FILE, m_groupProperties);
        loadInstallDisplay();
        loadInstallAmbassador();
        loadOrder();
        loadInstallValues();
        addAdditionalInstallValues();
    }

    void installSystem4() throws Exception
    {
        // boolean modifySystemParameterTable = false;
        System.out.println("\n");

        if (!determineOperatingSystem())
        {
            System.err.println("GlobalSight cannot be installed on this OS.");
            System.err.println("Install aborted.");
            System.exit(1);
        }

        // Load the installOrder hash from the installOrder properties file
        loadProperties();
        gotoInfoPage();
        gotoWelcomePage();
    }

    private void gotoInfoPage()
    {
        clear();

        System.out.print("\n-- " + TITLE + " ");
        for (int i = 0; i < WIDTH - TITLE.length() - 4; i++)
        {
            System.out.print("-");
        }
        System.out.println("\n");

        List<Action> actions = new ArrayList<Action>();

        actions.add(QUIT_ACTION);
        actions.add(LOAD_SETTINGS_ACTION);
        actions.add(NEXT_ACTION);

        System.out.println(m_installAmbassador
                .getProperty("pre_install_message_no_ui"));
        System.out.println();
        for (int i = 0; i < actions.size(); i++)
        {
            Action action = (Action) actions.get(i);
            System.out.print(action.toString());
        }

        System.out.println("\n");
        for (int i = 0; i < WIDTH; i++)
        {
            System.out.print("-");
        }
        System.out.println("\n");

        int inputValue = InstallUtil.getSelection(0, actions);
        for (int i = 0; i < actions.size(); i++)
        {
            Action action = (Action) actions.get(i);
            if (action.getValue() == inputValue)
            {
                action.doAction();
            }
        }
    }

    private void clear()
    {
        try
        {
            if (m_operatingSystem == OS_LINUX)
            {
                String[] cmd = { "sh", "./data/linux/clearScreen.sh" };
                execute(cmd);
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private void gotoWelcomePage()
    {
        clear();
        PAGE.init();

        List<Action> actions = new ArrayList<Action>();
        actions.add(QUIT_ACTION);
        actions.add(LOAD_SETTINGS_ACTION);
        actions.add(NEXT_ACTION);

        printWelcomePage(actions);
        List<String> pages = InstallUtil.getPages();
        int inputValue = InstallUtil.getSelection(pages.size(), actions);

        for (int i = 0; i < actions.size(); i++)
        {
            Action action = (Action) actions.get(i);
            if (action.getValue() == inputValue)
            {
                action.doAction();
            }
        }

        gotoPropertiesPage(inputValue);
    }

    private void gotoPropertiesPage(int n)
    {
        gotoPropertiesPage(n, 0);
    }

    private void gotoPropertiesPage(int pageIndex, int page)
    {
        clear();
        if (pageIndex < 1 || pageIndex > InstallUtil.getPropertiesSize())
        {
            gotoWelcomePage();
            return;
        }

        PAGE.setIndex(pageIndex);
        List<InputOption> properties = InstallUtil.getPageOptions(pageIndex);

        List<Action> actions = new ArrayList<Action>();
        actions.add(QUIT_ACTION);
        actions.add(WELCOLM_PAGE_ACTION);
        actions.add(LOAD_SETTINGS_ACTION);

        if (pageIndex > 0)
        {
            actions.add(BACK_ACTION);
        }
        if (pageIndex < InstallUtil.getPropertiesSize())
        {
            actions.add(NEXT_ACTION);
        }
        if (pageIndex >= InstallUtil.getPropertiesSize())
        {
            actions.add(INSTALL_ACTION);
        }

        printPage(pageIndex, actions, page);
        int size = properties.size();
        int inputValue = 0;

        if (size > Page.MAX_ROW)
        {
            int min = PAGE.getPageNumber() * Page.MAX_ROW + 1;
            int max = (PAGE.getPageNumber() + 1) * Page.MAX_ROW;
            if (max > size)
            {
                max = size;
            }

            inputValue = InstallUtil.getSelection(min, max, actions);
        }
        else
        {
            inputValue = InstallUtil.getSelection(properties.size(), actions);
        }

        for (int i = 0; i < actions.size(); i++)
        {
            Action action = (Action) actions.get(i);
            if (action.getValue() == inputValue)
            {
                action.doAction();
            }
        }

        Properties storeProperty = m_installValues;
        if (pageIndex == InstallUtil.getPropertiesSize())
        {
            storeProperty = InstallUtil.getInstallOptions();
        }

        InputOption option = (InputOption) properties.get(inputValue - 1);
        String value = InstallUtil.getInput(option);
        if (value.length() > 0)
        {
            storeProperty.put(option.getKey(), value);
        }

        gotoPropertiesPage(pageIndex, (inputValue - 1) / Page.MAX_ROW);
    }

    private void install() throws Exception
    {
        System.out.println("\nStoring properties...");
        storeUserInput();

        String createDatabase = InstallUtil.getInstallOptions().getProperty(
                InstallUtil.CREATE_DATABASE);
        if (createDatabase.equalsIgnoreCase("true"))
        {
            String confirm = m_installAmbassador
                    .getProperty("alert.confirm_create_db_no_ui");
            InputOption confirmCreateTable = new InputOption(null, confirm,
                    InputOption.YES_NO);
            String input = InstallUtil.getInput(confirmCreateTable, ":");
            if ("n".equalsIgnoreCase(input))
            {
                gotoPropertiesPage(InstallUtil.getPropertiesSize());
            }
        }

        String generate = InstallUtil.getInstallOptions().getProperty(
                InstallUtil.GENERATE_CONFGURATEION_FILE);
        if (generate.equalsIgnoreCase("true"))
        {
            System.out.println(INSTALL_OPTION_RESOURCE
                    .getString(InstallUtil.GENERATE_CONFGURATEION_FILE));
            System.out.println();
            processFiles();
            System.out.println();
        }

        String createService = InstallUtil.getInstallOptions().getProperty(
                InstallUtil.CREATE_NT_SERVICE);
        if (createService.equalsIgnoreCase("true"))
        {
            System.out.println(INSTALL_OPTION_RESOURCE
                    .getString(InstallUtil.CREATE_NT_SERVICE));
            System.out.println();
            installGlobalSightService();
        }

        if (createDatabase.equalsIgnoreCase("true"))
        {
            System.out.println();
            System.out.println(INSTALL_OPTION_RESOURCE
                    .getString(InstallUtil.CREATE_DATABASE));
            System.out.println();
            createDatabaseTables();
        }
        else
        {
            System.out.println();
            System.out.println("Update tables");
            updateDatabaseTables();
        }

        String merge = InstallUtil.getInstallOptions().getProperty(
                InstallUtil.MERGE_PROPERTIES);
        if (merge.equalsIgnoreCase("true"))
        {
            String previousAmbassadorHome = readEntry(
                    "Enter the path to the previous GlobalSight home", "");
            new MergeProperties(previousAmbassadorHome, Install.GS_HOME);
        }

        System.out.println();
        System.out.println(RESOURCE.getString("install_finish"));
        System.in.read();
    }

    /**
     * Sets the location of where to read the previous values. By default it is
     * <temp>/installValues.properties.
     * <p>
     * 
     * @param p_propertyFileLocation
     *            path to properties file
     */
    public static void setLastInstallValuesLocation(
            String p_propertyFileLocation)
    {
        INSTALL_VALUES_PROPERTIES_FILE = p_propertyFileLocation;
    }

    public int getOperatingSystem()
    {
        return m_operatingSystem;
    }

    public String getInstallValue(String key)
    {
        return m_installValues.getProperty(key);
    }

    public void setInstallValue(String key, String value)
    {
        m_installValues.put(key, value);
    }

    public void clearInstallValues()
    {
        m_installValues.clear();
    }

    public ArrayList<String> getInstallOrderArrayList()
    {
        return m_installOrderArrayList;
    }

    /**
     * Load the specified file into the properties object. The file is loaded
     * from the file system first. If it is not found there, it is loaded as a
     * resource, in case the file is stored in the same jar file as the class.
     * <p>
     * 
     * @param p_propertyFileName
     *            name of file to load
     * @param p_properties
     *            object to load into
     * @throws FileNotFoundException
     * @throws IOException
     */
    private InputStream getResource(String p_propertyFileName)
            throws IOException
    {
        InputStream inputstream;

        try
        {
            // Open the file in its specified location
            inputstream = new FileInputStream(p_propertyFileName);
        }
        catch (FileNotFoundException ex)
        {
            // File is not found, so try to open it again as a resource.
            inputstream = getClass().getResourceAsStream(p_propertyFileName);

            // File is still not found, so pass on the exception
            if (inputstream == null) throw ex;
        }

        return inputstream;
    }

    /**
     * Load the specified file into the properties object. The file is loaded
     * from the file system first. If it is not found there, it is loaded as a
     * resource, in case the file is stored in the same jar file as the class.
     * <p>
     * 
     * @param p_propertyFileName
     *            name of file to load
     * @param p_properties
     *            object to load into
     * @throws FileNotFoundException
     * @throws IOException
     */
    public void loadProperties(String p_propertyFileName,
            Properties p_properties) throws IOException
    {
        InputStream inputstream = getResource(p_propertyFileName);
        p_properties.load(inputstream);
        inputstream.close();
    }

    /**
     * Load the specified file into a new properties object. The file is loaded
     * from the file system first. If it is not found there, it is loaded as a
     * resource, in case the file is stored in the same jar file as the class.
     * <p>
     * 
     * @param p_propertyFileName
     *            name of file to load
     * @return properties
     * @throws FileNotFoundException
     * @throws IOException
     */
    public Properties loadProperties(String p_propertyFileName)
            throws IOException
    {
        Properties properties = new Properties();
        loadProperties(p_propertyFileName, properties);
        return properties;
    }

    /**
     * Load the default and saved install values. The default values are loaded
     * first, then the last saved values are loaded. If they exist, then the
     * last saved values will overwrite the default values. Any new values that
     * were not saved before will continue to have the default values.
     * <p>
     * 
     * @throws IOException
     */
    public boolean loadInstallValues() throws IOException
    {
        boolean lastSettingsFileLoaded = true;

        try
        {
            if (m_operatingSystem == OS_WINDOWS)
            {
                // Load the default values
                loadProperties(DEFAULT_INSTALL_VALUES_PROPERTIES_FILE,
                        m_installValues);
            }
            else if (m_operatingSystem == OS_LINUX)
            {
                loadProperties(DEFAULT_INSTALL_VALUES_PROPERTIES_FILE_NO_UI,
                        m_installValues);
            }
            else
            {
                System.err.println("Your OS is not supported.");
            }

            // Now load the last saved values
            try
            {
                loadProperties(INSTALL_VALUES_PROPERTIES_FILE, m_installValues);
            }
            catch (IOException exception)
            {
                try
                {
                    loadProperties(SYSTEM4_LAST_INSTALL_VALUES_PROPERTIES_FILE,
                            m_installValues);
                }
                catch (IOException exception2)
                {
                    // Previously saved values do not exist, so the
                    // default values remain.
                    lastSettingsFileLoaded = false;
                    System.out
                            .println("Using default (first time) installation values.");
                }
            }

        }
        catch (IOException e)
        {
            System.out.println("Error reading Install values "
                    + DEFAULT_INSTALL_VALUES_PROPERTIES_FILE);
            throw e;
        }

        return lastSettingsFileLoaded;
    }

    // Get the order
    public void loadOrderedList(String p_propertiesFile,
            ArrayList<String> p_list) throws IOException
    {
        InputStream inputstream = getResource(p_propertiesFile);
        BufferedReader in = new BufferedReader(new InputStreamReader(
                inputstream));

        String str;
        while ((str = in.readLine()) != null)
        {
            if (str.startsWith("#")) // It's a comment
            {
                continue;
            }
            else if (str.length() > 0)
            {
                p_list.add(str);
            }
        }

        in.close();
    }

    // Get the order
    public void loadOrder() throws IOException
    {
        try
        {
            loadOrderedList(INSTALL_ORDER_PROPERTIES_FILE,
                    m_installOrderArrayList);
        }
        catch (IOException e)
        {
            System.out.println("Error reading Install order "
                    + INSTALL_ORDER_PROPERTIES_FILE);
            throw e;
        }
    }

    // load the install display string properties file
    private void loadInstallAmbassador() throws IOException
    {
        // Load the key-displayname hash from the properties file
        String dir = INSTALLATION_DATA_DIRECTORY
                + "/installAmbassador.properties";

        try
        {
            loadProperties(dir, m_installAmbassador);
        }
        catch (IOException e)
        {
            System.out.println("Error reading Install display strings " + dir);
            throw e;
        }
    }

    // load the install display string properties file
    private void loadInstallDisplay() throws IOException
    {
        // Load the key-displayname hash from the properties file
        String installDisplayProperties = INSTALLATION_DATA_DIRECTORY
                + "/installDisplay.properties";

        try
        {
            loadProperties(installDisplayProperties, m_installDisplay);
        }
        catch (IOException e)
        {
            System.out.println("Error reading Install display strings "
                    + installDisplayProperties);
            throw e;
        }
    }

    // Save user settings
    public void storeUserInput() throws IOException
    {
        IOException exception = null;

        try
        {
            storeUserInput(SYSTEM4_LAST_INSTALL_VALUES_PROPERTIES_FILE);
        }
        catch (IOException e1)
        {
            exception = e1;
        }

        try
        {
            storeUserInput(concatPath(INSTALLATION_DATA_DIRECTORY,
                    "installValues.properties"));
        }
        catch (IOException e3)
        {
            exception = e3;
        }

        if (exception != null)
        {
            throw exception;
        }
    }

    // Save user settings
    private void storeUserInput(String p_fileName) throws IOException
    {
        try
        {
            System.out.print("\nSaving your settings to " + p_fileName
                    + ".\n\n");

            m_installValues.store(new FileOutputStream(p_fileName), null);
        }
        catch (IOException e)
        {
            System.out.println("Error saving your settings " + p_fileName);
            throw e;
        }
    }

    // initializes the list of configuration files
    private void initializeConfigurationFileList()
    {
        m_configFileList = new Hashtable<String, String>();

        String choice = SERVER_JBOSS;
        m_configFileList.put(concatPath(GS_HOME, choice
                + "/application.xml.template"), concatPath(
                DEPLOYMENT_DIRECTORY,
                "globalsight.ear/META-INF/application.xml"));

        // startJboss.cmd.template
        if (SERVER_JBOSS.equals(choice))
        {
            m_configFileList.put(concatPath(GS_HOME,
                    "jboss/startJboss.cmd.template"), concatPath(GS_HOME,
                    "jboss/startJboss.cmd"));
            // jboss-service.xml
            m_configFileList.put(concatPath(GS_HOME,
                    "jboss/jboss-service.xml.template"), concatPath(GS_HOME,
                    "jboss/jboss-service.xml"));

            m_configFileList.put(concatPath(GS_HOME,
                    "jboss/server.xml.template"), concatPath(GS_HOME,
                    "jboss/server.xml"));

            m_configFileList.put(concatPath(GS_HOME,
                    "jboss/mail-service.xml.template"), concatPath(GS_HOME,
                    "jboss/mail-service.xml"));
            
            // mysql-ds.xml
            m_configFileList.put(concatPath(GS_HOME,
                    "jboss/mysql-ds.xml.template"), concatPath(DEPLOYMENT_DIRECTORY,
                    "mysql-ds.xml"));

            // Jboss-web.xml
            m_configFileList
                    .put(
                            concatPath(DEPLOYMENT_DIRECTORY,
                                    "globalsight.ear/globalsight-web.war/WEB-INF/jboss-web.xml.template"),
                            concatPath(DEPLOYMENT_DIRECTORY,
                                    "globalsight.ear/globalsight-web.war/WEB-INF/jboss-web.xml"));

            // Jboss-web.xml
            m_configFileList
                    .put(
                            concatPath(DEPLOYMENT_DIRECTORY,
                                    "globalsight.ear/xdespellchecker.war/WEB-INF/jboss-web.xml.template"),
                            concatPath(DEPLOYMENT_DIRECTORY,
                                    "globalsight.ear/xdespellchecker.war/WEB-INF/jboss-web.xml"));

            // Jboss-web.xml
            m_configFileList
                    .put(
                            concatPath(DEPLOYMENT_DIRECTORY,
                                    "globalsight.ear/spellchecker.war/WEB-INF/jboss-web.xml.template"),
                            concatPath(DEPLOYMENT_DIRECTORY,
                                    "globalsight.ear/spellchecker.war/WEB-INF/jboss-web.xml"));
        }

        // Process files in the deployment directory

        m_configFileList.put(concatPath(DEPLOYMENT_DIRECTORY,
                "globalsight.ear/lib/classes/hibernate-jbpm.cfg.xml.template"),
                concatPath(DEPLOYMENT_DIRECTORY,
                        "globalsight.ear/lib/classes/hibernate-jbpm.cfg.xml"));
        m_configFileList
                .put(
                        concatPath(DEPLOYMENT_DIRECTORY,
                                "globalsight.ear/globalsight-web.war/WEB-INF/classes/log4j.properties.template"),
                        concatPath(DEPLOYMENT_DIRECTORY,
                                "globalsight.ear/globalsight-web.war/WEB-INF/classes/log4j.properties"));

        m_configFileList.put(concatPath(DEPLOYMENT_DIRECTORY,
                "globalsight.ear/lib/classes/quartz.properties.template"),
                concatPath(DEPLOYMENT_DIRECTORY,
                        "globalsight.ear/lib/classes/quartz.properties"));

        m_configFileList
                .put(
                        concatPath(DEPLOYMENT_DIRECTORY,
                                "globalsight.ear/globalsight-web.war/WEB-INF/web.xml.template"),
                        concatPath(DEPLOYMENT_DIRECTORY,
                                "globalsight.ear/globalsight-web.war/WEB-INF/web.xml"));

        m_configFileList
                .put(
                        concatPath(DEPLOYMENT_DIRECTORY,
                                "globalsight.ear/lib/classes/properties/envoy_generated.properties.template"),
                        concatPath(DEPLOYMENT_DIRECTORY,
                                "globalsight.ear/lib/classes/properties/envoy_generated.properties"));

        m_configFileList
                .put(
                        concatPath(DEPLOYMENT_DIRECTORY,
                                "globalsight.ear/lib/classes/properties/db_connection.properties.template"),
                        concatPath(DEPLOYMENT_DIRECTORY,
                                "globalsight.ear/lib/classes/properties/db_connection.properties"));

        m_configFileList.put(concatPath(DEPLOYMENT_DIRECTORY,
                "globalsight.ear/lib/classes/hibernate.properties.template"),
                concatPath(DEPLOYMENT_DIRECTORY,
                        "globalsight.ear/lib/classes/hibernate.properties"));

        m_configFileList
                .put(
                        concatPath(DEPLOYMENT_DIRECTORY,
                                "globalsight.ear/lib/classes/properties/Logger.properties.template"),
                        concatPath(DEPLOYMENT_DIRECTORY,
                                "globalsight.ear/lib/classes/properties/Logger.properties"));

        // process the XDE spellchecker files
        m_configFileList
                .put(
                        concatPath(DEPLOYMENT_DIRECTORY,
                                "globalsight.ear/xdespellchecker.war/WEB-INF/web.xml.template"),
                        concatPath(DEPLOYMENT_DIRECTORY,
                                "globalsight.ear/xdespellchecker.war/WEB-INF/web.xml"));

        // process the GlobalSight spellchecker files
        m_configFileList.put(concatPath(DEPLOYMENT_DIRECTORY,
                "globalsight.ear/spellchecker.war/WEB-INF/web.xml.template"),
                concatPath(DEPLOYMENT_DIRECTORY,
                        "globalsight.ear/spellchecker.war/WEB-INF/web.xml"));

        m_configFileList
                .put(
                        concatPath(
                                DEPLOYMENT_DIRECTORY,
                                "globalsight.ear/spellchecker.war/WEB-INF/classes/spell/spell.properties.template"),
                        concatPath(DEPLOYMENT_DIRECTORY,
                                "globalsight.ear/spellchecker.war/WEB-INF/classes/spell/spell.properties"));

        // process command line scripts
        m_configFileList.put(concatPath(DEPLOYMENT_DIRECTORY,
                "globalsight.ear/bin/CreateDictionary.cmd.template"),
                concatPath(DEPLOYMENT_DIRECTORY,
                        "globalsight.ear/bin/CreateDictionary.cmd"));

        m_configFileList
                .put(
                        concatPath(DEPLOYMENT_DIRECTORY,
                                "globalsight.ear/globalsight-web.war/reports/datasource.xml.template"),
                        concatPath(DEPLOYMENT_DIRECTORY,
                                "globalsight.ear/globalsight-web.war/reports/datasource.xml"));

        // teamsite auto import
        m_configFileList
                .put(
                        concatPath(DEPLOYMENT_DIRECTORY,
                                "globalsight.ear/lib/classes/properties/autoTeamSiteImport.map"),
                        concatPath(INSTALLATION_DATA_DIRECTORY,
                                "teamsite/tsautoimport/properties/autoTeamSiteImport.map"));

        m_configFileList
                .put(
                        concatPath(DEPLOYMENT_DIRECTORY,
                                "globalsight.ear/lib/classes/properties/autoTeamSiteImport.properties"),
                        concatPath(INSTALLATION_DATA_DIRECTORY,
                                "teamsite/tsautoimport/properties/autoTeamSiteImport.properties"));

        m_configFileList
                .put(
                        concatPath(INSTALLATION_DATA_DIRECTORY,
                                "teamsite/tsautoimport/properties/teamsiteParams.properties.template"),
                        concatPath(INSTALLATION_DATA_DIRECTORY,
                                "teamsite/tsautoimport/properties/teamsiteParams.properties"));

        // files for LDAP (Windows Version)
        // ldif file
        m_configFileList.put(concatPath(INSTALLATION_OPENLDAP_DIRECTORY,
                "globalsight.ldif.template"), concatPath(
                INSTALLATION_OPENLDAP_DIRECTORY, "globalsight.ldif"));

        m_configFileList.put(concatPath(INSTALLATION_OPENLDAP_DIRECTORY,
                "addCustomer.ldif.template"), concatPath(
                INSTALLATION_OPENLDAP_DIRECTORY, "addCustomer.ldif"));

        m_configFileList.put(concatPath(INSTALLATION_OPENLDAP_DIRECTORY,
                "vendor_management.ldif.template"), concatPath(
                INSTALLATION_OPENLDAP_DIRECTORY, "vendor_management.ldif"));

        // acl file
        m_configFileList.put(concatPath(INSTALLATION_OPENLDAP_SUB_DIRECTORY,
                "globalsight.acl.template"), concatPath(
                INSTALLATION_OPENLDAP_SUB_DIRECTORY, "globalsight.acl"));

        // conf file
        m_configFileList.put(concatPath(INSTALLATION_OPENLDAP_DIRECTORY,
                "ldap.conf.template"), concatPath(
                INSTALLATION_OPENLDAP_DIRECTORY, "ldap.conf"));

        m_configFileList.put(concatPath(INSTALLATION_OPENLDAP_DIRECTORY,
                "slapd.conf.template"), concatPath(
                INSTALLATION_OPENLDAP_DIRECTORY, "slapd.conf"));

        // files for LDAP (Linux Version)
        // ldif file
        m_configFileList.put(concatPath(INSTALLATION_OPENLDAP_DIRECTORY_LINUX,
                "globalsight.ldif.template"), concatPath(
                INSTALLATION_OPENLDAP_DIRECTORY_LINUX, "globalsight.ldif"));

        m_configFileList.put(concatPath(INSTALLATION_OPENLDAP_DIRECTORY_LINUX,
                "addCustomer.ldif.template"), concatPath(
                INSTALLATION_OPENLDAP_DIRECTORY_LINUX, "addCustomer.ldif"));

        m_configFileList.put(concatPath(INSTALLATION_OPENLDAP_DIRECTORY_LINUX,
                "vendor_management.ldif.template"),
                concatPath(INSTALLATION_OPENLDAP_DIRECTORY_LINUX,
                        "vendor_management.ldif"));

        // acl file
        m_configFileList.put(concatPath(
                INSTALLATION_OPENLDAP_SUB_DIRECTORY_LINUX,
                "globalsight.acl.template"), concatPath(
                INSTALLATION_OPENLDAP_SUB_DIRECTORY_LINUX, "globalsight.acl"));

        // conf file
        m_configFileList.put(concatPath(INSTALLATION_OPENLDAP_DIRECTORY_LINUX,
                "ldap.conf.template"), concatPath(
                INSTALLATION_OPENLDAP_DIRECTORY_LINUX, "ldap.conf"));

        m_configFileList.put(concatPath(INSTALLATION_OPENLDAP_DIRECTORY_LINUX,
                "slapd.conf.template"), concatPath(
                INSTALLATION_OPENLDAP_DIRECTORY_LINUX, "slapd.conf"));

        // ldif file for netegrity integration - vendor viewer
        String vvLdifPath = concatPath(NETEGRITY,
                "migrate_anonymous_vv.ldif.template");
        File vvLdifFile = new File(vvLdifPath);
        if (vvLdifFile.exists())
        {
            m_configFileList.put(vvLdifPath, concatPath(NETEGRITY,
                    "migrate_anonymous_vv.ldif"));
        }

        m_configFileList.put(concatPath(INSTALLATION_MYSQL_DIRECTORY,
                "create_cap_mysql.sql.template"), concatPath(
                INSTALLATION_MYSQL_DIRECTORY, "create_cap_mysql.sql"));

        m_configFileList.put(concatPath(INSTALLATION_MYSQL_DIRECTORY,
                "delete_index_tables_mysql.sql.template"), concatPath(
                INSTALLATION_MYSQL_DIRECTORY, "delete_index_tables_mysql.sql"));

        m_configFileList.put(concatPath(INSTALLATION_MYSQL_DIRECTORY,
                "gsdb_mysql.sql.template"), concatPath(
                INSTALLATION_MYSQL_DIRECTORY, "gsdb_mysql.sql"));

        m_configFileList.put(concatPath(INSTALLATION_MYSQL_DIRECTORY,
                "insert_default_calendar_mysql.sql.template"), concatPath(
                INSTALLATION_MYSQL_DIRECTORY,
                "insert_default_calendar_mysql.sql"));

        m_configFileList.put(concatPath(INSTALLATION_MYSQL_DIRECTORY,
                "insert_exportlocation_mysql.sql.template"),
                concatPath(INSTALLATION_MYSQL_DIRECTORY,
                        "insert_exportlocation_mysql.sql"));

        m_configFileList.put(concatPath(INSTALLATION_MYSQL_DIRECTORY,
                "insert_system_parameters_mysql.sql.template"), concatPath(
                INSTALLATION_MYSQL_DIRECTORY,
                "insert_system_parameters_mysql.sql"));

        m_configFileList.put(concatPath(INSTALLATION_MYSQL_DIRECTORY,
                "insert_UImodifiable_system_parameters_mysql.sql.template"),
                concatPath(INSTALLATION_MYSQL_DIRECTORY,
                        "insert_UImodifiable_system_parameters_mysql.sql"));

        m_configFileList.put(concatPath(INSTALLATION_MYSQL_DIRECTORY,
                "update_exportlocation_mysql.sql.template"),
                concatPath(INSTALLATION_MYSQL_DIRECTORY,
                        "update_exportlocation_mysql.sql"));

        m_configFileList.put(concatPath(INSTALLATION_MYSQL_DIRECTORY,
                "drop_all_mysql.sql.template"), concatPath(
                INSTALLATION_MYSQL_DIRECTORY, "drop_all_mysql.sql"));

        m_configFileList.put(concatPath(INSTALLATION_MYSQL_DIRECTORY,
                "drop_jbpm_tables.sql.template"), concatPath(
                INSTALLATION_MYSQL_DIRECTORY, "drop_jbpm_tables.sql"));

        // Configuration file for Java Service Wrapper
        m_configFileList.put(concatPath("JavaServiceWrapper/conf",
                "wrapper.conf.template"), concatPath("JavaServiceWrapper/conf",
                "wrapper.conf"));

        m_configFileList.put(
                concatPath(GS_HOME, "jboss/startJboss.sh.template"),
                concatPath(GS_HOME, "jboss/startJboss.sh"));
    }

    // returns the number of configuration files in the list
    public int countConfigurationFiles()
    {
        if (m_configFileList == null)
        {
            initializeConfigurationFileList();
        }

        // Add the two files not included in this list to the count,
        int size = m_configFileList.size() + 3;

        return size;
    }

    private void copyFilesToJbossHome() throws Exception
    {
        System.out
                .println("\nCopying GlobalSight-JMS-service.xml to jboss home directory.");

        String deployDir = GS_HOME + FORWARDSLASH
                + "jboss/jboss_server/server/default/deploy";
        String configDir = GS_HOME + FORWARDSLASH
                + "jboss/jboss_server/server/default/conf";
        String jboss_tomcat_sar_dir = GS_HOME
                + FORWARDSLASH
                + "jboss/jboss_server/server/default/deploy/jbossweb-tomcat50.sar";
        RecursiveCopy copier = new RecursiveCopy();

        copier.copyFile(concatPath(GS_HOME, "jboss/jboss-service.xml"),
                configDir);

        copier.copyFile(
                concatPath(GS_HOME, "jboss/GlobalSight-JMS-service.xml"),
                deployDir);
        copier.copyFile(
                concatPath(GS_HOME, "jboss/mail-service.xml"),
                deployDir);
        copier.copyFile(concatPath(GS_HOME, "jboss/uil2-service.xml"),
                deployDir + FORWARDSLASH + "jms");

        copier.copyFile(concatPath(GS_HOME, "jboss/ear-deployer.xml"),
                deployDir);

        copier.copyFile(concatPath(GS_HOME, "jboss/server.xml"),
                jboss_tomcat_sar_dir);
    }

    // public void createXDEKey() throws Exception
    // {
    // String hostnames = getInstallValue("spellcheck_server");
    //
    // StringBuffer keys = new StringBuffer(
    // "//xde.net,//mirrordns.com,//10.1.1.5");
    //
    // StringTokenizer toks = new StringTokenizer(hostnames, ",");
    // while (toks.hasMoreTokens())
    // {
    // String tok = toks.nextToken().trim();
    //
    // if (tok.length() > 0)
    // {
    // keys.append(",//");
    // keys.append(tok);
    // }
    // }
    //
    // MakeKey makekey = new MakeKey();
    // makekey.setProductName("SpellCheckerv4");
    // makekey.setCustomerName("XDE");
    // makekey
    // .setSpecialCount("//localhost,//127.0.0.1,//xde.net,//mirrordns.com,"
    // + keys);
    // // This writes to stdout:
    // // The original key, the encoded key, and the decoded key again.
    // String systemjar = makekey.start();
    //
    // // copy the resulting system.jar to XDE's lib directory
    // RecursiveCopy copier = new RecursiveCopy();
    // copier.copyFile(systemjar, DEPLOYMENT_DIRECTORY
    // + "/globalsight.ear/xdespellchecker.war/WEB-INF/lib");
    //
    // File f = new File(systemjar);
    // f.delete();
    // }

    public void processFiles() throws Exception
    {
        // createXDEKey();

        System.out.println("Copying saaj.jar into <java_home>/jre/lib/ext.");
        String saajLibJar = concatPath(INSTALLATION_DATA_DIRECTORY,
                "axis/saaj.jar");
        String jreLibExtDir = concatPath(getInstallValue("java_home"),
                "jre/lib/ext");
        RecursiveCopy copier = new RecursiveCopy();
        copier.copyFile(saajLibJar, jreLibExtDir);

        if (m_configFileList == null)
        {
            initializeConfigurationFileList();
        }
        
        boolean enableSSL = prehandleServerSSL();
        
        prehandleMailServer();

        for (Enumeration<String> e = m_configFileList.keys(); e
                .hasMoreElements();)
        {
            String source = e.nextElement().toString();
            String destination = m_configFileList.get(source).toString();
            processFile(source, destination);
        }
        copyFilesToJbossHome();
        
        if (enableSSL)
        {
            processSsl();
        }
    }

    private boolean prehandleMailServer()
    {
        boolean useSSLMail = "true".equalsIgnoreCase(m_installValues.getProperty("mailserver_use_ssl", "false"));
        if (useSSLMail)
            m_installValues.setProperty("mail_transport_protocol", "smtps");
        else
            m_installValues.setProperty("mail_transport_protocol", "smtp");
        
        String pop3Server = "";
        String mailAddress = m_installValues.getProperty("admin_email");
        if (mailAddress != null && mailAddress.contains("@"))
            pop3Server = "pop3." + mailAddress.substring(mailAddress.indexOf("@") + 1); 
        else
            pop3Server = m_installValues.getProperty("mailserver");
        m_installValues.setProperty("mailserver_pop3", pop3Server);
        
        return useSSLMail;
    }

    private boolean prehandleServerSSL()
    {
        boolean enableSSL = "true".equalsIgnoreCase(m_installValues.getProperty("server_ssl_enable", "false"));
        
        if (enableSSL)
        {
            m_installValues.setProperty("ssl_comments_end", "-->");
            m_installValues.setProperty("ssl_comments_start", "<!--");
            String kspwd = m_installValues.getProperty("server_ssl_ks_pwd");
            if (kspwd == null || "".equals(kspwd.trim()))
                m_installValues.setProperty("server_ssl_ks_pwd", "changeit");
        }
        else
        {
            m_installValues.setProperty("ssl_comments_end", "");
            m_installValues.setProperty("ssl_comments_start", "");
            m_installValues.setProperty("server_ssl_port", "443");
            m_installValues.setProperty("server_ssl_ks_pwd", "changeit");
        }
        return enableSSL;
    }
    
    public void processSsl() throws Exception
    {
        String keystoreDir = concatPath(GS_HOME, "jboss/jboss_server/server/default/conf");
        String keystoreFileName = "globalsight.keystore";
        String jksFile = m_installValues.getProperty("server_ssl_ks_path");
        File ksfile = new File(jksFile);
        String newks = jksFile;
        if (jksFile == null || "".equals(jksFile.trim()) || !ksfile.isFile())
        {
            newks = concatPath(GS_HOME, "jboss/jboss_server/server/default/conf/globalsight_ori.keystore");
        }
        
        RecursiveCopy copier = new RecursiveCopy();
        copier.copyFile(newks, keystoreDir, keystoreFileName);
    }

    private void processFile(String sourceFileStr, String destFileStr)
            throws IOException
    {
        processFile(sourceFileStr, destFileStr, m_installValues);
    }

    // replace instances of "%%key%%" with "value" in the source file,
    // and save it to the destination file. The keys and values are
    // from the specified properties object.
    public void processFile(String sourceFileStr, String destFileStr,
            Properties p_properties) throws IOException
    {
        File sourceFile = new File(sourceFileStr);
        File destFile = new File(destFileStr);

        System.out.print("\nProcessing " + sourceFile.getName() + "...");
        fireActionEvent(sourceFile.getName());

        destFile.getParentFile().mkdirs();
        destFile.createNewFile();

        try
        {
            InputStream inputstream = getResource(sourceFileStr);
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    inputstream));
            BufferedWriter out = new BufferedWriter(new FileWriter(destFile));

            String str, newstr;

            while ((str = in.readLine()) != null)
            {
                if (str.startsWith("#")) // It's a comment
                {
                    newstr = str;
                }
                else
                {
                    newstr = str;

                    // deal with the case of "log4j.proterties.template"
                    if (str.indexOf("%%Jboss_JNDI_prefix%%") != -1) // has match
                    {
                        newstr = replace(str, "%%Jboss_JNDI_prefix%%", "topic/");
                    }
                    // deal with the case of "log4j.proterties.template"
                    else if (str.indexOf("%%ldap_user_password%%") != -1) // has
                    // match
                    {
                        newstr = replace(str, "%%ldap_user_password%%",
                                encodeMD5(p_properties
                                        .getProperty("ldap_password")));
                    }
                    else if (str.indexOf("%%super_admin_password%%") != -1) // has
                    // match
                    {
                        newstr = replace(str, "%%super_admin_password%%",
                                encodeMD5(p_properties
                                        .getProperty("system4_admin_password")));
                    }
                    else
                    {
                        // Iterate over the array to see if the string matches
                        // *any* of the install keys
                        for (Enumeration<?> e = p_properties.propertyNames(); e
                                .hasMoreElements();)
                        {
                            String key = (String) e.nextElement();
                            String pattern = "%%" + key + "%%";
                            Object replaceObj = p_properties.get(key);
                            String replace = replaceObj.toString();

                            if (str.indexOf(pattern) == -1) // no match
                            {
                                continue;
                            }

                            newstr = replace(str, pattern,
                                    replacePathSlash(replace));
                            str = newstr;
                        }
                    }
                }

                out.write(newstr);
                out.newLine();
            }

            in.close();
            out.close();
        }
        catch (IOException e)
        {
            System.out.println("Error processing file.");
            throw e;
        }

        System.out.println("done.");
    }

    // Replacing Substrings in a String
    private String replace(String str, String pattern, String replace)
    {
        int s = 0;
        int e = 0;
        StringBuffer result = new StringBuffer();

        while ((e = str.indexOf(pattern, s)) >= 0)
        {
            result.append(str.substring(s, e));
            result.append(replace);
            s = e + pattern.length();
        }

        result.append(str.substring(s));

        return result.toString();
    }

    private String replacePathSlash(String str)
    {
        if (str.startsWith(BACKSLASH))
        {
            return (DOUBLEBACKSLASH + str.substring(2).replaceAll(
                    DOUBLEBACKSLASH, FORWARDSLASH)).replaceAll(
                    DOUBLEBACKSLASH_REG, DOUBLEBACKSLASH_REP);
        }
        else
        {
            return str.replaceAll(DOUBLEBACKSLASH, FORWARDSLASH);
        }
    }

    public int countCreateDatabaseCommands()
    {
        return 30;
    }

    public void initOpenLDAP() throws IOException
    {
        if (m_operatingSystem == OS_LINUX)
        {
            // This operation does not support Linux yet.
            return;
        }

        String ldapInstallDir = m_installValues.getProperty("ldap_install_dir");
        if (ldapInstallDir.startsWith("\\\\"))
        {
            System.out
                    .println("OpenLDAP is not installed in the local machine.");
            return;
        }

        String event = "Initializing OpenLDAP data.";
        System.out.print("\n" + event);

        fireActionEvent(event);

        // if (m_operatingSystem == OS_LINUX)
        // {
        // execute(new String[] { "sh",
        // "./JavaServiceWrapper/bin/initOpenLDAP.sh", ldapInstallDir });
        // }
        // else
        if (m_operatingSystem == OS_WINDOWS)
        {
            ldapInstallDir = '"' + ldapInstallDir.replace('/', '\\') + '"';
            String[] cmds = {
                    concatPath(JAVA_SERVICE_WRAPPER_HOME, "initOpenLDAP.bat"),
                    ldapInstallDir };
            executeNoOutputNoError(cmds);
        }
        else
        {
            System.out.println("Your OS is not supported.");
        }

        System.out.println("done.");
    }

    public void createDatabaseTables() throws IOException
    {
        System.out.println("\nDropping GlobalSight tables.");

        for (int i = 0, length = cleanSqlFiles.length; i < length; i++)
        {
            importSqlFile(concatPath(INSTALLATION_DATA_DIRECTORY, "/mysql/"
                    + cleanSqlFiles[i]));
        }

        System.out.println("\nCreating GlobalSight tables.");

        for (int i = 0, length = creationSqlFiles.length; i < length; i++)
        {
            importSqlFile(concatPath(INSTALLATION_DATA_DIRECTORY, "/mysql/"
                    + creationSqlFiles[i]));
        }
        System.out.println("\nCreating GlobalSight views.");
        importSqlFile(concatPath(INSTALLATION_DATA_DIRECTORY,
                "/mysql/create_views_mysql.sql"));

        initOpenLDAP();
    }

    public int countUpdateDatabaseCommands()
    {
        return 450;
    }

    public void updateDatabaseTables() throws IOException
    {
        System.out.println("\nUpdating SYSTEM_PARAMETER table.");
        importSqlFile(concatPath(INSTALLATION_DATA_DIRECTORY,
                "/mysql/insert_system_parameters_mysql.sql"));

        System.out.println("\nUpdating EXPORT_LOCATION table.");
        importSqlFile(concatPath(INSTALLATION_DATA_DIRECTORY,
                "/mysql/update_exportlocation_mysql.sql"));
    }

    /**
     * Runs the SQL script against the db as the globalsight user <br>
     * 
     * @param sqlfile --
     *            the script to run
     */
    private void importSqlFile(String sqlfile) throws IOException
    {
        // Make a file just so we can append "quit;" to it
        File sqltemp = new File(sqlfile);

        System.out.println("\nProcessing " + sqlfile + "...");
        fireActionEvent(sqlfile);

        if (m_operatingSystem == OS_LINUX)
        {
            String[] sqlStmt = { "sh", "./data/linux/importSqlFile.sh",
                    getInstallValue("database_server"),
                    getInstallValue("database_port"),
                    getInstallValue("database_username"),
                    getInstallValue("database_password"),
                    getInstallValue("database_instance_name"),
                    sqltemp.getAbsolutePath() };

            execute(sqlStmt);
        }
        else
        {
            String commandName = "cmd";

            String[] sqlStmt = { commandName, "/c", "mysql",
                    "-h", getInstallValue("database_server"),
                    "-P", getInstallValue("database_port"),
                    "-u", getInstallValue("database_username"),
                    // This one is different: "-p [password]" waits for
                    // stdin
                    "-p" + getInstallValue("database_password"),
                    "-D", getInstallValue("database_instance_name"),
                    "<", "\"" + sqltemp.getAbsolutePath() + "\""};

            execute(sqlStmt);
        }

        System.out.println("done.");
    }

    // executes the command and spits output to stdout
    private void execute(String[] command) throws IOException
    {
        Process p = Runtime.getRuntime().exec(command);
        printExecutionOutput(p, true, true, true);
    }

    private void executeNoUIOutput(String[] command) throws IOException
    {
        Process p = Runtime.getRuntime().exec(command);
        printExecutionOutput(p, true, true, false);
    }

    // executes the command and spits output to stdout
    private void execute(String command) throws IOException
    {
        Process p = Runtime.getRuntime().exec(command);
        printExecutionOutput(p, true, true, true);
    }

    // executes the command and hides output, but does display "." and ":"

    private void executeNoOutputNoError(String command) throws IOException
    {
        Process p = Runtime.getRuntime().exec(command);
        printExecutionOutput(p, false, false, false);
    }

    private void executeNoOutputNoError(String[] command) throws IOException
    {
        Process p = Runtime.getRuntime().exec(command);
        printExecutionOutput(p, false, false, false);
    }

    private void printExecutionOutput(Process p, boolean showCmdOutput,
            boolean showError, boolean showUIOutput) throws IOException
    {
        try
        {
            BufferedReader in = new BufferedReader(new InputStreamReader(p
                    .getInputStream()));

            BufferedReader sin = new BufferedReader(new InputStreamReader(p
                    .getErrorStream()));

            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(p
                    .getOutputStream()));

            String line;
            int linenum = 0;

            // note for future modification:
            // handling stdout and stderr this way might block...this should
            // be handled with separate threads to process stdout and stderr

            while ((line = in.readLine()) != null)
            {
                linenum++;

                if (showCmdOutput)
                {
                    System.out.println(line);
                }
                else if (showUIOutput)
                {
                    fireActionEvent(line);
                }
                else
                {
                    if (linenum % 10 == 0)
                    {
                        if (linenum % 100 == 0)
                        {
                            System.out.print(":");
                        }
                        else
                        {
                            System.out.print(".");
                        }
                    }
                }
            }

            // print out all the stderr that happened
            while ((line = sin.readLine()) != null)
            {
                if (showError)
                {
                    System.out.println(line);
                }
            }

            in.close();
            sin.close();
            out.close();

        }
        catch (IOException e)
        {
            System.out.println("Error executing system command.");
            throw e;
        }
    }

    // Utility function to read a line from standard input
    // if no output is provided, the default is returned
    // if 'q' is hit, it will exit
    private String readEntry(String prompt, String p_default)
            throws IOException
    {
        System.out.print(prompt + "[" + p_default + "]: ");
        System.out.flush();
        BufferedReader input = new BufferedReader(new InputStreamReader(
                System.in));
        String userInput = input.readLine();
        String entry = null;

        if (userInput == null || userInput.length() == 0)
        {
            entry = p_default;
        }
        else
        {
            if (userInput.equalsIgnoreCase("q"))
            {
                storeUserInput();
                System.exit(0);
            }

            entry = userInput;
        }

        return entry;
    }

    // modify the install values hash tables with versions of some values
    public void addAdditionalInstallValues()
    {
        // Make some special parameters for certain files.

        // set up the main log directory
        StringBuffer logDir = new StringBuffer(GS_HOME);
        logDir.append(File.separator);
        logDir.append("logs");
        File logDirFile = new File(logDir.toString());

        // make the log directory
        if (logDirFile.exists() == false)
        {
            logDirFile.mkdirs();
        }

        m_installValues.put("system_log_directory", logDir.toString());

        String logDirForwardSlash = replace(logDir.toString(), BACKSLASH,
                FORWARDSLASH);
        m_installValues.put("system_log_directory_forwardslash",
                logDirForwardSlash);

        // java_home - add forwardslash variable
        String java_home = getInstallValue("java_home");
        String java_home_forwardslash = replace(java_home, BACKSLASH,
                FORWARDSLASH);
        m_installValues.put("java_home_forwardslash", java_home_forwardslash);

        // msoffice_dir_forwardslash
        String msoffice_dir = getInstallValue("msoffice_dir");
        String msoffice_dir_forwardslash = replace(msoffice_dir, BACKSLASH,
                FORWARDSLASH);
        m_installValues.put("msoffice_dir_forwardslash",
                msoffice_dir_forwardslash);

        // file_storage_dir_forwardslash (secondary target file
        String file_storage_dir = getInstallValue("file_storage_dir");
        String file_storage_dir_forwardslash = replace(file_storage_dir,
                BACKSLASH, FORWARDSLASH);
        m_installValues.put("file_storage_dir_forwardslash",
                file_storage_dir_forwardslash);

        // gs_home_forwardslash
        String gs_home_forwardslash = replace(GS_HOME, BACKSLASH, FORWARDSLASH);
        m_installValues.put("gs_home_forwardslash", gs_home_forwardslash);
        m_installValues.put("gs_home", GS_HOME);

        // change gs_ear_root
        String earRoot = (m_operatingSystem == OS_WINDOWS ? "\\jboss\\jboss_server\\server\\default\\deploy\\globalsight.ear"
                : "/jboss/jboss_server/server/default/deploy/globalsight.ear");
        String gs_ear_root = GS_HOME + earRoot;
        m_installValues.put("gs_ear_root", gs_ear_root);

        String gs_ear_root_forwardslash = replace(gs_ear_root, BACKSLASH,
                FORWARDSLASH);
        m_installValues.put("gs_ear_root_forwardslash",
                gs_ear_root_forwardslash);

        String classpath_separator = ";";
        if (m_operatingSystem == OS_WINDOWS)
        {
            classpath_separator = ";";
        }
        else
        {
            classpath_separator = ":";
        }
        m_installValues.put("classpath_separator", classpath_separator);

        // add cxe_docsDir_forwardslash - for config.xml, web.xml
        String cxe_docsDir = getInstallValue("cxe_docsDir");
        String cxe_docsDir_forwardslash = replace(cxe_docsDir, BACKSLASH,
                FORWARDSLASH);
        m_installValues.put("cxe_docsDir_forwardslash",
                cxe_docsDir_forwardslash);

        // figure out what the CAP login Url should be for users
        StringBuffer baseUrl = new StringBuffer();
        String cap_login_url = "";
        String serverHost = getInstallValue(SERVER_HOST);
        if ("localhost".equals(serverHost)) {
        	try
			{
				serverHost = InetAddress.getLocalHost().getHostAddress();
			}
			catch (UnknownHostException e)
			{
			}
        }
        // String proxyServer = getInstallValue("proxy_server_name");
        // String useProxy = getInstallValue("use_proxy_server").toLowerCase();
        // String enableProxySSL = getInstallValue("enable_proxy_ssl")
        // .toLowerCase();
        // String inetsoftReportServlet = null;
        // String inetsoftReportsPort = null;
        //
        // if (useProxy.equals("true"))
        // {
        // if (enableProxySSL.equals("true"))
        // {
        // baseUrl.append("https://");
        // }
        // else
        // {
        // baseUrl.append("http://");
        // }
        //
        // baseUrl.append(proxyServer).append(":");
        // baseUrl.append(getInstallValue("proxy_server_port"));
        // inetsoftReportsPort = getInstallValue("proxy_server_port");
        // }
        // else
        // {
        baseUrl.append("http://").append(serverHost).append(":");
        baseUrl.append(getInstallValue("server_port"));
        // inetsoftReportsPort = getInstallValue("server_port");
        // }

        // cap_login_url = getInstallValue("cap_login_url");
        // if (cap_login_url == null || "".equals(cap_login_url)
        // || "default".equalsIgnoreCase(cap_login_url))
        // {
        cap_login_url = baseUrl.toString() + "/globalsight";
        m_installValues.put("cap_login_url", cap_login_url);
        
        boolean enableSSL = "true".equalsIgnoreCase(getInstallValue("server_ssl_enable"));
        String cap_login_url_ssl = "";
        if (enableSSL)
        {
            String sslPort = getInstallValue("server_ssl_port");
            StringBuffer sslUrl = new StringBuffer("https://");
            sslUrl.append(serverHost);

            if (!"443".endsWith(sslPort))
            {
                sslUrl.append(":").append(sslPort);
            }

            sslUrl.append("/globalsight");
            cap_login_url_ssl = sslUrl.toString();
        }
        m_installValues.put("cap_login_url_ssl", cap_login_url_ssl);
        
        // }
        // inetsoftReportServlet = baseUrl.toString() + "/globalsight/Reports";

        // m_installValues.put("inetsoftReportServlet", inetsoftReportServlet);
        //
        // m_installValues.put("inetsoftReportsPort", inetsoftReportsPort);

        // Add the installation data dir to the hashtable
        StringBuffer install_data_dir = new StringBuffer(GS_HOME);
        install_data_dir.append(File.separator);
        install_data_dir.append("install");
        install_data_dir.append(File.separator);
        install_data_dir.append(INSTALLATION_DATA_DIRECTORY);
        String install_data_dir_forwardslash = replace(install_data_dir
                .toString(), BACKSLASH, FORWARDSLASH);
        m_installValues.put("install_data_dir_forwardslash",
                install_data_dir_forwardslash);

        m_installValues.put("canoncial_mysql_path", MYSQL_SQL_FILE);
        m_installValues.put("ear_home", EAR_HOME);
        m_installValues.put("GS_HOME", GS_HOME);
    }

    public boolean determineOperatingSystem()
    {
        boolean goodOs = true;

        String os = System.getProperty("os.name");
        // if (os.equals("SunOS"))
        // {
        // m_operatingSystem = OS_SOLARIS;
        // // System.out.println("Determined operating system to be Solaris.");
        // }
        // else if (os.equals("HP-UX"))
        // {
        // m_operatingSystem = OS_HPUX;
        // // System.out.println("Determined operating system to be HP-UX.");
        // }
        // else
        if (os.startsWith("Win"))
        {
            m_operatingSystem = OS_WINDOWS;
            // System.out.println("Determined operating system to be Windows ("
            // + os + ").");
        }
        else if (os.startsWith("Linux"))
        {
            m_operatingSystem = OS_LINUX;
            // System.out.println("Determined operating system to be Windows ("
            // + os + ").");
        }
        else
        {
            goodOs = false;
            System.out.println("Unsupported OS: " + os);
        }

        return goodOs;
    }

    public static String concatPath(String parent, String child)
    {
        File path = new File(parent, child);
        return path.getPath();
    }

    public void createStartMenu()
    {
        if (m_operatingSystem != OS_WINDOWS)
        {
            // Create Start Menu is only available for Windows.
            return;
        }

        String event = "Creating Start Menu...";
        System.out.print("\n" + event);
        fireActionEvent(event);

        String cmdPath = concatPath(JAVA_SERVICE_WRAPPER_HOME,
                "CreateStartMenu.cmd");

        try
        {
            Runtime.getRuntime().exec(cmdPath);
            System.out.println("done.");
        }
        catch (IOException ioe)
        {
            ioe.printStackTrace();
        }
    }

    // installs "GlobalSight" as an NT service
    public void installGlobalSightService() throws IOException
    {
        if (m_operatingSystem == OS_WINDOWS)
        {
            System.out.println("\nRemoving NT service "
                    + "\"GlobalSight Service\" if it is installed.");
            String uninstallCommand = concatPath(JAVA_SERVICE_WRAPPER_HOME,
                    "UninstallApp-NT.cmd");
            executeNoOutputNoError(uninstallCommand);

            System.out.println("\nAdding NT service "
                    + "\"GlobalSight Service\"");
            String installCommand = concatPath(JAVA_SERVICE_WRAPPER_HOME,
                    "InstallApp-NT.cmd");

            execute(installCommand);
        }
        else if (m_operatingSystem == OS_LINUX)
        {
            String initFilePath = concatPath(JBOSS_BIN, "jboss_init_unix.sh");
            String jbossHome = JBOSS_HOME.replace("/", "\\/");
            String[] sqlStmt = { "sh", "./data/linux/replace.sh",
                    "%%JBOSS_HOME%%", jbossHome, initFilePath };

            execute(sqlStmt);

            String installCommand = concatPath(JAVA_SERVICE_WRAPPER_HOME,
                    "InstallApp-NT.sh");
            String[] sqlStmt2 = { "sh", installCommand, initFilePath,
                    RESOURCE.getString("service_name") };
            execute(sqlStmt2);
        }
    }

    /***************************************************************************
     * Makes the EAR file for deployment to WebSphere since exploded format
     * cannot be used with WebSphere *
     **************************************************************************/
    public void makeWebSphereEAR() throws Exception
    {
        System.out.println("Creating .ear file for WebSphere");
        String earfile = "makeear.bat";
        if (m_operatingSystem != OS_WINDOWS) earfile = "makeear.sh";
        String earcmd = concatPath(GS_HOME + File.separator + "websphere",
                earfile);
        execute(earcmd);
    }

    /***************************************************************************
     * Sets up the embeddeded JMS for WebSphere. Runs the JACL scripts using
     * wsadmin. *
     **************************************************************************/
    public void setupWebSphereJMS() throws IOException
    {
        System.out.println("Setting up JMS for WebSphere");
        String was_home = getInstallValue("websphere_home");
        String jaclscript = concatPath(GS_HOME + File.separator + "websphere",
                "setupJMS.jacl");
        String wsadmin = "wsadmin.bat";

        if (m_operatingSystem != OS_WINDOWS)
        {
            was_home = getInstallValue("websphere_home_forwardslash");
            String gs_home_forwardslash = getInstallValue("gs_home_forwardslash");
            jaclscript = concatPath(gs_home_forwardslash + File.separator
                    + "websphere", "setupJMS.jacl");
            wsadmin = "wsadmin";
        }

        String jaclcmd = concatPath(was_home + File.separator + "bin", wsadmin
                + " -f " + jaclscript);
        execute(jaclcmd);
    }

    private static String determineGsHome()
    {
        try
        {
            File f = new File("../");
            String gsh = f.getCanonicalPath();
            return gsh;
        }
        catch (IOException e)
        {
            throw new IllegalStateException(
                    "Cannot determine current working directory."
                            + e.getMessage());
        }
    }

    private static String encodeMD5(String msg)
    {
        try
        {
            byte[] md5Msg = MessageDigest.getInstance("MD5").digest(
                    msg.getBytes());
            return "{MD5}" + new String(new Base64().encode(md5Msg));
        }
        catch (NoSuchAlgorithmException e)
        {
            System.err.println("Can not find the MD5 ALGORITHM.");
            return null;
        }
    }

}