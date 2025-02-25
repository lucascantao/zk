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
        int max_threads = 4;
        int num_threads = 4;
        BarreiraReutilizavel.SIZE = max_threads;

        for (int i = 0; i < num_threads; i++) {
            BarreiraReutilizavel barrier = new BarreiraReutilizavel("localhost:2181");
            new Thread(new Task(barrier, 3)).start();
        }
    }

    static class Task implements Runnable {
        String acoes[] = {
            "Ataque corpo a corpo", 
            "Ataque a distancia", 
            "Cura"
        };

        static String arr[] = {
            "Astarion", 
            "Shadowheart", 
            "Karlach", 
            "Wyll"
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

                for(int i = 0; i < num_cycles; i++) {

                    if(Task.personagens.size() == 0) 
                        Task.personagens.addAll(Arrays.asList(Task.arr));

                    System.out.println("\n >>> $ INICIANDO TURNO: " + i + "\n");
                    Random rand = new Random();
                    int process = rand.nextInt(1000);
                    String threadName = Task.personagens.remove(rand.nextInt(personagens.size()));
                    System.out.println("\n >>> $ Turno de: " + threadName + "\n");
                    Thread.sleep(process);
                    System.out.println("\n>>> [" + threadName + "] usou " + acoes[rand.nextInt(3)] + "\n");
                    
                    barrier.enter(threadName);

                    barrier.leave();

                }


            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
