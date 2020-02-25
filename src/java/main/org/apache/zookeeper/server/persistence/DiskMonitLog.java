package org.apache.zookeeper.server.persistence;

import org.apache.zookeeper.server.ZooKeeperThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Semaphore;

/**
 * This is used to monitor the disk condition when fsync timeout occurs.
 * Use iostat and iotop commands.
 */
public class DiskMonitLog extends ZooKeeperThread {
    private static final Logger LOG = LoggerFactory.getLogger(DiskMonitLog.class);
    private static final DiskMonitLog INSTANCE = new DiskMonitLog();
    private static final String DEFAULT_CHARSET_NAME = "UTF-8";
    private static final int PRINT_TIME = 30;
    private volatile boolean started = false;
    private boolean diskPrintEnabled = false;
    private static Semaphore semaphore = new Semaphore(0);

    private DiskMonitLog() {
        super("-DiskMonitLog");
        setDiskPrintEnabled();
    }

    public static DiskMonitLog getInstance() { return INSTANCE; }

    public boolean isStarted() {
        return started;
    }

    public void setDiskPrintEnabled() {
        try {
            String value = System.getProperty("zookeeper.diskPrintEnabled");
            if (value == null) {
                diskPrintEnabled = false;
                return;
            }
            if (value.toLowerCase().equals("true")) {
                diskPrintEnabled = true;
            } else if (value.toLowerCase().equals("false")) {
                diskPrintEnabled = false;
            } else {
                LOG.error("Invalid option "
                        + value
                        + " for disk print enabled. Choose 'true' or 'false.'");
                diskPrintEnabled = false;
            }
        } catch (IllegalArgumentException e) {
            // for upgrade zk ,  parameter diskPrintEnabled is empty.
            diskPrintEnabled = false;
        }
    }

    public boolean isDiskPrintEnabled() {
        return diskPrintEnabled;
    }

    @Override
    public synchronized void start() {
        if (diskPrintEnabled) {
            started = true;
            super.start();
        } else {
            LOG.warn("No need to start disk monitor thread.");
        }
    }

    @Override
    public void run() {
        LOG.info("Begin to run disk monitor log thread.");
        while(started) {
            try {
                for (int i=0; i < PRINT_TIME; i++) {
                    logIoStat();
                    logIoTop();
                    Thread.sleep(800);
                }
                semaphore.acquire();
            } catch (InterruptedException e) {
                LOG.error("", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    public void wakeMeUp() {
        LOG.info("Start to log disk monitor, wake up log thread.");
        semaphore.release();
    }

    @Override
    public void interrupt() {
        started = false;
        super.interrupt();
    }

    public void logIoStat() {
        String cmd = "iostat -d -x";
        ExecuteResult er = execute(cmd);
        if (er.getExitValue() != 0) {
           LOG.error("execute iostat failed. cmd: {}, stdout: {}, stderr: {}",
                   cmd, er.getStdOutput(), er.getErrOutput());
        } else {
            LOG.info("\n{}\n", er.getStdOutput());
        }
    }

    public void logIoTop() {
        String cmd = "iotop -botq --iter=1";
        ExecuteResult er = execute(cmd);
        if (er.getExitValue() != 0) {
            LOG.error("execute iotop failed. cmd: {}, stdout: {}, stderr: {}",
                    cmd, er.getStdOutput(), er.getErrOutput());
        } else {
            LOG.info("\n{}\n", er.getStdOutput());
        }
    }

    public ExecuteResult execute(String cmd) {
        Runtime r = Runtime.getRuntime();
        try {
            ByteArrayOutputStream stdOutputStream = new ByteArrayOutputStream();
            ByteArrayOutputStream errOutputStream = new ByteArrayOutputStream();
            Process proc = r.exec(new String[] {"sh", "-c", cmd});
            StreamGobbler stdoutGobbler = new StreamGobbler(
                    proc.getInputStream(), stdOutputStream);
            StreamGobbler stderrGobbler = new StreamGobbler(
                    proc.getErrorStream(), errOutputStream);
            stdoutGobbler.start();
            stderrGobbler.start();
            stdoutGobbler.join();
            stderrGobbler.join();
            int exitValue = proc.waitFor();
            return new ExecuteResult(exitValue,
                    stdOutputStream.toString(DEFAULT_CHARSET_NAME),
                    errOutputStream.toString(DEFAULT_CHARSET_NAME));
        } catch (InterruptedException e) {
            LOG.error("", e);
            return new ExecuteResult(-1, "", e.getMessage());
        } catch (IOException e) {
            LOG.error("", e);
            return new ExecuteResult(-1, "", e.getMessage());
        }

    }

    static class StreamGobbler extends Thread {
        private InputStream is;
        private ByteArrayOutputStream outputStream;

        public StreamGobbler(InputStream is,
                             ByteArrayOutputStream outputStream) {
            this.is = is;
            this.outputStream = outputStream;
        }

        @Override
        public void run() {
            try {
                byte[] buf = new byte[1024];
                int len;
                while ((len = is.read(buf)) != -1) {
                    outputStream.write(buf, 0, len);
                }
            } catch (IOException ioe) {
                LOG.error("", ioe);
            }
        }
    }

    static class ExecuteResult {
        private int exitValue;
        private String stdOutput;
        private String errOutput;

        public ExecuteResult(int exitValue, String stdOutput, String errOutput) {
            this.exitValue = exitValue;
            this.stdOutput = stdOutput;
            this.errOutput = errOutput;
        }

        public int getExitValue() {
            return exitValue;
        }

        public String getStdOutput() {
            return stdOutput;
        }

        public String getErrOutput() {
            return errOutput;
        }

        @Override
        public String toString() {
            return String.format("exit code: %d, stdout: %s, stderr: %s",
                    exitValue, stdOutput, errOutput);
        }
    }
}
