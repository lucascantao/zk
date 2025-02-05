package zk.test.fase2;

import org.apache.zookeeper.*;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

public class BarreiraDupla implements Watcher {

    private static final String ROOT = "/b1";
    private static final int SESSION_TIMEOUT = 3000;
    private static final int SIZE = 3;

    private ZooKeeper zk = null;
    static Integer mutex;
    static Integer mutexReady;
    String name;
    // private final CountDownLatch connectedSignal = new CountDownLatch(1);

    public BarreiraDupla(String hostPort, String name) throws IOException {
        this.name = name;
        if(zk == null){
            try {
                System.out.println("Starting ZK:");
                zk = new ZooKeeper(hostPort, SESSION_TIMEOUT, this);
                mutex = new Integer(-1);
                mutexReady = new Integer(-1);
                System.out.println("Finished starting ZK: " + zk);
            } catch (IOException e) {
                System.out.println(e.toString());
                zk = null;
            }
        }
        
        // zk = new ZooKeeper(hostPort, SESSION_TIMEOUT, this);
        // try {
        //     connectedSignal.await();
        // } catch(InterruptedException e) {
        //     Thread.currentThread().interrupt();
        // }
    }

    @Override
    synchronized public void process(WatchedEvent event) {
        synchronized (mutex) {
            System.out.println("Process: " + event.getType());
            mutex.notify();
        }
    }

    class MyWatcher implements Watcher {
        
        @Override
        synchronized public void process(WatchedEvent event) {
            synchronized (mutexReady) {
                // if (event.getType() == Event.EventType.NodeChildrenChanged) {
                    System.out.println("NÓ READY CRIADO...");
                    mutexReady.notify();
                // }
            }
        }
    }

    public void createBarrierNode() throws KeeperException, InterruptedException {
        Stat stat = zk.exists(ROOT, false);
        if(stat == null) {
            zk.create(ROOT, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            System.out.println("Barrier node created: " + ROOT);
        }

        // try {
        //     name = new String(InetAddress.getLocalHost().getCanonicalHostName().toString());
        // } catch (UnknownHostException e) {
        //     System.out.println(e.toString());
        // }
    }
    

    public boolean enter() throws KeeperException, InterruptedException {
        // 1. Create a name n = b+“/”+p
        String n = ROOT + "/" + name;

        // 2. Create child: create( n, EPHEMERAL)
        String path = zk.create(n, new byte[0], Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);

        // 3. Set watch: exists(b + ‘‘/ready’’, true)
        zk.exists(ROOT + "/ready", new MyWatcher());
        System.out.println("Entering Node: " + path);

        // 4. L = getChildren(b, false)
        List<String> list = zk.getChildren(ROOT, true);

        // 5. if fewer children in L than x, wait for watch event
        if (list.size() >= SIZE) {
            zk.create(ROOT + "/ready", new byte[0], Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
            return true;
        }
        synchronized (mutexReady) {
            mutexReady.wait();
            return true;
        }
    }


    boolean leave() throws KeeperException, InterruptedException{

        if(zk.exists(ROOT + "/ready", false) != null){
            zk.delete(ROOT + "/ready", -1);
        }

        String nodePath = ROOT + "/" + name;

        while (true) {

            // 1. L = getChildren(b, false)
            // Obter lista atual de nós de processos
            List<String> children = zk.getChildren(ROOT, false);

            // 2. If no children, exit
            if (children.isEmpty()) {
                System.out.println("No process nodes left. Exiting.");
                return true;
            }

            // Identifica o nó atual e classifica a lista
            children.sort(String::compareTo);

            // Checa se o nó atual foi removido, quando o loop retornar por conta do notify()
            int currentIndex = children.indexOf(name);
            System.out.println("\nSORTED CHILDREN: " + children);
            System.out.print("NAME: " + name + " CURRENT INDEX: " + currentIndex + "\n");
            if (currentIndex == -1) {
                System.out.println("Node already deleted. Exiting.");
                return true;
            }

            String lowestNode = children.get(0);
            String highestNode = children.get(children.size() - 1);

            // 3. if p is only process node in L, delete(n) and exit
            if (children.size() == 1 && children.get(0).equals(name)) {
                // Caso único: Processo é o último nó restante
                System.out.println("Last process node, deleting: " + nodePath);
                zk.delete(nodePath, -1);
                return true;
            }

            // 4. if p is the lowest process node in L, wait on highest process node in P
            if (name.equals(lowestNode)) {
                // Se for o menor nó, aguarda remoção do maior nó
                System.out.println("Waiting for highest node to be deleted: " + highestNode);
                zk.exists(ROOT + "/" + highestNode, event -> {
                    synchronized (mutex) {
                        mutex.notify();
                    }
                });
            } 
            
            // 5. else delete(n) if still exists and wait on lowest process node in L
            else {
                // Se não for o menor, espera a remoção do nó anterior
                String previousNode = children.get(currentIndex - 1);
                System.out.println("Waiting for previous node to be deleted: " + previousNode);
                zk.exists(ROOT + "/" + previousNode, event -> {
                    synchronized (mutex) {
                        mutex.notify();
                    }
                });

                // Deleta o nó atual se ainda existir
                if (zk.exists(nodePath, false) != null) {
                    System.out.println("Deleting current node: " + nodePath);
                    zk.delete(nodePath, -1);
                }
            }

            // Aguarda notificação do watcher conforme os demais nós vão saindo
            synchronized (mutex) {
                mutex.wait();
            }
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException, KeeperException {
        String nodeName = "node-" + (int) (Math.random() * 1000);
        BarreiraDupla barrier = new BarreiraDupla("localhost:2181", nodeName);

        barrier.createBarrierNode();

        barrier.enter();
        
        System.out.println("Barrier released. Proceeding with execution!");

        Random rand = new Random();
        int r = rand.nextInt(100);
        // Loop for rand iterations
        for (int i = 0; i < r; i++) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
            }
        }
        
        System.out.println("Execution Finished!!");
        barrier.leave();
    }
}
