package zk.test.fase3;

import org.apache.zookeeper.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

/**
 * Hello world!
 *
 */
public class App 
{

    public static void main( String[] args ) throws IOException, InterruptedException, KeeperException
    {

        int num_threads = 4;
        int num_cycles = 3;
        BarreiraReutilizavel.SIZE = num_threads;

        for (int i = 0; i < num_threads; i++) {
            BarreiraReutilizavel barrier = new BarreiraReutilizavel("localhost:2181");
            Thread.sleep(100);
            new Thread(new Task(barrier, num_cycles)).start();
        }
    }

    static class Task implements Runnable {
        String acoes[] = {
            "Ataque corpo a corpo", 
            "Ataque a distancia", 
            "Cura",
            "Disparada",
            "Benção"
        };

        static String arr[] = {
            "Astarion", 
            "Shadowheart", 
            "Karlach",
            "Wyll",
        };

        static ArrayList<String> personagens = new ArrayList<>(Arrays.asList(arr));
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
                barrier.acoes = acoes;

                for(int i = 0; i < num_cycles; i++) {

                    System.out.println("Iniciando turno " + i);

                    if(Task.personagens.size() == 0) 
                        Task.personagens.addAll(Arrays.asList(Task.arr));

                    Random rand = new Random();

                    String threadName = Task.personagens.remove(rand.nextInt(personagens.size()));
                    System.out.println("Turno de: " + threadName + ", Turno: " + i + "");
                    
                    Thread.sleep(rand.nextInt(1000,1500));
                    
                    barrier.enter(threadName);
                    Thread.sleep(1000);
                    barrier.leave();

                }


            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
