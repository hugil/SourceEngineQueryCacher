package com.aayushatharva.sourcecenginequerycacher;

import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Properties;

public class Config {

    private static final Logger logger = LogManager.getLogger(Config.class);
    private static final Options options;
    /**
     * Cacher Server Transport Type
     */
    public static String Transport = "Nio";
    /**
     * Cacher Server Threads
     */
    public static Integer Threads = 2;
    /**
     * Game Update Interval
     */
    public static Long GameUpdateInterval = 1000L;
    /**
     * Maximum Challenge Code in Cache
     */
    public static Long MaxChallengeCode = 100000L;
    /**
     * Challenge Code Cache Cleaner Interval
     */
    public static Long ChallengeCodeCacheCleanerInterval = 1000L;
    /**
     * Challenge Code Validity
     */
    public static Long ChallengeCodeTTL = 5000L;
    /**
     * Challenge Code Cache Concurrency
     */
    public static int ChallengeCacheConcurrency = 8;
    // IP Addresses and Ports
    public static InetAddress IPAddress = InetAddress.getLoopbackAddress();
    public static Integer Port = 27016;
    public static InetAddress GameServerIPAddress = InetAddress.getLoopbackAddress();
    public static Integer GameServerPort = 27015;
    // Buffers
    public static Integer ReceiveBufferSize = 65535;
    public static Integer SendBufferSize = 65535;
    public static Integer FixedReceiveAllocatorBufferSize = 65535;
    // Stats
    public static boolean Stats_PPS = false;
    public static boolean Stats_BPS = false;

    static {
        options = new Options()
                /*General Configuration*/
                .addOption("h", "help", true, "Display Usages")
                .addOption("c", "config", true, "Configuration File Path")
                .addOption("t", "transport", true, "Set Transport to be used [Epoll or Nio]")
                .addOption("w", "threads", true, "Number of Threads")
                .addOption("u", "update", true, "Game Server Update rate in Milliseconds")
                .addOption("p", "ppsStats", false, "Enable Packets per Second Stats")
                .addOption("b", "bpsStats", false, "Enable Bits per Second Stats")
                .addOption("maxChallengeCode", true, "Maximum Challenge Codes to be saved")
                .addOption("challengeCodeCacheCleaner", true, "Challenge Code Cache Cleaner Interval in Milliseconds")
                .addOption("challengeCodeTTL", true, "Maximum Validity of Challenge Code in Milliseconds")
                .addOption("challengeCodeCacheConcurrency", true, "Challenge Code Cache Concurrency")

                /*IP Addresses and Ports*/
                .addOption("gameip", true, "Game Server IP Address")
                .addOption("gameport", true, "Game Server Port")
                .addOption("bind", true, "IP Address on which Cacher Server will bind and listen")
                .addOption("port", true, "Port on which Cacher Server will bind and listen")

                /*Buffers*/
                .addOption("r", "receiveBuf", true, "Server Receive Buffer Size")
                .addOption("s", "sendBuf", true, "Server Send Buffer Size")
                .addOption("a", "receiveAllocatorBuf", true, "Fixed Receive ByteBuf Allocator Buffer Size");
    }

    public static void setup(String[] args) throws ParseException, IOException {

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        if (cmd.getOptionValue("help") != null) {
            displayHelpAndExit();
        }

        /*
         * If `config` Parameter is present, parse the config file and load configuration.
         */
        if (cmd.getOptionValue("config") != null) {
            logger.atInfo().log("Configuration Path: " + cmd.getOptionValue("config"));

            // Parse Config File
            parseConfigFile(cmd.getOptionValue("config"));
        } else {

            if (cmd.getOptionValue("transport") != null) {
                Transport = cmd.getOptionValue("transport");
            }

            if (cmd.getOptionValue("threads") != null) {
                Threads = Integer.parseInt(cmd.getOptionValue("threads"));
            }

            if (cmd.getOptionValue("update") != null) {
                GameUpdateInterval = Long.parseLong(cmd.getOptionValue("update"));
            }

            if (cmd.hasOption("ppsStats")) {
                Stats_PPS = true;
            }

            if (cmd.hasOption("bpsStats")) {
                Stats_BPS = true;
            }

            if (cmd.getOptionValue("maxChallengeCode") != null) {
                MaxChallengeCode = Long.parseLong(cmd.getOptionValue("maxChallengeCode"));
            }

            if (cmd.getOptionValue("challengeCodeCacheCleaner") != null) {
                ChallengeCodeCacheCleanerInterval = Long.parseLong(cmd.getOptionValue("challengeCacheCleaner"));
            }

            if (cmd.getOptionValue("challengeCodeTTL") != null) {
                ChallengeCodeTTL = Long.parseLong(cmd.getOptionValue("challengeCodeTTL"));
            }

            if (cmd.getOptionValue("challengeCodeCacheConcurrency") != null) {
                ChallengeCodeCacheCleanerInterval = Long.parseLong(cmd.getOptionValue("challengeCodeCacheConcurrency"));
            }

            if (cmd.getOptionValue("gameip") != null) {
                GameServerIPAddress = InetAddress.getByName(cmd.getOptionValue("gameip"));
            }

            if (cmd.getOptionValue("gameport") != null) {
                GameServerPort = Integer.parseInt(cmd.getOptionValue("gameport"));
            }

            if (cmd.getOptionValue("bind") != null) {
                IPAddress = InetAddress.getByName(cmd.getOptionValue("bind"));
            }

            if (cmd.getOptionValue("port") != null) {
                Port = Integer.parseInt(cmd.getOptionValue("port"));
            }

            if (cmd.getOptionValue("receiveBuf") != null) {
                ReceiveBufferSize = Integer.parseInt(cmd.getOptionValue("receiveBuf"));
            }

            if (cmd.getOptionValue("sendBuf") != null) {
                SendBufferSize = Integer.parseInt(cmd.getOptionValue("sendBuf"));
            }

            if (cmd.getOptionValue("receiveAllocatorBuf") != null) {
                FixedReceiveAllocatorBufferSize = Integer.parseInt(cmd.getOptionValue("receiveAllocatorBuf"));
            }
        }

        if (logger.isDebugEnabled()) {
            displayConfig();
        }
    }

