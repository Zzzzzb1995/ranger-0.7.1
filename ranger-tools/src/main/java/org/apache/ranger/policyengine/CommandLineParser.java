/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.ranger.policyengine;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;

public class CommandLineParser
{
    static final Log LOG      = LogFactory.getLog(CommandLineParser.class);

    private String servicePoliciesFileName;
    private String[] requestFileNames;
    private String statCollectionFileName;

    private URL servicePoliciesFileURL;
    private URL[] requestFileURLs;
    private URL statCollectionFileURL;


    private int concurrentClientCount = 1;
    private int iterationsCount = 1;

    private boolean isDynamicReorderingDisabled = true;
    private boolean isTrieLookupPrefixDisabled = true;

    private Options options = new Options();

    CommandLineParser() {}

    final PerfTestOptions parse(final String[] args) {
        PerfTestOptions ret = null;
        if (parseArguments(args) && validateInputFiles()) {
            // Instantiate a data-object and return
            ret = new PerfTestOptions(servicePoliciesFileURL, requestFileURLs, statCollectionFileURL, concurrentClientCount, iterationsCount, isDynamicReorderingDisabled, isTrieLookupPrefixDisabled);
        } else {
            showUsage();
        }
        return ret;
    }

    // Parse the arguments

    /* Arguments :
            -s servicePolicies-file-name
            -c concurrent-client-count
            -r request-file-name-list
            -n number-of-iterations
            -p modules-to-collect-stats
            -o

            If the concurrent-client-count is more than the number of files in the request-file-name-list,
            then reuse the request-file-names in a round-robin way

    */

    final boolean parseArguments(final String[] args) {

        if (LOG.isDebugEnabled()) {
            LOG.debug("==> parseArguments()");
        }
        boolean ret = false;

        options.addOption("h", "help", false, "show help.");
        options.addOption("s", "service-policies", true, "Policies File Name");
        options.addOption("r", "requests", true, "Request Definition File Name");
        options.addOption("p", "statistics", true, "Modules for stat collection File Name");
        options.addOption("c", "clients", true, "Number of concurrent clients");
        options.addOption("n", "cycles", true, "Number of iterations");
        options.addOption("o", "optimize", false, "Enable usage-based policy reordering");
        options.addOption("t", "trie-prefilter", false, "Enable trie-prefilter");

        org.apache.commons.cli.CommandLineParser commandLineParser = new DefaultParser();

        try {
            CommandLine commandLine = commandLineParser.parse(options, args);

            if (commandLine.hasOption("h")) {
                showUsage();
                return false;
            }

            servicePoliciesFileName = commandLine.getOptionValue("s");
            requestFileNames = commandLine.getOptionValues("r");
            statCollectionFileName = commandLine.getOptionValue("p");

            concurrentClientCount = 1;
            String clientOptionValue = commandLine.getOptionValue("c");
            if (clientOptionValue != null) {
                concurrentClientCount = Integer.parseInt(clientOptionValue);
            }

            iterationsCount = 1;
            String iterationsOptionValue = commandLine.getOptionValue("n");
            if (iterationsOptionValue != null) {
                iterationsCount = Integer.parseInt(iterationsOptionValue);
            }
            if (commandLine.hasOption("o")) {
                isDynamicReorderingDisabled = false;
            }
            if (commandLine.hasOption("t")) {
                isTrieLookupPrefixDisabled = false;
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("servicePoliciesFileName=" + servicePoliciesFileName + ", requestFileName=" + Arrays.toString(requestFileNames));
                LOG.debug("concurrentClientCount=" + concurrentClientCount + ", iterationsCount=" + iterationsCount);
                LOG.debug("isDynamicReorderingDisabled=" + isDynamicReorderingDisabled);
                LOG.debug("isTrieLookupPrefixDisabled=" + isTrieLookupPrefixDisabled);
            }

            ret = true;
        } catch (Exception exception) {
            LOG.error("Error processing command-line arguments: ", exception);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("<== parseArguments() : " + ret);
        }

        return ret;
    }

    final boolean validateInputFiles() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("==> validateInputFiles()");
        }

        boolean ret = false;

        if (servicePoliciesFileName != null) {
            this.servicePoliciesFileURL = getInputFileURL(servicePoliciesFileName);
            if (servicePoliciesFileURL != null) {
                if (requestFileNames != null) {
                    if (validateRequestFiles()) {
                        if (statCollectionFileName != null) {
                            statCollectionFileURL = getInputFileURL(statCollectionFileName);
                            ret = statCollectionFileURL != null;
                        }  else {
                            ret = true;
                        }
                    }
                } else {
                    LOG.error("Error processing requests file: No requests files provided.");
                }
            } else {
                LOG.error("Error processing service-policies file: unreadable service-policies file: " + servicePoliciesFileName);
            }
        } else {
            LOG.error("Error processing service-policies file: null service-policies file");
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("<== validateInputFiles(): " + ret);
        }

        return ret;
    }

    final boolean validateRequestFiles() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("==> validateRequestFiles()");
        }
        boolean ret = requestFileNames.length > 0;

        if (ret) {
            requestFileURLs = new URL[requestFileNames.length];

            for (int i = 0; ret && i < requestFileNames.length; i++) {
                if (requestFileNames[i] != null) {
                    if ((requestFileURLs[i] = getInputFileURL(requestFileNames[i])) == null) {
                        LOG.error("Cannot read file: " + requestFileNames[i]);
                        ret = false;
                    }
                } else {
                    LOG.error("Error processing request-file: null input file-name for request-file");
                    ret = false;
                }
            }
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("<== validateRequestFiles(): " + ret);
        }
        return ret;
    }

    public static URL getInputFileURL(final String name) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("==> getResourceFileURL(" + name + ")");
        }
        URL ret = null;
        InputStream in = null;


        if (StringUtils.isNotBlank(name)) {

            File f = new File(name);

            if (f.exists() && f.isFile() && f.canRead()) {
                try {

                    in = new FileInputStream(f);
                    ret = f.toURI().toURL();
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("URL:" + ret);
                    }

                } catch (FileNotFoundException exception) {
                    LOG.error("Error processing input file:" + name + " or no privilege for reading file " + name, exception);
                } catch (MalformedURLException malformedException) {
                    LOG.error("Error processing input file:" + name + " cannot be converted to URL " + name, malformedException);
                }
            } else {

                URL fileURL = CommandLineParser.class.getResource(name);
                if (fileURL == null) {
                    if (!name.startsWith("/")) {
                        fileURL = CommandLineParser.class.getResource("/" + name);
                    }
                }

                if (fileURL == null) {
                    fileURL = ClassLoader.getSystemClassLoader().getResource(name);
                    if (fileURL == null) {
                        if (!name.startsWith("/")) {
                            fileURL = ClassLoader.getSystemClassLoader().getResource("/" + name);
                        }
                    }
                }

                if (fileURL != null) {
                    try {
                        in = fileURL.openStream();
                        ret = fileURL;
                    } catch (Exception exception) {
                        LOG.error(name + " cannot be opened:", exception);
                    }
                } else {
                    LOG.warn("Error processing input file: URL not found for " + name + " or no privilege for reading file " + name);
                }
            }
        }
        if (in != null) {
            try {
                in.close();
            } catch (Exception e) {
                // Ignore
            }
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("<== getResourceFileURL(" + name + ", URL=" + ret + ")");
        }
        return ret;
    }

    void showUsage() {
        HelpFormatter formater = new HelpFormatter();
        formater.printHelp("perfTester", options);
    }
}
