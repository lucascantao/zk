// package zk.test;

// import java.io.IOException;
// import java.net.InetAddress;
// import java.net.UnknownHostException;
// import java.nio.ByteBuffer;
// import java.util.List;
// import java.util.Random;

// import org.apache.zookeeper.CreateMode;
// import org.apache.zookeeper.KeeperException;
// import org.apache.zookeeper.WatchedEvent;
// import org.apache.zookeeper.Watcher;
// import org.apache.zookeeper.ZooKeeper;
// import org.apache.zookeeper.ZooDefs.Ids;
// import org.apache.zookeeper.data.Stat;

// import zk.test.SyncPrimitive.Barrier;

// class SyncPrimitive implements Watcher {

//     static ZooKeeper zk = null;
//     static Integer mutex;
//     static Integer mutexReady;
//     String root;

//     SyncPrimitive(String address) {
//         if(zk == null){
//             try {
//                 System.out.println("Starting ZK:");
//                 zk = new ZooKeeper(address, 3000, this);
//                 mutex = new Integer(-1);
//                 mutexReady = new Integer(-1);
//                 System.out.println("Finished starting ZK: " + zk);
//             } catch (IOException e) {
//                 System.out.println(e.toString());
//                 zk = null;
//             }
//         }
//         //else mutex = new Integer(-1);
//     }

//     synchronized public void process(WatchedEvent event) {
//         synchronized (mutex) {
//             System.out.println("Process: " + event.getType());
//             mutex.notify();
//         }
//     }

//     class MyWatcher implements Watcher {
        
//         @Override
//         synchronized public void process(WatchedEvent event) {
//             synchronized (mutexReady) {
//                 // if (event.getType() == Event.EventType.NodeChildrenChanged) {
//                     System.out.println("NÓ READY CRIADO...");
//                     mutexReady.notify();
//                 // }
//             }
//         }
//     }

//     /**
//      * Barrier
//      */
//     static public class Barrier extends SyncPrimitive {
//         int size;
//         String name;

//         /**
//          * Barrier constructor
//          *
//          * @param address
//          * @param root
//          * @param size
//          */
//         Barrier(String address, String root, int size) {
//             super(address);
//             this.root = root;
//             this.size = size;

//             // Create barrier node
//             if (zk != null) {
//                 try {
//                     Stat s = zk.exists(root, false);
//                     if (s == null) {
//                         zk.create(root, new byte[0], Ids.OPEN_ACL_UNSAFE,
//                                 CreateMode.PERSISTENT_SEQUENTIAL);
//                     }
//                 } catch (KeeperException e) {
//                     System.out
//                             .println("Keeper exception when instantiating queue: "
//                                     + e.toString());
//                 } catch (InterruptedException e) {
//                     System.out.println("Interrupted exception");
//                 }
//             }

//             // My node name
//             // this.name = name;
//             try {
//                 name = new String(InetAddress.getLocalHost().getCanonicalHostName().toString());
//             } catch (UnknownHostException e) {
//                 System.out.println(e.toString());
//             }

//         }

//         /**
//          * Join barrier
//          *
//          * @return
//          * @throws KeeperException
//          * @throws InterruptedException
//          */

//         boolean enter() throws KeeperException, InterruptedException{

//             // 1. Create a name n = b+“/”+p
//             String n = root + "/" + name;

//             // 2. Create child: create( n, EPHEMERAL)
//             String path = zk.create(n, new byte[0], Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);

//             // 3. Set watch: exists(b + ‘‘/ready’’, true)
//             zk.exists(root + "/ready", new MyWatcher());
//             System.out.println("Entering Node: " + path);

//             // 4. L = getChildren(b, false)
//             List<String> list = zk.getChildren(root, true);

//             // 5. if fewer children in L than x, wait for watch event
//             if (list.size() >= size) {
//                 zk.create(root + "/ready", new byte[0], Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
//                 return true;
//             }
//             synchronized (mutexReady) {
//                 mutexReady.wait();
//                 return true;
//             }
//         }

//         /**
//          * Wait until all reach barrier
//          *
//          * @return
//          * @throws KeeperException
//          * @throws InterruptedException
//          */
//         boolean leave() throws KeeperException, InterruptedException{

//             String nodePath = root + "/" + name;

//             while (true) {
//                 // Obter lista atual de nós de processos
//                 List<String> children = zk.getChildren(root, false);
//                 System.out.println(children);
//                 if (children.isEmpty()) {
//                     System.out.println("No process nodes left. Exiting.");
//                     return true;
//                 }

//                 // Identifica o nó atual e classifica a lista
//                 children.sort(String::compareTo);

//                 int currentIndex = children.indexOf(name);
//                 if (currentIndex == -1) {
//                     System.out.println("Node already deleted. Exiting.");
//                     return true;
//                 }

//                 String lowestNode = children.get(0);
//                 String highestNode = children.get(children.size() - 1);

//                 if (children.size() == 1 && children.get(0).equals(name)) {
//                     // Caso único: Processo é o último nó restante
//                     System.out.println("Last process node, deleting: " + nodePath);
//                     zk.delete(nodePath, -1);
//                     return true;
//                 }

//                 if (name.equals(lowestNode)) {
//                     // Se for o menor nó, aguarda remoção do maior nó
//                     System.out.println("Waiting for highest node to be deleted: " + highestNode);
//                     zk.exists(root + "/" + highestNode, event -> {
//                         synchronized (mutex) {
//                             mutex.notify();
//                         }
//                     });
//                 } else {
//                     // Se não for o menor, espera a remoção do nó anterior
//                     String previousNode = children.get(currentIndex - 1);
//                     System.out.println("Waiting for previous node to be deleted: " + previousNode);
//                     zk.exists(root + "/" + previousNode, event -> {
//                         synchronized (mutex) {
//                             mutex.notify();
//                         }
//                     });

//                     // Deleta o nó atual se ainda existir
//                     if (zk.exists(nodePath, false) != null) {
//                         System.out.println("Deleting current node: " + nodePath);
//                         zk.delete(nodePath, -1);
//                     }
//                 }

//                 // Aguarda notificação do watcher
//                 synchronized (mutex) {
//                     mutex.wait();
//                 }
//             }

//             // System.out.println("Exiting Node: " + root + "/" + name);
//             // zk.delete(root + "/" + name, 0);
//             // while (true) {
//             //     synchronized (mutex) {
//             //         List<String> list = zk.getChildren(root, true);
//             //             if (list.size() > 0) {
//             //                 mutex.wait();
//             //             } else {
//             //                 return true;
//             //             }
//             //         }
//             //     }
//             // }
//         }
//     }
// }

// public class BarreiraDuplaTeste {

//     public static void main(String args[]) {
//         String b_array[] = {"bTest", "localhost", "3"};

//         barrierTest(b_array);
//     }

//     public static void barrierTest(String args[]) {
//         Barrier b = new Barrier(args[1], "/b1", new Integer(args[2]));
//         try{
//             boolean flag = b.enter();
//             System.out.println("Entered barrier: " + args[2]);
//             if(!flag) System.out.println("Error when entering the barrier");
//         } catch (KeeperException e){
//         } catch (InterruptedException e){
//         }

//         // Generate random integer
//         Random rand = new Random();
//         int r = rand.nextInt(100);
//         // Loop for rand iterations
//         for (int i = 0; i < r; i++) {
//             try {
//                 Thread.sleep(100);
//             } catch (InterruptedException e) {
//             }
//         }
//         try{
//             b.leave();
//         } catch (KeeperException e){

//         } catch (InterruptedException e){

//         }
//         System.out.println("Left barrier");
//     }
// }