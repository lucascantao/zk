package zk.test;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;

/**
 * Hello world!
 *
 */
public class App 
{

    private static ZooKeeper zooKeeper;

    public static void main( String[] args ) throws IOException, InterruptedException, KeeperException
    {
        zooKeeper = new ZooKeeper("localhost:2181", 3000, new Watcher() {
            @Override
            public void process(WatchedEvent event) {
                System.out.println("Evento recebido: " + event);
            }
        });
        
        String path = "/exampleNode";
        byte[] data = "Hello ZooKeeper".getBytes();

        String createdPath = zooKeeper.create(path, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        System.out.print(createdPath);

        Stat stat = new Stat();
        byte[] retrivedData = zooKeeper.getData(path, true, stat);
        System.out.println("Dados do nó: " + new String(retrivedData));

        byte[] newData = "Updated Data".getBytes();
        zooKeeper.setData(path, newData, stat.getVersion());
        System.out.println("Dados atualizados");

        Stat updatedStat = zooKeeper.exists(path, false);

        zooKeeper.delete(path, updatedStat.getVersion());
        System.out.println("Nó excluído");

    }
}
