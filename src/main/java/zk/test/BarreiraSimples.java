package zk.test;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class BarreiraSimples implements Watcher {

    private static final String ROOT = "/b_lock";
    private static final int SESSION_TIMEOUT = 3000;
    private static final int SIZE = 3;

    private ZooKeeper zk = null;
    static Integer mutex;
    String name;

    public BarreiraSimples(String hostPort) throws IOException {
        this.name = name;
        if(zk == null){
            try {
                System.out.println("Starting ZK:");
                zk = new ZooKeeper(hostPort, SESSION_TIMEOUT, this);
                mutex = new Integer(-1);
                System.out.println("Finished starting ZK: " + zk);
            } catch (IOException e) {
                System.out.println(e.toString());
                zk = null;
            }
        }
    }

    @Override
    synchronized public void process(WatchedEvent event) {
        synchronized (mutex) {
            System.out.println("Process: " + event.getType());
            mutex.notify();
        }
    }

    public void createBarrierNode() throws KeeperException, InterruptedException {
        Stat stat = zk.exists(ROOT, false);
        if(stat == null) {
            zk.create(ROOT, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            System.out.println("Barrier node created: " + ROOT);
        }
    }
    

    public void enter(String nodeName) throws KeeperException, InterruptedException {
        System.out.println("\nProcess "+ nodeName +" waiting for the barrier...");
        barrier_wait();
    }

    public void barrier_wait() throws KeeperException, InterruptedException {
        Stat barrier = zk.exists(ROOT, new BarrierWatcher());
        if(barrier == null) {
            return;
        } else {
            synchronized (mutex) {
                mutex.wait();
            }
        }
    }

    private class BarrierWatcher implements Watcher {
        @Override
        public void process(WatchedEvent event) {
            if (event.getType() == Event.EventType.NodeDeleted) {
                synchronized (mutex){
                    System.out.println("Barrier deleted, releasing processes...");
                    mutex.notify();
                }
            }
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException, KeeperException {
        BarreiraSimples barrier = new BarreiraSimples("localhost:2181");

        barrier.createBarrierNode();

        String nodeName = "process-" + (int) (Math.random() * 1000);
        barrier.enter(nodeName);

        System.out.println("Barrier released. Proceeding with execution!");
    }
}
