package pl.edu.icm.maven.oozie.plugin;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.io.IOUtils;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.springframework.util.PropertyPlaceholderHelper;

public abstract class AbstractIntegrationTestMojo extends AbstractOozieMojo {

    protected List<String> obligatoryProperties = Arrays.asList(
            OoziePluginConstants.HDFS_USER_NAME,
            OoziePluginConstants.NAME_NODE,
            OoziePluginConstants.HDFS_WORKING_DIR_URI,
            OoziePluginConstants.WF_DIR
            );

    protected URI hdfsURI;
    protected String hdfsWorkingDirURI;
    protected String hdfsUserName;
    protected String wfDir;
    protected FileSystem hdfsFS;

    @Override
    public void execute() throws MojoExecutionException {
        super.execute();
        initializeFieldsUsingEnvPropertiesFile();
        createHdfsFS();
    }

    protected void initializeFieldsUsingEnvPropertiesFile()
            throws MojoExecutionException {

        String itEnvPropertiesLocation = OoziePluginConstants.IT_ENV_PROPERTIES_LOCATION.replaceAll(
                Pattern.quote(OoziePluginConstants.IT_ENV_PLACEHOLDER),
                envIT);

        Properties envConf = loadPropertiesFromTestResources(itEnvPropertiesLocation);
        resolvePlaceholders(envConf);
        checkProperties(envConf);

        hdfsUserName = envConf.getProperty(OoziePluginConstants.HDFS_USER_NAME);
        System.setProperty("HADOOP_USER_NAME", hdfsUserName);

        String hdfsURIName = envConf.getProperty(OoziePluginConstants.NAME_NODE);
        if (! hdfsURIName.startsWith("hdfs://")) {
            hdfsURIName = "hdfs://" + hdfsURIName;
        }
        try {
            hdfsURI = new URI(hdfsURIName);
        } catch (URISyntaxException e) {
            throw new MojoExecutionException("Property " + OoziePluginConstants.NAME_NODE + " cannot be parsed.", e);
        }

        hdfsWorkingDirURI = envConf.getProperty(OoziePluginConstants.HDFS_WORKING_DIR_URI);
        wfDir = envConf.getProperty(OoziePluginConstants.WF_DIR);
    }

    private void createHdfsFS() throws MojoExecutionException {
        Configuration hdfsFSconf = new Configuration();
        hdfsFSconf.set("fs.hdfs.impl",
                "org.apache.hadoop.hdfs.DistributedFileSystem");
        try {
            hdfsFS = FileSystem.get(hdfsURI, hdfsFSconf);
        } catch (IOException e) {
            throw new MojoExecutionException("HDFS FileSystem with URI "
                    + hdfsURI.toString() + " failed to be created.", e);
        }
    }

    protected void resolvePlaceholders(Properties properties) {
        String placeholderPrefix = properties.getProperty(
                OoziePluginConstants.PLACEHOLDER_PREFIX_NAME,
                OoziePluginConstants.PLACEHOLDER_PREFIX_DEFAULT);
        String placeholderSuffix = properties.getProperty(
                OoziePluginConstants.PLACEHOLDER_SUFFIX_NAME,
                OoziePluginConstants.PLACEHOLDER_SUFFIX_DEFAULT);
        String placeholderValuesSeparator = properties.getProperty(
                OoziePluginConstants.PLACEHOLDER_VALUE_SEPARATOR_NAME,
                OoziePluginConstants.PLACEHOLDER_VALUE_SEPARATOR_DEFAULT);

        boolean ignoreUnresolvablePlaceholders = Boolean.parseBoolean(properties.getProperty(
                OoziePluginConstants.PLACEHOLDER_IGNORE_UNRESOLVABLE_PLACEHOLDERS_NAME,
                OoziePluginConstants.PLACEHOLDER_IGNORE_UNRESOLVABLE_PLACEHOLDERS_DEFAULT));

        PropertyPlaceholderHelper pph = new PropertyPlaceholderHelper(
                placeholderPrefix, placeholderSuffix,
                placeholderValuesSeparator, ignoreUnresolvablePlaceholders);

        for (String propertyName : properties.stringPropertyNames()) {
            properties.setProperty(
                    propertyName,
                    pph.replacePlaceholders(
                    properties.getProperty(propertyName), properties));
        }
    }

    protected void checkProperties(Properties properties) throws MojoExecutionException {
        for (String property : obligatoryProperties) {
            if (!properties.containsKey(property) || properties.getProperty(property).isEmpty()) {
                throw new MojoExecutionException("Property " + property + " must be set");
            }
        }
    }

    protected Properties loadPropertiesFromTestResources(
            String propertiesFileLocation) throws MojoExecutionException {

        propertiesFileLocation = new File(propertiesFileLocation).getPath();

        List<Resource> testResourcesDirs = mavenProject.getTestResources();
        File propertiesFile = null;

        outerloop: for (Resource testResource : testResourcesDirs) {
            File testResourceDir = new File(testResource.getDirectory());
            if (!testResourceDir.isDirectory()) {
                continue;
            }
            Collection<File> testResourceFiles = FileUtils.listFiles(
                    testResourceDir, null, true);
            for (File testResourceFile : testResourceFiles) {
                if (testResourceFile.getAbsolutePath().endsWith(
                        propertiesFileLocation)) {
                    propertiesFile = testResourceFile;
                    break outerloop;
                }
            }
        }

        if (propertiesFile == null) {
            throw new SkipTestsException("Properties file " + propertiesFileLocation + " does not exist");
        }

        Properties properties = new Properties();
        FileReader fileReader = null;

        try {
            fileReader = new FileReader(propertiesFile);
            properties.load(fileReader);
        } catch (Exception e) {
            throw new MojoExecutionException("Environement properties file "
                    + propertiesFileLocation + " not found in test resources.");
        } finally {
            IOUtils.closeStream(fileReader);
        }
        return properties;
    }
}