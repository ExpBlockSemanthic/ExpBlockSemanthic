// ExpBlock.java
package gr.edu.ihu.expblock;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.SplittableRandom;
import java.util.stream.IntStream;
import org.apache.commons.lang3.ArrayUtils;

public class ExpBlock {

    public double epsilon;
    public double delta = 0.1;
    public double q;
    public int w;
    public int b;
    public int globalRecNo = 0;
    public int occupied = 0;
    public double xi = .08;
    public int currentRound = 1;
    public int matchingPairsNo = 0;
    public int trulyMatchingPairsNo = 1000000;
    public MinHash minHash = new MinHash();
    public static FileWriter writer;
    public Block[] arr;
    IntStream rS;
    int[] r;
    int noRandoms = 5000;

    public ExpBlock(double epsilon, double q, int b) {
        try {
            this.epsilon = epsilon;
            this.b = b;
            this.q = q;
            this.arr = new Block[this.b];

            for (int i = 0; i < 10; i++) {
                IntStream rS = new SplittableRandom().ints(this.noRandoms / 10, 0, this.b);
                int[] r = rS.toArray();
                Arrays.sort(r);
                this.r = ArrayUtils.addAll(this.r, r);
            }
            this.w = (int) Math.ceil(3 * Math.log(2 / this.delta) / (this.q * (this.epsilon * this.epsilon)));
            writer = new FileWriter("results.txt");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void put(Record rec) {
        if (this.occupied == b) {
            int avg = this.globalRecNo / b;
            if (avg == 0) avg = 1;

            int v = 0;
            int j = 0;
            int i = r[j];
            while (v < (int) Math.floor((xi * b))) {
                Block block = arr[i];
                if (block == null) {
                    j++;
                    if (j == this.noRandoms) j = 0;
                    i = r[j];
                    continue;
                }
                block.setDegree(avg, currentRound);
                if (block.degree <= 0) {
                    arr[i] = null;
                    v++;
                } else {
                    block.recNo = block.recNo - avg;
                }
                j++;
                if (j == this.noRandoms) j = 0;
                i = r[j];
            }
            this.occupied = this.occupied - ((int) Math.floor((xi * b)));
            currentRound++;
        }
        this.globalRecNo++;
        String key = rec.getBlockingKey(minHash);

        boolean blockExists = false;
        int emptyPos = -1;
        for (int i = 0; i < arr.length; i++) {
            Block block = arr[i];
            if (block != null) {
                if (block.key.equals(key)) {
                    int mp = block.put(rec, w, currentRound, writer);
                    this.matchingPairsNo = this.matchingPairsNo + mp;
                    blockExists = true;
                    break;
                }
            } else {
                emptyPos = i;
            }
        }
        if (!blockExists) {
            Block newBlock = new Block(key, this.q);
            this.occupied++;
            int mp = newBlock.put(rec, w, currentRound, writer);
            if (emptyPos != -1) {
                arr[emptyPos] = newBlock;
            } else {
                for (int i = 0; i < this.b; i++) {
                    if (arr[i] == null) {
                        arr[i] = newBlock;
                        break;
                    }
                }
            }
            this.matchingPairsNo = this.matchingPairsNo + mp;
        }
    }

    public static Record prepare(String[] lineInArray) {
        String surname = lineInArray[1];
        String name = lineInArray[2];
        String address = lineInArray[3];
        String town = lineInArray[4];
        String poBox = lineInArray[5];
        String id = lineInArray[0];
        Record rec = new Record();
        rec.id = id;
        rec.name = name;
        rec.surname = surname;
        rec.town = town;
        rec.poBox = poBox;
        rec.origin = id.startsWith("a") ? "A" : "B"; 
        return rec;
    }

    public static void main(String[] args) {
        // ================== INICIALIZAÇÃO ==================
        String word2VecModelPath = "C:\\Users\\danie\\OneDrive\\Documentos\\ExpBlockSemanthic\\GoogleNews-vectors-negative300.bin";
        SimilarityService.initialize(word2VecModelPath);

        ExpBlock e = new ExpBlock(0.1, 2.0 / 3, 1000);
        System.out.println("Running ExpBlock using b=" + e.b + " w=" + e.w);
        int recNoA = 0;
        int recNoB = 0;
        long startTime = System.currentTimeMillis();

        String fileA = "C:\\Users\\danie\\OneDrive\\Documentos\\ExpBlockSemanthic\\target\\test_voters_A.txt";
        String fileB = "C:\\Users\\danie\\OneDrive\\Documentos\\ExpBlockSemanthic\\target\\test_voters_B.txt";

        try (CSVReader readerA = new CSVReader(new FileReader(fileA));
             CSVReader readerB = new CSVReader(new FileReader(fileB))) {

            String[] lineInArray1, lineInArray2;
            boolean fileAHasNext = true;
            boolean fileBHasNext = true;

            while (fileAHasNext || fileBHasNext) {
                if (fileAHasNext) {
                    lineInArray1 = readerA.readNext();
                    if (lineInArray1 != null) {
                        if (lineInArray1.length >= 6) {
                            recNoA++;
                            Record rec1 = prepare(lineInArray1);
                            e.put(rec1);
                        }
                    } else {
                        fileAHasNext = false;
                    }
                }

                if (fileBHasNext) {
                    lineInArray2 = readerB.readNext();
                    if (lineInArray2 != null) {
                        if (lineInArray2.length >= 6) {
                            recNoB++;
                            Record rec2 = prepare(lineInArray2);
                            e.put(rec2);
                        }
                    } else {
                        fileBHasNext = false;
                    }
                }

                if ((recNoA + recNoB) % 10000 == 0 && (recNoA + recNoB) > 0) {
                     System.out.println("====== Processed " + (recNoA + recNoB) + " records. Identified " + e.matchingPairsNo + " matching pairs.");
                }
            }
        } catch (IOException | CsvValidationException ex) {
            ex.printStackTrace();
        } finally {
            try {
                if (writer != null) writer.close();
            } catch (IOException ex2) {
                ex2.printStackTrace();
            }
        }

        long stopTime = System.currentTimeMillis();
        long elapsedTime = stopTime - startTime;
        System.out.println("\n==================== FINAL RESULTS ====================");
        System.out.println("Elapsed time: " + (elapsedTime / 1000.0) + " seconds.");
        System.out.println("Processed " + (recNoA + recNoB) + " records in total.");
        System.out.println("Processed " + recNoA + " records from A.");
        System.out.println("Processed " + recNoB + " records from B.");
        System.out.println("Identified " + e.matchingPairsNo + " matching pairs in total. Recall = " + (e.matchingPairsNo * 1.0 / e.trulyMatchingPairsNo));
        System.out.println("======================================================");
    }
}