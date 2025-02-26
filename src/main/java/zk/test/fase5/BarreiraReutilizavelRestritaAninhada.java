package zk.test.fase5;

import org.apache.zookeeper.*;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.data.Stat;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

public class BarreiraReutilizavelRestritaAninhada implements Watcher {

    public static String ROOT = "/b1";
    public static int SESSION_TIMEOUT = 3000;
    public static int SIZE;
    
    static int turno_global = 0;
    public int turno;
    public String acoes[];

    private static Queue<String> waitingQueue = new LinkedList<>();

    private static ZooKeeper zk = null;
    static Integer mutex = new Integer(-1);
    static Integer mutexReady = new Integer(-1);
    static Integer mutexFull = new Integer(-1);
    String personagem;

    public BarreiraReutilizavelRestritaAninhada(String hostPort) throws IOException {

        Logger logger = (Logger) LoggerFactory.getLogger("org.apache.zookeeper");
        logger.setLevel(Level.ERROR); // Só mostra erros
        System.out.println("Desativando LOGs");

        if(zk == null){
            try {
                System.out.println(">>> $ Starting ZK: ");
                zk = new ZooKeeper(hostPort, SESSION_TIMEOUT, this);
                System.out.println(">>> $ Finished starting ZK: " + zk + "");
            } catch (IOException e) {
                System.out.println(e.toString());
                zk = null;
            }
        }
    }

    @Override
    synchronized public void process(WatchedEvent event) {
        synchronized (mutex) {
            // System.out.println(">>> $ process: " + name +"");
            System.out.println("[turno "+turno+"]["+personagem+"] NOTIFY: " + event.getType() + "");
            mutex.notifyAll();
        }
    }

    class MyWatcher implements Watcher {
        
        @Override
        synchronized public void process(WatchedEvent event) {
            synchronized (mutexReady) {
                System.out.println("[turno "+turno+"]["+personagem+"] NÓ READY CRIADO... ");
                mutexReady.notifyAll();
            }
        }
    }

    public void createBarrierNode() throws KeeperException, InterruptedException {
        Stat stat = zk.exists(ROOT, false);
        if(stat == null) {
            zk.create(ROOT, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            System.out.println("[turno "+turno+"]["+personagem+"] Barrier node created: " + ROOT);
        }
    }
    

    public boolean enter(String name) throws KeeperException, InterruptedException {
        
        while(turno > turno_global) {
            synchronized(mutex){
                System.out.println("\n[turno "+turno+"]["+name+"] Aguardando turno...");
                mutex.wait();
            }
        }
        
        Stat ready = zk.exists(ROOT + "/ready", new MyWatcher());

        this.personagem = name;
        String n = ROOT + "/" + name;

        List<String> current_list = zk.getChildren(ROOT, false);
        current_list.removeIf(s -> s.equals("ready"));

        if (ready != null) {
            System.out.println("[turno "+turno+"][" + name + "] Grupo cheio! Indo para o acampamento...");
            waitingQueue.add(name);
            // Espera até ser liberado pela thread que sai da barreira
            synchronized(mutexFull)
            {
                while (waitingQueue.contains(name)) {
                    System.out.println("[turno "+turno+"][" + name + "] " + waitingQueue);
                    mutexFull.wait();
                }
            }
        }

        String path = zk.create(n, new byte[0], Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);

        System.out.println("[turno "+turno+"][" + name + "] usou " + acoes[new Random().nextInt(acoes.length - 1)] + "");
        System.out.println(">>>["+name+"]: Encerrando Turno!");

        if(ready!= null) {
            return true;
        }

        List<String> list = zk.getChildren(ROOT, true);

        synchronized(mutex){
            if (list.size() >= SIZE) {
                if(zk.exists(ROOT + "/ready", false) == null){
                    System.out.println(">>>["+name+"] CRIANDO NÓ READY... ");
                    zk.create(ROOT + "/ready", new byte[0], Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
                }
                return true;
            }
        }
        synchronized (mutexReady) {
            System.out.println("[turno "+turno+"][" + name + "] Aguardando demais personagens... ");
            mutexReady.wait();
            return true;
        }
    }  


    public boolean leave() throws KeeperException, InterruptedException{

        int loop_count = 0;
        int has_node = 1;

        String nodePath = ROOT + "/" + personagem;

            while (true) {
                String processName = personagem+"|loop=" + loop_count + "|n="+has_node;

                List<String> children = zk.getChildren(ROOT, false);
                children.removeIf(s -> s.equals("ready"));
                
                if (children.isEmpty()) {
                    System.out.println("[turno "+turno+"]["+processName+"] No process nodes left. Exiting.");
                    return true;
                }
    
                children.sort(String::compareTo);
    
                int currentIndex = children.indexOf(personagem);
                // System.out.println("[turno "+turno+"]["+processName+"] SORTED CHILDREN: " + children + "");
                // System.out.println("[turno "+turno+"]["+processName+"] NAME: " + personagem + " CURRENT INDEX: " + currentIndex + "");
                if (currentIndex == -1) {
                    System.out.println("[turno "+turno+"]["+processName+"] Node already deleted. Exiting.");
                    return true;
                }
    
                String lowestNode = children.get(0);
                String highestNode = children.get(children.size() - 1);
    
                if (children.size() == 1 && children.get(0).equals(personagem)) {
                    zk.delete(nodePath, -1);
                    System.out.println("[turno "+turno+"]["+processName+"] Last process node, deleting: " + nodePath + "");
                    has_node = 0;
                    
                    synchronized(mutex) {
                        if(zk.exists(ROOT + "/ready", false) != null && waitingQueue.size() == 0){
                            // System.out.println("[turno "+turno+"]["+personagem+"] DELETANDO NÓ READY... fila: " + waitingQueue);
                            turno_global++;
                            zk.delete(ROOT + "/ready", -1);
                        }
                    }
                    
                    return true;
                }
    
                if (personagem.equals(lowestNode)) {
                    if(zk.exists(ROOT + "/" + highestNode, this) == null){
                        // System.out.println("[turno "+turno+"]["+processName+"] Highest node already deleted: " + highestNode + "");
                        continue;
                    }
                    // System.out.println("[turno "+turno+"]["+processName+"] Waiting for highest node to be deleted: " + highestNode + "");
                    zk.exists(ROOT + "/" + highestNode, this);                    
                } 
                
                else {
                    String previousNode = children.get(currentIndex - 1);
                    if(zk.exists(ROOT + "/" + previousNode, this) == null){
                        System.out.println("[turno "+turno+"]["+processName+"] previous node: " + previousNode + " already deleted. exiting");
                    } else {
                        System.out.println("[turno "+turno+"]["+processName+"] Waiting for previous node to be deleted: " + previousNode + "");
                    }
                    
                    if (zk.exists(nodePath, this) != null) {
                        zk.delete(nodePath, -1);
                        System.out.println("[turno "+turno+"]["+processName+"] Deleting current node: " + nodePath + "");
                        has_node = 0;
                    }
                }

                synchronized(mutexFull) {
                    if (!waitingQueue.isEmpty()) {
                        String nextInLine = waitingQueue.poll();
                        System.out.println(">>>[" + nextInLine + "] Saiu da fila de espera e pode entrar!");
                        mutexFull.notifyAll(); // Avisa as threads na fila
                    }
                }
    
                synchronized (mutex) {
                    mutex.wait();
                    // System.out.println("[turno "+turno+"]["+processName+"] Notification Received, proceding to next loop.");
                    loop_count++;
                }
            }

    }
}
