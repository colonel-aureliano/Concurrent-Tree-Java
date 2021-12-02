package treeTest;

import maybe.Maybe;
import org.junit.jupiter.api.Test;
import tree.ConcurrentTree;
import tree.ConcurrentTreeImpl;
import util.ConcurrentTreeFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;

public class TreeTests {
    private long sleep = 100;
    volatile boolean failed = false;

    /**
     * helper function to get a tree of Strings
     *
     * @return string tree
     */

    private ConcurrentTree<String, String> getTree() {
        return ConcurrentTreeFactory.makeStringTree();
    }

    /**
     * helper function to get a tree of Integers
     *
     * @return integer tree
     */
    private ConcurrentTree<Integer, Integer> getITree() {
        var tree = ConcurrentTreeFactory.makeIntTree();
        tree.setCompare(Comparator.comparingInt(i -> i));
        return tree;
    }

    /**
     * get a list of random integers
     *
     * @param length # of elements
     * @return the list
     */
    private ArrayList<Integer> getList(int length) {
        ArrayList<Integer> lst = new ArrayList<>();
        for (int i = 0; i < length; ++i)
            lst.add((int) (Math.random() * 0x7fffffff));
        return lst;
    }

    /**
     * tests four threads giving values at once.
     * If testQuery is set to true, also queries those values back
     *
     * @throws InterruptedException
     */
    @Test
    public void testGiveAndQuery() throws InterruptedException {
        boolean testQuery = true;
        var tree = getTree();
        Runnable lam1 = () ->
        {
            for (char c : "qwertyuiopasdfghjklzxcvbnm".toCharArray()) {
                tree.give(String.valueOf(c), String.valueOf(c));
                try {
                    Thread.sleep(sleep);
                    if (testQuery)
                        assertEquals(Maybe.some(String.valueOf(c)), tree.query(String.valueOf(c)));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    failed = true;
                }
            }
        };
        Runnable lam2 = () ->
        {
            for (char c : "qwertyuiopasdfghjklzxcvbnm".toUpperCase().toCharArray()) {
                tree.give(String.valueOf(c), String.valueOf(c));
                try {
                    Thread.sleep(sleep);
                    if (testQuery)
                        assertEquals(Maybe.some(String.valueOf(c)), tree.query(String.valueOf(c)));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    failed = true;
                }
            }
        };
        if (failed) {
            failed = false;
            fail();
        }
        var t1 = new Thread(lam1);
        var t2 = new Thread(lam2);
        var t3 = new Thread(lam1);
        var t4 = new Thread(lam2);
        t1.start();
        t2.start();
        t3.start();
        t4.start();
        t1.join();
        t2.join();
        t3.join();
        t4.join();
        tree.preOrder();
        tree.inOrder();
        for (char c : "qwertyuiopasdfghjklzxcvbnm".toCharArray()) {
            assertEquals(tree.get(String.valueOf(c)), Maybe.some(String.valueOf(c)));
            assertEquals(tree.get(String.valueOf(c).toUpperCase()), Maybe.some(String.valueOf(c).toUpperCase()));
        }
    }

    /**
     * Runs tests on n threads, multiple times, averages results, and writes them
     *
     * @param n       number of threads to use
     * @param lst     the list of integers to pass in
     * @param os      the stream to write the results to
     * @param numRuns and the number of runs to average over
     */

