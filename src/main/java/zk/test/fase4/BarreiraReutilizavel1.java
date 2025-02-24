package zk.test.fase4;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class BarreiraReutilizavel1 implements Watcher {

    private static final String ROOT = "/b_lock";
    private static final int SESSION_TIMEOUT = 3000;
    private static final int SIZE = 3;  // Número esperado de processos

    private ZooKeeper zk;
    private final String nodeName;
    private static final Object mutex = new Object();

    public BarreiraReutilizavel1(String hostPort) throws IOException {
        this.nodeName = "process-" + (int) (Math.random() * 1000);
        this.zk = new ZooKeeper(hostPort, SESSION_TIMEOUT, this);
    }

    @Override
    public void process(WatchedEvent event) {
        synchronized (mutex) {
            if (event.getType() == Event.EventType.NodeChildrenChanged && event.getPath().equals(ROOT)) {
                mutex.notifyAll();
            }
        }
    }

    public void createBarrierNode() throws KeeperException, InterruptedException {
        Stat stat = zk.exists(ROOT, false);
        if (stat == null) {
            zk.create(ROOT, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            System.out.println("\n >>> Barrier node created: " + ROOT + "\n|__");
        }
    }

    public void enterBarrier() throws KeeperException, InterruptedException {
        String processPath = ROOT + "/" + nodeName;
        zk.create(processPath, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        System.out.println(nodeName + " waiting at barrier...");

        synchronized (mutex) {
            while (true) {
                List<String> children = zk.getChildren(ROOT, true);
                if (children.size() >= SIZE) {
                    System.out.println("\n >>> All processes arrived. Releasing barrier...\n|__");
                    releaseBarrier();
                    return;
                }
                mutex.wait();
            }
        }
    }

    private void releaseBarrier() throws KeeperException, InterruptedException {
        List<String> children = zk.getChildren(ROOT, false);
        for (String child : children) {
            zk.delete(ROOT + "/" + child, -1);
        }
        System.out.println("\n >>> Barrier reset for next cycle.\n|__");
    }

    public void close() throws InterruptedException {
        zk.close();
    }

    public static void main(String[] args) {
        int numThreads = 3;
        int numCycles = 3;

        for (int i = 0; i < numThreads; i++) {
            new Thread(() -> {
                try {
                    BarreiraReutilizavel1 barrier = new BarreiraReutilizavel1("localhost:2181");
                    barrier.createBarrierNode();

                    for (int cycle = 1; cycle <= numCycles; cycle++) {
                        System.out.println(Thread.currentThread().getName() + " executing cycle " + cycle);
                        Thread.sleep((long) (Math.random() * 1000));
                        barrier.enterBarrier(); // Sincronização entre processos
                    }
                    barrier.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }
}
