package zk.test;

import org.apache.zookeeper.*;

import zk.test.fase3.BarreiraReutilizavel;

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
        
        for (int i = 0; i < 3; i++) {
            BarreiraReutilizavel barrier = new BarreiraReutilizavel("localhost:2181");
            Thread.sleep(1000);
            // System.out.println("\n >>> $ INICIANDO THREAD: " + i + "\n");
            new Thread(new Task(barrier, 10)).start();
        }
    }
    static class Task implements Runnable {
        private final BarreiraReutilizavel barrier;
        private final int num_cycles;

        public Task(BarreiraReutilizavel barrier, int num_cycles) {
            this.barrier = barrier;
            this.num_cycles = num_cycles;
        }

        @Override
        public void run() {
            try {
                barrier.createBarrierNode();

                for(int i = 0; i < num_cycles; i++) {
                    System.out.println("\n >>> $ INICIANDO CICLO: " + i + "\n");
                    int process = new Random().nextInt(5000);

                    String t_name = Thread.currentThread().getName() +"0"+ i;
                    System.out.println("\n >>> $ " + t_name + " processando ciclo "+ i +" \n");
                    Thread.sleep(process);
                    
                    barrier.enter(t_name);

                    barrier.leave();

                }


            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