    public void testNThreads(int n, ArrayList<Integer> lst, OutputStream os, int numRuns) {
        float avgPerf = 0;
        long avgTime = 0;
        boolean wait = os == null;
        if (os == null)
            os = OutputStream.nullOutputStream();
        for (int k = 0; k < numRuns; ++k) {
            var threadList = new ArrayList<Thread>();

            var tree = getITree();
            //the giver
            threadList.add(new Thread(() ->
            {
                for (int i = 0; i < lst.size() / 50; ++i) {
                    var v = lst.get((int) (Math.random() * lst.size()));
                    tree.give(v, v + 1);
                }
            }));
            for (int i = 0; i < n; ++i) {
                int finalI = i;
                threadList.add(new Thread(() ->
                {
                    for (double _j = finalI; _j < lst.size(); _j += n) {
                        int j = (int) _j;
                        var v = lst.get(j);
                        tree.give(v, v);
                        if (wait)
                            try {
                                Thread.sleep((long) (Math.random() * 5));
                            } catch (InterruptedException ie) {
                                fail();
                            }
                        var found = tree.query(v);
                        assertTrue(found.equals(Maybe.some(v)) || found.equals(Maybe.some(v + 1)));
                    }
                    for (int j = finalI; j < lst.size(); j += n) {
                        var v = lst.get(j);
                        var found = tree.query(v);
                        assertTrue(found.equals(Maybe.some(v)) || found.equals(Maybe.some(v + 1)));
                    }
                }));
            }
            long tStart = System.nanoTime();
            for (Thread t : threadList)
                t.start();
            for (Thread t : threadList)
                try {
                    t.join();
                } catch (InterruptedException i) {
                    i.printStackTrace();
                }
            long rsp = (System.nanoTime() - tStart) / 1000000;
            avgPerf += 1f / (float) rsp;
            avgTime += rsp;
        }
        avgPerf /= (float) numRuns;
        avgTime /= numRuns;
        System.out.println("done testing " + n + " threads!");
        try {
            os.write((n + ", " + avgPerf + ", " + avgTime + "\n").getBytes());
        } catch (IOException ignored) {
        }
    }

    /**
     * Runs tests for 1 to n threads where n is the number of logical cores on your machine
     * if you do too many more than this the overhead from the threads will outweigh any concurrency
     * advantages. Feel free to set this to go higher to experiment with it. Also tests insertion
     * and lookups without any locks.
     * <p>
     * Results are stored in a file perfX.csv where X is the number of times you have run this function
     * This gives you a method of measuring how concurrent your tree is. If it doesn't improve at all
     * with more threads, you are probably being too conservative with your locking. If you are having
     * correctness issues, then you want to lock more.
     */
    @Test
    public void testAll() {
        int numRuns = 4;
        var prefs = Preferences.userNodeForPackage(ConcurrentTree.class);
        int run = prefs.getInt("Test Number", 0);
        var lst = getList(500000);
        FileOutputStream fo = null;
        try {
            fo = new FileOutputStream("perf" + run + ".csv");
            fo.write((getITree().getClass().toString() + "\n").getBytes());
        } catch (IOException ignored) {
        }

        for (int i = 1; i <= Runtime.getRuntime().availableProcessors() - 1; ++i)
            testNThreads(i, lst, fo, numRuns);
        long tAvg = 0;
        for (int i = 0; i < numRuns; ++i) {
            var tree = getITree();
            long tInit = System.nanoTime();
            for (int j : lst) {
                tree.insert(j, j + 1);
                tree.insert(j, j);
                assertEquals(tree.get(j), Maybe.some(j + 1));
            }
            for (int j : lst) {
                assertEquals(tree.get(j), Maybe.some(j + 1));
            }
            tAvg += System.nanoTime() - tInit;
        }
        tAvg /= (numRuns * 1000000);
        try {
            fo.write(("single, " + 1f / tAvg + ", " + tAvg + "\n").getBytes());
        } catch (IOException ignored) {
        }

        try {
            fo.close();
        } catch (IOException ignored) {
        }
        prefs.putInt("Test Number", run + 1);
        System.out.println("results in: perf" + run + ".csv");

    }

    @Test
    public void testInsert() throws InterruptedException {
        ConcurrentTreeImpl<String, Integer> t = new ConcurrentTreeImpl<>();
        assertEquals(Maybe.none(), t.insert("Basant", 175));
        assertEquals(Maybe.none(), t.insert("Yanny", 160));
        assertEquals(Maybe.none(), t.insert("Gries", 140));
        assertEquals(Maybe.none(), t.insert("Ambrose", 160));
        assertEquals(Maybe.none(), t.insert("Balll", 175));
        assertEquals(Maybe.some(175), t.insert("Balll", 176));
    }

    @Test
    public void myThreadTest() throws InterruptedException {
        ConcurrentTreeImpl<String, Integer> t = new ConcurrentTreeImpl<>();
        t.insert("Basant",175);
        t.insert("Yanny",120);

        Runnable lam1 = () -> {
            assertEquals(Maybe.some(120),t.give("Yanny", 160));
            t.give("Gries", 140);
        };

        Runnable lam2 = () -> {
            t.give("Ambrose", 159);
            t.give("Ball", 190);
        };

        Thread t1 = new Thread(lam1);
        Thread t2 = new Thread(lam2);

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        t.inOrder();
        t.preOrder();
    }
}
