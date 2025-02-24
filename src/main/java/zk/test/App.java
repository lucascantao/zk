package zk.test;

import org.apache.zookeeper.*;

import zk.test.fase4.BarreiraReutilizavelRestrita;

import java.io.IOException;
import java.util.Random;

/**
 * Hello world!
 *
 */
public class App 
{

    

    public static void main( String[] args ) throws IOException, InterruptedException, KeeperException
    {

        int max_threads = 3;
        int num_threads = 5;
        BarreiraReutilizavelRestrita.SIZE = max_threads;

        for (int i = 0; i < num_threads; i++) {
            BarreiraReutilizavelRestrita barrier = new BarreiraReutilizavelRestrita("localhost:2181");
            new Thread(new Task(barrier, 5)).start();
        }
    }
    static class Task implements Runnable {
        private final BarreiraReutilizavelRestrita barrier;
        private final int num_cycles;

        public Task(BarreiraReutilizavelRestrita barrier, int num_cycles) {
            this.barrier = barrier;
            this.num_cycles = num_cycles;
        }

        @Override
        public void run() {
            try {
                barrier.createBarrierNode();

                for(int i = 0; i < num_cycles; i++) {
                    System.out.println("\n >>> $ INICIANDO CICLO: " + i + "\n");
                    int process = new Random().nextInt(1000);

                    String threadName = Thread.currentThread().getName();
                    System.out.println("\n >>> $ Criando thread: " + threadName + "\n");
                    Thread.sleep(process);
                    
                    barrier.enter(threadName);

                    barrier.leave();

                }


            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
