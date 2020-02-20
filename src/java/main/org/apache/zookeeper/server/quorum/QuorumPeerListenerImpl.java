package org.apache.zookeeper.server.quorum;

import org.apache.zookeeper.server.ZooKeeperServerListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuorumPeerListenerImpl implements ZooKeeperServerListener {
    private static final Logger LOG = LoggerFactory.getLogger(QuorumPeerListenerImpl.class);

    private final QuorumPeer quorumPeer;

    QuorumPeerListenerImpl(QuorumPeer quorumPeer) {
        this.quorumPeer = quorumPeer;
    }

    @Override
    public void notifyStopping(String threadName, int exitCode) {
        LOG.info("Thread {} exits, error code {}", threadName, exitCode);
        if (quorumPeer != null) {
            quorumPeer.setRunning(false);
        }
    }
}
