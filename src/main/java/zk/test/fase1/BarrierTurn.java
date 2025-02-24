package zk.test.fase1;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Random;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.data.Stat;

import zk.test.fase1.TurnBasedSystem.Barrier;

class TurnBasedSystem implements Watcher {

    static ZooKeeper zk = null;
    static Object mutex;
    String root;

    TurnBasedSystem(String address) {
        if(zk == null){
            try {
                System.out.println("Starting Battle");
                zk = new ZooKeeper(address, 3000, this);
                mutex = new Object();
                // System.out.println("Finished starting ZK: " + zk);
            } catch (IOException e) {
                System.out.println(e.toString());
                zk = null;
            }
        }
    }

    synchronized public void process(WatchedEvent event) {
        synchronized (mutex) {
            System.out.println("Process: " + event.getType());
            mutex.notify();
        }
    }

    static public class Barrier extends TurnBasedSystem {
        int tamanho_do_time;
        String personagem;

        Barrier(String address, String root, int tam_time) {
            super(address);
            this.root = root;
            this.tamanho_do_time = tam_time;

            if (zk != null) {
                try {
                    Stat s = zk.exists(root, false);
                    if (s == null) {
                        zk.create(root, new byte[0], Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                    }
                } catch (KeeperException e) {
                    System.out.println("Keeper exception when instantiating queue: " + e.toString());
                } catch (InterruptedException e) {
                    System.out.println("Interrupted exception");
                }
            }

            try {
                personagem = new String(InetAddress.getLocalHost().getCanonicalHostName().toString());
            } catch (UnknownHostException e) {
                System.out.println(e.toString());
            }

        }

        /**
         * Join barrier
         *
         * @return
         * @throws KeeperException
         * @throws InterruptedException
         */

        boolean enter() throws KeeperException, InterruptedException{
            
            zk.create(root + "/" + personagem, new byte[0], Ids.OPEN_ACL_UNSAFE,
            CreateMode.EPHEMERAL);
            System.out.println("\n>>> [" + personagem + "] Encerrou seu turno!\n");	
            while (true) {
                synchronized (mutex) {
                    List<String> personagems_na_barreira = zk.getChildren(root, true);

                    if (personagems_na_barreira.size() < tamanho_do_time) {
                        mutex.wait();
                    } else {
                        return true;
                    }
                }
            }
        }

        /**
         * Wait until all reach barrier
         *
         * @return
         * @throws KeeperException
         * @throws InterruptedException
         */
        boolean leave() throws KeeperException, InterruptedException{
            System.out.println("\n>>> [" + personagem + "] Saindo da barreira: " + root + "/" + personagem + "\n*************\n");	
            zk.delete(root + "/" + personagem, 0);
            while (true) {
                synchronized (mutex) {
                    List<String> list = zk.getChildren(root, true);
                        if (list.size() > 0) {
                            mutex.wait();
                        } else {
                            return true;
                        }
                    }
                }
            }
        }

}

public class BarrierTurn {

    public static void main(String args[]) {
        barrierTest( "localhost", "3");
    }

    public static void barrierTest(String address, String b_tamanho) {

        String acoes[] = {"Ataque corpo a corpo", "Ataque a distancia", "Cura"};
        Barrier b = new Barrier(address, "/final_do_turno", Integer.parseInt(b_tamanho));

        Random rand = new Random();
        int r = rand.nextInt(100);
        for (int i = 0; i < r; i++) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
            }
        }

        System.out.println("\n>>> [" + b.personagem + "] usou " + acoes[rand.nextInt(3)]);

        try{
            boolean flag = b.enter();
            System.out.println("Entered barrier: " + b_tamanho);
            if(!flag) System.out.println("Error when entering the barrier");
        } catch (KeeperException e){
        } catch (InterruptedException e){
        }

        try{
            b.leave();
        } catch (KeeperException e){

        } catch (InterruptedException e){

        }
        System.out.println("Left barrier");
    }
}