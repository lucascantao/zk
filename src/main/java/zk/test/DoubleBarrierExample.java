package zk.test;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class DoubleBarrierExample {
    private static final String BARRIER_PATH = "/double_barrier";
    private static final int SESSION_TIMEOUT = 3000;
    private static final int NUM_PARTICIPANTS = 3;
    private ZooKeeper zooKeeper;
    private final CountDownLatch connectedSignal = new CountDownLatch(1);

    public DoubleBarrierExample(String hostPort) throws IOException {
        zooKeeper = new ZooKeeper(hostPort, SESSION_TIMEOUT, event -> {
            if (event.getState() == Watcher.Event.KeeperState.SyncConnected) {
                connectedSignal.countDown();
            }
        });
        try {
            connectedSignal.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void createBarrierNode() throws KeeperException, InterruptedException {
        Stat stat = zooKeeper.exists(BARRIER_PATH, false);
        if (stat == null) {
            zooKeeper.create(BARRIER_PATH, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            System.out.println("Barrier node created: " + BARRIER_PATH);
        }
    }

    public void enter(String nodeName) throws KeeperException, InterruptedException {

        // 1. Create a name n = b+“/”+p
        String nodePath = BARRIER_PATH + "/" + nodeName;

        // 2. Set watch: exists(b + ‘‘/ready’’, true)
        zooKeeper.exists(BARRIER_PATH + "/ready", true); // checar se ele notifica o nó ready

        // 3. Create child: create( n, EPHEMERAL)
        zooKeeper.create(nodePath, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);

        while (true) {

            // 4. L = getChildren(b, false)
            List<String> children = zooKeeper.getChildren(BARRIER_PATH, false);
            long count = children.size();
            System.out.println("Current participants in: " + count);

            // 5. if fewer children in L than x, wait for watch event
            if (NUM_PARTICIPANTS < count) {
                synchronized(this) {
                    wait();
                }
            } else {
                // 6. else create(b + ‘‘/ready’’, REGULAR)
                zooKeeper.create(BARRIER_PATH + "/ready", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
                break;
            }
        }

        // waitForParticipants("enter");
    }

    public void leave(String nodeName) throws KeeperException, InterruptedException {
        String nodePath = BARRIER_PATH + "/exit_" + nodeName;
        zooKeeper.create(nodePath, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        // waitForParticipants("exit");
    }

    // private void waitForParticipants(String phase) throws KeeperException, InterruptedException {
    //     while (true) {

    //         // 4. L = getChildren(b, false)
    //         List<String> children = zooKeeper.getChildren(BARRIER_PATH, false);
    //         long count = children.stream().filter(node -> node.contains(phase)).count();
    //         System.out.println("Current participants in " + phase + ": " + count);

    //         // 5. if fewer children in L than x, wait for watch event
    //         if (count >= NUM_PARTICIPANTS) {
                
    //             // 6. else create(b + ‘‘/ready’’, REGULAR)
    //             zooKeeper.create(BARRIER_PATH + "/ready", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
    //             break;
    //         }
    //         Thread.sleep(1000);
    //     }
    // }

    private class BarrierWatcher implements Watcher {
        @Override
        public void process(WatchedEvent event) {
            if (event.getType() == Event.EventType.NodeChildrenChanged) {
                System.out.println("nó ready criado!");
            }
        }
    }

    public static void main(String[] args) throws IOException, KeeperException, InterruptedException {
        DoubleBarrierExample barrier = new DoubleBarrierExample("localhost:2181");

        // Criando Barreira
        barrier.createBarrierNode();

        String nodeName = "node-" + (int) (Math.random() * 1000);
        System.out.println("Entering first barrier...");
        barrier.enter(nodeName);

        System.out.println("Executing critical section...");
        Thread.sleep(2000); // Simula execução

        System.out.println("Entering second barrier...");
        barrier.leave(nodeName);

        System.out.println("Execution complete!");
    }
}

