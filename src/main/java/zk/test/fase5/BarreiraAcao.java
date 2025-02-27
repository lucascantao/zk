package zk.test.fase5;

import org.apache.zookeeper.*;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.data.Stat;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Random;

public class BarreiraAcao implements Watcher {

    public static String ROOT = "/b_turno";
    public String parent;
    public static int SESSION_TIMEOUT = 3000;
    public static int SIZE = 2;

    public String acoes[];

    private static ZooKeeper zk = null;
    static Integer mutex = new Integer(-1);
    static Integer mutexReady = new Integer(-1);
    String acao;

    public BarreiraAcao(String hostPort, String parent, ZooKeeper zk) throws IOException {

        this.parent = parent;
        
        Logger logger = (Logger) LoggerFactory.getLogger("org.apache.zookeeper");
        logger.setLevel(Level.ERROR); // Só mostra erros

        BarreiraAcao.zk = zk;

    }

    @Override
    synchronized public void process(WatchedEvent event) {
        synchronized (mutex) {
            // System.out.println(">>> $ process: " + name +"");
            System.out.println("["+acao+"] NOTIFY: " + event.getType() + "");
            mutex.notifyAll();
        }
    }

    class MyWatcher implements Watcher {
        
        @Override
        synchronized public void process(WatchedEvent event) {
            synchronized (mutexReady) {
                System.out.println("["+acao+"] NÓ READY CRIADO... ");
                mutexReady.notifyAll();
            }
        }
    }

    public void createBarrierNode() throws KeeperException, InterruptedException {
        Stat stat = zk.exists(ROOT + "/" + parent, false);
        if(stat == null) {
            zk.create(ROOT, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            System.out.println("["+acao+"] Barrier node created: " + ROOT);
        }
    }
    

    public boolean enter(String name) throws KeeperException, InterruptedException {

        this.acao = name;
        String n = ROOT + "/" + parent + "/" + name;
        String path = zk.create(n, new byte[0], Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);

        zk.exists(ROOT + "/" + parent + "/ready", new MyWatcher());

        System.out.println("\n[" + parent + "] usou " + acoes[new Random().nextInt(acoes.length - 1)] + "");
        // System.out.println("["+name+"]: Ecerrando Turno!");

        List<String> list = zk.getChildren(ROOT + "/" + parent, true);

        synchronized(mutex){
            if (list.size() >= SIZE) {
                if(zk.exists(ROOT + "/" + parent + "/ready", false) == null){
                    System.out.println("["+name+"] CRIANDO NÓ READY... ");
                    zk.create(ROOT + "/" + parent + "/ready", new byte[0], Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
                }
                return true;
            }
        }
        synchronized (mutexReady) {
            // System.out.println("[" + name + "] Aguardando demais personagens... ");
            mutexReady.wait();
            return true;
        }
    }  


    public boolean leave() throws KeeperException, InterruptedException{

        int loop_count = 0;
        int has_node = 1;

        String nodePath = ROOT + "/" + parent + "/" + acao;

            while (true) {
                String processName = acao+"|turno=" + loop_count + "|n="+has_node;

                List<String> children = zk.getChildren(ROOT + "/" + parent, false);
                children.removeIf(s -> s.equals("ready"));
                
                if (children.isEmpty()) {
                    System.out.println("["+processName+"] No process nodes left. Exiting.");
                    return true;
                }
    
                children.sort(String::compareTo);
    
                int currentIndex = children.indexOf(acao);
                System.out.println("["+processName+"] SORTED CHILDREN: " + children + "");
                System.out.println("["+processName+"] NAME: " + acao + " CURRENT INDEX: " + currentIndex + "");
                if (currentIndex == -1) {
                    System.out.println("["+processName+"] Node already deleted. Exiting.");
                    return true;
                }
    
                String lowestNode = children.get(0);
                String highestNode = children.get(children.size() - 1);
    
                if (children.size() == 1 && children.get(0).equals(acao)) {
                    zk.delete(nodePath, -1);
                    System.out.println("["+processName+"] Last process node, deleting: " + nodePath + "");
                    has_node = 0;
                    
                    synchronized(mutex) {
                        if(zk.exists(ROOT + "/" + parent + "/ready", false) != null){
                            System.out.println("["+acao+"] DELETANDO NÓ READY... ");
                            zk.delete(ROOT + "/" + parent + "/ready", -1);
                        }
                    }
                    
                    return true;
                }
    
                if (acao.equals(lowestNode)) {
                    if(zk.exists(ROOT + "/" + parent + "/" + highestNode, this) == null){
                        System.out.println("["+processName+"] Highest node already deleted: " + highestNode + "");
                        continue;
                    }
                    System.out.println("["+processName+"] Waiting for highest node to be deleted: " + highestNode + "");
                    zk.exists(ROOT + "/" + highestNode, this);                    
                } 
                
                else {
                    String previousNode = children.get(currentIndex - 1);
                    if(zk.exists(ROOT + "/" + parent + "/" + previousNode, this) == null){
                        System.out.println("["+processName+"] previous node: " + previousNode + " already deleted. exiting");
                    } else {
                        System.out.println("["+processName+"] Waiting for previous node to be deleted: " + previousNode + "");
                    }
                    
                    if (zk.exists(nodePath, this) != null) {
                        zk.delete(nodePath, -1);
                        System.out.println("["+processName+"] Deleting current node: " + nodePath + "");
                        has_node = 0;
                    }
                }
    
                synchronized (mutex) {
                    mutex.wait();
                    System.out.println("["+processName+"] Notification Received, proceding to next loop.");
                    loop_count++;
                }
            }

    }
}
