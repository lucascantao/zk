package zk.test;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class DistributedBarrier implements Watcher {

    private static final String BARRIER_PATH = "/distributed_barrier";
    private static final int SESSION_TIMEOUT = 3000;
    private static final int NUM_PARTICIPANTS = 3;

    private ZooKeeper zooKeeper;
    private final CountDownLatch connectedSignal = new CountDownLatch(1);

    public DistributedBarrier(String hostPort) throws IOException {
        zooKeeper = new ZooKeeper(hostPort, SESSION_TIMEOUT, this);
        try {
            connectedSignal.await();
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void process(WatchedEvent event) {
        if(event.getState() == Event.KeeperState.SyncConnected) {
            connectedSignal.countDown();
        }
    }

    public void createBarrierNode() throws KeeperException, InterruptedException {
        Stat stat = zooKeeper.exists(BARRIER_PATH, false);
        if(stat == null) {
            zooKeeper.create(BARRIER_PATH, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            System.out.println("Barrier node created: " + BARRIER_PATH);
        }
    }
    

    public void enter(String nodeName) throws KeeperException, InterruptedException {
        String nodePath = BARRIER_PATH + "/" + nodeName;
        zooKeeper.create(nodePath, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        System.out.println("Node entered barrier: " + nodePath);
        barrier_wait();
    }

    public void barrier_wait() throws KeeperException, InterruptedException {
        while (true) {
            List<String> children = zooKeeper.getChildren(BARRIER_PATH, new BarrierWatcher());
            System.out.println("Current participants: " + children.size());
            if (children.size() >= NUM_PARTICIPANTS) {
                System.out.println("Barrier condition met. All nodes ready.");
                break;
            }

            Thread.sleep(1000);
        }
    }

    private class BarrierWatcher implements Watcher {
        @Override
        public void process(WatchedEvent event) {
            if (event.getType() == Event.EventType.NodeChildrenChanged) {
                System.out.println("Barrier state changed, checking participants...");
            }
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException, KeeperException {
        DistributedBarrier barrier = new DistributedBarrier("localhost:2181");

        barrier.createBarrierNode();

        String nodeName = "node-" + (int) (Math.random() * 1000);
        barrier.enter(nodeName);

        System.out.println("Barrier released. Proceeding with execution!");
    }
}