    private static void parseConfigFile(String path) throws IOException {
        Properties Data = new Properties();
        Data.load(new FileInputStream(path));

        // Load all Data
        Transport = Data.getProperty("Transport", Transport);
        Threads = Integer.parseInt(Data.getProperty("Threads", String.valueOf(Threads)));
        GameUpdateInterval = Long.parseLong(Data.getProperty("GameUpdateInterval", String.valueOf(GameUpdateInterval)));
        Stats_PPS = Boolean.parseBoolean(Data.getProperty("StatsPPS", String.valueOf(Stats_PPS)));
        Stats_BPS = Boolean.parseBoolean(Data.getProperty("StatsBPS", String.valueOf(Stats_PPS)));
        MaxChallengeCode = Long.parseLong(Data.getProperty("MaxChallengeCode", String.valueOf(MaxChallengeCode)));
        ChallengeCodeCacheCleanerInterval = Long.parseLong(Data.getProperty("ChallengeCodeCacheCleanerInterval", String.valueOf(ChallengeCodeCacheCleanerInterval)));
        ChallengeCacheConcurrency = Integer.parseInt(Data.getProperty("ChallengeCacheConcurrency", String.valueOf(ChallengeCacheConcurrency)));

        IPAddress = InetAddress.getByName(Data.getProperty("IPAddress", IPAddress.getHostAddress()));
        Port = Integer.parseInt(Data.getProperty("Port", String.valueOf(Port)));
        GameServerIPAddress = InetAddress.getByName(Data.getProperty("GameServerIPAddress", GameServerIPAddress.getHostAddress()));
        GameServerPort = Integer.parseInt(Data.getProperty("GameServerPort", String.valueOf(GameServerPort)));

        ReceiveBufferSize = Integer.parseInt(Data.getProperty("ReceiveBufferSize", String.valueOf(ReceiveBufferSize)));
        SendBufferSize = Integer.parseInt(Data.getProperty("SendBufferSize", String.valueOf(SendBufferSize)));
        FixedReceiveAllocatorBufferSize = Integer.parseInt(Data.getProperty("FixedReceiveAllocatorBufferSize", String.valueOf(FixedReceiveAllocatorBufferSize)));

        Data.clear(); // Clear Properties
    }

    private static void displayConfig() {
        logger.atDebug().log("Transport: " + Transport);
        logger.atDebug().log("Threads: " + Threads);
        logger.atDebug().log("GameUpdateInterval: " + GameUpdateInterval);
        logger.atDebug().log("MaxChallengeCode: " + MaxChallengeCode);
        logger.atDebug().log("ChallengeCodeCacheCleanerInterval: " + ChallengeCodeCacheCleanerInterval);
        logger.atDebug().log("ChallengeCacheConcurrency: " + ChallengeCacheConcurrency);
        logger.atDebug().log("IPAddress: " + IPAddress.getHostAddress());
        logger.atDebug().log("Port: " + Port);
        logger.atDebug().log("GameServerIPAddress: " + GameServerIPAddress.getHostAddress());
        logger.atDebug().log("GameServerPort: " + GameServerPort);
        logger.atDebug().log("ReceiveBufferSize: " + ReceiveBufferSize);
        logger.atDebug().log("SendBufferSize: " + SendBufferSize);
        logger.atDebug().log("FixedReceiveAllocatorBufferSize: " + FixedReceiveAllocatorBufferSize);
    }

    private static void displayHelpAndExit() {
        HelpFormatter helpFormatter = new HelpFormatter();
        helpFormatter.printHelp("java -jar FILENAME", options);

        System.exit(0);
    }
}
