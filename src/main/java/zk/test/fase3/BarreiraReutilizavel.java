package zk.test.fase3;

import org.apache.zookeeper.*;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

public class BarreiraReutilizavel implements Watcher {

    private static final String ROOT = "/b1";
    private static final int SESSION_TIMEOUT = 3000;
    private static final int SIZE = 3;

    private static ZooKeeper zk = null;
    static Integer mutex = new Integer(-1);
    static Integer mutexReady = new Integer(-1);
    String name;

    public BarreiraReutilizavel(String hostPort) throws IOException {
        if(zk == null){
            try {
                System.out.println("\n >>> $ Starting ZK: \n");
                zk = new ZooKeeper(hostPort, SESSION_TIMEOUT, this);
                System.out.println("\n >>> $ Finished starting ZK: " + zk + "\n");
            } catch (IOException e) {
                System.out.println(e.toString());
                zk = null;
            }
        }
    }

    @Override
    synchronized public void process(WatchedEvent event) {
        synchronized (mutex) {
            // System.out.println("\n >>> $ process: " + name +"\n");
            System.out.println("\n >>> $["+name+"] NOTIFY: " + event.getType() + "\n");
            mutex.notifyAll();
        }
    }

    class MyWatcher implements Watcher {
        
        @Override
        synchronized public void process(WatchedEvent event) {
            synchronized (mutexReady) {
                if(event.getType() == Event.EventType.NodeDeleted){
                    System.out.println("\n >>> $["+name+"] NÓ READY DELETADO... \n");
                    mutexReady.notify();
                } else {
                    System.out.println("\n >>> $["+name+"] NÓ READY CRIADO... \n");
                    // System.out.println("\n >>> $["+name+"] Barreira deletada...");
                    mutexReady.notifyAll();
                }
            }
        }
    }

