package dk.statsbiblioteket.doms.transformers.fileobjectcreator;

import dk.statsbiblioteket.doms.central.CentralWebservice;
import dk.statsbiblioteket.doms.transformers.common.DomsWebserviceFactory;
import dk.statsbiblioteket.doms.transformers.common.muxchannels.MuxFileChannelCalculator;
import dk.statsbiblioteket.doms.transformers.fileenricher.FFProbeLocationPropertyBasedDomsConfig;
import jsr166y.ForkJoinPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class FileObjectCreator {
    private static Logger log = LoggerFactory.getLogger(FileObjectCreator.class);
    private static final String baseName = "fileobjectcreator_";
    private static BufferedWriter newUuidWriter;
    private static BufferedWriter existingUuidWriter;
    private static BufferedWriter successWriter;
    private static BufferedWriter failureWriter;
    private static BufferedWriter badFFProbeWriter;
    private static BufferedWriter ignoreWriter;
    private static FFProbeLocationPropertyBasedDomsConfig config;
    private static final int STATUS_CHAR_PR_LINE = 100;
    private static final char SUCCESS_CHAR = '+';
    private static final char EXISTING_CHAR = ',';
    private static final char FAILURE_CHAR = '#';
    private static final char IGNORE_CHAR = '.';
    private static int logCounter = 0;
    private static boolean shutdown = false;

    private static BufferedReader fileListReader = null;

    public static void main(String[] args) {
        File configFile = null;
        switch (args.length) {
            case 1:
                configFile = new File(args[0]);
                System.out.println("Reading data from stdin..");
                fileListReader = new BufferedReader(new InputStreamReader(System.in));
                run(configFile, fileListReader);
                break;
            case 2:
                configFile = new File(args[0]);
                System.out.println("Input file: " + args[1]);
                try {
                    fileListReader = new BufferedReader(new FileReader(new File(args[1])));
                    run(configFile, fileListReader);
                } catch (FileNotFoundException e) {
                    System.err.println("File not found: " + args[1]);
                    System.exit(1);
                }
                break;
            default:
                System.out.println("Usage: bin/fileobjectcreator.sh config-file [input-file]");
                System.exit(1);
        }
    }

    public static void run(File configFile, BufferedReader fileListReader) {
        if (!configFile.exists()) {
            System.out.println("Config file does not exist: " + config);
            System.exit(1);
        }

        if (!configFile.canRead()) {
            System.out.println("Could not read config file: " + config);
            System.exit(1);
        }

        try {
            config = new FFProbeLocationPropertyBasedDomsConfig(configFile);
            System.out.println(config);

            File newUuidLog = new File(baseName + "new-uuids");
            File existingUuidLog = new File(baseName + "existing-uuids");
            File successLog = new File(baseName + "successful-files");
            File failureLog = new File(baseName + "failed-files");
            File badFFProbeLog = new File(baseName + "ffprobe-errors");
            File ignoreLog = new File(baseName + "ignored-files");

            List<File> logFiles = new LinkedList<File>();
            List<BufferedWriter> logWriters = new LinkedList<BufferedWriter>();
            logFiles.add(newUuidLog);
            logFiles.add(existingUuidLog);
            logFiles.add(successLog);
            logFiles.add(failureLog);
            logFiles.add(badFFProbeLog);
            logFiles.add(ignoreLog);

            boolean logsCleared = true;

            for (File f : logFiles) {
                if (f.exists()) {
                    logsCleared = false;
                    System.out.println("File already exists: " + f);
                }
            }

            if (!logsCleared) {
                System.exit(1);
            } else {
                newUuidWriter = new BufferedWriter(new FileWriter(newUuidLog));
                existingUuidWriter = new BufferedWriter(new FileWriter(existingUuidLog));
                successWriter = new BufferedWriter(new FileWriter(successLog));
                failureWriter = new BufferedWriter(new FileWriter(failureLog));
                badFFProbeWriter = new BufferedWriter(new FileWriter(badFFProbeLog));
                ignoreWriter = new BufferedWriter(new FileWriter(ignoreLog));
                logWriters.add(newUuidWriter);
                logWriters.add(existingUuidWriter);
                logWriters.add(successWriter);
                logWriters.add(failureWriter);
                logWriters.add(badFFProbeWriter);
                logWriters.add(ignoreWriter);
            }

            new FileObjectCreator(fileListReader);

        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public FileObjectCreator(BufferedReader reader) {
        try {
            List<String> data = new ArrayList<String>();
            String line;
            while((line = reader.readLine()) != null) {
                data.add(line);
            }

            System.out.println(
                    String.format(
                            "'%c': added file, '%c': ignored file, '%c': existing file, '%c': failed file, %d files/line.",
                            SUCCESS_CHAR,
                            IGNORE_CHAR,
                            EXISTING_CHAR,
                            FAILURE_CHAR,
                            STATUS_CHAR_PR_LINE));

            MuxFileChannelCalculator muxFileChannelCalculator = new MuxFileChannelCalculator(
                    Thread.currentThread().getContextClassLoader().getResourceAsStream("muxChannels.csv"));

            String baseUrl = config.getProperty("dk.statsbiblioteket.doms.transformers.baseurl", "");
            if (baseUrl.isEmpty()) {
                log.warn("Empty base URL.");
            }

            FileObjectCreatorWorker fileObjectCreatorWorker =
                    new FileObjectCreatorWorker(baseUrl, data, muxFileChannelCalculator);

            ForkJoinPool forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors()*2);
            Long start = System.currentTimeMillis();
            forkJoinPool.invoke(fileObjectCreatorWorker);
            Long end = System.currentTimeMillis();
            System.out.println("Time taken: " + (end-start));

        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    public static CentralWebservice newWebservice() {
        try {
            CentralWebservice webservice = new DomsWebserviceFactory(config).getWebservice();
            return webservice;
        } catch (RuntimeException e) {
            System.err.println("Error communication with DOMS. Config: " + config);
            requestShutdown();
        }
        return null;
    }

    public static FFProbeLocationPropertyBasedDomsConfig getConfig() {
        return config;
    }

    public static String getFFProbeDir() {
        return config.getFFprobeFilesLocation();
    }

    public static String getFFProbeFileLocation(String fileName) {
        return getFFProbeDir() + System.getProperty("file.separator") + fileName;
    }

    public static File getFFProbeFile(String fileName) {
        return new File(getFFProbeFileLocation(fileName));
    }

    public static synchronized void logSuccess(String data) {
        try {
            successWriter.write(data + "\n");
            successWriter.flush();
            logChar(SUCCESS_CHAR);
        } catch (Exception e) {
            requestShutdown(e);
        }
    }

    public static synchronized void logFailure(String data) {
        try {
            failureWriter.write(data + "\n");
            failureWriter.flush();
            logChar(FAILURE_CHAR);
        } catch (Exception e) {
            requestShutdown(e);
        }
    }

    public static synchronized void logBadFFProbeData(DomsObject domsObject) {
        try {
            badFFProbeWriter.write(domsObject.formatAsInput() + "\n");
            badFFProbeWriter.flush();
        } catch (Exception e) {
            requestShutdown(e);
        }
    }

    public static synchronized void logIgnored(String data) {
        try {
            ignoreWriter.write(data + "\n");
            ignoreWriter.flush();
            logChar(IGNORE_CHAR);
        } catch (Exception e) {
            requestShutdown(e);
        }
    }

    public static synchronized void logExisting(String data) {
        try {
            existingUuidWriter.write(data + "\n");
            existingUuidWriter.flush();
            logChar(EXISTING_CHAR);
        } catch (Exception e) {
            requestShutdown(e);
        }
    }

    public static synchronized void logNewUuid(String uuid) {
        try {
            newUuidWriter.write(uuid + "\n");
            newUuidWriter.flush();
        } catch (Exception e) {
            requestShutdown(e);
        }
    }

    private static synchronized void logChar(char c) {
        System.out.print(c + incrementLogCounter());
    }

    private static synchronized String incrementLogCounter() {
        logCounter += 1;

        if (logCounter % 100 == 0) {
            return " " + logCounter + System.getProperty("line.separator");
        }

        return "";
    }

    public static boolean permissionToRun() {
        return !shutdown;
    }

    public static void requestShutdown(Throwable throwable) {
        requestShutdown();
        throwable.printStackTrace();
        System.err.println(String.format("Could not write to logfile, shutting down.."));
    }

    public static void requestShutdown() {
        shutdown = true;
        log.info("Shutdown requested.");
    }
}
