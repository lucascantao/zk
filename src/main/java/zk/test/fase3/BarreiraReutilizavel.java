package zk.test.fase3;

import org.apache.zookeeper.*;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.util.List;

public class BarreiraReutilizavel implements Watcher {

    private static final String ROOT = "/b1";
    private static final int SESSION_TIMEOUT = 3000;
    private static final int SIZE = 3;

    private static ZooKeeper zk = null;
    static Integer mutex = new Integer(-1);
    static Integer mutexReady = new Integer(-1);
    static Integer mutexEnter = new Integer(-1);
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
                System.out.println("\n >>> $["+name+"] NÓ READY CRIADO... \n");
                // System.out.println("\n >>> $["+name+"] Barreira deletada...");
                mutexReady.notifyAll();
            }
        }
    }
    class MyWatcher2 implements Watcher {
        
        @Override
        synchronized public void process(WatchedEvent event) {
            synchronized (mutexEnter) {
                if(event.getType() == Event.EventType.NodeDeleted){
                    System.out.println("\n >>> $["+name+"] NÓ READY DELETADO... \n");
                    mutexEnter.notifyAll();
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
        
        synchronized(mutexEnter) {
            if(zk.exists(ROOT + "/ready", new MyWatcher2()) != null){
                System.out.println("\n >>> $["+name+"] NÓ READY AINDA EXISTE \n");
                mutexEnter.wait();
            }
        }


        this.name = name;
        String n = ROOT + "/" + name;

        String path = zk.create(n, new byte[0], Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);

        zk.exists(ROOT + "/ready", new MyWatcher());
        System.out.println("\n >>> $["+name+"] Entering Node: " + path + "\n");

        List<String> list = zk.getChildren(ROOT, true);
        // list.removeIf(s -> s.equals("ready"));

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

            while (true) {

                List<String> children = zk.getChildren(ROOT, false);
                children.removeIf(s -> s.equals("ready"));
                
                if (children.isEmpty()) {
                    System.out.println("\n >>> $["+name+"|loop=" + loop_count + "|n="+has_node+"] No process nodes left. Exiting.\n");
                    return true;
                }
    
                children.sort(String::compareTo);
    
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
                    if(zk.exists(ROOT + "/" + highestNode, this) == null){
                        System.out.println("\n >>> $["+name+"|loop=" + loop_count + "|n="+has_node+"] Highest node already deleted: " + highestNode + "\n");
                        continue;
                    }
                    System.out.println("\n >>> $["+name+"|loop=" + loop_count + "|n="+has_node+"] Waiting for highest node to be deleted: " + highestNode + "\n");
                    zk.exists(ROOT + "/" + highestNode, this);                    
                } 
                
                else {
                    String previousNode = children.get(currentIndex - 1);
                    if(zk.exists(ROOT + "/" + previousNode, this) == null){
                        System.out.println("\n >>> $["+name+"|loop=" + loop_count + "|n="+has_node+"] previous node: " + previousNode + " already deleted. exiting\n");
                    } else {
                        System.out.println("\n >>> $["+name+"|loop=" + loop_count + "|n="+has_node+"] Waiting for previous node to be deleted: " + previousNode + "\n");
                    }
                    
                    if (zk.exists(nodePath, this) != null) {
                        zk.delete(nodePath, -1);
                        System.out.println("\n >>> $["+name+"|loop=" + loop_count + "|n="+has_node+"] Deleting current node: " + nodePath + "\n");
                        has_node = 0;
                    }
                }
    
                synchronized (mutex) {
                    mutex.wait();
                    System.out.println("\n >>> $["+name+"|loop=" + loop_count + "|n="+has_node+"] Notification Received, proceding to next loop.\n");
                    loop_count++;
                }
            }

    }
}