    public void createBarrierNode() throws KeeperException, InterruptedException {
        Stat stat = zk.exists(ROOT, false);
        if(stat == null) {
            zk.create(ROOT, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            System.out.println("\n >>> $["+name+"] Barrier node created: " + ROOT);
        }
    }
    

    public boolean enter(String name) throws KeeperException, InterruptedException {
        
        synchronized(mutexReady) {
            if(zk.exists(ROOT + "/ready", new MyWatcher()) != null){
                mutexReady.wait();
            }
        }


        // 1. Create a name n = b+“/”+p
        this.name = name;
        String n = ROOT + "/" + name;

        // 2. Create child: create( n, EPHEMERAL)
        String path = zk.create(n, new byte[0], Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);

        // 3. Set watch: exists(b + ‘‘/ready’’, true)
        zk.exists(ROOT + "/ready", new MyWatcher());
        System.out.println("\n >>> $["+name+"] Entering Node: " + path + "\n");

        // 4. L = getChildren(b, false)
        List<String> list = zk.getChildren(ROOT, true);

        // 5. if fewer children in L than x, wait for watch event
        synchronized(mutex){
            if (list.size() >= SIZE) {
                if(zk.exists(ROOT + "/ready", false) == null){
                    System.out.println("\n >>> $["+name+"] CRIANDO NÓ READY... \n");
                    zk.create(ROOT + "/ready", new byte[0], Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
                }
                return true;
            }
        }
        synchronized (mutexReady) {
            System.out.println("\n >>>" + name + " AGUARDANDO NA BARREIRA \n");
            mutexReady.wait();
            return true;
        }
    }
    


    public boolean leave() throws KeeperException, InterruptedException{

        int loop_count = 0;
        int has_node = 1;

        String nodePath = ROOT + "/" + name;

        // synchronized(mutex){

            while (true) {

                // Obter lista atual de nós de processos
                
                // List<String> list = zk.getChildren(ROOT, false);
                List<String> children = zk.getChildren(ROOT, false);
                children.removeIf(s -> s.equals("ready"));
                
                if (children.isEmpty()) {
                    System.out.println("\n >>> $["+name+"|loop=" + loop_count + "|n="+has_node+"] No process nodes left. Exiting.\n");
                    return true;
                }
    
                // Identifica o nó atual e classifica a lista
                children.sort(String::compareTo);
    
                // Checa se o nó atual foi removido, quando o loop retornar por conta do notify()
                int currentIndex = children.indexOf(name);
                System.out.println("\n >>> $["+name+"|loop=" + loop_count + "|n="+has_node+"] SORTED CHILDREN: " + children + "\n");
                System.out.println("\n >>> $["+name+"|loop=" + loop_count + "|n="+has_node+"] NAME: " + name + " CURRENT INDEX: " + currentIndex + "\n");
                if (currentIndex == -1) {
                    System.out.println("\n >>> $["+name+"|loop=" + loop_count + "|n="+has_node+"] Node already deleted. Exiting.\n");
                    return true;
                }
    
                String lowestNode = children.get(0);
                String highestNode = children.get(children.size() - 1);
    
                if (children.size() == 1 && children.get(0).equals(name)) {
                    // Caso único: Processo é o último nó restante
                    zk.delete(nodePath, -1);
                    System.out.println("\n >>> $["+name+"|loop=" + loop_count + "|n="+has_node+"] Last process node, deleting: " + nodePath + "\n");
                    has_node = 0;
                    
                    synchronized(mutex) {
                        if(zk.exists(ROOT + "/ready", false) != null){
                            System.out.println("\n >>> $["+name+"] DELETANDO NÓ READY... \n");
                            zk.delete(ROOT + "/ready", -1);
                        }
                    }
                    
                    return true;
                }
    
                if (name.equals(lowestNode)) {
                    // Se for o menor nó, aguarda remoção do maior nó
                    System.out.println("\n >>> $["+name+"|loop=" + loop_count + "|n="+has_node+"] Waiting for highest node to be deleted: " + highestNode + "\n");
                    zk.exists(ROOT + "/" + highestNode, this);                    
                } 
                
                else {
                    // Se não for o menor, espera a remoção do nó anterior
                    String previousNode = children.get(currentIndex - 1);
                    if(zk.exists(ROOT + "/" + previousNode, this) == null){
                        System.out.println("\n >>> $["+name+"|loop=" + loop_count + "|n="+has_node+"]" + previousNode + " already deleted. exiting\n");
                    } else {
                        System.out.println("\n >>> $["+name+"|loop=" + loop_count + "|n="+has_node+"] Waiting for previous node to be deleted: " + previousNode + "\n");
                    }
                    // zk.exists(ROOT + "/" + previousNode, this);
    
                    // Deleta o nó atual se ainda existir
                    if (zk.exists(nodePath, this) != null) {
                        zk.delete(nodePath, -1);
                        System.out.println("\n >>> $["+name+"|loop=" + loop_count + "|n="+has_node+"] Deleting current node: " + nodePath + "\n");
                        has_node = 0;
                    }
                }
    
                // Aguarda notificação do watcher conforme os demais nós vão saindo
                synchronized (mutex) {
                    mutex.wait();
                    System.out.println("\n >>> $["+name+"|loop=" + loop_count + "|n="+has_node+"] Notification Received, proceding to next loop.\n");
                    loop_count++;
                }
            }
        // }

        // return true;

    }

    public static void main(String[] args) throws IOException, InterruptedException, KeeperException {
        // BarreiraReutilizavel barrier = new BarreiraReutilizavel("localhost:2181");

        // barrier.createBarrierNode();

        // for (int i = 0; i < 3; i++) {
        //     Thread.sleep(1000);
        //     new Thread(new Task(new BarreiraReutilizavel("localhost:2181"))).start();
        // }

        // barrier.leave();
        
    }

    // static class Task implements Runnable {
    //     private final BarreiraReutilizavel barrier;
    //     // private final int num_cycles;

    //     public Task(BarreiraReutilizavel barrier) {
    //         this.barrier = barrier;
    //         // this.num_cycles = num_cycles;
    //     }

    //     @Override
    //     public void run() {
    //         try {
    //             String t_name = Thread.currentThread().getName();
    //             barrier.createBarrierNode();
    //             barrier.enter(t_name);
    //             System.out.println("\n >>> $ BARREIRA LIBERADA - " + t_name);
    //             // barrier.leave();
    //         } catch (Exception e) {
    //             e.printStackTrace();
    //         }
    //     }
    // }
}
