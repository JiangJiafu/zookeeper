package org.apache.zookeeper.server.persistence;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

public class DiskMonitLogTest {

    @Ignore
    public void wakeMeUp() {
        DiskMonitLog diskMonitLog = DiskMonitLog.getInstance();
        if (!diskMonitLog.isStarted()) {
            diskMonitLog.start();
        }
        try {
            for (int i=0; i<2; i++) {
                diskMonitLog.wakeMeUp();
                Thread.sleep(60 *1000);
            }
        } catch (InterruptedException e) {
            Assert.fail(e.getMessage());
        }
        Assert.assertTrue(true);
    }
}