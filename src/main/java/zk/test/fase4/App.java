package zk.test.fase4;

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
        int num_threads = 6;
        BarreiraReutilizavelRestrita.SIZE = max_threads;

        for (int i = 0; i < num_threads; i++) {
            BarreiraReutilizavelRestrita barrier = new BarreiraReutilizavelRestrita("localhost:2181");
            Thread.sleep(100);
            new Thread(new Task(barrier, 5)).start();
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
            "Gale",
            "Lae'zel",
        };

        static ArrayList<String> personagens = new ArrayList<>(Arrays.asList(arr));
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
                barrier.acoes = acoes;

                for(int i = 0; i < num_cycles; i++) {
                    
                    barrier.turno = i;

                    if(Task.personagens.size() == 0) 
                        Task.personagens.addAll(Arrays.asList(Task.arr));

                    
                    Random rand = new Random();
                    int process = rand.nextInt(1000);
                    String threadName = Task.personagens.remove(rand.nextInt(personagens.size()));
                    System.out.println("[turno "+i+"]Turno de: " + threadName + ", Turno: " + i + "");
                    Thread.sleep(process);
                    // System.out.println("[turno "+i+"][" + threadName + "] usou " + acoes[rand.nextInt(acoes.length - 1)] + "");
                    
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
